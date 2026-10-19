package com.amplify.app.ui.admin.verify;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.R;
import com.amplify.app.adapters.VerifyRequestAdapter;
import com.amplify.app.data.AdminVerificationRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.FragmentAdminVerifyBinding;
import com.amplify.app.models.VerifyRequest;
import com.amplify.app.ui.admin.AdminVerificationDetailActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin > Verify Users tab (Use Case: Verify a User Before Posting).
 *
 * Shows real requests from Supabase with three filters: Pending (longest waiting first),
 * Approved and Declined. Tapping a card opens the detail screen. The list reloads every
 * time the tab comes back to the front, so a decision made on the detail screen shows
 * up straight away.
 */
public class AdminVerifyFragment extends Fragment {

    private FragmentAdminVerifyBinding binding;
    private AdminVerificationRepository repo;
    private VerifyRequestAdapter adapter;

    private String currentFilter = AdminVerificationRepository.PENDING;

    /** Lets us ignore a slow answer when the admin has already switched filters. */
    private int loadCounter = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminVerifyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repo = new AdminVerificationRepository(requireContext());

        adapter = new VerifyRequestAdapter(request ->
                AdminVerificationDetailActivity.start(requireContext(), request));
        binding.rvVerifyRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvVerifyRequests.setAdapter(adapter);

        binding.tvFilterPending.setOnClickListener(
                v -> selectFilter(AdminVerificationRepository.PENDING));
        binding.tvFilterApproved.setOnClickListener(
                v -> selectFilter(AdminVerificationRepository.APPROVED));
        binding.tvFilterDeclined.setOnClickListener(
                v -> selectFilter(AdminVerificationRepository.REJECTED));

        updateFilterAppearance();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRequests();
    }

    private void selectFilter(String filter) {
        if (filter.equals(currentFilter)) return;
        currentFilter = filter;
        updateFilterAppearance();
        loadRequests();
    }

    private void updateFilterAppearance() {
        styleFilter(binding.tvFilterPending,
                AdminVerificationRepository.PENDING.equals(currentFilter));
        styleFilter(binding.tvFilterApproved,
                AdminVerificationRepository.APPROVED.equals(currentFilter));
        styleFilter(binding.tvFilterDeclined,
                AdminVerificationRepository.REJECTED.equals(currentFilter));
    }

    private void styleFilter(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected
                ? R.drawable.bg_filter_selected
                : R.drawable.bg_filter_unselected);
        chip.setTextColor(ContextCompat.getColor(requireContext(),
                selected ? android.R.color.white : R.color.muted));
    }

    private void loadRequests() {
        if (binding == null) return;

        final int thisLoad = ++loadCounter;
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvEmpty.setVisibility(View.GONE);

        repo.loadRequests(currentFilter, new SupabaseApi.Callback<List<VerifyRequest>>() {
            @Override
            public void onSuccess(List<VerifyRequest> requests) {
                if (!isAdded() || binding == null || thisLoad != loadCounter) return;

                binding.progress.setVisibility(View.GONE);
                adapter.setItems(requests);

                if (requests.isEmpty()) {
                    binding.tvEmpty.setText(emptyMessage());
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || binding == null || thisLoad != loadCounter) return;

                binding.progress.setVisibility(View.GONE);
                adapter.setItems(new ArrayList<>());
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String emptyMessage() {
        if (AdminVerificationRepository.APPROVED.equals(currentFilter)) {
            return "No approved users yet.";
        }
        if (AdminVerificationRepository.REJECTED.equals(currentFilter)) {
            return "No declined requests.";
        }
        return "No pending requests.";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
