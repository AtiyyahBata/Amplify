package com.amplify.app.ui.admin;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.AdminVerificationRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.ActivityAdminVerificationDetailBinding;
import com.amplify.app.databinding.DialogRejectVerificationBinding;
import com.amplify.app.models.VerifyRequest;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * Admin > one verification request.
 *
 * Top: name, email and state (waiting, approved or declined).
 * Middle: the certificate image, loaded through a temporary link because the bucket is private.
 * Bottom of the page: university and the reason for applying.
 * Bottom bar (pending only): Approve, or Reject with an optional reason and a block option.
 */
public class AdminVerificationDetailActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "id";
    private static final String EXTRA_USER_ID = "user_id";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_EMAIL = "email";
    private static final String EXTRA_UNIVERSITY = "university";
    private static final String EXTRA_REASON = "reason";
    private static final String EXTRA_PATH = "path";
    private static final String EXTRA_STATUS = "status";
    private static final String EXTRA_NOTE = "note";
    private static final String EXTRA_BLOCKED = "blocked";
    private static final String EXTRA_CREATED = "created";
    private static final String EXTRA_REVIEWED = "reviewed";

    /** Opens the screen for one request. */
    public static void start(Context context, VerifyRequest r) {
        Intent intent = new Intent(context, AdminVerificationDetailActivity.class);
        intent.putExtra(EXTRA_ID, r.getId());
        intent.putExtra(EXTRA_USER_ID, r.getUserId());
        intent.putExtra(EXTRA_NAME, r.getUserName());
        intent.putExtra(EXTRA_EMAIL, r.getEmail());
        intent.putExtra(EXTRA_UNIVERSITY, r.getUniversity());
        intent.putExtra(EXTRA_REASON, r.getReason());
        intent.putExtra(EXTRA_PATH, r.getCertificatePath());
        intent.putExtra(EXTRA_STATUS, r.getStatus());
        intent.putExtra(EXTRA_NOTE, r.getAdminNote());
        intent.putExtra(EXTRA_BLOCKED, r.isBlocked());
        intent.putExtra(EXTRA_CREATED, r.getCreatedAtMillis());
        intent.putExtra(EXTRA_REVIEWED, r.getReviewedAtMillis());
        context.startActivity(intent);
    }

    private ActivityAdminVerificationDetailBinding binding;
    private AdminVerificationRepository repo;
    private VerifyRequest request;
    private boolean busy = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminVerificationDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repo = new AdminVerificationRepository(this);

        Intent in = getIntent();
        request = new VerifyRequest(
                in.getStringExtra(EXTRA_ID) != null ? in.getStringExtra(EXTRA_ID) : "",
                nonNull(in.getStringExtra(EXTRA_USER_ID)),
                nonNull(in.getStringExtra(EXTRA_NAME)),
                nonNull(in.getStringExtra(EXTRA_EMAIL)),
                nonNull(in.getStringExtra(EXTRA_UNIVERSITY)),
                nonNull(in.getStringExtra(EXTRA_REASON)),
                nonNull(in.getStringExtra(EXTRA_PATH)),
                nonNull(in.getStringExtra(EXTRA_STATUS)),
                nonNull(in.getStringExtra(EXTRA_NOTE)),
                in.getBooleanExtra(EXTRA_BLOCKED, false),
                in.getLongExtra(EXTRA_CREATED, 0),
                in.getLongExtra(EXTRA_REVIEWED, 0));

        if (request.getId().isEmpty()) {
            Toast.makeText(this, "This request could not be opened.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        binding.header.tvHeaderTitle.setText("Verification request");
        binding.header.btnBack.setOnClickListener(v -> finish());

        showRequest();
        loadCertificate();

        binding.tvCertError.setOnClickListener(v -> loadCertificate());
        binding.btnApprove.setOnClickListener(v -> confirmApprove());
        binding.btnReject.setOnClickListener(v -> showRejectDialog());
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed() && binding != null;
    }

    // ------------------------------------------------------------------
    // Showing the request
    // ------------------------------------------------------------------

    private void showRequest() {
        binding.tvName.setText(request.getUserName());
        binding.tvEmail.setText(request.getEmail().isEmpty() ? "No email on file"
                : request.getEmail());
        binding.tvUniversity.setText(request.getUniversity().isEmpty()
                ? "Not provided" : request.getUniversity());
        binding.tvReason.setText(request.getReason().isEmpty()
                ? "Not provided" : request.getReason());

        if (request.isPending()) {
            binding.tvStatusLine.setText(request.getWaitingText());
            binding.bottomBar.setVisibility(View.VISIBLE);
            return;
        }

        binding.bottomBar.setVisibility(View.GONE);

        if ("approved".equals(request.getStatus())) {
            binding.tvStatusLine.setText(
                    "Approved on " + VerifyRequest.formatDate(request.getReviewedAtMillis()));
        } else {
            binding.tvStatusLine.setText(
                    "Declined on " + VerifyRequest.formatDate(request.getReviewedAtMillis())
                            + (request.isBlocked() ? " · Blocked from applying again" : ""));
            if (!request.getAdminNote().isEmpty()) {
                binding.tvDecisionNote.setText("Reason given: " + request.getAdminNote());
                binding.tvDecisionNote.setVisibility(View.VISIBLE);
            }
        }
    }

    // ------------------------------------------------------------------
    // The certificate image
    // ------------------------------------------------------------------

    private void loadCertificate() {
        binding.certProgress.setVisibility(View.VISIBLE);
        binding.tvCertError.setVisibility(View.GONE);

        if (request.getCertificatePath().isEmpty()) {
            showCertificateError("No certificate was uploaded.");
            return;
        }

        repo.getSignedUrl(request.getCertificatePath(), new SupabaseApi.Callback<String>() {
            @Override
            public void onSuccess(String url) {
                if (!alive()) return;

                Glide.with(AdminVerificationDetailActivity.this)
                        .load(url)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                        Target<Drawable> target,
                                                        boolean isFirstResource) {
                                if (alive()) {
                                    showCertificateError("Couldn't load the certificate.");
                                }
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model,
                                                           Target<Drawable> target,
                                                           DataSource dataSource,
                                                           boolean isFirstResource) {
                                if (alive()) {
                                    binding.certProgress.setVisibility(View.GONE);
                                }
                                return false;
                            }
                        })
                        .into(binding.ivCertificate);
            }

            @Override
            public void onError(String message) {
                if (!alive()) return;
                showCertificateError(message);
            }
        });
    }

    private void showCertificateError(String message) {
        binding.certProgress.setVisibility(View.GONE);
        binding.tvCertError.setText(message + "\nTap to try again.");
        binding.tvCertError.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // Approve / reject
    // ------------------------------------------------------------------

    private void confirmApprove() {
        if (busy) return;
        new AlertDialog.Builder(this)
                .setTitle("Approve " + request.getUserName() + "?")
                .setMessage("They will be verified and able to post in Amplify.")
                .setPositiveButton("Approve", (dialog, which) -> submitDecision(true, null, false))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRejectDialog() {
        if (busy) return;

        DialogRejectVerificationBinding d =
                DialogRejectVerificationBinding.inflate(getLayoutInflater());

        new AlertDialog.Builder(this)
                .setTitle("Reject " + request.getUserName() + "?")
                .setView(d.getRoot())
                .setPositiveButton("Reject", (dialog, which) -> submitDecision(
                        false,
                        d.etRejectReason.getText().toString().trim(),
                        d.cbBlock.isChecked()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitDecision(boolean approve, String note, boolean block) {
        setBusy(true);

        repo.review(request.getId(), approve, note, block, new SupabaseApi.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (!alive()) return;
                Toast.makeText(AdminVerificationDetailActivity.this,
                        approve ? request.getUserName() + " is now verified"
                                : "Request rejected",
                        Toast.LENGTH_LONG).show();
                finish(); // the list reloads itself when it comes back
            }

            @Override
            public void onError(String message) {
                if (!alive()) return;
                setBusy(false);
                Toast.makeText(AdminVerificationDetailActivity.this,
                        message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        binding.btnApprove.setEnabled(!value);
        binding.btnReject.setEnabled(!value);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
