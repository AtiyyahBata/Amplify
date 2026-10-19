package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.databinding.ItemVerifyRequestBinding;
import com.amplify.app.models.VerifyRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin > Verify Users list. Each card shows the person, their university and either how
 * long they have been waiting (pending) or when they were approved or declined.
 * Tapping a card opens the detail screen, where the admin approves or rejects.
 */
public class VerifyRequestAdapter
        extends RecyclerView.Adapter<VerifyRequestAdapter.RequestViewHolder> {

    public interface OnRequestClickListener {
        void onRequestClick(VerifyRequest request);
    }

    private final List<VerifyRequest> requests = new ArrayList<>();
    private final OnRequestClickListener listener;

    public VerifyRequestAdapter(OnRequestClickListener listener) {
        this.listener = listener;
    }

    /** Replaces everything in the list. */
    public void setItems(List<VerifyRequest> items) {
        requests.clear();
        requests.addAll(items);
        notifyDataSetChanged();
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
        holder.binding.tvRequestLabel.setText(
                request.getUserName() + " — " + request.getUniversity());
        holder.binding.tvRequestMeta.setText(request.getMetaText());

        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onRequestClick(request);
        });
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
