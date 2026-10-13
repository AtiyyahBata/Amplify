package com.amplify.app.ui.main.artificial_intelligence;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amplify.app.R;
import com.amplify.app.adapters.ChatHistoryAdapter;
import com.amplify.app.data.ChatRepository;
import com.amplify.app.models.ChatHistoryItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen 4 & Screen 6: Chat History Screen & Search in History.
 * Shows the signed-in user's saved chats from Supabase.
 *  - tap a chat        -> resume it
 *  - long-press a chat -> delete just that chat
 *  - "Delete Old Chats" -> delete all chats (confirmation screen)
 */
public class AiChatHistoryActivity extends AppCompatActivity {

    private static final int REQUEST_DELETE = 1001;
    private ChatHistoryAdapter adapter;
    private ChatRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat_history);

        repository = new ChatRepository(this);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        RecyclerView rvHistory = findViewById(R.id.rvHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        setUpAdapter(rvHistory);

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

    /** Reload every time the screen comes back, so new chats and deletions show up. */
    @Override
    protected void onResume() {
        super.onResume();
        loadChats();
    }

    private void setUpAdapter(RecyclerView rvHistory) {
        adapter = new ChatHistoryAdapter(new ArrayList<>(), item -> {
            Intent intent = new Intent(AiChatHistoryActivity.this, AiChatDetailActivity.class);
            intent.putExtra("CHAT_ID", item.getId());
            intent.putExtra("CHAT_TOPIC", item.getTitle());
            startActivity(intent);
        });
        adapter.setOnItemLongClickListener(this::confirmDeleteChat);
        rvHistory.setAdapter(adapter);
    }

    private void loadChats() {
        repository.loadChats(new ChatRepository.ResultCallback<List<ChatHistoryItem>>() {
            @Override
            public void onSuccess(List<ChatHistoryItem> chats) {
                if (isFinishing()) return;
                adapter.setItems(chats);
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                Toast.makeText(AiChatHistoryActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDeleteChat(ChatHistoryItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this chat?")
                .setMessage("\"" + item.getTitle() + "\" will be permanently deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        repository.deleteChat(item.getId(), new ChatRepository.ResultCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                if (isFinishing()) return;
                                Toast.makeText(AiChatHistoryActivity.this,
                                        "Chat deleted", Toast.LENGTH_SHORT).show();
                                loadChats();
                            }

                            @Override
                            public void onError(String message) {
                                if (isFinishing()) return;
                                Toast.makeText(AiChatHistoryActivity.this,
                                        message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
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
