package com.example.stop_fgastos

import com.example.stop_fgastos.model.ShoppingItemRecord
import com.example.stop_fgastos.ui.MarketListSnapshot
import com.example.stop_fgastos.ui.buildShoppingComparison
import com.example.stop_fgastos.util.FinanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceRulesTest {

    @Test
    fun splitInstallments_preservesExactTotalInCents() {
        val parts = FinanceCalculator.splitInstallments(100.0, 3)

        assertEquals(listOf(33.34, 33.33, 33.33), parts)
        assertEquals(100.0, FinanceCalculator.sum(parts), 0.000001)
    }

    @Test
    fun splitInstallments_limitsSeriesToSixty() {
        val parts = FinanceCalculator.splitInstallments(120.0, 99)

        assertEquals(60, parts.size)
        assertEquals(120.0, FinanceCalculator.sum(parts), 0.000001)
    }

    @Test
    fun marketComparison_ranksEquivalentBasketAndCalculatesSavings() {
        val first = MarketListSnapshot(
            id = "a",
            name = "Lista A",
            store = "Mercado A",
            items = listOf(
                ShoppingItemRecord(id = "a1", product = "Arroz 5kg", qty = 1.0, unitPrice = 25.0),
                ShoppingItemRecord(id = "a2", product = "Feijão 1kg", qty = 1.0, unitPrice = 8.0)
            )
        )
        val second = MarketListSnapshot(
            id = "b",
            name = "Lista B",
            store = "Mercado B",
            items = listOf(
                ShoppingItemRecord(id = "b1", product = "Arroz 5 kg", qty = 1.0, unitPrice = 28.0),
                ShoppingItemRecord(id = "b2", product = "Feijao 1kg", qty = 1.0, unitPrice = 7.0)
            )
        )

        val model = buildShoppingComparison(listOf(first, second))

        assertEquals(2, model.commonProducts)
        assertEquals("Mercado A", model.bestMarket?.label)
        assertEquals(33.0, model.bestMarket?.comparable ?: 0.0, 0.000001)
        assertEquals(35.0, model.worstMarket?.comparable ?: 0.0, 0.000001)
        assertEquals(2.0, model.savings, 0.000001)
        assertEquals(32.0, model.idealSplit, 0.000001)
        assertEquals(1.0, model.splitSavings, 0.000001)
        assertTrue(model.productRows.any { it.product.contains("Arroz", ignoreCase = true) })
    }
}
