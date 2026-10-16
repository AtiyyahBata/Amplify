package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.R;
import com.amplify.app.models.ChatHistoryItem;

import java.util.ArrayList;
import java.util.List;

/** Adapter for displaying AI Chat history items in a list. */
public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(ChatHistoryItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(ChatHistoryItem item);
    }

    private final List<ChatHistoryItem> fullList;
    private final List<ChatHistoryItem> displayList;
    private final OnItemClickListener listener;
    private OnItemLongClickListener longClickListener;
    private String currentQuery = "";

    public ChatHistoryAdapter(List<ChatHistoryItem> list, OnItemClickListener listener) {
        this.fullList = new ArrayList<>(list);
        this.displayList = new ArrayList<>(list);
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    /** Replace the data (used when the list arrives from Supabase). Keeps the search filter. */
    public void setItems(List<ChatHistoryItem> items) {
        fullList.clear();
        fullList.addAll(items);
        filter(currentQuery);
    }

    public void filter(String query) {
        currentQuery = query == null ? "" : query;
        displayList.clear();
        if (currentQuery.trim().isEmpty()) {
            displayList.addAll(fullList);
        } else {
            String lower = currentQuery.toLowerCase().trim();
            for (ChatHistoryItem item : fullList) {
                if (item.getTitle().toLowerCase().contains(lower) ||
                        item.getPreview().toLowerCase().contains(lower)) {
                    displayList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void clear() {
        fullList.clear();
        displayList.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatHistoryItem item = displayList.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvPreview.setText(item.getPreview());
        holder.tvDate.setText(item.getDate());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(item);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvPreview, tvDate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvHistoryTitle);
            tvPreview = itemView.findViewById(R.id.tvHistoryPreview);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
        }
    }
}
