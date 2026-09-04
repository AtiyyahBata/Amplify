package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.databinding.ItemVerifyRequestBinding;
import com.amplify.app.models.VerifyRequest;

import java.util.List;

/**
 * Admin > Verify Users list. Approve/Reject only removes the row from this
 * in-memory list - a real implementation would write the decision back to
 * the user's VerificationRequests document in Firestore (Part I.2).
 */
public class VerifyRequestAdapter extends RecyclerView.Adapter<VerifyRequestAdapter.RequestViewHolder> {

    public interface OnDecisionListener {
        void onDecision(VerifyRequest request, boolean approved);
    }

    private final List<VerifyRequest> requests;
    private final OnDecisionListener listener;

    public VerifyRequestAdapter(List<VerifyRequest> requests, OnDecisionListener listener) {
        this.requests = requests;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVerifyRequestBinding binding = ItemVerifyRequestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new RequestViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        VerifyRequest request = requests.get(position);
        holder.binding.tvAvatar.setText(request.getInitials());
        holder.binding.tvRequestLabel.setText(request.getUserName() + " — " + request.getDocumentLabel());

        holder.binding.btnApprove.setOnClickListener(v -> removeRow(holder, request, true));
        holder.binding.btnReject.setOnClickListener(v -> removeRow(holder, request, false));
    }

    private void removeRow(RequestViewHolder holder, VerifyRequest request, boolean approved) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        requests.remove(position);
        notifyItemRemoved(position);
        if (listener != null) listener.onDecision(request, approved);
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class RequestViewHolder extends RecyclerView.ViewHolder {
        final ItemVerifyRequestBinding binding;

        RequestViewHolder(ItemVerifyRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
