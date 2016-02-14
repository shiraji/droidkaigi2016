package io.github.droidkaigi.confsched.activity;

import android.app.Activity;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import javax.inject.Inject;

import io.github.droidkaigi.confsched.MainApplication;
import io.github.droidkaigi.confsched.R;
import io.github.droidkaigi.confsched.databinding.ActivityMainBinding;
import io.github.droidkaigi.confsched.fragment.SessionsFragment;
import io.github.droidkaigi.confsched.model.MainContentStateBrokerProvider;
import io.github.droidkaigi.confsched.model.Page;
import io.github.droidkaigi.confsched.util.AnalyticsTracker;
import io.github.droidkaigi.confsched.util.AppUtil;
import rx.subscriptions.CompositeSubscription;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String EXTRA_SHOULD_REFRESH = "should_refresh";

    @Inject
    AnalyticsTracker analyticsTracker;

    @Inject
    MainContentStateBrokerProvider brokerProvider;

    @Inject
    CompositeSubscription subscription;

    private ActivityMainBinding binding;

    static void start(@NonNull Activity activity, boolean shouldRefresh) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra(EXTRA_SHOULD_REFRESH, shouldRefresh);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.activity_fade_enter, R.anim.activity_fade_exit);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUtil.initLocale(this);

        boolean shouldRefresh = getIntent().getBooleanExtra(EXTRA_SHOULD_REFRESH, false);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        DataBindingUtil.bind(binding.navView.getHeaderView(0));

        MainApplication.getComponent(this).inject(this);

        subscription.add(brokerProvider.get().observe().subscribe(page -> {
            toggleToolbarElevation(page.shouldToggleToolbar());
            changePage(page.getTitleResId(), page.createFragment());
            binding.navView.setCheckedItem(page.getMenuId());
        }));
        initView();
        AppUtil.setTaskDescription(this, getString(R.string.all_sessions), AppUtil.getThemeColorPrimary(this));

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.content_view);
            if (fragment == null) {
                return;
            }
            Page page = Page.forFragmentName(fragment.getClass().getSimpleName());
            binding.toolbar.setTitle(page.getTitleResId());
            AppUtil.setTaskDescription(this, getString(page.getTitleResId()), AppUtil.getThemeColorPrimary(this));
        });

        if (savedInstanceState == null) {
            replaceFragment(SessionsFragment.newInstance(shouldRefresh));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subscription.unsubscribe();
    }

    private void initView() {
        setSupportActionBar(binding.toolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this,
                binding.drawer, binding.toolbar, R.string.open, R.string.close);
        binding.drawer.setDrawerListener(toggle);
        toggle.syncState();
        binding.navView.setNavigationItemSelectedListener(this);
        binding.navView.setCheckedItem(R.id.nav_all_sessions);
    }

    private void replaceFragment(Fragment fragment) {
        final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(R.anim.fragment_fade_enter, R.anim.fragment_fade_exit);
        ft.addToBackStack(null);
        ft.replace(R.id.content_view, fragment, fragment.getClass().getSimpleName());
        ft.commit();
    }

    @Override
    protected void onStart() {
        super.onStart();
        analyticsTracker.sendScreenView("main");
    }

    @Override
    public void onBackPressed() {
        if (binding.drawer.isDrawerOpen(GravityCompat.START)) {
            binding.drawer.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        binding.drawer.closeDrawer(GravityCompat.START);

        Page page = Page.forMenuId(item);
        toggleToolbarElevation(page.shouldToggleToolbar());
        changePage(page.getTitleResId(), page.createFragment());

        return true;
    }

    private void toggleToolbarElevation(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            float elevation = enable ? getResources().getDimension(R.dimen.elevation) : 0;
            binding.toolbar.setElevation(elevation);
        }
    }

    private void changePage(@StringRes int titleRes, @NonNull Fragment fragment) {
        new Handler().postDelayed(() -> {
            binding.toolbar.setTitle(titleRes);
            AppUtil.setTaskDescription(this, getString(titleRes), AppUtil.getThemeColorPrimary(this));
            replaceFragment(fragment);
        }, 300);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.activity_fade_enter, R.anim.activity_fade_exit);
    }

}
