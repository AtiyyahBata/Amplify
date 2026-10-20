package com.amplify.app.ui.admin;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.AdminReportRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.ActivityAdminReportDetailBinding;
import com.amplify.app.databinding.DialogResolveReportBinding;
import com.amplify.app.models.Report;
import com.bumptech.glide.Glide;

import java.util.Locale;

/**
 * Admin > one report.
 *
 * Top: the reason, who reported it, when, and what they wrote.
 * Below: the reported post itself (title, text, image or video).
 * Bottom bar (open reports only): Dismiss, or Remove post.
 */
public class AdminReportDetailActivity extends AppCompatActivity {

    private static final String EXTRA_REPORT = "report";

    /** Opens the screen for one report. */
    public static void start(Context context, Report report) {
        Intent intent = new Intent(context, AdminReportDetailActivity.class);
        intent.putExtra(EXTRA_REPORT, report);
        context.startActivity(intent);
    }

    private ActivityAdminReportDetailBinding binding;
    private AdminReportRepository repo;
    private Report report;
    private boolean busy = false;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminReportDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repo = new AdminReportRepository(this);

        report = (Report) getIntent().getSerializableExtra(EXTRA_REPORT);
        if (report == null || report.getId().isEmpty()) {
            Toast.makeText(this, "This report could not be opened.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        binding.header.tvHeaderTitle.setText("Report");
        binding.header.btnBack.setOnClickListener(v -> finish());

        showReport();

        if ("post".equals(report.getTargetType())) {
            loadPost();
        } else {
            // Reports about users arrive in a later step.
            binding.cardPost.setVisibility(View.GONE);
        }

        binding.tvPostError.setOnClickListener(v -> loadPost());
        binding.btnDismiss.setOnClickListener(v -> showResolveDialog(false));
        binding.btnRemovePost.setOnClickListener(v -> showResolveDialog(true));
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed() && binding != null;
    }

    // ------------------------------------------------------------------
    // Showing the report
    // ------------------------------------------------------------------

    private void showReport() {
        binding.tvReasonBadge.setText(report.getReasonLabel());
        binding.tvReporter.setText("Reported by " + report.getReporterName());
        binding.tvWhen.setText(report.getAgeText().isEmpty()
                ? Report.formatDate(report.getCreatedAtMillis())
                : report.getAgeText() + "  •  "
                + Report.formatDate(report.getCreatedAtMillis()));

        binding.tvDescription.setText(report.getDescription().isEmpty()
                ? "The reporter did not write any details."
                : report.getDescription());

        if (report.isOpen()) {
            binding.tvStatusLine.setText("Waiting for a decision");
            binding.bottomBar.setVisibility(View.VISIBLE);
            return;
        }

        binding.bottomBar.setVisibility(View.GONE);

        if ("actioned".equals(report.getStatus())) {
            binding.tvStatusLine.setText(
                    "Post removed on " + Report.formatDate(report.getResolvedAtMillis()));
        } else {
            binding.tvStatusLine.setText(
                    "Dismissed on " + Report.formatDate(report.getResolvedAtMillis()));
        }

        if (!report.getAdminNote().isEmpty()) {
            binding.tvDecisionNote.setText("Admin note: " + report.getAdminNote());
            binding.tvDecisionNote.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------------
    // The reported post
    // ------------------------------------------------------------------

    private void loadPost() {
        binding.postProgress.setVisibility(View.VISIBLE);
        binding.tvPostError.setVisibility(View.GONE);
        binding.postContent.setVisibility(View.GONE);

        repo.loadPost(report.getPostId(),
                new SupabaseApi.Callback<AdminReportRepository.PostDetails>() {
                    @Override
                    public void onSuccess(AdminReportRepository.PostDetails post) {
                        if (!alive()) return;
                        binding.postProgress.setVisibility(View.GONE);
                        showPost(post);
                    }

                    @Override
                    public void onError(String message) {
                        if (!alive()) return;
                        binding.postProgress.setVisibility(View.GONE);
                        binding.tvPostError.setText(message + "\nTap to try again.");
                        binding.tvPostError.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void showPost(AdminReportRepository.PostDetails post) {
        binding.postContent.setVisibility(View.VISIBLE);

        binding.tvPostAuthor.setText("Posted by " + report.getReportedName());

        if ("removed".equals(post.status)) {
            binding.tvPostStatus.setText("This post has been removed from the app.");
            binding.tvPostStatus.setVisibility(View.VISIBLE);
        } else {
            binding.tvPostStatus.setVisibility(View.GONE);
        }

        binding.tvPostTitle.setText(post.title.isEmpty() ? "Untitled post" : post.title);

        if (post.caption.isEmpty()) {
            binding.tvPostCaption.setVisibility(View.GONE);
        } else {
            binding.tvPostCaption.setText(post.caption);
            binding.tvPostCaption.setVisibility(View.VISIBLE);
        }

        showMedia(post);
    }

    private void showMedia(AdminReportRepository.PostDetails post) {
        binding.mediaContainer.setVisibility(View.GONE);
        binding.ivPostMedia.setVisibility(View.GONE);
        binding.videoPostMedia.setVisibility(View.GONE);

        if (post.mediaUrl == null) return;

        String kind = post.mediaType.toLowerCase(Locale.ROOT);

        if (kind.contains("image")) {
            binding.mediaContainer.setVisibility(View.VISIBLE);
            binding.mediaContainer.setBackgroundColor(Color.TRANSPARENT);
            binding.ivPostMedia.setVisibility(View.VISIBLE);
            Glide.with(this).load(post.mediaUrl).fitCenter().into(binding.ivPostMedia);

        } else if (kind.contains("video")) {
            binding.mediaContainer.setVisibility(View.VISIBLE);
            binding.mediaContainer.setBackgroundColor(Color.BLACK);
            binding.videoPostMedia.setVisibility(View.VISIBLE);

            MediaController controller = new MediaController(this);
            controller.setAnchorView(binding.videoPostMedia);
            binding.videoPostMedia.setMediaController(controller);
            binding.videoPostMedia.setVideoURI(Uri.parse(post.mediaUrl));
            // Show the first frame; the admin taps to play.
            binding.videoPostMedia.setOnPreparedListener(
                    mp -> binding.videoPostMedia.seekTo(1));
        }
    }

    // ------------------------------------------------------------------
    // Remove post / dismiss
    // ------------------------------------------------------------------

    private void showResolveDialog(boolean removePost) {
        if (busy) return;

        DialogResolveReportBinding d = DialogResolveReportBinding.inflate(getLayoutInflater());

        new AlertDialog.Builder(this)
                .setTitle(removePost ? "Remove this post?" : "Dismiss this report?")
                .setMessage(removePost
                        ? "The post will disappear from the app for everyone, and any other "
                        + "open reports on it will be closed. The post stays in the database, "
                        + "so it can be restored if this was a mistake."
                        : "The post stays up and this report is closed.")
                .setView(d.getRoot())
                .setPositiveButton(removePost ? "Remove post" : "Dismiss",
                        (dialog, which) -> submit(removePost,
                                d.etNote.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submit(boolean removePost, String note) {
        setBusy(true);

        repo.resolve(report.getId(), removePost, note, new SupabaseApi.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (!alive()) return;
                Toast.makeText(AdminReportDetailActivity.this,
                        removePost ? "Post removed" : "Report dismissed",
                        Toast.LENGTH_LONG).show();
                finish(); // the list reloads itself when it comes back
            }

            @Override
            public void onError(String message) {
                if (!alive()) return;
                setBusy(false);
                Toast.makeText(AdminReportDetailActivity.this,
                        message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        binding.btnDismiss.setEnabled(!value);
        binding.btnRemovePost.setEnabled(!value);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
