package com.example.stop_fgastos.viewmodel

import androidx.lifecycle.ViewModel
import com.example.stop_fgastos.auth.GoogleAuthManager
import com.example.stop_fgastos.data.FinanceRepository
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
import com.example.stop_fgastos.util.FinanceCalculator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class MainUiState(
    val signedIn: Boolean = false,
    val loading: Boolean = true,
    val userName: String = "",
    val userEmail: String = "",
    val finance: FinanceState = FinanceState(),
    val syncMessage: String = "Local",
    val error: String = ""
)

class MainViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val repository = FinanceRepository()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        val user = auth.currentUser
        if (user != null) attachUser(user)
        else _uiState.value = MainUiState(loading = false)
    }

    fun onSignedIn(user: FirebaseUser) = attachUser(user)

    fun signOut() {
        repository.stop()
        GoogleAuthManager.signOut()
        _uiState.value = MainUiState(loading = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = "")
    }

    fun addTransaction(
        type: String,
        description: String,
        total: Double,
        purchaseDate: String,
        category: String,
        payment: String,
        cardId: String,
        installmentCount: Int
    ) {
        val records = buildTransactions(
            type = type,
            description = description,
            total = total,
            purchaseDate = purchaseDate,
            category = category,
            payment = payment,
            cardId = cardId,
            requestedInstallments = installmentCount
        )
        runWrite { callback -> repository.upsertTransactions(records, callback) }
    }

    fun addFixedCost(
        description: String,
        amount: Double,
        day: Int,
        category: String,
        payment: String,
        cardId: String,
        installmentCount: Int
    ) {
        val now = LocalDate.now()
        val safeDay = day.coerceIn(1, YearMonth.from(now).lengthOfMonth())
        val purchaseDate = now.withDayOfMonth(safeDay).toString()
        val id = "rec_" + UUID.randomUUID()
        val isCredit = payment == "Cartão de crédito"
        val effectiveCount = if (isCredit) installmentCount.coerceIn(1, 60) else 1
        val nowIso = nowIso()

        val recurring = RecurringRecord(
            id = id,
            description = description,
            amount = amount,
            day = day.coerceIn(1, 31),
            category = category,
            payment = payment,
            cardId = cardId,
            installmentCount = effectiveCount,
            installmentStartMonth = if (isCredit && effectiveCount > 1) purchaseDate.take(7) else "",
            active = true,
            updatedAt = nowIso
        )

        val records = buildTransactions(
            type = "expense",
            description = description,
            total = amount,
            purchaseDate = purchaseDate,
            category = category,
            payment = payment,
            cardId = cardId,
            requestedInstallments = effectiveCount,
            sourceRecurringId = id,
            sourceType = "recurringExpense"
        )

        runWrite { callback ->
            repository.saveRecurringWithTransactions(recurring, records, callback)
        }
    }

    fun addCard(
        name: String,
        cardType: String,
        brand: String,
        limit: Double,
        closingDay: Int,
        dueDay: Int
    ) {
        val defaultColor = when (cardType) {
            "meal" -> "#e97824"
            "food" -> "#16866f"
            "fuel" -> "#2f6fd6"
            "benefit" -> "#7357d8"
            else -> "#141b34"
        }

        val card = CardRecord(
            id = "card_" + UUID.randomUUID(),
            name = name,
            cardType = cardType,
            brand = brand,
            limit = limit,
            closingDay = closingDay.coerceIn(1, 31),
            dueDay = dueDay.coerceIn(1, 31),
            color = defaultColor
        )

        runWrite { callback -> repository.upsertCard(card, callback) }
    }

    fun addAccount(
        name: String,
        type: String,
        openingBalance: Double,
        icon: String
    ) {
        val account = AccountRecord(
            id = "acc_" + UUID.randomUUID(),
            name = name,
            type = type,
            openingBalance = openingBalance,
            icon = icon.ifBlank { "🏦" },
            updatedAt = nowIso()
        )
        runWrite { callback -> repository.upsertAccount(account, callback) }
    }

    fun addIncomeSource(
        kind: String,
        description: String,
        amount: Double,
        day: Int,
        accountId: String
    ) {
        val now = LocalDate.now()
        val month = YearMonth.from(now)
        val safeDay = day.coerceIn(1, month.lengthOfMonth())
        val date = month.atDay(safeDay).toString()
        val id = "inc_" + UUID.randomUUID()
        val timestamp = nowIso()

        val source = IncomeSourceRecord(
            id = id,
            kind = kind,
            description = description,
            amount = amount,
            day = day.coerceIn(1, 31),
            accountId = accountId,
            active = true,
            createdAt = timestamp,
            updatedAt = timestamp
        )

        val transaction = TransactionRecord(
            id = "tx_" + UUID.randomUUID(),
            type = "income",
            description = description,
            amount = amount,
            date = date,
            purchaseDate = date,
            category = if (kind == "salary") "salario" else "outros",
            payment = "Renda recorrente",
            accountId = accountId,
            purchaseTotal = amount,
            sourceRecurringId = id,
            sourceType = "incomeSource",
            createdAt = timestamp,
            updatedAt = timestamp
        )

        runWrite { callback ->
            repository.saveIncomeSourceWithTransactions(source, listOf(transaction), callback)
        }
    }

    fun addBill(
        type: String,
        description: String,
        amount: Double,
        dueDate: String,
        category: String,
        accountId: String,
        notes: String
    ) {
        val bill = BillRecord(
            id = "bill_" + UUID.randomUUID(),
            type = type,
            description = description,
            amount = amount,
            dueDate = dueDate,
            category = category,
            accountId = accountId,
            notes = notes,
            updatedAt = nowIso()
        )
        runWrite { callback -> repository.upsertBill(bill, callback) }
    }

    fun payBill(bill: BillRecord) {
        if (bill.paid) return
        val today = LocalDate.now().toString()
        val timestamp = nowIso()
        val transactionId = "tx_" + UUID.randomUUID()

        val transaction = TransactionRecord(
            id = transactionId,
            type = bill.type,
            description = bill.description,
            amount = bill.amount,
            date = today,
            purchaseDate = today,
            category = bill.category,
            payment = "Conta paga",
            accountId = bill.accountId,
            notes = bill.notes.ifBlank { "Gerado a partir de conta prevista" },
            purchaseTotal = bill.amount,
            billId = bill.id,
            createdAt = timestamp,
            updatedAt = timestamp
        )

        val updated = bill.copy(
            paid = true,
            paidAt = today,
            transactionId = transactionId,
            updatedAt = timestamp
        )

        runWrite { callback ->
            repository.saveBillWithTransaction(updated, transaction, callback)
        }
    }

    fun addTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        date: String,
        notes: String
    ) {
        if (fromAccountId == toAccountId) {
            setLocalError("Escolha contas de origem e destino diferentes.")
            return
        }
        val transfer = TransferRecord(
            id = "trf_" + UUID.randomUUID(),
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            date = date,
            notes = notes,
            createdAt = nowIso()
        )
        runWrite { callback -> repository.upsertTransfer(transfer, callback) }
    }

    fun addBudget(category: String, amount: Double) {
        if (_uiState.value.finance.budgets.any { it.category == category }) {
            setLocalError("Já existe um orçamento para esta categoria.")
            return
        }
        val budget = BudgetRecord(
            id = "bud_" + UUID.randomUUID(),
            category = category,
            amount = amount,
            updatedAt = nowIso()
        )
        runWrite { callback -> repository.upsertBudget(budget, callback) }
    }

    fun addGoal(
        name: String,
        target: Double,
        current: Double,
        deadline: String,
        icon: String
    ) {
        val goal = GoalRecord(
            id = "goal_" + UUID.randomUUID(),
            name = name,
            target = target,
            current = current,
            deadline = deadline,
            icon = icon.ifBlank { "🎯" },
            updatedAt = nowIso()
        )
        runWrite { callback -> repository.upsertGoal(goal, callback) }
    }

    fun addCategory(
        name: String,
        icon: String,
        group: String
    ) {
        val category = CategoryRecord(
            id = "cat_" + UUID.randomUUID(),
            name = name,
            icon = icon.ifBlank { "📦" },
            group = group
        )
        runWrite { callback -> repository.upsertCategory(category, callback) }
    }

    fun addShoppingList(name: String, store: String) {
        val timestamp = nowIso()
        val list = ShoppingListRecord(
            id = "shop_" + UUID.randomUUID(),
            name = name,
            store = store,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        runWrite { callback -> repository.upsertShoppingList(list, callback) }
    }

    fun addShoppingItem(
        listId: String,
        product: String,
        qty: Double,
        unitPrice: Double
    ) {
        val list = _uiState.value.finance.shoppingLists.firstOrNull { it.id == listId }
            ?: return setLocalError("Lista de compras não encontrada.")

        val timestamp = nowIso()
        val item = ShoppingItemRecord(
            id = "shopitem_" + UUID.randomUUID(),
            product = product,
            qty = qty,
            unitPrice = unitPrice,
            order = list.items.size + 1,
            createdAt = timestamp,
            updatedAt = timestamp
        )

        val updated = list.copy(
            items = list.items + item,
            updatedAt = timestamp
        )
        runWrite { callback -> repository.upsertShoppingList(updated, callback) }
    }

    fun deleteShoppingItem(listId: String, itemId: String) {
        val list = _uiState.value.finance.shoppingLists.firstOrNull { it.id == listId }
            ?: return setLocalError("Lista de compras não encontrada.")
        val updated = list.copy(
            items = list.items.filterNot { it.id == itemId },
            updatedAt = nowIso()
        )
        runWrite { callback -> repository.upsertShoppingList(updated, callback) }
    }

    fun deleteTransaction(record: TransactionRecord) =
        runWrite { callback -> repository.deleteTransaction(record, callback) }

    fun deleteRecurring(record: RecurringRecord) =
        runWrite { callback -> repository.deleteRecurring(record, callback) }

    fun deleteCard(record: CardRecord) =
        runWrite { callback -> repository.deleteCard(record, callback) }

    fun deleteAccount(record: AccountRecord) =
        runWrite { callback -> repository.deleteAccount(record, callback) }

    fun deleteIncomeSource(record: IncomeSourceRecord) =
        runWrite { callback -> repository.deleteIncomeSource(record, callback) }

    fun deleteBill(record: BillRecord) =
        runWrite { callback -> repository.deleteBill(record, callback) }

    fun deleteTransfer(record: TransferRecord) =
        runWrite { callback -> repository.deleteTransfer(record, callback) }

    fun deleteBudget(record: BudgetRecord) =
        runWrite { callback -> repository.deleteBudget(record, callback) }

    fun deleteGoal(record: GoalRecord) =
        runWrite { callback -> repository.deleteGoal(record, callback) }

    fun deleteCategory(record: CategoryRecord) {
        val finance = _uiState.value.finance
        val used = finance.transactions.any { it.category == record.id } ||
            finance.recurring.any { it.category == record.id } ||
            finance.budgets.any { it.category == record.id } ||
            finance.bills.any { it.category == record.id }

        if (used) {
            setLocalError("Esta categoria está em uso. Reclassifique os itens antes de excluí-la.")
            return
        }
        runWrite { callback -> repository.deleteCategory(record, callback) }
    }

    fun deleteShoppingList(record: ShoppingListRecord) =
        runWrite { callback -> repository.deleteShoppingList(record, callback) }

    private fun attachUser(user: FirebaseUser) {
        _uiState.value = MainUiState(
            signedIn = true,
            loading = true,
            userName = user.displayName.orEmpty(),
            userEmail = user.email.orEmpty(),
            syncMessage = "Sincronizando"
        )

        repository.start(
            uid = user.uid,
            onState = { finance ->
                _uiState.value = _uiState.value.copy(
                    signedIn = true,
                    loading = false,
                    finance = finance,
                    syncMessage = "Sincronizado",
                    error = ""
                )
            },
            onError = { setError(it) }
        )
    }

    private fun buildTransactions(
        type: String,
        description: String,
        total: Double,
        purchaseDate: String,
        category: String,
        payment: String,
        cardId: String,
        requestedInstallments: Int,
        sourceRecurringId: String = "",
        sourceType: String = ""
    ): List<TransactionRecord> {
        val card = _uiState.value.finance.cards.firstOrNull { it.id == cardId }
        val isCredit = type == "expense" &&
            payment == "Cartão de crédito" &&
            card != null &&
            !card.isBenefit

        val count = if (isCredit) requestedInstallments.coerceIn(1, 60) else 1
        val amounts = FinanceCalculator.splitInstallments(total, count)
        val purchase = LocalDate.parse(purchaseDate)
        val firstInvoice = if (isCredit) cardInvoiceMonth(purchase, card!!) else null
        val group = if (count > 1) "inst_" + UUID.randomUUID() else ""
        val now = nowIso()

        return amounts.mapIndexed { index, amount ->
            val installmentNo = index + 1
            val invoiceMonth = when {
                isCredit -> firstInvoice!!.plusMonths(index.toLong()).toString()
                card != null -> YearMonth.from(purchase).toString()
                else -> ""
            }

            val recordDate = if (isCredit && count > 1) {
                safeDate(firstInvoice!!.plusMonths(index.toLong()), card!!.dueDay).toString()
            } else {
                purchaseDate
            }

            TransactionRecord(
                id = "tx_" + UUID.randomUUID(),
                type = type,
                description = description,
                amount = amount,
                date = recordDate,
                purchaseDate = purchaseDate,
                category = category,
                payment = payment,
                cardId = cardId,
                invoiceMonth = invoiceMonth,
                purchaseTotal = total,
                installmentGroup = group,
                installmentNo = installmentNo,
                installmentCount = count,
                installmentAmount = amount,
                sourceRecurringId = sourceRecurringId,
                sourceType = sourceType,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    private fun cardInvoiceMonth(date: LocalDate, card: CardRecord): YearMonth {
        val month = YearMonth.from(date)
        return if (date.dayOfMonth <= card.closingDay) month else month.plusMonths(1)
    }

    private fun safeDate(month: YearMonth, day: Int): LocalDate =
        month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    private fun nowIso(): String = java.time.Instant.now().toString()

    private fun runWrite(
        operation: (((Result<Unit>) -> Unit)) -> Unit
    ) {
        setSyncing()
        operation { result ->
            result.fold(
                onSuccess = { setSynced() },
                onFailure = { setError(it) }
            )
        }
    }

    private fun setSyncing() {
        _uiState.value = _uiState.value.copy(syncMessage = "Sincronizando", error = "")
    }

    private fun setSynced() {
        _uiState.value = _uiState.value.copy(syncMessage = "Sincronizado", error = "")
    }

    private fun setLocalError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    private fun setError(error: Throwable) {
        _uiState.value = _uiState.value.copy(
            loading = false,
            syncMessage = "Erro",
            error = error.message ?: "Falha ao sincronizar com o Firebase."
        )
    }

    override fun onCleared() {
        repository.stop()
        super.onCleared()
    }
}