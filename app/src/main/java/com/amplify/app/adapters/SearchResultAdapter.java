package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.databinding.ItemSearchResultBinding;
import com.amplify.app.models.SearchResult;

import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder> {

    public interface OnResultClickListener {
        void onResultClick(SearchResult result);
    }

    private final List<SearchResult> results;
    private final OnResultClickListener listener;

    public SearchResultAdapter(List<SearchResult> results, OnResultClickListener listener) {
        this.results = results;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSearchResultBinding binding = ItemSearchResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ResultViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        SearchResult result = results.get(position);
        holder.binding.tvFormat.setText(result.getFormat());
        holder.binding.tvResultTitle.setText(result.getTitle());
        holder.binding.tvSource.setText(result.getSource());
        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onResultClick(result);
        });
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static class ResultViewHolder extends RecyclerView.ViewHolder {
        final ItemSearchResultBinding binding;

        ResultViewHolder(ItemSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
