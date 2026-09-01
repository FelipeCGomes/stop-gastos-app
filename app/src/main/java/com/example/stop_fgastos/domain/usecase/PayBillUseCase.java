package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.repository.FinanceRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PayBillUseCase {
    private final FinanceRepository repository;

    public PayBillUseCase(FinanceRepository repository) {
        this.repository = repository;
    }

    public void execute(FinanceRecord bill, ResultCallback<Void> callback) {
        if (bill.bool("paid")) {
            callback.onSuccess(null);
            return;
        }

        String date = LocalDate.now().toString();
        String now = Instant.now().toString();
        String transactionId = "tx_" + UUID.randomUUID();

        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("id", transactionId);
        tx.put("type", bill.text("type", "expense"));
        tx.put("description", bill.text("description"));
        tx.put("amount", bill.number("amount"));
        tx.put("date", date);
        tx.put("purchaseDate", date);
        tx.put("category", bill.text("category", "outros"));
        tx.put("payment", "Conta paga");
        tx.put("accountId", bill.text("accountId"));
        tx.put("notes", bill.text("notes"));
        tx.put("purchaseTotal", bill.number("amount"));
        tx.put("installmentNo", 1);
        tx.put("installmentCount", 1);
        tx.put("billId", bill.id());
        tx.put("createdAt", now);
        tx.put("updatedAt", now);

        Map<String, Object> paidFields = new LinkedHashMap<>(bill.fields());
        paidFields.put("paid", true);
        paidFields.put("paidAt", date);
        paidFields.put("transactionId", transactionId);
        paidFields.put("updatedAt", now);

        repository.saveBillWithTransaction(
                new FinanceRecord(bill.id(), paidFields),
                new FinanceRecord(transactionId, tx),
                callback
        );
    }
}
