package com.example.stop_fgastos.presentation.calendar;

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
import com.example.stop_fgastos.presentation.common.UiPrivacy;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CalendarFragment extends Fragment {
    private MainViewModel viewModel;
    private FinanceState state = new FinanceState();
    private YearMonth month = YearMonth.now();
    private TextView monthLabel;
    private TextView summaryLabel;
    private RecordAdapter adapter;

    public CalendarFragment() {
        super(R.layout.fragment_calendar);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);
        monthLabel = view.findViewById(R.id.calendar_month);
        summaryLabel = view.findViewById(R.id.calendar_summary);

        RecyclerView recycler = view.findViewById(R.id.calendar_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecordAdapter(new RecordAdapter.Listener() {
            @Override public void onPrimary(DisplayRow row) {}
            @Override public void onDelete(DisplayRow row) {}
        });
        recycler.setAdapter(adapter);

        view.findViewById(R.id.calendar_previous).setOnClickListener(v -> {
            month = month.minusMonths(1);
            viewModel.ensureMonth(month);
            render();
        });
        view.findViewById(R.id.calendar_next).setOnClickListener(v -> {
            month = month.plusMonths(1);
            viewModel.ensureMonth(month);
            render();
        });

        viewModel.ensureMonth(month);
        viewModel.finance().observe(getViewLifecycleOwner(), value -> {
            state = value;
            render();
        });
    }

    private void render() {
        monthLabel.setText(UiFormat.month(month));
        MonthlySummary summary = viewModel.summary(month);
        boolean showPositive = UiPrivacy.showPositiveValues(requireContext());
        summaryLabel.setText(
                (showPositive ? "Entradas: " + UiFormat.money(summary.income()) + "   " : "")
                        + "Saídas: " + UiFormat.money(summary.expense())
                        + "   Saldo: " + UiFormat.money(summary.balance())
        );

        String key = month.toString();
        List<DisplayRow> rows = new ArrayList<>();

        for (FinanceRecord tx : state.records(FinanceSection.TRANSACTIONS)) {
            if (!tx.text("date").startsWith(key)) continue;
            String installment = tx.integer("installmentCount") > 1
                    ? " · parcela " + tx.integer("installmentNo")
                    + "/" + tx.integer("installmentCount")
                    : "";
            rows.add(new DisplayRow(
                    tx,
                    tx.text("description", "Lançamento"),
                    UiFormat.date(tx.text("date")) + " · Lançamento" + installment,
                    !"expense".equals(tx.text("type")) && !showPositive
                            ? ""
                            : ("expense".equals(tx.text("type")) ? "- " : "+ ")
                            + UiFormat.money(tx.number("amount")),
                    "",
                    false
            ));
        }

        for (FinanceRecord bill : state.records(FinanceSection.BILLS)) {
            if (!bill.text("dueDate").startsWith(key)) continue;
            rows.add(new DisplayRow(
                    bill,
                    bill.text("description", "Conta prevista"),
                    UiFormat.date(bill.text("dueDate"))
                            + " · "
                            + (bill.bool("paid") ? "Pago" : "Previsto"),
                    !"expense".equals(bill.text("type")) && !showPositive
                            ? ""
                            : ("expense".equals(bill.text("type")) ? "- " : "+ ")
                            + UiFormat.money(bill.number("amount")),
                    "",
                    false
            ));
        }

        rows.sort(Comparator.comparing(row ->
                row.record.text("date", row.record.text("dueDate"))));
        adapter.submit(rows);
    }
}
