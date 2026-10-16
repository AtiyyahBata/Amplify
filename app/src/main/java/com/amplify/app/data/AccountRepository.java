package com.amplify.app.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Everything the Settings screen needs from Supabase: the account (email, linked GitHub),
 * the profile links, the password reset email and the email change.
 *
 * Every public method runs in the background and answers on the main thread through
 * SupabaseApi.Callback, the same way SupabaseApi does.
 *
 * Where each thing lives:
 *  - email + GitHub identity ........ Supabase Auth (/auth/v1/user)
 *  - the link people click .......... profiles.github_url / profiles.linkedin_url
 */
public class AccountRepository {

    private static final String TAG = "AmplifyAccount";

    /** Supabase sends the browser back here. AuthCallbackActivity catches it. */
    public static final String REDIRECT_BASE = "amplify://auth-callback";

    /** What the Settings screen shows. Empty string means "not set". */
    public static class Account {
        public String userId = "";
        public String email = "";
        public String pendingEmail = "";      // new email waiting to be confirmed
        public String githubIdentityId = "";  // empty = GitHub is not linked
        public String githubUserName = "";
        public String githubUrl = "";         // from the profile
        public String linkedinUrl = "";       // from the profile
    }

    private interface Job<T> {
        T run() throws Exception;
    }

    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context context;
    private final SupabaseApi api;

    public AccountRepository(Context context) {
        this.context = context.getApplicationContext();
        this.api = new SupabaseApi(this.context);
    }

    // ------------------------------------------------------------------
    // Load
    // ------------------------------------------------------------------

    /** Loads email, GitHub link state and both profile links. Also repairs mismatches. */
    public void load(SupabaseApi.Callback<Account> cb) {
        background(() -> {
            Account a = new Account();

            JSONObject user = new JSONObject(api.requestBlocking("GET", "/auth/v1/user", null, null));
            a.userId = str(user, "id");
            if (a.userId.isEmpty()) a.userId = SessionManager.getUserId(context);
            a.email = str(user, "email");
            a.pendingEmail = str(user, "new_email");

            JSONArray identities = user.optJSONArray("identities");
            if (identities != null) {
                for (int i = 0; i < identities.length(); i++) {
                    JSONObject identity = identities.getJSONObject(i);
                    if ("github".equals(str(identity, "provider"))) {
                        a.githubIdentityId = str(identity, "identity_id");
                        if (a.githubIdentityId.isEmpty()) a.githubIdentityId = str(identity, "id");
                        JSONObject data = identity.optJSONObject("identity_data");
                        if (data != null) {
                            a.githubUserName = str(data, "user_name");
                            if (a.githubUserName.isEmpty()) {
                                a.githubUserName = str(data, "preferred_username");
                            }
                        }
                    }
                }
            }

            JSONArray rows = new JSONArray(api.requestBlocking("GET",
                    "/rest/v1/profiles?select=github_url,linkedin_url&id=eq." + enc(a.userId),
                    null, null));
            if (rows.length() > 0) {
                JSONObject p = rows.getJSONObject(0);
                a.githubUrl = str(p, "github_url");
                a.linkedinUrl = str(p, "linkedin_url");
            }

            // Repair 1: GitHub just got linked (the person came back from the browser),
            // but the profile does not have the link yet.
            if (!a.githubIdentityId.isEmpty() && !a.githubUserName.isEmpty()
                    && a.githubUserName.matches("[A-Za-z0-9-]+")) {
                String expected = "https://github.com/" + a.githubUserName;
                if (!expected.equals(a.githubUrl)) {
                    try {
                        saveColumn(a.userId, "github_url", expected);
                        a.githubUrl = expected;
                    } catch (Exception e) {
                        Log.w(TAG, "Could not save the GitHub link: " + e);
                    }
                }
            }
            // Repair 2: GitHub is no longer linked but the profile still shows a link.
            if (identities != null && a.githubIdentityId.isEmpty() && !a.githubUrl.isEmpty()) {
                try {
                    saveColumn(a.userId, "github_url", null);
                    a.githubUrl = "";
                } catch (Exception e) {
                    Log.w(TAG, "Could not clear the GitHub link: " + e);
                }
            }
            return a;
        }, cb);
    }

    // ------------------------------------------------------------------
    // GitHub (real sign-in through the browser)
    // ------------------------------------------------------------------

    /** Gives back the GitHub sign-in page address. The caller opens it in the browser. */
    public void startGithubLink(SupabaseApi.Callback<String> cb) {
        background(() -> {
            String path = "/auth/v1/user/identities/authorize?provider=github"
                    + "&redirect_to=" + enc(REDIRECT_BASE + "/github")
                    + "&skip_http_redirect=true";
            JSONObject o = new JSONObject(api.requestBlocking("GET", path, null, null));
            String url = str(o, "url");
            if (url.isEmpty()) {
                throw new SupabaseApi.ApiException(0, "Could not start GitHub linking. Please try again.");
            }
            return url;
        }, cb);
    }

    public void unlinkGithub(String identityId, SupabaseApi.Callback<Boolean> cb) {
        background(() -> {
            api.requestBlocking("DELETE", "/auth/v1/user/identities/" + enc(identityId), null, null);
            saveColumn(SessionManager.getUserId(context), "github_url", null);
            return Boolean.TRUE;
        }, cb);
    }

    // ------------------------------------------------------------------
    // LinkedIn (the person pastes their profile link)
    // ------------------------------------------------------------------

    /**
     * Cleans up whatever the person pasted. Returns "https://www.linkedin.com/in/name",
     * or null when it is not a LinkedIn profile link.
     */
    public static String normalizeLinkedin(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty() || s.contains(" ")) return null;
        if (!s.toLowerCase(Locale.ROOT).startsWith("http")) s = "https://" + s;

        Uri uri = Uri.parse(s);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) return null;
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return null;

        host = host.toLowerCase(Locale.ROOT);
        if (!host.equals("linkedin.com") && !host.endsWith(".linkedin.com")) return null;

        List<String> parts = uri.getPathSegments();
        if (parts.size() < 2 || !"in".equalsIgnoreCase(parts.get(0))) return null;

        String slug = parts.get(1);
        if (slug.isEmpty() || !slug.matches("[\\p{L}\\p{N}_\\-.%]+")) return null;
        return "https://www.linkedin.com/in/" + Uri.encode(slug);
    }

    /** url must come from normalizeLinkedin(). */
    public void saveLinkedin(String url, SupabaseApi.Callback<Boolean> cb) {
        background(() -> {
            saveColumn(SessionManager.getUserId(context), "linkedin_url", url);
            return Boolean.TRUE;
        }, cb);
    }

    public void removeLinkedin(SupabaseApi.Callback<Boolean> cb) {
        background(() -> {
            saveColumn(SessionManager.getUserId(context), "linkedin_url", null);
            return Boolean.TRUE;
        }, cb);
    }

    // ------------------------------------------------------------------
    // Password reset (email link) and email change
    // ------------------------------------------------------------------

    /** Sends the reset email. Works when logged out too (Forgot password). */
    public void sendPasswordReset(String email, SupabaseApi.Callback<Boolean> cb) {
        String json;
        try {
            json = new JSONObject().put("email", email).toString();
        } catch (JSONException e) {
            cb.onError("Something went wrong. Please try again.");
            return;
        }
        api.anon("POST",
                "/auth/v1/recover?redirect_to=" + enc(REDIRECT_BASE + "/recovery"),
                json, null,
                new SupabaseApi.Callback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        cb.onSuccess(Boolean.TRUE);
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    /**
     * Sets the new password from the reset email link. recoveryToken is the access_token
     * that came with the link. When the same person is logged in on this phone, the message
     * key backup is re-locked with the new password so old private messages stay readable.
     */
    public void applyRecoveredPassword(String recoveryToken, String newPassword,
                                       SupabaseApi.Callback<Boolean> cb) {
        String json;
        try {
            json = new JSONObject().put("password", newPassword).toString();
        } catch (JSONException e) {
            cb.onError("Something went wrong. Please try again.");
            return;
        }
        api.anon("PUT", "/auth/v1/user", json, recoveryToken,
                new SupabaseApi.Callback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        String me = SessionManager.getUserId(context);
                        if (!me.isEmpty() && me.equals(subOf(recoveryToken))) {
                            ChatKeyManager.relockBackup(context, newPassword,
                                    () -> cb.onSuccess(Boolean.TRUE));
                        } else {
                            cb.onSuccess(Boolean.TRUE);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    /**
     * Checks the password, then asks Supabase to change the email. Supabase emails a
     * confirmation link (to the new address, and to the old one too when "secure email
     * change" is on). The email only changes after the link is opened.
     */
    public void changeEmail(String newEmail, String password, SupabaseApi.Callback<Boolean> cb) {
        background(() -> {
            JSONObject user = new JSONObject(api.requestBlocking("GET", "/auth/v1/user", null, null));
            verifyPassword(str(user, "email"), password);

            String json = new JSONObject().put("email", newEmail).toString();
            api.requestBlocking("PUT",
                    "/auth/v1/user?redirect_to=" + enc(REDIRECT_BASE + "/email"), json, null);
            return Boolean.TRUE;
        }, cb);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Only checks the password; the login it creates is thrown away. */
    private void verifyPassword(String email, String password) throws Exception {
        String json = new JSONObject().put("email", email).put("password", password).toString();
        try {
            api.anonBlocking("POST", "/auth/v1/token?grant_type=password", json, null);
        } catch (SupabaseApi.ApiException e) {
            if (e.httpCode == 400) {
                throw new SupabaseApi.ApiException(400, "That password is not correct.");
            }
            throw e;
        }
    }

    /** Sets one column on the signed-in person's own profile. null clears it. */
    private void saveColumn(String userId, String column, String valueOrNull) throws Exception {
        JSONObject body = new JSONObject();
        body.put(column, valueOrNull == null ? JSONObject.NULL : valueOrNull);
        String result = api.requestBlocking("PATCH",
                "/rest/v1/profiles?id=eq." + enc(userId), body.toString(), "return=representation");
        // Row-level security can answer "OK" while changing nothing. An empty list means that.
        if (new JSONArray(result).length() == 0) {
            throw new SupabaseApi.ApiException(403, "Could not save to your profile.");
        }
    }

    private <T> void background(Job<T> job, SupabaseApi.Callback<T> cb) {
        POOL.execute(() -> {
            try {
                final T result = job.run();
                MAIN.post(() -> cb.onSuccess(result));
            } catch (SupabaseApi.ApiException e) {
                MAIN.post(() -> cb.onError(e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Account call failed: " + e);
                MAIN.post(() -> cb.onError("Something went wrong. Please try again."));
            }
        });
    }

    /** org.json turns a JSON null into the text "null", so check for it first. */
    private static String str(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "");
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    /** The user id ("sub") stored inside a login token. */
    private static String subOf(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            byte[] decoded = Base64.decode(parts[1],
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            return new JSONObject(new String(decoded, "UTF-8")).optString("sub", "");
        } catch (Exception e) {
            return "";
        }
    }
}
