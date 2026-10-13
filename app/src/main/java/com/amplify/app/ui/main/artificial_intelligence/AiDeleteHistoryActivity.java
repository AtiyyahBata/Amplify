package com.amplify.app.ui.main.artificial_intelligence;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.R;
import com.amplify.app.data.DummyData;

/**
 * Screen 7: Delete Old Chats Confirmation Activity.
 * Asks user for confirmation before permanently clearing chat history.
 */
public class AiDeleteHistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_delete_history);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());

        findViewById(R.id.btnDeleteAll).setOnClickListener(v -> {
            DummyData.getAiChatHistory().clear();
            setResult(RESULT_OK);
            Toast.makeText(this, "All chat history deleted", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
