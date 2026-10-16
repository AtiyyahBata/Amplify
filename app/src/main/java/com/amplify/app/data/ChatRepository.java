package com.amplify.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.amplify.app.models.AiMessage;
import com.amplify.app.models.ChatHistoryItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Talks to Supabase for the AI chat. This replaces the AI parts of DummyData.
 *
 *  - loadChats()          -> history list
 *  - loadMessages()       -> resume a chat
 *  - sendMessageStream()  -> calls the "ai-chat" Edge Function; the answer arrives word by word
 *  - deleteChat()         -> delete one chat
 *  - deleteAllChats()     -> delete every chat of the signed-in user
 *
 * Before every call it checks whether the access token is about to expire and, if so,
 * swaps it for a fresh one using the refresh token.
 *
 * Every callback runs on the main (UI) thread, so activities can touch views directly.
 */
public class ChatRepository {

    private static final String TAG = "AmplifyChat";

    /** Anything that can be told "this failed". */
    public interface ErrorSink {
        void onError(String message);
    }

    public interface ResultCallback<T> extends ErrorSink {
        void onSuccess(T result);
    }

    /** Callbacks for a streamed AI answer. */
    public interface StreamCallback extends ErrorSink {
        /** A new piece of the answer arrived. Append it to the bubble. */
        void onText(String chunk);

        /** The answer is complete and has been saved. */
        void onDone(String chatId);
    }

    private interface Parser<T> {
        T parse(String body) throws Exception;
    }

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    // Used only for the AI call: waits longer for the first words, and for the whole answer.
    private static final OkHttpClient CLIENT_SLOW = CLIENT.newBuilder()
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build();

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ChatRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    // ------------------------------------------------------------------
    // History list
    // ------------------------------------------------------------------
    public void loadChats(ResultCallback<List<ChatHistoryItem>> callback) {
        withFreshToken(() -> {
            HttpUrl url = HttpUrl.parse(SupabaseConfig.PROJECT_URL + "/rest/v1/chats").newBuilder()
                    .addQueryParameter("select", "id,title,updated_at,messages(content)")
                    .addQueryParameter("order", "updated_at.desc")
                    // only the latest message of each chat, used as the preview line
                    .addQueryParameter("messages.order", "created_at.desc")
                    .addQueryParameter("messages.limit", "1")
                    .build();

            Request.Builder builder = authedRequest(url, callback);
            if (builder == null) return;

            execute(builder.get().build(), body -> {
                List<ChatHistoryItem> list = new ArrayList<>();
                JSONArray arr = new JSONArray(body);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String preview = "";
                    JSONArray msgs = o.optJSONArray("messages");
                    if (msgs != null && msgs.length() > 0) {
                        preview = msgs.getJSONObject(0).optString("content", "").replace("\n", " ");
                    }
                    list.add(new ChatHistoryItem(
                            o.getString("id"),
                            o.optString("title", "New chat"),
                            preview,
                            formatDate(o.optString("updated_at", ""))));
                }
                return list;
            }, callback);
        }, callback);
    }

    // ------------------------------------------------------------------
    // Resume a chat
    // ------------------------------------------------------------------
    public void loadMessages(String chatId, ResultCallback<List<AiMessage>> callback) {
        withFreshToken(() -> {
            HttpUrl url = HttpUrl.parse(SupabaseConfig.PROJECT_URL + "/rest/v1/messages").newBuilder()
                    .addQueryParameter("chat_id", "eq." + chatId)
                    .addQueryParameter("select", "role,content,created_at")
                    .addQueryParameter("order", "created_at.asc")
                    .build();

            Request.Builder builder = authedRequest(url, callback);
            if (builder == null) return;

            execute(builder.get().build(), body -> {
                List<AiMessage> list = new ArrayList<>();
                JSONArray arr = new JSONArray(body);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    list.add(new AiMessage(
                            o.getString("role"),
                            o.getString("content"),
                            formatTime(o.optString("created_at", ""))));
                }
                return list;
            }, callback);
        }, callback);
    }

    // ------------------------------------------------------------------
    // Send a message and receive the answer as a live stream
    // (Edge Function -> Gemini -> words forwarded -> saved in Supabase)
    // ------------------------------------------------------------------
    /** Pass chatId = null to start a brand new chat. */
    public void sendMessageStream(String chatId, String message, StreamCallback callback) {
        withFreshToken(() -> {
            HttpUrl url = HttpUrl.parse(SupabaseConfig.PROJECT_URL + "/functions/v1/ai-chat");

            Request.Builder builder = authedRequest(url, callback);
            if (builder == null) return;

            String json;
            try {
                JSONObject payload = new JSONObject();
                payload.put("message", message);
                if (!TextUtils.isEmpty(chatId)) {
                    payload.put("chat_id", chatId);
                }
                json = payload.toString();
            } catch (Exception e) {
                postError(callback, "Could not build the request.");
                return;
            }

            Request request = builder
                    .post(RequestBody.create(JSON_TYPE, json))
                    // No compression, so every word is delivered the moment it is sent.
                    .header("Accept-Encoding", "identity")
                    .build();

            CLIENT_SLOW.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Network failure: " + e);
                    postError(callback, networkMessage(e));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response r = response) {
                        ResponseBody responseBody = r.body();
                        if (!r.isSuccessful()) {
                            String body = responseBody != null ? responseBody.string() : "";
                            postError(callback, friendlyError(r.code(), body));
                            return;
                        }
                        if (responseBody == null) {
                            postError(callback, "The AI sent an empty answer.");
                            return;
                        }

                        boolean finished = false;
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) continue;

                            JSONObject event = new JSONObject(line);
                            String type = event.optString("type");

                            if ("delta".equals(type)) {
                                String chunk = event.optString("text", "");
                                mainHandler.post(() -> callback.onText(chunk));
                            } else if ("done".equals(type)) {
                                finished = true;
                                String id = event.optString("chat_id", "");
                                mainHandler.post(() -> callback.onDone(id));
                            } else if ("error".equals(type)) {
                                finished = true;
                                postError(callback,
                                        event.optString("message", "The AI could not answer."));
                            }
                        }

                        if (!finished) {
                            postError(callback, "The connection was interrupted. Please try again.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Stream failure: " + e);
                        postError(callback, networkMessage(e));
                    }
                }
            });
        }, callback);
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------
    public void deleteChat(String chatId, ResultCallback<Boolean> callback) {
        withFreshToken(() -> {
            HttpUrl url = HttpUrl.parse(SupabaseConfig.PROJECT_URL + "/rest/v1/chats").newBuilder()
                    .addQueryParameter("id", "eq." + chatId)
                    .build();

            Request.Builder builder = authedRequest(url, callback);
            if (builder == null) return;

            execute(builder.delete().build(), body -> true, callback);
        }, callback);
    }

    public void deleteAllChats(ResultCallback<Boolean> callback) {
        withFreshToken(() -> {
            // Supabase refuses a DELETE with no filter, so we use one that matches every row.
            // Row Level Security still limits it to the signed-in user's own chats.
            HttpUrl url = HttpUrl.parse(SupabaseConfig.PROJECT_URL + "/rest/v1/chats").newBuilder()
                    .addQueryParameter("id", "not.is.null")
                    .build();

            Request.Builder builder = authedRequest(url, callback);
            if (builder == null) return;

            execute(builder.delete().build(), body -> true, callback);
        }, callback);
    }

    // ------------------------------------------------------------------
    // Token refresh
    // ------------------------------------------------------------------
    /**
     * Runs "proceed" once the saved access token is good to use.
     * If the token is expired (or about to be), it is swapped for a new one first.
     * "proceed" always runs on the main thread.
     */
    private void withFreshToken(Runnable proceed, ErrorSink errors) {
        if (!SessionManager.isAccessTokenExpiring(context)) {
            proceed.run();
            return;
        }

        String refreshToken = SessionManager.getRefreshToken(context);
        if (TextUtils.isEmpty(refreshToken)) {
            postError(errors, "Your session expired. Please log in again.");
            return;
        }

        String json;
        try {
            json = new JSONObject().put("refresh_token", refreshToken).toString();
        } catch (Exception e) {
            postError(errors, "Your session expired. Please log in again.");
            return;
        }

        HttpUrl url = HttpUrl.parse(SupabaseConfig.PROJECT_URL + "/auth/v1/token").newBuilder()
                .addQueryParameter("grant_type", "refresh_token")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(JSON_TYPE, json))
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                postError(errors, networkMessage(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    String body = r.body() != null ? r.body().string() : "";
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "Token refresh failed. HTTP " + r.code() + ": " + body);
                        postError(errors, "Your session expired. Please log in again.");
                        return;
                    }
                    JSONObject o = new JSONObject(body);
                    // Supabase gives a NEW refresh token every time, so save both.
                    SessionManager.saveSession(context,
                            o.getString("access_token"),
                            o.optString("refresh_token", refreshToken));
                    mainHandler.post(proceed);
                } catch (Exception e) {
                    postError(errors, "Your session expired. Please log in again.");
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------
    /** Builds a request with the Supabase headers, or reports "not signed in". */
    private Request.Builder authedRequest(HttpUrl url, ErrorSink errors) {
        String token = SessionManager.getAccessToken(context);
        if (TextUtils.isEmpty(token)) {
            postError(errors, "You are not signed in. Please log in again.");
            return null;
        }
        return new Request.Builder()
                .url(url)
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
    }

    private <T> void execute(Request request, Parser<T> parser, ResultCallback<T> callback) {
        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network failure: " + e);
                postError(callback, networkMessage(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    String body = r.body() != null ? r.body().string() : "";
                    if (!r.isSuccessful()) {
                        postError(callback, friendlyError(r.code(), body));
                        return;
                    }
                    T result = parser.parse(body);
                    mainHandler.post(() -> callback.onSuccess(result));
                } catch (Exception e) {
                    postError(callback, "Something went wrong reading the response.");
                }
            }
        });
    }

    private void postError(ErrorSink errors, String message) {
        mainHandler.post(() -> errors.onError(message));
    }

    private static String networkMessage(Exception e) {
        if (e instanceof SocketTimeoutException) {
            return "The AI is taking too long. Please try again.";
        }
        return "No connection. Check your internet and try again.";
    }

    private String friendlyError(int code, String body) {
        // Full details go to Logcat (filter by "AmplifyChat") so problems are easy to diagnose.
        Log.e(TAG, "HTTP " + code + ": " + body);
        if (code == 401) {
            String detail = "";
            try {
                JSONObject o = new JSONObject(body);
                detail = o.optString("message", o.optString("msg", o.optString("error", "")));
            } catch (Exception ignored) {
                // body was not JSON
            }
            return "Login problem (401)" + (detail.isEmpty() ? "" : ": " + detail);
        }
        try {
            JSONObject o = new JSONObject(body);
            String msg = o.optString("error", o.optString("message", o.optString("msg", "")));
            if (!msg.isEmpty()) return msg;
        } catch (Exception ignored) {
            // body was not JSON
        }
        return "Request failed (" + code + "). Please try again.";
    }

    // ------------------------------------------------------------------
    // Date helpers (Supabase sends UTC timestamps like 2026-10-13T17:45:12.123+00:00)
    // ------------------------------------------------------------------
    private static Date parseIso(String iso) {
        if (iso == null || iso.length() < 19) return null;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso.substring(0, 19));
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatDate(String iso) {
        Date d = parseIso(iso);
        return d == null ? "" : new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(d);
    }

    public static String formatTime(String iso) {
        Date d = parseIso(iso);
        return d == null ? "" : new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
    }
}
