package com.example.stop_fgastos.data

import com.example.stop_fgastos.model.FamilyInvitationRecord
import com.example.stop_fgastos.model.FamilyMemberRecord
import com.example.stop_fgastos.model.FamilyRecord
import com.example.stop_fgastos.model.FamilyShoppingListRecord
import com.example.stop_fgastos.model.FamilyState
import com.example.stop_fgastos.model.ShoppingItemRecord
import com.example.stop_fgastos.model.UserProfileRecord
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.security.MessageDigest
import java.util.Date
import java.util.Locale
import java.util.UUID

class FamilyRepository {

    private val db = FirebaseFirestore.getInstance()
    private var user: FirebaseUser? = null
    private var state = FamilyState()
    private val listeners = mutableListOf<ListenerRegistration>()
    private var onState: ((FamilyState) -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null

    fun start(
        user: FirebaseUser,
        onState: (FamilyState) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        stop()
        this.user = user
        this.onState = onState
        this.onError = onError

        ensureOwnProfile { result ->
            result.onSuccess { profile ->
                state = state.copy(profile = profile)
                notifyState()
                watchProfile()
                watchInvitations()
                refresh()
            }.onFailure(::emitError)
        }
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
        user = null
        onState = null
        onError = null
        state = FamilyState()
    }

    fun refresh() {
        val current = user ?: return
        ensureOwnProfile { profileResult ->
            profileResult.onFailure(::emitError)
            profileResult.onSuccess { profile ->
                if (profile.familyId.isBlank()) {
                    state = state.copy(
                        profile = profile,
                        family = null,
                        members = emptyList(),
                        sharedLists = emptyList()
                    )
                    notifyState()
                    return@onSuccess
                }

                val ownMemberRef = db.collection("families")
                    .document(profile.familyId)
                    .collection("members")
                    .document(current.uid)

                ownMemberRef.get()
                    .addOnSuccessListener { ownMemberSnap ->
                        val familyRef = db.collection("families").document(profile.familyId)
                        familyRef.get()
                            .addOnSuccessListener { familySnap ->
                                if (!familySnap.exists()) {
                                    clearOwnFamilyLink()
                                    return@addOnSuccessListener
                                }

                                val family = FamilyRecord(
                                    id = familySnap.id,
                                    name = familySnap.getString("name") ?: "Família",
                                    ownerUid = familySnap.getString("ownerUid").orEmpty()
                                )
                                val isOwner = family.ownerUid == current.uid

                                if (ownMemberSnap.exists()) {
                                    val status = ownMemberSnap.getString("status") ?: "active"
                                    if (status != "active") {
                                        clearOwnFamilyLink()
                                        return@addOnSuccessListener
                                    }
                                } else if (isOwner) {
                                    repairOwnerMember(family) { repaired ->
                                        if (repaired) loadMembersAndLists(profile.copy(role = "admin"), family)
                                    }
                                    return@addOnSuccessListener
                                } else {
                                    clearOwnFamilyLink()
                                    return@addOnSuccessListener
                                }

                                val expectedRole = if (isOwner) "admin"
                                else ownMemberSnap.getString("role") ?: "member"

                                val normalizedProfile = if (profile.role != expectedRole) {
                                    profile.copy(role = expectedRole)
                                } else profile

                                if (profile.role != expectedRole) {
                                    profileRef(current.uid).set(
                                        mapOf(
                                            "familyId" to profile.familyId,
                                            "role" to expectedRole,
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        ),
                                        SetOptions.merge()
                                    )
                                }

                                loadMembersAndLists(normalizedProfile, family)
                            }
                            .addOnFailureListener(::emitError)
                    }
                    .addOnFailureListener(::emitError)
            }
        }
    }

    fun createFamily(name: String, onResult: (Result<Unit>) -> Unit) {
        val current = user ?: return onResult(Result.failure(IllegalStateException("Usuário não autenticado.")))
        val cleanName = name.trim()
        if (cleanName.length < 2) {
            onResult(Result.failure(IllegalArgumentException("Informe um nome para a família.")))
            return
        }
        if (state.family != null || state.profile.familyId.isNotBlank()) {
            onResult(Result.failure(IllegalStateException("Você já participa de uma família.")))
            return
        }

        val familyId = UUID.randomUUID().toString()
        val familyRef = db.collection("families").document(familyId)
        val memberRef = familyRef.collection("members").document(current.uid)

        familyRef.set(
            mapOf(
                "name" to cleanName,
                "ownerUid" to current.uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            memberRef.set(
                mapOf(
                    "uid" to current.uid,
                    "displayName" to current.displayName.orEmpty(),
                    "email" to normalizeEmail(current.email),
                    "photoURL" to current.photoUrl?.toString().orEmpty(),
                    "role" to "admin",
                    "status" to "active",
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).addOnSuccessListener {
                profileRef(current.uid).set(
                    mapOf(
                        "familyId" to familyId,
                        "role" to "admin",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).addOnSuccessListener {
                    refresh()
                    onResult(Result.success(Unit))
                }.addOnFailureListener { onResult(Result.failure(it)) }
            }.addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun sendInviteByEmail(email: String, onResult: (Result<Unit>) -> Unit) {
        val current = user ?: return onResult(Result.failure(IllegalStateException("Usuário não autenticado.")))
        val family = state.family
            ?: return onResult(Result.failure(IllegalStateException("Crie ou participe de uma família primeiro.")))

        if (!state.isAdmin) {
            onResult(Result.failure(IllegalStateException("Apenas o administrador pode convidar membros.")))
            return
        }

        val normalized = normalizeEmail(email)
        if (normalized.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Informe o e-mail Google do membro.")))
            return
        }
        if (normalized == normalizeEmail(current.email)) {
            onResult(Result.failure(IllegalArgumentException("Você já é o administrador desta família.")))
            return
        }

        val key = emailDirectoryKey(normalized)
        db.collection("userDirectory").document(key).get()
            .addOnSuccessListener { targetSnap ->
                if (!targetSnap.exists()) {
                    onResult(Result.failure(IllegalStateException("Essa conta ainda não entrou no Stop Gastos com esse e-mail.")))
                    return@addOnSuccessListener
                }

                val targetUid = targetSnap.getString("uid").orEmpty()
                val targetEmail = targetSnap.getString("email").orEmpty()
                if (targetUid.isBlank() || normalizeEmail(targetEmail) != normalized) {
                    onResult(Result.failure(IllegalStateException("Conta não localizada para esse e-mail.")))
                    return@addOnSuccessListener
                }

                val memberRef = db.collection("families")
                    .document(family.id)
                    .collection("members")
                    .document(targetUid)

                memberRef.get().addOnSuccessListener { memberSnap ->
                    if (memberSnap.exists()) {
                        when (memberSnap.getString("status") ?: "active") {
                            "active" -> {
                                onResult(Result.failure(IllegalStateException("Essa pessoa já é membro ativo.")))
                                return@addOnSuccessListener
                            }
                            "pending" -> {
                                onResult(Result.failure(IllegalStateException("Já existe um convite pendente.")))
                                return@addOnSuccessListener
                            }
                        }
                    }

                    val requestId = UUID.randomUUID().toString()
                    val expiresAt = Timestamp(Date(System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L))

                    memberRef.set(
                        mapOf(
                            "uid" to targetUid,
                            "displayName" to (targetSnap.getString("displayName") ?: ""),
                            "email" to normalized,
                            "photoURL" to (targetSnap.getString("photoURL") ?: ""),
                            "role" to "member",
                            "status" to "pending",
                            "invitedBy" to current.uid,
                            "invitedByName" to current.displayName.orEmpty(),
                            "invitedAt" to FieldValue.serverTimestamp(),
                            "responseAt" to null,
                            "declinedAt" to null,
                            "acceptedAt" to null,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).addOnSuccessListener {
                        db.collection("familyRequests").document(requestId).set(
                            mapOf(
                                "requestId" to requestId,
                                "familyId" to family.id,
                                "familyName" to family.name,
                                "targetUid" to targetUid,
                                "targetEmail" to normalized,
                                "targetName" to (targetSnap.getString("displayName") ?: ""),
                                "targetPhotoURL" to (targetSnap.getString("photoURL") ?: ""),
                                "createdBy" to current.uid,
                                "createdByName" to current.displayName.orEmpty(),
                                "status" to "pending",
                                "expiresAt" to expiresAt,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "respondedAt" to null,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener {
                            refresh()
                            onResult(Result.success(Unit))
                        }.addOnFailureListener { error ->
                            memberRef.set(
                                mapOf(
                                    "status" to "declined",
                                    "responseAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                ),
                                SetOptions.merge()
                            )
                            onResult(Result.failure(error))
                        }
                    }.addOnFailureListener { onResult(Result.failure(it)) }
                }.addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun respondInvitation(
        invitation: FamilyInvitationRecord,
        accept: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        val current = user ?: return onResult(Result.failure(IllegalStateException("Usuário não autenticado.")))
        val requestRef = db.collection("familyRequests").document(invitation.id)

        requestRef.get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                onResult(Result.failure(IllegalStateException("Convite não encontrado.")))
                return@addOnSuccessListener
            }
            if (snap.getString("targetUid") != current.uid) {
                onResult(Result.failure(IllegalStateException("Este convite pertence a outra conta.")))
                return@addOnSuccessListener
            }
            if ((snap.getString("status") ?: "") != "pending") {
                onResult(Result.failure(IllegalStateException("Este convite já foi respondido.")))
                return@addOnSuccessListener
            }

            val expiry = snap.getTimestamp("expiresAt")?.toDate()
            if (expiry != null && expiry.time <= System.currentTimeMillis()) {
                onResult(Result.failure(IllegalStateException("Este convite expirou.")))
                return@addOnSuccessListener
            }

            val familyId = snap.getString("familyId").orEmpty()
            val memberRef = db.collection("families").document(familyId)
                .collection("members").document(current.uid)

            if (accept && state.profile.familyId.isNotBlank() && state.profile.familyId != familyId) {
                onResult(Result.failure(IllegalStateException("Você já participa de outra família.")))
                return@addOnSuccessListener
            }

            val memberPayload = if (accept) {
                mapOf(
                    "uid" to current.uid,
                    "displayName" to current.displayName.orEmpty(),
                    "email" to normalizeEmail(current.email),
                    "photoURL" to current.photoUrl?.toString().orEmpty(),
                    "role" to "member",
                    "status" to "active",
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "acceptedAt" to FieldValue.serverTimestamp(),
                    "responseAt" to FieldValue.serverTimestamp(),
                    "declinedAt" to null,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "uid" to current.uid,
                    "displayName" to current.displayName.orEmpty(),
                    "email" to normalizeEmail(current.email),
                    "photoURL" to current.photoUrl?.toString().orEmpty(),
                    "role" to "member",
                    "status" to "declined",
                    "responseAt" to FieldValue.serverTimestamp(),
                    "declinedAt" to FieldValue.serverTimestamp(),
                    "acceptedAt" to null,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }

            memberRef.set(memberPayload, SetOptions.merge())
                .addOnSuccessListener {
                    val continueWithRequest: () -> Unit = {
                        requestRef.set(
                            mapOf(
                                "status" to if (accept) "accepted" else "declined",
                                "respondedAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        ).addOnSuccessListener {
                            refresh()
                            onResult(Result.success(Unit))
                        }.addOnFailureListener { onResult(Result.failure(it)) }
                    }

                    if (accept) {
                        profileRef(current.uid).set(
                            mapOf(
                                "familyId" to familyId,
                                "role" to "member",
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        ).addOnSuccessListener { continueWithRequest() }
                            .addOnFailureListener { onResult(Result.failure(it)) }
                    } else {
                        continueWithRequest()
                    }
                }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun removeMember(member: FamilyMemberRecord, onResult: (Result<Unit>) -> Unit) {
        val family = state.family
            ?: return onResult(Result.failure(IllegalStateException("Família não encontrada.")))
        if (!state.isAdmin) {
            onResult(Result.failure(IllegalStateException("Apenas o administrador pode remover membros.")))
            return
        }
        if (member.uid == family.ownerUid) {
            onResult(Result.failure(IllegalStateException("O proprietário não pode ser removido.")))
            return
        }

        val memberRef = db.collection("families").document(family.id)
            .collection("members").document(member.uid)

        memberRef.delete().addOnSuccessListener {
            profileRef(member.uid).set(
                mapOf(
                    "familyId" to "",
                    "role" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                refresh()
                onResult(Result.success(Unit))
            }.addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun createSharedList(name: String, store: String, onResult: (Result<Unit>) -> Unit) {
        val current = user ?: return onResult(Result.failure(IllegalStateException("Usuário não autenticado.")))
        val family = state.family
            ?: return onResult(Result.failure(IllegalStateException("Você precisa participar de uma família.")))
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Informe o nome da lista.")))
            return
        }

        val ref = db.collection("families").document(family.id)
            .collection("shoppingLists").document()

        ref.set(
            mapOf(
                "id" to ref.id,
                "familyId" to family.id,
                "name" to cleanName,
                "store" to store.trim(),
                "createdBy" to current.uid,
                "createdByName" to (current.displayName ?: current.email ?: ""),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedBy" to current.uid,
                "updatedAt" to FieldValue.serverTimestamp(),
                "clientUpdatedAt" to java.time.Instant.now().toString()
            )
        ).addOnSuccessListener {
            refresh()
            onResult(Result.success(Unit))
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun loadSharedItems(listId: String, onResult: (Result<Unit>) -> Unit = {}) {
        val family = state.family ?: return
        db.collection("families").document(family.id)
            .collection("shoppingLists").document(listId)
            .collection("items")
            .get()
            .addOnSuccessListener { snaps ->
                val items = snaps.documents.map { doc ->
                    ShoppingItemRecord(
                        id = doc.id,
                        product = doc.getString("product").orEmpty(),
                        qty = doc.getDouble("qty") ?: 0.0,
                        unitPrice = doc.getDouble("unitPrice") ?: 0.0,
                        order = doc.getLong("order")?.toInt() ?: 0,
                        createdBy = doc.getString("createdBy").orEmpty(),
                        createdByName = doc.getString("createdByName").orEmpty()
                    )
                }.sortedBy { it.order }

                state = state.copy(
                    sharedLists = state.sharedLists.map { list ->
                        if (list.id == listId) list.copy(items = items) else list
                    }
                )
                notifyState()
                onResult(Result.success(Unit))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun addSharedItem(
        listId: String,
        product: String,
        qty: Double,
        unitPrice: Double,
        onResult: (Result<Unit>) -> Unit
    ) {
        val current = user ?: return onResult(Result.failure(IllegalStateException("Usuário não autenticado.")))
        val family = state.family
            ?: return onResult(Result.failure(IllegalStateException("Família não encontrada.")))
        val itemId = UUID.randomUUID().toString()
        val listRef = db.collection("families").document(family.id)
            .collection("shoppingLists").document(listId)
        val itemRef = listRef.collection("items").document(itemId)
        val order = (state.sharedLists.firstOrNull { it.id == listId }?.items?.size ?: 0) + 1

        itemRef.set(
            mapOf(
                "product" to product.trim(),
                "qty" to qty.coerceAtLeast(0.0),
                "unitPrice" to unitPrice.coerceAtLeast(0.0),
                "order" to order,
                "createdBy" to current.uid,
                "createdByName" to (current.displayName ?: current.email ?: ""),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedBy" to current.uid,
                "updatedByName" to (current.displayName ?: current.email ?: ""),
                "updatedAt" to FieldValue.serverTimestamp(),
                "clientUpdatedAt" to java.time.Instant.now().toString()
            )
        ).addOnSuccessListener {
            listRef.set(
                mapOf(
                    "updatedBy" to current.uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "clientUpdatedAt" to java.time.Instant.now().toString()
                ),
                SetOptions.merge()
            )
            loadSharedItems(listId) { loadResult ->
                onResult(loadResult)
            }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun deleteSharedItem(
        listId: String,
        itemId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val family = state.family
            ?: return onResult(Result.failure(IllegalStateException("Família não encontrada.")))
        val listRef = db.collection("families").document(family.id)
            .collection("shoppingLists").document(listId)

        listRef.collection("items").document(itemId).delete()
            .addOnSuccessListener {
                loadSharedItems(listId) { loadResult -> onResult(loadResult) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun deleteSharedList(
        listId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val family = state.family
            ?: return onResult(Result.failure(IllegalStateException("Família não encontrada.")))
        val listRef = db.collection("families").document(family.id)
            .collection("shoppingLists").document(listId)

        listRef.collection("items").get()
            .addOnSuccessListener { items ->
                val batch = db.batch()
                items.documents.forEach { batch.delete(it.reference) }
                batch.delete(listRef)
                batch.commit()
                    .addOnSuccessListener {
                        refresh()
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun watchProfile() {
        val current = user ?: return
        listeners += profileRef(current.uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                emitError(error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val profile = profileFromSnapshot(
                uid = current.uid,
                data = snapshot.data.orEmpty()
            )
            val familyChanged = profile.familyId != state.profile.familyId || profile.role != state.profile.role
            state = state.copy(profile = profile)
            notifyState()
            if (familyChanged) refresh()
        }
    }

    private fun watchInvitations() {
        val current = user ?: return
        listeners += db.collection("familyRequests")
            .whereEqualTo("targetUid", current.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    emitError(error)
                    return@addSnapshotListener
                }

                val invitations = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val status = doc.getString("status") ?: "pending"
                    val expiry = doc.getTimestamp("expiresAt")?.toDate()
                    if (status != "pending") return@mapNotNull null
                    if (expiry != null && expiry.time <= System.currentTimeMillis()) return@mapNotNull null

                    FamilyInvitationRecord(
                        id = doc.id,
                        familyId = doc.getString("familyId").orEmpty(),
                        familyName = doc.getString("familyName") ?: "Família",
                        targetUid = doc.getString("targetUid").orEmpty(),
                        targetEmail = doc.getString("targetEmail").orEmpty(),
                        createdBy = doc.getString("createdBy").orEmpty(),
                        createdByName = doc.getString("createdByName").orEmpty(),
                        status = status
                    )
                }

                state = state.copy(invitations = invitations)
                notifyState()
            }
    }

    private fun loadMembersAndLists(profile: UserProfileRecord, family: FamilyRecord) {
        val membersQuery = if (profile.role == "admin") {
            db.collection("families").document(family.id).collection("members")
        } else {
            db.collection("families").document(family.id).collection("members")
                .whereEqualTo("status", "active")
        }

        membersQuery.get().addOnSuccessListener { memberSnaps ->
            val members = memberSnaps.documents.map { doc ->
                FamilyMemberRecord(
                    uid = doc.getString("uid") ?: doc.id,
                    displayName = doc.getString("displayName").orEmpty(),
                    email = doc.getString("email").orEmpty(),
                    photoURL = doc.getString("photoURL").orEmpty(),
                    role = doc.getString("role") ?: "member",
                    status = doc.getString("status") ?: "active"
                )
            }

            db.collection("families").document(family.id).collection("shoppingLists")
                .get()
                .addOnSuccessListener { listSnaps ->
                    val lists = listSnaps.documents.map { doc ->
                        FamilyShoppingListRecord(
                            id = doc.id,
                            familyId = family.id,
                            name = doc.getString("name") ?: "Lista",
                            store = doc.getString("store").orEmpty(),
                            createdBy = doc.getString("createdBy").orEmpty(),
                            createdByName = doc.getString("createdByName").orEmpty()
                        )
                    }.sortedWith(compareBy<FamilyShoppingListRecord> { it.store.lowercase(Locale("pt", "BR")) }
                        .thenBy { it.name.lowercase(Locale("pt", "BR")) })

                    state = state.copy(
                        profile = profile,
                        family = family,
                        members = members,
                        sharedLists = lists
                    )
                    notifyState()
                }
                .addOnFailureListener(::emitError)
        }.addOnFailureListener(::emitError)
    }

    private fun ensureOwnProfile(onResult: (Result<UserProfileRecord>) -> Unit) {
        val current = user
            ?: return onResult(Result.failure(IllegalStateException("Usuário não autenticado.")))
        val ref = profileRef(current.uid)

        ref.get().addOnSuccessListener { snap ->
            val existing = snap.data.orEmpty()
            val familyId = existing["familyId"]?.toString().orEmpty()
            val role = existing["role"]?.toString().orEmpty()

            val payload = mapOf(
                "uid" to current.uid,
                "displayName" to (current.displayName ?: existing["displayName"]?.toString().orEmpty()),
                "email" to normalizeEmail(current.email ?: existing["email"]?.toString().orEmpty()),
                "photoURL" to (current.photoUrl?.toString() ?: existing["photoURL"]?.toString().orEmpty()),
                "familyId" to familyId,
                "role" to role,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            ref.set(payload, SetOptions.merge())
                .addOnSuccessListener {
                    registerDirectoryEntry(current) { directoryResult ->
                        directoryResult.onFailure { onResult(Result.failure(it)) }
                        directoryResult.onSuccess {
                            onResult(
                                Result.success(
                                    UserProfileRecord(
                                        uid = current.uid,
                                        displayName = payload["displayName"].toString(),
                                        email = payload["email"].toString(),
                                        photoURL = payload["photoURL"].toString(),
                                        familyId = familyId,
                                        role = role
                                    )
                                )
                            )
                        }
                    }
                }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun registerDirectoryEntry(
        current: FirebaseUser,
        onResult: (Result<Unit>) -> Unit
    ) {
        val rawEmail = current.email.orEmpty().trim()
        if (rawEmail.isBlank()) {
            onResult(Result.success(Unit))
            return
        }

        val key = emailDirectoryKey(rawEmail)
        db.collection("userDirectory").document(key).set(
            mapOf(
                "uid" to current.uid,
                "email" to rawEmail,
                "displayName" to current.displayName.orEmpty(),
                "photoURL" to current.photoUrl?.toString().orEmpty(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener {
            onResult(Result.failure(it))
        }
    }

    private fun repairOwnerMember(family: FamilyRecord, onResult: (Boolean) -> Unit) {
        val current = user ?: return onResult(false)
        val memberRef = db.collection("families").document(family.id)
            .collection("members").document(current.uid)

        memberRef.set(
            mapOf(
                "uid" to current.uid,
                "displayName" to current.displayName.orEmpty(),
                "email" to normalizeEmail(current.email),
                "photoURL" to current.photoUrl?.toString().orEmpty(),
                "role" to "admin",
                "status" to "active",
                "joinedAt" to FieldValue.serverTimestamp(),
                "repairedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).addOnSuccessListener {
            profileRef(current.uid).set(
                mapOf(
                    "familyId" to family.id,
                    "role" to "admin",
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnSuccessListener { onResult(true) }
                .addOnFailureListener {
                    emitError(it)
                    onResult(false)
                }
        }.addOnFailureListener {
            emitError(it)
            onResult(false)
        }
    }

    private fun clearOwnFamilyLink() {
        val current = user ?: return
        profileRef(current.uid).set(
            mapOf(
                "familyId" to "",
                "role" to "",
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).addOnSuccessListener {
            state = state.copy(
                profile = state.profile.copy(familyId = "", role = ""),
                family = null,
                members = emptyList(),
                sharedLists = emptyList()
            )
            notifyState()
        }.addOnFailureListener(::emitError)
    }

    private fun profileRef(uid: String) =
        db.collection("users").document(uid).collection("profile").document("main")

    private fun profileFromSnapshot(uid: String, data: Map<String, Any>): UserProfileRecord =
        UserProfileRecord(
            uid = uid,
            displayName = data["displayName"]?.toString().orEmpty(),
            email = data["email"]?.toString().orEmpty(),
            photoURL = data["photoURL"]?.toString().orEmpty(),
            familyId = data["familyId"]?.toString().orEmpty(),
            role = data["role"]?.toString().orEmpty()
        )

    private fun normalizeEmail(value: String?): String =
        value.orEmpty().trim().lowercase(Locale.ROOT)

    private fun emailDirectoryKey(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizeEmail(email).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun notifyState() {
        onState?.invoke(state)
    }

    private fun emitError(error: Throwable) {
        onError?.invoke(error)
    }
}
