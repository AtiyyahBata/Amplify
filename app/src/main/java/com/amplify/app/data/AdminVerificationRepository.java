package com.amplify.app.data;

import android.content.Context;

import com.amplify.app.models.VerifyRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the admin side of verification needs from Supabase:
 *  - loadRequests: the list for a filter (pending, approved or rejected)
 *  - getSignedUrl: a temporary link to view a certificate in the private bucket
 *  - review: approve or reject, through the review_verification database function
 *
 * Uses SupabaseApi, so the admin's login token is refreshed automatically
 * (this needs AdminLogin to save the session, see the small AdminLogin edit).
 * All callbacks arrive on the main thread.
 */
public class AdminVerificationRepository {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";

    private final SupabaseApi api;

    public AdminVerificationRepository(Context context) {
        this.api = new SupabaseApi(context.getApplicationContext());
    }

    // ------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------

    /** status is PENDING, APPROVED or REJECTED. Pending shows the longest-waiting first. */
    public void loadRequests(String status, SupabaseApi.Callback<List<VerifyRequest>> callback) {
        String order = PENDING.equals(status) ? "created_at.asc" : "reviewed_at.desc";

        // profiles!user_id(...) joins the applicant's name and email
        // (the table has two links to profiles, so we say which one).
        String path = "/rest/v1/verification_requests"
                + "?select=id,user_id,reason,university,certificate_path,status,"
                + "admin_note,blocked,created_at,reviewed_at,profiles!user_id(full_name,email,avatar_url)"
                + "&status=eq." + status
                + "&order=" + order
                + "&limit=100";

        api.get(path, new SupabaseApi.Callback<String>() {
            @Override
            public void onSuccess(String body) {
                try {
                    callback.onSuccess(parseRequests(body));
                } catch (Exception e) {
                    callback.onError("Could not read the verification requests.");
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private static List<VerifyRequest> parseRequests(String body) throws Exception {
        List<VerifyRequest> list = new ArrayList<>();
        JSONArray rows = new JSONArray(body);

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);

            JSONObject profile = row.optJSONObject("profiles");
            String name = profile != null ? profile.optString("full_name", "") : "";
            if (name.trim().isEmpty()) name = "Unknown user";
            String email = (profile != null && !profile.isNull("email"))
                    ? profile.optString("email", "") : "";
            String avatarUrl = (profile != null && !profile.isNull("avatar_url"))
                    ? profile.optString("avatar_url", "") : "";

            list.add(new VerifyRequest(
                    row.optString("id", ""),
                    row.optString("user_id", ""),
                    name.trim(),
                    email,
                    avatarUrl,
                    row.optString("university", ""),
                    row.optString("reason", ""),
                    row.optString("certificate_path", ""),
                    row.optString("status", ""),
                    row.isNull("admin_note") ? "" : row.optString("admin_note", ""),
                    row.optBoolean("blocked", false),
                    VerificationRepository.parseTimestamp(row.optString("created_at", "")),
                    row.isNull("reviewed_at") ? 0
                            : VerificationRepository.parseTimestamp(
                            row.optString("reviewed_at", ""))));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Viewing a certificate (the bucket is private)
    // ------------------------------------------------------------------

    /** Gives back a full link that works for 10 minutes. */
    public void getSignedUrl(String certificatePath, SupabaseApi.Callback<String> callback) {
        String path = "/storage/v1/object/sign/"
                + VerificationRepository.BUCKET + "/" + certificatePath;

        api.post(path, "{\"expiresIn\":600}", null, new SupabaseApi.Callback<String>() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONObject json = new JSONObject(body);
                    String signed = json.optString("signedURL",
                            json.optString("signedUrl", ""));
                    if (signed.isEmpty()) {
                        callback.onError("Could not get a link to the certificate.");
                        return;
                    }
                    if (!signed.startsWith("http")) {
                        if (!signed.startsWith("/storage/v1")) {
                            signed = "/storage/v1" + signed;
                        }
                        signed = SupabaseConfig.PROJECT_URL + signed;
                    }
                    callback.onSuccess(signed);
                } catch (Exception e) {
                    callback.onError("Could not read the certificate link.");
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    // ------------------------------------------------------------------
    // Approve or reject
    // ------------------------------------------------------------------

    /**
     * Approve: sets the request to approved and the user to Verified.
     * Reject: sets the request to rejected with an optional note, and can block the user
     * from applying again. Both happen inside the database function in one step.
     */
    public void review(String requestId, boolean approve, String note, boolean block,
                       SupabaseApi.Callback<Boolean> callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("p_request_id", requestId);
            json.put("p_approve", approve);
            json.put("p_note", (note == null || note.trim().isEmpty())
                    ? JSONObject.NULL : note.trim());
            json.put("p_block", block);

            api.rpc("review_verification", json.toString(),
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
