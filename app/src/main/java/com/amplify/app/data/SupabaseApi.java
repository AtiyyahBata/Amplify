package com.amplify.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Small helper for talking to Supabase from the private-messaging code.
 * (ChatRepository does the same job for the AI chat and is left untouched.)
 *
 * Two ways to use it:
 *  - get / post / rpc / delete: run in the background and answer on the main thread
 *    through a Callback. Use these from screens.
 *  - requestBlocking: waits for the answer. ONLY call it from a background thread.
 *
 * Before every call it makes sure the login token is still valid and swaps it for a
 * fresh one when it is about to expire.
 */
public class SupabaseApi {

    private static final String TAG = "AmplifyApi";

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(String message);
    }

    /** httpCode 0 means "could not reach the server". */
    public static class ApiException extends Exception {
        public final int httpCode;

        public ApiException(int httpCode, String message) {
            super(message);
            this.httpCode = httpCode;
        }
    }

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Object REFRESH_LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context context;

    public SupabaseApi(Context context) {
        this.context = context.getApplicationContext();
    }

    // ------------------------------------------------------------------
    // Background versions (answer arrives on the main thread)
    // ------------------------------------------------------------------

    /** pathAndQuery example: "/rest/v1/user_keys?select=public_key&user_id=eq.123" */
    public void get(String pathAndQuery, Callback<String> callback) {
        async("GET", pathAndQuery, null, null, callback);
    }

    /** "prefer" can be null, or e.g. "return=representation" to get the saved row back. */
    public void post(String pathAndQuery, String json, String prefer, Callback<String> callback) {
        async("POST", pathAndQuery, json, prefer, callback);
    }

    /** Calls a database function, e.g. rpc("start_conversation", "{\"other_user\":\"...\"}", cb). */
    public void rpc(String function, String json, Callback<String> callback) {
        async("POST", "/rest/v1/rpc/" + function, json == null ? "{}" : json, null, callback);
    }

    public void delete(String pathAndQuery, Callback<String> callback) {
        async("DELETE", pathAndQuery, null, null, callback);
    }

    /** DELETE variant with a PostgREST Prefer header, such as return=representation. */
    public void delete(String pathAndQuery, String prefer, Callback<String> callback) {
        async("DELETE", pathAndQuery, null, prefer, callback);
    }

    private void async(String method, String path, String json, String prefer,
                       Callback<String> callback) {
        POOL.execute(() -> {
            try {
                String result = requestBlocking(method, path, json, prefer);
                MAIN.post(() -> callback.onSuccess(result));
            } catch (ApiException e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            } catch (RuntimeException e) {
                Log.e(TAG, "Unexpected problem: " + e);
                MAIN.post(() -> callback.onError("Something went wrong. Please try again."));
            }
        });
    }

    // ------------------------------------------------------------------
    // The one place a request is actually sent
    // ------------------------------------------------------------------

    /** Waits for the answer and returns the response text. Never call on the main thread. */
    public String requestBlocking(String method, String pathAndQuery, String jsonBody,
                                  String prefer) throws ApiException {
        String token = freshAccessToken();

        Request.Builder builder = new Request.Builder()
                .url(SupabaseConfig.PROJECT_URL + pathAndQuery)
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        if (prefer != null) {
            builder.header("Prefer", prefer);
        }

        switch (method) {
            case "GET":
                builder.get();
                break;
            case "POST":
                builder.post(RequestBody.create(jsonBody == null ? "{}" : jsonBody, JSON_TYPE));
                break;
            case "DELETE":
                builder.delete();
                break;
            case "PATCH":
                builder.patch(RequestBody.create(jsonBody == null ? "{}" : jsonBody, JSON_TYPE));
                break;
            case "PUT":
                builder.put(RequestBody.create(jsonBody == null ? "{}" : jsonBody, JSON_TYPE));
                break;
            default:
                throw new IllegalArgumentException("Unsupported method " + method);
        }

        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(), friendlyError(response.code(), text));
            }
            return text;
        } catch (SocketTimeoutException e) {
            throw new ApiException(0, "The server is taking too long. Please try again.");
        } catch (IOException e) {
            Log.e(TAG, "Network failure: " + e);
            throw new ApiException(0, "No connection. Check your internet and try again.");
        }
    }

    /** Same as anonBlocking, but answers on the main thread. */
    public void anon(String method, String pathAndQuery, String json, String bearer,
                     Callback<String> callback) {
        POOL.execute(() -> {
            try {
                String result = anonBlocking(method, pathAndQuery, json, bearer);
                MAIN.post(() -> callback.onSuccess(result));
            } catch (ApiException e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            } catch (RuntimeException e) {
                Log.e(TAG, "Unexpected problem: " + e);
                MAIN.post(() -> callback.onError("Something went wrong. Please try again."));
            }
        });
    }

    /**
     * Calls Supabase WITHOUT the saved login (apikey only, plus an optional token such as the
     * one from a reset-password link). POST or PUT. Never call on the main thread.
     * Used for: the reset-password email, checking a password, and the reset-link screen.
     */
    public String anonBlocking(String method, String pathAndQuery, String json, String bearer)
            throws ApiException {
        Request.Builder builder = new Request.Builder()
                .url(SupabaseConfig.PROJECT_URL + pathAndQuery)
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Content-Type", "application/json");
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        RequestBody body = RequestBody.create(json == null ? "{}" : json, JSON_TYPE);
        if ("PUT".equals(method)) {
            builder.put(body);
        } else {
            builder.post(body);
        }

        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(), friendlyError(response.code(), text));
            }
            return text;
        } catch (SocketTimeoutException e) {
            throw new ApiException(0, "The server is taking too long. Please try again.");
        } catch (IOException e) {
            Log.e(TAG, "Network failure: " + e);
            throw new ApiException(0, "No connection. Check your internet and try again.");
        }
    }

    /**
     * Logs this phone out on Supabase (only this phone, not other devices). Best effort:
     * the callback always runs on the main thread with onSuccess, even if there was no
     * connection, so the caller can carry on with the local sign out. Uses the saved token as
     * it is and never triggers the "session expired" screen.
     */
    public void signOut(Callback<Boolean> callback) {
        final String token = SessionManager.getAccessToken(context);
        POOL.execute(() -> {
            if (!TextUtils.isEmpty(token)) {
                try {
                    Request request = new Request.Builder()
                            .url(SupabaseConfig.PROJECT_URL + "/auth/v1/logout?scope=local")
                            .header("apikey", SupabaseConfig.ANON_KEY)
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .post(RequestBody.create("{}", JSON_TYPE))
                            .build();
                    OkHttpClient quick = CLIENT.newBuilder()
                            .callTimeout(6, TimeUnit.SECONDS)
                            .build();
                    try (Response response = quick.newCall(request).execute()) {
                        Log.d(TAG, "Server sign out: HTTP " + response.code());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Server sign out skipped: " + e);
                }
            }
            MAIN.post(() -> callback.onSuccess(Boolean.TRUE));
        });
    }

    /** Same as the private refresh below, for the live-updates connection. Background threads only. */
    public String getFreshAccessToken() throws ApiException {
        return freshAccessToken();
    }

    // ------------------------------------------------------------------
    // Login token (refreshes it when it is about to expire)
    // ------------------------------------------------------------------
    private String freshAccessToken() throws ApiException {
        synchronized (REFRESH_LOCK) {
            String token = SessionManager.getAccessToken(context);
            if (TextUtils.isEmpty(token)) {
                throw new ApiException(401, "You are not signed in. Please log in again.");
            }
            if (!SessionManager.isAccessTokenExpiring(context)) {
                return token;
            }

            String refreshToken = SessionManager.getRefreshToken(context);
            if (TextUtils.isEmpty(refreshToken)) {
                SessionExpiry.handle(context);
                throw new ApiException(401, "Your session expired. Please log in again.");
            }

            try {
                String json = new JSONObject().put("refresh_token", refreshToken).toString();
                Request request = new Request.Builder()
                        .url(SupabaseConfig.PROJECT_URL + "/auth/v1/token?grant_type=refresh_token")
                        .header("apikey", SupabaseConfig.ANON_KEY)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(json, JSON_TYPE))
                        .build();

                try (Response response = CLIENT.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        throw new ApiException(401, "Your session expired. Please log in again.");
                    }
                    JSONObject o = new JSONObject(body);
                    String newAccess = o.getString("access_token");
                    // Supabase gives a NEW refresh token every time, so save both.
                    SessionManager.saveSession(context, newAccess,
                            o.optString("refresh_token", refreshToken));
                    return newAccess;
                }
            } catch (IOException e) {
                throw new ApiException(0, "No connection. Check your internet and try again.");
            } catch (JSONException e) {
                throw new ApiException(401, "Your session expired. Please log in again.");
            }
        }
    }

    private static String friendlyError(int code, String body) {
        // Full details go to Logcat (filter by "AmplifyApi").
        Log.e(TAG, "HTTP " + code + ": " + body);
        String message = "";
        try {
            JSONObject o = new JSONObject(body);
            message = o.optString("message",
                    o.optString("msg", o.optString("error_description", o.optString("error", ""))));
        } catch (Exception ignored) {
            // body was not JSON
        }
        if (code == 401) {
            return "Login problem (401)" + (message.isEmpty() ? "" : ": " + message);
        }
        if (!message.isEmpty()) {
            return message;
        }
        return "Request failed (" + code + "). Please try again.";
    }
}
