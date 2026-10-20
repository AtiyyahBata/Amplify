package com.amplify.app.adapters;

import android.graphics.Typeface;
import android.content.Intent;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.databinding.ItemDmMeBinding;
import com.amplify.app.databinding.ItemDmThemBinding;
import com.amplify.app.data.PostShareMessage;
import com.amplify.app.models.DirectMessage;
import com.amplify.app.ui.content.ViewPostActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The bubbles inside one conversation. */
public class DirectMessageAdapter extends RecyclerView.Adapter<DirectMessageAdapter.Holder> {

    public interface OnMessageLongClickListener {
        void onMessageLongClick(DirectMessage message);
    }

    private static final int TYPE_ME = 0;
    private static final int TYPE_THEM = 1;

    public static final String DELETED_TEXT = "This message was deleted";
    public static final String UNREADABLE_TEXT = "🔒 Message can't be read";

    private final List<DirectMessage> items = new ArrayList<>();
    private OnMessageLongClickListener longClickListener;

    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.longClickListener = listener;
    }

    // ------------------------------------------------------------------
    // Changing the list
    // ------------------------------------------------------------------

    private static final Comparator<DirectMessage> BY_TIME = new Comparator<DirectMessage>() {
        @Override
        public int compare(DirectMessage a, DirectMessage b) {
            return Long.compare(a.getCreatedAt(), b.getCreatedAt());
        }
    };

    public void setMessages(List<DirectMessage> messages) {
        items.clear();
        items.addAll(messages);
        notifyDataSetChanged();
    }

    /**
     * Combines a fresh list from the server with what is on screen. Messages still being
     * sent stay put. Everything else becomes exactly what the server says.
     */
    public void merge(List<DirectMessage> fromServer) {
        Map<String, DirectMessage> byId = new HashMap<>();
        for (DirectMessage m : fromServer) byId.put(m.getId(), m);

        List<DirectMessage> result = new ArrayList<>(fromServer);
        for (DirectMessage m : items) {
            if (m.isPending() && !byId.containsKey(m.getId())) result.add(m);
        }
        Collections.sort(result, BY_TIME);   // stable, so ties keep their order

        items.clear();
        items.addAll(result);
        notifyDataSetChanged();
    }

    private int indexOf(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    /** Adds a message, or replaces it if it is already there. Returns true if it was new. */
    public boolean addMessage(DirectMessage message) {
        int existing = indexOf(message.getId());
        if (existing >= 0) {
            items.set(existing, message);
            notifyItemChanged(existing);
            return false;
        }
        int pos = items.size();
        while (pos > 0 && items.get(pos - 1).getCreatedAt() > message.getCreatedAt()
                && !items.get(pos - 1).isPending()) {
            pos--;
        }
        items.add(pos, message);
        notifyItemInserted(pos);
        return true;
    }

    /**
     * The server has saved a message we showed early. Swaps the early copy for the real one.
     * If the real one already arrived another way, just drops the early copy.
     */
    public void confirmSent(String tempId, DirectMessage real) {
        int tempPos = indexOf(tempId);
        int realPos = indexOf(real.getId());
        if (realPos >= 0) {
            items.set(realPos, real);
            notifyItemChanged(realPos);
            if (tempPos >= 0) {
                items.remove(tempPos);
                notifyItemRemoved(tempPos);
            }
        } else if (tempPos >= 0) {
            items.set(tempPos, real);
            notifyItemChanged(tempPos);
        } else {
            addMessage(real);
        }
    }

    public void removeById(String id) {
        int pos = indexOf(id);
        if (pos >= 0) {
            items.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    /** A message changed on the server (it was read, or deleted by its sender). */
    public void applyUpdate(String id, boolean read, boolean deleted) {
        int pos = indexOf(id);
        if (pos < 0) return;
        DirectMessage m = items.get(pos);
        if (read) m.markRead();
        if (deleted) m.markDeleted();
        notifyItemChanged(pos);
    }

    public void markDeleted(String id) {
        applyUpdate(id, false, true);
    }

    public boolean hasUnreadIncoming() {
        for (DirectMessage m : items) {
            if (!m.isMine() && !m.isRead() && !m.isDeleted() && !m.isPending()) return true;
        }
        return false;
    }

    /** Marks everything from the other person as seen (on screen only). */
    public void markIncomingRead() {
        for (int i = 0; i < items.size(); i++) {
            DirectMessage m = items.get(i);
            if (!m.isMine() && !m.isRead()) {
                m.markRead();
            }
        }
    }

    public int lastPosition() {
        return items.size() - 1;
    }

    public DirectMessage getItem(int position) {
        return items.get(position);
    }

    // ------------------------------------------------------------------
    // RecyclerView plumbing
    // ------------------------------------------------------------------

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isMine() ? TYPE_ME : TYPE_THEM;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ME) {
            ItemDmMeBinding b = ItemDmMeBinding.inflate(inflater, parent, false);
            return new Holder(b.getRoot(), b.tvDmText, b.tvDmMeta,
                    b.cardDmPost, b.ivDmPostImage, b.tvDmPostTitle);
        }
        ItemDmThemBinding b = ItemDmThemBinding.inflate(inflater, parent, false);
        return new Holder(b.getRoot(), b.tvDmText, b.tvDmMeta,
                b.cardDmPost, b.ivDmPostImage, b.tvDmPostTitle);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DirectMessage m = items.get(position);
        holder.text.setOnClickListener(null);
        holder.postCard.setOnClickListener(null);

        if (m.isDeleted()) {
            holder.postCard.setVisibility(View.GONE);
            holder.text.setVisibility(View.VISIBLE);
            Glide.with(holder.postImage.getContext()).clear(holder.postImage);
            holder.text.setText(DELETED_TEXT);
            holder.text.setTypeface(null, Typeface.ITALIC);
            holder.text.setAlpha(0.7f);
        } else if (m.isUndecryptable()) {
            holder.postCard.setVisibility(View.GONE);
            holder.text.setVisibility(View.VISIBLE);
            Glide.with(holder.postImage.getContext()).clear(holder.postImage);
            holder.text.setText(UNREADABLE_TEXT);
            holder.text.setTypeface(null, Typeface.ITALIC);
            holder.text.setAlpha(0.7f);
        } else {
            String sharedPostId = PostShareMessage.postId(m.getText());
            holder.text.setTypeface(null, Typeface.NORMAL);
            holder.text.setAlpha(1f);
            if (sharedPostId != null) {
                holder.text.setVisibility(View.GONE);
                holder.postCard.setVisibility(View.VISIBLE);
                holder.postTitle.setText(PostShareMessage.title(m.getText()));
                holder.postCard.setOnClickListener(v -> {
                    Intent intent = new Intent(v.getContext(), ViewPostActivity.class);
                    intent.putExtra("post_id", sharedPostId);
                    v.getContext().startActivity(intent);
                });
                String mediaUrl = PostShareMessage.mediaUrl(m.getText());
                if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
                    Glide.with(holder.postImage.getContext()).clear(holder.postImage);
                    holder.postImage.setVisibility(View.GONE);
                } else {
                    holder.postImage.setVisibility(View.VISIBLE);
                    String mediaType = PostShareMessage.mediaType(m.getText());
                    if (mediaType.toLowerCase(java.util.Locale.ROOT).contains("video")) {
                        Glide.with(holder.postImage.getContext())
                                .asBitmap()
                                .load(mediaUrl)
                                .apply(new RequestOptions().frame(1_000_000))
                                .centerCrop()
                                .placeholder(com.amplify.app.R.color.ink)
                                .error(com.amplify.app.R.color.ink)
                                .into(holder.postImage);
                    } else {
                        Glide.with(holder.postImage.getContext())
                                .load(mediaUrl)
                                .centerCrop()
                                .placeholder(com.amplify.app.R.color.paper)
                                .error(com.amplify.app.R.color.paper)
                                .into(holder.postImage);
                    }
                }
            } else {
                holder.postCard.setVisibility(View.GONE);
                holder.text.setVisibility(View.VISIBLE);
                Glide.with(holder.postImage.getContext()).clear(holder.postImage);
                holder.text.setText(PostShareMessage.displayText(m.getText()));
                holder.text.setOnClickListener(null);
            }
        }

        holder.meta.setText(metaFor(m));

        boolean canDelete = m.isMine() && !m.isPending() && !m.isDeleted();
        if (canDelete) {
            View.OnLongClickListener deleteListener = v -> {
                if (longClickListener != null) longClickListener.onMessageLongClick(m);
                return true;
            };
            holder.text.setOnLongClickListener(deleteListener);
            holder.postCard.setOnLongClickListener(deleteListener);
        } else {
            holder.text.setOnLongClickListener(null);
            holder.text.setLongClickable(false);
            holder.postCard.setOnLongClickListener(null);
            holder.postCard.setLongClickable(false);
        }
    }

    /** "14:05", plus a tick for my own messages: sending, sent, or read. */
    public static String metaFor(DirectMessage m) {
        if (!m.isMine()) return m.getTime();
        if (m.isPending()) return "Sending…";
        if (m.isDeleted()) return m.getTime();
        return m.getTime() + (m.isRead() ? "  ✓✓" : "  ✓");
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView meta;
        final View postCard;
        final ImageView postImage;
        final TextView postTitle;

        Holder(android.view.View root, TextView text, TextView meta, View postCard,
               ImageView postImage, TextView postTitle) {
            super(root);
            this.text = text;
            this.meta = meta;
            this.postCard = postCard;
            this.postImage = postImage;
            this.postTitle = postTitle;
        }
    }
}
