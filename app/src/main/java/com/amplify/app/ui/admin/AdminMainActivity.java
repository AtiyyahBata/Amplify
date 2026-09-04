package com.amplify.app.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.amplify.app.R;
import com.amplify.app.databinding.ActivityAdminMainBinding;
import com.amplify.app.ui.admin.home.AdminHomeFragment;
import com.amplify.app.ui.admin.reports.AdminReportsFragment;
import com.amplify.app.ui.admin.verify.AdminVerifyFragment;
import com.amplify.app.ui.onboarding.WelcomeActivity;

/**
 * Hosts the Admin panel described in Part G.5 of the reference document.
 * This is a separate frontend on the same backend (Part H.5) - here it is
 * simply a separate Activity with its own top tab bar and its own fragments,
 * completely independent of the regular user's bottom navigation.
 */
public class AdminMainActivity extends AppCompatActivity {

    private ActivityAdminMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnExitAdmin.setOnClickListener(v -> {
            Intent intent = new Intent(this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        binding.tabHome.setOnClickListener(v -> selectTab(0));
        binding.tabVerify.setOnClickListener(v -> selectTab(1));
        binding.tabReports.setOnClickListener(v -> selectTab(2));

        selectTab(0);
    }

    private void selectTab(int index) {
        Fragment fragment;
        if (index == 0) {
            fragment = new AdminHomeFragment();
        } else if (index == 1) {
            fragment = new AdminVerifyFragment();
        } else {
            fragment = new AdminReportsFragment();
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.adminFragmentContainer, fragment)
                .commit();

        styleTab(binding.tabHome, index == 0);
        styleTab(binding.tabVerify, index == 1);
        styleTab(binding.tabReports, index == 2);
    }

    private void styleTab(TextView tab, boolean active) {
        if (active) {
            tab.setBackgroundResource(R.drawable.bg_tab_active);
            tab.setTextColor(getColor(R.color.white));
        } else {
            tab.setBackgroundResource(R.drawable.bg_tab_inactive);
            tab.setTextColor(getColor(R.color.muted));
        }
    }
}
