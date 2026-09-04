package com.amplify.app.ui.admin.verify;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.VerifyRequestAdapter;
import com.amplify.app.data.DummyData;
import com.amplify.app.databinding.FragmentAdminVerifyBinding;

/** Admin > Verify Users tab (Use Case: Verify a User Before Posting). */
public class AdminVerifyFragment extends Fragment {

    private FragmentAdminVerifyBinding binding;

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

        VerifyRequestAdapter adapter = new VerifyRequestAdapter(
                DummyData.getPendingVerifications(),
                (request, approved) -> Toast.makeText(getContext(),
                        approved ? "Approved" : "Rejected", Toast.LENGTH_SHORT).show());

        binding.rvVerifyRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvVerifyRequests.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
