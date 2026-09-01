package com.example.stop_fgastos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stop_fgastos.model.CardRecord
import com.example.stop_fgastos.model.CategoryRecord
import com.example.stop_fgastos.model.FinanceState
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun AccountDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Conta corrente") }
    var opening by remember { mutableStateOf("0") }
    var icon by remember { mutableStateOf("🏦") }

    FormDialog("Nova conta", onDismiss, {
        FormText("Nome", name) { name = it }
        ChoiceField(
            "Tipo",
            type,
            listOf("Conta corrente", "Conta poupança", "Dinheiro", "Carteira digital", "Investimento")
        ) { type = it }
        FormNumber("Saldo inicial", opening) { opening = it }
        FormText("Ícone", icon) { icon = it }
    }) {
        if (name.isNotBlank()) onSave(name.trim(), type, parseNumber(opening), icon)
    }
}

@Composable
internal fun IncomeSourceDialog(
    finance: FinanceState,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Int, String) -> Unit
) {
    val kinds = listOf(
        "salary" to "Salário",
        "extra" to "Renda extra",
        "freelance" to "Freelance",
        "rent" to "Aluguel recebido",
        "commission" to "Comissão",
        "other" to "Outra renda"
    )
    var kind by remember { mutableStateOf("salary") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("5") }
    var accountId by remember { mutableStateOf("") }

    FormDialog("Nova renda recorrente", onDismiss, {
        ChoiceField("Tipo", kinds.first { it.first == kind }.second, kinds.map { it.second }) { label ->
            kind = kinds.first { it.second == label }.first
        }
        FormText("Descrição", description) { description = it }
        FormNumber("Valor", amount) { amount = it }
        FormNumber("Dia de recebimento", day) { day = it }
        if (finance.accounts.isNotEmpty()) {
            ChoiceField(
                "Conta de destino",
                finance.accounts.firstOrNull { it.id == accountId }?.name ?: "Sem conta",
                listOf("Sem conta") + finance.accounts.map { it.name }
            ) { label ->
                accountId = finance.accounts.firstOrNull { it.name == label }?.id.orEmpty()
            }
        }
    }) {
        val value = parseNumber(amount)
        if (description.isNotBlank() && value > 0) {
            onSave(kind, description.trim(), value, day.toIntOrNull()?.coerceIn(1, 31) ?: 1, accountId)
        }
    }
}

@Composable
internal fun BillDialog(
    finance: FinanceState,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String, String, String) -> Unit
) {
    var type by remember { mutableStateOf("expense") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember { mutableStateOf("outros") }
    var accountId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    FormDialog("Nova conta prevista", onDismiss, {
        ChoiceField("Tipo", if (type == "expense") "A pagar" else "A receber", listOf("A pagar", "A receber")) {
            type = if (it == "A pagar") "expense" else "income"
        }
        FormText("Descrição", description) { description = it }
        FormNumber("Valor", amount) { amount = it }
        FormText("Vencimento (AAAA-MM-DD)", dueDate) { dueDate = it }
        CategoryChoice(finance, category) { category = it }
        if (finance.accounts.isNotEmpty()) {
            ChoiceField(
                "Conta",
                finance.accounts.firstOrNull { it.id == accountId }?.name ?: "Sem conta",
                listOf("Sem conta") + finance.accounts.map { it.name }
            ) { label ->
                accountId = finance.accounts.firstOrNull { it.name == label }?.id.orEmpty()
            }
        }
        FormText("Observação", notes) { notes = it }
    }) {
        val value = parseNumber(amount)
        if (
            description.isNotBlank() &&
            value > 0 &&
            runCatching { LocalDate.parse(dueDate) }.isSuccess
        ) {
            onSave(type, description.trim(), value, dueDate, category, accountId, notes.trim())
        }
    }
}

@Composable
internal fun TransferDialog(
    finance: FinanceState,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String) -> Unit
) {
    var from by remember { mutableStateOf(finance.accounts.firstOrNull()?.id.orEmpty()) }
    var to by remember { mutableStateOf(finance.accounts.drop(1).firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember { mutableStateOf("") }

    FormDialog("Nova transferência", onDismiss, {
        ChoiceField(
            "Origem",
            finance.accounts.firstOrNull { it.id == from }?.name ?: "Selecione",
            finance.accounts.map { it.name }
        ) { label ->
            from = finance.accounts.firstOrNull { it.name == label }?.id.orEmpty()
        }
        ChoiceField(
            "Destino",
            finance.accounts.firstOrNull { it.id == to }?.name ?: "Selecione",
            finance.accounts.map { it.name }
        ) { label ->
            to = finance.accounts.firstOrNull { it.name == label }?.id.orEmpty()
        }
        FormNumber("Valor", amount) { amount = it }
        FormText("Data (AAAA-MM-DD)", date) { date = it }
        FormText("Observação", notes) { notes = it }
    }) {
        val value = parseNumber(amount)
        if (
            from.isNotBlank() &&
            to.isNotBlank() &&
            from != to &&
            value > 0 &&
            runCatching { LocalDate.parse(date) }.isSuccess
        ) {
            onSave(from, to, value, date, notes.trim())
        }
    }
}

@Composable
internal fun BudgetDialog(
    finance: FinanceState,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var category by remember {
        mutableStateOf(categoryOptions(finance).firstOrNull()?.first ?: "outros")
    }
    var amount by remember { mutableStateOf("") }

    FormDialog("Novo orçamento", onDismiss, {
        CategoryChoice(finance, category) { category = it }
        FormNumber("Limite mensal", amount) { amount = it }
    }) {
        val value = parseNumber(amount)
        if (value > 0) onSave(category, value)
    }
}

@Composable
internal fun GoalDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("0") }
    var deadline by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("🎯") }

    FormDialog("Nova meta", onDismiss, {
        FormText("Nome", name) { name = it }
        FormNumber("Objetivo", target) { target = it }
        FormNumber("Valor atual", current) { current = it }
        FormText("Prazo (AAAA-MM-DD)", deadline) { deadline = it }
        FormText("Ícone", icon) { icon = it }
    }) {
        val targetValue = parseNumber(target)
        val currentValue = parseNumber(current)
        if (name.isNotBlank() && targetValue > 0 && currentValue >= 0) {
            onSave(name.trim(), targetValue, currentValue, deadline.trim(), icon)
        }
    }
}

@Composable
internal fun CategoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("📦") }
    var group by remember { mutableStateOf("essential") }
    val groups = listOf(
        "essential" to "Essencial",
        "wants" to "Desejos",
        "future" to "Futuro",
        "income" to "Receita"
    )

    FormDialog("Nova categoria", onDismiss, {
        FormText("Nome", name) { name = it }
        FormText("Ícone", icon) { icon = it }
        ChoiceField("Grupo", groups.first { it.first == group }.second, groups.map { it.second }) { label ->
            group = groups.first { it.second == label }.first
        }
    }) {
        if (name.isNotBlank()) onSave(name.trim(), icon, group)
    }
}

@Composable
internal fun ShoppingListDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("") }

    FormDialog("Nova lista", onDismiss, {
        FormText("Nome da lista", name) { name = it }
        FormText("Mercado/local", store) { store = it }
    }) {
        if (name.isNotBlank()) onSave(name.trim(), store.trim())
    }
}

@Composable
internal fun ShoppingItemDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, Double) -> Unit
) {
    var product by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("0") }

    FormDialog("Adicionar produto", onDismiss, {
        FormText("Produto", product) { product = it }
        FormNumber("Quantidade", qty) { qty = it }
        FormNumber("Valor unitário", price) { price = it }
    }) {
        val q = parseNumber(qty)
        val p = parseNumber(price)
        if (product.isNotBlank() && q > 0 && p >= 0) onSave(product.trim(), q, p)
    }
}

@Composable
internal fun FixedCostDialog(
    finance: FinanceState,
    onDismiss: () -> Unit,
    onSave: (String, Double, Int, String, String, String, Int) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("10") }
    var category by remember { mutableStateOf("outros") }
    var payment by remember { mutableStateOf("Pix") }
    var cardId by remember { mutableStateOf("") }
    var installments by remember { mutableStateOf("1") }

    val payments = listOf(
        "Pix", "Débito", "Dinheiro", "Cartão de crédito",
        "Vale-refeição", "Vale-alimentação", "Vale-combustível"
    )
    val matching = extendedCardsForPayment(finance.cards, payment)

    LaunchedEffect(payment, finance.cards) {
        if (matching.none { it.id == cardId }) {
            cardId = matching.firstOrNull()?.id.orEmpty()
        }
    }

    FormDialog("Novo custo fixo", onDismiss, {
        FormText("Descrição", description) { description = it }
        FormNumber("Valor total", amount) { amount = it }
        FormNumber("Dia", day) { day = it }
        CategoryChoice(finance, category) { category = it }
        ChoiceField("Pagamento", payment, payments) { payment = it }

        if (matching.isNotEmpty()) {
            ChoiceField(
                "Cartão/benefício",
                matching.firstOrNull { it.id == cardId }?.name ?: "Selecione",
                matching.map { it.name }
            ) { label ->
                cardId = matching.firstOrNull { it.name == label }?.id.orEmpty()
            }
        }

        if (payment == "Cartão de crédito") {
            FormNumber("Parcelas", installments) { installments = it }
        }
    }) {
        val value = parseNumber(amount)
        if (description.isNotBlank() && value > 0) {
            onSave(
                description.trim(),
                value,
                day.toIntOrNull()?.coerceIn(1, 31) ?: 1,
                category,
                payment,
                cardId,
                installments.toIntOrNull()?.coerceIn(1, 60) ?: 1
            )
        }
    }
}

@Composable
internal fun CardDialogExtended(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Int, Int) -> Unit
) {
    val types = listOf(
        "credit" to "Cartão de crédito",
        "meal" to "Vale-refeição",
        "food" to "Vale-alimentação",
        "fuel" to "Vale-combustível",
        "benefit" to "Outro benefício"
    )
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("credit") }
    var brand by remember { mutableStateOf("Visa") }
    var limit by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf("3") }
    var due by remember { mutableStateOf("10") }

    FormDialog("Novo cartão/benefício", onDismiss, {
        FormText("Nome", name) { name = it }
        ChoiceField("Tipo", types.first { it.first == type }.second, types.map { it.second }) { label ->
            type = types.first { it.second == label }.first
        }
        FormText(if (type == "credit") "Bandeira" else "Emissor", brand) { brand = it }
        FormNumber(if (type == "credit") "Limite" else "Saldo/crédito", limit) { limit = it }
        if (type == "credit") {
            FormNumber("Dia do fechamento", closing) { closing = it }
            FormNumber("Dia do vencimento", due) { due = it }
        }
    }) {
        val value = parseNumber(limit)
        if (name.isNotBlank() && value >= 0) {
            onSave(
                name.trim(),
                type,
                brand.trim(),
                value,
                closing.toIntOrNull()?.coerceIn(1, 31) ?: 3,
                due.toIntOrNull()?.coerceIn(1, 31) ?: 10
            )
        }
    }
}

@Composable
private fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    fields: @Composable () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { fields() }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun FormText(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun FormNumber(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

@Composable
internal fun ChoiceField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChoice(
    finance: FinanceState,
    value: String,
    onSelect: (String) -> Unit
) {
    val options = categoryOptions(finance)
    ChoiceField(
        label = "Categoria",
        value = options.firstOrNull { it.first == value }?.second ?: value,
        options = options.map { it.second }
    ) { label ->
        onSelect(options.firstOrNull { it.second == label }?.first ?: "outros")
    }
}

internal fun categoryOptions(finance: FinanceState): List<Pair<String, String>> {
    val records = if (finance.categories.isEmpty()) fallbackCategoryRecords() else finance.categories
    return records.map { it.id to "${it.icon} ${it.name}" }
}

internal fun categoryLabel(finance: FinanceState, id: String): String =
    categoryOptions(finance).firstOrNull { it.first == id }?.second ?: id

internal fun fallbackCategoryRecords(): List<CategoryRecord> = listOf(
    CategoryRecord("moradia", "Moradia", "🏠", group = "essential"),
    CategoryRecord("alimentacao", "Alimentação", "🍽️", group = "essential"),
    CategoryRecord("transporte", "Transporte", "🚗", group = "essential"),
    CategoryRecord("saude", "Saúde", "❤️", group = "essential"),
    CategoryRecord("educacao", "Educação", "📚", group = "essential"),
    CategoryRecord("lazer", "Lazer", "🎮", group = "wants"),
    CategoryRecord("assinaturas", "Assinaturas", "📺", group = "wants"),
    CategoryRecord("compras", "Compras", "🛍️", group = "wants"),
    CategoryRecord("investimentos", "Investimentos", "📈", group = "future"),
    CategoryRecord("salario", "Salário", "💰", group = "income"),
    CategoryRecord("outros", "Outros", "📦", group = "essential")
)

internal fun categoryGroupLabel(group: String): String = when (group) {
    "wants" -> "Desejos"
    "future" -> "Futuro"
    "income" -> "Receita"
    else -> "Essencial"
}

internal fun incomeKindLabel(kind: String): String = when (kind) {
    "extra" -> "Renda extra"
    "freelance" -> "Freelance"
    "rent" -> "Aluguel recebido"
    "commission" -> "Comissão"
    "other" -> "Outra renda"
    else -> "Salário"
}

internal fun cardTypeLabelExtended(type: String): String = when (type) {
    "meal" -> "Vale-refeição"
    "food" -> "Vale-alimentação"
    "fuel" -> "Vale-combustível"
    "benefit" -> "Outro benefício"
    else -> "Cartão de crédito"
}

private fun extendedCardsForPayment(cards: List<CardRecord>, payment: String): List<CardRecord> {
    val type = when (payment) {
        "Cartão de crédito" -> "credit"
        "Vale-refeição" -> "meal"
        "Vale-alimentação" -> "food"
        "Vale-combustível" -> "fuel"
        else -> ""
    }
    if (type.isBlank()) return emptyList()
    return cards.filter {
        if (type == "credit") it.cardType == "credit"
        else it.cardType == type || it.cardType == "benefit"
    }
}

private fun parseNumber(value: String): Double =
    value.replace(",", ".").toDoubleOrNull() ?: 0.0

internal fun extendedMoney(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

internal fun formatQty(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale("pt", "BR"), "%.2f", value)
