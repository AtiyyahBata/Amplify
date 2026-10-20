package com.amplify.app.adapters;

import android.graphics.Color;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.R;
import com.amplify.app.databinding.ItemPostBinding;
import com.amplify.app.models.Post;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;
import java.util.Locale;

/**
 * Feeds Post objects into the Home feed and (with minor reuse) anywhere else
 * a list of posts needs to be shown. Tapping a row fires the click listener
 * passed in from the fragment/activity, which decides where to navigate.
 *
 * Every card is the same size: fixed media box, title max 2 lines,
 * description max 3 lines with "Read more" when it is cut off.
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

        // Title (max 2 lines, set in the XML)
        String title = post.getTitle();
        if (title != null && !title.trim().isEmpty()) {
            b.tvPostTitle.setVisibility(View.VISIBLE);
            b.tvPostTitle.setText(title);
        } else {
            b.tvPostTitle.setVisibility(View.GONE);
        }

        // Post media: whole picture visible inside a fixed-size box
        bindMedia(b, post);

        // Body / Caption (max 3 lines, set in the XML)
        String body = post.getBody();
        if (body != null && !body.trim().isEmpty()) {
            b.tvPostBody.setVisibility(View.VISIBLE);
            b.tvPostBody.setText(body);
            showReadMoreIfCutOff(b, body);
        } else {
            b.tvPostBody.setVisibility(View.GONE);
            b.tvReadMore.setVisibility(View.GONE);
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

    private void bindMedia(ItemPostBinding b, Post post) {
        ImageView iv = b.ivPostMedia;
        String mediaUrl = post.getMediaUrl();
        b.ivPostPlay.setVisibility(View.GONE);

        if (mediaUrl == null || mediaUrl.isEmpty()) {
            Glide.with(iv.getContext()).clear(iv);
            iv.setImageDrawable(null);
            iv.setVisibility(View.GONE);
            return;
        }

        iv.setVisibility(View.VISIBLE);

        String type = post.getMediaType() == null
                ? "" : post.getMediaType().toLowerCase(Locale.ROOT);

        if (type.contains("video")) {
            // Show a frame from the uploaded video as its feed preview.
            iv.setBackgroundColor(Color.BLACK);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            b.ivPostPlay.setVisibility(View.VISIBLE);
            Glide.with(iv.getContext())
                    .asBitmap()
                    .load(mediaUrl)
                    .apply(new RequestOptions().frame(1_000_000))
                    .centerCrop()
                    .placeholder(android.R.color.black)
                    .error(android.R.color.black)
                    .into(iv);
        } else {
            iv.setBackgroundColor(ContextCompat.getColor(iv.getContext(), R.color.paper));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

            Glide.with(iv.getContext())
                    .load(mediaUrl)
                    .placeholder(android.R.color.darker_gray)
                    .error(android.R.drawable.ic_menu_report_image)
                    .fitCenter()
                    .into(iv);
        }
    }

    /**
     * Waits until the text has been laid out, then checks whether the last
     * visible line ends in "...". If it does, "Read more" is shown.
     * INVISIBLE (not GONE) keeps every card the same height.
     */
    private void showReadMoreIfCutOff(ItemPostBinding b, String body) {
        b.tvReadMore.setVisibility(View.INVISIBLE);

        b.tvPostBody.post(() -> {
            Layout layout = b.tvPostBody.getLayout();
            if (layout == null || layout.getLineCount() == 0) return;

            // Guard against a recycled card getting an old result.
            if (!body.equals(b.tvPostBody.getText().toString())) return;

            int last = layout.getLineCount() - 1;
            boolean cutOff = layout.getEllipsisCount(last) > 0;
            b.tvReadMore.setVisibility(cutOff ? View.VISIBLE : View.INVISIBLE);
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
