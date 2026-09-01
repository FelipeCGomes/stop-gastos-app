package com.example.stop_fgastos.viewmodel

import androidx.lifecycle.ViewModel
import com.example.stop_fgastos.auth.GoogleAuthManager
import com.example.stop_fgastos.data.FinanceRepository
import com.example.stop_fgastos.model.CardRecord
import com.example.stop_fgastos.model.FinanceState
import com.example.stop_fgastos.model.RecurringRecord
import com.example.stop_fgastos.model.TransactionRecord
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
        if (user != null) {
            attachUser(user)
        } else {
            _uiState.value = MainUiState(loading = false)
        }
    }

    fun onSignedIn(user: FirebaseUser) {
        attachUser(user)
    }

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

        setSyncing()
        repository.upsertTransactions(records) { result ->
            result.fold(
                onSuccess = { setSynced() },
                onFailure = { setError(it) }
            )
        }
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
            installmentStartMonth = if (isCredit && effectiveCount > 1) {
                purchaseDate.take(7)
            } else "",
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

        setSyncing()
        repository.saveRecurringWithTransactions(recurring, records) { result ->
            result.fold(
                onSuccess = { setSynced() },
                onFailure = { setError(it) }
            )
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

        setSyncing()
        repository.upsertCard(card) { result ->
            result.fold(
                onSuccess = { setSynced() },
                onFailure = { setError(it) }
            )
        }
    }

    fun deleteTransaction(record: TransactionRecord) {
        setSyncing()
        repository.deleteTransaction(record) { result ->
            result.fold(
                onSuccess = { setSynced() },
                onFailure = { setError(it) }
            )
        }
    }

    fun deleteRecurring(record: RecurringRecord) {
        setSyncing()
        repository.deleteRecurring(record) { result ->
            result.fold(
                onSuccess = { setSynced() },
                onFailure = { setError(it) }
            )
        }
    }

    fun deleteCard(record: CardRecord) {
        setSyncing()
        repository.deleteCard(record) { result ->
            result.fold(
                onSuccess = { setSynced() },
                onFailure = { setError(it) }
            )
        }
    }

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

    private fun nowIso(): String =
        java.time.Instant.now().toString()

    private fun setSyncing() {
        _uiState.value = _uiState.value.copy(syncMessage = "Sincronizando", error = "")
    }

    private fun setSynced() {
        _uiState.value = _uiState.value.copy(syncMessage = "Sincronizado", error = "")
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