package com.amplify.app.ui.main;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.amplify.app.R;
import com.amplify.app.databinding.ActivityMainBinding;
import com.amplify.app.ui.main.home.HomeFragment;
import com.amplify.app.ui.main.messages.MessagesFragment;
import com.amplify.app.ui.main.post.PostFragment;
import com.amplify.app.ui.main.profile.ProfileFragment;
import com.amplify.app.ui.main.search.SearchFragment;

/**
 * Hosts the five core screens (Home, Search, Post, Messages, Profile) as Fragments
 * behind one shared BottomNavigationView, so the nav bar itself never rebuilds -
 * only the content above it swaps out. Each fragment still lives in its own file
 * with its own XML layout, exactly like every other screen in this project.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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

    private void openFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
