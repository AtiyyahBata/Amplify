package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.databinding.ItemReportBinding;
import com.amplify.app.models.Report;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin > Reports list. Each card shows the reason, what was reported and who reported it.
 * Tapping a card opens the detail screen, where the admin takes action.
 */
public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    public interface OnReportClickListener {
        void onReportClick(Report report);
    }

    private final List<Report> reports = new ArrayList<>();
    private final OnReportClickListener listener;

    public ReportAdapter(OnReportClickListener listener) {
        this.listener = listener;
    }

    /** Replaces everything in the list. */
    public void setItems(List<Report> items) {
        reports.clear();
        reports.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReportBinding binding = ItemReportBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ReportViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Report report = reports.get(position);

        holder.binding.tvSeverity.setText(report.getReasonLabel());
        holder.binding.tvSubject.setText(report.getSubjectLabel());
        holder.binding.tvReason.setText(report.getReason());
        holder.binding.tvReportUser.setText(report.getMetaText());

        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onReportClick(report);
        });
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        final ItemReportBinding binding;

        ReportViewHolder(ItemReportBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
