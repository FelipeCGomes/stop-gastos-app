package com.example.stop_fgastos.domain.model;

public enum FinanceSection {
    TRANSACTIONS("transactions"),
    RECURRING("recurring"),
    CARDS("cards"),
    ACCOUNTS("accounts"),
    INCOME_SOURCES("incomeSources"),
    BILLS("bills"),
    TRANSFERS("transfers"),
    BUDGETS("budgets"),
    GOALS("goals"),
    CATEGORIES("categories"),
    SHOPPING_LISTS("shoppingLists"),
    AUDIT("audit"),
    SETTINGS("settings");

    private final String firestoreId;

    FinanceSection(String firestoreId) {
        this.firestoreId = firestoreId;
    }

    public String firestoreId() {
        return firestoreId;
    }

    public static FinanceSection fromFirestoreId(String id) {
        for (FinanceSection section : values()) {
            if (section.firestoreId.equals(id)) return section;
        }
        throw new IllegalArgumentException("Seção desconhecida: " + id);
    }
}
