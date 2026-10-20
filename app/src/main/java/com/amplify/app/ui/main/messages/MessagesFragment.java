package com.amplify.app.ui.main.messages;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.MessagePreviewAdapter;
import com.amplify.app.data.MessagingRealtime;
import com.amplify.app.data.MessagingRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.data.SystemMessageRepository;
import com.amplify.app.databinding.FragmentMessagesBinding;
import com.amplify.app.models.MessagePreview;
import com.amplify.app.ui.messaging.ConversationActivity;
import com.amplify.app.ui.messaging.LexiMessagesActivity;
import com.amplify.app.ui.messaging.NewMessageActivity;

import org.json.JSONObject;

import java.util.List;

/**
 * Messages tab. Shows your real private chats, newest first, with an unread count on
 * each one. Tap a row to open it, long-press to delete the chat, + to start a new one.
 */
public class MessagesFragment extends Fragment {

    private static final String EMPTY_TEXT =
            "No messages yet.\nTap + to start a chat with someone.";

    private FragmentMessagesBinding binding;
    private MessagePreviewAdapter adapter;
    private MessagingRepository repository;
    private SystemMessageRepository systemRepository;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int loadCounter = 0;
    private boolean loadedOnce = false;

    private final Runnable reloadRunnable = this::reload;

    private final MessagingRealtime.Listener realtimeListener = new MessagingRealtime.Listener() {
        @Override
        public void onChange(String table, String type, JSONObject record, JSONObject oldRecord) {
            // Wait a moment so a burst of changes only causes one reload.
            handler.removeCallbacks(reloadRunnable);
            handler.postDelayed(reloadRunnable, 250);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentMessagesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new MessagingRepository(requireContext());
        systemRepository = new SystemMessageRepository(requireContext());

        adapter = new MessagePreviewAdapter(null, preview ->
                ConversationActivity.open(requireContext(), preview.getConversationId(),
                        preview.getOtherUserId(), preview.getName()));
        adapter.setOnConversationLongClickListener(this::confirmDelete);

        binding.rvConversations.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvConversations.setAdapter(adapter);

        binding.btnNewMessage.setOnClickListener(v ->
                startActivity(new android.content.Intent(getActivity(), NewMessageActivity.class)));

        binding.lexiRow.setOnClickListener(v ->
                LexiMessagesActivity.open(requireContext()));
    }

    @Override
    public void onStart() {
        super.onStart();
        MessagingRealtime.get(requireContext()).addListener(realtimeListener);
        reload();
        loadLexi();
    }

    @Override
    public void onStop() {
        super.onStop();
        MessagingRealtime.get(requireContext()).removeListener(realtimeListener);
        handler.removeCallbacks(reloadRunnable);
    }

    private void reload() {
        if (binding == null || !isAdded()) return;
        final int myLoad = ++loadCounter;

        repository.loadConversations(new SupabaseApi.Callback<List<MessagePreview>>() {
            @Override
            public void onSuccess(List<MessagePreview> result) {
                if (binding == null || myLoad != loadCounter) return;
                loadedOnce = true;
                adapter.setItems(result);
                showEmpty(result.isEmpty() ? EMPTY_TEXT : null);
            }

            @Override
            public void onError(String message) {
                if (binding == null || myLoad != loadCounter) return;
                // Keep what is on screen. Only say something if there is nothing to show.
                if (adapter.isEmpty()) {
                    showEmpty(loadedOnce ? EMPTY_TEXT : message);
                }
            }
        });
    }

    /** Shows or hides the Lexi row, with its latest message and unread count. */
    private void loadLexi() {
        if (binding == null || !isAdded()) return;

        systemRepository.loadSummary(new SupabaseApi.Callback<SystemMessageRepository.Summary>() {
            @Override
            public void onSuccess(SystemMessageRepository.Summary s) {
                if (binding == null) return;

                if (!s.hasAny) {
                    binding.lexiRow.setVisibility(View.GONE);
                    return;
                }

                binding.lexiRow.setVisibility(View.VISIBLE);
                binding.tvLexiLastMessage.setText(s.preview);
                binding.tvLexiTime.setText(s.timeAgo);

                if (s.unread > 0) {
                    binding.tvLexiUnread.setText(s.unread > 99 ? "99+" : String.valueOf(s.unread));
                    binding.tvLexiUnread.setVisibility(View.VISIBLE);
                    binding.tvLexiLastMessage.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.tvLexiUnread.setVisibility(View.GONE);
                    binding.tvLexiLastMessage.setTypeface(null, Typeface.NORMAL);
                }
            }

            @Override
            public void onError(String message) {
                // Keep whatever is on screen. It tries again next time the tab opens.
            }
        });
    }

    private void showEmpty(String text) {
        if (binding == null) return;
        if (text == null) {
            binding.tvEmpty.setVisibility(View.GONE);
        } else {
            binding.tvEmpty.setText(text);
            binding.tvEmpty.setVisibility(View.VISIBLE);
        }
    }

    private void confirmDelete(MessagePreview preview) {
        if (!isAdded() || preview.getConversationId() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete chat with " + preview.getName() + "?")
                .setMessage("This deletes the whole chat for both of you. "
                        + "It can't be undone.")
                .setPositiveButton("Delete", (d, w) ->
                        repository.deleteConversation(preview.getConversationId(),
                                new SupabaseApi.Callback<Boolean>() {
                                    @Override
                                    public void onSuccess(Boolean result) {
                                        reload();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        if (getContext() != null) {
                                            Toast.makeText(getContext(), message,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    }
                                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        binding = null;
    }
}
