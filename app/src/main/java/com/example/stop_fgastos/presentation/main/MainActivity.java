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
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Set;

public final class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigation;
    private View bottomNavigationCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigationCard = findViewById(R.id.bottom_navigation_card);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host == null) throw new IllegalStateException("NavHost não encontrado.");

        NavController navController = host.getNavController();
        NavigationUI.setupWithNavController(bottomNavigation, navController);

        Set<Integer> primary = Set.of(
                R.id.dashboardFragment,
                R.id.transactionsFragment,
                R.id.planningFragment,
                R.id.walletFragment,
                R.id.moreFragment
        );

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean showBottom = primary.contains(destination.getId());
            if (showBottom) {
                if (bottomNavigationCard.getVisibility() != View.VISIBLE) {
                    bottomNavigationCard.setVisibility(View.VISIBLE);
                    UiMotion.enter(bottomNavigationCard);
                }
            } else {
                bottomNavigationCard.setVisibility(View.GONE);
            }
            View hostView = host.getView();
            if (hostView != null) UiMotion.enter(hostView);
        });
    }
}
