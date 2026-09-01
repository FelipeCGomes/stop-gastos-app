package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.repository.FinanceRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TransactionPaymentUseCase {
    private final FinanceRepository repository;

    public TransactionPaymentUseCase(FinanceRepository repository) {
        this.repository = repository;
    }

    public void pay(
            FinanceRecord transaction,
            double paidAmount,
            ResultCallback<Void> callback
    ) {
        if (paidAmount <= 0.0) {
            callback.onError(new IllegalArgumentException(
                    "O valor pago precisa ser maior que zero."
            ));
            return;
        }

        LocalDate paidDate = LocalDate.now();
        LocalDate dueDate = parseDate(
                transaction.text("dueDate", transaction.text("date")),
                paidDate
        );

        double originalAmount = transaction.number("originalAmount") > 0
                ? transaction.number("originalAmount")
                : transaction.number("amount");

        long daysLate = Math.max(0, ChronoUnit.DAYS.between(dueDate, paidDate));
        double difference = roundMoney(paidAmount - originalAmount);
        double additionalAmount = Math.max(0.0, difference);
        double lateFeeAmount = daysLate > 0 ? additionalAmount : 0.0;
        double discountAmount = Math.max(0.0, -difference);
        double lateFeeRate = originalAmount <= 0.0
                ? 0.0
                : roundRate((lateFeeAmount / originalAmount) * 100.0);

        Map<String, Object> fields = new LinkedHashMap<>(transaction.fields());
        fields.put("amount", roundMoney(paidAmount));
        fields.put("originalAmount", roundMoney(originalAmount));
        fields.put("paid", true);
        fields.put("paidAt", paidDate.toString());
        fields.put("paidAmount", roundMoney(paidAmount));
        fields.put("paymentDifference", difference);
        fields.put("additionalAmount", additionalAmount);
        fields.put("lateFeeAmount", lateFeeAmount);
        fields.put("lateFeeRate", lateFeeRate);
        fields.put("discountAmount", discountAmount);
        fields.put("daysLate", daysLate);
        fields.put("dueDate", dueDate.toString());
        fields.put("updatedAt", Instant.now().toString());

        repository.upsert(
                FinanceSection.TRANSACTIONS,
                new FinanceRecord(transaction.id(), fields),
                callback
        );
    }

    public void undo(
            FinanceRecord transaction,
            ResultCallback<Void> callback
    ) {
        double originalAmount = transaction.number("originalAmount") > 0
                ? transaction.number("originalAmount")
                : transaction.number("amount");

        Map<String, Object> fields = new LinkedHashMap<>(transaction.fields());
        fields.put("amount", roundMoney(originalAmount));
        fields.put("paid", false);
        fields.remove("paidAt");
        fields.remove("paidAmount");
        fields.remove("paymentDifference");
        fields.remove("additionalAmount");
        fields.remove("lateFeeAmount");
        fields.remove("lateFeeRate");
        fields.remove("discountAmount");
        fields.remove("daysLate");
        fields.put("updatedAt", Instant.now().toString());

        repository.upsert(
                FinanceSection.TRANSACTIONS,
                new FinanceRecord(transaction.id(), fields),
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
