package com.amplify.app.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Loads, uploads and removes the signed-in person's profile photo.
 *
 * The photo is shrunk to a 512 x 512 square JPEG, saved in the public "avatars"
 * storage bucket at  <userId>/avatar.jpg , and its link is written to
 * profiles.avatar_url.
 *
 * Every method answers on the main thread through a SupabaseApi.Callback.
 */
public class AvatarRepository {

    private static final String TAG = "AvatarRepository";
    private static final String BUCKET = "avatars";
    private static final int SIZE = 512;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final MediaType JPEG = MediaType.parse("image/jpeg");
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Context context;
    private final SupabaseApi api;

    public AvatarRepository(Context context) {
        this.context = context.getApplicationContext();
        this.api = new SupabaseApi(this.context);
    }

    // ------------------------------------------------------------------
    // Read: { full name, avatar url } (both "" when missing)
    // ------------------------------------------------------------------

    public void fetch(SupabaseApi.Callback<String[]> callback) {
        String uid = SessionManager.getUserId(context);
        if (uid == null || uid.isEmpty()) {
            callback.onError("Please log in again.");
            return;
        }

        api.get("/rest/v1/profiles?select=full_name,avatar_url&id=eq." + Uri.encode(uid)
                + "&limit=1", new SupabaseApi.Callback<String>() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONArray rows = new JSONArray(body);
                    String name = "";
                    String url = "";
                    if (rows.length() > 0) {
                        JSONObject o = rows.getJSONObject(0);
                        name = o.isNull("full_name") ? "" : o.optString("full_name", "");
                        url = o.isNull("avatar_url") ? "" : o.optString("avatar_url", "");
                    }
                    callback.onSuccess(new String[]{name, url});
                } catch (Exception e) {
                    callback.onError("Couldn't read your profile.");
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    // ------------------------------------------------------------------
    // Upload a new photo. Answers with the new public link.
    // ------------------------------------------------------------------

    public void upload(Uri imageUri, SupabaseApi.Callback<String> callback) {
        POOL.execute(() -> {
            try {
                String url = uploadBlocking(imageUri);
                MAIN.post(() -> callback.onSuccess(url));
            } catch (SupabaseApi.ApiException e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Upload failed: " + e);
                MAIN.post(() -> callback.onError("Couldn't upload the photo. Please try again."));
            }
        });
    }

    private String uploadBlocking(Uri imageUri) throws Exception {
        String uid = SessionManager.getUserId(context);
        if (uid == null || uid.isEmpty()) {
            throw new SupabaseApi.ApiException(401, "Please log in again.");
        }

        byte[] jpeg = prepareJpeg(imageUri);
        String token = api.getFreshAccessToken();

        // 1. Put the file in storage (upsert replaces the old photo).
        Request request = new Request.Builder()
                .url(SupabaseConfig.PROJECT_URL + "/storage/v1/object/" + BUCKET + "/"
                        + uid + "/avatar.jpg")
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Authorization", "Bearer " + token)
                .header("x-upsert", "true")
                .post(RequestBody.create(jpeg, JPEG))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String text = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "Storage upload HTTP " + response.code() + ": " + text);
                throw new SupabaseApi.ApiException(response.code(),
                        "Couldn't upload the photo (" + response.code()
                                + "). Check the avatars storage policies.");
            }
        } catch (IOException e) {
            throw new SupabaseApi.ApiException(0,
                    "No connection. Check your internet and try again.");
        }

        // 2. Save the link on the profile. "?v=" makes phones fetch the new picture.
        String publicUrl = SupabaseConfig.PROJECT_URL + "/storage/v1/object/public/" + BUCKET
                + "/" + uid + "/avatar.jpg?v=" + System.currentTimeMillis();

        String answer = api.requestBlocking("PATCH",
                "/rest/v1/profiles?id=eq." + Uri.encode(uid),
                new JSONObject().put("avatar_url", publicUrl).toString(),
                "return=representation");

        if (new JSONArray(answer).length() == 0) {
            throw new SupabaseApi.ApiException(403,
                    "The photo was uploaded but your profile couldn't be updated.");
        }
        return publicUrl;
    }

    // ------------------------------------------------------------------
    // Remove the photo
    // ------------------------------------------------------------------

    public void remove(SupabaseApi.Callback<Boolean> callback) {
        POOL.execute(() -> {
            try {
                String uid = SessionManager.getUserId(context);
                if (uid == null || uid.isEmpty()) {
                    throw new SupabaseApi.ApiException(401, "Please log in again.");
                }

                String answer = api.requestBlocking("PATCH",
                        "/rest/v1/profiles?id=eq." + Uri.encode(uid),
                        "{\"avatar_url\":null}", "return=representation");
                if (new JSONArray(answer).length() == 0) {
                    throw new SupabaseApi.ApiException(403, "Couldn't update your profile.");
                }

                // Best effort: the file itself is not important once the link is gone.
                try {
                    api.requestBlocking("DELETE",
                            "/storage/v1/object/" + BUCKET + "/" + uid + "/avatar.jpg",
                            null, null);
                } catch (Exception e) {
                    Log.w(TAG, "Old photo file not removed: " + e);
                }

                MAIN.post(() -> callback.onSuccess(Boolean.TRUE));
            } catch (SupabaseApi.ApiException e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError("Couldn't remove the photo. Please try again."));
            }
        });
    }

    // ------------------------------------------------------------------
    // Turn the picked picture into a small, upright, square JPEG
    // ------------------------------------------------------------------

    private byte[] prepareJpeg(Uri uri) throws IOException {
        // 1. Read only the size, so a huge photo does not fill the memory.
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Not a picture");
        }

        int sample = 1;
        while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) {
            sample *= 2;
        }

        // 2. Decode the smaller version.
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) throw new IOException("Could not read the picture");

        // 3. Turn it upright if the camera stored it sideways.
        int rotation = 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                int orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            }
        } catch (Exception ignored) {
            // No rotation info: use the picture as it is.
        }

        // 4. Crop the middle square, scale to SIZE x SIZE.
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int x = (bitmap.getWidth() - side) / 2;
        int y = (bitmap.getHeight() - side) / 2;
        Matrix m = new Matrix();
        if (rotation != 0) m.postRotate(rotation);
        float scale = (float) SIZE / side;
        m.postScale(scale, scale);
        Bitmap square = Bitmap.createBitmap(bitmap, x, y, side, side, m, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        square.compress(Bitmap.CompressFormat.JPEG, 88, out);
        return out.toByteArray();
    }
}
