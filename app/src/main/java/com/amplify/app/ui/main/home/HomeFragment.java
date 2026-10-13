package com.amplify.app.ui.main.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.R;
import com.amplify.app.adapters.PostAdapter;
import com.amplify.app.data.DummyData;
import com.amplify.app.databinding.FragmentHomeBinding;
import com.amplify.app.ui.content.ViewPostActivity;
import com.amplify.app.ui.main.artificial_intelligence.Artificial_Intelligence;

/**
 * Home tab (Use Case: Browse Available Content). Shows the community feed;
 * tapping a post opens ViewPostActivity. Data comes from DummyData for now.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PostAdapter adapter = new PostAdapter(DummyData.getFeedPosts(), post ->
                startActivity(new Intent(getActivity(), ViewPostActivity.class)));

        binding.rvFeed.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvFeed.setAdapter(adapter);

        View btnAiFloating = view.findViewById(R.id.btnAiFloating);
        if (btnAiFloating != null) {
            btnAiFloating.setOnClickListener(v ->
                    startActivity(new Intent(getActivity(), Artificial_Intelligence.class)));
        }

        View btnHeaderAiRobot = view.findViewById(R.id.btnHeaderAiRobot);
        if (btnHeaderAiRobot != null) {
            btnHeaderAiRobot.setOnClickListener(v ->
                    startActivity(new Intent(getActivity(), Artificial_Intelligence.class)));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
