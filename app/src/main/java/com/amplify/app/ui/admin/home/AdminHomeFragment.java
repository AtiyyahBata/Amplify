package com.amplify.app.ui.admin.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amplify.app.databinding.FragmentAdminHomeBinding;

/**
 * Admin > Home tab. Shows the active-user count and quick stats mentioned in
 * Part G.5 of the reference document. The numbers here are hard-coded in the
 * layout for now - swap them for a real Firestore query once that layer exists.
 */
public class AdminHomeFragment extends Fragment {

    private FragmentAdminHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
