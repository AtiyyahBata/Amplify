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
 *
 * Typing a question and pressing send opens a NEW chat and sends that question to the AI.
 * The suggestion chips open a new chat with the starter text already typed in the input box,
 * so the user can finish the sentence (for example paste the post) before sending.
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
            btnChipSummarize.setOnClickListener(v ->
                    openNewChatWithPrefill("Summarize this post", "Summarize this post: "));
        }

        LinearLayout btnChipExplain = findViewById(R.id.btnChipExplain);
        if (btnChipExplain != null) {
            btnChipExplain.setOnClickListener(v ->
                    openNewChatWithPrefill("Explain something", "Explain this to me: "));
        }

        LinearLayout btnChipIdeas = findViewById(R.id.btnChipIdeas);
        if (btnChipIdeas != null) {
            btnChipIdeas.setOnClickListener(v ->
                    openNewChatWithPrefill("Give me ideas", "Give me ideas for: "));
        }

        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(msg)) {
                openNewChatAndSend(msg);
                etMessage.setText("");
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Opens a new chat and immediately sends the message to the AI. */
    private void openNewChatAndSend(String message) {
        Intent intent = new Intent(this, AiChatDetailActivity.class);
        intent.putExtra("CHAT_TOPIC", message);
        intent.putExtra("INITIAL_MESSAGE", message);
        startActivity(intent);
    }

    /** Opens a new chat with starter text typed in, waiting for the user to finish and send. */
    private void openNewChatWithPrefill(String topic, String prefill) {
        Intent intent = new Intent(this, AiChatDetailActivity.class);
        intent.putExtra("CHAT_TOPIC", topic);
        intent.putExtra("PREFILL", prefill);
        startActivity(intent);
    }
}
