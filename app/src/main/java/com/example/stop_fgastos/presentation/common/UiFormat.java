package com.example.stop_fgastos.presentation.common;

import java.text.NumberFormat;
import java.time.YearMonth;
import java.util.Locale;

public final class UiFormat {
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final String[] MONTHS = {
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    };

    private UiFormat() {}

    public static String money(double value) {
        return CURRENCY.format(value);
    }

    public static String month(YearMonth month) {
        return MONTHS[month.getMonthValue() - 1] + " " + month.getYear();
    }

    public static double parseMoney(String value) {
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.trim().replace(",", "."));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
