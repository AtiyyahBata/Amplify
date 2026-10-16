package com.amplify.app.ui.main.artificial_intelligence;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.R;
import com.amplify.app.data.ChatRepository;

/**
 * Screen 7: Delete Old Chats Confirmation Activity.
 * Asks user for confirmation before permanently clearing ALL of their saved chats in Supabase.
 */
public class AiDeleteHistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_delete_history);

        ChatRepository repository = new ChatRepository(this);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());

        View btnDeleteAll = findViewById(R.id.btnDeleteAll);
        btnDeleteAll.setOnClickListener(v -> {
            btnDeleteAll.setEnabled(false);   // stop double taps while it runs
            repository.deleteAllChats(new ChatRepository.ResultCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    if (isFinishing()) return;
                    setResult(RESULT_OK);
                    Toast.makeText(AiDeleteHistoryActivity.this,
                            "All chat history deleted", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    if (isFinishing()) return;
                    btnDeleteAll.setEnabled(true);
                    Toast.makeText(AiDeleteHistoryActivity.this,
                            message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
