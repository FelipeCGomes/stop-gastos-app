package com.example.stop_fgastos.model

private fun Map<String, Any?>.text(key: String, fallback: String = ""): String =
    this[key]?.toString() ?: fallback

private fun Map<String, Any?>.number(key: String, fallback: Double = 0.0): Double =
    (this[key] as? Number)?.toDouble() ?: this[key]?.toString()?.toDoubleOrNull() ?: fallback

private fun Map<String, Any?>.intNumber(key: String, fallback: Int = 0): Int =
    number(key, fallback.toDouble()).toInt()

private fun Map<String, Any?>.bool(key: String, fallback: Boolean = false): Boolean =
    this[key] as? Boolean ?: fallback

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

data class IncomeSourceRecord(
    val id: String,
    val kind: String = "salary",
    val description: String,
    val amount: Double,
    val day: Int,
    val accountId: String = "",
    val active: Boolean = true,
    val raw: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun fromMap(map: Map<String, Any?>) = IncomeSourceRecord(
            id = map.text("id"),
            kind = map.text("kind", "salary"),
            description = map.text("description"),
            amount = map.number("amount"),
            day = map.intNumber("day", 1),
            accountId = map.text("accountId"),
            active = map.bool("active", true),
            raw = map
        )
    }
}

data class FinanceState(
    val transactions: List<TransactionRecord> = emptyList(),
    val recurring: List<RecurringRecord> = emptyList(),
    val cards: List<CardRecord> = emptyList(),
    val incomeSources: List<IncomeSourceRecord> = emptyList()
)