package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.databinding.ItemPostBinding;
import com.amplify.app.models.Post;

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
        holder.binding.tvAvatar.setText(post.getAuthorInitials());
        holder.binding.tvAuthorName.setText(post.getAuthorName());
        holder.binding.tvPostTitle.setText(post.getTitle());
        holder.binding.tvPostBody.setText(post.getBody());
        holder.binding.tvTagOne.setText(post.getTagOne());
        holder.binding.tvTagTwo.setText(post.getTagTwo());

        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onPostClick(post);
        });
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        final ItemPostBinding binding;

        PostViewHolder(ItemPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
