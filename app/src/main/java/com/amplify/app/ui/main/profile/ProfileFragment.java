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
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.ProfilePostRowAdapter;
import com.amplify.app.data.AvatarLoader;
import com.amplify.app.data.LinkUtils;
import com.amplify.app.data.SessionManager;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.data.SupabaseConfig;
import com.amplify.app.databinding.FragmentProfileBinding;
import com.amplify.app.models.Post;
import com.amplify.app.ui.content.ViewPostActivity;
import com.amplify.app.ui.settings.GetVerifiedActivity;
import com.amplify.app.ui.settings.ProfileSettingsActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private static final String BUCKET = "Amplify-media";

    /** How many posts are loaded per page. */
    private static final int PAGE_SIZE = 10;

    private final OkHttpClient client = new OkHttpClient();

    // ---- The list of your own posts ----
    private final List<Post> myPosts = new ArrayList<>();
    private final Set<String> myPostIds = new HashSet<>();
    private ProfilePostRowAdapter postAdapter;
    private SupabaseApi api;

    private boolean loadingPosts = false;
    private boolean endReached = false;

    /** Goes up on every fresh load, so an old answer arriving late gets ignored. */
    private int generation = 0;

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

        api = new SupabaseApi(requireContext());

        binding.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(
                        requireContext(),
                        ProfileSettingsActivity.class)));

        binding.btnVerificationStatus.setOnClickListener(v ->
                startActivity(new Intent(
                        requireContext(),
                        GetVerifiedActivity.class)));

        setUpPostGrid();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload every time the screen is shown, so changes made in Settings
        // (and posts added or removed) show up.
        if (binding != null) {
            loadUserProfile();
            loadMyPosts(true);
        }
    }

    // ------------------------------------------------------------------
    // Your posts: a list that loads more as you scroll down
    // ------------------------------------------------------------------

    private void setUpPostGrid() {
        postAdapter = new ProfilePostRowAdapter(myPosts, post -> {
            Intent intent = new Intent(requireContext(), ViewPostActivity.class);
            intent.putExtra("post_id", post.getId());
            startActivity(intent);
        });

        binding.rvMyPosts.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.rvMyPosts.setAdapter(postAdapter);

        // When you get close to the bottom of the page, load the next posts.
        binding.scrollProfile.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldX, oldY) -> {
                    View content = v.getChildAt(0);
                    if (content == null) return;

                    int distanceToBottom = content.getBottom() - (v.getHeight() + scrollY);
                    if (distanceToBottom < dp(300)) {
                        loadMyPosts(false);
                    }
                });
    }

    /**
     * reset = true: start again from the newest post (used when the screen opens).
     * reset = false: load the next page (used when scrolling down).
     */
    private void loadMyPosts(boolean reset) {
        Context context = getContext();
        if (binding == null || context == null || api == null) return;

        final String userId = SessionManager.getUserId(context);
        if (userId == null || userId.isEmpty()) return;

        if (reset) {
            generation++;
            loadingPosts = false;
            endReached = false;
        }

        if (loadingPosts || endReached) return;

        loadingPosts = true;
        final int myGeneration = generation;
        final int offset = reset ? 0 : myPosts.size();

        // Spinner only when there is nothing on screen yet, or when paging.
        if (!reset || myPosts.isEmpty()) {
            binding.progressMore.setVisibility(View.VISIBLE);
        }

        String path = "/rest/v1/posts"
                + "?select=id,user_id,title,caption,media_type,media_path,status,created_at"
                + "&user_id=eq." + userId
                + "&status=eq.published"
                + "&order=created_at.desc"
                + "&limit=" + PAGE_SIZE
                + "&offset=" + offset;

        api.get(path, new SupabaseApi.Callback<String>() {
            @Override
            public void onSuccess(String body) {
                if (binding == null || myGeneration != generation) return;

                try {
                    JSONArray rows = new JSONArray(body);
                    List<Post> page = new ArrayList<>();

                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject item = rows.optJSONObject(i);
                        if (item == null) continue;

                        String mediaType = item.optString("media_type", "");
                        String mediaPath = item.optString("media_path", "");

                        Post post = new Post(
                                item.optString("id", ""),
                                item.optString("user_id", ""),
                                "", "", false, "",
                                item.optString("title", "Untitled post"),
                                item.optString("caption", ""),
                                mediaType,
                                "community",
                                mediaType,
                                mediaPath);

                        String publicUrl = buildPublicUrl(mediaPath);
                        if (publicUrl != null) post.setMediaUrl(publicUrl);

                        page.add(post);
                    }

                    showPage(page, reset, rows.length());

                } catch (Exception e) {
                    finishLoadingWithMessage("Couldn't read your posts.");
                }
            }

            @Override
            public void onError(String message) {
                if (binding == null || myGeneration != generation) return;
                finishLoadingWithMessage(message);
            }
        });
    }

    private void showPage(List<Post> page, boolean reset, int rawCount) {
        if (reset) {
            myPosts.clear();
            myPostIds.clear();
            for (Post post : page) {
                if (myPostIds.add(post.getId())) myPosts.add(post);
            }
            postAdapter.notifyDataSetChanged();
        } else {
            int start = myPosts.size();
            for (Post post : page) {
                if (myPostIds.add(post.getId())) myPosts.add(post);
            }
            int added = myPosts.size() - start;
            if (added > 0) postAdapter.notifyItemRangeInserted(start, added);
        }

        endReached = rawCount < PAGE_SIZE;
        loadingPosts = false;

        binding.progressMore.setVisibility(View.GONE);
        binding.tvNoMyPosts.setVisibility(myPosts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void finishLoadingWithMessage(String message) {
        loadingPosts = false;
        binding.progressMore.setVisibility(View.GONE);
        binding.tvNoMyPosts.setVisibility(myPosts.isEmpty() ? View.VISIBLE : View.GONE);
        showMessage(message);
    }

    private String buildPublicUrl(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }

        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        if (cleanPath.startsWith(BUCKET + "/")) {
            cleanPath = cleanPath.substring(BUCKET.length() + 1);
        }

        String[] parts = cleanPath.split("/");
        StringBuilder encoded = new StringBuilder();

        for (String part : parts) {
            if (encoded.length() > 0) {
                encoded.append("/");
            }
            try {
                encoded.append(java.net.URLEncoder.encode(part, "UTF-8").replace("+", "%20"));
            } catch (Exception e) {
                encoded.append(part);
            }
        }

        return SupabaseConfig.PROJECT_URL + "/storage/v1/object/public/"
                + BUCKET + "/" + encoded;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------
    // Your profile (name, field, links). Unchanged.
    // ------------------------------------------------------------------

    private void openSettings() {
        Context c = getContext();
        if (c != null) startActivity(new Intent(c, ProfileSettingsActivity.class));
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
                            + "&select=full_name,field_of_study,user_type,github_url,linkedin_url,avatar_url"
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
                                // optString turns a database null into the text "null", so check first.
                                String fieldOfStudy = profile.isNull("field_of_study")
                                        ? "" : profile.optString("field_of_study", "");
                                final String github = profile.isNull("github_url")
                                        ? "" : profile.optString("github_url", "");
                                final String linkedin = profile.isNull("linkedin_url")
                                        ? "" : profile.optString("linkedin_url", "");
                                String userType = profile.optString("user_type", "Normal");
                                final String avatar = profile.isNull("avatar_url")
                                        ? "" : profile.optString("avatar_url", "");

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
                                    AvatarLoader.bind(avatar, binding.cardProfileAvatar,
                                            binding.ivProfileAvatar);

                                    binding.tvFieldOfStudy.setText(
                                            study.isEmpty()
                                                    ? "Field of study not provided"
                                                    : study);

                                    // The "Get verified" button is only for people who are
                                    // not verified yet. Verified users and admins never see it.
                                    boolean alreadyVerified = type.equalsIgnoreCase("Verified")
                                            || type.equalsIgnoreCase("Admin");
                                    binding.btnVerificationStatus.setVisibility(
                                            alreadyVerified ? View.GONE : View.VISIBLE);

                                    if (type.equalsIgnoreCase("Verified")) {
                                        binding.tvVerificationLabel.setText("Verified");
                                    } else if (type.equalsIgnoreCase("Admin")) {
                                        binding.tvVerificationLabel.setText("Admin");
                                    } else {
                                        binding.tvVerificationLabel.setText("Not verified");
                                    }

                                    // Linked: opens the browser. Not linked: grey, and a tap
                                    // opens Settings so you can link it.
                                    LinkUtils.bindSocialButton(binding.btnGithub, github,
                                            "github.com",
                                            () -> ProfileFragment.this.openSettings());
                                    LinkUtils.bindSocialButton(binding.btnLinkedIn, linkedin,
                                            "linkedin.com",
                                            () -> ProfileFragment.this.openSettings());
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
        // Makes any answer that is still on its way get ignored.
        generation++;
        loadingPosts = false;
        postAdapter = null;
        binding = null;
    }
}
