package com.example.stop_fgastos.presentation.transactions;

import android.os.Bundle;
import android.view.View;
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
import com.example.stop_fgastos.presentation.common.UiPrivacy;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TransactionsFragment extends Fragment {
    private MainViewModel viewModel;
    private RecordAdapter adapter;
    private FinanceState state = new FinanceState();

    public TransactionsFragment() {
        super(R.layout.fragment_module);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);

        ((TextView) view.findViewById(R.id.module_title)).setText("Lançamentos");
        ((TextView) view.findViewById(R.id.module_subtitle)).setText(
                "Receitas, despesas, cartões e parcelamentos"
        );
        Spinner spinner = view.findViewById(R.id.module_spinner);
        spinner.setVisibility(View.GONE);

        MaterialButton add = view.findViewById(R.id.module_add);
        add.setText("Novo lançamento");
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
                viewModel.deleteTransaction(row.record);
            }
        });
        recycler.setAdapter(adapter);

        viewModel.finance().observe(getViewLifecycleOwner(), value -> {
            state = value;
            render();
        });
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
        List<FinanceRecord> transactions = new ArrayList<>(
                state.records(FinanceSection.TRANSACTIONS)
        );
        transactions.sort(Comparator
                .comparing((FinanceRecord record) -> record.text("date"))
                .reversed()
                .thenComparing(record -> record.text("updatedAt"), Comparator.reverseOrder()));

        List<DisplayRow> rows = new ArrayList<>();
        for (FinanceRecord tx : transactions) {
            String installment = tx.integer("installmentCount") > 1
                    ? " · " + tx.integer("installmentNo") + "/" + tx.integer("installmentCount")
                    : "";
            rows.add(new DisplayRow(
                    tx,
                    tx.text("description", "Lançamento"),
                    tx.text("date") + " · " + tx.text("payment") + installment,
                    !"expense".equals(tx.text("type")) && !UiPrivacy.showPositiveValues(requireContext())
                            ? ""
                            : ("expense".equals(tx.text("type")) ? "- " : "+ ") + UiFormat.money(tx.number("amount")),
                    "Editar",
                    true
            ));
        }
        adapter.submit(rows);
    }
}
