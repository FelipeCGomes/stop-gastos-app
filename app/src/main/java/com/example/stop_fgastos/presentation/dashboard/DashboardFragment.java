package com.example.stop_fgastos.presentation.dashboard;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DashboardFragment extends Fragment {
    private MainViewModel viewModel;
    private TextView month;
    private TextView balance;
    private TextView income;
    private TextView expense;
    private TextView savings;
    private TextView balanceStatus;
    private TextView healthTitle;
    private TextView healthDetail;
    private TextView healthScore;
    private TextView empty;
    private ImageButton privacyButton;
    private boolean privacyEnabled;
    private FinanceState latestState = new FinanceState();
    private LinearProgressIndicator healthProgress;
    private FinanceTrendChartView trendChart;
    private RecordAdapter adapter;

    public DashboardFragment() {
        super(R.layout.fragment_dashboard);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);

        month = view.findViewById(R.id.dashboard_month);
        balance = view.findViewById(R.id.dashboard_balance);
        income = view.findViewById(R.id.dashboard_income);
        expense = view.findViewById(R.id.dashboard_expense);
        savings = view.findViewById(R.id.dashboard_savings);
        balanceStatus = view.findViewById(R.id.dashboard_balance_status);
        healthTitle = view.findViewById(R.id.dashboard_health_title);
        healthDetail = view.findViewById(R.id.dashboard_health_detail);
        healthScore = view.findViewById(R.id.dashboard_health_score);
        healthProgress = view.findViewById(R.id.dashboard_health_progress);
        empty = view.findViewById(R.id.dashboard_empty);
        privacyButton = view.findViewById(R.id.dashboard_privacy);
        trendChart = view.findViewById(R.id.dashboard_trend_chart);

        privacyEnabled = UiPrivacy.enabled(requireContext());
        renderPrivacyIcon();
        privacyButton.setOnClickListener(v -> {
            privacyEnabled = !privacyEnabled;
            UiPrivacy.setEnabled(requireContext(), privacyEnabled);
            renderPrivacyIcon();
            UiMotion.pop(v);
            render(latestState);
        });

        RecyclerView recycler = view.findViewById(R.id.dashboard_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setNestedScrollingEnabled(false);

        adapter = new RecordAdapter(new RecordAdapter.Listener() {
            @Override public void onPrimary(DisplayRow row) {}
            @Override public void onDelete(DisplayRow row) {}
        });
        recycler.setAdapter(adapter);

        UiMotion.enter(view);

        YearMonth currentMonth = YearMonth.now();
        viewModel.ensureMonth(currentMonth);
        viewModel.finance().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean updated = UiPrivacy.showPositiveValues(requireContext());
        if (updated != showPositiveValues) {
            showPositiveValues = updated;
            render(latestState);
        }
    }

    private void render(FinanceState state) {
        latestState = state == null ? new FinanceState() : state;
        YearMonth currentMonth = YearMonth.now();
        MonthlySummary summary = viewModel.summary(currentMonth);

        month.setText(UiFormat.month(currentMonth));
        balance.setText(privateMoney(summary.balance()));
        income.setText(privateMoney(summary.income()));
        expense.setText(privateMoney(summary.expense()));

        if (privacyEnabled) {
            savings.setText("Valores ocultos");
            balanceStatus.setText("Toque no olho para revelar");
        } else {
            savings.setText(String.format(
                    Locale.getDefault(),
                    "Economia %.1f%%",
                    summary.savingsRate()
            ));
            balanceStatus.setText(
                    summary.balance() > 0
                            ? "Saldo positivo"
                            : summary.balance() < 0
                            ? "Saldo negativo"
                            : "Mês equilibrado"
            );
        }

        renderHealth(summary);

        List<String> labels = new ArrayList<>();
        List<Double> trendIncome = new ArrayList<>();
        List<Double> trendExpense = new ArrayList<>();

        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern(
                "MMM",
                new Locale("pt", "BR")
        );

        for (int i = 5; i >= 0; i--) {
            YearMonth target = currentMonth.minusMonths(i);
            MonthlySummary item = viewModel.summary(target);

            String label = target.format(labelFormatter)
                    .replace(".", "")
                    .toLowerCase(Locale.getDefault());

            labels.add(label);
            trendIncome.add(item.income());
            trendExpense.add(item.expense());
        }

        trendChart.setPrivacyEnabled(privacyEnabled);
        trendChart.setSeries(labels, trendIncome, trendExpense);

        List<FinanceRecord> transactions = new ArrayList<>(
                state.records(FinanceSection.TRANSACTIONS)
        );

        transactions.sort(
                Comparator.comparing((FinanceRecord record) -> record.text("date"))
                        .reversed()
                        .thenComparing(
                                record -> record.text("updatedAt"),
                                Comparator.reverseOrder()
                        )
        );

        List<DisplayRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(8, transactions.size()); i++) {
            FinanceRecord tx = transactions.get(i);

            rows.add(new DisplayRow(
                    tx,
                    tx.text("description", "Lançamento"),
                    tx.text("date") + " · " + tx.text("payment"),
                    privacyEnabled
                            ? "••••"
                            : ("expense".equals(tx.text("type")) ? "- " : "+ ")
                            + UiFormat.money(tx.number("amount")),
                    "",
                    false
            ));
        }

        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.submit(rows);

        UiMotion.pop(balance);
    }

    private String privateMoney(double value) {
        return privacyEnabled ? "••••" : UiFormat.money(value);
    }

    private void renderPrivacyIcon() {
        if (privacyButton == null) return;
        privacyButton.setImageResource(
                privacyEnabled ? R.drawable.ic_visibility_off : R.drawable.ic_visibility
        );
        privacyButton.setContentDescription(
                privacyEnabled ? "Mostrar valores" : "Ocultar valores"
        );
    }

    private void renderHealth(MonthlySummary summary) {
        if (summary.income() == 0 && summary.expense() == 0) {
            healthScore.setText("0/100");
            healthTitle.setText("Comece seu mês");
            healthDetail.setText("Adicione receitas e despesas para gerar seu indicador.");
            healthProgress.setProgressCompat(0, true);
            return;
        }

        int score;
        if (summary.income() <= 0 && summary.expense() > 0) {
            score = 10;
        } else {
            double savingsRate = Math.max(-40, Math.min(60, summary.savingsRate()));
            score = (int) Math.round(40 + savingsRate);
            score = Math.max(0, Math.min(100, score));
        }

        healthScore.setText(score + "/100");
        healthProgress.setProgressCompat(score, true);

        if (score >= 80) {
            healthTitle.setText("Excelente controle");
            healthDetail.setText("Seu saldo e sua taxa de economia estão muito saudáveis.");
        } else if (score >= 60) {
            healthTitle.setText("Mês saudável");
            healthDetail.setText("Você está mantendo uma boa relação entre entradas e saídas.");
        } else if (score >= 40) {
            healthTitle.setText("Atenção ao ritmo");
            healthDetail.setText("Os gastos estão consumindo uma parcela importante da renda.");
        } else {
            healthTitle.setText("Orçamento pressionado");
            healthDetail.setText("Vale revisar despesas e contas previstas para o restante do mês.");
        }
    }
}
