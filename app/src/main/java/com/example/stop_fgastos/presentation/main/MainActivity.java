package com.example.stop_fgastos.presentation.main;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.stop_fgastos.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Set;

public final class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigation = findViewById(R.id.bottom_navigation);
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
            bottomNavigation.setVisibility(
                    primary.contains(destination.getId()) ? View.VISIBLE : View.GONE
            );
        });
    }
}
