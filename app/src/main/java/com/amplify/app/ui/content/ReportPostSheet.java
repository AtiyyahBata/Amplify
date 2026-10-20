package com.amplify.app.ui.content;

import android.app.Activity;
import android.view.View;
import android.view.WindowManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.amplify.app.R;
import com.amplify.app.data.ReportRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.DialogReportPostBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * The "Report post" bottom sheet: pick a reason, write details, send.
 * Use it with: ReportPostSheet.show(this, postId);
 */
public final class ReportPostSheet {

    private ReportPostSheet() {}

    public static void show(Activity activity, String postId) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        DialogReportPostBinding b =
                DialogReportPostBinding.inflate(activity.getLayoutInflater());
        dialog.setContentView(b.getRoot());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        // Open fully, so the keyboard does not cover the form.
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        ReportRepository repo = new ReportRepository(activity);

        // Index of the chosen reason (-1 = nothing chosen yet).
        final int[] selected = {-1};

        // --- Reason dropdown ---
        b.reasonField.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(activity, v);
            for (int i = 0; i < ReportRepository.REASON_LABELS.length; i++) {
                menu.getMenu().add(0, i, i, ReportRepository.REASON_LABELS[i]);
            }
            menu.setOnMenuItemClickListener(item -> {
                selected[0] = item.getItemId();
                b.tvReasonValue.setText(ReportRepository.REASON_LABELS[selected[0]]);
                b.tvReasonValue.setTextColor(ContextCompat.getColor(activity, R.color.ink));

                boolean isOther = "other".equals(ReportRepository.REASON_CODES[selected[0]]);
                b.tvDetailsLabel.setText(isOther ? "Details (required)" : "Details (optional)");
                return true;
            });
            menu.show();
        });

        // --- Character counter ---
        b.etDescription.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                b.tvCharCount.setText(s.length() + "/1000");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // --- Buttons ---
        b.btnCancelReport.setOnClickListener(v -> dialog.dismiss());

        b.btnSubmitReport.setOnClickListener(v -> {
            if (selected[0] < 0) {
                Toast.makeText(activity, "Please choose a reason.", Toast.LENGTH_SHORT).show();
                return;
            }

            String code = ReportRepository.REASON_CODES[selected[0]];
            String description = b.etDescription.getText().toString().trim();

            if ("other".equals(code) && description.isEmpty()) {
                b.etDescription.setError("Please describe the problem");
                b.etDescription.requestFocus();
                return;
            }

            setBusy(dialog, b, true);

            repo.submitPostReport(postId, code, description,
                    new SupabaseApi.Callback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            dialog.dismiss();
                            Toast.makeText(activity,
                                    "Thank you. Your report was sent to the admins.",
                                    Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String message) {
                            if (dialog.isShowing()) {
                                setBusy(dialog, b, false);
                            }
                            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        dialog.show();
    }

    /** Locks the form while the report is being sent. */
    private static void setBusy(BottomSheetDialog dialog, DialogReportPostBinding b,
                                boolean busy) {
        b.btnSubmitReport.setEnabled(!busy);
        b.btnSubmitReport.setText(busy ? "Sending..." : "Submit report");
        b.btnCancelReport.setEnabled(!busy);
        dialog.setCancelable(!busy);
    }
}
