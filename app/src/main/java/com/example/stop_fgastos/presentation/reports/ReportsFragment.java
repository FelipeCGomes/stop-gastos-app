package com.example.stop_fgastos.presentation.reports;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceState;
import com.example.stop_fgastos.domain.model.MonthlySummary;
import com.example.stop_fgastos.presentation.common.DisplayRow;
import com.example.stop_fgastos.presentation.common.RecordAdapter;
import com.example.stop_fgastos.presentation.common.UiFormat;
import com.example.stop_fgastos.presentation.common.UiMotion;
import com.example.stop_fgastos.presentation.common.UiPrivacy;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReportsFragment extends Fragment {
    private MainViewModel viewModel;
    private FinanceState state = new FinanceState();
    private YearMonth month = YearMonth.now();
    private TextView monthLabel;
    private TextView summaryLabel;
    private TextView emptyLabel;
    private FinanceBarChartView chart;
    private RecordAdapter adapter;
    private String pendingCsv = "";

    private final ActivityResultLauncher<String> createCsv =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("text/csv"),
                    this::writeCsv
            );

    public ReportsFragment() {
        super(R.layout.fragment_reports);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = ViewModelAccess.from(this);
        monthLabel = view.findViewById(R.id.report_month);
        summaryLabel = view.findViewById(R.id.report_summary);
        emptyLabel = view.findViewById(R.id.report_empty);
        chart = view.findViewById(R.id.report_chart);

        RecyclerView recycler = view.findViewById(R.id.report_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setNestedScrollingEnabled(false);

        adapter = new RecordAdapter(new RecordAdapter.Listener() {
            @Override public void onPrimary(DisplayRow row) {}
            @Override public void onDelete(DisplayRow row) {}
        });
        recycler.setAdapter(adapter);

        view.findViewById(R.id.report_previous).setOnClickListener(v -> {
            month = month.minusMonths(1);
            viewModel.ensureMonth(month);
            render();
        });

        view.findViewById(R.id.report_next).setOnClickListener(v -> {
            month = month.plusMonths(1);
            viewModel.ensureMonth(month);
            render();
        });

        view.findViewById(R.id.report_export).setOnClickListener(v -> {
            pendingCsv = viewModel.reportCsv(month);
            createCsv.launch("stop-gastos-" + month + ".csv");
        });

        viewModel.ensureMonth(month);
        viewModel.finance().observe(getViewLifecycleOwner(), value -> {
            state = value;
            render();
        });

        UiMotion.enter(view);
    }

    private void render() {
        monthLabel.setText(UiFormat.month(month));

        MonthlySummary summary = viewModel.summary(month);
        boolean showPositive = UiPrivacy.showPositiveValues(requireContext());
        summaryLabel.setText(
                (showPositive ? "Receitas   " + UiFormat.money(summary.income()) + "\n" : "")
                        + "Despesas   " + UiFormat.money(summary.expense())
                        + "\nSaldo   " + UiFormat.money(summary.balance())
                        + "\nEconomia   "
                        + String.format(
                                java.util.Locale.getDefault(),
                                "%.1f%%",
                                summary.savingsRate()
                        )
        );

        Map<String, Double> categories = viewModel.categoryExpenses(month);
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(categories.entrySet());
        sorted.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        List<String> chartLabels = new ArrayList<>();
        List<Double> chartValues = new ArrayList<>();
        List<DisplayRow> rows = new ArrayList<>();

        for (Map.Entry<String, Double> entry : sorted) {
            String category = categoryName(entry.getKey());
            chartLabels.add(category);
            chartValues.add(entry.getValue());

            FinanceRecord record = new FinanceRecord(
                    entry.getKey(),
                    Map.of("id", entry.getKey(), "amount", entry.getValue())
            );

            rows.add(new DisplayRow(
                    record,
                    category,
                    "Despesa no mês",
                    "- " + UiFormat.money(entry.getValue()),
                    "",
                    false
            ));
        }

        boolean empty = chartValues.isEmpty();
        chart.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyLabel.setVisibility(empty ? View.VISIBLE : View.GONE);

        chart.setData(chartLabels, chartValues);
        adapter.submit(rows);
    }

    private String categoryName(String id) {
        return state.find(
                        com.example.stop_fgastos.domain.model.FinanceSection.CATEGORIES,
                        id
                )
                .map(record -> {
                    String icon = record.text("icon");
                    String name = record.text("name");
                    return (icon.isBlank() ? "" : icon + " ") + name;
                })
                .orElse(id);
    }

    private void writeCsv(Uri uri) {
        if (uri == null || pendingCsv.isBlank()) return;

        try (OutputStreamWriter writer = new OutputStreamWriter(
                requireContext().getContentResolver().openOutputStream(uri),
                StandardCharsets.UTF_8
        )) {
            writer.write('﻿');
            writer.write(pendingCsv);
        } catch (Exception ignored) {
        }
    }
}
