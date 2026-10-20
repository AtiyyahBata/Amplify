package com.amplify.app.ui.messaging;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.DirectMessageAdapter;
import com.amplify.app.data.MessagingRealtime;
import com.amplify.app.data.MessagingRepository;
import com.amplify.app.data.SessionManager;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.ActivityConversationBinding;
import com.amplify.app.models.DirectMessage;
import com.amplify.app.models.PersonResult;

import org.json.JSONObject;

import java.util.List;

/**
 * One open private chat. Messages are encrypted on this phone before they leave it, and new
 * messages from the other person appear straight away through the live connection.
 *
 * Open it with ConversationActivity.open(context, conversationId, otherUserId, otherName).
 */
public class ConversationActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_OTHER_USER_ID = "other_user_id";
    public static final String EXTRA_OTHER_NAME = "other_name";
    public static final String EXTRA_INITIAL_MESSAGE = "initial_message";

    private static final int MAX_LENGTH = 4000;
    private static final String STATE_INITIAL_MESSAGE_SENT = "initial_message_sent";

    public static void open(Context context, String conversationId, String otherUserId,
                            String otherName) {
        open(context, conversationId, otherUserId, otherName, null);
    }

    public static void open(Context context, String conversationId, String otherUserId,
                            String otherName, String initialMessage) {
        Intent intent = new Intent(context, ConversationActivity.class);
        intent.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        intent.putExtra(EXTRA_OTHER_USER_ID, otherUserId);
        intent.putExtra(EXTRA_OTHER_NAME, otherName);
        if (initialMessage != null && !initialMessage.isEmpty()) {
            intent.putExtra(EXTRA_INITIAL_MESSAGE, initialMessage);
        }
        context.startActivity(intent);
    }

    private ActivityConversationBinding binding;
    private MessagingRepository repository;
    private DirectMessageAdapter adapter;
    private LinearLayoutManager layoutManager;

    private String conversationId;
    private String otherUserId;
    private String otherName;
    private String myId;
    private String initialMessage;
    private boolean initialMessageSent;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed = false;
    private boolean loadedOnce = false;
    private boolean loading = false;
    private boolean reloadAgain = false;
    private int pendingSends = 0;

    private final MessagingRealtime.Listener realtimeListener = new MessagingRealtime.Listener() {
        @Override
        public void onChange(String table, String type, JSONObject record, JSONObject oldRecord) {
            handleChange(table, type, record, oldRecord);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        otherUserId = getIntent().getStringExtra(EXTRA_OTHER_USER_ID);
        otherName = getIntent().getStringExtra(EXTRA_OTHER_NAME);
        initialMessage = getIntent().getStringExtra(EXTRA_INITIAL_MESSAGE);
        initialMessageSent = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_INITIAL_MESSAGE_SENT, false);
        myId = SessionManager.getUserId(this);

        if (conversationId == null || otherUserId == null || myId == null) {
            Toast.makeText(this, "This chat can't be opened.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (otherName == null || otherName.isEmpty()) otherName = "Chat";

        repository = new MessagingRepository(this);

        binding.tvContactName.setText(otherName);
        binding.tvAvatar.setText(PersonResult.initialsOf(otherName));
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnChatMenu.setOnClickListener(this::showMenu);

        adapter = new DirectMessageAdapter();
        adapter.setOnMessageLongClickListener(this::confirmDeleteMessage);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);

        // When the keyboard opens the list gets shorter. Keep the newest message in view.
        binding.rvMessages.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (bottom < oldBottom && adapter.getItemCount() > 0) {
                        binding.rvMessages.post(this::scrollToEnd);
                    }
                });

        binding.btnSendChat.setOnClickListener(v -> send());

        showStatus("Loading…");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (repository == null) return;
        // Keeps the live connection open while this screen is showing.
        MessagingRealtime realtime = MessagingRealtime.get(this);
        realtime.acquire();
        realtime.addListener(realtimeListener);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        maybeMarkRead();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (repository == null) return;
        MessagingRealtime realtime = MessagingRealtime.get(this);
        realtime.removeListener(realtimeListener);
        realtime.release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_INITIAL_MESSAGE_SENT, initialMessageSent);
        super.onSaveInstanceState(outState);
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    private void load() {
        if (loading) {
            reloadAgain = true;
            return;
        }
        loading = true;
        repository.loadMessages(conversationId, new SupabaseApi.Callback<List<DirectMessage>>() {
            @Override
            public void onSuccess(List<DirectMessage> result) {
                loading = false;
                if (isFinishing()) return;
                boolean nearEnd = isNearEnd();
                if (!loadedOnce) {
                    adapter.setMessages(result);
                    loadedOnce = true;
                    scrollToEnd();
                    sendInitialMessageIfNeeded();
                } else {
                    adapter.merge(result);
                    if (nearEnd) scrollToEnd();
                }
                updateStatus();
                maybeMarkRead();
                if (reloadAgain) {
                    reloadAgain = false;
                    load();
                }
            }

            @Override
            public void onError(String message) {
                loading = false;
                reloadAgain = false;
                if (isFinishing()) return;
                if (!loadedOnce) showStatus(message);
            }
        });
    }

    private void sendInitialMessageIfNeeded() {
        if (initialMessageSent || initialMessage == null || initialMessage.isEmpty()) return;
        initialMessageSent = true;
        binding.etChatInput.setText(initialMessage);
        send();
    }

    private void updateStatus() {
        if (adapter.getItemCount() == 0) {
            showStatus("No messages yet. Say hi!");
        } else {
            binding.tvChatStatus.setVisibility(View.GONE);
        }
    }

    private void showStatus(String text) {
        binding.tvChatStatus.setText(text);
        binding.tvChatStatus.setVisibility(View.VISIBLE);
    }

    private boolean isNearEnd() {
        int count = adapter.getItemCount();
        if (count == 0) return true;
        return layoutManager.findLastVisibleItemPosition() >= count - 2;
    }

    private void scrollToEnd() {
        if (adapter.getItemCount() > 0) {
            binding.rvMessages.scrollToPosition(adapter.lastPosition());
        }
    }

    /** Tells the server (and so the other person's ticks) that I have seen their messages. */
    private void maybeMarkRead() {
        if (!resumed || adapter == null || !adapter.hasUnreadIncoming()) return;
        adapter.markIncomingRead();
        repository.markRead(conversationId);
    }

    // ------------------------------------------------------------------
    // Live changes
    // ------------------------------------------------------------------

    private void handleChange(String table, String type, JSONObject record, JSONObject oldRecord) {
        if (isFinishing() || adapter == null) return;

        if ("poll".equals(table)) {
            load();
            return;
        }

        if ("conversations".equals(table)) {
            if ("DELETE".equals(type) && oldRecord != null
                    && conversationId.equals(oldRecord.optString("id", ""))) {
                Toast.makeText(this, "This chat was deleted.", Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        }

        if (!"direct_messages".equals(table) || record == null) return;
        if (!conversationId.equals(record.optString("conversation_id", ""))) return;

        if ("INSERT".equals(type)) {
            boolean fromMe = myId.equals(record.optString("sender_id", ""));
            // My own message is added when the send finishes, so skip it while sending.
            if (fromMe && pendingSends > 0) return;

            final boolean nearEnd = isNearEnd();
            repository.decodeRowAsync(record, new SupabaseApi.Callback<DirectMessage>() {
                @Override
                public void onSuccess(DirectMessage message) {
                    if (isFinishing()) return;
                    adapter.addMessage(message);
                    binding.tvChatStatus.setVisibility(View.GONE);
                    if (nearEnd || message.isMine()) scrollToEnd();
                    maybeMarkRead();
                }

                @Override
                public void onError(String message) {
                    load();
                }
            });
        } else if ("UPDATE".equals(type)) {
            String id = record.optString("id", "");
            boolean read = !record.isNull("read_at") && record.has("read_at");
            boolean deleted = !record.isNull("deleted_at") && record.has("deleted_at");
            adapter.applyUpdate(id, read, deleted);
        }
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    private void send() {
        final String text = binding.etChatInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        if (text.length() > MAX_LENGTH) {
            Toast.makeText(this, "That message is too long.", Toast.LENGTH_SHORT).show();
            return;
        }

        final long now = System.currentTimeMillis();
        final String tempId = "tmp-" + System.nanoTime();
        adapter.addMessage(DirectMessage.pending(tempId, myId, text, now,
                MessagingRepository.clockTime(now)));
        binding.tvChatStatus.setVisibility(View.GONE);
        scrollToEnd();
        binding.etChatInput.setText("");
        pendingSends++;

        repository.sendMessage(conversationId, otherUserId, text,
                new SupabaseApi.Callback<DirectMessage>() {
                    @Override
                    public void onSuccess(DirectMessage saved) {
                        pendingSends--;
                        if (isFinishing()) return;
                        adapter.confirmSent(tempId, saved);
                        scrollToEnd();
                    }

                    @Override
                    public void onError(String message) {
                        pendingSends--;
                        if (isFinishing()) return;
                        adapter.removeById(tempId);
                        updateStatus();
                        // Give the text back so nothing is lost.
                        if (binding.etChatInput.getText().toString().isEmpty()) {
                            binding.etChatInput.setText(text);
                            binding.etChatInput.setSelection(text.length());
                        }
                        Toast.makeText(ConversationActivity.this, message,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Deleting
    // ------------------------------------------------------------------

    private void confirmDeleteMessage(DirectMessage message) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this message?")
                .setMessage("It will show as “This message was deleted” for both of you.")
                .setPositiveButton("Delete", (d, w) ->
                        repository.deleteMessage(message.getId(),
                                new SupabaseApi.Callback<Boolean>() {
                                    @Override
                                    public void onSuccess(Boolean result) {
                                        if (!isFinishing()) adapter.markDeleted(message.getId());
                                    }

                                    @Override
                                    public void onError(String error) {
                                        Toast.makeText(ConversationActivity.this, error,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Delete chat");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                confirmDeleteChat();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void confirmDeleteChat() {
        new AlertDialog.Builder(this)
                .setTitle("Delete chat with " + otherName + "?")
                .setMessage("This deletes the whole chat for both of you. It can't be undone.")
                .setPositiveButton("Delete", (d, w) ->
                        repository.deleteConversation(conversationId,
                                new SupabaseApi.Callback<Boolean>() {
                                    @Override
                                    public void onSuccess(Boolean result) {
                                        finish();
                                    }

                                    @Override
                                    public void onError(String error) {
                                        Toast.makeText(ConversationActivity.this, error,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
