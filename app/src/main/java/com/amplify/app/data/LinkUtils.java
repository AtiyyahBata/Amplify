package com.amplify.app.data;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.Toast;

import java.util.Locale;

/** Opens profile links in the browser, and styles the GitHub / LinkedIn buttons. */
public final class LinkUtils {

    private LinkUtils() {}

    /**
     * True only for https links on the given site (for example "github.com").
     * Anyone can write their own profile row, so a link is checked again right before it is
     * opened instead of being trusted just because it came from the database.
     */
    public static boolean isSafeLink(String url, String domain) {
        if (url == null || url.isEmpty()) return false;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || !"https".equalsIgnoreCase(scheme)) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals(domain) || host.endsWith("." + domain);
    }

    /** Opens any web address in Chrome (or whatever browser the phone uses). */
    public static void openExternal(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No browser found on this phone.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Linked: normal button, opens the link in the browser.
     * Not linked: greyed out. If whenNotLinked is null the button is also disabled; otherwise
     * a tap runs it (the own profile uses this to jump to Settings).
     */
    public static void bindSocialButton(Button button, String url, String domain,
                                        Runnable whenNotLinked) {
        final boolean linked = isSafeLink(url, domain);
        button.setAlpha(linked ? 1f : 0.45f);
        button.setEnabled(linked || whenNotLinked != null);
        button.setOnClickListener(v -> {
            if (linked) {
                openExternal(v.getContext(), url);
            } else if (whenNotLinked != null) {
                whenNotLinked.run();
            }
        });
    }
}
