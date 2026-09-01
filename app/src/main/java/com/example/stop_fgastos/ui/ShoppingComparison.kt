package com.example.stop_fgastos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stop_fgastos.model.ShoppingItemRecord
import java.text.Normalizer
import java.util.Locale

data class MarketListSnapshot(
    val id: String,
    val name: String,
    val store: String,
    val items: List<ShoppingItemRecord>
) {
    val label: String get() = store.ifBlank { name }
}

data class MarketRankingRow(
    val id: String,
    val label: String,
    val comparable: Double,
    val total: Double,
    val pricedProducts: Int,
    val products: Int
)

data class ProductComparisonRow(
    val key: String,
    val product: String,
    val bestMarket: String,
    val min: Double,
    val max: Double,
    val savings: Double
)

data class ShoppingComparisonModel(
    val ranking: List<MarketRankingRow>,
    val productRows: List<ProductComparisonRow>,
    val commonProducts: Int,
    val bestMarket: MarketRankingRow?,
    val worstMarket: MarketRankingRow?,
    val savings: Double,
    val idealSplit: Double,
    val splitSavings: Double
)

private data class PricedProduct(
    val key: String,
    val product: String,
    val price: Double
)

fun buildShoppingComparison(lists: List<MarketListSnapshot>): ShoppingComparisonModel {
    val markets = lists.mapNotNull { list ->
        val priced = linkedMapOf<String, PricedProduct>()
        list.items.forEach { item ->
            val price = item.unitPrice.coerceAtLeast(0.0)
            val key = shoppingProductKey(item.product)
            if (key.isBlank() || price <= 0.0) return@forEach
            val previous = priced[key]
            if (previous == null || price < previous.price) {
                priced[key] = PricedProduct(key, item.product.trim(), price)
            }
        }
        if (priced.isEmpty()) null else {
            val total = list.items.sumOf { item -> item.qty.coerceAtLeast(0.0) * item.unitPrice.coerceAtLeast(0.0) }
            val pricedProducts = list.items.count { it.unitPrice > 0.0 }
            Triple(list, priced, Triple(total, pricedProducts, list.items.size))
        }
    }

    val buckets = linkedMapOf<String, MutableList<Pair<MarketListSnapshot, PricedProduct>>>()
    markets.forEach { (list, priced, _) ->
        priced.forEach { (key, product) ->
            buckets.getOrPut(key) { mutableListOf() }.add(list to product)
        }
    }

    val productRows = buckets.mapNotNull { (key, values) ->
        if (values.size < 2) return@mapNotNull null
        val sorted = values.sortedBy { it.second.price }
        val best = sorted.first()
        val worst = sorted.last()
        ProductComparisonRow(
            key = key,
            product = best.second.product,
            bestMarket = best.first.label,
            min = best.second.price,
            max = worst.second.price,
            savings = (worst.second.price - best.second.price).coerceAtLeast(0.0)
        )
    }.sortedWith(compareByDescending<ProductComparisonRow> { it.savings }.thenBy { it.product })

    val commonKeys = if (markets.size >= 2) {
        markets.first().second.keys.filter { key ->
            markets.all { (_, priced, _) -> priced.containsKey(key) }
        }
    } else emptyList()

    val ranking = if (markets.size >= 2 && commonKeys.isNotEmpty()) {
        markets.map { (list, priced, totals) ->
            MarketRankingRow(
                id = list.id,
                label = list.label,
                comparable = commonKeys.sumOf { key -> priced[key]?.price ?: 0.0 },
                total = totals.first,
                pricedProducts = totals.second,
                products = totals.third
            )
        }.sortedWith(compareBy<MarketRankingRow> { it.comparable }.thenBy { it.label })
    } else emptyList()

    val best = ranking.firstOrNull()
    val worst = ranking.lastOrNull()
    val savings = if (best != null && worst != null) {
        (worst.comparable - best.comparable).coerceAtLeast(0.0)
    } else 0.0

    val idealSplit = commonKeys.sumOf { key ->
        markets.mapNotNull { (_, priced, _) -> priced[key]?.price }.minOrNull() ?: 0.0
    }
    val splitSavings = if (best != null) (best.comparable - idealSplit).coerceAtLeast(0.0) else 0.0

    return ShoppingComparisonModel(
        ranking = ranking,
        productRows = productRows,
        commonProducts = commonKeys.size,
        bestMarket = best,
        worstMarket = worst,
        savings = savings,
        idealSplit = idealSplit,
        splitSavings = splitSavings
    )
}

private fun shoppingProductKey(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("[\\\\p{InCombiningDiacriticalMarks}]"), "")
        .lowercase(Locale("pt", "BR"))
        .replace("×", "x")
        .replace(Regex("([0-9])\\\\s*(kg|ml|g|l|unid)\\\\b"), "$1 $2")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\\\s+"), " ")

@Composable
fun ShoppingComparisonPanel(
    lists: List<MarketListSnapshot>,
    modifier: Modifier = Modifier
) {
    if (lists.size < 2) return
    val model = buildShoppingComparison(lists)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Comparação de mercados",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (model.bestMarket == null) {
            Card {
                Text(
                    "Informe o preço dos mesmos produtos em pelo menos dois mercados para montar uma cesta comparável.",
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@Column
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Melhor mercado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    model.bestMarket.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${model.commonProducts} produto(s) comparáveis · cesta ${extendedMoney(model.bestMarket.comparable)}"
                )
                if (model.worstMarket != null) {
                    Text(
                        "Economia de ${extendedMoney(model.savings)} contra ${model.worstMarket.label}",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (model.splitSavings > 0) {
                    Text(
                        "Comprando cada item no menor preço: ${extendedMoney(model.idealSplit)} · economia adicional ${extendedMoney(model.splitSavings)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        model.ranking.forEach { market ->
            val max = model.ranking.maxOfOrNull { it.comparable } ?: 1.0
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row {
                        Text(market.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(extendedMoney(market.comparable), fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { (market.comparable / max).coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${market.pricedProducts}/${market.products} preços preenchidos",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (model.productRows.isNotEmpty()) {
            Text(
                "Onde cada produto está mais barato",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            model.productRows.take(12).forEach { row ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(row.product, fontWeight = FontWeight.SemiBold)
                            Text(row.bestMarket, style = MaterialTheme.typography.bodySmall)
                        }
                        Column {
                            Text(extendedMoney(row.min), fontWeight = FontWeight.Bold)
                            if (row.savings > 0) {
                                Text(
                                    "economiza ${extendedMoney(row.savings)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
