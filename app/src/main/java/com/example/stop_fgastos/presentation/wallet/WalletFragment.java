package com.example.stop_fgastos.presentation.wallet;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
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
import com.example.stop_fgastos.presentation.common.DisplayRow;
import com.example.stop_fgastos.presentation.common.RecordAdapter;
import com.example.stop_fgastos.presentation.common.RecordDialogs;
import com.example.stop_fgastos.presentation.common.UiFormat;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;
import com.google.android.material.button.MaterialButton;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class WalletFragment extends Fragment {
    private static final String[] LABELS = {"Contas e carteiras", "Cartões e benefícios", "Transferências"};
    private static final FinanceSection[] SECTIONS = {
            FinanceSection.ACCOUNTS,
            FinanceSection.CARDS,
            FinanceSection.TRANSFERS
    };

    private MainViewModel viewModel;
    private FinanceState state = new FinanceState();
    private Spinner spinner;
    private RecordAdapter adapter;

    public WalletFragment() {
        super(R.layout.fragment_module);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);

        ((TextView) view.findViewById(R.id.module_title)).setText("Carteira");
        ((TextView) view.findViewById(R.id.module_subtitle)).setText(
                "Contas, cartões, benefícios e transferências"
        );

        spinner = view.findViewById(R.id.module_spinner);
        spinner.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                LABELS
        ));

        MaterialButton add = view.findViewById(R.id.module_add);
        add.setOnClickListener(v -> openEditor(null));

        RecyclerView recycler = view.findViewById(R.id.module_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecordAdapter(new RecordAdapter.Listener() {
            @Override
            public void onPrimary(DisplayRow row) {
                openEditor(row.record);
            }

            @Override
            public void onDelete(DisplayRow row) {
                viewModel.deleteRecord(currentSection(), row.record);
            }
        });
        recycler.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                render();
                add.setText("Adicionar " + LABELS[position].toLowerCase());
            }

            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        viewModel.finance().observe(getViewLifecycleOwner(), value -> {
            state = value;
            render();
        });
    }

    private FinanceSection currentSection() {
        return SECTIONS[Math.max(0, spinner.getSelectedItemPosition())];
    }

    private void openEditor(FinanceRecord existing) {
        FinanceSection section = currentSection();
        RecordDialogs.record(
                requireContext(),
                state,
                section,
                existing,
                record -> viewModel.saveRecord(section, record)
        );
    }

    private void render() {
        if (adapter == null || spinner == null) return;
        FinanceSection section = currentSection();
        List<DisplayRow> rows = new ArrayList<>();

        for (FinanceRecord record : state.records(section)) {
            if (section == FinanceSection.ACCOUNTS) {
                rows.add(new DisplayRow(
                        record,
                        record.text("icon", "🏦") + " " + record.text("name", "Conta"),
                        record.text("type", "Conta"),
                        UiFormat.money(viewModel.accountBalance(record.id())),
                        "Editar",
                        true
                ));
            } else if (section == FinanceSection.CARDS) {
                rows.add(new DisplayRow(
                        record,
                        record.text("name", "Cartão"),
                        cardType(record.text("cardType"))
                                + ("credit".equals(record.text("cardType"))
                                ? " · fecha " + record.integer("closingDay")
                                + " · vence " + record.integer("dueDay")
                                : ""),
                        UiFormat.money(cardUsage(record))
                                + " / " + UiFormat.money(record.number("limit")),
                        "Editar",
                        true
                ));
            } else if (section == FinanceSection.TRANSFERS) {
                rows.add(new DisplayRow(
                        record,
                        accountName(record.text("fromAccountId"))
                                + " → " + accountName(record.text("toAccountId")),
                        record.text("date") + (record.text("notes").isBlank()
                                ? ""
                                : " · " + record.text("notes")),
                        UiFormat.money(record.number("amount")),
                        "Editar",
                        true
                ));
            }
        }
        adapter.submit(rows);
    }

    private String accountName(String id) {
        return state.find(FinanceSection.ACCOUNTS, id)
                .map(record -> record.text("name", "Conta"))
                .orElse("Conta");
    }

    private double cardUsage(FinanceRecord card) {
        String month = YearMonth.now().toString();
        double total = 0.0;
        boolean benefit = !"credit".equals(card.text("cardType", "credit"));
        for (FinanceRecord tx : state.records(FinanceSection.TRANSACTIONS)) {
            if (!"expense".equals(tx.text("type")) || !card.id().equals(tx.text("cardId"))) continue;
            boolean sameMonth = benefit
                    ? tx.text("purchaseDate").startsWith(month)
                    : tx.text("invoiceMonth").startsWith(month);
            if (sameMonth) total += tx.number("amount");
        }
        return total;
    }

    private String cardType(String type) {
        switch (type) {
            case "meal": return "Vale-refeição";
            case "food": return "Vale-alimentação";
            case "fuel": return "Vale-combustível";
            case "benefit": return "Outro benefício";
            default: return "Cartão de crédito";
        }
    }
}
