package com.amplify.app.ui.content;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.SessionManager;
import com.amplify.app.databinding.ActivityProfileOtherBinding;
import com.amplify.app.databinding.ItemPostBinding;
import com.amplify.app.data.MessagingRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.ui.messaging.ConversationActivity;
import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Profile screen when viewing someone else (Use Case: View a User Profile).
 * Loads the profile and published posts of the user_id passed in the Intent,
 * and shows each post with the same card (item_post) used in the feed.
 */
public class ProfileOtherActivity extends AppCompatActivity {

    private static final String SUPABASE_URL =
            "https://pggwokpdcqtigxmcrtxh.supabase.co";
    private static final String SUPABASE_KEY =
            "sb_publishable_yoqGxzzEd6H2iRWyWckd-g_RkHgxhqC";
    private static final String BUCKET = "Amplify-media";

    private ActivityProfileOtherBinding binding;
    private final OkHttpClient client = new OkHttpClient();

    private String userId;
    private String displayName = "Amplify user";
    private boolean isVerified;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileOtherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Profile");
        binding.header.btnBack.setOnClickListener(v -> finish());

        userId = getIntent().getStringExtra("user_id");
        if (userId == null || userId.trim().isEmpty()) {
            Toast.makeText(this, "Profile not found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // You can't message yourself, so hide the button on your own profile.
        String me = SessionManager.getUserId(this);
        if (me != null && me.equals(userId)) {
            binding.btnMessage.setVisibility(View.GONE);
        }

        binding.btnMessage.setOnClickListener(v -> startChat());

        binding.btnGithub.setOnClickListener(v ->
                Toast.makeText(this, "GitHub link coming soon",
                        Toast.LENGTH_SHORT).show());

        binding.btnLinkedIn.setOnClickListener(v ->
                Toast.makeText(this, "LinkedIn link coming soon",
                        Toast.LENGTH_SHORT).show());

        loadProfile();
        loadPosts();
    }

    /** Finds (or creates) the private chat with this person and opens it. */
    private void startChat() {
        final String otherId = userId;
        final String otherName = displayName;

        binding.btnMessage.setEnabled(false);

        new MessagingRepository(this).startConversation(
                otherId,
                new SupabaseApi.Callback<String>() {
                    @Override
                    public void onSuccess(String conversationId) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.btnMessage.setEnabled(true);
                        ConversationActivity.open(
                                ProfileOtherActivity.this,
                                conversationId,
                                otherId,
                                otherName
                        );
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.btnMessage.setEnabled(true);
                        showToast(message);
                    }
                }
        );
    }

    private String getToken() {
        String token = SessionManager.getAccessToken(this);
        if (token != null && !token.isEmpty()) {
            return token;
        }
        String intentToken = getIntent().getStringExtra("access_token");
        if (intentToken != null && !intentToken.isEmpty()) {
            return intentToken;
        }
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("access_token", "");
    }

    private Request.Builder authRequest(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + getToken())
                .addHeader("Accept", "application/json");
    }

    private void loadProfile() {
        HttpUrl base = HttpUrl.parse(SUPABASE_URL + "/rest/v1/profiles");
        if (base == null) return;

        HttpUrl url = base.newBuilder()
                .addQueryParameter("select", "id,full_name,field_of_study,user_type")
                .addQueryParameter("id", "eq." + userId)
                .build();

        client.newCall(authRequest(url).get().build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showToast("Unable to load profile.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                String body;
                int code;
                try (Response res = response) {
                    code = res.code();
                    body = res.body() != null ? res.body().string() : "";
                }

                if (code < 200 || code >= 300) {
                    showToast("Unable to load profile. HTTP " + code);
                    return;
                }

                try {
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() == 0) {
                        showToast("This profile is unavailable.");
                        return;
                    }

                    JSONObject p = arr.getJSONObject(0);

                    String name = p.optString("full_name", "").trim();
                    if (name.isEmpty()) name = "Amplify user";

                    String study = p.optString("field_of_study", "").trim();
                    String type = p.optString("user_type", "").trim();

                    final String finalName = name;
                    final boolean verified = "Verified".equalsIgnoreCase(type);
                    final String label = verified ? "Verified"
                            : "Admin".equalsIgnoreCase(type) ? "Admin"
                            : "Not verified";

                    displayName = finalName;
                    isVerified = verified;

                    runOnUiThread(() -> {
                        binding.tvProfileName.setText(finalName);
                        binding.tvProfileInitials.setText(initials(finalName));
                        binding.tvFieldOfStudy.setText(study.isEmpty()
                                ? "Field of study not provided" : study);
                        binding.tvVerificationLabel.setText(label);
                    });

                } catch (Exception e) {
                    showToast("Couldn't read profile.");
                }
            }
        });
    }

    private void loadPosts() {
        HttpUrl base = HttpUrl.parse(SUPABASE_URL + "/rest/v1/posts");
        if (base == null) return;

        HttpUrl url = base.newBuilder()
                .addQueryParameter("select",
                        "id,title,caption,media_type,media_path")
                .addQueryParameter("user_id", "eq." + userId)
                .addQueryParameter("status", "eq.published")
                .addQueryParameter("order", "created_at.desc")
                .build();

        client.newCall(authRequest(url).get().build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showToast("Unable to load posts.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                String body;
                int code;
                try (Response res = response) {
                    code = res.code();
                    body = res.body() != null ? res.body().string() : "";
                }

                if (code < 200 || code >= 300) {
                    showToast("Unable to load posts. HTTP " + code);
                    return;
                }

                try {
                    JSONArray arr = new JSONArray(body);
                    runOnUiThread(() -> showPosts(arr));
                } catch (Exception e) {
                    showToast("Couldn't read posts.");
                }
            }
        });
    }

    private void showPosts(JSONArray arr) {
        binding.postsContainer.removeAllViews();

        if (arr.length() == 0) {
            binding.tvNoPosts.setVisibility(View.VISIBLE);
            return;
        }
        binding.tvNoPosts.setVisibility(View.GONE);

        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p == null) continue;

            final String postId = p.optString("id", "");
            String type = p.optString("media_type", "");
            String path = p.optString("media_path", "");

            ItemPostBinding item = ItemPostBinding.inflate(
                    LayoutInflater.from(this), binding.postsContainer, false);

            item.tvAuthorName.setText(displayName);
            item.tvAvatar.setText(initials(displayName));
            item.verifiedContainer.setVisibility(
                    isVerified ? View.VISIBLE : View.GONE);
            item.tvPostTitle.setText(p.optString("title", "Untitled post"));
            item.tvPostBody.setText(p.optString("caption", ""));
            item.tvTagOne.setText(type.isEmpty() ? "community" : type);
            item.tvTagTwo.setText("community");

            if (type.toLowerCase(Locale.ROOT).contains("image") && !path.isEmpty()) {
                item.ivPostMedia.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(SUPABASE_URL + "/storage/v1/object/public/"
                                + BUCKET + "/" + path)
                        .into(item.ivPostMedia);
            }

            item.getRoot().setOnClickListener(v -> {
                Intent intent = new Intent(this, ViewPostActivity.class);
                intent.putExtra("post_id", postId);
                intent.putExtra("access_token", getToken());
                startActivity(intent);
            });

            binding.postsContainer.addView(item.getRoot());
        }
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "U";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length()))
                    .toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1)
                + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(Locale.ROOT);
    }

    private void showToast(String message) {
        runOnUiThread(() ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
}
