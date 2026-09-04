package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.databinding.ItemMessagePreviewBinding;
import com.amplify.app.models.MessagePreview;

import java.util.List;

public class MessagePreviewAdapter extends RecyclerView.Adapter<MessagePreviewAdapter.PreviewViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(MessagePreview preview);
    }

    private final List<MessagePreview> conversations;
    private final OnConversationClickListener listener;

    public MessagePreviewAdapter(List<MessagePreview> conversations, OnConversationClickListener listener) {
        this.conversations = conversations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMessagePreviewBinding binding = ItemMessagePreviewBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PreviewViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PreviewViewHolder holder, int position) {
        MessagePreview preview = conversations.get(position);
        holder.binding.tvAvatar.setText(preview.getInitials());
        holder.binding.tvName.setText(preview.getName());
        holder.binding.tvLastMessage.setText(preview.getLastMessage());
        holder.binding.tvTime.setText(preview.getTimeAgo());
        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onConversationClick(preview);
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class PreviewViewHolder extends RecyclerView.ViewHolder {
        final ItemMessagePreviewBinding binding;

        PreviewViewHolder(ItemMessagePreviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
