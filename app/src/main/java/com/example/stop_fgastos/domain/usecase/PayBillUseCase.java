package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.repository.FinanceRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PayBillUseCase {
    private final FinanceRepository repository;

    public PayBillUseCase(FinanceRepository repository) {
        this.repository = repository;
    }

    public void execute(
            FinanceRecord bill,
            double paidAmount,
            LocalDate paidDate,
            ResultCallback<Void> callback
    ) {
        if (bill.bool("paid")) {
            callback.onSuccess(null);
            return;
        }
        if (paidAmount <= 0.0) {
            callback.onError(new IllegalArgumentException("O valor pago precisa ser maior que zero."));
            return;
        }

        LocalDate effectivePaidDate = paidDate == null ? LocalDate.now() : paidDate;
        LocalDate dueDate = parseDate(bill.text("dueDate"), effectivePaidDate);
        long daysLate = Math.max(0, ChronoUnit.DAYS.between(dueDate, effectivePaidDate));

        double originalAmount = bill.number("amount");
        double difference = roundMoney(paidAmount - originalAmount);
        double additionalAmount = Math.max(0.0, difference);
        double discountAmount = Math.max(0.0, -difference);
        double lateFeeAmount = daysLate > 0 ? additionalAmount : 0.0;
        double lateFeeRate = originalAmount <= 0.0
                ? 0.0
                : roundRate((lateFeeAmount / originalAmount) * 100.0);

        String date = effectivePaidDate.toString();
        String now = Instant.now().toString();
        String transactionId = "tx_" + UUID.randomUUID();

        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("id", transactionId);
        tx.put("type", bill.text("type", "expense"));
        tx.put("description", bill.text("description"));
        tx.put("amount", roundMoney(paidAmount));
        tx.put("originalAmount", roundMoney(originalAmount));
        tx.put("paidAmount", roundMoney(paidAmount));
        tx.put("additionalAmount", additionalAmount);
        tx.put("lateFeeAmount", lateFeeAmount);
        tx.put("lateFeeRate", lateFeeRate);
        tx.put("discountAmount", discountAmount);
        tx.put("daysLate", daysLate);
        tx.put("dueDate", dueDate.toString());
        tx.put("date", date);
        tx.put("purchaseDate", date);
        tx.put("category", bill.text("category", "outros"));
        tx.put("payment", "Conta paga");
        tx.put("accountId", bill.text("accountId"));
        tx.put("notes", bill.text("notes"));
        tx.put("purchaseTotal", roundMoney(paidAmount));
        tx.put("installmentNo", 1);
        tx.put("installmentCount", 1);
        tx.put("billId", bill.id());
        tx.put("createdAt", now);
        tx.put("updatedAt", now);

        Map<String, Object> paidFields = new LinkedHashMap<>(bill.fields());
        paidFields.put("paid", true);
        paidFields.put("paidAt", date);
        paidFields.put("paidAmount", roundMoney(paidAmount));
        paidFields.put("paymentDifference", difference);
        paidFields.put("additionalAmount", additionalAmount);
        paidFields.put("lateFeeAmount", lateFeeAmount);
        paidFields.put("lateFeeRate", lateFeeRate);
        paidFields.put("discountAmount", discountAmount);
        paidFields.put("daysLate", daysLate);
        paidFields.put("transactionId", transactionId);
        paidFields.put("updatedAt", now);

        repository.saveBillWithTransaction(
                new FinanceRecord(bill.id(), paidFields),
                new FinanceRecord(transactionId, tx),
                callback
        );
    }

    public void undo(FinanceRecord bill, ResultCallback<Void> callback) {
        if (!bill.bool("paid")) {
            callback.onSuccess(null);
            return;
        }

        String transactionId = bill.text("transactionId");
        Map<String, Object> fields = new LinkedHashMap<>(bill.fields());

        fields.put("paid", false);
        fields.remove("paidAt");
        fields.remove("paidAmount");
        fields.remove("paymentDifference");
        fields.remove("additionalAmount");
        fields.remove("lateFeeAmount");
        fields.remove("lateFeeRate");
        fields.remove("discountAmount");
        fields.remove("daysLate");
        fields.remove("transactionId");
        fields.put("updatedAt", Instant.now().toString());

        repository.clearBillPayment(
                new FinanceRecord(bill.id(), fields),
                transactionId,
                callback
        );
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double roundRate(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
