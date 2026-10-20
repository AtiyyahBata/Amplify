package com.amplify.app.ui.messaging;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.LexiMessageAdapter;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.data.SystemMessageRepository;
import com.amplify.app.databinding.ActivityLexiMessagesBinding;
import com.amplify.app.models.SystemMessage;

import java.util.List;

/**
 * The Lexi screen: read-only messages from the system, newest at the bottom.
 * Opening it marks everything as read.
 */
public class LexiMessagesActivity extends AppCompatActivity {

    public static void open(Context context) {
        context.startActivity(new Intent(context, LexiMessagesActivity.class));
    }

    private ActivityLexiMessagesBinding binding;
    private SystemMessageRepository repo;
    private LexiMessageAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLexiMessagesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Lexi");
        binding.header.btnBack.setOnClickListener(v -> finish());

        repo = new SystemMessageRepository(this);

        adapter = new LexiMessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);   // start at the newest message
        binding.rvLexi.setLayoutManager(layoutManager);
        binding.rvLexi.setAdapter(adapter);

        loadMessages();
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed() && binding != null;
    }

    private void loadMessages() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvEmpty.setVisibility(View.GONE);

        repo.loadAll(new SupabaseApi.Callback<List<SystemMessage>>() {
            @Override
            public void onSuccess(List<SystemMessage> messages) {
                if (!alive()) return;
                binding.progress.setVisibility(View.GONE);
                adapter.setItems(messages);

                if (messages.isEmpty()) {
                    binding.tvEmpty.setText("No messages from Lexi yet.");
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }

                binding.rvLexi.scrollToPosition(messages.size() - 1);

                // The NEW tags stay on screen this time; the unread count clears now.
                repo.markAllRead();
            }

            @Override
            public void onError(String message) {
                if (!alive()) return;
                binding.progress.setVisibility(View.GONE);
                binding.tvEmpty.setText(message);
                binding.tvEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(LexiMessagesActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
