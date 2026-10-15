package com.amplify.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Keeps one live connection to Supabase Realtime so new messages show up instantly.
 *
 * Screens do not create this themselves:
 *   MessagingRealtime rt = MessagingRealtime.get(context);
 *   rt.addListener(listener);   // start hearing about changes
 *   rt.removeListener(listener);
 * MainActivity calls acquire() when it opens and release() when it closes, so the
 * connection is only open while the app is on screen.
 *
 * Safety net: whenever the live connection is down, a "poll" event is sent every 4 seconds.
 * Screens answer a poll by reloading from the server, so messages still arrive (a few
 * seconds late) even if Realtime isn't working. Filter Logcat by "AmplifyRealtime" to see why.
 *
 * Only the changes are announced here. Messages are read by the screens themselves, which
 * decrypt them.
 */
public class MessagingRealtime {

    private static final String TAG = "AmplifyRealtime";

    /** Everything has a default, so a screen only overrides what it cares about. */
    public interface Listener {
        /**
         * table: "direct_messages" or "conversations" (or "poll" when the live connection is down)
         * type: "INSERT", "UPDATE", "DELETE" (or "POLL")
         */
        default void onChange(String table, String type, JSONObject record, JSONObject oldRecord) {
        }

        default void onLiveChanged(boolean live) {
        }
    }

    private static final String TOPIC = "realtime:amplify-direct-messages";
    private static final long HEARTBEAT_MS = 25_000L;
    private static final long TOKEN_REFRESH_MS = 10 * 60_000L;
    private static final long POLL_MS = 4_000L;
    private static final long JOIN_TIMEOUT_MS = 15_000L;

    private static MessagingRealtime instance;

    public static synchronized MessagingRealtime get(Context context) {
        if (instance == null) instance = new MessagingRealtime(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final SupabaseApi api;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    // One thread owns all the state below, so no locking is needed for it.
    private final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
    private boolean tickRunning = false;
    private int users = 0;
    private WebSocket socket = null;
    private boolean live = false;
    private int attempts = 0;
    private int refCounter = 0;
    private String joinRef = null;
    private boolean heartbeatPending = false;
    private long nextConnectAt = 0;
    private long connectStartedAt = 0;
    private long lastHeartbeat = 0;
    private long lastToken = 0;
    private long lastPoll = 0;

    private volatile boolean liveForReaders = false;

    private MessagingRealtime(Context context) {
        this.context = context;
        this.api = new SupabaseApi(context);
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isLive() {
        return liveForReaders;
    }

    /** Opens the connection if nobody else has. Pair every acquire() with a release(). */
    public void acquire() {
        sched.execute(() -> {
            users++;
            if (!tickRunning) {
                tickRunning = true;
                nextConnectAt = 0;
                sched.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
            }
        });
    }

    public void release() {
        sched.execute(() -> {
            if (users > 0) users--;
            if (users == 0) closeSocket();
        });
    }

    // ------------------------------------------------------------------
    // Runs once a second on the scheduler thread
    // ------------------------------------------------------------------
    private void tick() {
        try {
            if (users == 0) return;
            long now = System.currentTimeMillis();

            if (socket == null) {
                if (now >= nextConnectAt) connect(now);
            } else if (!live) {
                if (now - connectStartedAt > JOIN_TIMEOUT_MS) {
                    Log.w(TAG, "Joining took too long. Trying again.");
                    failed();
                }
            } else {
                if (now - lastHeartbeat >= HEARTBEAT_MS) {
                    if (heartbeatPending) {
                        Log.w(TAG, "Server stopped answering. Reconnecting.");
                        failed();
                    } else {
                        sendHeartbeat(now);
                    }
                }
                if (live && now - lastToken >= TOKEN_REFRESH_MS) {
                    sendToken(now);
                }
            }

            if (!live && now - lastPoll >= POLL_MS) {
                lastPoll = now;
                dispatch("poll", "POLL", null, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "tick problem: " + e);
        }
    }

    private void connect(long now) {
        String token;
        try {
            token = api.getFreshAccessToken();
        } catch (Exception e) {
            Log.w(TAG, "No token for live connection: " + e.getMessage());
            scheduleRetry(now);
            return;
        }

        String url = SupabaseConfig.PROJECT_URL.replaceFirst("^http", "ws")
                + "/realtime/v1/websocket?apikey=" + SupabaseConfig.ANON_KEY + "&vsn=1.0.0";
        connectStartedAt = now;
        live = false;
        heartbeatPending = false;

        final WebSocket[] holder = new WebSocket[1];
        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                sched.execute(() -> {
                    if (ws != socket) return;
                    Log.d(TAG, "Socket open. Joining.");
                    sendJoin(ws, token);
                });
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                sched.execute(() -> {
                    if (ws == socket) handleMessage(text);
                });
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                sched.execute(() -> {
                    if (ws == socket) {
                        Log.w(TAG, "Socket closed: " + code + " " + reason);
                        failed();
                    }
                });
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                sched.execute(() -> {
                    if (ws == socket) {
                        Log.w(TAG, "Socket failure: " + t
                                + (response != null ? " (HTTP " + response.code() + ")" : ""));
                        failed();
                    }
                });
            }
        };

        try {
            Request request = new Request.Builder().url(url).build();
            socket = client.newWebSocket(request, listener);
        } catch (Exception e) {
            Log.w(TAG, "Could not open socket: " + e);
            socket = null;
            scheduleRetry(now);
        }
    }

    private void scheduleRetry(long now) {
        attempts++;
        long wait = Math.min(30_000L, 1000L << Math.min(attempts, 5));
        nextConnectAt = now + wait;
    }

    private String nextRef() {
        refCounter++;
        return String.valueOf(refCounter);
    }

    private void sendJoin(WebSocket ws, String token) {
        try {
            JSONObject config = new JSONObject();
            config.put("broadcast", new JSONObject().put("self", false));
            config.put("presence", new JSONObject().put("key", ""));
            config.put("private_channel", false);

            org.json.JSONArray changes = new org.json.JSONArray();
            changes.put(new JSONObject().put("event", "*").put("schema", "public")
                    .put("table", "direct_messages"));
            changes.put(new JSONObject().put("event", "*").put("schema", "public")
                    .put("table", "conversations"));
            config.put("postgres_changes", changes);

            JSONObject payload = new JSONObject();
            payload.put("config", config);
            payload.put("access_token", token);

            joinRef = nextRef();
            long now = System.currentTimeMillis();
            lastToken = now;
            lastHeartbeat = now;
            send(ws, TOPIC, "phx_join", payload, joinRef);
        } catch (Exception e) {
            Log.e(TAG, "Join failed: " + e);
            failed();
        }
    }

    private void sendHeartbeat(long now) {
        lastHeartbeat = now;
        heartbeatPending = true;
        send(socket, "phoenix", "heartbeat", new JSONObject(), nextRef());
    }

    private void sendToken(long now) {
        lastToken = now;
        try {
            String token = api.getFreshAccessToken();
            send(socket, TOPIC, "access_token", new JSONObject().put("access_token", token),
                    nextRef());
        } catch (Exception e) {
            Log.w(TAG, "Could not refresh live token: " + e.getMessage());
        }
    }

    private void send(WebSocket ws, String topic, String event, JSONObject payload, String ref) {
        if (ws == null) return;
        try {
            JSONObject m = new JSONObject();
            m.put("topic", topic);
            m.put("event", event);
            m.put("payload", payload);
            m.put("ref", ref);
            ws.send(m.toString());
        } catch (Exception e) {
            Log.e(TAG, "Send failed: " + e);
        }
    }

    private void handleMessage(String text) {
        try {
            JSONObject m = new JSONObject(text);
            String event = m.optString("event", "");
            String topic = m.optString("topic", "");
            JSONObject payload = m.optJSONObject("payload");

            switch (event) {
                case "phx_reply":
                    if ("phoenix".equals(topic)) {
                        heartbeatPending = false;
                    } else if (joinRef != null && joinRef.equals(m.optString("ref", ""))) {
                        String status = payload == null ? "" : payload.optString("status", "");
                        if ("ok".equals(status)) {
                            Log.d(TAG, "Live connection ready.");
                            attempts = 0;
                            setLive(true);
                            // Catch up on anything missed while we were offline.
                            dispatch("poll", "POLL", null, null);
                        } else {
                            Log.w(TAG, "Join was refused: " + text);
                            failed();
                        }
                    }
                    break;

                case "postgres_changes":
                    JSONObject data = payload == null ? null : payload.optJSONObject("data");
                    if (data != null) {
                        dispatch(data.optString("table", ""), data.optString("type", ""),
                                data.optJSONObject("record"), data.optJSONObject("old_record"));
                    }
                    break;

                case "system":
                    if (payload != null && "error".equals(payload.optString("status", ""))) {
                        Log.w(TAG, "System error: " + text);
                    }
                    break;

                case "phx_error":
                case "phx_close":
                    Log.w(TAG, "Channel problem: " + text);
                    failed();
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read message: " + e);
        }
    }

    // ------------------------------------------------------------------
    // State changes and delivery
    // ------------------------------------------------------------------
    private void setLive(boolean value) {
        if (live == value) return;
        live = value;
        liveForReaders = value;
        main.post(() -> {
            for (Listener l : listeners) l.onLiveChanged(value);
        });
    }

    /** Drops the connection and plans another try (waits a little longer each time). */
    private void failed() {
        closeSocket();
        if (users > 0) scheduleRetry(System.currentTimeMillis());
    }

    private void closeSocket() {
        WebSocket old = socket;
        socket = null;
        joinRef = null;
        if (old != null) {
            try {
                old.close(1000, "bye");
            } catch (Exception ignored) {
            }
            try {
                old.cancel();
            } catch (Exception ignored) {
            }
        }
        setLive(false);
        // Poll straight away so there is no gap while the connection is down.
        lastPoll = 0;
    }

    private void dispatch(String table, String type, JSONObject record, JSONObject oldRecord) {
        main.post(() -> {
            for (Listener l : listeners) l.onChange(table, type, record, oldRecord);
        });
    }
}
