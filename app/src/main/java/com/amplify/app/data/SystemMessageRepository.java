package com.amplify.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.amplify.app.models.SystemMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lexi's messages (the system_messages table): the latest message and the unread count
 * for the Messages tab. All callbacks arrive on the main thread.
 */
public class SystemMessageRepository {

    private static final String TAG = "AmplifySystemMsg";

    /** What the Lexi row on the Messages tab needs. */
    public static class Summary {
        public boolean hasAny = false;   // false = Lexi has never sent anything
        public String preview = "";      // title of the latest message
        public String timeAgo = "";
        public int unread = 0;
    }

    private interface Job<T> {
        T run() throws Exception;
    }

    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final SupabaseApi api;

    public SystemMessageRepository(Context context) {
        this.api = new SupabaseApi(context.getApplicationContext());
    }

    /** Latest message + unread count, in one go. */
    public void loadSummary(SupabaseApi.Callback<Summary> callback) {
        background(() -> {
            Summary s = new Summary();

            JSONArray rows = new JSONArray(api.requestBlocking("GET",
                    "/rest/v1/system_messages?select=title,created_at"
                            + "&order=created_at.desc&limit=1", null, null));
            if (rows.length() == 0) return s;

            JSONObject row = rows.getJSONObject(0);
            s.hasAny = true;
            s.preview = row.optString("title", "");
            long then = MessagingRepository.parseTime(row.optString("created_at", ""));
            s.timeAgo = MessagingRepository.timeAgo(then, System.currentTimeMillis());
            s.unread = readUnreadCount();
            return s;
        }, callback);
    }

    /** Only the unread number (used for the badge on the Messages tab). */
    public void loadUnreadCount(SupabaseApi.Callback<Integer> callback) {
        background(this::readUnreadCount, callback);
    }

    /** All of my Lexi messages, oldest first (so the newest is at the bottom, like a chat). */
    public void loadAll(SupabaseApi.Callback<List<SystemMessage>> callback) {
        background(() -> {
            String body = api.requestBlocking("GET",
                    "/rest/v1/system_messages"
                            + "?select=id,type,title,body,post_title,post_caption,"
                            + "post_media_type,post_media_path,read_at,created_at"
                            + "&order=created_at.asc&limit=100", null, null);

            List<SystemMessage> list = new ArrayList<>();
            JSONArray rows = new JSONArray(body);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                String mediaPath = text(r, "post_media_path");
                list.add(new SystemMessage(
                        text(r, "id"),
                        text(r, "type"),
                        text(r, "title"),
                        text(r, "body"),
                        text(r, "post_title"),
                        text(r, "post_caption"),
                        text(r, "post_media_type"),
                        mediaPath.isEmpty() ? null
                                : AdminReportRepository.buildPublicUrl(mediaPath),
                        MessagingRepository.parseTime(text(r, "created_at")),
                        r.isNull("read_at")));
            }
            return list;
        }, callback);
    }

    /** Marks all of my Lexi messages as read. Best effort, nobody waits for it. */
    public void markAllRead() {
        POOL.execute(() -> {
            try {
                api.requestBlocking("POST", "/rest/v1/rpc/mark_system_messages_read",
                        "{}", null);
            } catch (Exception e) {
                Log.w(TAG, "Could not mark Lexi messages as read: " + e);
            }
        });
    }

    /** org.json turns a JSON null into the text "null", so check for it first. */
    private static String text(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "");
    }

    private int readUnreadCount() throws Exception {
        String body = api.requestBlocking("POST", "/rest/v1/rpc/system_unread_count",
                "{}", null).trim();
        try {
            return Integer.parseInt(body);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private <T> void background(Job<T> job, SupabaseApi.Callback<T> callback) {
        POOL.execute(() -> {
            try {
                final T result = job.run();
                MAIN.post(() -> callback.onSuccess(result));
            } catch (SupabaseApi.ApiException e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "System message call failed: " + e);
                MAIN.post(() -> callback.onError("Something went wrong. Please try again."));
            }
        });
    }
}
