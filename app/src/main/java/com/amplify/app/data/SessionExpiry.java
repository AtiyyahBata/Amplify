package com.amplify.app.data;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.amplify.app.ui.onboarding.WelcomeActivity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles the one situation where someone is signed out without pressing Log out:
 * Supabase says the saved login can't be renewed any more (password changed, account removed).
 *
 * It forgets the login on this phone and sends the person to the Welcome screen, which shows
 * "Please log in again." It only fires once until the Welcome screen has opened.
 */
public final class SessionExpiry {

    private static final String PREFS = "amplify_session_notice";
    private static final String KEY_NOTICE = "show_log_in_again";

    private static final AtomicBoolean FIRED = new AtomicBoolean(false);

    private SessionExpiry() {}

    /** Safe to call from any thread. */
    public static void handle(Context context) {
        if (!FIRED.compareAndSet(false, true)) return;

        final Context app = context.getApplicationContext();

        // Remember to show the message, even if the screen can't open right now
        // (for example while the app is in the background).
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_NOTICE, true).apply();

        SessionManager.clear(app);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Intent intent = new Intent(app, WelcomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                app.startActivity(intent);
            } catch (Exception ignored) {
                // Android may block this in the background. The next app start shows Welcome.
            }
        });
    }

    /** True once if the "Please log in again" message is waiting. */
    public static boolean consumeNotice(Context context) {
        android.content.SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean waiting = prefs.getBoolean(KEY_NOTICE, false);
        if (waiting) prefs.edit().putBoolean(KEY_NOTICE, false).apply();
        return waiting;
    }

    /** Called when the Welcome screen opens, so a later problem can fire again. */
    public static void reset() {
        FIRED.set(false);
    }
}
