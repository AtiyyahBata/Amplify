package com.amplify.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

/**
 * Gives the AI chat and the private messages the signed-in user's Supabase access token.
 *
 * Your login code must call saveSession(...) after a successful login, passing the
 * "access_token" and "refresh_token" from Supabase's response.
 *
 * Access tokens only last about an hour. ChatRepository and SupabaseApi use the refresh
 * token to get a new one automatically, and call saveSession(...) again with the new pair.
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

        // Some older screens still read the token from "app_prefs". Keep that copy fresh too,
        // so a token renewed anywhere in the app is also used by those screens.
        context.getApplicationContext()
                .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putString("access_token", accessToken).apply();
    }

    /**
     * True when this phone holds a saved login. The app uses this at start-up to skip the
     * Welcome screen. (The access token may be old; it is renewed on the first request.)
     */
    public static boolean isSignedIn(Context context) {
        String refresh = getRefreshToken(context);
        return refresh != null && !refresh.isEmpty();
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

    /**
     * The signed-in user's id (the same id as auth.users.id and profiles.id),
     * or empty string when nobody is signed in. It is read from the "sub" value inside the token.
     */
    public static String getUserId(Context context) {
        String token = getAccessToken(context);
        if (token == null) return "";
        try {
            String sub = readPayload(token).optString("sub", "");
            return sub != null ? sub : "";
        } catch (Exception e) {
            return "";
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
            long exp = readPayload(token).optLong("exp", 0);
            if (exp == 0) return false;
            long nowSeconds = System.currentTimeMillis() / 1000L;
            return exp - nowSeconds < 60;
        } catch (Exception e) {
            return false;
        }
    }

    /** Opens the middle part of the token, which holds "sub" (user id) and "exp" (expiry). */
    private static JSONObject readPayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Not a token");
        byte[] decoded = Base64.decode(parts[1],
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        return new JSONObject(new String(decoded, "UTF-8"));
    }

    /**
     * Call this on logout. Forgets the login on this phone (including the copy LoginActivity
     * keeps in "app_prefs") and removes the message keys from this phone.
     */
    public static void clear(Context context) {
        Context app = context.getApplicationContext();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply();
        app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().remove("access_token").apply();
        ChatKeyManager.clearLocal(app);
    }
}
