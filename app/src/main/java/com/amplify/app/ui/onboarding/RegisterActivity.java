package com.amplify.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.databinding.ActivityRegisterBinding;

/**
 * Register screen (Use Case 1). Tapping "Register" does not create a real
 * Firebase account yet - it just confirms the flow and sends the user to
 * Log In, matching the narrative in Part E of the reference document.
 */
public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Amplify");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.tvGoLogin.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        binding.btnRegister.setOnClickListener(v -> {
            Toast.makeText(this, "Account created — log in to continue", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}
