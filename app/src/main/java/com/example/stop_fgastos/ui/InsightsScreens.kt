package com.example.stop_fgastos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stop_fgastos.model.FinanceState
import com.example.stop_fgastos.viewmodel.MainViewModel
import java.time.YearMonth
import kotlin.math.max

private data class CalendarEntry(
    val id: String,
    val date: String,
    val title: String,
    val amount: Double,
    val type: String,
    val status: String
)

private data class MonthTrendPoint(
    val month: YearMonth,
    val income: Double,
    val expense: Double
)

@Composable
fun FinancialCalendarScreen(finance: FinanceState, viewModel: MainViewModel) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val key = month.toString()

    LaunchedEffect(month) {
        viewModel.ensureRecurringForMonth(month)
    }

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
                        status = if (tx.installmentCount > 1) {
                            "Parcela ${tx.installmentNo}/${tx.installmentCount}"
                        } else {
                            "Lançamento"
                        }
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

    val income = finance.transactions
        .filter { it.type == "income" && it.date.startsWith(key) }
        .sumOf { it.amount }
    val expense = finance.transactions
        .filter { it.type == "expense" && it.date.startsWith(key) }
        .sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        monthLabelNative(month),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                    Text(
                        "Nenhum evento financeiro neste mês.",
                        Modifier.fillMaxWidth().padding(18.dp)
                    )
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
                            (if (entry.type == "expense") "- " else "+ ") +
                                extendedMoney(entry.amount),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialReportsScreen(finance: FinanceState, viewModel: MainViewModel) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val key = month.toString()
    val context = LocalContext.current
    var csvPayload by remember { mutableStateOf("") }

    LaunchedEffect(month) {
        viewModel.ensureRecurringForMonth(month)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null && csvPayload.isNotBlank()) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(csvPayload)
            }
        }
    }

    val transactions = finance.transactions.filter { it.date.startsWith(key) }
    val income = transactions.filter { it.type == "income" }.sumOf { it.amount }
    val expense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
    val balance = income - expense
    val savingsRate = if (income > 0) (balance / income) * 100.0 else 0.0

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
        MonthTrendPoint(
            month = target,
            income = tx.filter { it.type == "income" }.sumOf { it.amount },
            expense = tx.filter { it.type == "expense" }.sumOf { it.amount }
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
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Relatórios",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(monthLabelNative(month))
                }
                TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
            }
        }

        item {
            Button(
                onClick = {
                    csvPayload = buildReportCsv(finance, month)
                    exportLauncher.launch("stop-gastos-${month}.csv")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exportar relatório CSV")
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportMetric("Receitas", extendedMoney(income), Modifier.weight(1f))
                ReportMetric("Despesas", extendedMoney(expense), Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportMetric("Saldo", extendedMoney(balance), Modifier.weight(1f))
                ReportMetric(
                    "Economia",
                    String.format(java.util.Locale("pt", "BR"), "%.1f%%", savingsRate),
                    Modifier.weight(1f)
                )
            }
        }

        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Evolução de 6 meses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    MonthlyTrendChart(sixMonths)
                    Row(Modifier.fillMaxWidth()) {
                        Text("Entradas", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        Text("Saídas", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            Text(
                "Despesas por categoria",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (categorySpend.isEmpty()) {
            item {
                Card {
                    Text(
                        "Sem despesas no período.",
                        Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }
        } else {
            item {
                CategoryBarChart(
                    values = categorySpend.take(6),
                    finance = finance
                )
            }

            items(categorySpend.take(10), key = { it.first }) { (categoryId, value) ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row {
                            Text(
                                categoryLabel(finance, categoryId),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(extendedMoney(value), fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = {
                                (value / maxCategory).coerceIn(0.0, 1.0).toFloat()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Histórico",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(sixMonths, key = { it.month.toString() }) { row ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(monthLabelNative(row.month), fontWeight = FontWeight.SemiBold)
                    Row {
                        Text(
                            "Entradas: ${extendedMoney(row.income)}",
                            modifier = Modifier.weight(1f)
                        )
                        Text("Saídas: ${extendedMoney(row.expense)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyTrendChart(points: List<MonthTrendPoint>) {
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outlineVariant
    val maxValue = max(
        1.0,
        points.maxOfOrNull { max(it.income, it.expense) } ?: 1.0
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .padding(top = 18.dp, bottom = 8.dp)
    ) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val height = bottom - top
        val width = right - left

        drawLine(
            color = outline,
            start = Offset(left, bottom),
            end = Offset(right, bottom),
            strokeWidth = 1.dp.toPx()
        )

        fun point(index: Int, value: Double): Offset {
            val x = if (points.size <= 1) left else left + width * index / (points.size - 1)
            val y = bottom - (value / maxValue).toFloat() * height
            return Offset(x, y)
        }

        points.windowed(2).forEachIndexed { index, _ ->
            drawLine(
                color = primary,
                start = point(index, points[index].income),
                end = point(index + 1, points[index + 1].income),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = error,
                start = point(index, points[index].expense),
                end = point(index + 1, points[index + 1].expense),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        points.forEachIndexed { index, row ->
            drawCircle(primary, radius = 4.dp.toPx(), center = point(index, row.income))
            drawCircle(error, radius = 4.dp.toPx(), center = point(index, row.expense))
        }
    }
}

@Composable
private fun CategoryBarChart(
    values: List<Pair<String, Double>>,
    finance: FinanceState
) {
    val primary = MaterialTheme.colorScheme.primary
    val maxValue = values.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                val gap = 8.dp.toPx()
                val usableWidth = size.width - gap * (values.size + 1)
                val barWidth = if (values.isEmpty()) 0f else usableWidth / values.size
                val bottom = size.height

                values.forEachIndexed { index, (_, value) ->
                    val h = (value / maxValue).toFloat() * (size.height - 12.dp.toPx())
                    val left = gap + index * (barWidth + gap)
                    drawRect(
                        color = primary,
                        topLeft = Offset(left, bottom - h),
                        size = Size(barWidth, h)
                    )
                }
            }

            values.forEach { (categoryId, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        categoryLabel(finance, categoryId),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        extendedMoney(value),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
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
private fun ReportMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun buildReportCsv(finance: FinanceState, month: YearMonth): String {
    val key = month.toString()
    val transactions = finance.transactions
        .filter { it.date.startsWith(key) }
        .sortedBy { it.date }

    val builder = StringBuilder()
    builder.appendLine("Stop Gastos;Relatório;${monthLabelNative(month)}")
    builder.appendLine()
    builder.appendLine("Data;Tipo;Descrição;Categoria;Pagamento;Conta;Cartão;Parcela;Valor;Tags;Observações")

    transactions.forEach { tx ->
        val account = finance.accounts.firstOrNull { it.id == tx.accountId }?.name.orEmpty()
        val card = finance.cards.firstOrNull { it.id == tx.cardId }?.name.orEmpty()
        val installment = if (tx.installmentCount > 1) {
            "${tx.installmentNo}/${tx.installmentCount}"
        } else {
            "1/1"
        }

        builder.appendLine(
            listOf(
                tx.date,
                if (tx.type == "expense") "Despesa" else "Receita",
                tx.description,
                categoryLabel(finance, tx.category),
                tx.payment,
                account,
                card,
                installment,
                String.format(java.util.Locale.US, "%.2f", tx.amount),
                tx.tags,
                tx.notes
            ).joinToString(";") { csvCell(it) }
        )
    }

    builder.appendLine()
    val income = transactions.filter { it.type == "income" }.sumOf { it.amount }
    val expense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
    builder.appendLine("Resumo;Valor")
    builder.appendLine("Receitas;${String.format(java.util.Locale.US, "%.2f", income)}")
    builder.appendLine("Despesas;${String.format(java.util.Locale.US, "%.2f", expense)}")
    builder.appendLine("Saldo;${String.format(java.util.Locale.US, "%.2f", income - expense)}")

    return builder.toString()
}

private fun csvCell(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""

private fun monthLabelNative(month: YearMonth): String {
    val names = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    return "${names[month.monthValue - 1]} ${month.year}"
}
