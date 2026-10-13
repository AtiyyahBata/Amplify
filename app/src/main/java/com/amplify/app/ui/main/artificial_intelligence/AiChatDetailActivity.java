package com.amplify.app.ui.main.artificial_intelligence;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Screen 5: Open a Chat from History / Active AI Chat Detail view.
 * Displays existing chat messages and supports appending user questions & AI responses.
 */
public class AiChatDetailActivity extends AppCompatActivity {

    private LinearLayout llMessageContainer;
    private ScrollView scrollChat;
    private EditText etMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat_detail);

        String topic = getIntent().getStringExtra("CHAT_TOPIC");
        if (TextUtils.isEmpty(topic)) {
            topic = "App Development";
        }

        TextView tvTopicTitle = findViewById(R.id.tvTopicTitle);
        if (tvTopicTitle != null) {
            tvTopicTitle.setText(topic);
        }

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        llMessageContainer = findViewById(R.id.llMessageContainer);
        scrollChat = findViewById(R.id.scrollChat);
        etMessage = findViewById(R.id.etMessage);

        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String userText = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(userText)) {
            return;
        }

        addUserMessage(userText);
        etMessage.setText("");

        // Auto respond with AI message after short delay
        llMessageContainer.postDelayed(() -> addAiMessage("Here is helpful info regarding \"" + userText + "\":\n\n• Point 1: Key insight on this topic\n• Point 2: Recommended next steps\n• Point 3: Additional resources in Amplify"), 600);
    }

    private void addUserMessage(String message) {
        String time = getCurrentTime();

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
    }

    private void addAiMessage(String message) {
        String time = getCurrentTime();

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

        // Bubble
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_ai_bubble_them);
        bubble.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(10));

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bubble.setLayoutParams(bubbleParams);

        TextView tvText = new TextView(this);
        tvText.setText(message);
        tvText.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.ink));
        tvText.setTextSize(14.5f);

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

        bubble.addView(tvText);
        bubble.addView(tvTime);

        wrapper.addView(avatarFrame);
        wrapper.addView(bubble);

        llMessageContainer.addView(wrapper);
        scrollToBottom();
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
