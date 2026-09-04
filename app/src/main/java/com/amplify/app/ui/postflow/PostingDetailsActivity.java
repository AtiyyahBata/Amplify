package com.amplify.app.ui.postflow;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.databinding.ActivityPostingDetailsBinding;
import com.amplify.app.ui.main.MainActivity;

/**
 * Posting Details screen - the review step before a post goes live (Part G.4).
 * Publishing here does not write to Firestore yet; it just simulates success
 * and returns to Home. This is where the FR9 verification check should be
 * enforced once real accounts/roles exist.
 */
public class PostingDetailsActivity extends AppCompatActivity {

    private ActivityPostingDetailsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPostingDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Review post");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.btnPublish.setOnClickListener(v -> {
            Toast.makeText(this, "Post published", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }
}
