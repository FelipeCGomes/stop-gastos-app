package com.example.stop_fgastos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.example.stop_fgastos.model.AccountRecord
import com.example.stop_fgastos.model.BillRecord
import com.example.stop_fgastos.model.BudgetRecord
import com.example.stop_fgastos.model.CardRecord
import com.example.stop_fgastos.model.CategoryRecord
import com.example.stop_fgastos.model.FinanceState
import com.example.stop_fgastos.model.GoalRecord
import com.example.stop_fgastos.model.IncomeSourceRecord
import com.example.stop_fgastos.model.RecurringRecord
import com.example.stop_fgastos.model.ShoppingItemRecord
import com.example.stop_fgastos.model.ShoppingListRecord
import com.example.stop_fgastos.model.TransactionRecord
import com.example.stop_fgastos.model.TransferRecord
import java.time.LocalDate

@Composable
internal fun TransactionEditorDialog(
    finance: FinanceState,
    initial: TransactionRecord?,
    onDismiss: () -> Unit,
    onSave: (
        type: String,
        description: String,
        amount: Double,
        date: String,
        category: String,
        payment: String,
        cardId: String,
        installments: Int,
        accountId: String,
        tags: String,
        notes: String
    ) -> Unit
) {
    val lockedSource = initial?.sourceType in setOf("recurringExpense", "incomeSource")
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: "expense") }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var amount by remember(initial?.id) {
        mutableStateOf(
            initial?.purchaseTotal?.takeIf { it > 0 }?.toString()
                ?: initial?.amount?.toString().orEmpty()
        )
    }
    var date by remember(initial?.id) {
        mutableStateOf(initial?.purchaseDate?.ifBlank { initial.date } ?: LocalDate.now().toString())
    }
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "outros") }
    var payment by remember(initial?.id) { mutableStateOf(initial?.payment?.ifBlank { "Pix" } ?: "Pix") }
    var cardId by remember(initial?.id) { mutableStateOf(initial?.cardId.orEmpty()) }
    var installments by remember(initial?.id) { mutableStateOf((initial?.installmentCount ?: 1).toString()) }
    var accountId by remember(initial?.id) { mutableStateOf(initial?.accountId.orEmpty()) }
    var tags by remember(initial?.id) { mutableStateOf(initial?.tags.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }

    val matchingCards = editorCardsForPayment(finance.cards, payment)

    LaunchedEffect(payment, finance.cards) {
        if (paymentUsesCardEditor(payment)) {
            if (matchingCards.none { it.id == cardId }) {
                cardId = matchingCards.firstOrNull()?.id.orEmpty()
            }
        } else {
            cardId = ""
        }
        if (payment != "Cartão de crédito") installments = "1"
    }

    EditorDialog(
        title = if (initial == null) "Novo lançamento" else "Editar lançamento",
        onDismiss = onDismiss,
        onConfirm = {
            val total = editorNumber(amount)
            val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
            val count = installments.toIntOrNull()?.coerceIn(1, 60) ?: 1
            val cardRequired = type == "expense" && paymentUsesCardEditor(payment)

            if (
                description.isNotBlank() &&
                total > 0 &&
                parsedDate != null &&
                (!cardRequired || cardId.isNotBlank())
            ) {
                onSave(
                    type,
                    description.trim(),
                    total,
                    date,
                    category,
                    payment,
                    cardId,
                    count,
                    accountId,
                    tags.trim(),
                    notes.trim()
                )
            }
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = type == "expense",
                enabled = !lockedSource || initial?.type == "expense",
                onClick = { type = "expense" },
                label = { Text("Despesa") }
            )
            FilterChip(
                selected = type == "income",
                enabled = !lockedSource || initial?.type == "income",
                onClick = {
                    type = "income"
                    payment = "Pix"
                    cardId = ""
                    installments = "1"
                },
                label = { Text("Receita") }
            )
        }

        EditorText("Descrição", description) { description = it }
        EditorNumber("Valor total", amount) { amount = it }
        EditorText("Data da compra (AAAA-MM-DD)", date) { date = it }

        val categories = categoryOptions(finance)
        ChoiceField(
            "Categoria",
            categories.firstOrNull { it.first == category }?.second ?: category,
            categories.map { it.second }
        ) { label ->
            category = categories.firstOrNull { it.second == label }?.first ?: "outros"
        }

        if (finance.accounts.isNotEmpty()) {
            ChoiceField(
                "Conta/carteira",
                finance.accounts.firstOrNull { it.id == accountId }?.name ?: "Sem conta",
                listOf("Sem conta") + finance.accounts.map { it.name }
            ) { label ->
                accountId = finance.accounts.firstOrNull { it.name == label }?.id.orEmpty()
            }
        }

        if (type == "expense") {
            ChoiceField(
                "Pagamento",
                payment,
                listOf(
                    "Pix",
                    "Débito",
                    "Dinheiro",
                    "Cartão de crédito",
                    "Vale-refeição",
                    "Vale-alimentação",
                    "Vale-combustível"
                )
            ) { payment = it }

            if (paymentUsesCardEditor(payment)) {
                ChoiceField(
                    "Cartão/benefício",
                    matchingCards.firstOrNull { it.id == cardId }?.name ?: "Selecione",
                    matchingCards.map { it.name }
                ) { label ->
                    cardId = matchingCards.firstOrNull { it.name == label }?.id.orEmpty()
                }
            }

            if (payment == "Cartão de crédito") {
                EditorNumber("Parcelas (1 a 60)", installments) { installments = it }
                val total = editorNumber(amount)
                val count = installments.toIntOrNull()?.coerceIn(1, 60) ?: 1
                if (total > 0) {
                    Text(
                        if (count == 1) "À vista · ${extendedMoney(total)}"
                        else "$count × aproximadamente ${extendedMoney(total / count)} · total ${extendedMoney(total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        EditorText("Tags", tags) { tags = it }
        EditorText("Observações", notes) { notes = it }

        if (initial != null && initial.installmentCount > 1) {
            Text(
                "Ao salvar, todo o parcelamento ${initial.installmentNo}/${initial.installmentCount} será recalculado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
internal fun FixedCostEditorDialog(
    finance: FinanceState,
    initial: RecurringRecord?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Int, String, String, String, Int, String, Boolean) -> Unit
) {
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var amount by remember(initial?.id) { mutableStateOf(initial?.amount?.toString().orEmpty()) }
    var day by remember(initial?.id) { mutableStateOf((initial?.day ?: 10).toString()) }
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "outros") }
    var payment by remember(initial?.id) { mutableStateOf(initial?.payment?.ifBlank { "Pix" } ?: "Pix") }
    var cardId by remember(initial?.id) { mutableStateOf(initial?.cardId.orEmpty()) }
    var installments by remember(initial?.id) { mutableStateOf((initial?.installmentCount ?: 1).toString()) }
    var kind by remember(initial?.id) { mutableStateOf(initial?.kind ?: "fixed") }
    var active by remember(initial?.id) { mutableStateOf(initial?.active ?: true) }

    val matching = editorCardsForPayment(finance.cards, payment)
    LaunchedEffect(payment, finance.cards) {
        if (paymentUsesCardEditor(payment) && matching.none { it.id == cardId }) {
            cardId = matching.firstOrNull()?.id.orEmpty()
        }
        if (!paymentUsesCardEditor(payment)) cardId = ""
        if (payment != "Cartão de crédito") installments = "1"
    }

    EditorDialog(
        if (initial == null) "Novo custo fixo" else "Editar custo fixo",
        onDismiss,
        onConfirm = {
            val value = editorNumber(amount)
            if (description.isNotBlank() && value > 0) {
                onSave(
                    description.trim(),
                    value,
                    day.toIntOrNull()?.coerceIn(1, 31) ?: 1,
                    category,
                    payment,
                    cardId,
                    installments.toIntOrNull()?.coerceIn(1, 60) ?: 1,
                    kind,
                    active
                )
            }
        }
    ) {
        EditorText("Descrição", description) { description = it }
        ChoiceField(
            "Tipo",
            if (kind == "subscription") "Assinatura" else "Despesa fixa",
            listOf("Despesa fixa", "Assinatura")
        ) { label ->
            kind = if (label == "Assinatura") "subscription" else "fixed"
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ativo", modifier = Modifier.weight(1f))
            Switch(checked = active, onCheckedChange = { active = it })
        }
        EditorNumber("Valor", amount) { amount = it }
        EditorNumber("Dia", day) { day = it }
        val cats = categoryOptions(finance)
        ChoiceField("Categoria", cats.firstOrNull { it.first == category }?.second ?: category, cats.map { it.second }) { label ->
            category = cats.firstOrNull { it.second == label }?.first ?: "outros"
        }
        ChoiceField(
            "Pagamento",
            payment,
            listOf("Pix", "Débito", "Dinheiro", "Cartão de crédito", "Vale-refeição", "Vale-alimentação", "Vale-combustível")
        ) { payment = it }
        if (paymentUsesCardEditor(payment)) {
            ChoiceField(
                "Cartão/benefício",
                matching.firstOrNull { it.id == cardId }?.name ?: "Selecione",
                matching.map { it.name }
            ) { label -> cardId = matching.firstOrNull { it.name == label }?.id.orEmpty() }
        }
        if (payment == "Cartão de crédito") {
            EditorNumber("Parcelas", installments) { installments = it }
        }
    }
}

@Composable
internal fun AccountEditorDialog(
    initial: AccountRecord?,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: "Conta corrente") }
    var opening by remember(initial?.id) { mutableStateOf((initial?.openingBalance ?: 0.0).toString()) }
    var icon by remember(initial?.id) { mutableStateOf(initial?.icon ?: "🏦") }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: "#7c5cff") }
    EditorDialog(if (initial == null) "Nova conta" else "Editar conta", onDismiss, {
        if (name.isNotBlank()) onSave(name.trim(), type, editorNumber(opening), icon, normalizeHexColor(color, "#7c5cff"))
    }) {
        EditorText("Nome", name) { name = it }
        ChoiceField("Tipo", type, listOf("Conta corrente", "Conta poupança", "Dinheiro", "Carteira digital", "Investimento")) { type = it }
        EditorNumber("Saldo inicial", opening) { opening = it }
        EditorText("Ícone", icon) { icon = it }
        EditorText("Cor (#RRGGBB)", color) { color = it }
    }
}

@Composable
internal fun IncomeSourceEditorDialog(
    finance: FinanceState,
    initial: IncomeSourceRecord?,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Int, String, Boolean) -> Unit
) {
    val kinds = listOf(
        "salary" to "Salário",
        "extra" to "Renda extra",
        "freelance" to "Freelance",
        "rent" to "Aluguel recebido",
        "commission" to "Comissão",
        "other" to "Outra renda"
    )
    var kind by remember(initial?.id) { mutableStateOf(initial?.kind ?: "salary") }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var amount by remember(initial?.id) { mutableStateOf(initial?.amount?.toString().orEmpty()) }
    var day by remember(initial?.id) { mutableStateOf((initial?.day ?: 5).toString()) }
    var accountId by remember(initial?.id) { mutableStateOf(initial?.accountId.orEmpty()) }
    var active by remember(initial?.id) { mutableStateOf(initial?.active ?: true) }

    EditorDialog(if (initial == null) "Nova renda recorrente" else "Editar renda recorrente", onDismiss, {
        val value = editorNumber(amount)
        if (description.isNotBlank() && value > 0) {
            onSave(kind, description.trim(), value, day.toIntOrNull()?.coerceIn(1, 31) ?: 1, accountId, active)
        }
    }) {
        ChoiceField("Tipo", kinds.first { it.first == kind }.second, kinds.map { it.second }) { label ->
            kind = kinds.first { it.second == label }.first
        }
        EditorText("Descrição", description) { description = it }
        EditorNumber("Valor", amount) { amount = it }
        EditorNumber("Dia de recebimento", day) { day = it }
        if (finance.accounts.isNotEmpty()) {
            ChoiceField(
                "Conta de destino",
                finance.accounts.firstOrNull { it.id == accountId }?.name ?: "Sem conta",
                listOf("Sem conta") + finance.accounts.map { it.name }
            ) { label -> accountId = finance.accounts.firstOrNull { it.name == label }?.id.orEmpty() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ativa", modifier = Modifier.weight(1f))
            Switch(checked = active, onCheckedChange = { active = it })
        }
    }
}

@Composable
internal fun BillEditorDialog(
    finance: FinanceState,
    initial: BillRecord?,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String, String, String) -> Unit
) {
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: "expense") }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var amount by remember(initial?.id) { mutableStateOf(initial?.amount?.toString().orEmpty()) }
    var dueDate by remember(initial?.id) { mutableStateOf(initial?.dueDate ?: LocalDate.now().toString()) }
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "outros") }
    var accountId by remember(initial?.id) { mutableStateOf(initial?.accountId.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }

    EditorDialog(if (initial == null) "Nova conta prevista" else "Editar conta prevista", onDismiss, {
        val value = editorNumber(amount)
        if (description.isNotBlank() && value > 0 && runCatching { LocalDate.parse(dueDate) }.isSuccess) {
            onSave(type, description.trim(), value, dueDate, category, accountId, notes.trim())
        }
    }) {
        ChoiceField("Tipo", if (type == "expense") "A pagar" else "A receber", listOf("A pagar", "A receber")) {
            type = if (it == "A pagar") "expense" else "income"
        }
        EditorText("Descrição", description) { description = it }
        EditorNumber("Valor", amount) { amount = it }
        EditorText("Vencimento (AAAA-MM-DD)", dueDate) { dueDate = it }
        val cats = categoryOptions(finance)
        ChoiceField("Categoria", cats.firstOrNull { it.first == category }?.second ?: category, cats.map { it.second }) { label ->
            category = cats.firstOrNull { it.second == label }?.first ?: "outros"
        }
        if (finance.accounts.isNotEmpty()) {
            ChoiceField(
                "Conta",
                finance.accounts.firstOrNull { it.id == accountId }?.name ?: "Sem conta",
                listOf("Sem conta") + finance.accounts.map { it.name }
            ) { label -> accountId = finance.accounts.firstOrNull { it.name == label }?.id.orEmpty() }
        }
        EditorText("Observação", notes) { notes = it }
        if (initial?.paid == true) {
            Text("Esta conta já foi baixada. A edição não apaga o lançamento realizado.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun TransferEditorDialog(
    finance: FinanceState,
    initial: TransferRecord?,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String) -> Unit
) {
    var from by remember(initial?.id) { mutableStateOf(initial?.fromAccountId ?: finance.accounts.firstOrNull()?.id.orEmpty()) }
    var to by remember(initial?.id) { mutableStateOf(initial?.toAccountId ?: finance.accounts.drop(1).firstOrNull()?.id.orEmpty()) }
    var amount by remember(initial?.id) { mutableStateOf(initial?.amount?.toString().orEmpty()) }
    var date by remember(initial?.id) { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }

    EditorDialog(if (initial == null) "Nova transferência" else "Editar transferência", onDismiss, {
        val value = editorNumber(amount)
        if (from.isNotBlank() && to.isNotBlank() && from != to && value > 0 && runCatching { LocalDate.parse(date) }.isSuccess) {
            onSave(from, to, value, date, notes.trim())
        }
    }) {
        ChoiceField("Origem", finance.accounts.firstOrNull { it.id == from }?.name ?: "Selecione", finance.accounts.map { it.name }) { label ->
            from = finance.accounts.firstOrNull { it.name == label }?.id.orEmpty()
        }
        ChoiceField("Destino", finance.accounts.firstOrNull { it.id == to }?.name ?: "Selecione", finance.accounts.map { it.name }) { label ->
            to = finance.accounts.firstOrNull { it.name == label }?.id.orEmpty()
        }
        EditorNumber("Valor", amount) { amount = it }
        EditorText("Data (AAAA-MM-DD)", date) { date = it }
        EditorText("Observação", notes) { notes = it }
    }
}

@Composable
internal fun BudgetEditorDialog(
    finance: FinanceState,
    initial: BudgetRecord?,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    val options = categoryOptions(finance)
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: options.firstOrNull()?.first ?: "outros") }
    var amount by remember(initial?.id) { mutableStateOf(initial?.amount?.toString().orEmpty()) }
    EditorDialog(if (initial == null) "Novo orçamento" else "Editar orçamento", onDismiss, {
        val value = editorNumber(amount)
        if (value > 0) onSave(category, value)
    }) {
        ChoiceField("Categoria", options.firstOrNull { it.first == category }?.second ?: category, options.map { it.second }) { label ->
            category = options.firstOrNull { it.second == label }?.first ?: "outros"
        }
        EditorNumber("Limite mensal", amount) { amount = it }
    }
}

@Composable
internal fun GoalEditorDialog(
    initial: GoalRecord?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, String, String) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var target by remember(initial?.id) { mutableStateOf(initial?.target?.toString().orEmpty()) }
    var current by remember(initial?.id) { mutableStateOf((initial?.current ?: 0.0).toString()) }
    var deadline by remember(initial?.id) { mutableStateOf(initial?.deadline.orEmpty()) }
    var icon by remember(initial?.id) { mutableStateOf(initial?.icon ?: "🎯") }
    EditorDialog(if (initial == null) "Nova meta" else "Editar meta", onDismiss, {
        val targetValue = editorNumber(target)
        val currentValue = editorNumber(current)
        if (name.isNotBlank() && targetValue > 0 && currentValue >= 0) {
            onSave(name.trim(), targetValue, currentValue, deadline.trim(), icon)
        }
    }) {
        EditorText("Nome", name) { name = it }
        EditorNumber("Objetivo", target) { target = it }
        EditorNumber("Valor atual", current) { current = it }
        EditorText("Prazo (AAAA-MM-DD)", deadline) { deadline = it }
        EditorText("Ícone", icon) { icon = it }
    }
}

@Composable
internal fun CategoryEditorDialog(
    initial: CategoryRecord?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    val groups = listOf(
        "essential" to "Essencial",
        "wants" to "Desejos",
        "future" to "Futuro",
        "income" to "Receita"
    )
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var icon by remember(initial?.id) { mutableStateOf(initial?.icon ?: "📦") }
    var group by remember(initial?.id) { mutableStateOf(initial?.group ?: "essential") }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: "#8d99ae") }
    EditorDialog(if (initial == null) "Nova categoria" else "Editar categoria", onDismiss, {
        if (name.isNotBlank()) onSave(name.trim(), icon, group, normalizeHexColor(color, "#8d99ae"))
    }) {
        EditorText("Nome", name) { name = it }
        EditorText("Ícone", icon) { icon = it }
        EditorText("Cor (#RRGGBB)", color) { color = it }
        ChoiceField("Grupo", groups.first { it.first == group }.second, groups.map { it.second }) { label ->
            group = groups.first { it.second == label }.first
        }
    }
}

@Composable
internal fun CardEditorDialog(
    finance: FinanceState,
    initial: CardRecord?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Int, Int, String, String) -> Unit
) {
    val types = listOf(
        "credit" to "Cartão de crédito",
        "meal" to "Vale-refeição",
        "food" to "Vale-alimentação",
        "fuel" to "Vale-combustível",
        "benefit" to "Outro benefício"
    )
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var type by remember(initial?.id) { mutableStateOf(initial?.cardType ?: "credit") }
    var brand by remember(initial?.id) { mutableStateOf(initial?.brand ?: "Visa") }
    var limit by remember(initial?.id) { mutableStateOf(initial?.limit?.toString().orEmpty()) }
    var closing by remember(initial?.id) { mutableStateOf((initial?.closingDay ?: 3).toString()) }
    var due by remember(initial?.id) { mutableStateOf((initial?.dueDay ?: 10).toString()) }
    var accountId by remember(initial?.id) { mutableStateOf(initial?.accountId.orEmpty()) }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: "#141b34") }

    LaunchedEffect(type) {
        if (type != "credit") accountId = ""
    }

    EditorDialog(if (initial == null) "Novo cartão/benefício" else "Editar cartão/benefício", onDismiss, {
        val value = editorNumber(limit)
        if (name.isNotBlank() && value >= 0) {
            onSave(
                name.trim(),
                type,
                brand.trim(),
                value,
                closing.toIntOrNull()?.coerceIn(1, 31) ?: 3,
                due.toIntOrNull()?.coerceIn(1, 31) ?: 10,
                accountId,
                normalizeHexColor(color, "#141b34")
            )
        }
    }) {
        EditorText("Nome", name) { name = it }
        ChoiceField("Tipo", types.first { it.first == type }.second, types.map { it.second }) { label ->
            type = types.first { it.second == label }.first
        }
        EditorText(if (type == "credit") "Bandeira" else "Emissor", brand) { brand = it }
        EditorNumber(if (type == "credit") "Limite" else "Saldo/crédito", limit) { limit = it }
        EditorText("Cor (#RRGGBB)", color) { color = it }
        if (type == "credit") {
            if (finance.accounts.isNotEmpty()) {
                ChoiceField(
                    "Conta vinculada",
                    finance.accounts.firstOrNull { it.id == accountId }?.name ?: "Sem conta",
                    listOf("Sem conta") + finance.accounts.map { it.name }
                ) { label ->
                    accountId = finance.accounts.firstOrNull { it.name == label }?.id.orEmpty()
                }
            }
            EditorNumber("Dia do fechamento", closing) { closing = it }
            EditorNumber("Dia do vencimento", due) { due = it }
        }
    }
}

@Composable
internal fun ShoppingListEditorDialog(
    initial: ShoppingListRecord?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var store by remember(initial?.id) { mutableStateOf(initial?.store.orEmpty()) }
    EditorDialog(if (initial == null) "Nova lista" else "Editar lista", onDismiss, {
        if (name.isNotBlank()) onSave(name.trim(), store.trim())
    }) {
        EditorText("Nome da lista", name) { name = it }
        EditorText("Mercado/local", store) { store = it }
    }
}

@Composable
internal fun ShoppingItemEditorDialog(
    initial: ShoppingItemRecord?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double) -> Unit
) {
    var product by remember(initial?.id) { mutableStateOf(initial?.product.orEmpty()) }
    var qty by remember(initial?.id) { mutableStateOf((initial?.qty ?: 1.0).toString()) }
    var price by remember(initial?.id) { mutableStateOf((initial?.unitPrice ?: 0.0).toString()) }
    EditorDialog(if (initial == null) "Adicionar produto" else "Editar produto", onDismiss, {
        val q = editorNumber(qty)
        val p = editorNumber(price)
        if (product.isNotBlank() && q > 0 && p >= 0) onSave(product.trim(), q, p)
    }) {
        EditorText("Produto", product) { product = it }
        EditorNumber("Quantidade", qty) { qty = it }
        EditorNumber("Valor unitário", price) { price = it }
    }
}

@Composable
private fun EditorDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    fields: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        fields()
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EditorText(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun EditorNumber(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

private fun normalizeHexColor(value: String, fallback: String): String {
    val clean = value.trim()
    return if (Regex("^#[0-9A-Fa-f]{6}$").matches(clean)) clean else fallback
}

private fun editorNumber(value: String): Double =
    value.trim().replace(",", ".").toDoubleOrNull() ?: 0.0

private fun paymentUsesCardEditor(payment: String): Boolean =
    payment in setOf(
        "Cartão de crédito",
        "Vale-refeição",
        "Vale-alimentação",
        "Vale-combustível"
    )

private fun editorCardsForPayment(cards: List<CardRecord>, payment: String): List<CardRecord> {
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
