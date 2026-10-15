
package com.amplify.app.ui.main.post;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amplify.app.databinding.FragmentPostBinding;
import com.amplify.app.ui.postflow.PostingDetailsActivity;

public class PostFragment extends Fragment {

    private FragmentPostBinding binding;

    private Uri selectedMediaUri;
    private String selectedMediaType;
    private String selectedMimeType;

    private final ActivityResultLauncher<String[]> mediaPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri == null || binding == null) {
                            return;
                        }

                        String mimeType = requireContext()
                                .getContentResolver()
                                .getType(uri);

                        if (mimeType == null
                                || (!mimeType.startsWith("image/")
                                && !mimeType.startsWith("video/"))) {

                            Toast.makeText(
                                    requireContext(),
                                    "Please choose an image or video.",
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        selectedMediaUri = uri;
                        selectedMimeType = mimeType;
                        selectedMediaType = mimeType.startsWith("image/")
                                ? "image"
                                : "video";

                        try {
                            requireContext()
                                    .getContentResolver()
                                    .takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    );
                        } catch (SecurityException ignored) {
                            // Continue if persistent permission is unavailable.
                        }

                        binding.tvSelectedMedia.setText(getFileName(uri));
                    }
            );

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentPostBinding.inflate(
                inflater,
                container,
                false
        );

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnSelectMedia.setOnClickListener(v ->
                mediaPicker.launch(new String[]{"image/*", "video/*"})
        );

        binding.btnPreviewPost.setOnClickListener(v -> openPreview());
    }

    private void openPreview() {

        String title = binding.etPostTitle.getText()
                .toString()
                .trim();

        String caption = binding.etCaption.getText()
                .toString()
                .trim();

        if (title.isEmpty()) {
            binding.etPostTitle.setError("Please enter a title");
            binding.etPostTitle.requestFocus();
            return;
        }

        if (selectedMediaUri == null) {
            Toast.makeText(
                    requireContext(),
                    "Please select an image or video first.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        // Get the login token passed to MainActivity.

        String accessToken = requireActivity()
                .getIntent()
                .getStringExtra("access_token");

// If MainActivity's Intent has no token, read the saved login token.
        if (accessToken == null || accessToken.trim().isEmpty()) {
            accessToken = requireContext()
                    .getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    .getString("access_token", "");
        }

// Stop only if the token is still missing.
        if (accessToken == null || accessToken.trim().isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    "Your login session was not found. Please log in again.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }


        if (accessToken == null || accessToken.trim().isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    "Your login session was not found. Please log in again.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        Intent intent = new Intent(
                requireContext(),
                PostingDetailsActivity.class
        );

        // Pass the exact values expected by PostingDetailsActivity.
        intent.putExtra("access_token", accessToken);
        intent.putExtra("post_title", title);
        intent.putExtra("post_caption", caption);
        intent.putExtra("media_uri", selectedMediaUri.toString());
        intent.putExtra("media_type", selectedMediaType);
        intent.putExtra("media_mime_type", selectedMimeType);

        startActivity(intent);
    }

    private String getFileName(Uri uri) {

        String name = uri.getLastPathSegment();

        try (android.database.Cursor cursor =
                     requireContext().getContentResolver().query(
                             uri,
                             null,
                             null,
                             null,
                             null
                     )) {

            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                );

                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // Use the fallback name.
        }

        return name != null ? name : "Selected media";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
