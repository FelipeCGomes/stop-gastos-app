package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.model.FinanceState;
import com.example.stop_fgastos.domain.model.TransactionInput;
import com.example.stop_fgastos.domain.repository.FinanceRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SaveTransactionUseCase {
    private final FinanceRepository repository;

    public SaveTransactionUseCase(FinanceRepository repository) {
        this.repository = repository;
    }

    public void create(
            FinanceState state,
            TransactionInput input,
            ResultCallback<Void> callback
    ) {
        repository.upsertAll(
                FinanceSection.TRANSACTIONS,
                buildPlan(state, input, ""),
                callback
        );
    }

    public void update(
            FinanceState state,
            FinanceRecord existing,
            TransactionInput input,
            ResultCallback<Void> callback
    ) {
        input.sourceRecurringId = existing.text("sourceRecurringId");
        input.sourceType = existing.text("sourceType");
        input.billId = existing.text("billId");

        List<FinanceRecord> replacement = buildPlan(
                state,
                input,
                existing.text("createdAt")
        );

        repository.replaceTransactionPlan(existing, replacement, callback);
    }

    public void delete(FinanceRecord existing, ResultCallback<Void> callback) {
        repository.replaceTransactionPlan(existing, List.of(), callback);
    }

    public List<FinanceRecord> buildPlan(
            FinanceState state,
            TransactionInput input,
            String originalCreatedAt
    ) {
        if (input.description == null || input.description.isBlank()) {
            throw new IllegalArgumentException("Informe a descrição.");
        }
        if (input.total <= 0.0) {
            throw new IllegalArgumentException("Informe um valor maior que zero.");
        }

        LocalDate purchase = LocalDate.parse(input.purchaseDate);
        FinanceRecord card = state.find(FinanceSection.CARDS, input.cardId).orElse(null);

        boolean credit = "expense".equals(input.type)
                && "Cartão de crédito".equals(input.payment)
                && card != null
                && "credit".equals(card.text("cardType", "credit"));

        int count = credit ? Math.max(1, Math.min(60, input.installments)) : 1;
        List<Double> amounts = FinanceCalculator.splitInstallments(input.total, count);
        YearMonth firstInvoice = credit
                ? FinanceCalculator.invoiceMonth(purchase, Math.max(1, card.integer("closingDay")))
                : null;

        String groupId = count > 1 ? "inst_" + UUID.randomUUID() : "";
        String now = Instant.now().toString();
        String createdAt = originalCreatedAt == null || originalCreatedAt.isBlank()
                ? now
                : originalCreatedAt;

        List<FinanceRecord> records = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            int installmentNo = index + 1;
            YearMonth invoice = credit ? firstInvoice.plusMonths(index) : null;
            String invoiceMonth = invoice == null ? "" : invoice.toString();

            String recordDate;
            if (credit && count > 1) {
                int dueDay = Math.max(1, card.integer("dueDay"));
                recordDate = FinanceCalculator.safeDate(invoice, dueDay).toString();
            } else {
                recordDate = input.purchaseDate;
            }

            Map<String, Object> map = new LinkedHashMap<>();
            String id = "tx_" + UUID.randomUUID();
            map.put("id", id);
            map.put("type", input.type);
            map.put("description", input.description.trim());
            map.put("amount", amounts.get(index));
            map.put("date", recordDate);
            map.put("purchaseDate", input.purchaseDate);
            map.put("category", input.category);
            map.put("payment", input.payment);
            map.put("accountId", input.accountId);
            map.put("cardId", input.cardId);
            map.put("invoiceMonth", invoiceMonth);
            map.put("tags", input.tags == null ? "" : input.tags.trim());
            map.put("notes", input.notes == null ? "" : input.notes.trim());
            map.put("purchaseTotal", FinanceCalculator.roundMoney(input.total));
            map.put("installmentGroup", groupId);
            map.put("installmentNo", installmentNo);
            map.put("installmentCount", count);
            map.put("installmentAmount", amounts.get(index));
            map.put("sourceRecurringId", input.sourceRecurringId);
            map.put("sourceType", input.sourceType);
            map.put("billId", input.billId);

            if ("expense".equals(input.type)
                    && "recurringExpense".equals(input.sourceType)) {
                map.put("paid", false);
                map.put("dueDate", recordDate);
                map.put("originalAmount", amounts.get(index));
            }

            map.put("createdAt", createdAt);
            map.put("updatedAt", now);
            records.add(new FinanceRecord(id, map));
        }

        return records;
    }
}
