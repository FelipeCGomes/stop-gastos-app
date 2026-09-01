package com.example.stop_fgastos.domain.model;

public final class MonthlySummary {
    private final double income;
    private final double expense;

    public MonthlySummary(double income, double expense) {
        this.income = income;
        this.expense = expense;
    }

    public double income() { return income; }
    public double expense() { return expense; }
    public double balance() { return income - expense; }
    public double savingsRate() { return income <= 0.0 ? 0.0 : ((income - expense) / income) * 100.0; }
}
