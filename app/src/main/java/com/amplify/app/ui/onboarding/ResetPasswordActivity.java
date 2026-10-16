package com.amplify.app.ui.onboarding;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.AccountRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.ActivityResetPasswordBinding;

/** Reset Password screen (from Log in > Forgot password). Sends a real reset email. */
public class ResetPasswordActivity extends AppCompatActivity {

    private ActivityResetPasswordBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResetPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Amplify");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.btnSendReset.setOnClickListener(v -> sendReset());
    }

    private void sendReset() {
        String email = binding.etEmail.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError("Please enter a valid email");
            binding.etEmail.requestFocus();
            return;
        }

        binding.btnSendReset.setEnabled(false);

        new AccountRepository(this).sendPasswordReset(email, new SupabaseApi.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (isFinishing() || isDestroyed()) return;
                // Same message whether or not the email has an account (keeps accounts private).
                Toast.makeText(ResetPasswordActivity.this,
                        "If that email has an account, a reset link is on its way.",
                        Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                binding.btnSendReset.setEnabled(true);
                Toast.makeText(ResetPasswordActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
