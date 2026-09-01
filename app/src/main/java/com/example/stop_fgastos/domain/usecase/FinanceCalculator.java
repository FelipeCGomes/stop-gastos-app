package com.example.stop_fgastos.domain.usecase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class FinanceCalculator {
    private FinanceCalculator() {}

    public static List<Double> splitInstallments(double total, int count) {
        int safeCount = Math.max(1, Math.min(60, count));
        long cents = BigDecimal.valueOf(Math.max(0.0, total))
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        long base = cents / safeCount;
        long remainder = cents % safeCount;

        List<Double> result = new ArrayList<>(safeCount);
        for (int i = 0; i < safeCount; i++) {
            long part = base + (i < remainder ? 1 : 0);
            result.add(BigDecimal.valueOf(part, 2).doubleValue());
        }
        return result;
    }

    public static YearMonth invoiceMonth(LocalDate purchase, int closingDay) {
        int close = Math.max(1, Math.min(31, closingDay));
        YearMonth month = YearMonth.from(purchase);
        return purchase.getDayOfMonth() <= close ? month : month.plusMonths(1);
    }

    public static LocalDate safeDate(YearMonth month, int day) {
        return month.atDay(Math.max(1, Math.min(month.lengthOfMonth(), day)));
    }

    public static double roundMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
