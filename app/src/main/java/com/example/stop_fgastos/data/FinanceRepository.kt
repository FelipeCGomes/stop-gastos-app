package com.example.stop_fgastos.data

import com.example.stop_fgastos.model.AccountRecord
import com.example.stop_fgastos.model.BillRecord
import com.example.stop_fgastos.model.BudgetRecord
import com.example.stop_fgastos.model.CardRecord
import com.example.stop_fgastos.model.CategoryRecord
import com.example.stop_fgastos.model.FinanceState
import com.example.stop_fgastos.model.GoalRecord
import com.example.stop_fgastos.model.IncomeSourceRecord
import com.example.stop_fgastos.model.RecurringRecord
import com.example.stop_fgastos.model.ShoppingListRecord
import com.example.stop_fgastos.model.TransactionRecord
import com.example.stop_fgastos.model.TransferRecord
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class FinanceRepository {

    private val db = FirebaseFirestore.getInstance()
    private var uid: String = ""
    private var state = FinanceState()
    private val listeners: MutableList<ListenerRegistration> = mutableListOf()
    private var stateCallback: ((FinanceState) -> Unit)? = null
    private var errorCallback: ((Throwable) -> Unit)? = null

    fun start(
        uid: String,
        onState: (FinanceState) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        stop()
        this.uid = uid
        this.state = FinanceState()
        this.stateCallback = onState
        this.errorCallback = onError

        listeners += listenSection("transactions") { values ->
            state = state.copy(transactions = values.map(TransactionRecord::fromMap))
        }
        listeners += listenSection("recurring") { values ->
            state = state.copy(recurring = values.map(RecurringRecord::fromMap))
        }
        listeners += listenSection("cards") { values ->
            state = state.copy(cards = values.map(CardRecord::fromMap))
        }
        listeners += listenSection("accounts") { values ->
            state = state.copy(accounts = values.map(AccountRecord::fromMap))
        }
        listeners += listenSection("incomeSources") { values ->
            state = state.copy(incomeSources = values.map(IncomeSourceRecord::fromMap))
        }
        listeners += listenSection("bills") { values ->
            state = state.copy(bills = values.map(BillRecord::fromMap))
        }
        listeners += listenSection("transfers") { values ->
            state = state.copy(transfers = values.map(TransferRecord::fromMap))
        }
        listeners += listenSection("budgets") { values ->
            state = state.copy(budgets = values.map(BudgetRecord::fromMap))
        }
        listeners += listenSection("goals") { values ->
            state = state.copy(goals = values.map(GoalRecord::fromMap))
        }
        listeners += listenSection("categories") { values ->
            state = state.copy(categories = values.map(CategoryRecord::fromMap))
        }
        listeners += listenSection("shoppingLists") { values ->
            state = state.copy(shoppingLists = values.map(ShoppingListRecord::fromMap))
        }
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
        uid = ""
        stateCallback = null
        errorCallback = null
    }

    fun upsertTransactions(
        records: List<TransactionRecord>,
        onResult: (Result<Unit>) -> Unit
    ) {
        mutateList("transactions", { list ->
            records.forEach { record ->
                upsertInMemory(list, record.id, record.toMap())
            }
        }, onResult)
    }

    fun saveRecurringWithTransactions(
        recurring: RecurringRecord,
        records: List<TransactionRecord>,
        onResult: (Result<Unit>) -> Unit
    ) {
        mutateTwoSections(
            firstSection = "recurring",
            secondSection = "transactions",
            firstMutation = { list -> upsertInMemory(list, recurring.id, recurring.toMap()) },
            secondMutation = { list ->
                records.forEach { record -> upsertInMemory(list, record.id, record.toMap()) }
            },
            onResult = onResult
        )
    }

    fun upsertCard(card: CardRecord, onResult: (Result<Unit>) -> Unit) =
        upsertRecord("cards", card.id, card.toMap(), onResult)

    fun upsertAccount(account: AccountRecord, onResult: (Result<Unit>) -> Unit) =
        upsertRecord("accounts", account.id, account.toMap(), onResult)

    fun upsertIncomeSource(source: IncomeSourceRecord, onResult: (Result<Unit>) -> Unit) =
        upsertRecord("incomeSources", source.id, source.toMap(), onResult)

    fun saveIncomeSourceWithTransactions(
        source: IncomeSourceRecord,
        records: List<TransactionRecord>,
        onResult: (Result<Unit>) -> Unit
    ) {
        mutateTwoSections(
            firstSection = "incomeSources",
            secondSection = "transactions",
            firstMutation = { list -> upsertInMemory(list, source.id, source.toMap()) },
            secondMutation = { list ->
                records.forEach { record -> upsertInMemory(list, record.id, record.toMap()) }
            },
            onResult = onResult
        )
    }

    fun upsertBill(bill: BillRecord, onResult: (Result<Unit>) -> Unit) =
        upsertRecord("bills", bill.id, bill.toMap(), onResult)

    fun saveBillWithTransaction(
        bill: BillRecord,
        transaction: TransactionRecord,
        onResult: (Result<Unit>) -> Unit
    ) {
        mutateTwoSections(
            firstSection = "bills",
            secondSection = "transactions",
            firstMutation = { list -> upsertInMemory(list, bill.id, bill.toMap()) },
            secondMutation = { list -> upsertInMemory(list, transaction.id, transaction.toMap()) },
            onResult = onResult
        )
    }

    fun upsertTransfer(transfer: TransferRecord, onResult: (Result<Unit>) -> Unit) =
        upsertRecord("transfers", transfer.id, transfer.toMap(), onResult)

    fun upsertBudget(budget: BudgetRecord, onResult: (Result<Unit>) -> Unit) =
        upsertRecord("budgets", budget.id, budget.toMap(), onResult)

    fun upsertGoal(goal: GoalRecord, onResult: (Result<Unit>) -> Unit) =
        upsertRecord("goals", goal.id, goal.toMap(), onResult)

    fun upsertCategory(category: CategoryRecord, onResult: (Result<Unit>) -> Unit) =
        upsertRecord("categories", category.id, category.toMap(), onResult)

    fun upsertShoppingList(list: ShoppingListRecord, onResult: (Result<Unit>) -> Unit) =
        upsertRecord("shoppingLists", list.id, list.toMap(), onResult)

    fun deleteTransaction(record: TransactionRecord, onResult: (Result<Unit>) -> Unit) {
        mutateList("transactions", { list ->
            if (record.installmentGroup.isNotBlank()) {
                list.removeAll { it["installmentGroup"]?.toString() == record.installmentGroup }
            } else {
                list.removeAll { it["id"]?.toString() == record.id }
            }
        }, onResult)
    }

    fun deleteRecurring(record: RecurringRecord, onResult: (Result<Unit>) -> Unit) =
        deleteRecord("recurring", record.id, onResult)

    fun deleteCard(record: CardRecord, onResult: (Result<Unit>) -> Unit) =
        deleteRecord("cards", record.id, onResult)

    fun deleteAccount(record: AccountRecord, onResult: (Result<Unit>) -> Unit) {
        mutateTwoSections(
            firstSection = "accounts",
            secondSection = "cards",
            firstMutation = { list ->
                list.removeAll { it["id"]?.toString() == record.id }
            },
            secondMutation = { list ->
                list.forEach { card ->
                    if (card["accountId"]?.toString() == record.id) {
                        card["accountId"] = ""
                    }
                }
            },
            onResult = onResult
        )
    }

    fun deleteIncomeSource(record: IncomeSourceRecord, onResult: (Result<Unit>) -> Unit) =
        deleteRecord("incomeSources", record.id, onResult)

    fun deleteBill(record: BillRecord, onResult: (Result<Unit>) -> Unit) =
        deleteRecord("bills", record.id, onResult)

    fun deleteTransfer(record: TransferRecord, onResult: (Result<Unit>) -> Unit) =
        deleteRecord("transfers", record.id, onResult)

    fun deleteBudget(record: BudgetRecord, onResult: (Result<Unit>) -> Unit) =
        deleteRecord("budgets", record.id, onResult)

    fun deleteGoal(record: GoalRecord, onResult: (Result<Unit>) -> Unit) =
        deleteRecord("goals", record.id, onResult)

    fun deleteCategory(record: CategoryRecord, onResult: (Result<Unit>) -> Unit) =
        deleteRecord("categories", record.id, onResult)

    fun deleteShoppingList(record: ShoppingListRecord, onResult: (Result<Unit>) -> Unit) =
        deleteRecord("shoppingLists", record.id, onResult)

    private fun upsertRecord(
        section: String,
        id: String,
        map: Map<String, Any?>,
        onResult: (Result<Unit>) -> Unit
    ) {
        mutateList(section, { list -> upsertInMemory(list, id, map) }, onResult)
    }

    private fun deleteRecord(
        section: String,
        id: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        mutateList(section, { list ->
            list.removeAll { it["id"]?.toString() == id }
        }, onResult)
    }

    private fun upsertInMemory(
        list: MutableList<MutableMap<String, Any?>>,
        id: String,
        map: Map<String, Any?>
    ) {
        val index = list.indexOfFirst { it["id"]?.toString() == id }
        if (index >= 0) list[index] = map.toMutableMap()
        else list.add(map.toMutableMap())
    }

    private fun sectionRef(section: String) =
        db.collection("users")
            .document(uid)
            .collection("data")
            .document(section)

    private fun listenSection(
        section: String,
        apply: (List<Map<String, Any?>>) -> Unit
    ): ListenerRegistration {
        return sectionRef(section).addSnapshotListener { snapshot, error ->
            if (error != null) {
                errorCallback?.invoke(error)
                return@addSnapshotListener
            }

            apply(readMaps(snapshot))
            stateCallback?.invoke(state)
        }
    }

    private fun mutateList(
        section: String,
        mutation: (MutableList<MutableMap<String, Any?>>) -> Unit,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (uid.isBlank()) {
            onResult(Result.failure(IllegalStateException("Usuário não autenticado.")))
            return
        }

        val ref = sectionRef(section)
        db.runTransaction { transaction ->
            val list = mutableMaps(transaction.get(ref))
            mutation(list)
            transaction.set(
                ref,
                mapOf("value" to list, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
        }.addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener {
            onResult(Result.failure(it))
        }
    }

    private fun mutateTwoSections(
        firstSection: String,
        secondSection: String,
        firstMutation: (MutableList<MutableMap<String, Any?>>) -> Unit,
        secondMutation: (MutableList<MutableMap<String, Any?>>) -> Unit,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (uid.isBlank()) {
            onResult(Result.failure(IllegalStateException("Usuário não autenticado.")))
            return
        }

        val firstRef = sectionRef(firstSection)
        val secondRef = sectionRef(secondSection)

        db.runTransaction { transaction ->
            val firstList = mutableMaps(transaction.get(firstRef))
            val secondList = mutableMaps(transaction.get(secondRef))

            firstMutation(firstList)
            secondMutation(secondList)

            transaction.set(
                firstRef,
                mapOf("value" to firstList, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            transaction.set(
                secondRef,
                mapOf("value" to secondList, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
        }.addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener {
            onResult(Result.failure(it))
        }
    }

    private fun readMaps(snapshot: DocumentSnapshot?): List<Map<String, Any?>> {
        val value = snapshot?.get("value") as? List<*> ?: return emptyList()
        return value.mapNotNull { item ->
            (item as? Map<*, *>)?.entries?.associate { entry ->
                entry.key.toString() to entry.value
            }
        }
    }

    private fun mutableMaps(snapshot: DocumentSnapshot): MutableList<MutableMap<String, Any?>> =
        readMaps(snapshot).map { it.toMutableMap() }.toMutableList()
}