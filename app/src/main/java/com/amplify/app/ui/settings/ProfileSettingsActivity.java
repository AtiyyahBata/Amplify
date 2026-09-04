package com.amplify.app.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

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

        binding.btnLogOut.setOnClickListener(v -> {
            Intent intent = new Intent(this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
