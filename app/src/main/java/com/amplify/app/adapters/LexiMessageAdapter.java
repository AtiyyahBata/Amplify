package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.databinding.ItemLexiMessageBinding;
import com.amplify.app.models.SystemMessage;
import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The messages on the Lexi screen: title, copy of the post, the explanation, the date. */
public class LexiMessageAdapter extends RecyclerView.Adapter<LexiMessageAdapter.LexiViewHolder> {

    private final List<SystemMessage> items = new ArrayList<>();

    public void setItems(List<SystemMessage> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LexiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLexiMessageBinding binding = ItemLexiMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LexiViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LexiViewHolder holder, int position) {
        SystemMessage m = items.get(position);
        ItemLexiMessageBinding b = holder.binding;

        b.tvLexiTitle.setText(m.getTitle());
        b.tvNew.setVisibility(m.isUnread() ? View.VISIBLE : View.GONE);
        b.tvLexiBody.setText(m.getBody());
        b.tvLexiTime.setText(m.getCreatedAtMillis() <= 0 ? ""
                : new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
                .format(new Date(m.getCreatedAtMillis())));

        // The copy of the post
        if (!m.hasPost()) {
            b.cardPost.setVisibility(View.GONE);
            return;
        }
        b.cardPost.setVisibility(View.VISIBLE);
        b.tvPostTitle.setText(m.getPostTitle());

        if (m.getPostCaption().isEmpty()) {
            b.tvPostCaption.setVisibility(View.GONE);
        } else {
            b.tvPostCaption.setText(m.getPostCaption());
            b.tvPostCaption.setVisibility(View.VISIBLE);
        }

        b.tvVideoLabel.setVisibility(m.isVideo() ? View.VISIBLE : View.GONE);

        if (m.isImage()) {
            b.ivPostMedia.setVisibility(View.VISIBLE);
            Glide.with(b.ivPostMedia.getContext())
                    .load(m.getMediaUrl())
                    .fitCenter()
                    .into(b.ivPostMedia);
        } else {
            Glide.with(b.ivPostMedia.getContext()).clear(b.ivPostMedia);
            b.ivPostMedia.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class LexiViewHolder extends RecyclerView.ViewHolder {
        final ItemLexiMessageBinding binding;

        LexiViewHolder(ItemLexiMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
