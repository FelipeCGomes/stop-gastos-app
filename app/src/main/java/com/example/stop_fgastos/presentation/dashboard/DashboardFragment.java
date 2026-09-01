package com.example.stop_fgastos.presentation.dashboard;

import android.os.Bundle;
import android.view.View;
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
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DashboardFragment extends Fragment {
    private MainViewModel viewModel;
    private TextView month, balance, income, expense, savings, empty;
    private FinanceTrendChartView trendChart;
    private RecordAdapter adapter;

    public DashboardFragment() { super(R.layout.fragment_dashboard); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);
        month = view.findViewById(R.id.dashboard_month);
        balance = view.findViewById(R.id.dashboard_balance);
        income = view.findViewById(R.id.dashboard_income);
        expense = view.findViewById(R.id.dashboard_expense);
        savings = view.findViewById(R.id.dashboard_savings);
        empty = view.findViewById(R.id.dashboard_empty);
        trendChart = view.findViewById(R.id.dashboard_trend_chart);

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

    private void render(FinanceState state) {
        YearMonth currentMonth = YearMonth.now();
        MonthlySummary summary = viewModel.summary(currentMonth);
        month.setText(UiFormat.month(currentMonth));
        balance.setText(UiFormat.money(summary.balance()));
        income.setText(UiFormat.money(summary.income()));
        expense.setText(UiFormat.money(summary.expense()));
        savings.setText(String.format(Locale.getDefault(), "Taxa de economia %.1f%%", summary.savingsRate()));

        List<Double> trendIncome = new ArrayList<>();
        List<Double> trendExpense = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            MonthlySummary item = viewModel.summary(currentMonth.minusMonths(i));
            trendIncome.add(item.income());
            trendExpense.add(item.expense());
        }
        trendChart.setSeries(trendIncome, trendExpense);

        List<FinanceRecord> transactions = new ArrayList<>(state.records(FinanceSection.TRANSACTIONS));
        transactions.sort(Comparator.comparing((FinanceRecord record) -> record.text("date")).reversed().thenComparing(record -> record.text("updatedAt"), Comparator.reverseOrder()));

        List<DisplayRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(8, transactions.size()); i++) {
            FinanceRecord tx = transactions.get(i);
            rows.add(new DisplayRow(tx, tx.text("description", "Lançamento"), tx.text("date") + " · " + tx.text("payment"),
                    ("expense".equals(tx.text("type")) ? "- " : "+ ") + UiFormat.money(tx.number("amount")), "", false));
        }
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.submit(rows);
    }
}
