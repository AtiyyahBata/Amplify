package com.amplify.app.ui.main.artificial_intelligence;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.R;

/**
 * AI Chat main screen Activity (Use Case: AI Companion / Assistant).
 * Allows users to ask questions, select prompt suggestions, or view Chat History.
 */
public class Artificial_Intelligence extends AppCompatActivity {

    private EditText etMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        findViewById(R.id.btnHistory).setOnClickListener(v ->
                startActivity(new Intent(Artificial_Intelligence.this, AiChatHistoryActivity.class)));

        etMessage = findViewById(R.id.etMessage);

        LinearLayout btnChipSummarize = findViewById(R.id.btnChipSummarize);
        if (btnChipSummarize != null) {
            btnChipSummarize.setOnClickListener(v -> openChatWithTopic("Summarize this post"));
        }

        LinearLayout btnChipExplain = findViewById(R.id.btnChipExplain);
        if (btnChipExplain != null) {
            btnChipExplain.setOnClickListener(v -> openChatWithTopic("Explain something"));
        }

        LinearLayout btnChipIdeas = findViewById(R.id.btnChipIdeas);
        if (btnChipIdeas != null) {
            btnChipIdeas.setOnClickListener(v -> openChatWithTopic("Give me ideas"));
        }

        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(msg)) {
                openChatWithTopic(msg);
                etMessage.setText("");
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openChatWithTopic(String topic) {
        Intent intent = new Intent(this, AiChatDetailActivity.class);
        intent.putExtra("CHAT_TOPIC", topic);
        startActivity(intent);
    }
}
