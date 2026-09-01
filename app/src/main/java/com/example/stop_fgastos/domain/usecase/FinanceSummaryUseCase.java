package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.model.FinanceState;
import com.example.stop_fgastos.domain.model.MonthlySummary;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FinanceSummaryUseCase {
    public MonthlySummary monthly(FinanceState state, YearMonth month) {
        String key = month.toString();
        double income = 0.0;
        double expense = 0.0;

        for (FinanceRecord tx : state.records(FinanceSection.TRANSACTIONS)) {
            if (!tx.text("date").startsWith(key)) continue;
            if ("income".equals(tx.text("type"))) income += tx.number("amount");
            if ("expense".equals(tx.text("type"))) expense += tx.number("amount");
        }

        return new MonthlySummary(
                FinanceCalculator.roundMoney(income),
                FinanceCalculator.roundMoney(expense)
        );
    }

    public double accountBalance(FinanceState state, String accountId) {
        FinanceRecord account = state.find(FinanceSection.ACCOUNTS, accountId).orElse(null);
        if (account == null) return 0.0;

        double balance = account.number("openingBalance");
        for (FinanceRecord tx : state.records(FinanceSection.TRANSACTIONS)) {
            if (!accountId.equals(tx.text("accountId"))) continue;
            if ("income".equals(tx.text("type"))) balance += tx.number("amount");
            if ("expense".equals(tx.text("type"))) balance -= tx.number("amount");
        }

        for (FinanceRecord transfer : state.records(FinanceSection.TRANSFERS)) {
            if (accountId.equals(transfer.text("fromAccountId"))) balance -= transfer.number("amount");
            if (accountId.equals(transfer.text("toAccountId"))) balance += transfer.number("amount");
        }

        return FinanceCalculator.roundMoney(balance);
    }

    public Map<String, Double> expenseByCategory(FinanceState state, YearMonth month) {
        String key = month.toString();
        Map<String, Double> result = new LinkedHashMap<>();
        for (FinanceRecord tx : state.records(FinanceSection.TRANSACTIONS)) {
            if (!"expense".equals(tx.text("type")) || !tx.text("date").startsWith(key)) continue;
            String category = tx.text("category", "outros");
            result.put(category, result.getOrDefault(category, 0.0) + tx.number("amount"));
        }
        return result;
    }
}
