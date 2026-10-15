package com.amplify.app.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.SessionManager;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.ActivityProfileSettingsBinding;
import com.amplify.app.ui.onboarding.WelcomeActivity;

/**
 * Profile Settings screen (Part G.4). Linking GitHub/LinkedIn here only
 * updates the on-screen label - a real implementation would call the
 * GitHub/LinkedIn APIs referenced in Part H.4 and save the result to the
 * user's Firestore document.
 */
public class ProfileSettingsActivity extends AppCompatActivity {

    private ActivityProfileSettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Settings");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.btnChangePassword.setOnClickListener(v ->
                Toast.makeText(this, "Password change link sent", Toast.LENGTH_SHORT).show());

        binding.btnLinkGithub.setOnClickListener(v -> {
            binding.tvGithubStatus.setText("Linked");
            binding.btnLinkGithub.setText("Linked");
            Toast.makeText(this, "GitHub linked", Toast.LENGTH_SHORT).show();
        });

        binding.btnLinkLinkedin.setOnClickListener(v -> {
            binding.tvLinkedinStatus.setText("Linked");
            binding.btnLinkLinkedin.setText("Linked");
            Toast.makeText(this, "LinkedIn linked", Toast.LENGTH_SHORT).show();
        });

        binding.btnLogOut.setOnClickListener(v -> confirmLogOut());
    }

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
        binding.btnLogOut.setText("Logging out\u2026");

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
