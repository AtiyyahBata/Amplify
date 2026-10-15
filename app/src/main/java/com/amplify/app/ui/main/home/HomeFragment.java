package com.amplify.app.ui.main.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.PostAdapter;
import com.amplify.app.databinding.FragmentHomeBinding;
import com.amplify.app.models.Post;
import com.amplify.app.ui.content.ViewPostActivity;

import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    private static final String SUPABASE_URL =
            "https://pggwokpdcqtigxmcrtxh.supabase.co";

    private static final String SUPABASE_KEY =
            "sb_publishable_yoqGxzzEd6H2iRWyWckd-g_RkHgxhqC";

    private static final String BUCKET = "Amplify-media";

    private final OkHttpClient client = new OkHttpClient();
    private final List<Post> feedPosts = new ArrayList<>();

    private PostAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(
                inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        binding.rvFeed.setLayoutManager(
                new LinearLayoutManager(requireContext()));

        adapter = new PostAdapter(feedPosts, post -> {
            Intent intent = new Intent(
                    requireContext(), ViewPostActivity.class);

            intent.putExtra("post_id", post.getId());
            startActivity(intent);
        });

        binding.rvFeed.setAdapter(adapter);

        loadPosts();
    }

    private String getAccessToken() {
        return requireContext()
                .getSharedPreferences(
                        "app_prefs", Context.MODE_PRIVATE)
                .getString("access_token", "");
    }

    private Request.Builder authenticatedRequest(String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .addHeader("Accept", "application/json");
    }

    private void loadPosts() {
        if (binding == null) return;

        String token = getAccessToken();

        if (token.isEmpty()) {
            showMessage("Please log in to view posts.");
            return;
        }

        binding.tvFeedStatus.setVisibility(View.VISIBLE);
        binding.tvFeedStatus.setText("Loading posts...");
        binding.rvFeed.setVisibility(View.GONE);

        HttpUrl url = HttpUrl.parse(SUPABASE_URL + "/rest/v1/posts");

        if (url == null) {
            showMessage("Invalid Supabase URL.");
            return;
        }

        url = url.newBuilder()
                .addQueryParameter(
                        "select",
                        "id,user_id,title,caption,media_type,media_path,status,created_at")
                .addQueryParameter("status", "eq.published")
                .addQueryParameter("order", "created_at.desc")
                .build();

        Request request = authenticatedRequest(url.toString())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e) {
                postMessage("Unable to connect. Check your internet.");
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response) throws IOException {

                String body;
                int code = response.code();

                try (Response res = response) {
                    body = res.body() != null
                            ? res.body().string() : "";
                }

                if (code < 200 || code >= 300) {
                    postMessage("Couldn't load posts. HTTP " + code);
                    return;
                }

                try {
                    JSONArray posts = new JSONArray(body);

                    if (posts.length() == 0) {
                        postMessage(
                                "No published posts yet. Be the first to share!");
                        return;
                    }

                    fetchProfiles(posts);

                } catch (Exception e) {
                    postMessage("Couldn't read posts from Supabase.");
                }
            }
        });
    }

    private void fetchProfiles(JSONArray rawPosts) {
        Set<String> ids = new LinkedHashSet<>();

        for (int i = 0; i < rawPosts.length(); i++) {
            String userId = rawPosts.optJSONObject(i)
                    .optString("user_id", "");

            if (!userId.isEmpty()) {
                ids.add(userId);
            }
        }

        if (ids.isEmpty()) {
            postMessage("No creator profiles were found.");
            return;
        }

        StringBuilder filter = new StringBuilder("in.(");
        int count = 0;

        for (String id : ids) {
            if (count++ > 0) filter.append(",");
            filter.append(id);
        }

        filter.append(")");

        HttpUrl url = HttpUrl.parse(
                SUPABASE_URL + "/rest/v1/profiles");

        if (url == null) {
            postMessage("Invalid profiles URL.");
            return;
        }

        url = url.newBuilder()
                .addQueryParameter(
                        "select",
                        "id,full_name,user_type,field_of_study")
                .addQueryParameter("id", filter.toString())
                .build();

        Request request = authenticatedRequest(url.toString())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e) {
                postMessage("Unable to load creator profiles.");
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response) throws IOException {

                String body;
                int code = response.code();

                try (Response res = response) {
                    body = res.body() != null
                            ? res.body().string() : "";
                }

                if (code < 200 || code >= 300) {
                    postMessage(
                            "Couldn't load profiles. HTTP " + code);
                    return;
                }

                try {
                    JSONArray profiles = new JSONArray(body);
                    Map<String, JSONObject> profileMap = new HashMap<>();

                    for (int i = 0; i < profiles.length(); i++) {
                        JSONObject profile = profiles.getJSONObject(i);
                        profileMap.put(
                                profile.optString("id"), profile);
                    }

                    List<Post> result = new ArrayList<>();

                    for (int i = 0; i < rawPosts.length(); i++) {
                        JSONObject item = rawPosts.getJSONObject(i);

                        String userId = item.optString("user_id", "");
                        JSONObject profile = profileMap.get(userId);

                        String name = profile == null
                                ? "Amplify creator"
                                : profile.optString(
                                "full_name", "Amplify creator");

                        if (name.trim().isEmpty()) {
                            name = "Amplify creator";
                        }

                        String userType = profile == null
                                ? "" : profile.optString("user_type", "");

                        String field = profile == null
                                ? "" : profile.optString("field_of_study", "");

                        String initials = makeInitials(name);

                        String title = item.optString(
                                "title", "Untitled post");

                        String caption = item.optString("caption", "");
                        String mediaType = item.optString("media_type", "");
                        String mediaPath = item.optString("media_path", "");

                        boolean verified =
                                "Verified".equalsIgnoreCase(userType);

                        Post post = new Post(
                                item.optString("id", ""),
                                userId,
                                name,
                                initials,
                                verified,
                                field,
                                title,
                                caption,
                                mediaType,
                                "community",
                                mediaType,
                                mediaPath
                        );

                        // Directly build and set the public URL correctly
                        String publicUrl = buildPublicUrl(mediaPath);
                        if (publicUrl != null) {
                            post.setMediaUrl(publicUrl);
                        }

                        result.add(post);
                    }

                    displayPosts(result);

                } catch (Exception e) {
                    postMessage("Couldn't process creator profiles.");
                }
            }
        });
    }

    private String buildPublicUrl(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }

        // Clean up any leading slashes or accidental bucket prefixes saved in db
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

        String finalUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET + "/" + encoded;
        android.util.Log.d("AMPLIFY_IMAGE", "Constructed Public URL: " + finalUrl);
        return finalUrl;
    }

    private String makeInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "AC";

        String[] parts = name.trim().split("\\s+");

        if (parts.length == 1) {
            return parts[0].substring(
                    0, Math.min(2, parts[0].length())).toUpperCase();
        }

        return (parts[0].substring(0, 1)
                + parts[parts.length - 1].substring(0, 1))
                .toUpperCase();
    }

    private void displayPosts(List<Post> posts) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (binding == null) return;

            feedPosts.clear();
            feedPosts.addAll(posts);
            adapter.notifyDataSetChanged();

            binding.tvFeedStatus.setVisibility(View.GONE);
            binding.rvFeed.setVisibility(View.VISIBLE);
        });
    }

    private void postMessage(String message) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> showMessage(message));
    }

    private void showMessage(String message) {
        if (binding == null) return;

        binding.tvFeedStatus.setText(message);
        binding.tvFeedStatus.setVisibility(View.VISIBLE);
        binding.rvFeed.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        adapter = null;
    }
}