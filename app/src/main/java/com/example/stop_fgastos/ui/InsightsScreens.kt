package com.example.stop_fgastos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stop_fgastos.model.FinanceState
import java.time.YearMonth

private data class CalendarEntry(
    val id: String,
    val date: String,
    val title: String,
    val amount: Double,
    val type: String,
    val status: String
)

@Composable
fun FinancialCalendarScreen(finance: FinanceState) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val key = month.toString()

    val entries = buildList {
        finance.transactions
            .filter { it.date.startsWith(key) }
            .forEach { tx ->
                add(
                    CalendarEntry(
                        id = "tx-" + tx.id,
                        date = tx.date,
                        title = tx.description,
                        amount = tx.amount,
                        type = tx.type,
                        status = if (tx.installmentCount > 1) "Parcela ${tx.installmentNo}/${tx.installmentCount}" else "Lançamento"
                    )
                )
            }

        finance.bills
            .filter { it.dueDate.startsWith(key) }
            .forEach { bill ->
                add(
                    CalendarEntry(
                        id = "bill-" + bill.id,
                        date = bill.dueDate,
                        title = bill.description,
                        amount = bill.amount,
                        type = bill.type,
                        status = if (bill.paid) "Pago" else "Previsto"
                    )
                )
            }
    }.sortedBy { it.date }

    val income = entries.filter { it.type == "income" && it.status != "Previsto" }.sumOf { it.amount }
    val expense = entries.filter { it.type == "expense" && it.status != "Previsto" }.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(monthLabelNative(month), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Calendário financeiro", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CalendarSummary("Entradas", extendedMoney(income), Modifier.weight(1f))
                CalendarSummary("Saídas", extendedMoney(expense), Modifier.weight(1f))
            }
        }

        if (entries.isEmpty()) {
            item {
                Card {
                    Text("Nenhum evento financeiro neste mês.", Modifier.fillMaxWidth().padding(18.dp))
                }
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${entry.date} · ${entry.status}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            (if (entry.type == "expense") "- " else "+ ") + extendedMoney(entry.amount),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialReportsScreen(finance: FinanceState) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val key = month.toString()
    val transactions = finance.transactions.filter { it.date.startsWith(key) }
    val income = transactions.filter { it.type == "income" }.sumOf { it.amount }
    val expense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
    val balance = income - expense
    val savingsRate = if (income > 0) ((balance / income) * 100.0) else 0.0

    val categorySpend = transactions
        .filter { it.type == "expense" }
        .groupBy { it.category }
        .mapValues { (_, values) -> values.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    val maxCategory = categorySpend.maxOfOrNull { it.second } ?: 1.0

    val sixMonths = (5 downTo 0).map { offset ->
        val target = month.minusMonths(offset.toLong())
        val targetKey = target.toString()
        val tx = finance.transactions.filter { it.date.startsWith(targetKey) }
        Triple(
            target,
            tx.filter { it.type == "income" }.sumOf { it.amount },
            tx.filter { it.type == "expense" }.sumOf { it.amount }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Relatórios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(monthLabelNative(month))
                }
                TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
            }
        }

        item {
            ReportMetric("Receitas", extendedMoney(income))
        }
        item {
            ReportMetric("Despesas", extendedMoney(expense))
        }
        item {
            ReportMetric("Saldo", extendedMoney(balance))
        }
        item {
            ReportMetric("Taxa de economia", String.format(java.util.Locale("pt", "BR"), "%.1f%%", savingsRate))
        }

        item {
            Text("Despesas por categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (categorySpend.isEmpty()) {
            item {
                Card { Text("Sem despesas no período.", Modifier.fillMaxWidth().padding(16.dp)) }
            }
        } else {
            items(categorySpend.take(8), key = { it.first }) { (categoryId, value) ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row {
                            Text(categoryLabel(finance, categoryId), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(extendedMoney(value), fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { (value / maxCategory).coerceIn(0.0, 1.0).toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            Text("Últimos 6 meses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(sixMonths, key = { it.first.toString() }) { row ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(monthLabelNative(row.first), fontWeight = FontWeight.SemiBold)
                    Row {
                        Text("Entradas: ${extendedMoney(row.second)}", modifier = Modifier.weight(1f))
                        Text("Saídas: ${extendedMoney(row.third)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSummary(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: String) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun monthLabelNative(month: YearMonth): String {
    val names = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    return "${names[month.monthValue - 1]} ${month.year}"
}
