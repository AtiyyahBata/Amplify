
package com.amplify.app.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.R;
import com.amplify.app.data.SessionManager;
import com.amplify.app.ui.onboarding.WelcomeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AdminLogin extends AppCompatActivity {

    private static final String SUPABASE_URL =
            "https://pggwokpdcqtigxmcrtxh.supabase.co";

    // Use the same publishable key as the rest of your app.
    private static final String SUPABASE_KEY =
            "sb_publishable_yoqGxzzEd6H2iRWyWckd-g_RkHgxhqC";

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    private String refreshToken = "";

    private android.widget.EditText emailInput;
    private android.widget.EditText passwordInput;
    private android.widget.Button signInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        emailInput = findViewById(R.id.etAdminEmail);
        passwordInput = findViewById(R.id.etAdminPassword);
        signInButton = findViewById(R.id.btnAdminSignIn);

        findViewById(R.id.btnAdminBack).setOnClickListener(v -> {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
        });

        signInButton.setOnClickListener(v -> signInAdmin());
    }

    private void signInAdmin() {

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please enter your email and password.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        signInButton.setEnabled(false);
        signInButton.setText("Signing in...");

        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL
                            + "/auth/v1/token?grant_type=password")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(
                        Call call,
                        IOException e) {

                    runOnUiThread(() -> {
                        resetButton();
                        Toast.makeText(
                                AdminLogin.this,
                                "Unable to connect. Check your internet.",
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }

                @Override
                public void onResponse(
                        Call call,
                        Response response) throws IOException {

                    String responseBody = response.body() != null
                            ? response.body().string()
                            : "";

                    if (!response.isSuccessful()) {
                        runOnUiThread(() -> {
                            resetButton();
                            Toast.makeText(
                                    AdminLogin.this,
                                    "Sign-in failed. Check your credentials.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                        return;
                    }

                    try {
                        JSONObject result =
                                new JSONObject(responseBody);

                        String accessToken =
                                result.getString("access_token");

                        refreshToken = result.optString("refresh_token", "");

                        String userId = result
                                .getJSONObject("user")
                                .getString("id");

                        checkAdminRole(accessToken, userId);

                    } catch (Exception e) {
                        Log.e("AdminLogin", "Invalid login response", e);

                        runOnUiThread(() -> {
                            resetButton();
                            Toast.makeText(
                                    AdminLogin.this,
                                    "Unable to verify your account.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
            });

        } catch (Exception e) {
            resetButton();
            Toast.makeText(
                    this,
                    "Unable to start sign-in.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void checkAdminRole(
            String accessToken,
            String userId) {

        String url = SUPABASE_URL
                + "/rest/v1/profiles"
                + "?select=user_type,account_status"
                + "&id=eq." + userId;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    resetButton();
                    Toast.makeText(
                            AdminLogin.this,
                            "Could not verify administrator access.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }

            @Override
            public void onResponse(
                    Call call,
                    Response response) throws IOException {

                String body = response.body() != null
                        ? response.body().string()
                        : "";

                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        resetButton();
                        Toast.makeText(
                                AdminLogin.this,
                                "Could not verify account permissions.",
                                Toast.LENGTH_LONG
                        ).show();
                    });
                    return;
                }

                try {
                    JSONArray profiles = new JSONArray(body);

                    if (profiles.length() == 0) {
                        denyAdminAccess();
                        return;
                    }

                    JSONObject profile = profiles.getJSONObject(0);

                    String role = profile.optString("user_type", "");
                    String status = profile.optString(
                            "account_status", "Active");

                    if (!"Admin".equalsIgnoreCase(role)) {
                        denyAdminAccess();
                        return;
                    }

                    if (!"Active".equalsIgnoreCase(status)) {
                        runOnUiThread(() -> {
                            resetButton();
                            Toast.makeText(
                                    AdminLogin.this,
                                    "This account is not active.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                        return;
                    }

                    runOnUiThread(() -> {
                        SessionManager.saveSession(AdminLogin.this, accessToken, refreshToken);

                        Intent intent = new Intent(
                                AdminLogin.this,
                                AdminMainActivity.class
                        );

                        intent.putExtra("access_token", accessToken);
                        intent.setFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        );

                        startActivity(intent);
                        finish();
                    });

                } catch (Exception e) {
                    Log.e("AdminLogin", "Profile verification failed", e);
                    runOnUiThread(() -> {
                        resetButton();
                        Toast.makeText(
                                AdminLogin.this,
                                "Unable to verify account permissions.",
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }
            }
        });
    }

    private void denyAdminAccess() {
        runOnUiThread(() -> {
            resetButton();
            Toast.makeText(
                    AdminLogin.this,
                    "This account is not authorised for admin access.",
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private void resetButton() {
        runOnUiThread(() -> {
            signInButton.setEnabled(true);
            signInButton.setText("Sign in to dashboard");
        });
    }
}
