package com.amplify.app.ui.admin.reports;

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
import com.amplify.app.adapters.ReportAdapter;
import com.amplify.app.data.AdminReportRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.FragmentAdminReportsBinding;
import com.amplify.app.models.Report;
import com.amplify.app.ui.admin.AdminReportDetailActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin > Reports tab: real reports from Supabase with Open / Actioned / Dismissed filters.
 * The list reloads every time the tab comes back to the front.
 */
public class AdminReportsFragment extends Fragment {

    private FragmentAdminReportsBinding binding;
    private AdminReportRepository repo;
    private ReportAdapter adapter;

    private String currentFilter = AdminReportRepository.OPEN;

    /** Lets us ignore a slow answer when the admin has already switched filters. */
    private int loadCounter = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repo = new AdminReportRepository(requireContext());

        adapter = new ReportAdapter(this::onReportTapped);
        binding.rvReports.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvReports.setAdapter(adapter);

        binding.tvFilterOpen.setOnClickListener(
                v -> selectFilter(AdminReportRepository.OPEN));
        binding.tvFilterActioned.setOnClickListener(
                v -> selectFilter(AdminReportRepository.ACTIONED));
        binding.tvFilterDismissed.setOnClickListener(
                v -> selectFilter(AdminReportRepository.DISMISSED));

        updateFilterAppearance();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadReports();
    }

    private void onReportTapped(Report report) {
        AdminReportDetailActivity.start(requireContext(), report);
    }

    private void selectFilter(String filter) {
        if (filter.equals(currentFilter)) return;
        currentFilter = filter;
        updateFilterAppearance();
        loadReports();
    }

    private void updateFilterAppearance() {
        styleFilter(binding.tvFilterOpen,
                AdminReportRepository.OPEN.equals(currentFilter));
        styleFilter(binding.tvFilterActioned,
                AdminReportRepository.ACTIONED.equals(currentFilter));
        styleFilter(binding.tvFilterDismissed,
                AdminReportRepository.DISMISSED.equals(currentFilter));
    }

    private void styleFilter(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected
                ? R.drawable.bg_filter_selected
                : R.drawable.bg_filter_unselected);
        chip.setTextColor(ContextCompat.getColor(requireContext(),
                selected ? android.R.color.white : R.color.muted));
    }

    private void loadReports() {
        if (binding == null) return;

        final int thisLoad = ++loadCounter;
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvEmpty.setVisibility(View.GONE);

        repo.loadReports(currentFilter, new SupabaseApi.Callback<List<Report>>() {
            @Override
            public void onSuccess(List<Report> reports) {
                if (!isAdded() || binding == null || thisLoad != loadCounter) return;

                binding.progress.setVisibility(View.GONE);
                adapter.setItems(reports);
                binding.tvActiveReportsCount.setText(reports.size() + " " + currentFilter);

                if (reports.isEmpty()) {
                    binding.tvEmpty.setText(emptyMessage());
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || binding == null || thisLoad != loadCounter) return;

                binding.progress.setVisibility(View.GONE);
                adapter.setItems(new ArrayList<>());
                binding.tvActiveReportsCount.setText("—");
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String emptyMessage() {
        if (AdminReportRepository.ACTIONED.equals(currentFilter)) {
            return "No actioned reports yet.";
        }
        if (AdminReportRepository.DISMISSED.equals(currentFilter)) {
            return "No dismissed reports.";
        }
        return "No open reports.";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
