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
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DashboardFragment extends Fragment {
    private MainViewModel viewModel;
    private TextView month;
    private TextView balance;
    private TextView income;
    private TextView expense;
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

        RecyclerView recycler = view.findViewById(R.id.dashboard_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecordAdapter(new RecordAdapter.Listener() {
            @Override public void onPrimary(DisplayRow row) {}
            @Override public void onDelete(DisplayRow row) {}
        });
        recycler.setAdapter(adapter);

        YearMonth currentMonth = YearMonth.now();
        viewModel.ensureMonth(currentMonth);
        viewModel.finance().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(FinanceState state) {
        YearMonth currentMonth = YearMonth.now();
        MonthlySummary summary = viewModel.summary(currentMonth);

        month.setText(UiFormat.month(currentMonth));
        balance.setText(UiFormat.money(summary.balance()));
        income.setText("Entradas\n" + UiFormat.money(summary.income()));
        expense.setText("Saídas\n" + UiFormat.money(summary.expense()));

        List<FinanceRecord> transactions = new ArrayList<>(
                state.records(FinanceSection.TRANSACTIONS)
        );
        transactions.sort(Comparator
                .comparing((FinanceRecord record) -> record.text("date"))
                .reversed()
                .thenComparing(record -> record.text("updatedAt"), Comparator.reverseOrder()));

        List<DisplayRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(8, transactions.size()); i++) {
            FinanceRecord tx = transactions.get(i);
            rows.add(new DisplayRow(
                    tx,
                    tx.text("description", "Lançamento"),
                    tx.text("date") + " · " + tx.text("payment"),
                    ("expense".equals(tx.text("type")) ? "- " : "+ ") + UiFormat.money(tx.number("amount")),
                    "",
                    false
            ));
        }
        adapter.submit(rows);
    }
}
