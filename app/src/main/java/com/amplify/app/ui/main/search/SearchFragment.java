package com.amplify.app.ui.main.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.SearchAdapter;
import com.amplify.app.data.SessionManager;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.data.SupabaseConfig;
import com.amplify.app.databinding.FragmentSearchBinding;
import com.amplify.app.models.PersonResult;
import com.amplify.app.ui.content.ProfileOtherActivity;
import com.amplify.app.ui.content.ViewPostActivity;
import com.amplify.app.ui.main.artificial_intelligence.Artificial_Intelligence;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Search tab.
 *
 *  - People: found by name or field of study.
 *  - Posts: found by title or description (only published posts).
 *  - The results update shortly after the person stops typing.
 *  - Tapping Search (or the search key on the keyboard) also asks the AI for a short summary
 *    of the topic. That summary is NOT saved anywhere, so it never shows up in the AI history.
 *
 * Tapping a person opens their profile (ProfileOtherActivity, extra "user_id").
 * Tapping a post opens it (ViewPostActivity, extra "post_id").
 */
public class SearchFragment extends Fragment {

    private static final String TAG = "AmplifySearch";

    private static final int MIN_LETTERS = 2;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final long DEBOUNCE_MS = 400;

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final ExecutorService BACKGROUND = Executors.newCachedThreadPool();

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build();

    private FragmentSearchBinding binding;
    private SearchAdapter adapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pendingTerm = "";
    private final Runnable searchRunnable = () -> runSearch(pendingTerm);

    // Every new search or summary gets a number, so an answer that arrives late is ignored.
    private int searchCounter = 0;
    private int summaryCounter = 0;
    private String summaryQuery = "";

    /** A failed request, carrying the HTTP code. */
    private static class HttpFailure extends IOException {
        final int code;

        HttpFailure(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new SearchAdapter(
                person -> {
                    Intent intent = new Intent(requireContext(), ProfileOtherActivity.class);
                    intent.putExtra("user_id", person.getId());
                    intent.putExtra("user_name", person.getFullName());
                    startActivity(intent);
                },
                post -> {
                    Intent intent = new Intent(requireContext(), ViewPostActivity.class);
                    intent.putExtra("post_id", post.getId());
                    startActivity(intent);
                });

        binding.rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvResults.setAdapter(adapter);

        showStatus("Search for people, posts or topics.");

        binding.btnSearch.setOnClickListener(v -> submitSearch());

        binding.etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch();
                return true;
            }
            return false;
        });

        binding.etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onQueryTyped(s.toString().trim());
            }
        });

        binding.btnAiFloating.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), Artificial_Intelligence.class)));
    }

    // ------------------------------------------------------------------
    // Typing and tapping Search
    // ------------------------------------------------------------------

    /** Results follow what is typed, after a short pause. */
    private void onQueryTyped(String term) {
        if (binding == null) return;
        handler.removeCallbacks(searchRunnable);

        // The summary belongs to the words it was made for. Hide it once they change.
        if (!term.equalsIgnoreCase(summaryQuery)) {
            summaryCounter++;
            binding.cardAiSummary.setVisibility(View.GONE);
        }

        if (sanitize(term).length() < MIN_LETTERS) {
            searchCounter++;   // anything still on its way back is now out of date
            adapter.clear();
            showStatus("Search for people, posts or topics.");
            return;
        }

        pendingTerm = term;
        handler.postDelayed(searchRunnable, DEBOUNCE_MS);
    }

    /** The Search button or the search key: search right away and ask for the AI summary. */
    private void submitSearch() {
        if (binding == null) return;
        String term = binding.etSearchQuery.getText().toString().trim();

        if (sanitize(term).length() < MIN_LETTERS) {
            Toast.makeText(requireContext(), "Type at least 2 letters to search.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        handler.removeCallbacks(searchRunnable);
        hideKeyboard();
        runSearch(term);
        loadSummary(term);
    }

    // ------------------------------------------------------------------
    // People and posts
    // ------------------------------------------------------------------

    private void runSearch(String term) {
        if (binding == null || !isAdded()) return;

        final int mine = ++searchCounter;
        final String clean = sanitize(term);

        if (clean.length() < MIN_LETTERS) {
            adapter.clear();
            showStatus("Search for people, posts or topics.");
            return;
        }

        if (adapter.isEmpty()) showStatus("Searching\u2026");

        final Context app = requireContext().getApplicationContext();

        BACKGROUND.execute(() -> {
            final String token;
            try {
                token = new SupabaseApi(app).getFreshAccessToken();
            } catch (SupabaseApi.ApiException e) {
                postSearchError(mine, e.getMessage());
                return;
            }

            try {
                String myId = SessionManager.getUserId(app);
                List<PersonResult> people = searchPeople(token, clean, myId);
                List<SearchAdapter.PostHit> posts = searchPosts(token, clean);
                postResults(mine, term, people, posts);
            } catch (HttpFailure e) {
                Log.w(TAG, "Search failed: " + e);
                postSearchError(mine, e.code == 401
                        ? "Your login has expired. Please log out and log in again."
                        : "Couldn't search right now (" + e.getMessage() + "). Try again.");
            } catch (IOException e) {
                Log.w(TAG, "Search failed: " + e);
                postSearchError(mine, "No connection. Check your internet and try again.");
            } catch (Exception e) {
                Log.w(TAG, "Search failed: " + e);
                postSearchError(mine, "Couldn't search right now. Please try again.");
            }
        });
    }

    /** Runs on a background thread. */
    private List<PersonResult> searchPeople(String token, String term, String myId)
            throws Exception {
        HttpUrl url = HttpUrl.parse(SupabaseConfig.PROJECT_URL + "/rest/v1/profiles")
                .newBuilder()
                .addQueryParameter("select", "id,full_name,field_of_study,avatar_url")
                .addQueryParameter("or",
                        "(full_name.ilike.*" + term + "*,field_of_study.ilike.*" + term + "*)")
                .addQueryParameter("order", "full_name.asc")
                .addQueryParameter("limit", "15")
                .build();

        JSONArray array = new JSONArray(get(url, token));
        List<PersonResult> people = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            String id = o.optString("id", "");
            String name = o.optString("full_name", "").trim();

            if (id.isEmpty() || name.isEmpty()) continue;
            if (id.equals(myId)) continue;   // no need to find yourself

            String avatarUrl = o.isNull("avatar_url") ? "" : o.optString("avatar_url", "");
            people.add(new PersonResult(id, name, o.optString("field_of_study", ""), avatarUrl));
        }
        return people;
    }

    /** Runs on a background thread. */
    private List<SearchAdapter.PostHit> searchPosts(String token, String term) throws Exception {
        HttpUrl url = HttpUrl.parse(SupabaseConfig.PROJECT_URL + "/rest/v1/posts")
                .newBuilder()
                .addQueryParameter("select", "id,user_id,title,caption,media_type")
                .addQueryParameter("status", "eq.published")
                .addQueryParameter("or",
                        "(title.ilike.*" + term + "*,caption.ilike.*" + term + "*)")
                .addQueryParameter("order", "created_at.desc")
                .addQueryParameter("limit", "30")
                .build();

        JSONArray array = new JSONArray(get(url, token));
        if (array.length() == 0) return new ArrayList<>();

        // Look up who made each post, so the result can say "by <name>".
        Set<String> authorIds = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            String userId = array.getJSONObject(i).optString("user_id", "");
            if (!userId.isEmpty()) authorIds.add(userId);
        }

        Map<String, String> names = new HashMap<>();
        if (!authorIds.isEmpty()) {
            StringBuilder filter = new StringBuilder("in.(");
            int count = 0;
            for (String id : authorIds) {
                if (count++ > 0) filter.append(",");
                filter.append(id);
            }
            filter.append(")");

            HttpUrl profilesUrl = HttpUrl.parse(SupabaseConfig.PROJECT_URL + "/rest/v1/profiles")
                    .newBuilder()
                    .addQueryParameter("select", "id,full_name")
                    .addQueryParameter("id", filter.toString())
                    .build();

            JSONArray profiles = new JSONArray(get(profilesUrl, token));
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject p = profiles.getJSONObject(i);
                names.put(p.optString("id"), p.optString("full_name", ""));
            }
        }

        List<SearchAdapter.PostHit> posts = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);

            String author = names.get(o.optString("user_id", ""));
            String source = (author == null || author.trim().isEmpty())
                    ? "by Amplify creator" : "by " + author.trim();

            String caption = o.optString("caption", "").trim().replace("\n", " ");
            if (!caption.isEmpty()) {
                if (caption.length() > 70) caption = caption.substring(0, 70) + "\u2026";
                source = source + "  \u2022  " + caption;
            }

            String title = o.optString("title", "").trim();
            if (title.isEmpty()) title = "Untitled post";

            String format = o.optString("media_type", "").trim();
            if (format.isEmpty()) format = "post";

            posts.add(new SearchAdapter.PostHit(o.optString("id", ""), title, source, format));
        }
        return posts;
    }

    /** One GET request. Runs on a background thread. */
    private String get(HttpUrl url, String token) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                Log.w(TAG, "HTTP " + response.code() + ": " + body);
                throw new HttpFailure(response.code());
            }
            return body;
        }
    }

    private void postResults(int mine, String term, List<PersonResult> people,
                             List<SearchAdapter.PostHit> posts) {
        Activity activity = getActivity();
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            if (binding == null || !isAdded() || mine != searchCounter) return;

            adapter.setResults(people, posts);

            if (adapter.isEmpty()) {
                showStatus("No results for \u201C" + term + "\u201D.");
            } else {
                binding.tvSearchStatus.setVisibility(View.GONE);
            }
        });
    }

    private void postSearchError(int mine, String message) {
        Activity activity = getActivity();
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            if (binding == null || !isAdded() || mine != searchCounter) return;

            if (adapter.isEmpty()) {
                showStatus(message);
            } else {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // The AI summary (not saved to the AI chat history)
    // ------------------------------------------------------------------

    private void loadSummary(String term) {
        if (binding == null || !isAdded()) return;

        final String query = term.length() > MAX_QUERY_LENGTH
                ? term.substring(0, MAX_QUERY_LENGTH) : term;
        final int mine = ++summaryCounter;
        summaryQuery = term;

        binding.cardAiSummary.setVisibility(View.VISIBLE);
        binding.tvAiSummary.setText("Summarising\u2026");

        final Context app = requireContext().getApplicationContext();

        BACKGROUND.execute(() -> {
            try {
                String token = new SupabaseApi(app).getFreshAccessToken();

                String json = new JSONObject().put("query", query).toString();
                Request request = new Request.Builder()
                        .url(SupabaseConfig.PROJECT_URL + "/functions/v1/ai-summary")
                        .header("apikey", SupabaseConfig.ANON_KEY)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(json, JSON_TYPE))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    JSONObject o = new JSONObject(body.isEmpty() ? "{}" : body);

                    if (!response.isSuccessful()) {
                        Log.w(TAG, "Summary HTTP " + response.code() + ": " + body);
                        postSummary(mine, null,
                                o.optString("error", "The summary isn't available right now."));
                        return;
                    }

                    // "summary": null means there is nothing worth showing (for example,
                    // the search was a person's name), so the box is simply hidden.
                    String summary = o.isNull("summary") ? "" : o.optString("summary", "").trim();
                    postSummary(mine, summary, null);
                }
            } catch (SupabaseApi.ApiException e) {
                postSummary(mine, null, e.getMessage());
            } catch (Exception e) {
                Log.w(TAG, "Summary failed: " + e);
                postSummary(mine, null, "The summary isn't available right now.");
            }
        });
    }

    private void postSummary(int mine, String summary, String error) {
        Activity activity = getActivity();
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            if (binding == null || !isAdded() || mine != summaryCounter) return;

            if (error != null) {
                binding.cardAiSummary.setVisibility(View.VISIBLE);
                binding.tvAiSummary.setText(error);
            } else if (summary == null || summary.isEmpty()) {
                binding.cardAiSummary.setVisibility(View.GONE);
            } else {
                binding.cardAiSummary.setVisibility(View.VISIBLE);
                binding.tvAiSummary.setText(summary);
            }
        });
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    /**
     * Removes characters that have a special meaning in a Supabase filter
     * (wildcards, commas, brackets, quotes), so what is typed is always searched as plain text.
     */
    private static String sanitize(String term) {
        if (term == null) return "";
        return term.replaceAll("[%_*,()\"\\\\]", " ").replaceAll("\\s+", " ").trim();
    }

    private void showStatus(String text) {
        if (binding == null) return;
        binding.tvSearchStatus.setText(text);
        binding.tvSearchStatus.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        if (binding == null || getContext() == null) return;
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.etSearchQuery.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        searchCounter++;
        summaryCounter++;
        binding = null;
        adapter = null;
    }
}
