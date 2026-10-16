package com.amplify.app.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.AccountRepository;
import com.amplify.app.data.LinkUtils;
import com.amplify.app.data.SessionManager;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.ActivityProfileSettingsBinding;
import com.amplify.app.databinding.DialogChangeEmailBinding;
import com.amplify.app.databinding.DialogLinkUrlBinding;
import com.amplify.app.ui.onboarding.WelcomeActivity;

import java.util.function.Consumer;

/**
 * Profile Settings screen.
 *
 *  - Email: shows the real email. "Change email" asks for the password first.
 *  - Password: "Send password reset link" emails a reset link to the account's email.
 *  - GitHub: Link opens the browser to sign in to GitHub. Unlink removes it.
 *  - LinkedIn: Link asks the person to paste their profile link. Unlink removes it.
 *  - Log out: unchanged.
 *
 * The screen reloads itself every time it comes back to the front (onResume), so it also
 * picks up a GitHub link finished in the browser or a confirmed email change.
 */
public class ProfileSettingsActivity extends AppCompatActivity {

    private ActivityProfileSettingsBinding binding;
    private AccountRepository repo;
    private AccountRepository.Account account; // null until the first load finishes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repo = new AccountRepository(this);

        binding.header.tvHeaderTitle.setText("Settings");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.btnChangeEmail.setOnClickListener(v -> showChangeEmailDialog());
        binding.btnChangePassword.setOnClickListener(v -> sendResetLink());
        binding.btnLinkGithub.setOnClickListener(v -> onGithubClicked());
        binding.btnLinkLinkedin.setOnClickListener(v -> onLinkedinClicked());
        binding.btnLogOut.setOnClickListener(v -> confirmLogOut());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAccount();
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    // ------------------------------------------------------------------
    // Loading and showing the account
    // ------------------------------------------------------------------

    private void loadAccount() {
        repo.load(ui(a -> {
            account = a;
            showAccount();
        }, null));
    }

    private void showAccount() {
        AccountRepository.Account a = account;

        binding.tvEmail.setText(a.email.isEmpty() ? "Unknown" : a.email);
        if (a.pendingEmail.isEmpty()) {
            binding.tvEmailPending.setVisibility(android.view.View.GONE);
        } else {
            binding.tvEmailPending.setText("Waiting for you to confirm " + a.pendingEmail
                    + ". Check your inbox.");
            binding.tvEmailPending.setVisibility(android.view.View.VISIBLE);
        }

        boolean githubLinked = !a.githubIdentityId.isEmpty();
        binding.tvGithubStatus.setText(githubLinked
                ? "Linked" + (a.githubUserName.isEmpty() ? "" : " as " + a.githubUserName)
                : "Not linked");
        binding.btnLinkGithub.setText(githubLinked ? "Unlink" : "Link");

        boolean linkedinLinked = !a.linkedinUrl.isEmpty();
        binding.tvLinkedinStatus.setText(linkedinLinked
                ? "Linked as " + a.linkedinUrl.replace("https://www.linkedin.com/in/", "")
                : "Not linked");
        binding.btnLinkLinkedin.setText(linkedinLinked ? "Unlink" : "Link");
    }

    // ------------------------------------------------------------------
    // Password reset email
    // ------------------------------------------------------------------

    private void sendResetLink() {
        if (account == null || account.email.isEmpty()) {
            toast("Still loading your email. Try again in a moment.");
            return;
        }
        final String email = account.email;
        binding.btnChangePassword.setEnabled(false);
        repo.sendPasswordReset(email, ui(
                ok -> Toast.makeText(this,
                        "Reset link sent to " + email + ". Open it on this phone.",
                        Toast.LENGTH_LONG).show(),
                () -> binding.btnChangePassword.setEnabled(true)));
    }

    // ------------------------------------------------------------------
    // Change email (needs the password)
    // ------------------------------------------------------------------

    private void showChangeEmailDialog() {
        DialogChangeEmailBinding d = DialogChangeEmailBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Change email")
                .setView(d.getRoot())
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        // Set the click listener after show() so a mistake keeps the dialog open.
        dialog.setOnShowListener(x -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String newEmail = d.etNewEmail.getText().toString().trim();
                String password = d.etPassword.getText().toString();

                if (!Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                    d.etNewEmail.setError("Enter a valid email");
                    return;
                }
                if (account != null && newEmail.equalsIgnoreCase(account.email)) {
                    d.etNewEmail.setError("That is already your email");
                    return;
                }
                if (password.isEmpty()) {
                    d.etPassword.setError("Enter your password");
                    return;
                }

                save.setEnabled(false);
                repo.changeEmail(newEmail, password, ui(ok -> {
                    dialog.dismiss();
                    Toast.makeText(this,
                            "Check your inbox and confirm the change. Your email updates after you do.",
                            Toast.LENGTH_LONG).show();
                    loadAccount();
                }, () -> save.setEnabled(true)));
            });
        });
        dialog.show();
    }

    // ------------------------------------------------------------------
    // GitHub
    // ------------------------------------------------------------------

    private void onGithubClicked() {
        if (account == null) {
            toast("Still loading your account. Try again in a moment.");
            return;
        }
        if (account.githubIdentityId.isEmpty()) {
            startGithubLink();
        } else {
            confirmUnlink("GitHub", this::unlinkGithub);
        }
    }

    /** Opens the browser on GitHub's sign-in page. The result comes back through onResume. */
    private void startGithubLink() {
        binding.btnLinkGithub.setEnabled(false);
        repo.startGithubLink(ui(
                url -> LinkUtils.openExternal(this, url),
                () -> binding.btnLinkGithub.setEnabled(true)));
    }

    private void unlinkGithub() {
        binding.btnLinkGithub.setEnabled(false);
        repo.unlinkGithub(account.githubIdentityId, ui(
                ok -> loadAccount(),
                () -> binding.btnLinkGithub.setEnabled(true)));
    }

    // ------------------------------------------------------------------
    // LinkedIn
    // ------------------------------------------------------------------

    private void onLinkedinClicked() {
        if (account == null) {
            toast("Still loading your account. Try again in a moment.");
            return;
        }
        if (account.linkedinUrl.isEmpty()) {
            showLinkedinDialog();
        } else {
            confirmUnlink("LinkedIn", this::removeLinkedin);
        }
    }

    private void showLinkedinDialog() {
        DialogLinkUrlBinding d = DialogLinkUrlBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Link LinkedIn")
                .setView(d.getRoot())
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(x -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String url = AccountRepository.normalizeLinkedin(d.etUrl.getText().toString());
                if (url == null) {
                    d.etUrl.setError("Paste your profile link, like linkedin.com/in/your-name");
                    return;
                }
                save.setEnabled(false);
                repo.saveLinkedin(url, ui(ok -> {
                    dialog.dismiss();
                    loadAccount();
                }, () -> save.setEnabled(true)));
            });
        });
        dialog.show();
    }

    private void removeLinkedin() {
        binding.btnLinkLinkedin.setEnabled(false);
        repo.removeLinkedin(ui(
                ok -> loadAccount(),
                () -> binding.btnLinkLinkedin.setEnabled(true)));
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private void confirmUnlink(String name, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle("Unlink " + name + "?")
                .setMessage("Your profile will stop showing a " + name
                        + " button. You can link it again any time.")
                .setPositiveButton("Unlink", (dialog, which) -> action.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Wraps a result handler: skips it when the screen is already closed, runs "always"
     * (usually re-enabling a button) first, and shows the error as a toast on failure.
     */
    private <T> SupabaseApi.Callback<T> ui(Consumer<T> onOk, Runnable always) {
        return new SupabaseApi.Callback<T>() {
            @Override
            public void onSuccess(T result) {
                if (!alive()) return;
                if (always != null) always.run();
                onOk.accept(result);
            }

            @Override
            public void onError(String message) {
                if (!alive()) return;
                if (always != null) always.run();
                toast(message);
            }
        };
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------
    // Log out (unchanged)
    // ------------------------------------------------------------------

    /** Asks first, so nobody logs out by accident. */
    private void confirmLogOut() {
        new AlertDialog.Builder(this)
                .setTitle("Log out of Amplify?")
                .setMessage("You'll need to log in again to use the app. "
                        + "Your private messages come back when you log in with your password.")
                .setPositiveButton("Log out", (dialog, which) -> logOut())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * The only way to leave a signed-in session. Cancels the login on Supabase (best effort),
     * then forgets the login and the message keys on this phone and returns to Welcome.
     */
    private void logOut() {
        binding.btnLogOut.setEnabled(false);
        binding.btnLogOut.setText("Logging out…");

        final android.content.Context app = getApplicationContext();

        new SupabaseApi(this).signOut(new SupabaseApi.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                finishLogOut(app);
            }

            @Override
            public void onError(String message) {
                finishLogOut(app);
            }
        });
    }

    private void finishLogOut(android.content.Context app) {
        SessionManager.clear(app);

        // CLEAR_TASK removes every screen behind this one, so Back can't return to the app.
        Intent intent = new Intent(app, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        app.startActivity(intent);
        finish();
    }
}
