package com.amplify.app.ui.content;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.databinding.ActivityProfileOtherBinding;
import com.amplify.app.ui.messaging.NewMessageActivity;

/**
 * Profile screen when viewing someone else (Use Case: View a User Profile).
 * Same visual layout as the user's own Profile tab, minus Settings, plus
 * a Message button - matching the reuse note in Part G.3 of the reference doc.
 */
public class ProfileOtherActivity extends AppCompatActivity {

    private ActivityProfileOtherBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileOtherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Profile");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.btnMessage.setOnClickListener(v ->
                startActivity(new Intent(this, NewMessageActivity.class)));

        binding.cardPost.setOnClickListener(v ->
                startActivity(new Intent(this, ViewPostActivity.class)));

        binding.btnGithub.setOnClickListener(v ->
                Toast.makeText(this, "Opens github.com/…", Toast.LENGTH_SHORT).show());

        binding.btnLinkedIn.setOnClickListener(v ->
                Toast.makeText(this, "Opens linkedin.com/…", Toast.LENGTH_SHORT).show());
    }
}
