package com.example.stop_fgastos.presentation.transactions;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
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
import com.example.stop_fgastos.presentation.common.BillPaymentDialog;
import com.example.stop_fgastos.presentation.common.DisplayRow;
import com.example.stop_fgastos.presentation.common.RecordAdapter;
import com.example.stop_fgastos.presentation.common.RecordDialogs;
import com.example.stop_fgastos.presentation.common.UiFormat;
import com.example.stop_fgastos.presentation.common.UiMotion;
import com.example.stop_fgastos.presentation.common.UiPrivacy;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TransactionsFragment extends Fragment {
    private MainViewModel viewModel;
    private RecordAdapter adapter;
    private FinanceState state = new FinanceState();

    private EditText search;
    private Spinner typeFilter;
    private TextView incomeSummary;
    private TextView expenseSummary;
    private TextView balanceSummary;
    private TextView countSummary;

    public TransactionsFragment() {
        super(R.layout.fragment_transactions);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);

        search = view.findViewById(R.id.transactions_search);
        typeFilter = view.findViewById(R.id.transactions_type_filter);
        incomeSummary = view.findViewById(R.id.transactions_income);
        expenseSummary = view.findViewById(R.id.transactions_expense);
        balanceSummary = view.findViewById(R.id.transactions_balance);
        countSummary = view.findViewById(R.id.transactions_count);

        typeFilter.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Todos os tipos", "Despesas", "Receitas"}
        ));

        view.findViewById(R.id.transactions_add).setOnClickListener(v -> openEditor(null));

        RecyclerView recycler = view.findViewById(R.id.transactions_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecordAdapter(new RecordAdapter.Listener() {
            @Override
            public void onPrimary(DisplayRow row) {
                openEditor(row.record);
            }

            @Override
            public void onSecondary(DisplayRow row) {
                FinanceRecord transaction = row.record;
                if (!isPaymentManaged(transaction)) return;

                if (transaction.bool("paid")) {
                    confirmUndoPayment(transaction);
                } else {
                    BillPaymentDialog.show(
                            requireContext(),
                            transaction,
                            (paidAmount, paidDate) -> viewModel.payTransaction(
                                    transaction,
                                    paidAmount,
                                    paidDate
                            )
                    );
                }
            }

            @Override
            public void onDelete(DisplayRow row) {
                viewModel.deleteTransaction(row.record);
            }
        });
        recycler.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        typeFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { render(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        viewModel.finance().observe(getViewLifecycleOwner(), value -> {
            state = value == null ? new FinanceState() : value;
            render();
        });

        UiMotion.enter(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    private void openEditor(FinanceRecord existing) {
        RecordDialogs.transaction(
                requireContext(),
                state,
                existing,
                input -> viewModel.saveTransaction(existing, input)
        );
    }

    private void render() {
        if (adapter == null || !isAdded()) return;

        YearMonth month = YearMonth.now();
        MonthlySummary summary = viewModel.summary(month);
        boolean showPositive = UiPrivacy.showPositiveValues(requireContext());

        incomeSummary.setText(showPositive ? UiFormat.money(summary.income()) : "Oculto");
        expenseSummary.setText(UiFormat.money(summary.expense()));
        balanceSummary.setText(UiFormat.money(summary.balance()));

        List<FinanceRecord> monthTransactions = new ArrayList<>();
        for (FinanceRecord tx : state.records(FinanceSection.TRANSACTIONS)) {
            if (tx.text("date").startsWith(month.toString())) {
                monthTransactions.add(tx);
            }
        }
        countSummary.setText(String.valueOf(monthTransactions.size()));

        String query = search == null
                ? ""
                : search.getText().toString().trim().toLowerCase(Locale.getDefault());
        int filter = typeFilter == null ? 0 : typeFilter.getSelectedItemPosition();

        monthTransactions.sort(
                Comparator.comparing((FinanceRecord record) -> record.text("date"))
                        .reversed()
                        .thenComparing(
                                record -> record.text("updatedAt"),
                                Comparator.reverseOrder()
                        )
        );

        List<DisplayRow> rows = new ArrayList<>();

        for (FinanceRecord tx : monthTransactions) {
            boolean expense = "expense".equals(tx.text("type"));
            if (filter == 1 && !expense) continue;
            if (filter == 2 && expense) continue;

            String category = categoryLabel(tx.text("category"));
            String haystack = (
                    tx.text("description") + " "
                            + category + " "
                            + tx.text("notes") + " "
                            + tx.text("tags")
            ).toLowerCase(Locale.getDefault());

            if (!query.isBlank() && !haystack.contains(query)) continue;

            String installment = tx.integer("installmentCount") > 1
                    ? " · " + tx.integer("installmentNo") + "/" + tx.integer("installmentCount")
                    : "";

            boolean paymentManaged = isPaymentManaged(tx);
            boolean paid = tx.bool("paid");

            String value = !expense && !showPositive
                    ? ""
                    : (expense ? "- " : "+ ") + UiFormat.money(tx.number("amount"));

            String subtitle = category + " · " + UiFormat.date(tx.text("date")) + installment;
            if (paymentManaged) {
                if (paid) {
                    subtitle += " · Pago em " + UiFormat.date(tx.text("paidAt"));
                    double paidAmount = tx.number("paidAmount");
                    if (paidAmount > 0) {
                        subtitle += " · Valor pago: " + UiFormat.money(paidAmount);
                    }
                    if (tx.number("lateFeeAmount") > 0.005) {
                        subtitle += " · Juros: "
                                + UiFormat.money(tx.number("lateFeeAmount"));
                    }
                } else {
                    LocalDate dueDate = parseDate(
                            tx.text("dueDate", tx.text("date"))
                    );
                    subtitle += dueDate.isBefore(LocalDate.now())
                            ? " · Vencido"
                            : " · Pendente";
                }
            }

            String paymentAction = paymentManaged
                    ? (paid ? "Desfazer pagamento" : "Registrar pagamento")
                    : "";

            rows.add(new DisplayRow(
                    tx,
                    tx.text("description", "Lançamento"),
                    subtitle,
                    value,
                    "Editar",
                    paymentAction,
                    true
            ));
        }

        adapter.submit(rows);
    }

    private boolean isPaymentManaged(FinanceRecord transaction) {
        return "expense".equals(transaction.text("type"))
                && (
                "recurringExpense".equals(transaction.text("sourceType"))
                        || transaction.fields().containsKey("paid")
                        || !transaction.text("paidAt").isBlank()
        );
    }

    private void confirmUndoPayment(FinanceRecord transaction) {
        double paidAmount = transaction.number("paidAmount") > 0
                ? transaction.number("paidAmount")
                : transaction.number("amount");

        StringBuilder message = new StringBuilder();
        message.append("Pagamento registrado em ")
                .append(UiFormat.date(transaction.text("paidAt")))
                .append(" no valor de ")
                .append(UiFormat.money(paidAmount))
                .append(".");

        if (transaction.number("lateFeeAmount") > 0.005) {
            message.append("\nJuros/acréscimos: ")
                    .append(UiFormat.money(transaction.number("lateFeeAmount")))
                    .append(".");
        }

        message.append("\n\nO lançamento voltará para Pendente e o valor original será restaurado.");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Desfazer pagamento?")
                .setMessage(message.toString())
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                        "Desfazer",
                        (dialog, which) ->
                                viewModel.undoTransactionPayment(transaction)
                )
                .show();
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private String categoryLabel(String id) {
        return state.find(FinanceSection.CATEGORIES, id)
                .map(record -> {
                    String icon = record.text("icon");
                    String name = record.text("name", id);
                    return (icon.isBlank() ? "" : icon + " ") + name;
                })
                .orElse(id == null || id.isBlank() ? "Sem categoria" : id);
    }
}
