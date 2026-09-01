package com.example.stop_fgastos.presentation.common;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class UiFormat {
    private static final NumberFormat CURRENCY =
            NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yy", new Locale("pt", "BR"));

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

    public static String date(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY_DATE);
    }

    public static String date(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            return date(parseDate(value));
        } catch (Exception ignored) {
            return value;
        }
    }

    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Informe a data.");
        }

        String clean = value.trim();

        if (clean.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(clean);
        }

        if (clean.matches("\\d{2}/\\d{2}/\\d{4}")) {
            String[] parts = clean.split("/");
            return LocalDate.of(
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[0])
            );
        }

        if (clean.matches("\\d{2}/\\d{2}/\\d{2}")) {
            String[] parts = clean.split("/");
            int year = 2000 + Integer.parseInt(parts[2]);
            return LocalDate.of(
                    year,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[0])
            );
        }

        throw new IllegalArgumentException("Use o formato dd/MM/aa.");
    }

    public static String isoDate(String value) {
        return parseDate(value).toString();
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
