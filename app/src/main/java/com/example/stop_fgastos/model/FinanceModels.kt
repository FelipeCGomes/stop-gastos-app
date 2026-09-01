package com.example.stop_fgastos.model

private fun Map<String, Any?>.text(key: String, fallback: String = ""): String =
    this[key]?.toString() ?: fallback

private fun Map<String, Any?>.number(key: String, fallback: Double = 0.0): Double =
    (this[key] as? Number)?.toDouble() ?: this[key]?.toString()?.toDoubleOrNull() ?: fallback

private fun Map<String, Any?>.intNumber(key: String, fallback: Int = 0): Int =
    number(key, fallback.toDouble()).toInt()

private fun Map<String, Any?>.bool(key: String, fallback: Boolean = false): Boolean =
    this[key] as? Boolean ?: fallback

private fun Map<String, Any?>.mapList(key: String): List<Map<String, Any?>> {
    val value = this[key] as? List<*> ?: return emptyList()
    return value.mapNotNull { item ->
        (item as? Map<*, *>)?.entries?.associate { entry ->
            entry.key.toString() to entry.value
        }
    }
}

data class TransactionRecord(
    val id: String,
    val type: String,
    val description: String,
    val amount: Double,
    val date: String,
    val purchaseDate: String,
    val category: String,
    val payment: String,
    val accountId: String = "",
    val cardId: String = "",
    val tags: String = "",
    val notes: String = "",
    val invoiceMonth: String = "",
    val purchaseTotal: Double = amount,
    val installmentGroup: String = "",
    val installmentNo: Int = 1,
    val installmentCount: Int = 1,
    val installmentAmount: Double = amount,
    val sourceRecurringId: String = "",
    val sourceType: String = "",
    val billId: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val raw: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("type", type)
        put("description", description)
        put("amount", amount)
        put("date", date)
        put("purchaseDate", purchaseDate)
        put("category", category)
        put("payment", payment)
        put("accountId", accountId)
        put("cardId", cardId)
        put("tags", tags)
        put("notes", notes)
        put("invoiceMonth", invoiceMonth)
        put("purchaseTotal", purchaseTotal)
        put("installmentGroup", installmentGroup)
        put("installmentNo", installmentNo)
        put("installmentCount", installmentCount)
        put("installmentAmount", installmentAmount)
        put("sourceRecurringId", sourceRecurringId)
        put("sourceType", sourceType)
        put("billId", billId)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = TransactionRecord(
            id = map.text("id"),
            type = map.text("type", "expense"),
            description = map.text("description"),
            amount = map.number("amount"),
            date = map.text("date"),
            purchaseDate = map.text("purchaseDate", map.text("date")),
            category = map.text("category", "outros"),
            payment = map.text("payment"),
            accountId = map.text("accountId"),
            cardId = map.text("cardId"),
            tags = map.text("tags"),
            notes = map.text("notes"),
            invoiceMonth = map.text("invoiceMonth"),
            purchaseTotal = map.number("purchaseTotal", map.number("amount")),
            installmentGroup = map.text("installmentGroup"),
            installmentNo = map.intNumber("installmentNo", 1),
            installmentCount = map.intNumber("installmentCount", 1),
            installmentAmount = map.number("installmentAmount", map.number("amount")),
            sourceRecurringId = map.text("sourceRecurringId"),
            sourceType = map.text("sourceType"),
            billId = map.text("billId"),
            createdAt = map.text("createdAt"),
            updatedAt = map.text("updatedAt"),
            raw = map
        )
    }
}

data class RecurringRecord(
    val id: String,
    val type: String = "expense",
    val kind: String = "fixed",
    val description: String,
    val amount: Double,
    val day: Int,
    val category: String,
    val payment: String,
    val cardId: String = "",
    val installmentCount: Int = 1,
    val installmentStartMonth: String = "",
    val active: Boolean = true,
    val updatedAt: String = "",
    val raw: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("type", type)
        put("kind", kind)
        put("description", description)
        put("amount", amount)
        put("day", day)
        put("category", category)
        put("payment", payment)
        put("cardId", cardId)
        put("installmentCount", installmentCount)
        put("installmentStartMonth", installmentStartMonth)
        put("active", active)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = RecurringRecord(
            id = map.text("id"),
            type = map.text("type", "expense"),
            kind = map.text("kind", "fixed"),
            description = map.text("description"),
            amount = map.number("amount"),
            day = map.intNumber("day", 1),
            category = map.text("category", "outros"),
            payment = map.text("payment"),
            cardId = map.text("cardId"),
            installmentCount = map.intNumber("installmentCount", 1),
            installmentStartMonth = map.text("installmentStartMonth"),
            active = map.bool("active", true),
            updatedAt = map.text("updatedAt"),
            raw = map
        )
    }
}

data class CardRecord(
    val id: String,
    val name: String,
    val cardType: String = "credit",
    val brand: String = "",
    val limit: Double = 0.0,
    val closingDay: Int = 1,
    val dueDay: Int = 10,
    val accountId: String = "",
    val color: String = "#141b34",
    val raw: Map<String, Any?> = emptyMap()
) {
    val isBenefit: Boolean get() = cardType != "credit"

    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("name", name)
        put("cardType", cardType)
        put("brand", brand)
        put("limit", limit)
        put("closingDay", closingDay)
        put("dueDay", dueDay)
        put("accountId", accountId)
        put("color", color)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = CardRecord(
            id = map.text("id"),
            name = map.text("name"),
            cardType = map.text("cardType", "credit"),
            brand = map.text("brand"),
            limit = map.number("limit"),
            closingDay = map.intNumber("closingDay", 1),
            dueDay = map.intNumber("dueDay", 10),
            accountId = map.text("accountId"),
            color = map.text("color", "#141b34"),
            raw = map
        )
    }
}

data class AccountRecord(
    val id: String,
    val name: String,
    val type: String = "Conta corrente",
    val openingBalance: Double = 0.0,
    val icon: String = "🏦",
    val color: String = "#7c5cff",
    val updatedAt: String = "",
    val raw: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("openingBalance", openingBalance)
        put("icon", icon)
        put("color", color)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = AccountRecord(
            id = map.text("id"),
            name = map.text("name"),
            type = map.text("type", "Conta corrente"),
            openingBalance = map.number("openingBalance"),
            icon = map.text("icon", "🏦"),
            color = map.text("color", "#7c5cff"),
            updatedAt = map.text("updatedAt"),
            raw = map
        )
    }
}

data class IncomeSourceRecord(
    val id: String,
    val kind: String = "salary",
    val description: String,
    val amount: Double,
    val day: Int,
    val accountId: String = "",
    val active: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
    val raw: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("kind", kind)
        put("description", description)
        put("amount", amount)
        put("day", day)
        put("accountId", accountId)
        put("active", active)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = IncomeSourceRecord(
            id = map.text("id"),
            kind = map.text("kind", "salary"),
            description = map.text("description"),
            amount = map.number("amount"),
            day = map.intNumber("day", 1),
            accountId = map.text("accountId"),
            active = map.bool("active", true),
            createdAt = map.text("createdAt"),
            updatedAt = map.text("updatedAt"),
            raw = map
        )
    }
}

data class BillRecord(
    val id: String,
    val type: String = "expense",
    val description: String,
    val amount: Double,
    val dueDate: String,
    val category: String = "outros",
    val accountId: String = "",
    val notes: String = "",
    val paid: Boolean = false,
    val paidAt: String = "",
    val transactionId: String = "",
    val updatedAt: String = "",
    val raw: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("type", type)
        put("description", description)
        put("amount", amount)
        put("dueDate", dueDate)
        put("category", category)
        put("accountId", accountId)
        put("notes", notes)
        put("paid", paid)
        put("paidAt", paidAt)
        put("transactionId", transactionId)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = BillRecord(
            id = map.text("id"),
            type = map.text("type", "expense"),
            description = map.text("description"),
            amount = map.number("amount"),
            dueDate = map.text("dueDate"),
            category = map.text("category", "outros"),
            accountId = map.text("accountId"),
            notes = map.text("notes"),
            paid = map.bool("paid"),
            paidAt = map.text("paidAt"),
            transactionId = map.text("transactionId"),
            updatedAt = map.text("updatedAt"),
            raw = map
        )
    }
}

data class TransferRecord(
    val id: String,
    val fromAccountId: String,
    val toAccountId: String,
    val amount: Double,
    val date: String,
    val notes: String = "",
    val createdAt: String = "",
    val raw: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("fromAccountId", fromAccountId)
        put("toAccountId", toAccountId)
        put("amount", amount)
        put("date", date)
        put("notes", notes)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = TransferRecord(
            id = map.text("id"),
            fromAccountId = map.text("fromAccountId"),
            toAccountId = map.text("toAccountId"),
            amount = map.number("amount"),
            date = map.text("date"),
            notes = map.text("notes"),
            createdAt = map.text("createdAt"),
            raw = map
        )
    }
}

data class BudgetRecord(
    val id: String,
    val category: String,
    val amount: Double,
    val updatedAt: String = "",
    val raw: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("category", category)
        put("amount", amount)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = BudgetRecord(
            id = map.text("id"),
            category = map.text("category"),
            amount = map.number("amount"),
            updatedAt = map.text("updatedAt"),
            raw = map
        )
    }
}

data class GoalRecord(
    val id: String,
    val name: String,
    val target: Double,
    val current: Double = 0.0,
    val deadline: String = "",
    val icon: String = "🎯",
    val updatedAt: String = "",
    val raw: Map<String, Any?> = emptyMap()
) {
    val progress: Double
        get() = if (target > 0) (current / target).coerceIn(0.0, 1.0) else 0.0

    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("name", name)
        put("target", target)
        put("current", current)
        put("deadline", deadline)
        put("icon", icon)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = GoalRecord(
            id = map.text("id"),
            name = map.text("name"),
            target = map.number("target"),
            current = map.number("current"),
            deadline = map.text("deadline"),
            icon = map.text("icon", "🎯"),
            updatedAt = map.text("updatedAt"),
            raw = map
        )
    }
}

data class CategoryRecord(
    val id: String,
    val name: String,
    val icon: String = "📦",
    val color: String = "#8d99ae",
    val group: String = "essential",
    val raw: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("name", name)
        put("icon", icon)
        put("color", color)
        put("group", group)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = CategoryRecord(
            id = map.text("id"),
            name = map.text("name"),
            icon = map.text("icon", "📦"),
            color = map.text("color", "#8d99ae"),
            group = map.text("group", "essential"),
            raw = map
        )
    }
}

data class ShoppingItemRecord(
    val id: String,
    val product: String,
    val qty: Double = 1.0,
    val unitPrice: Double = 0.0,
    val order: Int = 0,
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val raw: Map<String, Any?> = emptyMap()
) {
    val total: Double get() = qty * unitPrice

    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("product", product)
        put("qty", qty)
        put("unitPrice", unitPrice)
        put("order", order)
        put("createdBy", createdBy)
        put("createdByName", createdByName)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = ShoppingItemRecord(
            id = map.text("id"),
            product = map.text("product"),
            qty = map.number("qty", 1.0),
            unitPrice = map.number("unitPrice"),
            order = map.intNumber("order"),
            createdBy = map.text("createdBy"),
            createdByName = map.text("createdByName"),
            createdAt = map.text("createdAt"),
            updatedAt = map.text("updatedAt"),
            raw = map
        )
    }
}

data class ShoppingListRecord(
    val id: String,
    val name: String,
    val store: String = "",
    val items: List<ShoppingItemRecord> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
    val raw: Map<String, Any?> = emptyMap()
) {
    val total: Double get() = items.sumOf { it.total }

    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("id", id)
        put("name", name)
        put("store", store)
        put("items", items.map { it.toMap() })
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = ShoppingListRecord(
            id = map.text("id"),
            name = map.text("name"),
            store = map.text("store"),
            items = map.mapList("items").map(ShoppingItemRecord::fromMap),
            createdAt = map.text("createdAt"),
            updatedAt = map.text("updatedAt"),
            raw = map
        )
    }
}

data class FinanceSettingsRecord(
    val theme: String = "system",
    val monthlyBudget: Double = 0.0,
    val privacyMode: Boolean = false,
    val raw: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = raw.toMutableMap().apply {
        put("theme", theme)
        put("monthlyBudget", monthlyBudget)
        put("privacyMode", privacyMode)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>) = FinanceSettingsRecord(
            theme = map.text("theme", "system").let {
                if (it in setOf("system", "light", "dark")) it else "system"
            },
            monthlyBudget = map.number("monthlyBudget").coerceAtLeast(0.0),
            privacyMode = map.bool("privacyMode"),
            raw = map
        )
    }
}

data class FinanceState(
    val transactions: List<TransactionRecord> = emptyList(),
    val recurring: List<RecurringRecord> = emptyList(),
    val cards: List<CardRecord> = emptyList(),
    val accounts: List<AccountRecord> = emptyList(),
    val incomeSources: List<IncomeSourceRecord> = emptyList(),
    val bills: List<BillRecord> = emptyList(),
    val transfers: List<TransferRecord> = emptyList(),
    val budgets: List<BudgetRecord> = emptyList(),
    val goals: List<GoalRecord> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val shoppingLists: List<ShoppingListRecord> = emptyList(),
    val shoppingActiveListId: String = "",
    val audit: List<Map<String, Any?>> = emptyList(),
    val settings: FinanceSettingsRecord = FinanceSettingsRecord()
)