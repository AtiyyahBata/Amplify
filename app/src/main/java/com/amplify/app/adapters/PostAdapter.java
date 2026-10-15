package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.databinding.ItemPostBinding;
import com.amplify.app.models.Post;
import com.bumptech.glide.Glide;

import java.util.List;

/**
 * Feeds Post objects into the Home feed and (with minor reuse) anywhere else
 * a list of posts needs to be shown. Tapping a row fires the click listener
 * passed in from the fragment/activity, which decides where to navigate.
 */
public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    private final List<Post> posts;
    private final OnPostClickListener listener;

    public PostAdapter(List<Post> posts, OnPostClickListener listener) {
        this.posts = posts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPostBinding binding = ItemPostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PostViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        ItemPostBinding b = holder.binding;

        // Creator avatar initials and name
        b.tvAvatar.setText(post.getAuthorInitials() != null ? post.getAuthorInitials() : "");
        b.tvAuthorName.setText(post.getAuthorName() != null ? post.getAuthorName() : "");

        // Verified status
        b.verifiedContainer.setVisibility(post.isAuthorVerified() ? View.VISIBLE : View.GONE);

        // Creator field of study / field
        String authorField = post.getAuthorField();
        if (authorField != null && !authorField.trim().isEmpty()) {
            b.tvAuthorField.setVisibility(View.VISIBLE);
            b.tvAuthorField.setText(authorField);
        } else {
            b.tvAuthorField.setVisibility(View.GONE);
        }

        // Post media image
        String mediaUrl = post.getMediaUrl();
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            b.ivPostMedia.setVisibility(View.VISIBLE);

            Glide.with(b.ivPostMedia.getContext())
                    .load(mediaUrl)
                    .placeholder(android.R.color.darker_gray)
                    .error(android.R.drawable.ic_menu_report_image)
                    .centerCrop()
                    .into(b.ivPostMedia);
        } else {
            Glide.with(b.ivPostMedia.getContext()).clear(b.ivPostMedia);
            b.ivPostMedia.setImageDrawable(null);
            b.ivPostMedia.setVisibility(View.GONE);
        }

        // Title
        String title = post.getTitle();
        if (title != null && !title.trim().isEmpty()) {
            b.tvPostTitle.setVisibility(View.VISIBLE);
            b.tvPostTitle.setText(title);
        } else {
            b.tvPostTitle.setVisibility(View.GONE);
        }

        // Body / Caption
        String body = post.getBody();
        if (body != null && !body.trim().isEmpty()) {
            b.tvPostBody.setVisibility(View.VISIBLE);
            b.tvPostBody.setText(body);
        } else {
            b.tvPostBody.setVisibility(View.GONE);
        }

        // Tags
        String tagOne = post.getTagOne();
        if (tagOne != null && !tagOne.trim().isEmpty()) {
            b.tvTagOne.setVisibility(View.VISIBLE);
            b.tvTagOne.setText(tagOne);
        } else {
            b.tvTagOne.setVisibility(View.GONE);
        }

        String tagTwo = post.getTagTwo();
        if (tagTwo != null && !tagTwo.trim().isEmpty()) {
            b.tvTagTwo.setVisibility(View.VISIBLE);
            b.tvTagTwo.setText(tagTwo);
        } else {
            b.tvTagTwo.setVisibility(View.GONE);
        }

        // Item click listener
        b.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onPostClick(post);
            }
        });
    }

    @Override
    public int getItemCount() {
        return posts != null ? posts.size() : 0;
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        final ItemPostBinding binding;

        PostViewHolder(ItemPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
