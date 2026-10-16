package com.amplify.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.ChatKeyManager;
import com.amplify.app.data.SessionManager;
import com.amplify.app.databinding.ActivityLoginBinding;
import com.amplify.app.ui.main.MainActivity;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Log In screen (Use Case 2 in the reference document).
 *
 * Signs in with Supabase, saves the login, and then sets up the person's private message
 * keys with their password (ChatKeyManager.prepareAfterLogin). Without that second step
 * private messages cannot be sent, so the main screen only opens once it has finished.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;

    // Supabase project URL
    private static final String SUPABASE_URL =
            "https://pggwokpdcqtigxmcrtxh.supabase.co";

    // Supabase publishable key
    private static final String SUPABASE_KEY =
            "sb_publishable_yoqGxzzEd6H2iRWyWckd-g_RkHgxhqC";

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Amplify");
        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ResetPasswordActivity.class)));

        binding.tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        binding.btnLogIn.setOnClickListener(v -> loginUser());
        }

    /** Opens the main screen (the part that used to run straight after login). */
    private void openMain(String accessToken) {
        if (isFinishing() || isDestroyed()) return;

        Intent intent = new Intent(this, MainActivity.class);

        if (!accessToken.isEmpty()) {
            intent.putExtra("access_token", accessToken);
        }

        // Clear everything behind (Welcome, Register...) so Back from the app leaves it
        // instead of returning to the Welcome screen.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(intent);
        finish();
    }

    /**
     * Sets up secure messaging right after a successful login. The login is already saved,
     * so if this fails the person still gets into the app (messaging stays off until the
     * next login) and sees why.
     */
    private void setUpMessageKeys(String password, String accessToken) {
        ChatKeyManager.prepareAfterLogin(this, password, new ChatKeyManager.Listener() {
            @Override
            public void onReady() {
                openMain(accessToken);
            }

            @Override
            public void onNeedsFreshStart(Runnable startFresh, Runnable skip) {
                showFreshStartDialog(startFresh, skip);
            }

            @Override
            public void onFailed(String message) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                }
                openMain(accessToken);
            }
        });
    }

    /**
     * Shown when the old message keys can't be opened with this password
     * (usually after a password reset).
     */
    private void showFreshStartDialog(Runnable startFresh, Runnable skip) {
        if (isFinishing() || isDestroyed()) {
            skip.run();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Start fresh with messages?")
                .setMessage("We couldn't unlock your old private messages with this password. "
                        + "This usually happens after a password reset.\n\n"
                        + "If you continue, new message keys are created. Your old messages "
                        + "can't be read again, but new ones will work normally.")
                .setCancelable(false)
                .setPositiveButton("Start fresh", (dialog, which) -> startFresh.run())
                .setNegativeButton("Not now", (dialog, which) -> skip.run())
                .show();
    }

    private void loginUser() {

        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        // Validate email
        if (email.isEmpty()) {
            binding.etEmail.setError("Please enter your email");
            binding.etEmail.requestFocus();
            return;
        }

        // Validate password
        if (password.isEmpty()) {
            binding.etPassword.setError("Please enter your password");
            binding.etPassword.requestFocus();
            return;
        }

        binding.btnLogIn.setEnabled(false);

        try {

            JSONObject body = new JSONObject();

            body.put("email", email);
            body.put("password", password);

            RequestBody requestBody =
                    RequestBody.create(body.toString(), JSON);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/token?grant_type=password")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {

                    runOnUiThread(() -> {

                        binding.btnLogIn.setEnabled(true);

                        Toast.makeText(
                                LoginActivity.this,
                                "Unable to connect to Supabase",
                                Toast.LENGTH_SHORT
                        ).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response)
                        throws IOException {

                    String responseBody = response.body() != null
                            ? response.body().string()
                            : "";

                    runOnUiThread(() -> {

                        // On success the button stays off until the message keys are ready.
                        if (!response.isSuccessful()) {
                            binding.btnLogIn.setEnabled(true);
                        }

                        if (response.isSuccessful()) {

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Login successful!",
                                    Toast.LENGTH_SHORT
                            ).show();

                            String accessToken = "";
                            String refreshToken = "";
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                accessToken = json.optString("access_token", "");
                                refreshToken = json.optString("refresh_token", "");
                            } catch (Exception ignored) {
                            }

                            if (accessToken.isEmpty() || refreshToken.isEmpty()) {
                                binding.btnLogIn.setEnabled(true);
                                Toast.makeText(
                                        LoginActivity.this,
                                        "Login problem. Please try again.",
                                        Toast.LENGTH_LONG
                                ).show();
                                return;
                            }

                            getSharedPreferences("app_prefs", MODE_PRIVATE)
                                    .edit()
                                    .putString("access_token", accessToken)
                                    .apply();

                            // Used by the AI chat (ChatRepository) to stay signed in
                            // and to refresh the token when it expires.
                            SessionManager.saveSession(
                                    LoginActivity.this, accessToken, refreshToken);

                            // Create or unlock the private message keys, then open the app.
                            setUpMessageKeys(password, accessToken);

                        } else {

                            String errorMessage =
                                    "Invalid email or password";

                            try {

                                JSONObject error =
                                        new JSONObject(responseBody);

                                if (error.has("msg")) {
                                    errorMessage =
                                            error.getString("msg");

                                } else if (error.has("message")) {
                                    errorMessage =
                                            error.getString("message");

                                } else if (error.has("error_description")) {
                                    errorMessage =
                                            error.getString(
                                                    "error_description"
                                            );
                                }

                            } catch (Exception ignored) {
                            }

                            Toast.makeText(
                                    LoginActivity.this,
                                    errorMessage,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                }
            });

        } catch (Exception e) {

            binding.btnLogIn.setEnabled(true);

            Toast.makeText(
                    this,
                    "Something went wrong. Please try again.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}




