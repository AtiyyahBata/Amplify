package com.amplify.app.ui.onboarding;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Catches the amplify://auth-callback/... links that Supabase sends the browser back to:
 *   /github    after linking GitHub
 *   /signup    after confirming the email used to register
 *   /email     after confirming a new email
 *   /recovery  after tapping the reset-password email link
 *
 * It has no screen of its own. It reads the link, then either opens the set-new-password
 * screen or simply closes so the person lands back on Settings (which refreshes itself).
 */
public class AuthCallbackActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri data = getIntent() == null ? null : getIntent().getData();
        if (data == null) {
            finish();
            return;
        }

        Map<String, String> params = parse(data);
        String path = data.getPath() == null ? "" : data.getPath();
        String error = params.containsKey("error_description")
                ? params.get("error_description") : params.get("error");

        if (path.startsWith("/recovery")) {
            String token = params.get("access_token");
            if (error != null || !"recovery".equals(params.get("type"))
                    || token == null || token.isEmpty()) {
                toast(error != null ? error
                        : "This reset link is not valid. Please request a new one.");
                leave();
            } else {
                Intent intent = new Intent(this, SetNewPasswordActivity.class);
                intent.putExtra(SetNewPasswordActivity.EXTRA_TOKEN, token);
                startActivity(intent);
                finish();
            }
            return;
        }

        // Link from the "Confirm email address" email after registering.
        // The tokens in the link are ignored on purpose: the person logs in with their
        // password so their private message keys can be set up (see LoginActivity).
        if (path.startsWith("/signup")) {
            toast(error != null ? error
                    : "Email confirmed! You can now log in.");
            Intent intent = new Intent(this, error != null
                    ? WelcomeActivity.class : LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        if (error != null) {
            toast(path.startsWith("/github") ? "GitHub link failed: " + error : error);
        } else if (path.startsWith("/email")) {
            toast(params.containsKey("message")
                    ? params.get("message") : "Email change confirmed.");
        }
        leave();
    }

    /** If the app was not open, start it normally. Otherwise just reveal the screen below. */
    private void leave() {
        if (isTaskRoot()) {
            startActivity(new Intent(this, WelcomeActivity.class));
        }
        finish();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** Supabase puts its answer after a "#" (and sometimes after a "?"). Read both. */
    private static Map<String, String> parse(Uri uri) {
        Map<String, String> map = new HashMap<>();
        addAll(map, uri.getEncodedQuery());
        addAll(map, uri.getEncodedFragment());
        return map;
    }

    private static void addAll(Map<String, String> map, String text) {
        if (text == null) return;
        for (String pair : text.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                map.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                        URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (Exception ignored) {
                // skip a part that can't be read
            }
        }
    }
}
