package com.example.stop_fgastos.presentation.planning;

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
import com.example.stop_fgastos.presentation.common.BillPaymentDialog;
import com.example.stop_fgastos.presentation.common.DisplayRow;
import com.example.stop_fgastos.presentation.common.RecordAdapter;
import com.example.stop_fgastos.presentation.common.RecordDialogs;
import com.example.stop_fgastos.presentation.common.UiFormat;
import com.example.stop_fgastos.presentation.common.UiPrivacy;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class PlanningFragment extends Fragment {
    private static final String[] LABELS = {
            "Custos fixos", "Rendas recorrentes", "Contas a pagar/receber", "Orçamentos", "Metas"
    };
    private static final FinanceSection[] SECTIONS = {
            FinanceSection.RECURRING,
            FinanceSection.INCOME_SOURCES,
            FinanceSection.BILLS,
            FinanceSection.BUDGETS,
            FinanceSection.GOALS
    };

    private MainViewModel viewModel;
    private FinanceState state = new FinanceState();
    private Spinner spinner;
    private RecordAdapter adapter;

    public PlanningFragment() {
        super(R.layout.fragment_module);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);

        ((TextView) view.findViewById(R.id.module_title)).setText("Planejamento");
        ((TextView) view.findViewById(R.id.module_subtitle)).setText(
                "Fixos, rendas, contas previstas, orçamentos e metas"
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
            public void onSecondary(DisplayRow row) {
                if (currentSection() != FinanceSection.BILLS) return;

                if (row.record.bool("paid")) {
                    confirmUndoPayment(row.record);
                } else {
                    BillPaymentDialog.show(
                            requireContext(),
                            row.record,
                            (paidAmount, paidDate) -> viewModel.payBill(
                                    row.record,
                                    paidAmount,
                                    paidDate
                            )
                    );
                }
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
        int index = Math.max(0, spinner.getSelectedItemPosition());
        return SECTIONS[index];
    }

    private void openEditor(FinanceRecord existing) {
        FinanceSection section = currentSection();
        RecordDialogs.record(
                requireContext(),
                state,
                section,
                existing,
                record -> {
                    if (section == FinanceSection.RECURRING) {
                        viewModel.saveRecurring(existing, record.fields());
                    } else if (section == FinanceSection.INCOME_SOURCES) {
                        viewModel.saveIncomeSource(existing, record.fields());
                    } else {
                        viewModel.saveRecord(section, record);
                    }
                }
        );
    }

    private void render() {
        if (adapter == null || spinner == null) return;
        FinanceSection section = currentSection();
        List<DisplayRow> rows = new ArrayList<>();

        for (FinanceRecord record : state.records(section)) {
            switch (section) {
                case RECURRING:
                    rows.add(new DisplayRow(
                            record,
                            record.text("description", "Custo fixo"),
                            "Dia " + Math.max(1, record.integer("day"))
                                    + " · " + record.text("payment", "Pix")
                                    + (record.bool("active") ? "" : " · pausado"),
                            UiFormat.money(record.number("amount")),
                            "Editar",
                            true
                    ));
                    break;

                case INCOME_SOURCES:
                    rows.add(new DisplayRow(
                            record,
                            record.text("description", "Renda recorrente"),
                            incomeKind(record.text("kind"))
                                    + " · dia " + Math.max(1, record.integer("day"))
                                    + (record.bool("active") ? "" : " · pausada"),
                            UiPrivacy.showPositiveValues(requireContext())
                                    ? "+ " + UiFormat.money(record.number("amount"))
                                    : "",
                            "Editar",
                            true
                    ));
                    break;

                case BILLS:
                    boolean paid = record.bool("paid");
                    String status = paid
                            ? "Pago"
                            : (record.text("dueDate").compareTo(LocalDate.now().toString()) < 0
                            ? "Vencido"
                            : "Pendente");

                    StringBuilder billSubtitle = new StringBuilder();
                    billSubtitle.append("Vence ")
                            .append(UiFormat.date(record.text("dueDate")))
                            .append(" · ")
                            .append(status);

                    if (paid) {
                        double paidAmount = record.number("paidAmount") > 0
                                ? record.number("paidAmount")
                                : record.number("amount");

                        billSubtitle.append(" em ").append(UiFormat.date(record.text("paidAt")));
                        billSubtitle.append(" · Valor pago: ").append(UiFormat.money(paidAmount));

                        int daysLate = record.integer("daysLate");
                        double lateFee = record.number("lateFeeAmount");
                        double discount = record.number("discountAmount");

                        if (daysLate > 0) {
                            billSubtitle.append(" · ").append(daysLate).append(" dia(s) atraso");
                        }
                        if (lateFee > 0.005) {
                            billSubtitle.append(" · juros ").append(UiFormat.money(lateFee));
                        } else if (discount > 0.005) {
                            billSubtitle.append(" · desconto ").append(UiFormat.money(discount));
                        }
                    }

                    double displayAmount = paid && record.number("paidAmount") > 0
                            ? record.number("paidAmount")
                            : record.number("amount");

                    rows.add(new DisplayRow(
                            record,
                            record.text("description", "Conta"),
                            billSubtitle.toString(),
                            !"expense".equals(record.text("type"))
                                    && !UiPrivacy.showPositiveValues(requireContext())
                                    ? ""
                                    : ("expense".equals(record.text("type")) ? "- " : "+ ")
                                    + UiFormat.money(displayAmount),
                            "Editar",
                            paid ? "Desfazer pagamento" : "Registrar pagamento",
                            true
                    ));
                    break;

                case BUDGETS:
                    double spent = spentForCategory(record.text("category"));
                    rows.add(new DisplayRow(
                            record,
                            categoryLabel(record.text("category")),
                            UiFormat.money(spent) + " utilizado no mês",
                            UiFormat.money(record.number("amount")),
                            "Editar",
                            true
                    ));
                    break;

                case GOALS:
                    double target = record.number("target");
                    double current = record.number("current");
                    double progress = target <= 0 ? 0 : Math.min(100, (current / target) * 100);
                    rows.add(new DisplayRow(
                            record,
                            record.text("icon", "🎯") + " " + record.text("name", "Meta"),
                            String.format(
                                    java.util.Locale.getDefault(),
                                    "%.0f%% · %s",
                                    progress,
                                    UiFormat.date(record.text("deadline"))
                            ),
                            UiFormat.money(current) + " / " + UiFormat.money(target),
                            "Editar",
                            true
                    ));
                    break;

                default:
                    break;
            }
        }
        adapter.submit(rows);
    }

    private void confirmUndoPayment(FinanceRecord bill) {
        double paidAmount = bill.number("paidAmount") > 0
                ? bill.number("paidAmount")
                : bill.number("amount");

        String detail = "Pagamento registrado em " + UiFormat.date(bill.text("paidAt"))
                + " no valor de " + UiFormat.money(paidAmount) + ".";

        if (bill.number("lateFeeAmount") > 0.005) {
            detail += "\nJuros/acréscimos registrados: "
                    + UiFormat.money(bill.number("lateFeeAmount")) + ".";
        }

        detail += "\n\nAo desfazer, o lançamento financeiro criado por este pagamento também será removido.";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Marcar como não pago?")
                .setMessage(detail)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Desfazer pagamento", (dialog, which) ->
                        viewModel.undoBillPayment(bill)
                )
                .show();
    }

    private double spentForCategory(String category) {
        String month = YearMonth.now().toString();
        double total = 0.0;
        for (FinanceRecord tx : state.records(FinanceSection.TRANSACTIONS)) {
            if ("expense".equals(tx.text("type"))
                    && category.equals(tx.text("category"))
                    && tx.text("date").startsWith(month)) {
                total += tx.number("amount");
            }
        }
        return total;
    }

    private String categoryLabel(String id) {
        return state.find(FinanceSection.CATEGORIES, id)
                .map(record -> record.text("icon") + " " + record.text("name"))
                .orElse(id);
    }

    private String incomeKind(String value) {
        switch (value) {
            case "extra": return "Renda extra";
            case "freelance": return "Freelance";
            case "rent": return "Aluguel recebido";
            case "commission": return "Comissão";
            case "other": return "Outra renda";
            default: return "Salário";
        }
    }
}
