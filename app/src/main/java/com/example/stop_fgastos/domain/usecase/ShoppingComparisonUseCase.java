package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.MarketComparison;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ShoppingComparisonUseCase {
    private static final class ProductPrice {
        final String label;
        final double price;

        ProductPrice(String label, double price) {
            this.label = label;
            this.price = price;
        }
    }

    @SuppressWarnings("unchecked")
    public MarketComparison compare(List<FinanceRecord> lists) {
        Map<String, Map<String, ProductPrice>> marketProducts = new LinkedHashMap<>();
        Map<String, String> marketLabels = new LinkedHashMap<>();

        for (FinanceRecord list : lists) {
            String label = list.text("store", list.text("name", "Mercado"));
            marketLabels.put(list.id(), label);

            Map<String, ProductPrice> products = new HashMap<>();
            Object raw = list.fields().get("items");
            if (raw instanceof List<?>) {
                for (Object itemRaw : (List<?>) raw) {
                    if (!(itemRaw instanceof Map<?, ?>)) continue;
                    Map<String, Object> item = (Map<String, Object>) itemRaw;
                    String product = String.valueOf(item.getOrDefault("product", "")).trim();
                    double price = number(item.get("unitPrice"));
                    String key = normalize(product);
                    if (key.isBlank() || price <= 0.0) continue;

                    ProductPrice existing = products.get(key);
                    if (existing == null || price < existing.price) {
                        products.put(key, new ProductPrice(product, price));
                    }
                }
            }
            if (!products.isEmpty()) marketProducts.put(list.id(), products);
        }

        if (marketProducts.size() < 2) {
            return new MarketComparison(List.of(), 0.0, 0.0);
        }

        Set<String> common = null;
        for (Map<String, ProductPrice> products : marketProducts.values()) {
            if (common == null) common = new HashSet<>(products.keySet());
            else common.retainAll(products.keySet());
        }
        if (common == null || common.isEmpty()) {
            return new MarketComparison(List.of(), 0.0, 0.0);
        }

        List<MarketComparison.MarketRow> ranking = new ArrayList<>();
        for (Map.Entry<String, Map<String, ProductPrice>> market : marketProducts.entrySet()) {
            double total = 0.0;
            for (String key : common) total += market.getValue().get(key).price;
            ranking.add(new MarketComparison.MarketRow(
                    market.getKey(),
                    marketLabels.get(market.getKey()),
                    FinanceCalculator.roundMoney(total),
                    common.size()
            ));
        }
        ranking.sort(Comparator.comparingDouble(row -> row.comparableTotal));

        double bestSplit = 0.0;
        for (String key : common) {
            double min = Double.MAX_VALUE;
            for (Map<String, ProductPrice> products : marketProducts.values()) {
                min = Math.min(min, products.get(key).price);
            }
            bestSplit += min;
        }

        double savings = ranking.size() < 2
                ? 0.0
                : ranking.get(ranking.size() - 1).comparableTotal - ranking.get(0).comparableTotal;

        return new MarketComparison(
                ranking,
                FinanceCalculator.roundMoney(bestSplit),
                FinanceCalculator.roundMoney(Math.max(0.0, savings))
        );
    }

    private static double number(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return value == null ? 0.0 : Double.parseDouble(String.valueOf(value).replace(",", "."));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('×', 'x')
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
