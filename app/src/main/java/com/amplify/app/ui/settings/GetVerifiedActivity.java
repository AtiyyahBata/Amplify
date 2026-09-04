package com.amplify.app.ui.settings;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.databinding.ActivityGetVerifiedBinding;

/**
 * Get Verified screen (Use Case: Verify a User Before Posting). Uploading here
 * only changes the label on screen - a real version would open the system
 * file picker, upload to Cloud Storage, then run Google Cloud Vision as
 * described in Part H.4/H.5 of the reference document.
 */
public class GetVerifiedActivity extends AppCompatActivity {

    private ActivityGetVerifiedBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGetVerifiedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Get verified");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.uploadBox.setOnClickListener(v ->
                binding.tvUploadLabel.setText("certificate_scan.pdf uploaded"));

        binding.btnSubmit.setOnClickListener(v -> {
            Toast.makeText(this, "Submitted for review", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
