package com.amplify.app.data;

import android.content.Context;

import org.json.JSONObject;

/**
 * Sends reports to the Supabase "reports" table.
 *
 * Only the post, the reason and the description are sent. Who is reporting, who wrote the
 * post and the "open" status are filled in by the database, so they cannot be faked.
 * The database also refuses reports on your own post and a second open report on the
 * same post. All callbacks arrive on the main thread.
 */
public class ReportRepository {

    /** What is saved in the database (matches the check on reports.reason). */
    public static final String[] REASON_CODES = {
            "spam", "misleading", "inappropriate", "harassment", "other"
    };

    /** What the person sees in the dropdown (same order as REASON_CODES). */
    public static final String[] REASON_LABELS = {
            "Spam", "Misleading content", "Inappropriate content", "Harassment", "Other"
    };

    private final SupabaseApi api;

    public ReportRepository(Context context) {
        this.api = new SupabaseApi(context.getApplicationContext());
    }

    public void submitPostReport(String postId, String reasonCode, String description,
                                 SupabaseApi.Callback<Boolean> callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("target_type", "post");
            json.put("post_id", postId);
            json.put("reason", reasonCode);
            json.put("description", (description == null || description.trim().isEmpty())
                    ? JSONObject.NULL : description.trim());

            api.post("/rest/v1/reports", json.toString(), null,
                    new SupabaseApi.Callback<String>() {
                        @Override
                        public void onSuccess(String body) {
                            callback.onSuccess(Boolean.TRUE);
                        }

                        @Override
                        public void onError(String message) {
                            callback.onError(friendly(message));
                        }
                    });
        } catch (Exception e) {
            callback.onError("Something went wrong. Please try again.");
        }
    }

    /** Turns the database's technical messages into something a person can read. */
    private static String friendly(String message) {
        if (message == null) return "Something went wrong. Please try again.";
        if (message.contains("duplicate key")) {
            return "You have already reported this post. The admins will review it.";
        }
        if (message.contains("own content")) {
            return "You can't report your own post.";
        }
        return message;
    }
}
