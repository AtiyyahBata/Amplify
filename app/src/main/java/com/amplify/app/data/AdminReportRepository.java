package com.amplify.app.data;

import android.content.Context;

import com.amplify.app.models.Report;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the admin side of reports needs from Supabase.
 * Uses SupabaseApi, so the admin's login token is refreshed automatically.
 * All callbacks arrive on the main thread.
 */
public class AdminReportRepository {

    public static final String OPEN = "open";
    public static final String ACTIONED = "actioned";
    public static final String DISMISSED = "dismissed";

    private final SupabaseApi api;

    public AdminReportRepository(Context context) {
        this.api = new SupabaseApi(context.getApplicationContext());
    }

    /** status is OPEN, ACTIONED or DISMISSED. Open shows the longest-waiting first. */
    public void loadReports(String status, SupabaseApi.Callback<List<Report>> callback) {
        String order = OPEN.equals(status) ? "created_at.asc" : "resolved_at.desc";

        // The reports table links to profiles more than once, so "!reporter_id" and
        // "!reported_user_id" say which link to use.
        String path = "/rest/v1/reports"
                + "?select=id,target_type,post_id,reported_user_id,reason,description,"
                + "status,admin_note,created_at,resolved_at,"
                + "reporter:profiles!reporter_id(full_name),"
                + "reported:profiles!reported_user_id(full_name),"
                + "posts(title)"
                + "&status=eq." + status
                + "&order=" + order
                + "&limit=100";

        api.get(path, new SupabaseApi.Callback<String>() {
            @Override
            public void onSuccess(String body) {
                try {
                    callback.onSuccess(parseReports(body));
                } catch (Exception e) {
                    callback.onError("Could not read the reports.");
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private static List<Report> parseReports(String body) throws Exception {
        List<Report> list = new ArrayList<>();
        JSONArray rows = new JSONArray(body);

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);

            JSONObject post = row.optJSONObject("posts");
            String postTitle = post != null ? post.optString("title", "") : "";

            String code = row.optString("reason", "");

            list.add(new Report(
                    row.optString("id", ""),
                    row.optString("target_type", "post"),
                    row.isNull("post_id") ? "" : row.optString("post_id", ""),
                    postTitle,
                    row.optString("reported_user_id", ""),
                    nameOf(row.optJSONObject("reported")),
                    nameOf(row.optJSONObject("reporter")),
                    code,
                    reasonLabel(code),
                    row.isNull("description") ? "" : row.optString("description", ""),
                    row.optString("status", ""),
                    row.isNull("admin_note") ? "" : row.optString("admin_note", ""),
                    VerificationRepository.parseTimestamp(row.optString("created_at", "")),
                    row.isNull("resolved_at") ? 0
                            : VerificationRepository.parseTimestamp(
                            row.optString("resolved_at", ""))));
        }
        return list;
    }

    private static String nameOf(JSONObject profile) {
        if (profile == null) return "Unknown user";
        String name = profile.optString("full_name", "").trim();
        return name.isEmpty() ? "Unknown user" : name;
    }

    /** "misleading" becomes "Misleading content" (the same list the report form uses). */
    public static String reasonLabel(String code) {
        for (int i = 0; i < ReportRepository.REASON_CODES.length; i++) {
            if (ReportRepository.REASON_CODES[i].equals(code)) {
                return ReportRepository.REASON_LABELS[i];
            }
        }
        return code.isEmpty() ? "Unknown" : code;
    }

    // ------------------------------------------------------------------
    // The reported post
    // ------------------------------------------------------------------

    private static final String BUCKET = "Amplify-media";

    /** The parts of a post the admin needs to see. */
    public static class PostDetails {
        public final String title;
        public final String caption;
        public final String mediaType;
        public final String mediaUrl;   // null when the post has no media
        public final String status;

        PostDetails(String title, String caption, String mediaType,
                    String mediaUrl, String status) {
            this.title = title;
            this.caption = caption;
            this.mediaType = mediaType;
            this.mediaUrl = mediaUrl;
            this.status = status;
        }
    }

    /** Loads one post by id. Admins can read posts of any status, including removed. */
    public void loadPost(String postId, SupabaseApi.Callback<PostDetails> callback) {
        String path = "/rest/v1/posts"
                + "?select=title,caption,media_type,media_path,status"
                + "&id=eq." + postId;

        api.get(path, new SupabaseApi.Callback<String>() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONArray rows = new JSONArray(body);
                    if (rows.length() == 0) {
                        callback.onError("This post could not be found. It may have been deleted.");
                        return;
                    }
                    JSONObject p = rows.getJSONObject(0);
                    callback.onSuccess(new PostDetails(
                            p.optString("title", ""),
                            p.optString("caption", ""),
                            p.optString("media_type", ""),
                            buildPublicUrl(p.optString("media_path", "")),
                            p.optString("status", "")));
                } catch (Exception e) {
                    callback.onError("Could not read the post.");
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public static String buildPublicUrl(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;

        String clean = path.startsWith("/") ? path.substring(1) : path;
        if (clean.startsWith(BUCKET + "/")) {
            clean = clean.substring(BUCKET.length() + 1);
        }

        try {
            StringBuilder encoded = new StringBuilder();
            for (String part : clean.split("/")) {
                if (encoded.length() > 0) encoded.append("/");
                encoded.append(URLEncoder.encode(part, "UTF-8").replace("+", "%20"));
            }
            return SupabaseConfig.PROJECT_URL + "/storage/v1/object/public/"
                    + BUCKET + "/" + encoded;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Remove the post or dismiss the report
    // ------------------------------------------------------------------

    /**
     * removePost true: hides the post and closes every open report on it.
     * removePost false: closes only this report. Both happen inside the
     * resolve_report database function in one step.
     */
    public void resolve(String reportId, boolean removePost, String note,
                        SupabaseApi.Callback<Boolean> callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("p_report_id", reportId);
            json.put("p_action", removePost ? "remove_post" : "dismiss");
            json.put("p_note", (note == null || note.trim().isEmpty())
                    ? JSONObject.NULL : note.trim());

            api.rpc("resolve_report", json.toString(),
                    new SupabaseApi.Callback<String>() {
                        @Override
                        public void onSuccess(String body) {
                            callback.onSuccess(Boolean.TRUE);
                        }

                        @Override
                        public void onError(String message) {
                            callback.onError(message);
                        }
                    });
        } catch (Exception e) {
            callback.onError("Something went wrong. Please try again.");
        }
    }
}
