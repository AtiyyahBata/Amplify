package com.amplify.app.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.SupabaseApi;
import com.amplify.app.data.VerificationRepository;
import com.amplify.app.databinding.ActivityGetVerifiedBinding;

/**
 * Get Verified screen (user side).
 *
 * Shows the user's real verification status, and lets a normal user apply:
 *  - none yet      -> the form is shown
 *  - pending       -> "Waiting for review", form hidden (one request at a time)
 *  - rejected      -> the admin's reason is shown and the form is shown again
 *  - rejected and blocked -> reason shown, form hidden
 *  - Verified/Admin -> a confirmation, form hidden
 */
public class GetVerifiedActivity extends AppCompatActivity {

    private ActivityGetVerifiedBinding binding;
    private VerificationRepository repo;

    private Uri selectedImage;
    private boolean submitting = false;
    private boolean canRetryLoad = false;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || binding == null) return;

                String mime = getContentResolver().getType(uri);
                if (!VerificationRepository.isAllowedImage(mime)) {
                    toast("Please choose a JPG, PNG or WebP image.");
                    return;
                }

                selectedImage = uri;
                binding.ivCertificatePreview.setImageURI(uri);
                binding.ivCertificatePreview.setVisibility(View.VISIBLE);
                binding.ivUploadIcon.setVisibility(View.GONE);
                binding.tvUploadLabel.setText("Tap to choose a different photo");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGetVerifiedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repo = new VerificationRepository(this);

        binding.header.tvHeaderTitle.setText("Get verified");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.uploadBox.setOnClickListener(v -> imagePicker.launch("image/*"));
        binding.btnSubmit.setOnClickListener(v -> submit());

        // The status card doubles as a "tap to retry" when loading failed.
        binding.cardStatus.setOnClickListener(v -> {
            if (canRetryLoad) loadStatus();
        });

        loadStatus();
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed() && binding != null;
    }

    // ------------------------------------------------------------------
    // Loading and showing the status
    // ------------------------------------------------------------------

    private void loadStatus() {
        canRetryLoad = false;
        binding.progress.setVisibility(View.VISIBLE);
        binding.cardStatus.setVisibility(View.GONE);
        binding.formGroup.setVisibility(View.GONE);

        repo.loadMyStatus(new SupabaseApi.Callback<VerificationRepository.MyStatus>() {
            @Override
            public void onSuccess(VerificationRepository.MyStatus status) {
                if (!alive()) return;
                showStatus(status);
            }

            @Override
            public void onError(String message) {
                if (!alive()) return;
                binding.progress.setVisibility(View.GONE);
                canRetryLoad = true;
                showCard("Couldn't load your status", message + "\nTap here to try again.");
            }
        });
    }

    private void showStatus(VerificationRepository.MyStatus s) {
        binding.progress.setVisibility(View.GONE);

        if ("Admin".equalsIgnoreCase(s.userType)) {
            showCard("Admin account", "Admins don't need to be verified.");
            binding.formGroup.setVisibility(View.GONE);
            return;
        }

        if ("Verified".equalsIgnoreCase(s.userType)) {
            showCard("You're verified", "You can now post in Amplify.");
            binding.formGroup.setVisibility(View.GONE);
            return;
        }

        if ("pending".equals(s.requestStatus)) {
            String ago = DateUtils.getRelativeTimeSpanString(
                    s.createdAtMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS).toString();
            showCard("Waiting for review",
                    "Submitted " + ago + ". An admin will check your certificate.");
            binding.formGroup.setVisibility(View.GONE);
            return;
        }

        if ("rejected".equals(s.requestStatus)) {
            String note = s.adminNote.isEmpty()
                    ? "No reason was given."
                    : "Reason: " + s.adminNote;

            if (s.blocked) {
                showCard("Not approved",
                        note + "\nYou can no longer apply for verification.");
                binding.formGroup.setVisibility(View.GONE);
            } else {
                showCard("Not approved", note + "\nYou can apply again below.");
                binding.formGroup.setVisibility(View.VISIBLE);
            }
            return;
        }

        // Never applied.
        binding.cardStatus.setVisibility(View.GONE);
        binding.formGroup.setVisibility(View.VISIBLE);
    }

    private void showCard(String title, String detail) {
        binding.tvStatusTitle.setText(title);
        binding.tvStatusDetail.setText(detail);
        binding.cardStatus.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // Submitting
    // ------------------------------------------------------------------

    private void submit() {
        if (submitting) return;

        String university = binding.etUniversity.getText().toString().trim();
        String reason = binding.etReason.getText().toString().trim();

        if (university.isEmpty()) {
            binding.etUniversity.setError("Enter your university");
            binding.etUniversity.requestFocus();
            return;
        }
        if (reason.length() < 10) {
            binding.etReason.setError("Please write at least a short sentence");
            binding.etReason.requestFocus();
            return;
        }
        if (selectedImage == null) {
            toast("Please choose a photo of your certificate first.");
            return;
        }

        submitting = true;
        binding.btnSubmit.setEnabled(false);
        binding.btnSubmit.setText("Submitting...");

        repo.submit(selectedImage, reason, university, new SupabaseApi.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (!alive()) return;
                submitting = false;
                resetSubmitButton();
                clearForm();
                toast("Submitted for review");
                loadStatus(); // now shows "Waiting for review"
            }

            @Override
            public void onError(String message) {
                if (!alive()) return;
                submitting = false;
                resetSubmitButton();
                toast(message);
            }
        });
    }

    private void resetSubmitButton() {
        binding.btnSubmit.setEnabled(true);
        binding.btnSubmit.setText("Submit for verification");
    }

    private void clearForm() {
        binding.etUniversity.setText("");
        binding.etReason.setText("");
        selectedImage = null;
        binding.ivCertificatePreview.setImageDrawable(null);
        binding.ivCertificatePreview.setVisibility(View.GONE);
        binding.ivUploadIcon.setVisibility(View.VISIBLE);
        binding.tvUploadLabel.setText("Tap to upload a photo (JPG, PNG or WebP)");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
