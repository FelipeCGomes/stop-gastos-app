package com.example.stop_fgastos.model

data class UserProfileRecord(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoURL: String = "",
    val familyId: String = "",
    val role: String = ""
)

data class FamilyRecord(
    val id: String,
    val name: String,
    val ownerUid: String
)

data class FamilyMemberRecord(
    val uid: String,
    val displayName: String = "",
    val email: String = "",
    val photoURL: String = "",
    val role: String = "member",
    val status: String = "active"
)

data class FamilyInvitationRecord(
    val id: String,
    val familyId: String,
    val familyName: String = "Família",
    val targetUid: String,
    val targetEmail: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val status: String = "pending"
)

data class FamilyShoppingListRecord(
    val id: String,
    val familyId: String,
    val name: String,
    val store: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val items: List<ShoppingItemRecord> = emptyList()
) {
    val total: Double get() = items.sumOf { it.total }
}

data class FamilyState(
    val profile: UserProfileRecord = UserProfileRecord(),
    val family: FamilyRecord? = null,
    val members: List<FamilyMemberRecord> = emptyList(),
    val invitations: List<FamilyInvitationRecord> = emptyList(),
    val sharedLists: List<FamilyShoppingListRecord> = emptyList()
) {
    val isAdmin: Boolean get() = profile.role == "admin"
    val hasFamily: Boolean get() = family != null
}