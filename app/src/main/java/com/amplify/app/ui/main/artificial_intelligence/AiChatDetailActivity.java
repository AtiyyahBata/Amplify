package com.amplify.app.ui.main.artificial_intelligence;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.R;
import com.amplify.app.data.ChatRepository;
import com.amplify.app.models.AiMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Screen 5: Open a Chat from History / Active AI Chat Detail view.
 *
 * Intent extras it understands:
 *   CHAT_ID         existing chat to resume (omit for a brand new chat)
 *   CHAT_TOPIC      title shown in the top bar
 *   INITIAL_MESSAGE sent to the AI automatically as soon as the screen opens
 *   PREFILL         text placed in the input box without sending
 *
 * The AI's answer streams in word by word. Until the first words arrive, three bouncing
 * dots are shown (like the "typing..." indicator in messaging apps).
 * Messages are saved in Supabase by the ai-chat Edge Function, so nothing is stored here.
 */
public class AiChatDetailActivity extends AppCompatActivity {

    /** One AI bubble: the avatar + text bubble, plus its optional bouncing dots. */
    private static class AiBubble {
        View wrapper;
        LinearLayout bubble;
        TextView tvText;
        LinearLayout dotsRow;
        final List<ObjectAnimator> animators = new ArrayList<>();
        boolean hasText = false;
    }

    private LinearLayout llMessageContainer;
    private ScrollView scrollChat;
    private EditText etMessage;

    private ChatRepository repository;
    private String chatId;          // null until the first answer has been saved
    private boolean sending = false;
    private AiBubble activeBubble;  // the bubble currently being filled, if any

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat_detail);

        repository = new ChatRepository(this);

        chatId = getIntent().getStringExtra("CHAT_ID");
        String topic = getIntent().getStringExtra("CHAT_TOPIC");
        if (TextUtils.isEmpty(topic)) {
            topic = "New chat";
        }

        TextView tvTopicTitle = findViewById(R.id.tvTopicTitle);
        if (tvTopicTitle != null) {
            tvTopicTitle.setText(topic);
        }

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        ImageView btnMore = findViewById(R.id.btnMore);
        if (btnMore != null) {
            btnMore.setOnClickListener(v -> confirmDeleteThisChat());
        }

        llMessageContainer = findViewById(R.id.llMessageContainer);
        scrollChat = findViewById(R.id.scrollChat);
        etMessage = findViewById(R.id.etMessage);

        // Remove the mock bubbles that are written inside the XML layout.
        llMessageContainer.removeAllViews();

        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                etMessage.setText("");
                sendMessage(text);
            }
        });

        String prefill = getIntent().getStringExtra("PREFILL");
        if (!TextUtils.isEmpty(prefill)) {
            etMessage.setText(prefill);
            etMessage.setSelection(prefill.length());
        }

        if (!TextUtils.isEmpty(chatId)) {
            loadExistingMessages();
        } else {
            String initial = getIntent().getStringExtra("INITIAL_MESSAGE");
            if (!TextUtils.isEmpty(initial)) {
                sendMessage(initial);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop the dot animations so nothing keeps running after the screen is gone.
        if (activeBubble != null) {
            stopDots(activeBubble);
        }
    }

    // ------------------------------------------------------------------
    // Resume an old chat
    // ------------------------------------------------------------------
    private void loadExistingMessages() {
        final View loading = addAiMessage("Loading chat...", "");
        repository.loadMessages(chatId, new ChatRepository.ResultCallback<List<AiMessage>>() {
            @Override
            public void onSuccess(List<AiMessage> messages) {
                if (isFinishing()) return;
                llMessageContainer.removeView(loading);
                for (AiMessage m : messages) {
                    if (m.isUser()) {
                        addUserMessage(m.getContent(), m.getTime());
                    } else {
                        addAiMessage(m.getContent(), m.getTime());
                    }
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                llMessageContainer.removeView(loading);
                Toast.makeText(AiChatDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // Send a message to the AI and show the answer as it arrives
    // ------------------------------------------------------------------
    private void sendMessage(String userText) {
        if (sending) {
            Toast.makeText(this, "Please wait for the AI to finish first", Toast.LENGTH_SHORT).show();
            etMessage.setText(userText);
            return;
        }
        sending = true;

        final View userBubble = addUserMessage(userText, getCurrentTime());
        final AiBubble aiBubble = createAiBubble(true);   // starts with bouncing dots
        activeBubble = aiBubble;

        repository.sendMessageStream(chatId, userText, new ChatRepository.StreamCallback() {
            @Override
            public void onText(String chunk) {
                if (isFinishing() || isDestroyed()) return;
                appendText(aiBubble, chunk);
            }

            @Override
            public void onDone(String savedChatId) {
                sending = false;
                activeBubble = null;
                if (!TextUtils.isEmpty(savedChatId)) {
                    chatId = savedChatId;   // later messages continue this same chat
                }
                if (isFinishing() || isDestroyed()) return;
                finishBubble(aiBubble, getCurrentTime());
            }

            @Override
            public void onError(String message) {
                sending = false;
                activeBubble = null;
                if (isFinishing() || isDestroyed()) return;
                // Nothing was saved, so take the bubbles back and let the user retry.
                stopDots(aiBubble);
                llMessageContainer.removeView(aiBubble.wrapper);
                llMessageContainer.removeView(userBubble);
                etMessage.setText(userText);
                etMessage.setSelection(userText.length());
                Toast.makeText(AiChatDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // Delete this chat (three-dots button in the top bar)
    // ------------------------------------------------------------------
    private void confirmDeleteThisChat() {
        if (TextUtils.isEmpty(chatId)) {
            Toast.makeText(this, "This chat has not been saved yet", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete this chat?")
                .setMessage("This will permanently delete this conversation.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        repository.deleteChat(chatId, new ChatRepository.ResultCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                Toast.makeText(AiChatDetailActivity.this,
                                        "Chat deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(AiChatDetailActivity.this,
                                        message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }

    // ------------------------------------------------------------------
    // Bubbles
    // ------------------------------------------------------------------
    private View addUserMessage(String message, String time) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.END);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapperParams.bottomMargin = dpToPx(16);
        wrapper.setLayoutParams(wrapperParams);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_ai_bubble_me);
        bubble.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(10));

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bubble.setLayoutParams(bubbleParams);

        TextView tvText = new TextView(this);
        tvText.setText(message);
        tvText.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
        tvText.setTextSize(15);
        tvText.setTextIsSelectable(true);

        TextView tvTime = new TextView(this);
        tvTime.setText(time + " ✓✓");
        tvTime.setTextColor(0xFFDCD5FF);
        tvTime.setTextSize(11);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.gravity = Gravity.END;
        timeParams.topMargin = dpToPx(4);
        tvTime.setLayoutParams(timeParams);

        bubble.addView(tvText);
        bubble.addView(tvTime);
        wrapper.addView(bubble);

        llMessageContainer.addView(wrapper);
        scrollToBottom();
        return wrapper;
    }

    /** A finished AI message (used for loaded history). */
    private View addAiMessage(String message, String time) {
        AiBubble b = createAiBubble(false);
        b.tvText.setText(message);
        b.tvText.setTextIsSelectable(true);
        b.hasText = true;
        if (!TextUtils.isEmpty(time)) {
            addTimeLabel(b, time);
        }
        scrollToBottom();
        return b.wrapper;
    }

    /**
     * Builds an AI bubble (robot avatar + bubble) and adds it to the chat.
     * With showDots = true the bubble starts with three bouncing dots and an empty,
     * hidden text area that appendText() fills in.
     */
    private AiBubble createAiBubble(boolean showDots) {
        AiBubble b = new AiBubble();

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.START);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapperParams.bottomMargin = dpToPx(16);
        wrapper.setLayoutParams(wrapperParams);

        // Robot Avatar
        android.widget.FrameLayout avatarFrame = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
        avatarParams.rightMargin = dpToPx(10);
        avatarParams.gravity = Gravity.TOP;
        avatarFrame.setLayoutParams(avatarParams);
        avatarFrame.setBackgroundResource(R.drawable.bg_ai_floating_button);

        ImageView avatarIcon = new ImageView(this);
        avatarIcon.setImageResource(R.drawable.ic_ai_robot_avatar);
        android.widget.FrameLayout.LayoutParams iconParams = new android.widget.FrameLayout.LayoutParams(dpToPx(20), dpToPx(20));
        iconParams.gravity = Gravity.CENTER;
        avatarIcon.setLayoutParams(iconParams);
        avatarFrame.addView(avatarIcon);

        // Bubble (leaves room on the right so long answers wrap instead of running off screen)
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_ai_bubble_them);
        bubble.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(10));

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bubbleParams.rightMargin = dpToPx(40);
        bubble.setLayoutParams(bubbleParams);

        TextView tvText = new TextView(this);
        tvText.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.ink));
        tvText.setTextSize(14.5f);
        bubble.addView(tvText);

        b.wrapper = wrapper;
        b.bubble = bubble;
        b.tvText = tvText;

        if (showDots) {
            tvText.setVisibility(View.GONE);
            b.dotsRow = buildDotsRow();
            bubble.addView(b.dotsRow, 0);
            startDots(b);
        }

        wrapper.addView(avatarFrame);
        wrapper.addView(bubble);
        llMessageContainer.addView(wrapper);
        scrollToBottom();
        return b;
    }

    // ------------------------------------------------------------------
    // Bouncing dots ("the AI is thinking")
    // ------------------------------------------------------------------
    private LinearLayout buildDotsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpToPx(8), 0, dpToPx(8));

        for (int i = 0; i < 3; i++) {
            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextSize(12);
            dot.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.muted));
            dot.setPadding(dpToPx(3), 0, dpToPx(3), 0);
            row.addView(dot);
        }
        return row;
    }

    private void startDots(AiBubble b) {
        if (b.dotsRow == null) return;
        for (int i = 0; i < b.dotsRow.getChildCount(); i++) {
            View dot = b.dotsRow.getChildAt(i);
            ObjectAnimator bounce = ObjectAnimator.ofFloat(dot, "translationY", 0f, -dpToPx(6));
            bounce.setDuration(380);
            bounce.setStartDelay(i * 140L);              // each dot jumps a moment after the last
            bounce.setRepeatCount(ObjectAnimator.INFINITE);
            bounce.setRepeatMode(ObjectAnimator.REVERSE);
            bounce.setInterpolator(new AccelerateDecelerateInterpolator());
            bounce.start();
            b.animators.add(bounce);
        }
    }

    private void stopDots(AiBubble b) {
        for (ObjectAnimator a : b.animators) {
            a.cancel();
        }
        b.animators.clear();
        if (b.dotsRow != null) {
            b.dotsRow.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------
    // Live text
    // ------------------------------------------------------------------
    /** Adds a new piece of the answer. The first piece swaps the dots for the text. */
    private void appendText(AiBubble b, String chunk) {
        if (!b.hasText) {
            stopDots(b);
            b.tvText.setVisibility(View.VISIBLE);
            b.hasText = true;
        }
        b.tvText.append(chunk);
        scrollToBottom();
    }

    /** The answer is complete: add the time and make the text selectable. */
    private void finishBubble(AiBubble b, String time) {
        stopDots(b);
        if (!b.hasText) {
            b.tvText.setVisibility(View.VISIBLE);
        }
        b.tvText.setTextIsSelectable(true);
        addTimeLabel(b, time);
        scrollToBottom();
    }

    private void addTimeLabel(AiBubble b, String time) {
        TextView tvTime = new TextView(this);
        tvTime.setText(time);
        tvTime.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.muted));
        tvTime.setTextSize(11);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.gravity = Gravity.END;
        timeParams.topMargin = dpToPx(4);
        tvTime.setLayoutParams(timeParams);
        b.bubble.addView(tvTime);
    }

    private void scrollToBottom() {
        if (scrollChat != null) {
            scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
        }
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}