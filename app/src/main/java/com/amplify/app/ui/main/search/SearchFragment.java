package com.amplify.app.ui.main.search;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.SearchResultAdapter;
import com.amplify.app.data.DummyData;
import com.amplify.app.databinding.FragmentSearchBinding;
import com.amplify.app.ui.content.ViewPostActivity;

/**
 * Search tab (Use Case: Search for Learning Resources). The AI query itself
 * is not wired up yet - tapping "Search" just loads placeholder results from
 * DummyData so the layout and flow can be reviewed and later connected to the
 * Gemini API call described in Part H.4 of the reference document.
 */
public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.rvResults.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.btnSearch.setOnClickListener(v -> runSearch());

        View btnAiFloating = view.findViewById(com.amplify.app.R.id.btnAiFloating);
        if (btnAiFloating != null) {
            btnAiFloating.setOnClickListener(v ->
                    startActivity(new Intent(getActivity(), com.amplify.app.ui.main.artificial_intelligence.Artificial_Intelligence.class)));
        }
    }

    private void runSearch() {
        String query = binding.etSearchQuery.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            query = "RecyclerView vs ListView";
        }

        binding.cardAiSummary.setVisibility(View.VISIBLE);
        binding.tvAiSummary.setText("Here's an overview on \"" + query + "\", pulled from Amplify's own " +
                "content first and the wider web after. Read through the sources below rather than copying " +
                "an answer directly.");

        SearchResultAdapter adapter = new SearchResultAdapter(
                DummyData.getSearchResults(query),
                result -> startActivity(new Intent(getActivity(), ViewPostActivity.class)));
        binding.rvResults.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
