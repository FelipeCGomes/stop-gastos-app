package com.example.stop_fgastos.presentation.common;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.model.FinanceState;
import com.example.stop_fgastos.domain.model.TransactionInput;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecordDialogs {
    public interface TransactionHandler {
        void onSave(TransactionInput input);
    }

    public interface RecordHandler {
        void onSave(FinanceRecord record);
    }

    public interface TextHandler {
        void onSave(String value);
    }

    private static final class Choice {
        final String label;
        final String value;

        Choice(String label, String value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class Form {
        final View root;
        final EditText[] fields;
        final Spinner[] spinners;
        final CheckBox check;

        Form(Context context) {
            root = LayoutInflater.from(context).inflate(R.layout.dialog_record, null, false);
            fields = new EditText[] {
                    root.findViewById(R.id.field1),
                    root.findViewById(R.id.field2),
                    root.findViewById(R.id.field3),
                    root.findViewById(R.id.field4),
                    root.findViewById(R.id.field5),
                    root.findViewById(R.id.field6)
            };
            spinners = new Spinner[] {
                    root.findViewById(R.id.spinner1),
                    root.findViewById(R.id.spinner2),
                    root.findViewById(R.id.spinner3),
                    root.findViewById(R.id.spinner4),
                    root.findViewById(R.id.spinner5)
            };
            check = root.findViewById(R.id.check1);
            hideAll();
        }

        void hideAll() {
            for (EditText field : fields) field.setVisibility(View.GONE);
            for (Spinner spinner : spinners) spinner.setVisibility(View.GONE);
            check.setVisibility(View.GONE);
        }

        EditText field(int index, String hint, String value, boolean number) {
            EditText field = fields[index];
            field.setVisibility(View.VISIBLE);
            field.setHint(hint);
            field.setText(value == null ? "" : value);
            if (number) {
                field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            } else {
                field.setInputType(InputType.TYPE_CLASS_TEXT);
            }
            return field;
        }

        Spinner spinner(Context context, int index, List<Choice> choices, String selected) {
            Spinner spinner = spinners[index];
            spinner.setVisibility(View.VISIBLE);
            ArrayAdapter<Choice> adapter = new ArrayAdapter<>(
                    context,
                    android.R.layout.simple_spinner_item,
                    choices
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);

            int selectedIndex = 0;
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).value.equals(selected)) {
                    selectedIndex = i;
                    break;
                }
            }
            spinner.setSelection(selectedIndex);
            return spinner;
        }

        Choice selected(int index) {
            Object value = spinners[index].getSelectedItem();
            return value instanceof Choice ? (Choice) value : new Choice("", "");
        }

        void active(String label, boolean value) {
            check.setVisibility(View.VISIBLE);
            check.setText(label);
            check.setChecked(value);
        }
    }

    private RecordDialogs() {}

    public static void transaction(
            Context context,
            FinanceState state,
            FinanceRecord existing,
            TransactionHandler handler
    ) {
        Form form = new Form(context);

        String description = existing == null ? "" : existing.text("description");
        double total = existing == null
                ? 0.0
                : (existing.number("purchaseTotal") > 0
                ? existing.number("purchaseTotal")
                : existing.number("amount"));

        form.field(0, "Descrição", description, false);
        form.field(1, "Valor total", total <= 0 ? "" : String.valueOf(total), true);
        form.field(2, "Data da compra (AAAA-MM-DD)",
                existing == null
                        ? LocalDate.now().toString()
                        : existing.text("purchaseDate", existing.text("date")),
                false);
        form.field(3, "Parcelas", existing == null
                ? "1"
                : String.valueOf(Math.max(1, existing.integer("installmentCount"))), true);
        form.field(4, "Tags", existing == null ? "" : existing.text("tags"), false);
        form.field(5, "Observações", existing == null ? "" : existing.text("notes"), false);

        form.spinner(context, 0, List.of(
                new Choice("Despesa", "expense"),
                new Choice("Receita", "income")
        ), existing == null ? "expense" : existing.text("type", "expense"));

        form.spinner(context, 1, List.of(
                new Choice("Pix", "Pix"),
                new Choice("Débito", "Débito"),
                new Choice("Dinheiro", "Dinheiro"),
                new Choice("Cartão de crédito", "Cartão de crédito"),
                new Choice("Vale-refeição", "Vale-refeição"),
                new Choice("Vale-alimentação", "Vale-alimentação"),
                new Choice("Vale-combustível", "Vale-combustível")
        ), existing == null ? "Pix" : existing.text("payment", "Pix"));

        List<Choice> categories = categoryChoices(state);
        form.spinner(context, 2, categories,
                existing == null ? "outros" : existing.text("category", "outros"));
        form.spinner(context, 3, accountChoices(state),
                existing == null ? "" : existing.text("accountId"));
        form.spinner(context, 4, cardChoices(state),
                existing == null ? "" : existing.text("cardId"));

        new MaterialAlertDialogBuilder(context)
                .setTitle(existing == null ? "Novo lançamento" : "Editar lançamento")
                .setView(form.root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    TransactionInput input = new TransactionInput();
                    input.description = form.fields[0].getText().toString().trim();
                    input.total = UiFormat.parseMoney(form.fields[1].getText().toString());
                    input.purchaseDate = form.fields[2].getText().toString().trim();
                    input.installments = Math.max(
                            1,
                            (int) UiFormat.parseMoney(form.fields[3].getText().toString())
                    );
                    input.tags = form.fields[4].getText().toString().trim();
                    input.notes = form.fields[5].getText().toString().trim();
                    input.type = form.selected(0).value;
                    input.payment = form.selected(1).value;
                    input.category = form.selected(2).value;
                    input.accountId = form.selected(3).value;
                    input.cardId = form.selected(4).value;

                    if (input.description.isBlank() || input.total <= 0.0) return;
                    try {
                        LocalDate.parse(input.purchaseDate);
                    } catch (Exception error) {
                        return;
                    }
                    handler.onSave(input);
                })
                .show();
    }

    public static void record(
            Context context,
            FinanceState state,
            FinanceSection section,
            FinanceRecord existing,
            RecordHandler handler
    ) {
        Form form = new Form(context);
        Map<String, Object> current = existing == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(existing.fields());

        switch (section) {
            case RECURRING:
                configureRecurring(context, state, form, existing);
                break;
            case INCOME_SOURCES:
                configureIncome(context, state, form, existing);
                break;
            case BILLS:
                configureBill(context, state, form, existing);
                break;
            case BUDGETS:
                configureBudget(context, state, form, existing);
                break;
            case GOALS:
                configureGoal(form, existing);
                break;
            case ACCOUNTS:
                configureAccount(context, form, existing);
                break;
            case CARDS:
                configureCard(context, state, form, existing);
                break;
            case TRANSFERS:
                configureTransfer(context, state, form, existing);
                break;
            case CATEGORIES:
                configureCategory(context, form, existing);
                break;
            case SHOPPING_LISTS:
                configureShoppingList(form, existing);
                break;
            default:
                throw new IllegalArgumentException("Seção não editável por este formulário: " + section);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle((existing == null ? "Novo " : "Editar ") + sectionTitle(section))
                .setView(form.root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    Map<String, Object> values = new LinkedHashMap<>(current);
                    readSection(section, form, values);

                    String id = existing == null
                            ? idPrefix(section) + UUID.randomUUID()
                            : existing.id();
                    values.put("id", id);
                    values.put("updatedAt", Instant.now().toString());
                    if (existing == null) values.put("createdAt", Instant.now().toString());
                    handler.onSave(new FinanceRecord(id, values));
                })
                .show();
    }

    public static void shoppingItem(
            Context context,
            FinanceRecord existing,
            int order,
            RecordHandler handler
    ) {
        Form form = new Form(context);
        form.field(0, "Produto", existing == null ? "" : existing.text("product"), false);
        form.field(1, "Quantidade", existing == null ? "1" : String.valueOf(existing.number("qty")), true);
        form.field(2, "Valor unitário", existing == null ? "0" : String.valueOf(existing.number("unitPrice")), true);

        new MaterialAlertDialogBuilder(context)
                .setTitle(existing == null ? "Adicionar produto" : "Editar produto")
                .setView(form.root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String product = form.fields[0].getText().toString().trim();
                    double qty = UiFormat.parseMoney(form.fields[1].getText().toString());
                    double price = UiFormat.parseMoney(form.fields[2].getText().toString());
                    if (product.isBlank() || qty <= 0.0 || price < 0.0) return;

                    String id = existing == null
                            ? "shopitem_" + UUID.randomUUID()
                            : existing.id();
                    Map<String, Object> values = existing == null
                            ? new LinkedHashMap<>()
                            : new LinkedHashMap<>(existing.fields());
                    values.put("id", id);
                    values.put("product", product);
                    values.put("qty", qty);
                    values.put("unitPrice", price);
                    values.put("order", existing == null ? order : existing.integer("order"));
                    values.put("updatedAt", Instant.now().toString());
                    handler.onSave(new FinanceRecord(id, values));
                })
                .show();
    }

    public static void prompt(
            Context context,
            String title,
            String hint,
            String initial,
            TextHandler handler
    ) {
        Form form = new Form(context);
        form.field(0, hint, initial, false);
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(form.root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String value = form.fields[0].getText().toString().trim();
                    if (!value.isBlank()) handler.onSave(value);
                })
                .show();
    }

    private static void configureRecurring(
            Context context,
            FinanceState state,
            Form form,
            FinanceRecord record
    ) {
        form.field(0, "Descrição", text(record, "description"), false);
        form.field(1, "Valor", numberText(record, "amount"), true);
        form.field(2, "Dia", record == null ? "10" : String.valueOf(Math.max(1, record.integer("day"))), true);
        form.field(3, "Parcelas", record == null ? "1" : String.valueOf(Math.max(1, record.integer("installmentCount"))), true);
        form.spinner(context, 0, List.of(
                new Choice("Despesa fixa", "fixed"),
                new Choice("Assinatura", "subscription")
        ), record == null ? "fixed" : record.text("kind", "fixed"));
        form.spinner(context, 1, paymentChoices(), record == null ? "Pix" : record.text("payment", "Pix"));
        form.spinner(context, 2, categoryChoices(state), record == null ? "outros" : record.text("category", "outros"));
        form.spinner(context, 3, cardChoices(state), record == null ? "" : record.text("cardId"));
        form.active("Ativo", record == null || record.bool("active"));
    }

    private static void configureIncome(
            Context context,
            FinanceState state,
            Form form,
            FinanceRecord record
    ) {
        form.field(0, "Descrição", text(record, "description"), false);
        form.field(1, "Valor", numberText(record, "amount"), true);
        form.field(2, "Dia de recebimento", record == null ? "5" : String.valueOf(Math.max(1, record.integer("day"))), true);
        form.spinner(context, 0, List.of(
                new Choice("Salário", "salary"),
                new Choice("Renda extra", "extra"),
                new Choice("Freelance", "freelance"),
                new Choice("Aluguel recebido", "rent"),
                new Choice("Comissão", "commission"),
                new Choice("Outra renda", "other")
        ), record == null ? "salary" : record.text("kind", "salary"));
        form.spinner(context, 1, accountChoices(state), record == null ? "" : record.text("accountId"));
        form.active("Ativa", record == null || record.bool("active"));
    }

    private static void configureBill(
            Context context,
            FinanceState state,
            Form form,
            FinanceRecord record
    ) {
        form.field(0, "Descrição", text(record, "description"), false);
        form.field(1, "Valor", numberText(record, "amount"), true);
        form.field(2, "Vencimento (AAAA-MM-DD)", record == null ? LocalDate.now().toString() : record.text("dueDate"), false);
        form.field(3, "Observações", text(record, "notes"), false);
        form.spinner(context, 0, List.of(
                new Choice("A pagar", "expense"),
                new Choice("A receber", "income")
        ), record == null ? "expense" : record.text("type", "expense"));
        form.spinner(context, 1, categoryChoices(state), record == null ? "outros" : record.text("category", "outros"));
        form.spinner(context, 2, accountChoices(state), record == null ? "" : record.text("accountId"));
    }

    private static void configureBudget(
            Context context,
            FinanceState state,
            Form form,
            FinanceRecord record
    ) {
        form.field(0, "Limite mensal", numberText(record, "amount"), true);
        form.spinner(context, 0, categoryChoices(state), record == null ? "outros" : record.text("category", "outros"));
    }

    private static void configureGoal(Form form, FinanceRecord record) {
        form.field(0, "Nome da meta", text(record, "name"), false);
        form.field(1, "Objetivo", numberText(record, "target"), true);
        form.field(2, "Valor atual", record == null ? "0" : numberText(record, "current"), true);
        form.field(3, "Prazo (AAAA-MM-DD)", text(record, "deadline"), false);
        form.field(4, "Ícone", record == null ? "🎯" : record.text("icon", "🎯"), false);
    }

    private static void configureAccount(
            Context context,
            Form form,
            FinanceRecord record
    ) {
        form.field(0, "Nome", text(record, "name"), false);
        form.field(1, "Saldo inicial", record == null ? "0" : numberText(record, "openingBalance"), true);
        form.field(2, "Ícone", record == null ? "🏦" : record.text("icon", "🏦"), false);
        form.field(3, "Cor (#RRGGBB)", record == null ? "#7c5cff" : record.text("color", "#7c5cff"), false);
        form.spinner(context, 0, List.of(
                new Choice("Conta corrente", "Conta corrente"),
                new Choice("Conta poupança", "Conta poupança"),
                new Choice("Dinheiro", "Dinheiro"),
                new Choice("Carteira digital", "Carteira digital"),
                new Choice("Investimento", "Investimento")
        ), record == null ? "Conta corrente" : record.text("type", "Conta corrente"));
    }

    private static void configureCard(
            Context context,
            FinanceState state,
            Form form,
            FinanceRecord record
    ) {
        form.field(0, "Nome", text(record, "name"), false);
        form.field(1, "Bandeira/Emissor", record == null ? "Visa" : record.text("brand", "Visa"), false);
        form.field(2, "Limite/Saldo", numberText(record, "limit"), true);
        form.field(3, "Dia do fechamento", record == null ? "3" : String.valueOf(Math.max(0, record.integer("closingDay"))), true);
        form.field(4, "Dia do vencimento", record == null ? "10" : String.valueOf(Math.max(0, record.integer("dueDay"))), true);
        form.field(5, "Cor (#RRGGBB)", record == null ? "#141b34" : record.text("color", "#141b34"), false);
        form.spinner(context, 0, List.of(
                new Choice("Cartão de crédito", "credit"),
                new Choice("Vale-refeição", "meal"),
                new Choice("Vale-alimentação", "food"),
                new Choice("Vale-combustível", "fuel"),
                new Choice("Outro benefício", "benefit")
        ), record == null ? "credit" : record.text("cardType", "credit"));
        form.spinner(context, 1, accountChoices(state), record == null ? "" : record.text("accountId"));
    }

    private static void configureTransfer(
            Context context,
            FinanceState state,
            Form form,
            FinanceRecord record
    ) {
        form.field(0, "Valor", numberText(record, "amount"), true);
        form.field(1, "Data (AAAA-MM-DD)", record == null ? LocalDate.now().toString() : record.text("date"), false);
        form.field(2, "Observações", text(record, "notes"), false);
        form.spinner(context, 0, accountChoices(state), record == null ? "" : record.text("fromAccountId"));
        form.spinner(context, 1, accountChoices(state), record == null ? "" : record.text("toAccountId"));
    }

    private static void configureCategory(
            Context context,
            Form form,
            FinanceRecord record
    ) {
        form.field(0, "Nome", text(record, "name"), false);
        form.field(1, "Ícone", record == null ? "📦" : record.text("icon", "📦"), false);
        form.field(2, "Cor (#RRGGBB)", record == null ? "#8d99ae" : record.text("color", "#8d99ae"), false);
        form.spinner(context, 0, List.of(
                new Choice("Essencial", "essential"),
                new Choice("Desejos", "wants"),
                new Choice("Futuro", "future"),
                new Choice("Receita", "income")
        ), record == null ? "essential" : record.text("group", "essential"));
    }

    private static void configureShoppingList(Form form, FinanceRecord record) {
        form.field(0, "Nome da lista", text(record, "name"), false);
        form.field(1, "Mercado/local", text(record, "store"), false);
    }

    private static void readSection(
            FinanceSection section,
            Form form,
            Map<String, Object> out
    ) {
        switch (section) {
            case RECURRING:
                out.put("description", value(form, 0));
                out.put("amount", money(form, 1));
                out.put("day", integer(form, 2, 1));
                out.put("installmentCount", integer(form, 3, 1));
                out.put("kind", form.selected(0).value);
                out.put("payment", form.selected(1).value);
                out.put("category", form.selected(2).value);
                out.put("cardId", form.selected(3).value);
                out.put("active", form.check.isChecked());
                break;
            case INCOME_SOURCES:
                out.put("description", value(form, 0));
                out.put("amount", money(form, 1));
                out.put("day", integer(form, 2, 1));
                out.put("kind", form.selected(0).value);
                out.put("accountId", form.selected(1).value);
                out.put("active", form.check.isChecked());
                break;
            case BILLS:
                out.put("description", value(form, 0));
                out.put("amount", money(form, 1));
                out.put("dueDate", value(form, 2));
                out.put("notes", value(form, 3));
                out.put("type", form.selected(0).value);
                out.put("category", form.selected(1).value);
                out.put("accountId", form.selected(2).value);
                break;
            case BUDGETS:
                out.put("amount", money(form, 0));
                out.put("category", form.selected(0).value);
                break;
            case GOALS:
                out.put("name", value(form, 0));
                out.put("target", money(form, 1));
                out.put("current", money(form, 2));
                out.put("deadline", value(form, 3));
                out.put("icon", value(form, 4));
                break;
            case ACCOUNTS:
                out.put("name", value(form, 0));
                out.put("openingBalance", money(form, 1));
                out.put("icon", value(form, 2));
                out.put("color", value(form, 3));
                out.put("type", form.selected(0).value);
                break;
            case CARDS:
                out.put("name", value(form, 0));
                out.put("brand", value(form, 1));
                out.put("limit", money(form, 2));
                out.put("closingDay", integer(form, 3, 0));
                out.put("dueDay", integer(form, 4, 0));
                out.put("color", value(form, 5));
                out.put("cardType", form.selected(0).value);
                out.put("accountId", "credit".equals(form.selected(0).value)
                        ? form.selected(1).value
                        : "");
                break;
            case TRANSFERS:
                out.put("amount", money(form, 0));
                out.put("date", value(form, 1));
                out.put("notes", value(form, 2));
                out.put("fromAccountId", form.selected(0).value);
                out.put("toAccountId", form.selected(1).value);
                break;
            case CATEGORIES:
                out.put("name", value(form, 0));
                out.put("icon", value(form, 1));
                out.put("color", value(form, 2));
                out.put("group", form.selected(0).value);
                break;
            case SHOPPING_LISTS:
                out.put("name", value(form, 0));
                out.put("store", value(form, 1));
                if (!out.containsKey("items")) out.put("items", new ArrayList<>());
                break;
            default:
                break;
        }
    }

    private static List<Choice> paymentChoices() {
        return List.of(
                new Choice("Pix", "Pix"),
                new Choice("Débito", "Débito"),
                new Choice("Dinheiro", "Dinheiro"),
                new Choice("Cartão de crédito", "Cartão de crédito"),
                new Choice("Vale-refeição", "Vale-refeição"),
                new Choice("Vale-alimentação", "Vale-alimentação"),
                new Choice("Vale-combustível", "Vale-combustível")
        );
    }

    private static List<Choice> categoryChoices(FinanceState state) {
        List<Choice> result = new ArrayList<>();
        for (FinanceRecord category : state.records(FinanceSection.CATEGORIES)) {
            result.add(new Choice(
                    category.text("icon") + " " + category.text("name"),
                    category.id()
            ));
        }
        if (result.isEmpty()) {
            result.add(new Choice("🏠 Moradia", "moradia"));
            result.add(new Choice("🍽️ Alimentação", "alimentacao"));
            result.add(new Choice("🚗 Transporte", "transporte"));
            result.add(new Choice("❤️ Saúde", "saude"));
            result.add(new Choice("📚 Educação", "educacao"));
            result.add(new Choice("🎮 Lazer", "lazer"));
            result.add(new Choice("📺 Assinaturas", "assinaturas"));
            result.add(new Choice("🛍️ Compras", "compras"));
            result.add(new Choice("📈 Investimentos", "investimentos"));
            result.add(new Choice("💰 Salário", "salario"));
            result.add(new Choice("📦 Outros", "outros"));
        }
        return result;
    }

    private static List<Choice> accountChoices(FinanceState state) {
        List<Choice> result = new ArrayList<>();
        result.add(new Choice("Sem conta", ""));
        for (FinanceRecord account : state.records(FinanceSection.ACCOUNTS)) {
            result.add(new Choice(account.text("name", "Conta"), account.id()));
        }
        return result;
    }

    private static List<Choice> cardChoices(FinanceState state) {
        List<Choice> result = new ArrayList<>();
        result.add(new Choice("Sem cartão", ""));
        for (FinanceRecord card : state.records(FinanceSection.CARDS)) {
            result.add(new Choice(card.text("name", "Cartão"), card.id()));
        }
        return result;
    }

    private static String sectionTitle(FinanceSection section) {
        switch (section) {
            case RECURRING: return "custo fixo";
            case INCOME_SOURCES: return "renda recorrente";
            case BILLS: return "conta prevista";
            case BUDGETS: return "orçamento";
            case GOALS: return "meta";
            case ACCOUNTS: return "conta/carteira";
            case CARDS: return "cartão/benefício";
            case TRANSFERS: return "transferência";
            case CATEGORIES: return "categoria";
            case SHOPPING_LISTS: return "lista de compras";
            default: return "registro";
        }
    }

    private static String idPrefix(FinanceSection section) {
        switch (section) {
            case RECURRING: return "rec_";
            case INCOME_SOURCES: return "inc_";
            case BILLS: return "bill_";
            case BUDGETS: return "bud_";
            case GOALS: return "goal_";
            case ACCOUNTS: return "acc_";
            case CARDS: return "card_";
            case TRANSFERS: return "trf_";
            case CATEGORIES: return "cat_";
            case SHOPPING_LISTS: return "shop_";
            default: return "rec_";
        }
    }

    private static String text(FinanceRecord record, String key) {
        return record == null ? "" : record.text(key);
    }

    private static String numberText(FinanceRecord record, String key) {
        return record == null ? "" : String.valueOf(record.number(key));
    }

    private static String value(Form form, int index) {
        return form.fields[index].getText().toString().trim();
    }

    private static double money(Form form, int index) {
        return UiFormat.parseMoney(value(form, index));
    }

    private static int integer(Form form, int index, int fallback) {
        try {
            String value = value(form, index);
            return value.isBlank() ? fallback : Integer.parseInt(value.replace(",", ".").split("\\.")[0]);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
