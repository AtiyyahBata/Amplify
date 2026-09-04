package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.databinding.ItemReportBinding;
import com.amplify.app.models.Report;

import java.util.List;

/**
 * Admin > Reports list. Dismiss/Take action only removes the row locally -
 * a real implementation would update the report's status in Firestore and,
 * for "take action", remove the offending post or suspend the user.
 */
public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    public interface OnResolveListener {
        void onResolve(Report report, boolean actioned);
    }

    private final List<Report> reports;
    private final OnResolveListener listener;

    public ReportAdapter(List<Report> reports, OnResolveListener listener) {
        this.reports = reports;
        this.listener = listener;
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
        holder.binding.tvSubject.setText(report.getSubjectLabel());
        holder.binding.tvReason.setText(report.getReason());
        holder.binding.btnTakeAction.setText(report.getActionLabel());

        holder.binding.btnDismiss.setOnClickListener(v -> removeRow(holder, report, false));
        holder.binding.btnTakeAction.setOnClickListener(v -> removeRow(holder, report, true));
    }

    private void removeRow(ReportViewHolder holder, Report report, boolean actioned) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        reports.remove(position);
        notifyItemRemoved(position);
        if (listener != null) listener.onResolve(report, actioned);
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
