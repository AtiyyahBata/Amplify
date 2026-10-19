package com.amplify.app.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Everything the user side of verification needs from Supabase:
 *  - loadMyStatus: reads the user's type and their latest verification request
 *  - submit: uploads the certificate image, then saves the request
 *
 * It reuses SupabaseApi, so the login token is refreshed automatically.
 * All callbacks arrive on the main thread.
 */
public class VerificationRepository {

    private static final String TAG = "AmplifyVerify";

    public static final String BUCKET = "verification-certificates";

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB

    /** What the Get Verified screen needs to know about the signed-in user. */
    public static class MyStatus {
        /** Normal, Verified or Admin (from profiles.user_type). */
        public String userType = "Normal";
        /** "", "pending", "approved" or "rejected" (the latest request). "" = never applied. */
        public String requestStatus = "";
        public String adminNote = "";
        public boolean blocked = false;
        public long createdAtMillis = 0;
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final Context context;
    private final SupabaseApi api;

    public VerificationRepository(Context context) {
        this.context = context.getApplicationContext();
        this.api = new SupabaseApi(this.context);
    }

    // ------------------------------------------------------------------
    // Reading the status
    // ------------------------------------------------------------------

    public void loadMyStatus(SupabaseApi.Callback<MyStatus> callback) {
        final String userId = SessionManager.getUserId(context);
        if (userId.isEmpty()) {
            callback.onError("Please log in again.");
            return;
        }

        final MyStatus status = new MyStatus();

        api.get("/rest/v1/profiles?select=user_type&id=eq." + userId,
                new SupabaseApi.Callback<String>() {
                    @Override
                    public void onSuccess(String body) {
                        try {
                            JSONArray rows = new JSONArray(body);
                            if (rows.length() > 0) {
                                status.userType = rows.getJSONObject(0)
                                        .optString("user_type", "Normal");
                            }
                        } catch (Exception e) {
                            callback.onError("Could not read your account.");
                            return;
                        }

                        // Verified users and admins have no request to look at.
                        if ("Verified".equalsIgnoreCase(status.userType)
                                || "Admin".equalsIgnoreCase(status.userType)) {
                            callback.onSuccess(status);
                            return;
                        }
                        loadLatestRequest(userId, status, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private void loadLatestRequest(String userId, MyStatus status,
                                   SupabaseApi.Callback<MyStatus> callback) {
        String path = "/rest/v1/verification_requests"
                + "?select=status,admin_note,blocked,created_at"
                + "&user_id=eq." + userId
                + "&order=created_at.desc&limit=1";

        api.get(path, new SupabaseApi.Callback<String>() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONArray rows = new JSONArray(body);
                    if (rows.length() > 0) {
                        JSONObject row = rows.getJSONObject(0);
                        status.requestStatus = row.optString("status", "");
                        status.adminNote = row.isNull("admin_note")
                                ? "" : row.optString("admin_note", "");
                        status.blocked = row.optBoolean("blocked", false);
                        status.createdAtMillis =
                                parseTimestamp(row.optString("created_at", ""));
                    }
                } catch (Exception e) {
                    callback.onError("Could not read your verification status.");
                    return;
                }
                callback.onSuccess(status);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    // ------------------------------------------------------------------
    // Submitting a request
    // ------------------------------------------------------------------

    public static boolean isAllowedImage(String mime) {
        return "image/jpeg".equals(mime)
                || "image/png".equals(mime)
                || "image/webp".equals(mime);
    }

    /** Uploads the image to the private bucket, then saves the request row. */
    public void submit(Uri imageUri, String reason, String university,
                       SupabaseApi.Callback<Boolean> callback) {

        final String userId = SessionManager.getUserId(context);
        if (userId.isEmpty()) {
            callback.onError("Please log in again.");
            return;
        }

        POOL.execute(() -> {
            try {
                String mime = context.getContentResolver().getType(imageUri);
                if (!isAllowedImage(mime)) {
                    throw new SupabaseApi.ApiException(0,
                            "Please choose a JPG, PNG or WebP image.");
                }

                byte[] bytes = readBytes(imageUri);
                if (bytes.length == 0) {
                    throw new SupabaseApi.ApiException(0, "That image is empty.");
                }
                if (bytes.length > MAX_BYTES) {
                    throw new SupabaseApi.ApiException(0,
                            "That image is too large. The limit is 10 MB.");
                }

                String path = userId + "/" + UUID.randomUUID() + extensionFor(mime);
                uploadImage(path, bytes, mime);

                JSONObject json = new JSONObject();
                json.put("user_id", userId);
                json.put("reason", reason);
                json.put("university", university);
                json.put("certificate_path", path);

                MAIN.post(() -> api.post(
                        "/rest/v1/verification_requests",
                        json.toString(),
                        "return=minimal",
                        new SupabaseApi.Callback<String>() {
                            @Override
                            public void onSuccess(String result) {
                                callback.onSuccess(Boolean.TRUE);
                            }

                            @Override
                            public void onError(String message) {
                                callback.onError(friendly(message));
                            }
                        }));

            } catch (SupabaseApi.ApiException e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Submit failed: " + e);
                MAIN.post(() -> callback.onError("Something went wrong. Please try again."));
            }
        });
    }

    private void uploadImage(String path, byte[] bytes, String mime)
            throws SupabaseApi.ApiException {

        String token = api.getFreshAccessToken();

        Request request = new Request.Builder()
                .url(SupabaseConfig.PROJECT_URL + "/storage/v1/object/" + BUCKET + "/" + path)
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", mime)
                .header("x-upsert", "false")
                .post(RequestBody.create(bytes, MediaType.parse(mime)))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "Upload HTTP " + response.code() + ": " + body);
                throw new SupabaseApi.ApiException(response.code(),
                        "Could not upload the image (HTTP " + response.code() + ").");
            }
        } catch (IOException e) {
            Log.e(TAG, "Upload network failure: " + e);
            throw new SupabaseApi.ApiException(0,
                    "No connection. Check your internet and try again.");
        }
    }

    private byte[] readBytes(Uri uri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("Could not open the image.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > MAX_BYTES) break; // stop early on huge files
            }
            return output.toByteArray();
        }
    }

    private static String extensionFor(String mime) {
        switch (mime) {
            case "image/png":
                return ".png";
            case "image/webp":
                return ".webp";
            default:
                return ".jpg";
        }
    }

    private static String friendly(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.US);
        if (lower.contains("duplicate")) {
            return "You already have a request waiting for review.";
        }
        if (lower.contains("row-level security")) {
            return "You can't submit a verification request right now.";
        }
        return message;
    }

    // ------------------------------------------------------------------
    // Helpers (the admin screens will reuse parseTimestamp)
    // ------------------------------------------------------------------

    /**
     * Turns a Supabase timestamp such as "2026-10-16T08:12:44.123456+00:00" into
     * milliseconds. Supabase sends times in UTC, so the first 19 characters are enough.
     * Returns 0 if the text cannot be read.
     */
    public static long parseTimestamp(String iso) {
        if (iso == null || iso.length() < 19) return 0;
        try {
            SimpleDateFormat format =
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = format.parse(iso.substring(0, 19));
            return date == null ? 0 : date.getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
