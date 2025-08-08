package com.cinecraze.free.stream;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.viewpager2.widget.ViewPager2;
import androidx.appcompat.app.AlertDialog;

import com.cinecraze.free.stream.ui.BaseFragment;
import com.cinecraze.free.stream.ui.MainPagerAdapter;
import com.cinecraze.free.stream.repository.DataRepository;
import com.cinecraze.free.stream.net.ApiService;
import com.cinecraze.free.stream.net.RetrofitClient;
import com.gauravk.bubblenavigation.BubbleNavigationConstraintView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * SWIPE-ENABLED FRAGMENT-BASED IMPLEMENTATION
 *
 * This activity uses ViewPager2 with fragments for swipe navigation:
 * - HomeFragment: Shows all content with carousel
 * - MovieFragment: Shows only movies
 * - SeriesFragment: Shows only TV series
 * - LiveTVFragment: Shows only live TV channels
 *
 * Features:
 * - Swipe between sections
 * - Bottom navigation clicks sync with swipe position
 * - Better memory management with FragmentStateAdapter
 * - Smooth transitions and animations
 */
public class FragmentMainActivity extends AppCompatActivity {

    private BubbleNavigationConstraintView bottomNavigationView;
    private ImageView closeSearchIcon;
    private LinearLayout searchLayout;
    private AutoCompleteTextView searchBar;
    private FloatingActionButton floatingSearchIcon;
    private ViewPager2 mainViewPager;
    private MainPagerAdapter pagerAdapter;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;

    private boolean isSearchVisible = false;
    private boolean isProgrammaticChange = false; // Flag to prevent infinite loops

    private DataRepository dataRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up status bar and navigation bar
        setupStatusBar();

        setContentView(R.layout.activity_main_fragment);

        dataRepository = new DataRepository(this);

        initializeViews();

        // Preflight: if cache is invalid, prompt before initial bulk download
        if (dataRepository.hasValidCache()) {
            startFragments();
        } else {
            preflightAndPrompt();
        }
    }

    private void startFragments() {
        setupViewPager();
        setupBottomNavigation();
        setupSearch();
    }

    private void preflightAndPrompt() {
        ApiService api = RetrofitClient.getClient().create(ApiService.class);
        api.headPlaylist().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                long contentLength = 0L;
                try {
                    String len = response.headers().get("Content-Length");
                    if (len != null) contentLength = Long.parseLong(len);
                } catch (Exception ignored) {}
                showDownloadPrompt(contentLength > 0 ? contentLength : -1L);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                showDownloadPrompt(-1L);
            }
        });
    }

    private void showDownloadPrompt(long contentLengthBytes) {
        String sizeText;
        if (contentLengthBytes > 0) {
            double mb = contentLengthBytes / (1024.0 * 1024.0);
            sizeText = String.format(Locale.getDefault(), "%.1f MB", mb);
        } else {
            sizeText = "unknown size";
        }

        new AlertDialog.Builder(this)
            .setTitle("Download required")
            .setMessage("Initial data needs to be downloaded (" + sizeText + "). Continue?")
            .setPositiveButton("Download", (dialog, which) -> startInitialDownload(contentLengthBytes))
            .setNegativeButton("Cancel", (dialog, which) -> {
                // User canceled; you can finish or keep minimal UI
                finish();
            })
            .setCancelable(false)
            .show();
    }

    private AlertDialog downloadingDialog;

    private void startInitialDownload(long estimatedBytes) {
        showDownloadingDialog(estimatedBytes);
        dataRepository.ensureDataAvailable(new DataRepository.DataCallback() {
            @Override
            public void onSuccess(java.util.List<com.cinecraze.free.stream.models.Entry> entries) {
                runOnUiThread(() -> {
                    if (downloadingDialog != null && downloadingDialog.isShowing()) {
                        downloadingDialog.dismiss();
                    }
                    startFragments();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (downloadingDialog != null && downloadingDialog.isShowing()) {
                        downloadingDialog.dismiss();
                    }
                    android.widget.Toast.makeText(FragmentMainActivity.this, "Failed to initialize: " + error, android.widget.Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void showDownloadingDialog(long estimatedBytes) {
        String sizeText;
        if (estimatedBytes > 0) {
            double mb = estimatedBytes / (1024.0 * 1024.0);
            sizeText = String.format(Locale.getDefault(), "%.1f MB", mb);
        } else {
            sizeText = "unknown size";
        }

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this);
        progressBar.setIndeterminate(true);
        android.widget.LinearLayout.LayoutParams pbParams = new android.widget.LinearLayout.LayoutParams(
            (int) (24 * getResources().getDisplayMetrics().density),
            (int) (24 * getResources().getDisplayMetrics().density)
        );
        pbParams.setMargins(0, 0, padding, 0);
        progressBar.setLayoutParams(pbParams);

        android.widget.TextView message = new android.widget.TextView(this);
        message.setText("Downloading data (" + sizeText + ")...\nPlease wait, this may take a moment.");
        message.setTextSize(14);

        container.addView(progressBar);
        container.addView(message);

        downloadingDialog = new AlertDialog.Builder(this)
            .setTitle("Downloading")
            .setView(container)
            .setCancelable(false)
            .create();
        downloadingDialog.setCanceledOnTouchOutside(false);
        downloadingDialog.show();
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(android.graphics.Color.BLACK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = window.getDecorView();
                decor.setSystemUiVisibility(0); // Clear light status bar flag
            }
        }
    }

    private void initializeViews() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        closeSearchIcon = findViewById(R.id.close_search_icon);
        searchLayout = findViewById(R.id.search_layout);
        searchBar = findViewById(R.id.search_bar);
        floatingSearchIcon = findViewById(R.id.floating_search_icon);
        mainViewPager = findViewById(R.id.main_viewpager);
    }

    private void setupViewPager() {
        pagerAdapter = new MainPagerAdapter(this);
        mainViewPager.setAdapter(pagerAdapter);

        // Enable smooth scrolling and configure ViewPager2
        mainViewPager.setOffscreenPageLimit(1); // Keep adjacent fragments in memory

        // Add page change callback to sync with bottom navigation
        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                // Only update bottom navigation if this is not a programmatic change
                if (!isProgrammaticChange) {
                    bottomNavigationView.setCurrentActiveItem(position);
                }

                // Update floating search icon visibility
                updateSearchIconVisibility(position);
            }
        };
        mainViewPager.registerOnPageChangeCallback(pageChangeCallback);
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setNavigationChangeListener((view, position) -> {
            // Set flag to prevent infinite loop
            isProgrammaticChange = true;

            // Smoothly scroll to the selected page
            mainViewPager.setCurrentItem(position, true);

            // Update floating search icon visibility
            updateSearchIconVisibility(position);

            // Reset flag after a short delay to allow ViewPager to settle
            mainViewPager.post(() -> isProgrammaticChange = false);
        });
    }

    private void updateSearchIconVisibility(int position) {
        // Show search icon only on Home tab (position 0)
        if (position == 0) {
            floatingSearchIcon.setVisibility(View.VISIBLE);
        } else {
            floatingSearchIcon.setVisibility(View.GONE);
        }
    }

    private void setupSearch() {
        floatingSearchIcon.setOnClickListener(v -> toggleSearch());

        closeSearchIcon.setOnClickListener(v -> hideSearch());

        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performSearch(searchBar.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void toggleSearch() {
        if (isSearchVisible) {
            hideSearch();
        } else {
            showSearch();
        }
    }

    private void showSearch() {
        searchLayout.setVisibility(View.VISIBLE);
        searchLayout.animate()
            .translationY(0)
            .alpha(1.0f)
            .setDuration(300)
            .start();

        searchBar.requestFocus();
        isSearchVisible = true;
    }

    private void hideSearch() {
        searchLayout.animate()
            .translationY(-searchLayout.getHeight())
            .alpha(0.0f)
            .setDuration(300)
            .withEndAction(() -> {
                searchLayout.setVisibility(View.GONE);
                searchBar.setText("");
            })
            .start();

        isSearchVisible = false;
    }

    private void performSearch(String query) {
        // Get the current fragment and perform search
        int currentItem = mainViewPager.getCurrentItem();
        BaseFragment currentFragment = pagerAdapter.getFragmentAt(currentItem);

        if (currentFragment != null) {
            currentFragment.performSearch(query);
        }
        hideSearch();
    }

    @Override
    public void onBackPressed() {
        if (isSearchVisible) {
            hideSearch();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister ViewPager callback to prevent memory leaks
        if (mainViewPager != null && pageChangeCallback != null) {
            mainViewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
    }

    public void hideBottomNavigation() {
        if (bottomNavigationView.getVisibility() == View.VISIBLE) {
            bottomNavigationView.animate()
                .translationY(bottomNavigationView.getHeight())
                .setDuration(300)
                .withEndAction(() -> bottomNavigationView.setVisibility(View.GONE))
                .start();
            setViewPagerMargin(0);
        }
    }

    public void showBottomNavigation() {
        if (bottomNavigationView.getVisibility() == View.GONE) {
            bottomNavigationView.setVisibility(View.VISIBLE);
            bottomNavigationView.animate()
                .translationY(0)
                .setDuration(300)
                .start();
            setViewPagerMargin(60);
        }
    }

    private void setViewPagerMargin(int bottomMarginInDp) {
        if (mainViewPager != null) {
            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) mainViewPager.getLayoutParams();
            float density = getResources().getDisplayMetrics().density;
            int bottomMarginInPixels = (int) (bottomMarginInDp * density);
            params.setMargins(0, 0, 0, bottomMarginInPixels);
            mainViewPager.setLayoutParams(params);
        }
    }
}