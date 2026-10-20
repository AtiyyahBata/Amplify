package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.data.AvatarLoader;
import com.amplify.app.databinding.ItemMessagePreviewBinding;
import com.amplify.app.models.MessagePreview;

import java.util.ArrayList;
import java.util.List;

/** The rows on the Messages tab. */
public class MessagePreviewAdapter extends RecyclerView.Adapter<MessagePreviewAdapter.PreviewViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(MessagePreview preview);
    }

    public interface OnConversationLongClickListener {
        void onConversationLongClick(MessagePreview preview);
    }

    private final List<MessagePreview> conversations = new ArrayList<>();
    private final OnConversationClickListener listener;
    private OnConversationLongClickListener longListener;

    public MessagePreviewAdapter(List<MessagePreview> conversations, OnConversationClickListener listener) {
        if (conversations != null) this.conversations.addAll(conversations);
        this.listener = listener;
    }

    public void setOnConversationLongClickListener(OnConversationLongClickListener l) {
        this.longListener = l;
    }

    public void setItems(List<MessagePreview> items) {
        conversations.clear();
        conversations.addAll(items);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return conversations.isEmpty();
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
        AvatarLoader.bind(preview.getAvatarUrl(), holder.binding.cardAvatar, holder.binding.ivAvatar);
        holder.binding.tvName.setText(preview.getName());
        holder.binding.tvLastMessage.setText(preview.getLastMessage());
        holder.binding.tvTime.setText(preview.getTimeAgo());

        int unread = preview.getUnreadCount();
        if (unread > 0) {
            holder.binding.tvUnread.setText(unread > 99 ? "99+" : String.valueOf(unread));
            holder.binding.tvUnread.setVisibility(View.VISIBLE);
            holder.binding.tvLastMessage.setTypeface(null, Typeface.BOLD);
        } else {
            holder.binding.tvUnread.setVisibility(View.GONE);
            holder.binding.tvLastMessage.setTypeface(null, Typeface.NORMAL);
        }

        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onConversationClick(preview);
        });
        holder.binding.getRoot().setOnLongClickListener(v -> {
            if (longListener == null) return false;
            longListener.onConversationLongClick(preview);
            return true;
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
