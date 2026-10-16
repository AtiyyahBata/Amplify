package com.amplify.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.AccountRepository;
import com.amplify.app.data.SessionManager;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.ActivitySetNewPasswordBinding;

/**
 * Opens from the reset-password email link (through AuthCallbackActivity).
 * The person types a new password twice and it is saved with the one-time token
 * that came with the link.
 */
public class SetNewPasswordActivity extends AppCompatActivity {

    public static final String EXTRA_TOKEN = "recovery_token";

    private ActivitySetNewPasswordBinding binding;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetNewPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Amplify");
        binding.header.btnBack.setOnClickListener(v -> finish());

        token = getIntent().getStringExtra(EXTRA_TOKEN);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "This reset link is not valid.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        binding.btnSavePassword.setOnClickListener(v -> save());
    }

    private void save() {
        String password = binding.etNewPassword.getText().toString();
        String confirm = binding.etConfirmPassword.getText().toString();

        if (password.length() < 6) {
            binding.etNewPassword.setError("Use at least 6 characters");
            binding.etNewPassword.requestFocus();
            return;
        }
        if (!password.equals(confirm)) {
            binding.etConfirmPassword.setError("The passwords don't match");
            binding.etConfirmPassword.requestFocus();
            return;
        }

        binding.btnSavePassword.setEnabled(false);
        binding.btnSavePassword.setText("Saving…");

        new AccountRepository(this).applyRecoveredPassword(token, password,
                new SupabaseApi.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        if (isFinishing() || isDestroyed()) return;
                        boolean signedIn = SessionManager.isSignedIn(SetNewPasswordActivity.this);
                        Toast.makeText(SetNewPasswordActivity.this,
                                signedIn ? "Password updated."
                                        : "Password updated. Log in with your new password.",
                                Toast.LENGTH_LONG).show();
                        done(signedIn);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.btnSavePassword.setEnabled(true);
                        binding.btnSavePassword.setText("Save password");
                        Toast.makeText(SetNewPasswordActivity.this, message,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void done(boolean signedIn) {
        if (signedIn && !isTaskRoot()) {
            finish(); // back to Settings
            return;
        }
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
