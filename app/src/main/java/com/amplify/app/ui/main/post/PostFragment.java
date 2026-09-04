package com.amplify.app.ui.main.post;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amplify.app.databinding.FragmentPostBinding;
import com.amplify.app.ui.postflow.PostingDetailsActivity;

/**
 * Post tab (Use Case: Upload Content). Collects title/format/body then hands
 * off to PostingDetailsActivity for the review step. Nothing is written to a
 * database yet - FR9/FR10 (verification gate + publish) belong in that layer.
 */
public class PostFragment extends Fragment {

    private FragmentPostBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentPostBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnContinue.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), PostingDetailsActivity.class)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
