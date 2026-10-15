package com.amplify.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

/**
 * Gives the AI chat the signed-in user's Supabase access token.
 *
 * Your login code must call saveSession(...) after a successful login, passing the
 * "access_token" and "refresh_token" from Supabase's response.
 *
 * Access tokens only last about an hour. ChatRepository uses the refresh token to get a new
 * one automatically, and calls saveSession(...) again with the new pair.
 */
public final class SessionManager {

    private static final String PREFS = "amplify_session";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";

    private SessionManager() {}

    /** Call this from the login code right after Supabase says the login worked. */
    public static void saveSession(Context context, String accessToken, String refreshToken) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_ACCESS, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .apply();
    }

    /** Returns the current access token, or null when nobody is signed in. */
    public static String getAccessToken(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ACCESS, null);
    }

    /** Returns the refresh token, or null when there is none. */
    public static String getRefreshToken(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_REFRESH, null);
    }

    /** Returns the current user's ID (UUID from the access token), or null if not signed in. */
    public static String getUserId(Context context) {
        String token = getAccessToken(context);
        if (token == null) return null;
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            byte[] decoded = Base64.decode(parts[1],
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            JSONObject payload = new JSONObject(new String(decoded, "UTF-8"));
            String sub = payload.optString("sub", "");
            return sub.isEmpty() ? null : sub;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * True when the access token has expired or will expire within the next minute.
     * Reads the "exp" time stored inside the token. If it cannot be read, returns false
     * and the server gets to decide.
     */
    public static boolean isAccessTokenExpiring(Context context) {
        String token = getAccessToken(context);
        if (token == null) return false;
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return false;
            byte[] decoded = Base64.decode(parts[1],
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            JSONObject payload = new JSONObject(new String(decoded, "UTF-8"));
            long exp = payload.optLong("exp", 0);
            if (exp == 0) return false;
            long nowSeconds = System.currentTimeMillis() / 1000L;
            return exp - nowSeconds < 60;
        } catch (Exception e) {
            return false;
        }
    }

    /** Call this on logout. */
    public static void clear(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply();
    }
}