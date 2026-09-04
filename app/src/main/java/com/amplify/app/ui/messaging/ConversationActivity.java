package com.amplify.app.ui.messaging;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.DummyData;
import com.amplify.app.databinding.ActivityConversationBinding;
import com.amplify.app.databinding.ItemBubbleMeBinding;
import com.amplify.app.databinding.ItemBubbleThemBinding;
import com.amplify.app.models.ChatMessage;

/**
 * Conversation screen - one open thread (Use Case: Contact a Content Creator,
 * Messages screens in Part G.3/G.4). Messages typed here are added to the
 * screen only; they are not saved to Firestore or sent to anyone.
 */
public class ConversationActivity extends AppCompatActivity {

    private ActivityConversationBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        for (ChatMessage message : DummyData.getConversationThread()) {
            addBubble(message.getBody(), message.getType());
        }

        binding.btnSendChat.setOnClickListener(v -> {
            String text = binding.etChatInput.getText().toString().trim();
            if (TextUtils.isEmpty(text)) return;
            addBubble(text, ChatMessage.TYPE_ME);
            binding.etChatInput.setText("");
        });
    }

    private void addBubble(String text, int type) {
        View bubbleView;
        if (type == ChatMessage.TYPE_ME) {
            ItemBubbleMeBinding bubbleBinding = ItemBubbleMeBinding.inflate(
                    LayoutInflater.from(this), binding.chatContainer, false);
            bubbleBinding.tvBubbleText.setText(text);
            bubbleView = bubbleBinding.getRoot();
        } else {
            ItemBubbleThemBinding bubbleBinding = ItemBubbleThemBinding.inflate(
                    LayoutInflater.from(this), binding.chatContainer, false);
            bubbleBinding.tvBubbleText.setText(text);
            bubbleView = bubbleBinding.getRoot();
        }
        binding.chatContainer.addView(bubbleView);
        binding.chatScroll.post(() -> binding.chatScroll.fullScroll(View.FOCUS_DOWN));
    }
}
