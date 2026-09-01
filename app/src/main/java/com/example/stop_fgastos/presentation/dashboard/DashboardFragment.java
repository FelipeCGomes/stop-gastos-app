package com.example.stop_fgastos.presentation.dashboard;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.model.FinanceState;
import com.example.stop_fgastos.domain.model.MonthlySummary;
import com.example.stop_fgastos.presentation.common.DisplayRow;
import com.example.stop_fgastos.presentation.common.RecordAdapter;
import com.example.stop_fgastos.presentation.common.UiFormat;
import com.example.stop_fgastos.presentation.common.UiMotion;
import com.example.stop_fgastos.presentation.common.UiPrivacy;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DashboardFragment extends Fragment {
    private MainViewModel viewModel;
    private FinanceState latestState = new FinanceState();

    private TextView greeting;
    private TextView hint;
    private TextView alertIcon;
    private TextView alertText;
    private TextView income;
    private TextView incomeDelta;
    private TextView expense;
    private TextView expenseDelta;
    private TextView balance;
    private TextView savings;
    private TextView daily;
    private TextView projection;
    private TextView healthTitle;
    private TextView healthDetail;
    private TextView healthScore;
    private TextView empty;

    private LinearProgressIndicator healthProgress;
    private FinanceTrendChartView trendChart;
    private RecordAdapter adapter;

    private boolean privacyEnabled;
    private boolean showPositiveValues;

    public DashboardFragment() {
        super(R.layout.fragment_dashboard);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);

        greeting = view.findViewById(R.id.dashboard_greeting);
        hint = view.findViewById(R.id.dashboard_hint);
        alertIcon = view.findViewById(R.id.dashboard_alert_icon);
        alertText = view.findViewById(R.id.dashboard_alert_text);
        income = view.findViewById(R.id.dashboard_income);
        incomeDelta = view.findViewById(R.id.dashboard_income_delta);
        expense = view.findViewById(R.id.dashboard_expense);
        expenseDelta = view.findViewById(R.id.dashboard_expense_delta);
        balance = view.findViewById(R.id.dashboard_balance);
        savings = view.findViewById(R.id.dashboard_savings);
        daily = view.findViewById(R.id.dashboard_daily);
        projection = view.findViewById(R.id.dashboard_projection);
        healthTitle = view.findViewById(R.id.dashboard_health_title);
        healthDetail = view.findViewById(R.id.dashboard_health_detail);
        healthScore = view.findViewById(R.id.dashboard_health_score);
        healthProgress = view.findViewById(R.id.dashboard_health_progress);
        empty = view.findViewById(R.id.dashboard_empty);
        trendChart = view.findViewById(R.id.dashboard_trend_chart);

        view.findViewById(R.id.dashboard_add_recurring).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.planningFragment));
        view.findViewById(R.id.dashboard_open_reports).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.reportsFragment));

        RecyclerView recycler = view.findViewById(R.id.dashboard_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setNestedScrollingEnabled(false);
        adapter = new RecordAdapter(new RecordAdapter.Listener() {
            @Override public void onPrimary(DisplayRow row) {}
            @Override public void onDelete(DisplayRow row) {}
        });
        recycler.setAdapter(adapter);

        refreshPreferences();
        UiMotion.enter(view);

        YearMonth currentMonth = YearMonth.now();
        viewModel.ensureMonth(currentMonth);
        viewModel.finance().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean oldPrivacy = privacyEnabled;
        boolean oldPositive = showPositiveValues;
        refreshPreferences();
        if ((oldPrivacy != privacyEnabled || oldPositive != showPositiveValues)
                && latestState != null) {
            render(latestState);
        }
    }

    private void refreshPreferences() {
        if (!isAdded()) return;
        privacyEnabled = UiPrivacy.enabled(requireContext());
        showPositiveValues = UiPrivacy.showPositiveValues(requireContext());
    }

    private void render(FinanceState state) {
        latestState = state == null ? new FinanceState() : state;

        YearMonth currentMonth = YearMonth.now();
        MonthlySummary summary = viewModel.summary(currentMonth);
        MonthlySummary previous = viewModel.summary(currentMonth.minusMonths(1));

        String monthName = UiFormat.month(currentMonth);
        greeting.setText("Resumo de " + capitalize(monthName));

        int expenseCount = 0;
        for (FinanceRecord tx : latestState.records(FinanceSection.TRANSACTIONS)) {
            if ("expense".equals(tx.text("type"))
                    && tx.text("date").startsWith(currentMonth.toString())) {
                expenseCount++;
            }
        }

        if (expenseCount == 0) {
            hint.setText("Registre seus gastos e acompanhe o orçamento em tempo real.");
        } else if (privacyEnabled) {
            hint.setText("Você registrou " + expenseCount
                    + (expenseCount == 1 ? " despesa" : " despesas")
                    + " neste período.");
        } else {
            hint.setText("Você gastou " + UiFormat.money(summary.expense())
                    + " em " + expenseCount
                    + (expenseCount == 1 ? " despesa" : " despesas")
                    + " neste período.");
        }

        income.setText(showPositiveValues
                ? privateMoney(summary.income())
                : "Oculto");
        incomeDelta.setText(showPositiveValues
                ? deltaText(summary.income(), previous.income(), true)
                : "Oculto nas configurações");

        expense.setText(privateMoney(summary.expense()));
        expenseDelta.setText(deltaText(summary.expense(), previous.expense(), false));

        balance.setText(privateMoney(summary.balance()));

        double savingsRate = summary.savingsRate();
        if (privacyEnabled) {
            savings.setText("Valores ocultos");
        } else {
            savings.setText(String.format(
                    Locale.getDefault(),
                    "Taxa de economia %.1f%%",
                    savingsRate
            ));
        }

        int elapsedDays = Math.max(1, LocalDate.now().getDayOfMonth());
        double dailyAverage = summary.expense() / elapsedDays;
        double projected = dailyAverage * currentMonth.lengthOfMonth();
        daily.setText(privateMoney(dailyAverage));
        projection.setText(
                privacyEnabled
                        ? "Projeção: ••••"
                        : "Projeção: " + UiFormat.money(projected)
        );

        renderSmartAlert(summary);
        renderHealth(summary);
        renderTrend(currentMonth);
        renderRecent(currentMonth);
    }

    private void renderSmartAlert(MonthlySummary summary) {
        double rate = summary.savingsRate();

        if (summary.income() <= 0 && summary.expense() <= 0) {
            alertIcon.setText("↗");
            alertIcon.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));
            alertText.setText("Adicione seus primeiros lançamentos para gerar insights.");
            return;
        }

        if (rate >= 20) {
            alertIcon.setText("✓");
            alertIcon.setTextColor(ContextCompat.getColor(requireContext(), R.color.income));
            alertText.setText(String.format(
                    Locale.getDefault(),
                    "Boa taxa de economia: %.1f%%.",
                    rate
            ));
        } else if (rate >= 0) {
            alertIcon.setText("↗");
            alertIcon.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning));
            alertText.setText(String.format(
                    Locale.getDefault(),
                    "Seu mês está positivo. Economia atual: %.1f%%.",
                    rate
            ));
        } else {
            alertIcon.setText("!");
            alertIcon.setTextColor(ContextCompat.getColor(requireContext(), R.color.expense));
            alertText.setText("Atenção: as despesas estão acima das receitas neste mês.");
        }
    }

    private void renderTrend(YearMonth currentMonth) {
        List<String> labels = new ArrayList<>();
        List<Double> trendIncome = new ArrayList<>();
        List<Double> trendExpense = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                "MMM",
                new Locale("pt", "BR")
        );

        for (int i = 5; i >= 0; i--) {
            YearMonth target = currentMonth.minusMonths(i);
            MonthlySummary item = viewModel.summary(target);
            labels.add(target.format(formatter).replace(".", "").toLowerCase(Locale.getDefault()));
            trendIncome.add(item.income());
            trendExpense.add(item.expense());
        }

        trendChart.setPrivacyEnabled(privacyEnabled);
        trendChart.setShowPositiveValues(showPositiveValues);
        trendChart.setSeries(labels, trendIncome, trendExpense);
    }

    private void renderRecent(YearMonth currentMonth) {
        List<FinanceRecord> transactions = new ArrayList<>();
        for (FinanceRecord tx : latestState.records(FinanceSection.TRANSACTIONS)) {
            if (tx.text("date").startsWith(currentMonth.toString())) {
                transactions.add(tx);
            }
        }

        transactions.sort(
                Comparator.comparing((FinanceRecord record) -> record.text("date"))
                        .reversed()
                        .thenComparing(
                                record -> record.text("updatedAt"),
                                Comparator.reverseOrder()
                        )
        );

        List<DisplayRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(6, transactions.size()); i++) {
            FinanceRecord tx = transactions.get(i);
            boolean positive = !"expense".equals(tx.text("type"));

            String value;
            if (positive && !showPositiveValues) {
                value = "";
            } else if (privacyEnabled) {
                value = "••••";
            } else {
                value = (positive ? "+ " : "- ") + UiFormat.money(tx.number("amount"));
            }

            rows.add(new DisplayRow(
                    tx,
                    tx.text("description", "Lançamento"),
                    tx.text("date") + " · " + tx.text("payment"),
                    value,
                    "",
                    false
            ));
        }

        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.submit(rows);
    }

    private void renderHealth(MonthlySummary summary) {
        if (summary.income() == 0 && summary.expense() == 0) {
            healthScore.setText("0/100");
            healthTitle.setText("Começando");
            healthDetail.setText("Registre movimentações para calcular sua saúde financeira.");
            healthProgress.setProgressCompat(0, true);
            return;
        }

        int score;
        if (summary.income() <= 0 && summary.expense() > 0) {
            score = 10;
        } else {
            double rate = Math.max(-40, Math.min(60, summary.savingsRate()));
            score = (int) Math.round(40 + rate);
            score = Math.max(0, Math.min(100, score));
        }

        healthScore.setText(score + "/100");
        healthProgress.setProgressCompat(score, true);

        if (score >= 80) {
            healthTitle.setText("Excelente controle");
            healthDetail.setText("Seu saldo e sua taxa de economia estão muito saudáveis.");
        } else if (score >= 60) {
            healthTitle.setText("Mês saudável");
            healthDetail.setText("Você mantém uma boa relação entre entradas e saídas.");
        } else if (score >= 40) {
            healthTitle.setText("Atenção ao ritmo");
            healthDetail.setText("Os gastos estão consumindo uma parcela importante da renda.");
        } else {
            healthTitle.setText("Orçamento pressionado");
            healthDetail.setText("Revise despesas e contas previstas para o restante do mês.");
        }
    }

    private String deltaText(double current, double previous, boolean increaseIsGood) {
        if (privacyEnabled) return "Comparação oculta";
        if (previous <= 0.005) return "Sem comparação";

        double pct = ((current - previous) / previous) * 100.0;
        String arrow = pct > 0.05 ? "↑ " : pct < -0.05 ? "↓ " : "↔ ";
        return arrow
                + String.format(Locale.getDefault(), "%.1f%%", Math.abs(pct))
                + " vs. mês anterior";
    }

    private String privateMoney(double value) {
        return privacyEnabled ? "••••" : UiFormat.money(value);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return "";
        return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
    }
}
