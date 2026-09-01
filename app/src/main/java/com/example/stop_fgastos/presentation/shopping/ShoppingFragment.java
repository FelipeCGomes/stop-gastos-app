package com.example.stop_fgastos.presentation.shopping;

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
import com.example.stop_fgastos.domain.model.MarketComparison;
import com.example.stop_fgastos.presentation.common.DisplayRow;
import com.example.stop_fgastos.presentation.common.RecordAdapter;
import com.example.stop_fgastos.presentation.common.RecordDialogs;
import com.example.stop_fgastos.presentation.common.UiFormat;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShoppingFragment extends Fragment {
    private MainViewModel viewModel;
    private FinanceState state = new FinanceState();
    private Spinner spinner;
    private RecordAdapter adapter;
    private TextView comparison;
    private MaterialButton addItem;
    private MaterialButton editList;
    private MaterialButton deleteList;
    private List<FinanceRecord> lists = new ArrayList<>();

    public ShoppingFragment() {
        super(R.layout.fragment_shopping);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);

        spinner = view.findViewById(R.id.shopping_list_spinner);
        comparison = view.findViewById(R.id.shopping_comparison);
        addItem = view.findViewById(R.id.shopping_add_item);
        editList = view.findViewById(R.id.shopping_edit_list);
        deleteList = view.findViewById(R.id.shopping_delete_list);

        RecyclerView recycler = view.findViewById(R.id.shopping_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecordAdapter(new RecordAdapter.Listener() {
            @Override
            public void onPrimary(DisplayRow row) {
                FinanceRecord active = activeList();
                if (active == null) return;
                RecordDialogs.shoppingItem(
                        requireContext(),
                        row.record,
                        row.record.integer("order"),
                        item -> saveItem(active, item)
                );
            }

            @Override
            public void onDelete(DisplayRow row) {
                FinanceRecord active = activeList();
                if (active != null) deleteItem(active, row.record.id());
            }
        });
        recycler.setAdapter(adapter);

        view.findViewById(R.id.shopping_add_list).setOnClickListener(v ->
                RecordDialogs.record(
                        requireContext(),
                        state,
                        FinanceSection.SHOPPING_LISTS,
                        null,
                        record -> viewModel.saveRecord(FinanceSection.SHOPPING_LISTS, record)
                )
        );

        editList.setOnClickListener(v -> {
            FinanceRecord active = activeList();
            if (active == null) return;
            RecordDialogs.record(
                    requireContext(),
                    state,
                    FinanceSection.SHOPPING_LISTS,
                    active,
                    record -> viewModel.saveRecord(FinanceSection.SHOPPING_LISTS, record)
            );
        });

        deleteList.setOnClickListener(v -> {
            FinanceRecord active = activeList();
            if (active == null) return;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Excluir lista?")
                    .setMessage(active.text("name", "Lista") + " e seus itens serão removidos.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Excluir", (dialog, which) ->
                            viewModel.deleteRecord(FinanceSection.SHOPPING_LISTS, active))
                    .show();
        });

        addItem.setOnClickListener(v -> {
            FinanceRecord active = activeList();
            if (active == null) return;
            RecordDialogs.shoppingItem(
                    requireContext(),
                    null,
                    readItems(active).size() + 1,
                    item -> saveItem(active, item)
            );
        });

        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                renderItems();
            }

            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        viewModel.finance().observe(getViewLifecycleOwner(), value -> {
            String selectedId = activeList() == null ? "" : activeList().id();
            state = value;
            lists = new ArrayList<>(state.records(FinanceSection.SHOPPING_LISTS));
            lists.sort(Comparator.comparing(record -> record.text("name", "Lista")));
            bindSpinner(selectedId);
            renderItems();
            renderComparison();
        });
    }

    private void bindSpinner(String selectedId) {
        List<String> names = new ArrayList<>();
        for (FinanceRecord list : lists) {
            names.add(list.text("name", "Lista")
                    + (list.text("store").isBlank() ? "" : " · " + list.text("store")));
        }
        spinner.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                names
        ));

        int selected = 0;
        for (int i = 0; i < lists.size(); i++) {
            if (lists.get(i).id().equals(selectedId)) {
                selected = i;
                break;
            }
        }
        if (!lists.isEmpty()) spinner.setSelection(selected);

        boolean hasList = !lists.isEmpty();
        addItem.setEnabled(hasList);
        editList.setEnabled(hasList);
        deleteList.setEnabled(hasList);
    }

    private FinanceRecord activeList() {
        int position = spinner == null ? -1 : spinner.getSelectedItemPosition();
        if (position < 0 || position >= lists.size()) return null;
        return lists.get(position);
    }

    private void renderItems() {
        FinanceRecord active = activeList();
        if (active == null) {
            adapter.submit(List.of());
            return;
        }

        List<FinanceRecord> items = readItems(active);
        items.sort(Comparator.comparingInt(record -> record.integer("order")));

        List<DisplayRow> rows = new ArrayList<>();
        for (FinanceRecord item : items) {
            double total = item.number("qty") * item.number("unitPrice");
            rows.add(new DisplayRow(
                    item,
                    item.text("product", "Produto"),
                    formatQty(item.number("qty")) + " × " + UiFormat.money(item.number("unitPrice")),
                    UiFormat.money(total),
                    "Editar",
                    true
            ));
        }
        adapter.submit(rows);
    }

    private void renderComparison() {
        MarketComparison model = viewModel.compareShopping(lists);
        if (model.ranking().isEmpty()) {
            comparison.setText(
                    lists.size() < 2
                            ? "Crie duas listas de mercados para comparar preços."
                            : "Preencha preços dos mesmos produtos em pelo menos dois mercados."
            );
            return;
        }

        StringBuilder text = new StringBuilder("Comparação de mercados\n");
        for (int i = 0; i < model.ranking().size(); i++) {
            MarketComparison.MarketRow row = model.ranking().get(i);
            text.append(i + 1)
                    .append(". ")
                    .append(row.label)
                    .append(" · ")
                    .append(UiFormat.money(row.comparableTotal))
                    .append(" (")
                    .append(row.commonProducts)
                    .append(" itens comuns)\n");
        }
        text.append("Economia entre melhor e pior: ")
                .append(UiFormat.money(model.savings()))
                .append("\nCompra dividida no menor preço: ")
                .append(UiFormat.money(model.bestSplit()));

        comparison.setText(text.toString());
    }

    private void saveItem(FinanceRecord list, FinanceRecord item) {
        List<Map<String, Object>> raw = new ArrayList<>();
        boolean replaced = false;

        for (FinanceRecord current : readItems(list)) {
            if (current.id().equals(item.id())) {
                raw.add(item.toMap());
                replaced = true;
            } else {
                raw.add(current.toMap());
            }
        }
        if (!replaced) raw.add(item.toMap());

        Map<String, Object> updated = new LinkedHashMap<>(list.fields());
        updated.put("items", raw);
        updated.put("updatedAt", Instant.now().toString());
        viewModel.saveRecord(
                FinanceSection.SHOPPING_LISTS,
                new FinanceRecord(list.id(), updated)
        );
    }

    private void deleteItem(FinanceRecord list, String itemId) {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (FinanceRecord item : readItems(list)) {
            if (!item.id().equals(itemId)) raw.add(item.toMap());
        }

        Map<String, Object> updated = new LinkedHashMap<>(list.fields());
        updated.put("items", raw);
        updated.put("updatedAt", Instant.now().toString());
        viewModel.saveRecord(
                FinanceSection.SHOPPING_LISTS,
                new FinanceRecord(list.id(), updated)
        );
    }

    @SuppressWarnings("unchecked")
    private List<FinanceRecord> readItems(FinanceRecord list) {
        List<FinanceRecord> result = new ArrayList<>();
        Object raw = list.fields().get("items");
        if (!(raw instanceof List<?>)) return result;

        for (Object value : (List<?>) raw) {
            if (!(value instanceof Map<?, ?>)) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            String id = String.valueOf(map.getOrDefault("id", ""));
            if (!id.isBlank()) result.add(new FinanceRecord(id, map));
        }
        return result;
    }

    private String formatQty(double value) {
        return value == Math.rint(value)
                ? String.valueOf((int) value)
                : String.format(java.util.Locale.getDefault(), "%.2f", value);
    }
}
