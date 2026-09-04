package com.amplify.app.ui.main.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amplify.app.databinding.FragmentProfileBinding;
import com.amplify.app.ui.content.ViewPostActivity;
import com.amplify.app.ui.settings.GetVerifiedActivity;
import com.amplify.app.ui.settings.ProfileSettingsActivity;

/** Profile tab - the logged-in user's own profile (Use Case: View a User Profile). */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), ProfileSettingsActivity.class)));

        binding.btnVerificationStatus.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), GetVerifiedActivity.class)));

        binding.cardMyPost.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), ViewPostActivity.class)));

        binding.btnGithub.setOnClickListener(v ->
                Toast.makeText(getContext(), "Opens github.com/…", Toast.LENGTH_SHORT).show());

        binding.btnLinkedIn.setOnClickListener(v ->
                Toast.makeText(getContext(), "Opens linkedin.com/…", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
