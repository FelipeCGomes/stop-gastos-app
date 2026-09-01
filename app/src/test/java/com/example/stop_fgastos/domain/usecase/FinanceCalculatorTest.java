package com.example.stop_fgastos.domain.usecase;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class FinanceCalculatorTest {

    @Test
    public void splitInstallmentsPreservesTotal() {
        List<Double> values = FinanceCalculator.splitInstallments(100.00, 3);
        assertEquals(3, values.size());
        assertEquals(100.00, values.stream().mapToDouble(Double::doubleValue).sum(), 0.001);
        assertEquals(33.34, values.get(0), 0.001);
        assertEquals(33.33, values.get(1), 0.001);
        assertEquals(33.33, values.get(2), 0.001);
    }

    @Test
    public void purchaseAfterClosingMovesToNextInvoice() {
        YearMonth invoice = FinanceCalculator.invoiceMonth(
                LocalDate.of(2026, 9, 15),
                10
        );
        assertEquals(YearMonth.of(2026, 10), invoice);
    }

    @Test
    public void safeDateClampsDayToMonth() {
        assertEquals(
                LocalDate.of(2026, 2, 28),
                FinanceCalculator.safeDate(YearMonth.of(2026, 2), 31)
        );
    }
}
