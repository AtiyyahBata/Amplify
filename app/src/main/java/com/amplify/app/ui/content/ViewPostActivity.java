package com.amplify.app.ui.content;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.DummyData;
import com.amplify.app.databinding.ActivityViewPostBinding;
import com.amplify.app.databinding.ItemCommentBinding;
import com.amplify.app.models.Comment;
import com.amplify.app.ui.messaging.NewMessageActivity;

/**
 * View Post screen (Use Case: View and Watch a Video / Contact a Content Creator).
 * The post shown is a placeholder from DummyData - a real version would receive
 * the tapped Post's id via the Intent extras and load it from Firestore.
 */
public class ViewPostActivity extends AppCompatActivity {

    private ActivityViewPostBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityViewPostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Post");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.tvPostBody.setText("Most junior developers scroll straight to the bottom of a crash log " +
                "looking for red text. Start at the first line that mentions your own package name instead " +
                "- that's almost always where the real problem lives. Everything above it is usually " +
                "framework noise.");
        binding.tvTagOne.setText("debugging");
        binding.tvTagTwo.setText("android");

        binding.tvAvatar.setOnClickListener(v -> openOtherProfile());
        binding.tvAuthorName.setOnClickListener(v -> openOtherProfile());

        binding.btnMessageCreator.setOnClickListener(v ->
                startActivity(new Intent(this, NewMessageActivity.class)));

        loadComments();
    }

    private void openOtherProfile() {
        startActivity(new Intent(this, ProfileOtherActivity.class));
    }

    private void loadComments() {
        for (Comment comment : DummyData.getCommentsForPost()) {
            ItemCommentBinding commentBinding = ItemCommentBinding.inflate(
                    LayoutInflater.from(this), binding.commentContainer, false);
            commentBinding.tvCommentAuthor.setText(comment.getAuthorName());
            commentBinding.tvCommentTime.setText(comment.getTimeAgo());
            commentBinding.tvCommentBody.setText(comment.getBody());
            binding.commentContainer.addView(commentBinding.getRoot());
        }
    }
}
