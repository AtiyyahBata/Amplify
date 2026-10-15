package com.amplify.app.ui.admin;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.amplify.app.databinding.ActivityAdminMainBinding;
import com.amplify.app.ui.admin.home.AdminHomeFragment;
import com.amplify.app.ui.admin.verify.AdminVerifyFragment;
import com.amplify.app.ui.admin.reports.AdminReportsFragment;
import com.amplify.app.ui.onboarding.WelcomeActivity;

public class AdminMainActivity extends AppCompatActivity {

    private ActivityAdminMainBinding binding;

    private String accessToken;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAdminMainBinding.inflate(
                getLayoutInflater()
        );

        setContentView(binding.getRoot());

        // Receive the token from AdminLogin.
        accessToken = getIntent().getStringExtra("access_token");

        // Return to the welcome screen if no token was provided.
        if (accessToken == null || accessToken.trim().isEmpty()) {
            goToWelcome();
            return;
        }

        // Set up the dashboard tabs.
        binding.tabHome.setOnClickListener(v -> selectTab(0));
        binding.tabVerify.setOnClickListener(v -> selectTab(1));
        binding.tabReports.setOnClickListener(v -> selectTab(2));

        // Exit the admin dashboard.
        binding.btnExitAdmin.setOnClickListener(v -> goToWelcome());

        // Show Home when the dashboard opens.
        selectTab(0);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void selectTab(int tabIndex) {

        Fragment fragment;

        switch (tabIndex) {
            case 1:
                fragment = new AdminVerifyFragment();
                break;

            case 2:
                fragment = new AdminReportsFragment();
                break;

            case 0:
            default:
                fragment = new AdminHomeFragment();
                break;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        binding.adminFragmentContainer.getId(),
                        fragment
                )
                .commit();

        // Update the selected tab's visual state.
        updateTabAppearance(tabIndex);
    }

    private void updateTabAppearance(int selectedTab) {

        // Reset all tab labels to their normal state.
        binding.tabHome.setAlpha(0.65f);
        binding.tabVerify.setAlpha(0.65f);
        binding.tabReports.setAlpha(0.65f);

        // Highlight the selected tab.
        switch (selectedTab) {
            case 0:
                binding.tabHome.setAlpha(1.0f);
                break;

            case 1:
                binding.tabVerify.setAlpha(1.0f);
                break;

            case 2:
                binding.tabReports.setAlpha(1.0f);
                break;
        }
    }

    private void goToWelcome() {

        Intent intent = new Intent(
                AdminMainActivity.this,
                WelcomeActivity.class
        );

        // Prevent returning to the dashboard using the Back button.
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
