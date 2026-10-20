package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.data.AvatarLoader;
import com.amplify.app.databinding.ItemPersonBinding;
import com.amplify.app.databinding.ItemSearchHeaderBinding;
import com.amplify.app.databinding.ItemSearchResultBinding;
import com.amplify.app.models.PersonResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The Search tab's results: a "People" section followed by a "Posts" section.
 * Each section only appears when it has something to show.
 */
public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnPersonClickListener {
        void onPersonClick(PersonResult person);
    }

    public interface OnPostClickListener {
        void onPostClick(PostHit post);
    }

    /** One post found by a search. */
    public static class PostHit {
        private final String id;
        private final String title;
        private final String source;   // e.g. "by Jane Smith"
        private final String format;   // e.g. "image", "video", "post"

        public PostHit(String id, String title, String source, String format) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.source = source == null ? "" : source;
            this.format = format == null ? "" : format;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getSource() { return source; }
        public String getFormat() { return format; }
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PERSON = 1;
    private static final int TYPE_POST = 2;

    // Each row is a String (a section header), a PersonResult or a PostHit.
    private final List<Object> rows = new ArrayList<>();
    private final OnPersonClickListener personListener;
    private final OnPostClickListener postListener;

    public SearchAdapter(OnPersonClickListener personListener, OnPostClickListener postListener) {
        this.personListener = personListener;
        this.postListener = postListener;
    }

    public void setResults(List<PersonResult> people, List<PostHit> posts) {
        rows.clear();
        if (people != null && !people.isEmpty()) {
            rows.add("People (" + people.size() + ")");
            rows.addAll(people);
        }
        if (posts != null && !posts.isEmpty()) {
            rows.add("Posts (" + posts.size() + ")");
            rows.addAll(posts);
        }
        notifyDataSetChanged();
    }

    public void clear() {
        rows.clear();
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public int getItemViewType(int position) {
        Object row = rows.get(position);
        if (row instanceof PersonResult) return TYPE_PERSON;
        if (row instanceof PostHit) return TYPE_POST;
        return TYPE_HEADER;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_PERSON) {
            return new PersonHolder(ItemPersonBinding.inflate(inflater, parent, false));
        }
        if (viewType == TYPE_POST) {
            return new PostHolder(ItemSearchResultBinding.inflate(inflater, parent, false));
        }
        return new HeaderHolder(ItemSearchHeaderBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);

        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).binding.tvSearchHeader.setText((String) row);

        } else if (holder instanceof PersonHolder) {
            PersonResult p = (PersonResult) row;
            ItemPersonBinding b = ((PersonHolder) holder).binding;
            b.tvPersonAvatar.setText(p.getInitials());
            AvatarLoader.bind(p.getAvatarUrl(), b.cardPersonAvatar, b.ivPersonAvatar);
            b.tvPersonName.setText(p.getFullName());
            b.tvPersonField.setText(p.getFieldOfStudy());
            b.tvPersonField.setVisibility(
                    p.getFieldOfStudy().isEmpty() ? View.GONE : View.VISIBLE);
            b.getRoot().setOnClickListener(v -> {
                if (personListener != null) personListener.onPersonClick(p);
            });

        } else if (holder instanceof PostHolder) {
            PostHit post = (PostHit) row;
            ItemSearchResultBinding b = ((PostHolder) holder).binding;
            b.tvFormat.setText(post.getFormat());
            b.tvResultTitle.setText(post.getTitle());
            b.tvSource.setText(post.getSource());
            b.getRoot().setOnClickListener(v -> {
                if (postListener != null) postListener.onPostClick(post);
            });
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemSearchHeaderBinding binding;

        HeaderHolder(ItemSearchHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class PersonHolder extends RecyclerView.ViewHolder {
        final ItemPersonBinding binding;

        PersonHolder(ItemPersonBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class PostHolder extends RecyclerView.ViewHolder {
        final ItemSearchResultBinding binding;

        PostHolder(ItemSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
