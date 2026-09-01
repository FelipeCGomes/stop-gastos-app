package com.example.stop_fgastos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stop_fgastos.model.AccountRecord
import com.example.stop_fgastos.model.BillRecord
import com.example.stop_fgastos.model.CategoryRecord
import com.example.stop_fgastos.model.FinanceState
import com.example.stop_fgastos.viewmodel.MainUiState
import com.example.stop_fgastos.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.YearMonth

private enum class PlanningSection(val label: String) {
    FIXED("Fixos"), INCOME("Rendas"), BILLS("Contas"), BUDGETS("Orçamentos"), GOALS("Metas")
}

private enum class WalletSection(val label: String) {
    ACCOUNTS("Contas"), CARDS("Cartões"), TRANSFERS("Transferências")
}

private enum class MoreSection(val label: String) {
    CATEGORIES("Categorias"), SHOPPING("Compras"), SETTINGS("Conta")
}

@Composable
fun PlanningHubScreen(finance: FinanceState, viewModel: MainViewModel) {
    var section by remember { mutableStateOf(PlanningSection.FIXED) }
    Column(Modifier.fillMaxSize()) {
        HubTabs(PlanningSection.entries.map { it.label }, section.label) { label ->
            section = PlanningSection.entries.first { it.label == label }
        }
        when (section) {
            PlanningSection.FIXED -> NativeFixedCosts(finance, viewModel)
            PlanningSection.INCOME -> IncomeSourcesScreen(finance, viewModel)
            PlanningSection.BILLS -> BillsScreen(finance, viewModel)
            PlanningSection.BUDGETS -> BudgetsScreen(finance, viewModel)
            PlanningSection.GOALS -> GoalsScreen(finance, viewModel)
        }
    }
}

@Composable
fun WalletHubScreen(finance: FinanceState, viewModel: MainViewModel) {
    var section by remember { mutableStateOf(WalletSection.ACCOUNTS) }
    Column(Modifier.fillMaxSize()) {
        HubTabs(WalletSection.entries.map { it.label }, section.label) { label ->
            section = WalletSection.entries.first { it.label == label }
        }
        when (section) {
            WalletSection.ACCOUNTS -> AccountsScreen(finance, viewModel)
            WalletSection.CARDS -> NativeCards(finance, viewModel)
            WalletSection.TRANSFERS -> TransfersScreen(finance, viewModel)
        }
    }
}

@Composable
fun MoreHubScreen(state: MainUiState, viewModel: MainViewModel) {
    var section by remember { mutableStateOf(MoreSection.CATEGORIES) }
    Column(Modifier.fillMaxSize()) {
        HubTabs(MoreSection.entries.map { it.label }, section.label) { label ->
            section = MoreSection.entries.first { it.label == label }
        }
        when (section) {
            MoreSection.CATEGORIES -> CategoriesScreen(state.finance, viewModel)
            MoreSection.SHOPPING -> ShoppingScreen(state.finance, viewModel)
            MoreSection.SETTINGS -> NativeAccountSettings(state, viewModel)
        }
    }
}

@Composable
private fun HubTabs(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { label ->
            FilterChip(selected = label == selected, onClick = { onSelect(label) }, label = { Text(label) })
        }
    }
}

@Composable
private fun NativeFixedCosts(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    val records = finance.recurring.filter { it.type != "income" }.sortedBy { it.day }
    ModuleList("Custos fixos", "Despesas recorrentes e assinaturas", "Novo", { showAdd = true }) {
        if (records.isEmpty()) item { HubEmpty("Nenhum custo fixo cadastrado.") }
        else items(records, key = { it.id }) { record ->
            SimpleRecordCard(
                record.description,
                "Dia ${record.day} · ${record.payment}" + if (record.installmentCount > 1) " · ${record.installmentCount}x" else "",
                extendedMoney(record.amount)
            ) { viewModel.deleteRecurring(record) }
        }
    }
    if (showAdd) {
        FixedCostDialog(finance, { showAdd = false }) { description, amount, day, category, payment, cardId, installments ->
            viewModel.addFixedCost(description, amount, day, category, payment, cardId, installments)
            showAdd = false
        }
    }
}

@Composable
private fun IncomeSourcesScreen(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    ModuleList("Rendas recorrentes", "Salário, renda extra, freelance e outras entradas", "Nova", { showAdd = true }) {
        if (finance.incomeSources.isEmpty()) item { HubEmpty("Nenhuma renda recorrente cadastrada.") }
        else items(finance.incomeSources.sortedBy { it.day }, key = { it.id }) { source ->
            val account = finance.accounts.firstOrNull { it.id == source.accountId }?.name
            SimpleRecordCard(
                source.description,
                "${incomeKindLabel(source.kind)} · dia ${source.day}" + if (!account.isNullOrBlank()) " · $account" else "",
                "+ " + extendedMoney(source.amount)
            ) { viewModel.deleteIncomeSource(source) }
        }
    }
    if (showAdd) {
        IncomeSourceDialog(finance, { showAdd = false }) { kind, description, amount, day, accountId ->
            viewModel.addIncomeSource(kind, description, amount, day, accountId)
            showAdd = false
        }
    }
}

@Composable
private fun BillsScreen(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    val bills = finance.bills.sortedWith(compareBy<BillRecord> { it.paid }.thenBy { it.dueDate })
    ModuleList("Contas a pagar/receber", "Agenda financeira com baixa no caixa", "Nova", { showAdd = true }) {
        if (bills.isEmpty()) item { HubEmpty("Nenhuma conta prevista.") }
        else items(bills, key = { it.id }) { bill ->
            val status = when {
                bill.paid -> "Concluído"
                bill.dueDate < LocalDate.now().toString() -> "Vencido"
                else -> "Pendente"
            }
            Card {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(bill.description, fontWeight = FontWeight.SemiBold)
                            Text("${bill.dueDate} · $status", style = MaterialTheme.typography.bodySmall)
                        }
                        Text((if (bill.type == "expense") "- " else "+ ") + extendedMoney(bill.amount), fontWeight = FontWeight.Bold)
                    }
                    Row {
                        if (!bill.paid) TextButton(onClick = { viewModel.payBill(bill) }) { Text("Marcar pago") }
                        TextButton(onClick = { viewModel.deleteBill(bill) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
    if (showAdd) {
        BillDialog(finance, { showAdd = false }) { type, description, amount, dueDate, category, accountId, notes ->
            viewModel.addBill(type, description, amount, dueDate, category, accountId, notes)
            showAdd = false
        }
    }
}

@Composable
private fun BudgetsScreen(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    val month = YearMonth.now().toString()
    ModuleList("Orçamentos", "Limites mensais por categoria", "Novo", { showAdd = true }) {
        if (finance.budgets.isEmpty()) item { HubEmpty("Nenhum orçamento cadastrado.") }
        else items(finance.budgets, key = { it.id }) { budget ->
            val spent = finance.transactions.filter {
                it.type == "expense" && it.category == budget.category && it.date.startsWith(month)
            }.sumOf { it.amount }
            val progress = if (budget.amount > 0) (spent / budget.amount).coerceIn(0.0, 1.0) else 0.0
            Card {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(categoryLabel(finance, budget.category), fontWeight = FontWeight.SemiBold)
                            Text("${extendedMoney(spent)} de ${extendedMoney(budget.amount)}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { viewModel.deleteBudget(budget) }) { Text("Excluir") }
                    }
                    LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (showAdd) {
        BudgetDialog(finance, { showAdd = false }) { category, amount ->
            viewModel.addBudget(category, amount)
            showAdd = false
        }
    }
}

@Composable
private fun GoalsScreen(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    ModuleList("Metas financeiras", "Objetivos, progresso e prazo", "Nova", { showAdd = true }) {
        if (finance.goals.isEmpty()) item { HubEmpty("Nenhuma meta cadastrada.") }
        else items(finance.goals, key = { it.id }) { goal ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.icon)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(goal.name, fontWeight = FontWeight.SemiBold)
                            Text("${extendedMoney(goal.current)} de ${extendedMoney(goal.target)}" +
                                if (goal.deadline.isNotBlank()) " · ${goal.deadline}" else "",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { viewModel.deleteGoal(goal) }) { Text("Excluir") }
                    }
                    LinearProgressIndicator(progress = { goal.progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (showAdd) {
        GoalDialog({ showAdd = false }) { name, target, current, deadline, icon ->
            viewModel.addGoal(name, target, current, deadline, icon)
            showAdd = false
        }
    }
}

@Composable
private fun AccountsScreen(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    ModuleList("Contas e carteiras", "Saldo individual e consolidado", "Nova", { showAdd = true }) {
        if (finance.accounts.isEmpty()) item { HubEmpty("Cadastre sua conta bancária ou carteira.") }
        else {
            item { HubSummary("Saldo consolidado", extendedMoney(finance.accounts.sumOf { accountBalance(finance, it) })) }
            items(finance.accounts, key = { it.id }) { account ->
                SimpleRecordCard("${account.icon} ${account.name}", account.type, extendedMoney(accountBalance(finance, account))) {
                    viewModel.deleteAccount(account)
                }
            }
        }
    }
    if (showAdd) {
        AccountDialog({ showAdd = false }) { name, type, opening, icon ->
            viewModel.addAccount(name, type, opening, icon)
            showAdd = false
        }
    }
}

@Composable
private fun NativeCards(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    val month = YearMonth.now().toString()
    ModuleList("Cartões e benefícios", "Crédito, VR, VA e vale-combustível", "Novo", { showAdd = true }) {
        if (finance.cards.isEmpty()) item { HubEmpty("Nenhum cartão ou benefício cadastrado.") }
        else items(finance.cards, key = { it.id }) { card ->
            val used = finance.transactions.filter { tx ->
                tx.cardId == card.id && tx.type == "expense" &&
                    if (card.isBenefit) tx.purchaseDate.startsWith(month) else tx.invoiceMonth == month
            }.sumOf { it.amount }
            SimpleRecordCard(
                card.name,
                cardTypeLabelExtended(card.cardType) + if (card.cardType == "credit") " · fecha ${card.closingDay} · vence ${card.dueDay}" else "",
                "${extendedMoney(used)} / ${extendedMoney(card.limit)}"
            ) { viewModel.deleteCard(card) }
        }
    }
    if (showAdd) {
        CardDialogExtended({ showAdd = false }) { name, type, brand, limit, closing, due ->
            viewModel.addCard(name, type, brand, limit, closing, due)
            showAdd = false
        }
    }
}

@Composable
private fun TransfersScreen(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    ModuleList("Transferências", "Movimentação entre contas sem alterar receita/despesa", "Nova", { showAdd = true }) {
        if (finance.transfers.isEmpty()) item { HubEmpty("Nenhuma transferência registrada.") }
        else items(finance.transfers.sortedByDescending { it.date }, key = { it.id }) { transfer ->
            val from = finance.accounts.firstOrNull { it.id == transfer.fromAccountId }?.name ?: "Origem"
            val to = finance.accounts.firstOrNull { it.id == transfer.toAccountId }?.name ?: "Destino"
            SimpleRecordCard("$from → $to", transfer.date, extendedMoney(transfer.amount)) {
                viewModel.deleteTransfer(transfer)
            }
        }
    }
    if (showAdd) {
        TransferDialog(finance, { showAdd = false }) { from, to, amount, date, notes ->
            viewModel.addTransfer(from, to, amount, date, notes)
            showAdd = false
        }
    }
}

@Composable
private fun CategoriesScreen(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    val categories = if (finance.categories.isEmpty()) fallbackCategoryRecords() else finance.categories
    ModuleList("Categorias", "Organização de gastos, renda e planejamento", "Nova", { showAdd = true }) {
        items(categories, key = { it.id }) { category ->
            Card {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(category.icon)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(category.name, fontWeight = FontWeight.SemiBold)
                        Text(categoryGroupLabel(category.group), style = MaterialTheme.typography.bodySmall)
                    }
                    if (finance.categories.any { it.id == category.id }) {
                        TextButton(onClick = { viewModel.deleteCategory(category) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
    if (showAdd) {
        CategoryDialog({ showAdd = false }) { name, icon, group ->
            viewModel.addCategory(name, icon, group)
            showAdd = false
        }
    }
}

@Composable
private fun ShoppingScreen(finance: FinanceState, viewModel: MainViewModel) {
    var showListDialog by remember { mutableStateOf(false) }
    var showItemDialog by remember { mutableStateOf(false) }
    var activeListId by remember(finance.shoppingLists) { mutableStateOf(finance.shoppingLists.firstOrNull()?.id.orEmpty()) }

    LaunchedEffect(finance.shoppingLists) {
        if (finance.shoppingLists.none { it.id == activeListId }) activeListId = finance.shoppingLists.firstOrNull()?.id.orEmpty()
    }
    val active = finance.shoppingLists.firstOrNull { it.id == activeListId }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lista de compras", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Listas pessoais sincronizadas com o Firestore")
                }
                Button(onClick = { showListDialog = true }) { Text("Nova") }
            }
        }
        if (finance.shoppingLists.isNotEmpty()) {
            item {
                ChoiceField("Lista ativa", active?.name ?: "Selecione", finance.shoppingLists.map { it.name }) { label ->
                    activeListId = finance.shoppingLists.firstOrNull { it.name == label }?.id.orEmpty()
                }
            }
        }
        if (active == null) item { HubEmpty("Crie sua primeira lista de compras.") }
        else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(active.name, fontWeight = FontWeight.Bold)
                        Text("${active.store.ifBlank { "Sem mercado definido" }} · ${active.items.size} itens · ${extendedMoney(active.total)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { showItemDialog = true }) { Text("Item") }
                    TextButton(onClick = { viewModel.deleteShoppingList(active) }) { Text("Excluir") }
                }
            }
            if (active.items.isEmpty()) item { HubEmpty("Nenhum produto nesta lista.") }
            else items(active.items.sortedBy { it.order }, key = { it.id }) { item ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.product, fontWeight = FontWeight.SemiBold)
                            Text("${formatQty(item.qty)} × ${extendedMoney(item.unitPrice)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(extendedMoney(item.total), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { viewModel.deleteShoppingItem(active.id, item.id) }) { Text("×") }
                    }
                }
            }
        }
    }

    if (showListDialog) ShoppingListDialog({ showListDialog = false }) { name, store ->
        viewModel.addShoppingList(name, store); showListDialog = false
    }
    if (showItemDialog && active != null) ShoppingItemDialog({ showItemDialog = false }) { product, qty, price ->
        viewModel.addShoppingItem(active.id, product, qty, price); showItemDialog = false
    }
}

@Composable
private fun NativeAccountSettings(state: MainUiState, viewModel: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HubSummary("Conta Google", state.userName.ifBlank { state.userEmail }) }
        item { HubSummary("E-mail", state.userEmail) }
        item { HubSummary("Firebase", "stopgastos · ${state.syncMessage}") }
        item { HubSummary("Aplicativo", "Kotlin + Java · Android nativo") }
        item { OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) { Text("Sair da conta") } }
    }
}

@Composable
private fun ModuleList(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onAction) { Text(action) }
            }
        }
        content()
    }
}

@Composable
private fun SimpleRecordCard(title: String, subtitle: String, value: String, onDelete: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(value, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDelete) { Text("Excluir") }
            }
        }
    }
}

@Composable
private fun HubSummary(title: String, value: String) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HubEmpty(text: String) {
    Card { Text(text, Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

internal fun accountBalance(finance: FinanceState, account: AccountRecord): Double {
    var balance = account.openingBalance
    finance.transactions.forEach { tx ->
        if (tx.accountId == account.id) {
            if (tx.type == "income") balance += tx.amount
            if (tx.type == "expense") balance -= tx.amount
        }
    }
    finance.transfers.forEach { transfer ->
        if (transfer.fromAccountId == account.id) balance -= transfer.amount
        if (transfer.toAccountId == account.id) balance += transfer.amount
    }
    return balance
}
