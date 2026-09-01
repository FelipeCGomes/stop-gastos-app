package com.example.stop_fgastos.ui

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stop_fgastos.auth.GoogleAuthManager
import com.example.stop_fgastos.model.CardRecord
import com.example.stop_fgastos.model.FinanceState
import com.example.stop_fgastos.model.RecurringRecord
import com.example.stop_fgastos.model.TransactionRecord
import com.example.stop_fgastos.util.FinanceCalculator
import com.example.stop_fgastos.viewmodel.MainUiState
import com.example.stop_fgastos.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private enum class AppTab(val label: String, val glyph: String) {
    DASHBOARD("Início", "⌂"),
    TRANSACTIONS("Lançamentos", "↕"),
    PLANNING("Planejar", "◎"),
    WALLET("Carteira", "▣"),
    MORE("Mais", "☰")
}

@Composable
fun StopGastosApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()

    if (!state.signedIn) {
        LoginScreen(viewModel)
        return
    }

    var tab by remember { mutableStateOf(AppTab.DASHBOARD) }

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) {
            viewModel.ensureRecurringForMonth(YearMonth.now())
        }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Stop Gastos", fontWeight = FontWeight.Bold)
                        Text(
                            state.userName.ifBlank { state.userEmail },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.family.invitations.isNotEmpty()) {
                            TextButton(onClick = { tab = AppTab.MORE }) {
                                Text("🔔 " + state.family.invitations.size)
                            }
                        }
                        Text(
                            state.syncMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.syncMessage == "Erro") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.glyph) },
                        label = { Text(item.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Crossfade(
                targetState = tab,
                label = "main_navigation"
            ) { currentTab ->
                when (currentTab) {
                    AppTab.DASHBOARD -> DashboardScreen(state)
                    AppTab.TRANSACTIONS -> TransactionsScreen(state.finance, viewModel)
                    AppTab.PLANNING -> PlanningHubScreen(state.finance, viewModel)
                    AppTab.WALLET -> WalletHubScreen(state.finance, viewModel)
                    AppTab.MORE -> MoreHubScreen(state, viewModel)
                }
            }

            if (state.loading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Sincronizando seus dados…")
                    }
                }
            }
        }
    }

    if (state.error.isNotBlank()) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            },
            title = { Text("Não foi possível concluir") },
            text = { Text(state.error) }
        )
    }
}

@Composable
private fun LoginScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val configured = remember { GoogleAuthManager.isConfigured(activity) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Stop Gastos",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                "Controle financeiro pessoal e familiar — agora em Android nativo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        message = ""
                        runCatching { GoogleAuthManager.signIn(activity) }
                            .onSuccess(viewModel::onSignedIn)
                            .onFailure { message = it.message ?: "Falha no login Google." }
                        loading = false
                    }
                },
                enabled = !loading && configured,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Entrar com Google")
                }
            }

            if (!configured) {
                Spacer(Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        "Falta apenas registrar o app Android no Firebase e informar o Web Client ID em default_web_client_id. O restante da integração nativa já está preparado.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DashboardScreen(state: MainUiState) {
    val month = YearMonth.now().toString()
    val current = remember(state.finance.transactions, month) {
        state.finance.transactions.filter { it.date.startsWith(month) }
    }
    val income = FinanceCalculator.sum(current.filter { it.type == "income" }.map { it.amount })
    val expense = FinanceCalculator.sum(current.filter { it.type == "expense" }.map { it.amount })
    val balance = FinanceCalculator.balance(income, expense)
    val recent = current.sortedByDescending { it.date }.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Resumo de " + monthLabel(YearMonth.now()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Entradas", money(income), Modifier.weight(1f))
                SummaryCard("Saídas", money(expense), Modifier.weight(1f))
            }
        }

        item {
            SummaryCard(
                "Saldo do mês",
                money(balance),
                Modifier.fillMaxWidth(),
                emphasized = true
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(
                    "Custos fixos",
                    state.finance.recurring.count { it.active }.toString(),
                    Modifier.weight(1f)
                )
                SummaryCard(
                    "Cartões",
                    state.finance.cards.size.toString(),
                    Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                "Últimos lançamentos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (recent.isEmpty()) {
            item { EmptyCard("Nenhum lançamento neste mês.") }
        } else {
            items(recent, key = { it.id }) { transaction ->
                TransactionCard(transaction = transaction, onEdit = null, onDelete = null)
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier,
    emphasized: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TransactionsScreen(finance: FinanceState, viewModel: MainViewModel) {
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TransactionRecord?>(null) }

    val records = finance.transactions.sortedWith(
        compareByDescending<TransactionRecord> { it.date }
            .thenByDescending { it.updatedAt }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                title = "Lançamentos",
                subtitle = records.size.toString() + " registros",
                action = "Novo",
                onAction = {
                    editing = null
                    editorOpen = true
                }
            )
        }

        if (records.isEmpty()) {
            item { EmptyCard("Cadastre sua primeira receita ou despesa.") }
        } else {
            items(records, key = { it.id }) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    onEdit = {
                        editing = transaction
                        editorOpen = true
                    },
                    onDelete = { viewModel.deleteTransaction(transaction) }
                )
            }
        }
    }

    if (editorOpen) {
        TransactionEditorDialog(
            finance = finance,
            initial = editing,
            onDismiss = {
                editorOpen = false
                editing = null
            },
            onSave = {
                type,
                description,
                amount,
                date,
                category,
                payment,
                cardId,
                installments,
                accountId,
                tags,
                notes ->

                val current = editing
                if (current == null) {
                    viewModel.addTransaction(
                        type = type,
                        description = description,
                        total = amount,
                        purchaseDate = date,
                        category = category,
                        payment = payment,
                        cardId = cardId,
                        installmentCount = installments,
                        accountId = accountId,
                        tags = tags,
                        notes = notes
                    )
                } else {
                    viewModel.updateTransaction(
                        existing = current,
                        type = type,
                        description = description,
                        total = amount,
                        purchaseDate = date,
                        category = category,
                        payment = payment,
                        cardId = cardId,
                        installmentCount = installments,
                        accountId = accountId,
                        tags = tags,
                        notes = notes
                    )
                }

                editorOpen = false
                editing = null
            }
        )
    }
}

@Composable
private fun TransactionCard(
    transaction: TransactionRecord,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.description,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(transaction.date)
                        if (transaction.payment.isNotBlank()) append(" · " + transaction.payment)
                        if (transaction.installmentCount > 1) {
                            append(" · " + transaction.installmentNo + "/" + transaction.installmentCount)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transaction.tags.isNotBlank() || transaction.notes.isNotBlank()) {
                    Text(
                        listOf(transaction.tags, transaction.notes)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (transaction.type == "expense") "- " else "+ ") +
                        money(transaction.amount),
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == "expense") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Row {
                    if (onEdit != null) {
                        TextButton(onClick = onEdit) { Text("Editar") }
                    }
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FixedCostsScreen(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    val records = finance.recurring.filter { it.type != "income" }.sortedBy { it.day }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                "Custos fixos",
                "Despesas recorrentes",
                "Novo",
                onAction = { showAdd = true }
            )
        }

        if (records.isEmpty()) {
            item { EmptyCard("Nenhum custo fixo cadastrado.") }
        } else {
            items(records, key = { it.id }) { record ->
                RecurringCard(record) { viewModel.deleteRecurring(record) }
            }
        }
    }

    if (showAdd) {
        AddFixedCostDialog(
            cards = finance.cards,
            onDismiss = { showAdd = false },
            onSave = { description, amount, day, category, payment, cardId, installments ->
                viewModel.addFixedCost(
                    description,
                    amount,
                    day,
                    category,
                    payment,
                    cardId,
                    installments
                )
                showAdd = false
            }
        )
    }
}

@Composable
private fun RecurringCard(record: RecurringRecord, onDelete: () -> Unit) {
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(record.description, fontWeight = FontWeight.SemiBold)
                Text(
                    "Dia " + record.day + " · " + record.payment +
                        if (record.installmentCount > 1) " · " + record.installmentCount + "x" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(record.amount), fontWeight = FontWeight.Bold)
                TextButton(onClick = onDelete) { Text("Excluir") }
            }
        }
    }
}

@Composable
private fun CardsScreen(finance: FinanceState, viewModel: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                "Cartões e benefícios",
                finance.cards.size.toString() + " cadastrados",
                "Novo",
                onAction = { showAdd = true }
            )
        }

        if (finance.cards.isEmpty()) {
            item { EmptyCard("Cadastre cartão de crédito, VR, VA ou vale-combustível.") }
        } else {
            items(finance.cards, key = { it.id }) { card ->
                CardItem(card) { viewModel.deleteCard(card) }
            }
        }
    }

    if (showAdd) {
        AddCardDialog(
            onDismiss = { showAdd = false },
            onSave = { name, type, brand, limit, closingDay, dueDay ->
                viewModel.addCard(name, type, brand, limit, closingDay, dueDay)
                showAdd = false
            }
        )
    }
}

@Composable
private fun CardItem(card: CardRecord, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        cardTypeLabel(card.cardType) +
                            if (card.brand.isNotBlank()) " · " + card.brand else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDelete) { Text("Excluir") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (card.cardType == "credit") "Limite: " + money(card.limit)
                else "Saldo/crédito: " + money(card.limit),
                fontWeight = FontWeight.SemiBold
            )

            if (card.cardType == "credit") {
                Text(
                    "Fecha dia " + card.closingDay + " · vence dia " + card.dueDay,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: MainUiState, viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Configurações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            InfoCard("Conta", state.userName.ifBlank { "Conta Google" }, state.userEmail)
        }
        item {
            InfoCard("Firebase", "Projeto stopgastos", "Firestore modular compatível com a versão web")
        }
        item {
            InfoCard("Sincronização", state.syncMessage, "users/{uid}/data/{sectionId}")
        }
        item {
            InfoCard(
                "App nativo",
                "Kotlin + Java + Jetpack Compose",
                "Sem WebView, HTML, CSS ou JavaScript na interface Android"
            )
        }
        item {
            OutlinedButton(
                onClick = viewModel::signOut,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sair da conta")
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, detail: String) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card {
        Text(
            message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddTransactionDialog(
    cards: List<CardRecord>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String, String, String, Int) -> Unit
) {
    var type by remember { mutableStateOf("expense") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember { mutableStateOf("outros") }
    var payment by remember { mutableStateOf("Pix") }
    var cardId by remember { mutableStateOf("") }
    var installments by remember { mutableStateOf("1") }

    val matchingCards = cardsForPayment(cards, payment)
    LaunchedEffect(payment, cards) {
        if (cardId.isBlank() && matchingCards.isNotEmpty()) {
            cardId = matchingCards.first().id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo lançamento") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = type == "expense",
                            onClick = { type = "expense" },
                            label = { Text("Despesa") }
                        )
                        FilterChip(
                            selected = type == "income",
                            onClick = {
                                type = "income"
                                payment = "Pix"
                                cardId = ""
                                installments = "1"
                            },
                            label = { Text("Receita") }
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descrição") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Valor") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Data (AAAA-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Categoria") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (type == "expense") {
                    item {
                        DropdownSelector(
                            label = "Pagamento",
                            value = payment,
                            options = paymentOptions(),
                            onSelect = {
                                payment = it
                                cardId = ""
                                installments = "1"
                            }
                        )
                    }
                    if (usesCard(payment)) {
                        item {
                            DropdownSelector(
                                label = "Cartão/benefício",
                                value = matchingCards.firstOrNull { it.id == cardId }?.name ?: "Selecione",
                                options = matchingCards.map { it.name },
                                onSelect = { name ->
                                    cardId = matchingCards.firstOrNull { it.name == name }?.id.orEmpty()
                                }
                            )
                        }
                    }
                    if (payment == "Cartão de crédito") {
                        item {
                            OutlinedTextField(
                                value = installments,
                                onValueChange = { installments = it },
                                label = { Text("Parcelas") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
                    val count = installments.toIntOrNull()?.coerceIn(1, 60) ?: 1
                    if (description.isNotBlank() && parsed > 0 && runCatching { LocalDate.parse(date) }.isSuccess) {
                        onSave(type, description.trim(), parsed, date, category.trim(), payment, cardId, count)
                    }
                }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AddFixedCostDialog(
    cards: List<CardRecord>,
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

    val matchingCards = cardsForPayment(cards, payment)
    if (cardId.isBlank() && matchingCards.isNotEmpty()) cardId = matchingCards.first().id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo custo fixo") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        description,
                        { description = it },
                        label = { Text("Descrição") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        amount,
                        { amount = it },
                        label = { Text("Valor total") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        day,
                        { day = it },
                        label = { Text("Dia") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        category,
                        { category = it },
                        label = { Text("Categoria") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    DropdownSelector(
                        "Pagamento",
                        payment,
                        paymentOptions(),
                        onSelect = {
                            payment = it
                            cardId = ""
                            installments = "1"
                        }
                    )
                }
                if (usesCard(payment)) {
                    item {
                        DropdownSelector(
                            "Cartão/benefício",
                            matchingCards.firstOrNull { it.id == cardId }?.name ?: "Selecione",
                            matchingCards.map { it.name },
                            onSelect = { name ->
                                cardId = matchingCards.firstOrNull { it.name == name }?.id.orEmpty()
                            }
                        )
                    }
                }
                if (payment == "Cartão de crédito") {
                    item {
                        OutlinedTextField(
                            installments,
                            { installments = it },
                            label = { Text("Parcelas") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
                val parsedDay = day.toIntOrNull()?.coerceIn(1, 31) ?: 1
                val count = installments.toIntOrNull()?.coerceIn(1, 60) ?: 1
                if (description.isNotBlank() && parsed > 0) {
                    onSave(description.trim(), parsed, parsedDay, category.trim(), payment, cardId, count)
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AddCardDialog(
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo cartão/benefício") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text("Nome") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    DropdownSelector(
                        "Tipo",
                        types.first { it.first == type }.second,
                        types.map { it.second },
                        onSelect = { label ->
                            type = types.first { it.second == label }.first
                        }
                    )
                }
                item {
                    OutlinedTextField(
                        brand,
                        { brand = it },
                        label = { Text(if (type == "credit") "Bandeira" else "Emissor") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        limit,
                        { limit = it },
                        label = { Text(if (type == "credit") "Limite" else "Saldo/crédito") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (type == "credit") {
                    item {
                        OutlinedTextField(
                            closing,
                            { closing = it },
                            label = { Text("Dia do fechamento") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            due,
                            { due = it },
                            label = { Text("Dia do vencimento") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedLimit = limit.replace(",", ".").toDoubleOrNull() ?: 0.0
                if (name.isNotBlank() && parsedLimit >= 0) {
                    onSave(
                        name.trim(),
                        type,
                        brand.trim(),
                        parsedLimit,
                        closing.toIntOrNull()?.coerceIn(1, 31) ?: 3,
                        due.toIntOrNull()?.coerceIn(1, 31) ?: 10
                    )
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DropdownSelector(
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
                Text(value.ifBlank { "Selecione" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
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

private fun paymentOptions() = listOf(
    "Pix",
    "Débito",
    "Dinheiro",
    "Cartão de crédito",
    "Vale-refeição",
    "Vale-alimentação",
    "Vale-combustível"
)

private fun usesCard(payment: String): Boolean =
    payment in setOf(
        "Cartão de crédito",
        "Vale-refeição",
        "Vale-alimentação",
        "Vale-combustível"
    )

private fun cardsForPayment(cards: List<CardRecord>, payment: String): List<CardRecord> {
    val type = when (payment) {
        "Cartão de crédito" -> "credit"
        "Vale-refeição" -> "meal"
        "Vale-alimentação" -> "food"
        "Vale-combustível" -> "fuel"
        else -> ""
    }
    if (type.isBlank()) return emptyList()
    return cards.filter { card ->
        if (type == "credit") card.cardType == "credit"
        else card.cardType == type || card.cardType == "benefit"
    }
}

private fun cardTypeLabel(type: String): String = when (type) {
    "meal" -> "Vale-refeição"
    "food" -> "Vale-alimentação"
    "fuel" -> "Vale-combustível"
    "benefit" -> "Outro benefício"
    else -> "Cartão de crédito"
}

private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

private fun monthLabel(month: YearMonth): String {
    val names = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
    )
    return names[month.monthValue - 1] + "/" + month.year
}