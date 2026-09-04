package com.amplify.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.databinding.ActivityWelcomeBinding;
import com.amplify.app.ui.admin.AdminMainActivity;

/**
 * The very first screen a user sees. From here they either log in, register,
 * or (as a shortcut for this prototype) jump into the separate Admin panel.
 */
public class WelcomeActivity extends AppCompatActivity {

    private ActivityWelcomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogIn.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        binding.btnRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        binding.btnAdminLogin.setOnClickListener(v ->
                startActivity(new Intent(this, AdminMainActivity.class)));
    }
}
