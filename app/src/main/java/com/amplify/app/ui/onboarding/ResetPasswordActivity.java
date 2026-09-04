package com.amplify.app.ui.onboarding;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.databinding.ActivityResetPasswordBinding;

/** Reset Password screen. No real email is sent - this only simulates the flow. */
public class ResetPasswordActivity extends AppCompatActivity {

    private ActivityResetPasswordBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResetPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Amplify");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.btnSendReset.setOnClickListener(v -> {
            Toast.makeText(this, "Reset link sent", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
