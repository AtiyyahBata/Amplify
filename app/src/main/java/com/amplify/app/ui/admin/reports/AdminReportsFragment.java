package com.amplify.app.ui.admin.reports;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.ReportAdapter;
import com.amplify.app.data.DummyData;
import com.amplify.app.databinding.FragmentAdminReportsBinding;

/** Admin > Reports tab - user complaints about posts or members. */
public class AdminReportsFragment extends Fragment {

    private FragmentAdminReportsBinding binding;

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

        ReportAdapter adapter = new ReportAdapter(
                DummyData.getOpenReports(),
                (report, actioned) -> Toast.makeText(getContext(),
                        actioned ? report.getActionLabel() : "Dismissed", Toast.LENGTH_SHORT).show());

        binding.rvReports.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvReports.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
