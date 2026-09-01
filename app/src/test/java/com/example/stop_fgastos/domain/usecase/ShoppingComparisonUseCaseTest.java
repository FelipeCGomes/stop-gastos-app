package com.example.stop_fgastos.domain.usecase;

import static org.junit.Assert.assertEquals;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.MarketComparison;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public final class ShoppingComparisonUseCaseTest {

    @Test
    public void ranksCheapestMarketUsingCommonProducts() {
        FinanceRecord a = new FinanceRecord("a", Map.of(
                "id", "a",
                "name", "Mercado A",
                "store", "Mercado A",
                "items", List.of(
                        Map.of("product", "Arroz 5kg", "unitPrice", 25.0),
                        Map.of("product", "Feijão 1kg", "unitPrice", 8.0)
                )
        ));

        FinanceRecord b = new FinanceRecord("b", Map.of(
                "id", "b",
                "name", "Mercado B",
                "store", "Mercado B",
                "items", List.of(
                        Map.of("product", "Arroz 5kg", "unitPrice", 23.0),
                        Map.of("product", "Feijão 1kg", "unitPrice", 9.0)
                )
        ));

        MarketComparison result = new ShoppingComparisonUseCase().compare(List.of(a, b));

        assertEquals(2, result.ranking().size());
        assertEquals("Mercado B", result.ranking().get(0).label);
        assertEquals(32.0, result.ranking().get(0).comparableTotal, 0.001);
        assertEquals(1.0, result.savings(), 0.001);
        assertEquals(31.0, result.bestSplit(), 0.001);
    }
}
