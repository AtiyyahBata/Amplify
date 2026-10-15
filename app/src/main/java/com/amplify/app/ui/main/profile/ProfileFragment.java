package com.amplify.app.ui.main.profile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amplify.app.databinding.FragmentProfileBinding;
import com.amplify.app.ui.content.ViewPostActivity;
import com.amplify.app.ui.settings.GetVerifiedActivity;
import com.amplify.app.ui.settings.ProfileSettingsActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    private static final String SUPABASE_URL =
            "https://pggwokpdcqtigxmcrtxh.supabase.co";

    private static final String SUPABASE_KEY =
            "sb_publishable_yoqGxzzEd6H2iRWyWckd-g_RkHgxhqC";

    private final OkHttpClient client = new OkHttpClient();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        binding = FragmentProfileBinding.inflate(
                inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        binding.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(
                        requireContext(),
                        ProfileSettingsActivity.class)));

        binding.btnVerificationStatus.setOnClickListener(v ->
                startActivity(new Intent(
                        requireContext(),
                        GetVerifiedActivity.class)));

        binding.cardMyPost.setOnClickListener(v ->
                startActivity(new Intent(
                        requireContext(),
                        ViewPostActivity.class)));

        binding.btnGithub.setOnClickListener(v ->
                Toast.makeText(
                        requireContext(),
                        "GitHub link coming soon",
                        Toast.LENGTH_SHORT).show());

        binding.btnLinkedIn.setOnClickListener(v ->
                Toast.makeText(
                        requireContext(),
                        "LinkedIn link coming soon",
                        Toast.LENGTH_SHORT).show());

        loadUserProfile();
    }

    private void loadUserProfile() {
        Context context = getContext();
        Activity activity = getActivity();
        if (activity == null || context == null) return;

        // Try getting access token from intent extras first, then fallback to SharedPreferences
        String accessToken = activity.getIntent().getStringExtra("access_token");
        if (accessToken == null || accessToken.isEmpty()) {
            accessToken = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getString("access_token", null);
        }

        if (accessToken == null || accessToken.isEmpty()) {
            showMessage("Please log in again to load your profile.");
            return;
        }

        final String token = accessToken;

        // Step 1: Fetch authenticated user ID first, then fetch their profile
        Request userRequest = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/user")
                .get()
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/json")
                .build();

        client.newCall(userRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Activity act = getActivity();
                if (act == null || !isAdded()) return;
                act.runOnUiThread(() -> {
                    if (binding == null) return;
                    showMessage("Could not connect to verify your account.");
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String userBody;
                try (Response res = response) {
                    userBody = res.body() != null ? res.body().string() : "";
                    if (!res.isSuccessful()) {
                        Activity act = getActivity();
                        if (act == null || !isAdded()) return;
                        act.runOnUiThread(() -> {
                            if (binding == null) return;
                            showMessage("Session expired. Please log in again.");
                        });
                        return;
                    }
                }

                try {
                    JSONObject userJson = new JSONObject(userBody);
                    String userId = userJson.optString("id", "");

                    if (userId.isEmpty()) {
                        Activity act = getActivity();
                        if (act == null || !isAdded()) return;
                        act.runOnUiThread(() -> {
                            if (binding == null) return;
                            showMessage("Could not identify your user account.");
                        });
                        return;
                    }

                    // Step 2: Fetch profile specifically for this userId
                    String encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8");
                    String profileUrl = SUPABASE_URL
                            + "/rest/v1/profiles"
                            + "?id=eq." + encodedUserId
                            + "&select=full_name,field_of_study,user_type"
                            + "&limit=1";

                    Request profileRequest = new Request.Builder()
                            .url(profileUrl)
                            .get()
                            .addHeader("apikey", SUPABASE_KEY)
                            .addHeader("Authorization", "Bearer " + token)
                            .addHeader("Accept", "application/json")
                            .build();

                    client.newCall(profileRequest).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Activity act = getActivity();
                            if (act == null || !isAdded()) return;
                            act.runOnUiThread(() -> {
                                if (binding == null) return;
                                showMessage("Could not connect to your profile.");
                            });
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response profileResponse) throws IOException {
                            String responseBody;
                            try (Response res = profileResponse) {
                                responseBody = res.body() != null ? res.body().string() : "";
                            }

                            Activity act = getActivity();
                            if (act == null || !isAdded()) return;

                            if (!profileResponse.isSuccessful()) {
                                act.runOnUiThread(() -> {
                                    if (binding == null) return;
                                    showMessage("Could not load profile. HTTP " + profileResponse.code());
                                });
                                return;
                            }

                            try {
                                JSONArray profiles = new JSONArray(responseBody);

                                if (profiles.length() == 0) {
                                    act.runOnUiThread(() -> {
                                        if (binding == null) return;
                                        showMessage("No profile found for this account.");
                                    });
                                    return;
                                }

                                JSONObject profile = profiles.getJSONObject(0);

                                String fullName = profile.optString("full_name", "User");
                                String fieldOfStudy = profile.optString("field_of_study", "");
                                String userType = profile.optString("user_type", "Normal");

                                if (fullName.trim().isEmpty()) {
                                    fullName = "User";
                                }

                                final String name = fullName.trim();
                                final String study = fieldOfStudy.trim();
                                final String type = userType.trim();

                                act.runOnUiThread(() -> {
                                    if (binding == null) return;

                                    binding.tvProfileName.setText(name);
                                    binding.tvProfileInitials.setText(getInitials(name));

                                    binding.tvFieldOfStudy.setText(
                                            study.isEmpty()
                                                    ? "Field of study not provided"
                                                    : study);

                                    if (type.equalsIgnoreCase("Verified")) {
                                        binding.tvVerificationLabel.setText("Verified");
                                    } else if (type.equalsIgnoreCase("Admin")) {
                                        binding.tvVerificationLabel.setText("Admin");
                                    } else {
                                        binding.tvVerificationLabel.setText("Not verified");
                                    }
                                });

                            } catch (Exception e) {
                                act.runOnUiThread(() -> {
                                    if (binding == null) return;
                                    showMessage("Unable to read profile information.");
                                });
                            }
                        }
                    });

                } catch (Exception e) {
                    Activity act = getActivity();
                    if (act == null || !isAdded()) return;
                    act.runOnUiThread(() -> {
                        if (binding == null) return;
                        showMessage("Unable to process user session.");
                    });
                }
            }
        });
    }

    private String getInitials(String fullName) {

        String[] parts = fullName.trim().split("\\s+");

        if (parts.length == 0 || parts[0].isEmpty()) {
            return "U";
        }

        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }

        return (
                parts[0].substring(0, 1)
                        + parts[parts.length - 1].substring(0, 1)
        ).toUpperCase();
    }

    private void showMessage(String message) {
        Context ctx = getContext();
        if (ctx != null) {
            Toast.makeText(
                    ctx,
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
