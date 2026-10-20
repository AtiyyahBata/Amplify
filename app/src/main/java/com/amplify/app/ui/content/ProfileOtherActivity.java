package com.amplify.app.ui.content;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.ProfilePostRowAdapter;
import com.amplify.app.data.LinkUtils;
import com.amplify.app.data.MessagingRepository;
import com.amplify.app.data.SessionManager;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.ActivityProfileOtherBinding;
import com.amplify.app.models.Post;
import com.amplify.app.ui.messaging.ConversationActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Profile screen when viewing someone else (Use Case: View a User Profile).
 * Looks like your own profile: avatar and details, links, then a list of
 * the person's published posts (picture on the right). Tapping a tile opens the full post.
 */
public class ProfileOtherActivity extends AppCompatActivity {

    private static final String SUPABASE_URL =
            "https://pggwokpdcqtigxmcrtxh.supabase.co";
    private static final String SUPABASE_KEY =
            "sb_publishable_yoqGxzzEd6H2iRWyWckd-g_RkHgxhqC";
    private static final String BUCKET = "Amplify-media";

    /** How many posts are loaded per page. */
    private static final int PAGE_SIZE = 10;

    private ActivityProfileOtherBinding binding;
    private final OkHttpClient client = new OkHttpClient();

    private String userId;
    private String displayName = "Amplify user";

    // ---- The list of this person's posts ----
    private final List<Post> userPosts = new ArrayList<>();
    private final Set<String> userPostIds = new HashSet<>();
    private ProfilePostRowAdapter postAdapter;
    private SupabaseApi api;

    private boolean loadingPosts = false;
    private boolean endReached = false;

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

        api = new SupabaseApi(this);

        // You can't message yourself, so hide the button on your own profile.
        String me = SessionManager.getUserId(this);
        if (me != null && me.equals(userId)) {
            binding.btnMessage.setVisibility(View.GONE);
        }

        binding.btnMessage.setOnClickListener(v -> startChat());

        // Grey until the profile has loaded and shows which links this person has.
        LinkUtils.bindSocialButton(binding.btnGithub, null, "github.com", null);
        LinkUtils.bindSocialButton(binding.btnLinkedIn, null, "linkedin.com", null);

        setUpPostGrid();
        loadProfile();
        loadPosts();
    }

    // ------------------------------------------------------------------
    // Posts: a list that loads more as you scroll down
    // ------------------------------------------------------------------

    private void setUpPostGrid() {
        postAdapter = new ProfilePostRowAdapter(userPosts, post -> {
            Intent intent = new Intent(this, ViewPostActivity.class);
            intent.putExtra("post_id", post.getId());
            intent.putExtra("access_token", getToken());
            startActivity(intent);
        });

        binding.rvPosts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPosts.setAdapter(postAdapter);

        // When you get close to the bottom of the page, load the next posts.
        binding.scrollProfile.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldX, oldY) -> {
                    View content = v.getChildAt(0);
                    if (content == null) return;

                    int distanceToBottom = content.getBottom() - (v.getHeight() + scrollY);
                    if (distanceToBottom < dp(300)) {
                        loadPosts();
                    }
                });
    }

    /** Loads the next page of this person's published posts. */
    private void loadPosts() {
        if (loadingPosts || endReached) return;

        loadingPosts = true;
        final int offset = userPosts.size();
        binding.progressMore.setVisibility(View.VISIBLE);

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
                if (isFinishing() || isDestroyed()) return;

                try {
                    JSONArray rows = new JSONArray(body);
                    int start = userPosts.size();

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

                        if (userPostIds.add(post.getId())) userPosts.add(post);
                    }

                    int added = userPosts.size() - start;
                    if (added > 0) postAdapter.notifyItemRangeInserted(start, added);

                    endReached = rows.length() < PAGE_SIZE;
                    loadingPosts = false;
                    binding.progressMore.setVisibility(View.GONE);
                    binding.tvNoPosts.setVisibility(
                            userPosts.isEmpty() ? View.VISIBLE : View.GONE);

                } catch (Exception e) {
                    loadingPosts = false;
                    binding.progressMore.setVisibility(View.GONE);
                    showToast("Couldn't read posts.");
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                loadingPosts = false;
                binding.progressMore.setVisibility(View.GONE);
                showToast(message);
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

        return SUPABASE_URL + "/storage/v1/object/public/" + BUCKET + "/" + encoded;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------
    // Message button
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Profile details (name, field, links). Unchanged.
    // ------------------------------------------------------------------

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
                .addQueryParameter("select",
                        "id,full_name,field_of_study,user_type,github_url,linkedin_url")
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

                    // optString turns a database null into the text "null", so check first.
                    String study = p.isNull("field_of_study")
                            ? "" : p.optString("field_of_study", "").trim();
                    final String github = p.isNull("github_url")
                            ? "" : p.optString("github_url", "");
                    final String linkedin = p.isNull("linkedin_url")
                            ? "" : p.optString("linkedin_url", "");
                    String type = p.optString("user_type", "").trim();

                    final String finalName = name;
                    final boolean verified = "Verified".equalsIgnoreCase(type);
                    final String label = verified ? "Verified"
                            : "Admin".equalsIgnoreCase(type) ? "Admin"
                            : "Not verified";

                    displayName = finalName;

                    runOnUiThread(() -> {
                        binding.tvProfileName.setText(finalName);
                        binding.tvProfileInitials.setText(initials(finalName));
                        binding.tvFieldOfStudy.setText(study.isEmpty()
                                ? "Field of study not provided" : study);
                        binding.tvVerificationLabel.setText(label);

                        // Linked: opens the browser. Not linked: grey and disabled.
                        LinkUtils.bindSocialButton(binding.btnGithub, github,
                                "github.com", null);
                        LinkUtils.bindSocialButton(binding.btnLinkedIn, linkedin,
                                "linkedin.com", null);
                    });

                } catch (Exception e) {
                    showToast("Couldn't read profile.");
                }
            }
        });
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
