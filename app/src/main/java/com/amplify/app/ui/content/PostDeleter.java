package com.amplify.app.ui.content;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.amplify.app.data.SessionManager;
import com.amplify.app.data.SupabaseApi;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;

/**
 * Deletes one of your own posts: asks "are you sure?", removes the row from the
 * database, then (best effort) removes the picture/video from storage.
 *
 * Use it with:
 *   PostDeleter.confirmAndDelete(activity, postId, mediaPath, () -> { ...after delete... });
 */
public final class PostDeleter {

    private static final String TAG = "PostDeleter";
    private static final String BUCKET = "Amplify-media";

    private PostDeleter() {}

    public static void confirmAndDelete(Activity activity, String postId,
                                        String mediaPath, Runnable onDeleted) {
        if (postId == null || postId.trim().isEmpty()) {
            Toast.makeText(activity, "This post can't be deleted right now.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Delete this post?")
                .setMessage("It will be removed for everyone. This can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        deleteNow(activity, postId, mediaPath, onDeleted))
                .show();
    }

    private static void deleteNow(Activity activity, String postId,
                                  String mediaPath, Runnable onDeleted) {
        SupabaseApi api = new SupabaseApi(activity);

        // The extra user_id filter is a safety net: only ever delete your own row.
        String me = SessionManager.getUserId(activity);
        String path = "/rest/v1/posts?id=eq." + Uri.encode(postId)
                + (me != null && !me.isEmpty() ? "&user_id=eq." + Uri.encode(me) : "");

        Toast.makeText(activity, "Deleting...", Toast.LENGTH_SHORT).show();

        // return=representation gives back the deleted rows, so we can tell
        // "deleted" apart from "the database ignored it" (blocked by a policy).
        api.delete(path, "return=representation", new SupabaseApi.Callback<String>() {
            @Override
            public void onSuccess(String body) {
                boolean removed;
                try {
                    String text = body == null ? "" : body.trim();
                    removed = text.isEmpty() || new JSONArray(text).length() > 0;
                } catch (Exception e) {
                    removed = true;
                }

                if (!removed) {
                    Toast.makeText(activity,
                            "Couldn't delete this post. Only verified users can delete their own posts.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                deleteMedia(api, mediaPath);

                if (activity.isFinishing() || activity.isDestroyed()) return;
                Toast.makeText(activity, "Post deleted.", Toast.LENGTH_SHORT).show();
                if (onDeleted != null) onDeleted.run();
            }

            @Override
            public void onError(String message) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Removes the file from storage. Failure here never blocks the delete. */
    private static void deleteMedia(SupabaseApi api, String mediaPath) {
        if (mediaPath == null || mediaPath.trim().isEmpty()) return;

        String p = mediaPath.trim();
        String publicPrefix = "/object/public/" + BUCKET + "/";

        if (p.startsWith("http://") || p.startsWith("https://")) {
            int i = p.indexOf(publicPrefix);
            if (i < 0) return;
            p = Uri.decode(p.substring(i + publicPrefix.length()));
        }
        if (p.startsWith("/")) p = p.substring(1);
        if (p.startsWith(BUCKET + "/")) p = p.substring(BUCKET.length() + 1);
        if (p.isEmpty()) return;

        StringBuilder encoded = new StringBuilder();
        for (String part : p.split("/")) {
            if (encoded.length() > 0) encoded.append("/");
            encoded.append(Uri.encode(part));
        }

        api.delete("/storage/v1/object/" + BUCKET + "/" + encoded,
                new SupabaseApi.Callback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        Log.d(TAG, "Media delete answer: " + result);
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "Media not removed from storage: " + message);
                    }
                });
    }
}
