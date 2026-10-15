package com.amplify.app.ui.main;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.amplify.app.R;
import com.amplify.app.data.MessagingRealtime;
import com.amplify.app.data.MessagingRepository;
import com.amplify.app.data.SupabaseApi;
import com.amplify.app.databinding.ActivityMainBinding;
import com.amplify.app.ui.main.home.HomeFragment;
import com.amplify.app.ui.main.messages.MessagesFragment;
import com.amplify.app.ui.main.post.PostFragment;
import com.amplify.app.ui.main.profile.ProfileFragment;
import com.amplify.app.ui.main.search.SearchFragment;
import com.google.android.material.badge.BadgeDrawable;

import org.json.JSONObject;

/**
 * Hosts the five core screens (Home, Search, Post, Messages, Profile) as Fragments
 * behind one shared BottomNavigationView, so the nav bar itself never rebuilds -
 * only the content above it swaps out. Each fragment still lives in its own file
 * with its own XML layout, exactly like every other screen in this project.
 *
 * It also keeps the live message connection open while the app is on screen and shows
 * the unread-message count on the Messages tab.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MessagingRepository messagingRepository;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshBadgeRunnable = this::refreshUnreadBadge;

    private final MessagingRealtime.Listener realtimeListener = new MessagingRealtime.Listener() {
        @Override
        public void onChange(String table, String type, JSONObject record, JSONObject oldRecord) {
            handler.removeCallbacks(refreshBadgeRunnable);
            handler.postDelayed(refreshBadgeRunnable, 300);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        messagingRepository = new MessagingRepository(this);

        // Start on Home
        openFragment(new HomeFragment());
        binding.bottomNav.setSelectedItemId(R.id.nav_home);

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                openFragment(new HomeFragment());
                return true;
            } else if (id == R.id.nav_search) {
                openFragment(new SearchFragment());
                return true;
            } else if (id == R.id.nav_post) {
                openFragment(new PostFragment());
                return true;
            } else if (id == R.id.nav_messages) {
                openFragment(new MessagesFragment());
                return true;
            } else if (id == R.id.nav_profile) {
                openFragment(new ProfileFragment());
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        MessagingRealtime realtime = MessagingRealtime.get(this);
        realtime.acquire();
        realtime.addListener(realtimeListener);
        refreshUnreadBadge();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MessagingRealtime realtime = MessagingRealtime.get(this);
        realtime.removeListener(realtimeListener);
        realtime.release();
        handler.removeCallbacks(refreshBadgeRunnable);
    }

    private void refreshUnreadBadge() {
        messagingRepository.loadUnreadTotal(new SupabaseApi.Callback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                if (isFinishing() || isDestroyed()) return;
                showUnreadBadge(count);
            }

            @Override
            public void onError(String message) {
                // Not worth interrupting anyone for. The count tries again on the next change.
            }
        });
    }

    private void showUnreadBadge(int count) {
        if (count > 0) {
            BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.nav_messages);
            badge.setVisible(true);
            badge.setNumber(count);
        } else {
            binding.bottomNav.removeBadge(R.id.nav_messages);
        }
    }

    private void openFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
