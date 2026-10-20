package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.R;
import com.amplify.app.databinding.ItemProfilePostRowBinding;
import com.amplify.app.models.Post;
import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Locale;

/**
 * The list of posts on a profile (own profile and other people's).
 *
 * Each row: title and description on the left, picture on the right.
 * Video post: dark box with a play symbol.
 * Post without media: no picture box, the text uses the full width.
 */
public class ProfilePostRowAdapter
        extends RecyclerView.Adapter<ProfilePostRowAdapter.RowHolder> {

    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    private final List<Post> posts;
    private final OnPostClickListener listener;

    public ProfilePostRowAdapter(List<Post> posts, OnPostClickListener listener) {
        this.posts = posts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProfilePostRowBinding binding = ItemProfilePostRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new RowHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        Post post = posts.get(position);
        ItemProfilePostRowBinding b = holder.binding;

        // Title
        String title = post.getTitle();
        b.tvRowTitle.setText(title == null || title.trim().isEmpty()
                ? "Untitled post" : title);

        // Description
        String body = post.getBody();
        if (body != null && !body.trim().isEmpty()) {
            b.tvRowBody.setText(body);
            b.tvRowBody.setVisibility(View.VISIBLE);
        } else {
            b.tvRowBody.setVisibility(View.GONE);
        }

        // Picture. Start clean, because rows get reused while scrolling.
        Glide.with(b.ivRowMedia.getContext()).clear(b.ivRowMedia);
        b.ivRowMedia.setImageDrawable(null);
        b.ivRowPlay.setVisibility(View.GONE);

        String type = post.getMediaType() == null
                ? "" : post.getMediaType().toLowerCase(Locale.ROOT);
        String url = post.getMediaUrl();
        boolean hasMedia = url != null && !url.isEmpty();

        if (hasMedia && type.contains("image")) {
            b.cardRowMedia.setVisibility(View.VISIBLE);
            b.cardRowMedia.setCardBackgroundColor(
                    ContextCompat.getColor(b.getRoot().getContext(), R.color.teal_soft));
            Glide.with(b.ivRowMedia.getContext())
                    .load(url)
                    .override(400, 400)
                    .centerCrop()
                    .into(b.ivRowMedia);

        } else if (hasMedia && type.contains("video")) {
            b.cardRowMedia.setVisibility(View.VISIBLE);
            b.cardRowMedia.setCardBackgroundColor(
                    ContextCompat.getColor(b.getRoot().getContext(), R.color.ink));
            b.ivRowPlay.setVisibility(View.VISIBLE);

        } else {
            b.cardRowMedia.setVisibility(View.GONE);
        }

        b.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onPostClick(post);
        });
    }

    @Override
    public int getItemCount() {
        return posts != null ? posts.size() : 0;
    }

    public static class RowHolder extends RecyclerView.ViewHolder {
        final ItemProfilePostRowBinding binding;

        RowHolder(ItemProfilePostRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
