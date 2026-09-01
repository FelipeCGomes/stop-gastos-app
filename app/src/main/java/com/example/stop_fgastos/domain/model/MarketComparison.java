package com.example.stop_fgastos.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarketComparison {
    public static final class MarketRow {
        public final String id;
        public final String label;
        public final double comparableTotal;
        public final int commonProducts;

        public MarketRow(String id, String label, double comparableTotal, int commonProducts) {
            this.id = id;
            this.label = label;
            this.comparableTotal = comparableTotal;
            this.commonProducts = commonProducts;
        }
    }

    private final List<MarketRow> ranking;
    private final double bestSplit;
    private final double savings;

    public MarketComparison(List<MarketRow> ranking, double bestSplit, double savings) {
        this.ranking = Collections.unmodifiableList(new ArrayList<>(ranking));
        this.bestSplit = bestSplit;
        this.savings = savings;
    }

    public List<MarketRow> ranking() { return ranking; }
    public double bestSplit() { return bestSplit; }
    public double savings() { return savings; }
}
