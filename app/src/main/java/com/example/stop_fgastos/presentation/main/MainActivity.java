package com.example.stop_fgastos.presentation.main;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.widget.ImageViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.StopGastosApplication;
import com.example.stop_fgastos.domain.model.FinanceState;
import com.example.stop_fgastos.presentation.common.RecordDialogs;
import com.example.stop_fgastos.presentation.common.UiMotion;
import com.example.stop_fgastos.presentation.common.UiPrivacy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Set;

public final class MainActivity extends AppCompatActivity {
    private NavController navController;
    private MainViewModel viewModel;
    private FinanceState latestState = new FinanceState();

    private View topBar;
    private ImageButton topMenuButton;
    private ImageButton topNotificationsButton;
    private TextView topTitle;
    private View bottomNavigation;
    private ImageButton addButton;

    private View navDashboard;
    private View navTransactions;
    private View navFamily;
    private View navShopping;

    private ImageView navDashboardIcon;
    private ImageView navTransactionsIcon;
    private ImageView navFamilyIcon;
    private ImageView navShoppingIcon;

    private TextView navDashboardText;
    private TextView navTransactionsText;
    private TextView navFamilyText;
    private TextView navShoppingText;

    private final Set<Integer> primaryDestinations = Set.of(
            R.id.dashboardFragment,
            R.id.transactionsFragment,
            R.id.familyFragment,
            R.id.shoppingFragment
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        configureViewModel();
        configureNavigation();
    }

    private void bindViews() {
        topBar = findViewById(R.id.top_bar);
        topMenuButton = findViewById(R.id.top_menu_button);
        topNotificationsButton = findViewById(R.id.top_notifications_button);
        topTitle = findViewById(R.id.top_title);
        bottomNavigation = findViewById(R.id.bottom_navigation_container);
        addButton = findViewById(R.id.nav_add);

        navDashboard = findViewById(R.id.nav_dashboard);
        navTransactions = findViewById(R.id.nav_transactions);
        navFamily = findViewById(R.id.nav_family);
        navShopping = findViewById(R.id.nav_shopping);

        navDashboardIcon = findViewById(R.id.nav_dashboard_icon);
        navTransactionsIcon = findViewById(R.id.nav_transactions_icon);
        navFamilyIcon = findViewById(R.id.nav_family_icon);
        navShoppingIcon = findViewById(R.id.nav_shopping_icon);

        navDashboardText = findViewById(R.id.nav_dashboard_text);
        navTransactionsText = findViewById(R.id.nav_transactions_text);
        navFamilyText = findViewById(R.id.nav_family_text);
        navShoppingText = findViewById(R.id.nav_shopping_text);
    }

    private void configureViewModel() {
        StopGastosApplication app = (StopGastosApplication) getApplication();
        viewModel = new ViewModelProvider(
                this,
                new MainViewModel.Factory(app.container())
        ).get(MainViewModel.class);

        viewModel.finance().observe(this, state ->
                latestState = state == null ? new FinanceState() : state);
    }

    private void configureNavigation() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host == null) throw new IllegalStateException("NavHost não encontrado.");

        navController = host.getNavController();

        navDashboard.setOnClickListener(v -> navigatePrimary(R.id.dashboardFragment));
        navTransactions.setOnClickListener(v -> navigatePrimary(R.id.transactionsFragment));
        navFamily.setOnClickListener(v -> navigatePrimary(R.id.familyFragment));
        navShopping.setOnClickListener(v -> navigatePrimary(R.id.shoppingFragment));

        addButton.setOnClickListener(v -> {
            UiMotion.pop(v);
            RecordDialogs.transaction(
                    this,
                    latestState,
                    null,
                    input -> viewModel.saveTransaction(null, input)
            );
        });

        topNotificationsButton.setOnClickListener(v -> navigatePrimary(R.id.familyFragment));

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            boolean login = id == R.id.loginFragment;
            boolean primary = primaryDestinations.contains(id);

            topBar.setVisibility(login ? View.GONE : View.VISIBLE);
            bottomNavigation.setVisibility(primary ? View.VISIBLE : View.GONE);

            if (!login) {
                topTitle.setText(titleFor(id, destination.getLabel()));
                if (primary) {
                    topMenuButton.setImageResource(R.drawable.ic_menu);
                    topMenuButton.setContentDescription("Abrir menu");
                    topMenuButton.setOnClickListener(v -> showAppMenu());
                    topNotificationsButton.setVisibility(View.VISIBLE);
                } else {
                    topMenuButton.setImageResource(R.drawable.ic_back);
                    topMenuButton.setContentDescription("Voltar");
                    topMenuButton.setOnClickListener(v -> navController.navigateUp());
                    topNotificationsButton.setVisibility(View.GONE);
                }
            }

            updateBottomNavigation(id);

            View hostView = host.getView();
            if (hostView != null && !login) UiMotion.enter(hostView);
        });
    }

    private void navigatePrimary(int destination) {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == destination) {
            return;
        }
        navController.navigate(destination);
    }

    private void showAppMenu() {
        String privacy = UiPrivacy.enabled(this)
                ? "Mostrar valores do painel"
                : "Ocultar valores do painel";

        String[] items = {
                "Dashboard",
                "Lançamentos",
                "Planejamento",
                "Contas e cartões",
                "Família",
                "Compras",
                "Calendário",
                "Relatórios",
                "Configurações",
                privacy
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Stop Gastos")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: navigatePrimary(R.id.dashboardFragment); break;
                        case 1: navigatePrimary(R.id.transactionsFragment); break;
                        case 2: navController.navigate(R.id.planningFragment); break;
                        case 3: navController.navigate(R.id.walletFragment); break;
                        case 4: navigatePrimary(R.id.familyFragment); break;
                        case 5: navigatePrimary(R.id.shoppingFragment); break;
                        case 6: navController.navigate(R.id.calendarFragment); break;
                        case 7: navController.navigate(R.id.reportsFragment); break;
                        case 8: navController.navigate(R.id.settingsFragment); break;
                        case 9:
                            UiPrivacy.setEnabled(this, !UiPrivacy.enabled(this));
                            recreateCurrentDestination();
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    private void recreateCurrentDestination() {
        if (navController.getCurrentDestination() == null) return;
        int id = navController.getCurrentDestination().getId();
        if (id == R.id.dashboardFragment) {
            navController.navigate(R.id.dashboardFragment);
        }
    }

    private void updateBottomNavigation(int destination) {
        int activeColor = ContextCompat.getColor(this, R.color.hero_label);
        int inactiveColor = ContextCompat.getColor(this, R.color.text_muted);

        setNavState(
                navDashboardIcon,
                navDashboardText,
                destination == R.id.dashboardFragment,
                activeColor,
                inactiveColor
        );
        setNavState(
                navTransactionsIcon,
                navTransactionsText,
                destination == R.id.transactionsFragment,
                activeColor,
                inactiveColor
        );
        setNavState(
                navFamilyIcon,
                navFamilyText,
                destination == R.id.familyFragment,
                activeColor,
                inactiveColor
        );
        setNavState(
                navShoppingIcon,
                navShoppingText,
                destination == R.id.shoppingFragment,
                activeColor,
                inactiveColor
        );
    }

    private void setNavState(
            ImageView icon,
            TextView text,
            boolean active,
            int activeColor,
            int inactiveColor
    ) {
        int color = active ? activeColor : inactiveColor;
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));
        text.setTextColor(color);
        text.setTypeface(null, active
                ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
    }

    private String titleFor(int id, CharSequence fallback) {
        if (id == R.id.dashboardFragment) return "Dashboard";
        if (id == R.id.transactionsFragment) return "Lançamentos";
        if (id == R.id.familyFragment) return "Família";
        if (id == R.id.shoppingFragment) return "Compras";
        if (id == R.id.planningFragment) return "Planejamento";
        if (id == R.id.walletFragment) return "Contas e cartões";
        if (id == R.id.calendarFragment) return "Calendário";
        if (id == R.id.reportsFragment) return "Relatórios";
        if (id == R.id.settingsFragment) return "Configurações";
        return fallback == null ? "Stop Gastos" : fallback.toString();
    }
}
