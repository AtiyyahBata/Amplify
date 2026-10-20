package com.amplify.app.ui.admin.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amplify.app.databinding.FragmentAdminHomeBinding;
import com.amplify.app.ui.admin.AdminMainActivity;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AdminHomeFragment extends Fragment {

    private FragmentAdminHomeBinding binding;

    private static final String SUPABASE_URL =
            "https://pggwokpdcqtigxmcrtxh.supabase.co";

    // Replace this with the same Supabase publishable key
    // already used by your registration and login code.
    private static final String SUPABASE_KEY =
            "sb_publishable_yoqGxzzEd6H2iRWyWckd-g_RkHgxhqC";

    private final OkHttpClient client = new OkHttpClient();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        binding = FragmentAdminHomeBinding.inflate(
                inflater, container, false);

        // Load live statistics from Supabase.
        loadDashboardStatistics();

        return binding.getRoot();
    }

    private void loadDashboardStatistics() {

        if (!(requireActivity() instanceof AdminMainActivity)) {
            showError("Unable to access admin session.");
            return;
        }

        AdminMainActivity activity =
                (AdminMainActivity) requireActivity();

        String accessToken = activity.getAccessToken();

        if (accessToken == null || accessToken.trim().isEmpty()) {
            showError("Admin token is missing. Please log in again.");
            return;
        }

        // Load total profiles.
        loadUserCount(
                accessToken,
                "",
                binding.tvTotalUsers
        );

        // Load normal users.
        loadUserCount(
                accessToken,
                "&user_type=eq.Normal",
                binding.tvNormalUsers
        );

        // Load verified users.
        loadUserCount(
                accessToken,
                "&user_type=eq.Verified",
                binding.tvVerifiedUsers
        );

        // Load pending verification requests.
        loadCount(
                accessToken,
                "verification_requests",
                "&status=eq.pending",
                binding.tvPendingVerifications
        );

        // Load open reports.
        loadCount(
                accessToken,
                "reports",
                "&status=eq.open",
                binding.tvOpenReports
        );

        // Posts today will be connected in a later step.
        binding.tvPostsToday.setText("—");
    }

    private void loadUserCount(
            String accessToken,
            String filter,
            TextView target) {

        loadCount(accessToken, "profiles", filter, target);
    }

    private void loadCount(
            String accessToken,
            String table,
            String filter,
            TextView target) {

        String url = SUPABASE_URL
                + "/rest/v1/" + table + "?select=id"
                + filter;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Prefer", "count=exact")
                .addHeader("Range", "0-0")
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e) {

                updateText(target, "Error");
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response) {

                try (Response res = response) {

                    if (!res.isSuccessful()) {
                        updateText(target, "Error");
                        android.util.Log.e(
                                "AdminDashboard",
                                "Supabase request failed: HTTP "
                                        + res.code()
                        );
                        return;
                    }

                    String contentRange =
                            res.header("Content-Range");

                    if (contentRange == null
                            || !contentRange.contains("/")) {
                        updateText(target, "—");
                        return;
                    }

                    String countValue =
                            contentRange.substring(
                                    contentRange.lastIndexOf("/") + 1
                            );

                    updateText(target, countValue);

                }
            }
        });
    }

    private void updateText(TextView target, String value) {

        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(() -> {
            if (binding != null) {
                target.setText(value);
            }
        });
    }

    private void showError(String message) {

        if (isAdded()) {
            Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
