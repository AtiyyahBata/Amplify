package com.amplify.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.data.AvatarLoader;
import com.amplify.app.databinding.ItemPersonBinding;
import com.amplify.app.models.PersonResult;

import java.util.ArrayList;
import java.util.List;

/** The list of people shown while searching for someone to message. */
public class PersonAdapter extends RecyclerView.Adapter<PersonAdapter.Holder> {

    public interface OnPersonClickListener {
        void onPersonClick(PersonResult person);
    }

    private final List<PersonResult> people = new ArrayList<>();
    private final OnPersonClickListener listener;

    public PersonAdapter(OnPersonClickListener listener) {
        this.listener = listener;
    }

    public void setPeople(List<PersonResult> newPeople) {
        people.clear();
        people.addAll(newPeople);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPersonBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PersonResult p = people.get(position);
        holder.binding.tvPersonAvatar.setText(p.getInitials());
        AvatarLoader.bind(p.getAvatarUrl(), holder.binding.cardPersonAvatar, holder.binding.ivPersonAvatar);
        holder.binding.tvPersonName.setText(p.getFullName());
        holder.binding.tvPersonField.setText(p.getFieldOfStudy());
        holder.binding.tvPersonField.setVisibility(
                p.getFieldOfStudy().isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onPersonClick(p);
        });
    }

    @Override
    public int getItemCount() {
        return people.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemPersonBinding binding;

        Holder(ItemPersonBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
