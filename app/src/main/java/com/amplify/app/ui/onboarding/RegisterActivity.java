package com.amplify.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.amplify.app.data.AccountRepository;
import com.amplify.app.data.SupabaseConfig;
import com.amplify.app.databinding.ActivityRegisterBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.header.tvHeaderTitle.setText("Amplify");

        binding.header.btnBack.setOnClickListener(v -> finish());

        binding.tvGoLogin.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        binding.btnRegister.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {

        // Get values from the registration form
        String fullName = binding.etfullName.getText()
                .toString()
                .trim();

        String email = binding.etEmail.getText()
                .toString()
                .trim();

        String password = binding.etPassword.getText()
                .toString();

        String fieldOfStudy = binding.etFieldOfStudy.getText()
                .toString()
                .trim();


        // =========================
        // VALIDATION
        // =========================

        if (fullName.isEmpty()) {
            binding.etfullName.setError("Enter your full name");
            binding.etfullName.requestFocus();
            return;
        }

        if (email.isEmpty() ||
                !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

            binding.etEmail.setError("Enter a valid email address");
            binding.etEmail.requestFocus();
            return;
        }

        if (password.length() < 8) {

            binding.etPassword.setError(
                    "Password must be at least 8 characters"
            );

            binding.etPassword.requestFocus();
            return;
        }

        if (fieldOfStudy.isEmpty()) {

            binding.etFieldOfStudy.setError(
                    "Enter your field of study"
            );

            binding.etFieldOfStudy.requestFocus();
            return;
        }


        // =========================
        // CREATE JSON REQUEST
        // =========================

        try {

            JSONObject body = new JSONObject();

            body.put("email", email);
            body.put("password", password);


            // Store additional registration information
            // as Supabase user metadata.

            JSONObject metadata = new JSONObject();

            metadata.put("full_name", fullName);
            metadata.put("field_of_study", fieldOfStudy);

            body.put("data", metadata);


            // =========================
            // SUPABASE SIGNUP URL
            // =========================

            // redirect_to makes the "Confirm email address" link open the app
            // (AuthCallbackActivity handles /signup) instead of a web page.
            String url = SupabaseConfig.PROJECT_URL +
                    "/auth/v1/signup?redirect_to=" +
                    java.net.URLEncoder.encode(
                            AccountRepository.REDIRECT_BASE + "/signup",
                            "UTF-8"
                    );


            RequestBody requestBody =
                    RequestBody.create(
                            body.toString(),
                            JSON
                    );


            Request request =
                    new Request.Builder()
                            .url(url)
                            .addHeader(
                                    "apikey",
                                    SupabaseConfig.ANON_KEY
                            )
                            .addHeader(
                                    "Content-Type",
                                    "application/json"
                            )
                            .post(requestBody)
                            .build();


            // Prevent multiple clicks
            binding.btnRegister.setEnabled(false);


            // =========================
            // SEND REQUEST
            // =========================

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(
                        Call call,
                        IOException e
                ) {

                    runOnUiThread(() -> {

                        binding.btnRegister.setEnabled(true);

                        Toast.makeText(
                                RegisterActivity.this,
                                "Connection failed. Please check your internet connection.",
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }


                @Override
                public void onResponse(
                        Call call,
                        Response response
                ) throws IOException {

                    try (Response res = response) {

                        String responseBody =
                                res.body() != null
                                        ? res.body().string()
                                        : "";


                        runOnUiThread(() -> {

                            binding.btnRegister.setEnabled(true);


                            // =========================
                            // SUCCESS
                            // =========================

                            if (res.isSuccessful()) {

                                showCheckEmailDialog(email);

                            }


                            // =========================
                            // ERROR
                            // =========================

                            else {

                                String message =
                                        "Registration failed.";


                                try {

                                    JSONObject error =
                                            new JSONObject(responseBody);


                                    if (error.has("msg")) {

                                        message =
                                                error.getString("msg");

                                    } else if (error.has("message")) {

                                        message =
                                                error.getString("message");

                                    } else if (
                                            error.has("error_description")
                                    ) {

                                        message =
                                                error.getString(
                                                        "error_description"
                                                );
                                    }

                                } catch (JSONException ignored) {

                                }


                                Toast.makeText(
                                        RegisterActivity.this,
                                        message,
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        });
                    }
                }
            });

        } catch (JSONException | java.io.UnsupportedEncodingException e) {

            binding.btnRegister.setEnabled(true);

            Toast.makeText(
                    this,
                    "Unable to process registration.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    /**
     * Tells the person to confirm their email before they can log in.
     * Pressing OK takes them to the Log In screen.
     */
    private void showCheckEmailDialog(String email) {

        if (isFinishing() || isDestroyed()) return;

        new AlertDialog.Builder(this)
                .setTitle("Check your email \u2709\uFE0F")
                .setMessage(
                        "We sent a confirmation link to:\n\n"
                                + email
                                + "\n\nOpen that email and tap \"Confirm email address\" "
                                + "before you log in. You won't be able to use Amplify "
                                + "until your email is confirmed.\n\n"
                                + "Can't see it? Check your spam or junk folder."
                )
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {

                    Intent intent = new Intent(this, LoginActivity.class);
                    startActivity(intent);
                    finish();

                })
                .show();
    }
}