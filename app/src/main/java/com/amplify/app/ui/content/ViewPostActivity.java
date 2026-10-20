package com.amplify.app.ui.content;

import android.content.Context;
import android.content.Intent;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import com.amplify.app.R;

import com.amplify.app.data.SessionManager;
import com.amplify.app.databinding.ActivityViewPostBinding;
import com.amplify.app.databinding.ItemCommentBinding;
import com.amplify.app.models.Comment;
import com.amplify.app.data.MessagingRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.ui.messaging.ConversationActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ViewPostActivity extends AppCompatActivity {

    private ActivityViewPostBinding binding;

    private static final String SUPABASE_URL =
            "https://pggwokpdcqtigxmcrtxh.supabase.co";

    private static final String SUPABASE_KEY =
            "sb_publishable_yoqGxzzEd6H2iRWyWckd-g_RkHgxhqC";

    private static final String BUCKET = "Amplify-media";

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    private String postId;
    private String creatorUserId;
    private String creatorName = "Amplify creator";
    private String currentUserId;

    private boolean isVerifiedCreator;
    private String mediaUrl;

    private final List<Comment> comments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityViewPostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Post");
        binding.header.btnBack.setOnClickListener(v -> finish());

        postId = getIntent().getStringExtra("post_id");

        if (postId == null || postId.trim().isEmpty()) {
            Toast.makeText(this, "Post ID is missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        binding.tvAvatar.setOnClickListener(v -> openOtherProfile());
        binding.tvAuthorName.setOnClickListener(v -> openOtherProfile());

        binding.btnMessageCreator.setOnClickListener(v -> {
            if (creatorUserId == null || creatorUserId.isEmpty()) {
                Toast.makeText(
                        this,
                        "Creator information is not available yet.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            startChatWithCreator();
        });

        binding.btnPostOptions.setOnClickListener(v -> showPostOptionsMenu(v));

        binding.btnSubmitComment.setOnClickListener(v -> submitComment());

        String token = getAccessToken();

        if (token.isEmpty()) {
            Toast.makeText(this, "Please log in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initUserId();
        loadPost();
    }

    private String getAccessToken() {
        String token = SessionManager.getAccessToken(this);
        if (token != null && !token.isEmpty()) {
            return token;
        }
        String intentToken = getIntent().getStringExtra("access_token");
        if (intentToken != null && !intentToken.isEmpty()) {
            return intentToken;
        }
        return getSharedPreferences(
                "app_prefs",
                Context.MODE_PRIVATE
        ).getString("access_token", "");
    }

    /** Opens the small ⋮ menu under the button. */
    private void showPostOptionsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_post_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_report_post) {
                onReportPostClicked();
                return true;
            }
            return false;
        });
        popup.show();
    }

    /** Checks the post is ready, then (in the next step) opens the report form. */
    private void onReportPostClicked() {
        if (creatorUserId == null || creatorUserId.isEmpty()) {
            Toast.makeText(this, "The post is still loading. Try again in a moment.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (creatorUserId.equals(currentUserId)) {
            Toast.makeText(this, "You can't report your own post.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        ReportPostSheet.show(this, postId);
    }

    private void initUserId() {
        currentUserId = SessionManager.getUserId(this);
        if (currentUserId == null || currentUserId.isEmpty()) {
            loadCurrentUser();
        }
    }

    private Request.Builder authenticatedRequest(String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .addHeader("Accept", "application/json");
    }

    private void loadCurrentUser() {
        Request request = authenticatedRequest(
                SUPABASE_URL + "/auth/v1/user"
        ).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e
            ) {
                // Comment submission will remain unavailable until the user
                // identity can be confirmed.
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response
            ) throws IOException {
                String body;

                try (Response res = response) {
                    body = res.body() != null
                            ? res.body().string()
                            : "";

                    if (!res.isSuccessful()) {
                        return;
                    }
                }

                try {
                    JSONObject user = new JSONObject(body);
                    String id = user.optString("id", "");

                    runOnUiThread(() -> currentUserId = id);

                } catch (Exception ignored) {
                    // The authenticated-user response could not be parsed.
                }
            }
        });
    }

    private void loadPost() {
        HttpUrl baseUrl = HttpUrl.parse(SUPABASE_URL + "/rest/v1/posts");

        if (baseUrl == null) {
            showToast("Invalid Supabase URL.");
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter(
                        "select",
                        "id,user_id,title,caption,media_type,media_path,status,created_at"
                )
                .addQueryParameter("id", "eq." + postId)
                .addQueryParameter("status", "eq.published")
                .build();

        Request request = authenticatedRequest(url.toString())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e
            ) {
                showToast("Unable to connect to Supabase.");
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response
            ) throws IOException {
                String body;
                int code;

                try (Response res = response) {
                    code = res.code();
                    body = res.body() != null
                            ? res.body().string()
                            : "";
                }

                if (code < 200 || code >= 300) {
                    showToast("Unable to load post. HTTP " + code);
                    return;
                }

                try {
                    JSONArray posts = new JSONArray(body);

                    if (posts.length() == 0) {
                        showToast("This post is unavailable.");
                        return;
                    }

                    JSONObject post = posts.getJSONObject(0);

                    creatorUserId = post.optString("user_id", "");

                    String title = post.optString("title", "Untitled post");
                    String caption = post.optString("caption", "");
                    String type = post.optString("media_type", "");
                    String path = post.optString("media_path", "");

                    mediaUrl = buildPublicUrl(path);

                    runOnUiThread(() -> {
                        binding.tvPostTitle.setText(title);
                        binding.tvPostBody.setText(caption);

                        binding.tvTagOne.setText(
                                type.trim().isEmpty() ? "community" : type
                        );
                        binding.tvTagTwo.setText("community");
                        binding.tagContainer.setVisibility(View.VISIBLE);

                        displayMedia(type, mediaUrl);
                    });

                    loadCreatorProfile(creatorUserId);
                    loadComments();

                } catch (Exception e) {
                    showToast("Couldn't process the post.");
                }
            }
        });
    }

    private void loadCreatorProfile(String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }

        HttpUrl baseUrl = HttpUrl.parse(
                SUPABASE_URL + "/rest/v1/profiles"
        );

        if (baseUrl == null) {
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter(
                        "select",
                        "id,full_name,user_type,field_of_study"
                )
                .addQueryParameter("id", "eq." + userId)
                .build();

        Request request = authenticatedRequest(url.toString())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e
            ) {
                showToast("Unable to load creator profile.");
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response
            ) throws IOException {
                String body;
                int code;

                try (Response res = response) {
                    code = res.code();
                    body = res.body() != null
                            ? res.body().string()
                            : "";
                }

                if (code < 200 || code >= 300) {
                    showToast("Unable to load creator. HTTP " + code);
                    return;
                }

                try {
                    JSONArray profiles = new JSONArray(body);

                    if (profiles.length() == 0) {
                        return;
                    }

                    JSONObject profile = profiles.getJSONObject(0);

                    String name = profile.optString(
                            "full_name", "Amplify creator"
                    );

                    if (name.trim().isEmpty()) {
                        name = "Amplify creator";
                    }

                    String userType = profile.optString("user_type", "");

                    boolean verified =
                            "Verified".equalsIgnoreCase(userType);

                    String initials = makeInitials(name);

                    final String finalName = name;

                    // Remember the creator's name so the Message button
                    // can pass it to NewMessageActivity.
                    creatorName = finalName;

                    runOnUiThread(() -> {
                        binding.tvAuthorName.setText(finalName);
                        binding.tvAvatar.setText(initials);

                        isVerifiedCreator = verified;

                        binding.verifiedContainer.setVisibility(
                                verified ? View.VISIBLE : View.GONE
                        );
                    });

                } catch (Exception e) {
                    showToast("Couldn't read creator profile.");
                }
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * Sizes the media box to the picture's own shape: tall for portrait,
     * wide for landscape. Very tall pictures are capped so the comments
     * stay reachable (tap the picture to see it full screen).
     */
    private void fitMediaHeight(int srcWidth, int srcHeight) {
        if (srcWidth <= 0 || srcHeight <= 0) {
            return;
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int availableWidth = dm.widthPixels - dp(40);   // 20dp padding each side
        int maxHeight = Math.round(dm.heightPixels * 0.65f);
        int height = Math.round(availableWidth * (float) srcHeight / srcWidth);
        height = Math.max(dp(120), Math.min(height, maxHeight));

        ViewGroup.LayoutParams params = binding.mediaContainer.getLayoutParams();
        params.height = height;
        binding.mediaContainer.setLayoutParams(params);
    }

    private void displayMedia(String type, String url) {
        binding.ivPostMedia.setVisibility(View.GONE);
        binding.videoPostMedia.setVisibility(View.GONE);
        binding.mediaContainer.setVisibility(View.GONE);

        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String kind = type == null ? "" : type.toLowerCase(Locale.ROOT);

        if (kind.contains("image")) {

            binding.mediaContainer.setVisibility(View.VISIBLE);
            binding.mediaContainer.setBackgroundColor(Color.TRANSPARENT);
            binding.ivPostMedia.setVisibility(View.VISIBLE);

            Glide.with(this)
                    .load(url)
                    .override(1600, 1600)
                    .fitCenter()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                @Nullable GlideException e,
                                Object model,
                                Target<Drawable> target,
                                boolean isFirstResource
                        ) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(
                                Drawable resource,
                                Object model,
                                Target<Drawable> target,
                                DataSource dataSource,
                                boolean isFirstResource
                        ) {
                            fitMediaHeight(
                                    resource.getIntrinsicWidth(),
                                    resource.getIntrinsicHeight()
                            );
                            return false;
                        }
                    })
                    .into(binding.ivPostMedia);

            binding.ivPostMedia.setOnClickListener(v -> openFullSizeImage(url));

        } else if (kind.contains("video")) {

            binding.mediaContainer.setVisibility(View.VISIBLE);
            binding.mediaContainer.setBackgroundColor(Color.BLACK);
            binding.videoPostMedia.setVisibility(View.VISIBLE);

            MediaController controller = new MediaController(this);
            controller.setAnchorView(binding.videoPostMedia);

            binding.videoPostMedia.setMediaController(controller);
            binding.videoPostMedia.setVideoURI(Uri.parse(url));
            binding.videoPostMedia.requestFocus();
            binding.videoPostMedia.setOnPreparedListener(mp -> {
                fitMediaHeight(mp.getVideoWidth(), mp.getVideoHeight());
                mp.setOnVideoSizeChangedListener(
                        (player, w, h) -> fitMediaHeight(w, h)
                );
                mp.start();
            });
        }
    }

    /** Full-screen viewer: pinch to zoom, drag to move, double-tap to zoom in or out. */
    private void openFullSizeImage(String url) {
        Dialog dialog = new Dialog(
                this,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen
        );

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ZoomImageView zoom = new ZoomImageView(this);
        root.addView(zoom, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x99000000);

        TextView close = new TextView(this);
        close.setText("\u2715");
        close.setTextColor(Color.WHITE);
        close.setTextSize(20);
        close.setGravity(Gravity.CENTER);
        close.setBackground(circle);
        close.setOnClickListener(v -> dialog.dismiss());

        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                dp(44), dp(44), Gravity.TOP | Gravity.END
        );
        closeParams.setMargins(0, dp(32), dp(16), 0);
        root.addView(close, closeParams);

        Glide.with(this)
                .load(url)
                .override(2400, 2400)
                .fitCenter()
                .into(zoom);

        dialog.setContentView(root);
        dialog.show();
    }

    private String buildPublicUrl(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }

        String cleanPath = path.startsWith("/")
                ? path.substring(1)
                : path;

        if (cleanPath.startsWith(BUCKET + "/")) {
            cleanPath = cleanPath.substring(BUCKET.length() + 1);
        }

        String[] parts = cleanPath.split("/");
        StringBuilder encoded = new StringBuilder();

        try {
            for (String part : parts) {
                if (encoded.length() > 0) {
                    encoded.append("/");
                }

                encoded.append(
                        URLEncoder.encode(part, "UTF-8").replace("+", "%20")
                );
            }
        } catch (Exception e) {
            return null;
        }

        return SUPABASE_URL
                + "/storage/v1/object/public/"
                + BUCKET
                + "/"
                + encoded;
    }

    private void loadComments() {
        HttpUrl baseUrl = HttpUrl.parse(
                SUPABASE_URL + "/rest/v1/comments"
        );

        if (baseUrl == null) {
            showToast("Invalid comments URL.");
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter(
                        "select",
                        "id,post_id,user_id,body,created_at"
                )
                .addQueryParameter("post_id", "eq." + postId)
                .addQueryParameter("order", "created_at.asc")
                .build();

        Request request = authenticatedRequest(url.toString())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e
            ) {
                showToast("Unable to load comments.");
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response
            ) throws IOException {
                String body;
                int code;

                try (Response res = response) {
                    code = res.code();
                    body = res.body() != null
                            ? res.body().string()
                            : "";
                }

                if (code < 200 || code >= 300) {
                    showToast("Unable to load comments. HTTP " + code);
                    return;
                }

                try {
                    JSONArray rows = new JSONArray(body);
                    List<Comment> loaded = new ArrayList<>();
                    Map<String, String> userNames = new HashMap<>();

                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject item = rows.getJSONObject(i);

                        String userId = item.optString("user_id", "");

                        loaded.add(new Comment(
                                item.optString("id", ""),
                                item.optString("post_id", ""),
                                userId,
                                "Amplify user",
                                item.optString("body", ""),
                                item.optString("created_at", "")
                        ));

                        userNames.put(userId, "Amplify user");
                    }

                    if (userNames.isEmpty()) {
                        displayComments(loaded);
                        return;
                    }

                    fetchCommenterProfiles(loaded, userNames);

                } catch (Exception e) {
                    showToast("Couldn't process comments.");
                }
            }
        });
    }

    private void fetchCommenterProfiles(
            List<Comment> loaded,
            Map<String, String> userNames
    ) {
        StringBuilder ids = new StringBuilder("in.(");
        int count = 0;

        for (String id : userNames.keySet()) {
            if (id.isEmpty()) {
                continue;
            }

            if (count++ > 0) {
                ids.append(",");
            }

            ids.append(id);
        }

        ids.append(")");

        if (count == 0) {
            displayComments(loaded);
            return;
        }

        HttpUrl baseUrl = HttpUrl.parse(
                SUPABASE_URL + "/rest/v1/profiles"
        );

        if (baseUrl == null) {
            displayComments(loaded);
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("select", "id,full_name")
                .addQueryParameter("id", ids.toString())
                .build();

        Request request = authenticatedRequest(url.toString())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e
            ) {
                displayComments(loaded);
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response
            ) throws IOException {
                String body;
                int code;

                try (Response res = response) {
                    code = res.code();
                    body = res.body() != null
                            ? res.body().string()
                            : "";
                }

                if (code >= 200 && code < 300) {
                    try {
                        JSONArray profiles = new JSONArray(body);

                        for (int i = 0; i < profiles.length(); i++) {
                            JSONObject profile = profiles.getJSONObject(i);

                            String id = profile.optString("id", "");
                            String name = profile.optString(
                                    "full_name", "Amplify user"
                            );

                            if (!name.trim().isEmpty()) {
                                userNames.put(id, name);
                            }
                        }
                    } catch (Exception ignored) {
                        // Display comments with fallback names.
                    }
                }

                List<Comment> namedComments = new ArrayList<>();

                for (Comment comment : loaded) {
                    String name = userNames.getOrDefault(
                            comment.getUserId(), "Amplify user"
                    );

                    namedComments.add(new Comment(
                            comment.getId(),
                            comment.getPostId(),
                            comment.getUserId(),
                            name,
                            comment.getBody(),
                            comment.getCreatedAt()
                    ));
                }

                displayComments(namedComments);
            }
        });
    }

    private void displayComments(List<Comment> loaded) {
        runOnUiThread(() -> {
            binding.commentContainer.removeAllViews();
            comments.clear();
            comments.addAll(loaded);

            if (loaded.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("No comments yet. Start the conversation!");
                empty.setTextColor(
                        getResources().getColor(
                                com.amplify.app.R.color.ink_soft
                        )
                );
                empty.setPadding(0, 8, 0, 12);

                binding.commentContainer.addView(empty);
                return;
            }

            for (Comment comment : loaded) {
                ItemCommentBinding itemBinding =
                        ItemCommentBinding.inflate(
                                LayoutInflater.from(this),
                                binding.commentContainer,
                                false
                        );

                itemBinding.tvCommentAuthor.setText(
                        comment.getAuthorName()
                );

                itemBinding.tvCommentTime.setText(
                        formatTimestamp(comment.getCreatedAt())
                );

                itemBinding.tvCommentBody.setText(comment.getBody());

                binding.commentContainer.addView(itemBinding.getRoot());
            }
        });
    }

    private void submitComment() {
        String body = binding.etComment.getText()
                .toString()
                .trim();

        if (body.isEmpty()) {
            binding.etComment.setError("Please enter a comment.");
            return;
        }

        if (body.length() > 2000) {
            binding.etComment.setError(
                    "Comments cannot exceed 2000 characters."
            );
            return;
        }

        if (currentUserId == null || currentUserId.isEmpty()) {
            showToast("Your account is still loading. Try again shortly.");
            return;
        }

        if (getAccessToken().isEmpty()) {
            showToast("Please log in again.");
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("post_id", postId);
            json.put("user_id", currentUserId);
            json.put("body", body);

            RequestBody requestBody =
                    RequestBody.create(json.toString(), JSON);

            Request request = authenticatedRequest(
                    SUPABASE_URL + "/rest/v1/comments"
            )
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .post(requestBody)
                    .build();

            binding.btnSubmitComment.setEnabled(false);

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(
                        @NonNull Call call,
                        @NonNull IOException e
                ) {
                    runOnUiThread(() -> {
                        binding.btnSubmitComment.setEnabled(true);
                        showToast("Unable to submit comment.");
                    });
                }

                @Override
                public void onResponse(
                        @NonNull Call call,
                        @NonNull Response response
                ) throws IOException {
                    int code;

                    try (Response res = response) {
                        code = res.code();

                        if (!res.isSuccessful()) {
                            String error = res.body() != null
                                    ? res.body().string()
                                    : "";

                            android.util.Log.e(
                                    "AMPLIFY_COMMENTS",
                                    "Submit failed: HTTP " + code + " " + error
                            );
                        }
                    }

                    runOnUiThread(() -> {
                        binding.btnSubmitComment.setEnabled(true);

                        if (code >= 200 && code < 300) {
                            binding.etComment.setText("");
                            showToast("Comment posted!");
                            loadComments();
                        } else {
                            showToast(
                                    "Comment failed. HTTP " + code
                                            + ". Check Logcat for details."
                            );
                        }
                    });
                }
            });

        } catch (Exception e) {
            showToast("Couldn't prepare your comment.");
        }
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "";
        }

        try {
            SimpleDateFormat input = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                    Locale.US
            );

            input.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date date;

            try {
                date = input.parse(timestamp);
            } catch (Exception firstError) {
                SimpleDateFormat fallback = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ssXXX",
                        Locale.US
                );

                date = fallback.parse(timestamp);
            }

            if (date == null) {
                return timestamp;
            }

            SimpleDateFormat output = new SimpleDateFormat(
                    "dd MMM yyyy, HH:mm",
                    Locale.getDefault()
            );

            return output.format(date);

        } catch (Exception e) {
            return timestamp;
        }
    }

    private String makeInitials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "AC";
        }

        String[] parts = name.trim().split("\\s+");

        if (parts.length == 1) {
            return parts[0].substring(
                    0, Math.min(2, parts[0].length())
            ).toUpperCase(Locale.ROOT);
        }

        return (
                parts[0].substring(0, 1)
                        + parts[parts.length - 1].substring(0, 1)
        ).toUpperCase(Locale.ROOT);
    }

    private void openOtherProfile() {
        if (creatorUserId == null || creatorUserId.isEmpty()) {
            return;
        }

        Intent intent = new Intent(this, ProfileOtherActivity.class);
        intent.putExtra("user_id", creatorUserId);
        intent.putExtra("access_token", getAccessToken());
        startActivity(intent);
    }

    /**
     * Finds (or creates) the private chat with the post's creator and opens it
     * straight away. No people search in between.
     */
    private void startChatWithCreator() {
        String me = SessionManager.getUserId(this);
        if (me != null && me.equals(creatorUserId)) {
            showToast("This is your own post.");
            return;
        }

        final String otherId = creatorUserId;
        final String otherName = creatorName;

        binding.btnMessageCreator.setEnabled(false);

        new MessagingRepository(this).startConversation(
                otherId,
                new SupabaseApi.Callback<String>() {
                    @Override
                    public void onSuccess(String conversationId) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.btnMessageCreator.setEnabled(true);
                        ConversationActivity.open(
                                ViewPostActivity.this,
                                conversationId,
                                otherId,
                                otherName
                        );
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.btnMessageCreator.setEnabled(true);
                        showToast(message);
                    }
                }
        );
    }

    private void showToast(String message) {
        runOnUiThread(() ->
                Toast.makeText(
                        ViewPostActivity.this,
                        message,
                        Toast.LENGTH_SHORT
                ).show()
        );
    }
}
