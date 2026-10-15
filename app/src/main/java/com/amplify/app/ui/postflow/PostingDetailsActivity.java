
package com.amplify.app.ui.postflow;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.amplify.app.databinding.ActivityPostingDetailsBinding;
import com.amplify.app.ui.main.MainActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PostingDetailsActivity extends AppCompatActivity {

    private ActivityPostingDetailsBinding binding;

    private static final String SUPABASE_URL =
            "https://pggwokpdcqtigxmcrtxh.supabase.co";

    // Replace this with your publishable key from THIS Supabase project.

    private static final String SUPABASE_KEY =
            "sb_publishable_yoqGxzzEd6H2iRWyWckd-g_RkHgxhqC";

    private static final String STORAGE_BUCKET = "Amplify-media";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private String accessToken;
    private String postTitle;
    private String postCaption;
    private String mediaUriString;
    private String mediaType;
    private String mimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityPostingDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Review your post");
        displayPostPreview();
        binding.header.btnBack.setOnClickListener(v -> finish());
        binding.btnEditPost.setOnClickListener(v -> finish());
        binding.btnPublish.setOnClickListener(v -> publishPost());
    }

    private void displayPostPreview() {
        Intent intent = getIntent();

        String title = intent.getStringExtra("post_title");
        String caption = intent.getStringExtra("post_caption");
        String mediaUriString = intent.getStringExtra("media_uri");
        String mediaType = intent.getStringExtra("media_type");

        binding.tvPreviewTitle.setText(
                title != null ? title : "Untitled post"
        );

        binding.tvPreviewCaption.setText(
                caption != null && !caption.trim().isEmpty()
                        ? caption
                        : "No caption added."
        );

        binding.tvPreviewMediaType.setText(
                "video".equalsIgnoreCase(mediaType) ? "Video" : "Image"
        );

        if (mediaUriString != null && !mediaUriString.trim().isEmpty()) {
            Uri mediaUri = Uri.parse(mediaUriString);

            binding.ivPostPreview.setVisibility(View.VISIBLE);

            if ("image".equalsIgnoreCase(mediaType)) {
                binding.ivPostPreview.setImageURI(mediaUri);
            } else {
                // Temporary visual placeholder for video.
                // We'll add actual video playback separately.
                binding.ivPostPreview.setImageDrawable(null);
                binding.ivPostPreview.setBackgroundColor(
                        android.graphics.Color.DKGRAY
                );
                binding.ivPostPreview.setContentDescription(
                        "Selected video"
                );
            }
        } else {
            binding.ivPostPreview.setVisibility(View.GONE);
        }
    }


    private String getAccessToken() {
        Intent intent = getIntent();

        String token = intent.getStringExtra("access_token");

        if (token == null || token.trim().isEmpty()) {
            token = getSharedPreferences(
                    "app_prefs",
                    MODE_PRIVATE
            ).getString("access_token", "");
        }

        return token == null ? "" : token.trim();
    }


    private void publishPost() {
        Intent intent = getIntent();

        mediaUriString = intent.getStringExtra("media_uri");
        mediaType = intent.getStringExtra("media_type");
        postTitle = intent.getStringExtra("post_title");
        postCaption = intent.getStringExtra("post_caption");
        mimeType = intent.getStringExtra("media_mime_type");

        accessToken = getAccessToken();

        if (accessToken.isEmpty()) {
            Toast.makeText(
                    this,
                    "Your login session was not found. Please log in again.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        if (SUPABASE_KEY.equals(
                "PASTE_YOUR_SUPABASE_PUBLISHABLE_KEY_HERE")
                || SUPABASE_KEY.trim().isEmpty()) {
            showMessage("Add your Supabase publishable key first.");
            return;
        }

        if (accessToken == null || accessToken.trim().isEmpty()) {
            showMessage("Your login session is missing. Please log in again.");
            return;
        }

        if (mediaUriString == null || mediaUriString.trim().isEmpty()) {
            showMessage("Please select a photo or video first.");
            return;
        }

        Uri mediaUri = Uri.parse(mediaUriString);

        if (postTitle == null || postTitle.trim().isEmpty()) {
            postTitle = "";
        }

        if (postCaption == null) {
            postCaption = "";
        }

        if (mediaType == null || mediaType.trim().isEmpty()) {
            showMessage("The media type is missing. Please select your media again.");
            return;
        }

        if (mimeType == null || mimeType.trim().isEmpty()
                || "application/octet-stream".equalsIgnoreCase(mimeType)) {

            mimeType = getContentResolver().getType(mediaUri);
        }

        if (mimeType == null || mimeType.trim().isEmpty()
                || "application/octet-stream".equalsIgnoreCase(mimeType)) {

            if ("video".equalsIgnoreCase(mediaType)) {
                mimeType = "video/mp4";
            } else {
                mimeType = "image/jpeg";
            }
        }

        binding.btnPublish.setEnabled(false);

        executor.execute(() -> {
            try {
                // STEP 1: Confirm the logged-in Supabase user.
                JSONObject user = getAuthenticatedUser();
                String userId = user.getString("id");

                // STEP 2: Ensure the user is verified and active.
                checkVerifiedUser(userId);

                // STEP 3: Read the selected media.
                byte[] mediaBytes = readMedia(mediaUri);

                if (mediaBytes.length == 0) {
                    throw new Exception("The selected media file is empty.");
                }

                // STEP 4: Upload the media to Supabase Storage.
                String extension = getExtension(mimeType);
                String fileName = UUID.randomUUID().toString() + extension;
                String storagePath = userId + "/" + fileName;
                android.util.Log.d(
                        "AMPLIFY_UPLOAD",
                        "Storage path being uploaded: " + storagePath
                );

                uploadMedia(storagePath, mediaBytes, mimeType);

                // STEP 5: Save the post information in the database.
                savePost(userId, postTitle, postCaption,
                        mediaType, storagePath);

                runOnUiThread(() -> {
                    showMessage("Post published successfully!");
                    Intent mainIntent =
                            new Intent(PostingDetailsActivity.this,
                                    MainActivity.class);
                    mainIntent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(mainIntent);
                    finish();
                });

            } catch (Exception e) {
                android.util.Log.e(
                        "AmplifyPost",
                        "Publishing failed",
                        e
                );

                runOnUiThread(() -> {
                    binding.btnPublish.setEnabled(true);
                    showMessage(e.getMessage() == null
                            ? "Could not publish your post."
                            : e.getMessage());
                });
            }
        });
    }

    private JSONObject getAuthenticatedUser() throws Exception {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/user")
                .get()
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null
                    ? response.body().string() : "";

            if (!response.isSuccessful()) {
                android.util.Log.e("AmplifyAuth",
                        "HTTP " + response.code() + ": " + body);
                throw new Exception(
                        "Login verification failed (HTTP "
                                + response.code() + "). Check Logcat.");
            }

            return new JSONObject(body);
        }
    }

    private void checkVerifiedUser(String userId) throws Exception {
        String encodedUserId = URLEncoder.encode(
                userId, StandardCharsets.UTF_8.name());

        // Assumes profiles.user_id references the authenticated user's UUID.
        String url = SUPABASE_URL
                + "/rest/v1/profiles"
                + "?id=eq." + encodedUserId
                + "&select=user_type,account_status";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null
                    ? response.body().string() : "";

            if (!response.isSuccessful()) {
                android.util.Log.e("AmplifyProfile",
                        "HTTP " + response.code() + ": " + body);
                throw new Exception(
                        "Could not check your verification status (HTTP "
                                + response.code() + "). Check Logcat.");
            }

            JSONArray profiles = new JSONArray(body);

            if (profiles.length() == 0) {
                throw new Exception("Your profile could not be found.");
            }

            JSONObject profile = profiles.getJSONObject(0);

            boolean verified =
                    "Verified".equalsIgnoreCase(
                            profile.optString("user_type"))
                            && "Active".equalsIgnoreCase(
                            profile.optString("account_status"));

            if (!verified) {
                throw new Exception(
                        "Only active, verified users can publish posts.");
            }
        }
    }

    private byte[] readMedia(Uri uri) throws Exception {
        try (InputStream input =
                     getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            if (input == null) {
                throw new Exception(
                        "Could not open the selected media file.");
            }

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            return output.toByteArray();
        }
    }

    private void uploadMedia(
            String storagePath,
            byte[] mediaBytes,
            String contentType) throws Exception {

        String encodedPath = encodeStoragePath(storagePath);

        String uploadUrl = SUPABASE_URL
                + "/storage/v1/object/"
                + STORAGE_BUCKET + "/"
                + encodedPath;

        android.util.Log.d(
                "AMPLIFY_UPLOAD",
                "Upload URL: " + uploadUrl
        );

        MediaType mediaMediaType = MediaType.parse(contentType);
        RequestBody fileBody =
                RequestBody.create(mediaMediaType, mediaBytes);

        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(fileBody)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", contentType)
                .addHeader("x-upsert", "false")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null
                    ? response.body().string() : "";
            android.util.Log.d(
                    "AMPLIFY_UPLOAD",
                    "Upload HTTP status: " + response.code()
            );

            android.util.Log.d(
                    "AMPLIFY_UPLOAD",
                    "Upload response: " + body
            );

            if (!response.isSuccessful()) {
                android.util.Log.e("AmplifyStorage",
                        "HTTP " + response.code() + ": " + body);

                throw new Exception(
                        "Supabase storage rejected the upload (HTTP "
                                + response.code()
                                + "). Check Logcat under AmplifyStorage.");
            }
        }
    }

    private void savePost(
            String userId,
            String title,
            String caption,
            String type,
            String storagePath) throws Exception {

        JSONObject post = new JSONObject();
        post.put("user_id", userId);
        post.put("title", title);
        post.put("caption", caption);
        post.put("media_type", type);
        post.put("media_path", storagePath);
        post.put("status", "published");

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                post.toString().getBytes(StandardCharsets.UTF_8)
        );

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/posts")
                .post(body)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null
                    ? response.body().string() : "";

            if (!response.isSuccessful()) {
                android.util.Log.e("AmplifyDatabase",
                        "HTTP " + response.code() + ": " + responseBody);

                throw new Exception(
                        "Could not save the post (HTTP "
                                + response.code()
                                + "). Check Logcat under AmplifyDatabase.");
            }
        }
    }

    private String encodeStoragePath(String path) throws Exception {
        String[] parts = path.split("/");
        StringBuilder encoded = new StringBuilder();

        for (String part : parts) {
            if (encoded.length() > 0) {
                encoded.append("/");
            }

            encoded.append(
                    java.net.URLEncoder.encode(
                                    part, StandardCharsets.UTF_8.name())
                            .replace("+", "%20"));
        }

        return encoded.toString();
    }

    private String getExtension(String contentType) {
        if (contentType == null) {
            return ".bin";
        }

        switch (contentType.toLowerCase()) {
            case "image/jpeg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/webp":
                return ".webp";
            case "video/mp4":
                return ".mp4";
            case "video/webm":
                return ".webm";
            case "video/quicktime":
                return ".mov";
            case "video/3gpp":
                return ".3gp";
            case "video/x-matroska":
                return ".mkv";
            case "image/gif":
                return ".gif";
            default:
                if (contentType.startsWith("video/")) {
                    return ".mp4";
                } else if (contentType.startsWith("image/")) {
                    return ".jpg";
                }
                return ".bin";
        }
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
