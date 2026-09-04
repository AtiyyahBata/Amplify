package com.amplify.app.ui.messaging;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.databinding.ActivityNewMessageBinding;

/**
 * New Message screen (Use Case: Contact a Content Creator). Sending here does
 * not create a real Firestore Messages document yet - it opens the
 * Conversation screen so the thread UI can be reviewed end to end.
 */
public class NewMessageActivity extends AppCompatActivity {

    private ActivityNewMessageBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNewMessageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("New message");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.btnSend.setOnClickListener(v -> {
            startActivity(new Intent(this, ConversationActivity.class));
            finish();
        });
    }
}
