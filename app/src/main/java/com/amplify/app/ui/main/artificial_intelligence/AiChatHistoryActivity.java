package com.amplify.app.ui.main.artificial_intelligence;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.R;
import com.amplify.app.adapters.ChatHistoryAdapter;
import com.amplify.app.data.DummyData;

/**
 * Screen 4 & Screen 6: Chat History Screen & Search in History.
 * Displays list of past AI chats with real-time search filtering and delete confirmation option.
 */
public class AiChatHistoryActivity extends AppCompatActivity {

    private static final int REQUEST_DELETE = 1001;
    private ChatHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat_history);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        RecyclerView rvHistory = findViewById(R.id.rvHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));

        loadHistoryData(rvHistory);

        EditText etSearchChat = findViewById(R.id.etSearchChat);
        if (etSearchChat != null) {
            etSearchChat.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (adapter != null) {
                        adapter.filter(s.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        findViewById(R.id.btnDeleteOldChats).setOnClickListener(v -> {
            Intent intent = new Intent(AiChatHistoryActivity.this, AiDeleteHistoryActivity.class);
            startActivityForResult(intent, REQUEST_DELETE);
        });
    }

    private void loadHistoryData(RecyclerView rvHistory) {
        adapter = new ChatHistoryAdapter(DummyData.getAiChatHistory(), item -> {
            Intent intent = new Intent(AiChatHistoryActivity.this, AiChatDetailActivity.class);
            intent.putExtra("CHAT_TOPIC", item.getTitle());
            startActivity(intent);
        });
        rvHistory.setAdapter(adapter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DELETE && resultCode == RESULT_OK) {
            if (adapter != null) {
                adapter.clear();
            }
        }
    }
}
