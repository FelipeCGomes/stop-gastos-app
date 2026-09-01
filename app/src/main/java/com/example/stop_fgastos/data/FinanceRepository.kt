package com.example.stop_fgastos.data

import com.example.stop_fgastos.model.CardRecord
import com.example.stop_fgastos.model.FinanceState
import com.example.stop_fgastos.model.IncomeSourceRecord
import com.example.stop_fgastos.model.RecurringRecord
import com.example.stop_fgastos.model.TransactionRecord
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class FinanceRepository {

    private val db = FirebaseFirestore.getInstance()
    private var uid: String = ""
    private var state = FinanceState()
    private var listeners: MutableList<ListenerRegistration> = mutableListOf()
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
        listeners += listenSection("incomeSources") { values ->
            state = state.copy(incomeSources = values.map(IncomeSourceRecord::fromMap))
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
                val index = list.indexOfFirst { it["id"]?.toString() == record.id }
                if (index >= 0) list[index] = record.toMap().toMutableMap()
                else list.add(record.toMap().toMutableMap())
            }
        }, onResult)
    }

    fun saveRecurringWithTransactions(
        recurring: RecurringRecord,
        records: List<TransactionRecord>,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (uid.isBlank()) {
            onResult(Result.failure(IllegalStateException("Usuário não autenticado.")))
            return
        }

        val recurringRef = sectionRef("recurring")
        val transactionsRef = sectionRef("transactions")

        db.runTransaction { transaction ->
            val recurringList = mutableMaps(transaction.get(recurringRef))
            val recurringIndex = recurringList.indexOfFirst { it["id"]?.toString() == recurring.id }
            if (recurringIndex >= 0) recurringList[recurringIndex] = recurring.toMap().toMutableMap()
            else recurringList.add(recurring.toMap().toMutableMap())

            val transactionList = mutableMaps(transaction.get(transactionsRef))
            records.forEach { record ->
                val index = transactionList.indexOfFirst { it["id"]?.toString() == record.id }
                if (index >= 0) transactionList[index] = record.toMap().toMutableMap()
                else transactionList.add(record.toMap().toMutableMap())
            }

            transaction.set(
                recurringRef,
                mapOf("value" to recurringList, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            transaction.set(
                transactionsRef,
                mapOf("value" to transactionList, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
        }.addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener {
            onResult(Result.failure(it))
        }
    }

    fun upsertCard(card: CardRecord, onResult: (Result<Unit>) -> Unit) {
        mutateList("cards", { list ->
            val index = list.indexOfFirst { it["id"]?.toString() == card.id }
            if (index >= 0) list[index] = card.toMap().toMutableMap()
            else list.add(card.toMap().toMutableMap())
        }, onResult)
    }

    fun deleteTransaction(record: TransactionRecord, onResult: (Result<Unit>) -> Unit) {
        mutateList("transactions", { list ->
            if (record.installmentGroup.isNotBlank()) {
                list.removeAll { it["installmentGroup"]?.toString() == record.installmentGroup }
            } else {
                list.removeAll { it["id"]?.toString() == record.id }
            }
        }, onResult)
    }

    fun deleteRecurring(record: RecurringRecord, onResult: (Result<Unit>) -> Unit) {
        mutateList("recurring", { list ->
            list.removeAll { it["id"]?.toString() == record.id }
        }, onResult)
    }

    fun deleteCard(record: CardRecord, onResult: (Result<Unit>) -> Unit) {
        mutateList("cards", { list ->
            list.removeAll { it["id"]?.toString() == record.id }
        }, onResult)
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