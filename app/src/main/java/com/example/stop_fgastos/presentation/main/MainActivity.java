package com.example.stop_fgastos.presentation.main;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.presentation.common.UiMotion;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Set;

public final class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigation;
    private View bottomNavigationCard;
    private View topNavigationCard;
    private MaterialToolbar topNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigationCard = findViewById(R.id.bottom_navigation_card);
        topNavigationCard = findViewById(R.id.top_navigation_card);
        topNavigation = findViewById(R.id.top_navigation);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host == null) throw new IllegalStateException("NavHost não encontrado.");

        NavController navController = host.getNavController();
        NavigationUI.setupWithNavController(bottomNavigation, navController);
        topNavigation.setNavigationOnClickListener(v -> navController.navigateUp());

        Set<Integer> primary = Set.of(
                R.id.dashboardFragment,
                R.id.transactionsFragment,
                R.id.planningFragment,
                R.id.walletFragment,
                R.id.moreFragment
        );

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean isLogin = destination.getId() == R.id.loginFragment;
            boolean isPrimary = primary.contains(destination.getId());

            bottomNavigationCard.setVisibility(isPrimary ? View.VISIBLE : View.GONE);

            if (!isPrimary && !isLogin) {
                topNavigation.setTitle(destination.getLabel());
                if (topNavigationCard.getVisibility() != View.VISIBLE) {
                    topNavigationCard.setVisibility(View.VISIBLE);
                    UiMotion.enter(topNavigationCard);
                }
            } else {
                topNavigationCard.setVisibility(View.GONE);
            }

            View hostView = host.getView();
            if (hostView != null) UiMotion.enter(hostView);
        });
    }
}
