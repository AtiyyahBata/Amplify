package com.amplify.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.databinding.ActivityLoginBinding;
import com.amplify.app.ui.main.MainActivity;

/**
 * Log In screen (Use Case 2 in the reference document).
 *
 * This does not call Firebase Authentication yet - tapping "Log in" always
 * succeeds and opens MainActivity, so the rest of the app can be reviewed
 * without a real backend. Swap the button listener for a real sign-in call
 * once Firebase Auth is wired in.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Amplify");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ResetPasswordActivity.class)));

        binding.tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        binding.btnLogIn.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
