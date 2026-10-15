
package com.amplify.app.ui.onboarding;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import android.widget.Toast;

import com.amplify.app.data.SessionExpiry;
import com.amplify.app.data.SessionManager;
import com.amplify.app.databinding.ActivityWelcomeBinding;
import com.amplify.app.ui.admin.AdminMainActivity;
import com.amplify.app.ui.main.MainActivity;

public class WelcomeActivity extends AppCompatActivity {

    private ActivityWelcomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Already signed in? Skip this screen and go straight into the app. The only ways
        // to see Welcome again are Log out (Settings) or a login that can no longer be renewed.
        if (SessionManager.isSignedIn(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        SessionExpiry.reset();
        if (SessionExpiry.consumeNotice(this)) {
            Toast.makeText(this, "Please log in again.", Toast.LENGTH_LONG).show();
        }

        // Allow the welcome screen to draw behind the system bars.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Make the status bar and navigation bar transparent.
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // Use dark system-bar icons against the light background.
        WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView()
        ).setAppearanceLightStatusBars(true);

        WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView()
        ).setAppearanceLightNavigationBars(true);

        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Keep the illustration at the top, but protect content
        // at the bottom from overlapping the navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(
                binding.getRoot(),
                (view, windowInsets) -> {

                    Insets systemBars = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            view.getPaddingLeft(),
                            0,
                            view.getPaddingRight(),
                            systemBars.bottom
                    );

                    return windowInsets;
                }
        );

        // Log in
        binding.btnLogIn.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        // Create an account
        binding.btnRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        // Admin portal
        binding.btnAdminLogin.setOnClickListener(v ->
                startActivity(new Intent(this, AdminMainActivity.class)));
    }
}

