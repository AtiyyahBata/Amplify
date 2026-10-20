package com.amplify.app.ui.messaging;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.PersonAdapter;
import com.amplify.app.data.MessagingRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.ActivityNewMessageBinding;
import com.amplify.app.models.PersonResult;

import java.util.List;

/**
 * New Message screen. Search for someone by name and tap them to open your chat.
 *
 * Other screens (a profile, a post) can skip the search and jump straight into a chat by
 * passing the person's id and name:
 *   intent.putExtra(NewMessageActivity.EXTRA_USER_ID, id);
 *   intent.putExtra(NewMessageActivity.EXTRA_USER_NAME, name);
 */
public class NewMessageActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_USER_NAME = "user_name";
    public static final String EXTRA_INITIAL_MESSAGE = "initial_message";

    private static final int MIN_LETTERS = 2;
    private static final long DEBOUNCE_MS = 300;

    private ActivityNewMessageBinding binding;
    private MessagingRepository repository;
    private PersonAdapter adapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int searchCounter = 0;
    private boolean opening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNewMessageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new MessagingRepository(this);

        binding.header.tvHeaderTitle.setText("New message");
        binding.header.btnBack.setOnClickListener(v -> finish());

        adapter = new PersonAdapter(this::openChatWith);
        binding.rvPeople.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPeople.setAdapter(adapter);

        String directId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (directId != null && !directId.isEmpty()) {
            binding.etSearchPeople.setVisibility(View.GONE);
            binding.tvPeopleEmpty.setText("Opening chat…");
            openChat(directId, getIntent().getStringExtra(EXTRA_USER_NAME));
            return;
        }

        binding.tvPeopleEmpty.setText("Search for someone by their name.");

        binding.etSearchPeople.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final String term = s.toString().trim();
                handler.removeCallbacksAndMessages(null);
                searchCounter++;   // anything still on its way back is now out of date
                if (term.length() < MIN_LETTERS) {
                    adapter.setPeople(java.util.Collections.<PersonResult>emptyList());
                    showMessage("Search for someone by their name.");
                    return;
                }
                handler.postDelayed(() -> search(term), DEBOUNCE_MS);
            }
        });

        binding.etSearchPeople.requestFocus();
    }

    private void search(String term) {
        final int mySearch = ++searchCounter;
        repository.searchPeople(term, new SupabaseApi.Callback<List<PersonResult>>() {
            @Override
            public void onSuccess(List<PersonResult> result) {
                if (isFinishing() || mySearch != searchCounter) return;
                adapter.setPeople(result);
                showMessage(result.isEmpty() ? "No one found for “" + term + "”." : null);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || mySearch != searchCounter) return;
                adapter.setPeople(java.util.Collections.<PersonResult>emptyList());
                showMessage(message);
            }
        });
    }

    private void showMessage(String text) {
        if (text == null) {
            binding.tvPeopleEmpty.setVisibility(View.GONE);
        } else {
            binding.tvPeopleEmpty.setText(text);
            binding.tvPeopleEmpty.setVisibility(View.VISIBLE);
        }
    }

    private void openChatWith(PersonResult person) {
        openChat(person.getId(), person.getFullName());
    }

    private void openChat(String userId, String name) {
        if (opening) return;
        opening = true;
        final String displayName = (name == null || name.isEmpty()) ? "Chat" : name;

        repository.startConversation(userId, new SupabaseApi.Callback<String>() {
            @Override
            public void onSuccess(String conversationId) {
                if (isFinishing()) return;
                ConversationActivity.open(NewMessageActivity.this, conversationId,
                        userId, displayName,
                        getIntent().getStringExtra(EXTRA_INITIAL_MESSAGE));
                finish();
            }

            @Override
            public void onError(String message) {
                opening = false;
                if (isFinishing()) return;
                Toast.makeText(NewMessageActivity.this, message, Toast.LENGTH_LONG).show();
                if (getIntent().getStringExtra(EXTRA_USER_ID) != null) finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
