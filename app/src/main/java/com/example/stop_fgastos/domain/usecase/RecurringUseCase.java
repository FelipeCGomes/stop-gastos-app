package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.model.FinanceState;
import com.example.stop_fgastos.domain.model.TransactionInput;
import com.example.stop_fgastos.domain.repository.FinanceRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecurringUseCase {
    private final FinanceRepository repository;
    private final SaveTransactionUseCase transactionUseCase;

    public RecurringUseCase(
            FinanceRepository repository,
            SaveTransactionUseCase transactionUseCase
    ) {
        this.repository = repository;
        this.transactionUseCase = transactionUseCase;
    }

    public void saveRecurringExpense(
            FinanceState state,
            FinanceRecord existing,
            Map<String, Object> values,
            ResultCallback<Void> callback
    ) {
        String id = existing == null ? "rec_" + UUID.randomUUID() : existing.id();
        Map<String, Object> map = new LinkedHashMap<>();
        if (existing != null) map.putAll(existing.fields());
        map.putAll(values);
        map.put("id", id);
        map.put("updatedAt", Instant.now().toString());

        FinanceRecord source = new FinanceRecord(id, map);
        if (!source.bool("active")) {
            repository.upsert(FinanceSection.RECURRING, source, callback);
            return;
        }

        YearMonth month = YearMonth.now();
        int day = Math.max(1, Math.min(month.lengthOfMonth(), source.integer("day")));

        TransactionInput input = new TransactionInput();
        input.type = "expense";
        input.description = source.text("description");
        input.total = source.number("amount");
        input.purchaseDate = month.atDay(day).toString();
        input.category = source.text("category", "outros");
        input.payment = source.text("payment", "Pix");
        input.cardId = source.text("cardId");
        input.installments = Math.max(1, source.integer("installmentCount"));
        input.sourceRecurringId = id;
        input.sourceType = "recurringExpense";

        List<FinanceRecord> plan = transactionUseCase.buildPlan(state, input, "");
        repository.replaceSourceTransactions(
                FinanceSection.RECURRING,
                source,
                month.toString(),
                plan,
                callback
        );
    }

    public void saveIncomeSource(
            FinanceState state,
            FinanceRecord existing,
            Map<String, Object> values,
            ResultCallback<Void> callback
    ) {
        String id = existing == null ? "inc_" + UUID.randomUUID() : existing.id();
        Map<String, Object> map = new LinkedHashMap<>();
        if (existing != null) map.putAll(existing.fields());
        map.putAll(values);
        map.put("id", id);
        map.put("updatedAt", Instant.now().toString());
        FinanceRecord source = new FinanceRecord(id, map);

        if (!source.bool("active")) {
            repository.upsert(FinanceSection.INCOME_SOURCES, source, callback);
            return;
        }

        YearMonth month = YearMonth.now();
        int day = Math.max(1, Math.min(month.lengthOfMonth(), source.integer("day")));
        Map<String, Object> tx = new LinkedHashMap<>();
        String txId = "tx_" + UUID.randomUUID();
        String date = month.atDay(day).toString();
        tx.put("id", txId);
        tx.put("type", "income");
        tx.put("description", source.text("description"));
        tx.put("amount", source.number("amount"));
        tx.put("date", date);
        tx.put("purchaseDate", date);
        tx.put("category", "salary".equals(source.text("kind")) ? "salario" : "outros");
        tx.put("payment", "Renda recorrente");
        tx.put("accountId", source.text("accountId"));
        tx.put("purchaseTotal", source.number("amount"));
        tx.put("installmentNo", 1);
        tx.put("installmentCount", 1);
        tx.put("sourceRecurringId", id);
        tx.put("sourceType", "incomeSource");
        tx.put("createdAt", Instant.now().toString());
        tx.put("updatedAt", Instant.now().toString());

        repository.replaceSourceTransactions(
                FinanceSection.INCOME_SOURCES,
                source,
                month.toString(),
                List.of(new FinanceRecord(txId, tx)),
                callback
        );
    }

    public void ensureMonth(
            FinanceState state,
            YearMonth month,
            ResultCallback<Void> callback
    ) {
        List<FinanceRecord> pending = new ArrayList<>();
        String monthKey = month.toString();

        for (FinanceRecord source : state.records(FinanceSection.RECURRING)) {
            if (!source.bool("active")) continue;

            boolean already = state.records(FinanceSection.TRANSACTIONS).stream()
                    .anyMatch(tx -> source.id().equals(tx.text("sourceRecurringId"))
                            && tx.text("date").startsWith(monthKey));
            if (already) continue;

            int day = Math.max(1, Math.min(month.lengthOfMonth(), source.integer("day")));
            TransactionInput input = new TransactionInput();
            input.type = "expense";
            input.description = source.text("description");
            input.total = source.number("amount");
            input.purchaseDate = month.atDay(day).toString();
            input.category = source.text("category", "outros");
            input.payment = source.text("payment", "Pix");
            input.cardId = source.text("cardId");
            input.installments = Math.max(1, source.integer("installmentCount"));
            input.sourceRecurringId = source.id();
            input.sourceType = "recurringExpense";
            pending.addAll(transactionUseCase.buildPlan(state, input, ""));
        }

        for (FinanceRecord source : state.records(FinanceSection.INCOME_SOURCES)) {
            if (!source.bool("active")) continue;
            boolean already = state.records(FinanceSection.TRANSACTIONS).stream()
                    .anyMatch(tx -> source.id().equals(tx.text("sourceRecurringId"))
                            && tx.text("date").startsWith(monthKey));
            if (already) continue;

            int day = Math.max(1, Math.min(month.lengthOfMonth(), source.integer("day")));
            String id = "tx_" + UUID.randomUUID();
            Map<String, Object> tx = new LinkedHashMap<>();
            tx.put("id", id);
            tx.put("type", "income");
            tx.put("description", source.text("description"));
            tx.put("amount", source.number("amount"));
            tx.put("date", month.atDay(day).toString());
            tx.put("purchaseDate", month.atDay(day).toString());
            tx.put("category", "salary".equals(source.text("kind")) ? "salario" : "outros");
            tx.put("payment", "Renda recorrente");
            tx.put("accountId", source.text("accountId"));
            tx.put("purchaseTotal", source.number("amount"));
            tx.put("installmentNo", 1);
            tx.put("installmentCount", 1);
            tx.put("sourceRecurringId", source.id());
            tx.put("sourceType", "incomeSource");
            tx.put("createdAt", Instant.now().toString());
            tx.put("updatedAt", Instant.now().toString());
            pending.add(new FinanceRecord(id, tx));
        }

        if (pending.isEmpty()) {
            callback.onSuccess(null);
        } else {
            repository.upsertAll(FinanceSection.TRANSACTIONS, pending, callback);
        }
    }
}
