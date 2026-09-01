package com.example.stop_fgastos.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class FinanceCalculator {

    private FinanceCalculator() {
    }

    public static List<Double> splitInstallments(double total, int count) {
        int safeCount = Math.max(1, Math.min(60, count));
        long cents = Math.max(0L, Math.round(total * 100.0d));
        long base = cents / safeCount;
        long remainder = cents - (base * safeCount);

        List<Double> values = new ArrayList<>(safeCount);
        for (int i = 0; i < safeCount; i++) {
            long installmentCents = base + (i < remainder ? 1L : 0L);
            values.add(installmentCents / 100.0d);
        }
        return values;
    }

    public static double sum(Collection<Double> values) {
        double total = 0.0d;
        if (values == null) {
            return total;
        }
        for (Double value : values) {
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    public static double balance(double income, double expense) {
        return income - expense;
    }
}