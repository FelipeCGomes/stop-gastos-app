package com.example.stop_fgastos.data.firebase;

import com.example.stop_fgastos.domain.model.FamilyState;
import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.repository.FamilyRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FirestoreFamilyRepository implements FamilyRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final List<ListenerRegistration> listeners = new ArrayList<>();

    private Listener listener;
    private String familyId = "";
    private String familyName = "";
    private String ownerUid = "";
    private String role = "";
    private List<FinanceRecord> members = new ArrayList<>();
    private List<FinanceRecord> invitations = new ArrayList<>();
    private List<FinanceRecord> sharedLists = new ArrayList<>();

    public FirestoreFamilyRepository(FirebaseFirestore db, FirebaseAuth auth) {
        this.db = db;
        this.auth = auth;
    }

    @Override
    public void start(Listener listener) {
        stop();
        this.listener = listener;

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitError(new IllegalStateException("Usuário não autenticado."));
            return;
        }

        ensureProfile(user, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                watchProfile(user.getUid());
                watchInvitations(user.getUid());
                refresh();
            }

            @Override
            public void onError(Throwable error) {
                emitError(error);
            }
        });
    }

    @Override
    public void stop() {
        for (ListenerRegistration registration : listeners) registration.remove();
        listeners.clear();
        listener = null;
        familyId = "";
        familyName = "";
        ownerUid = "";
        role = "";
        members = new ArrayList<>();
        invitations = new ArrayList<>();
        sharedLists = new ArrayList<>();
    }

    @Override
    public void refresh() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        profileRef(user.getUid()).get()
                .addOnSuccessListener(profile -> {
                    familyId = string(profile.get("familyId"));
                    role = string(profile.get("role"));

                    if (familyId.isBlank()) {
                        familyName = "";
                        ownerUid = "";
                        members = new ArrayList<>();
                        sharedLists = new ArrayList<>();
                        emitState();
                        return;
                    }

                    loadFamilyContext(familyId);
                })
                .addOnFailureListener(this::emitError);
    }

    @Override
    public void createFamily(String name, ResultCallback<Void> callback) {
        FirebaseUser user = requireUser(callback);
        if (user == null) return;

        String clean = name == null ? "" : name.trim();
        if (clean.length() < 2) {
            callback.onError(new IllegalArgumentException("Informe o nome da família."));
            return;
        }
        if (!familyId.isBlank()) {
            callback.onError(new IllegalStateException("Você já participa de uma família."));
            return;
        }

        String id = UUID.randomUUID().toString();
        DocumentReference familyRef = db.collection("families").document(id);
        DocumentReference memberRef = familyRef.collection("members").document(user.getUid());

        Map<String, Object> family = new LinkedHashMap<>();
        family.put("name", clean);
        family.put("ownerUid", user.getUid());
        family.put("createdAt", FieldValue.serverTimestamp());
        family.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> member = userMap(user);
        member.put("role", "admin");
        member.put("status", "active");
        member.put("joinedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.set(familyRef, family, SetOptions.merge());
        batch.set(memberRef, member, SetOptions.merge());
        batch.set(
                profileRef(user.getUid()),
                Map.of(
                        "familyId", id,
                        "role", "admin",
                        "updatedAt", FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
        );
        batch.commit()
                .addOnSuccessListener(unused -> {
                    refresh();
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void inviteByEmail(String email, ResultCallback<Void> callback) {
        FirebaseUser user = requireUser(callback);
        if (user == null) return;

        if (familyId.isBlank()) {
            callback.onError(new IllegalStateException("Crie ou participe de uma família."));
            return;
        }
        if (!"admin".equals(role)) {
            callback.onError(new IllegalStateException("Apenas o administrador pode convidar."));
            return;
        }

        String normalized = normalizeEmail(email);
        if (normalized.isBlank() || !normalized.contains("@")) {
            callback.onError(new IllegalArgumentException("Informe um e-mail válido."));
            return;
        }
        if (normalized.equals(normalizeEmail(user.getEmail()))) {
            callback.onError(new IllegalArgumentException("Você já participa da família."));
            return;
        }

        db.collection("userDirectory").document(emailHash(normalized)).get()
                .addOnSuccessListener(target -> {
                    if (!target.exists()) {
                        callback.onError(new IllegalStateException(
                                "Essa conta ainda não entrou no Stop Gastos."
                        ));
                        return;
                    }

                    String targetUid = string(target.get("uid"));
                    if (targetUid.isBlank()) {
                        callback.onError(new IllegalStateException("Conta não localizada."));
                        return;
                    }

                    DocumentReference memberRef = db.collection("families")
                            .document(familyId)
                            .collection("members")
                            .document(targetUid);

                    memberRef.get().addOnSuccessListener(memberSnapshot -> {
                        if (memberSnapshot.exists()) {
                            String status = string(memberSnapshot.get("status"));
                            if ("active".equals(status)) {
                                callback.onError(new IllegalStateException("Essa pessoa já é membro."));
                                return;
                            }
                            if ("pending".equals(status)) {
                                callback.onError(new IllegalStateException("Já existe um convite pendente."));
                                return;
                            }
                        }

                        String requestId = UUID.randomUUID().toString();
                        Map<String, Object> member = new LinkedHashMap<>();
                        member.put("uid", targetUid);
                        member.put("displayName", string(target.get("displayName")));
                        member.put("email", normalized);
                        member.put("photoURL", string(target.get("photoURL")));
                        member.put("role", "member");
                        member.put("status", "pending");
                        member.put("invitedBy", user.getUid());
                        member.put("invitedByName", safe(user.getDisplayName()));
                        member.put("invitedAt", FieldValue.serverTimestamp());
                        member.put("updatedAt", FieldValue.serverTimestamp());

                        Map<String, Object> request = new LinkedHashMap<>();
                        request.put("requestId", requestId);
                        request.put("familyId", familyId);
                        request.put("familyName", familyName.isBlank() ? "Família" : familyName);
                        request.put("targetUid", targetUid);
                        request.put("targetEmail", normalized);
                        request.put("createdBy", user.getUid());
                        request.put("createdByName", safe(user.getDisplayName()));
                        request.put("status", "pending");
                        request.put(
                                "expiresAt",
                                new Timestamp(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
                        );
                        request.put("createdAt", FieldValue.serverTimestamp());
                        request.put("updatedAt", FieldValue.serverTimestamp());

                        WriteBatch batch = db.batch();
                        batch.set(memberRef, member, SetOptions.merge());
                        batch.set(db.collection("familyRequests").document(requestId), request);
                        batch.commit()
                                .addOnSuccessListener(unused -> callback.onSuccess(null))
                                .addOnFailureListener(callback::onError);
                    }).addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void respondInvite(String inviteId, boolean accept, ResultCallback<Void> callback) {
        FirebaseUser user = requireUser(callback);
        if (user == null) return;

        DocumentReference requestRef = db.collection("familyRequests").document(inviteId);
        requestRef.get().addOnSuccessListener(request -> {
            if (!request.exists()) {
                callback.onError(new IllegalStateException("Convite não encontrado."));
                return;
            }
            if (!user.getUid().equals(string(request.get("targetUid")))) {
                callback.onError(new IllegalStateException("Este convite pertence a outra conta."));
                return;
            }
            if (!"pending".equals(string(request.get("status")))) {
                callback.onError(new IllegalStateException("Convite já respondido."));
                return;
            }

            Timestamp expiry = request.getTimestamp("expiresAt");
            if (expiry != null && expiry.toDate().before(new Date())) {
                callback.onError(new IllegalStateException("Convite expirado."));
                return;
            }

            String targetFamily = string(request.get("familyId"));
            if (accept && !familyId.isBlank() && !familyId.equals(targetFamily)) {
                callback.onError(new IllegalStateException("Você já participa de outra família."));
                return;
            }

            DocumentReference memberRef = db.collection("families")
                    .document(targetFamily)
                    .collection("members")
                    .document(user.getUid());

            Map<String, Object> member = userMap(user);
            member.put("role", "member");
            member.put("status", accept ? "active" : "declined");
            member.put("responseAt", FieldValue.serverTimestamp());
            member.put("updatedAt", FieldValue.serverTimestamp());
            if (accept) member.put("joinedAt", FieldValue.serverTimestamp());

            WriteBatch batch = db.batch();
            batch.set(memberRef, member, SetOptions.merge());
            batch.set(
                    requestRef,
                    Map.of(
                            "status", accept ? "accepted" : "declined",
                            "respondedAt", FieldValue.serverTimestamp(),
                            "updatedAt", FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
            );
            if (accept) {
                batch.set(
                        profileRef(user.getUid()),
                        Map.of(
                                "familyId", targetFamily,
                                "role", "member",
                                "updatedAt", FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                );
            }

            batch.commit()
                    .addOnSuccessListener(unused -> {
                        refresh();
                        callback.onSuccess(null);
                    })
                    .addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }

    @Override
    public void removeMember(String uid, ResultCallback<Void> callback) {
        FirebaseUser user = requireUser(callback);
        if (user == null) return;

        if (!"admin".equals(role)) {
            callback.onError(new IllegalStateException("Apenas o administrador pode remover membros."));
            return;
        }
        if (uid.equals(ownerUid)) {
            callback.onError(new IllegalStateException("O proprietário não pode ser removido."));
            return;
        }

        WriteBatch batch = db.batch();
        batch.delete(
                db.collection("families")
                        .document(familyId)
                        .collection("members")
                        .document(uid)
        );
        batch.set(
                profileRef(uid),
                Map.of(
                        "familyId", "",
                        "role", "",
                        "updatedAt", FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
        );
        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void transferOwnership(String uid, ResultCallback<Void> callback) {
        FirebaseUser user = requireUser(callback);
        if (user == null) return;

        if (!user.getUid().equals(ownerUid) || !"admin".equals(role)) {
            callback.onError(new IllegalStateException("Somente o proprietário pode transferir."));
            return;
        }
        if (uid.equals(user.getUid())) {
            callback.onError(new IllegalArgumentException("Você já é o proprietário."));
            return;
        }

        DocumentReference familyRef = db.collection("families").document(familyId);
        WriteBatch batch = db.batch();
        batch.set(familyRef, Map.of(
                "ownerUid", uid,
                "updatedAt", FieldValue.serverTimestamp()
        ), SetOptions.merge());
        batch.set(
                familyRef.collection("members").document(uid),
                Map.of("role", "admin", "updatedAt", FieldValue.serverTimestamp()),
                SetOptions.merge()
        );
        batch.set(
                familyRef.collection("members").document(user.getUid()),
                Map.of("role", "member", "updatedAt", FieldValue.serverTimestamp()),
                SetOptions.merge()
        );
        batch.set(
                profileRef(uid),
                Map.of("familyId", familyId, "role", "admin", "updatedAt", FieldValue.serverTimestamp()),
                SetOptions.merge()
        );
        batch.set(
                profileRef(user.getUid()),
                Map.of("familyId", familyId, "role", "member", "updatedAt", FieldValue.serverTimestamp()),
                SetOptions.merge()
        );
        batch.commit()
                .addOnSuccessListener(unused -> {
                    refresh();
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void leaveFamily(ResultCallback<Void> callback) {
        FirebaseUser user = requireUser(callback);
        if (user == null) return;

        if (familyId.isBlank()) {
            callback.onSuccess(null);
            return;
        }
        if (user.getUid().equals(ownerUid)) {
            callback.onError(new IllegalStateException(
                    "Transfira a administração antes de sair da família."
            ));
            return;
        }

        WriteBatch batch = db.batch();
        batch.delete(
                db.collection("families")
                        .document(familyId)
                        .collection("members")
                        .document(user.getUid())
        );
        batch.set(
                profileRef(user.getUid()),
                Map.of(
                        "familyId", "",
                        "role", "",
                        "updatedAt", FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
        );
        batch.commit()
                .addOnSuccessListener(unused -> {
                    refresh();
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void createSharedList(String name, String store, ResultCallback<Void> callback) {
        FirebaseUser user = requireUser(callback);
        if (user == null) return;
        if (familyId.isBlank()) {
            callback.onError(new IllegalStateException("Você precisa participar de uma família."));
            return;
        }

        DocumentReference ref = db.collection("families")
                .document(familyId)
                .collection("shoppingLists")
                .document();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", ref.getId());
        payload.put("familyId", familyId);
        payload.put("name", name == null ? "" : name.trim());
        payload.put("store", store == null ? "" : store.trim());
        payload.put("createdBy", user.getUid());
        payload.put("createdByName", safe(user.getDisplayName()));
        payload.put("createdAt", FieldValue.serverTimestamp());
        payload.put("updatedAt", FieldValue.serverTimestamp());
        payload.put("clientUpdatedAt", Instant.now().toString());

        ref.set(payload)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void updateSharedList(FinanceRecord list, ResultCallback<Void> callback) {
        if (familyId.isBlank()) {
            callback.onError(new IllegalStateException("Família não encontrada."));
            return;
        }

        db.collection("families")
                .document(familyId)
                .collection("shoppingLists")
                .document(list.id())
                .set(
                        Map.of(
                                "name", list.text("name"),
                                "store", list.text("store"),
                                "updatedAt", FieldValue.serverTimestamp(),
                                "clientUpdatedAt", Instant.now().toString()
                        ),
                        SetOptions.merge()
                )
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void deleteSharedList(String listId, ResultCallback<Void> callback) {
        if (familyId.isBlank()) {
            callback.onError(new IllegalStateException("Família não encontrada."));
            return;
        }

        DocumentReference ref = db.collection("families")
                .document(familyId)
                .collection("shoppingLists")
                .document(listId);

        ref.collection("items").get()
                .addOnSuccessListener(items -> {
                    WriteBatch batch = db.batch();
                    items.getDocuments().forEach(document -> batch.delete(document.getReference()));
                    batch.delete(ref);
                    batch.commit()
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void addSharedItem(String listId, FinanceRecord item, ResultCallback<Void> callback) {
        writeSharedItem(listId, item, true, callback);
    }

    @Override
    public void updateSharedItem(String listId, FinanceRecord item, ResultCallback<Void> callback) {
        writeSharedItem(listId, item, false, callback);
    }

    @Override
    public void deleteSharedItem(String listId, String itemId, ResultCallback<Void> callback) {
        if (familyId.isBlank()) {
            callback.onError(new IllegalStateException("Família não encontrada."));
            return;
        }

        db.collection("families")
                .document(familyId)
                .collection("shoppingLists")
                .document(listId)
                .collection("items")
                .document(itemId)
                .delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void loadSharedItems(String listId, ResultCallback<List<FinanceRecord>> callback) {
        if (familyId.isBlank()) {
            callback.onError(new IllegalStateException("Família não encontrada."));
            return;
        }

        db.collection("families")
                .document(familyId)
                .collection("shoppingLists")
                .document(listId)
                .collection("items")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<FinanceRecord> result = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Map<String, Object> map = document.getData();
                        if (map == null) map = new LinkedHashMap<>();
                        map = new LinkedHashMap<>(map);
                        map.put("id", document.getId());
                        result.add(new FinanceRecord(document.getId(), map));
                    }
                    callback.onSuccess(result);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void deleteFamilyDataForCurrentUser(ResultCallback<Void> callback) {
        FirebaseUser user = requireUser(callback);
        if (user == null) return;

        if (familyId.isBlank()) {
            callback.onSuccess(null);
            return;
        }

        boolean owner = user.getUid().equals(ownerUid);
        long otherMembers = members.stream()
                .filter(member -> !member.id().equals(user.getUid()))
                .count();

        if (owner && otherMembers > 0) {
            callback.onError(new IllegalStateException(
                    "Transfira a administração ou remova os demais membros antes de excluir a conta."
            ));
            return;
        }

        if (!owner) {
            leaveFamily(callback);
            return;
        }

        DocumentReference familyRef = db.collection("families").document(familyId);
        familyRef.collection("shoppingLists").get()
                .addOnSuccessListener(listsSnapshot -> {
                    if (listsSnapshot.isEmpty()) {
                        deleteOwnedFamilyDocument(familyRef, user, callback);
                        return;
                    }

                    final int[] remaining = {listsSnapshot.size()};
                    final Throwable[] failure = {null};

                    for (DocumentSnapshot list : listsSnapshot.getDocuments()) {
                        list.getReference().collection("items").get()
                                .addOnSuccessListener(items -> {
                                    WriteBatch batch = db.batch();
                                    items.getDocuments().forEach(document -> batch.delete(document.getReference()));
                                    batch.delete(list.getReference());
                                    batch.commit().addOnFailureListener(error -> failure[0] = error)
                                            .addOnCompleteListener(task -> {
                                                remaining[0]--;
                                                if (remaining[0] == 0) {
                                                    if (failure[0] != null) callback.onError(failure[0]);
                                                    else deleteOwnedFamilyDocument(familyRef, user, callback);
                                                }
                                            });
                                })
                                .addOnFailureListener(error -> {
                                    failure[0] = error;
                                    remaining[0]--;
                                    if (remaining[0] == 0) callback.onError(failure[0]);
                                });
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    private void writeSharedItem(
            String listId,
            FinanceRecord item,
            boolean create,
            ResultCallback<Void> callback
    ) {
        FirebaseUser user = requireUser(callback);
        if (user == null) return;
        if (familyId.isBlank()) {
            callback.onError(new IllegalStateException("Família não encontrada."));
            return;
        }

        String itemId = create || item.id().isBlank()
                ? UUID.randomUUID().toString()
                : item.id();

        Map<String, Object> payload = new LinkedHashMap<>(item.fields());
        payload.put("id", itemId);
        payload.put("createdBy", payload.getOrDefault("createdBy", user.getUid()));
        payload.put("createdByName", payload.getOrDefault("createdByName", safe(user.getDisplayName())));
        payload.put("updatedBy", user.getUid());
        payload.put("updatedByName", safe(user.getDisplayName()));
        payload.put("updatedAt", FieldValue.serverTimestamp());
        payload.put("clientUpdatedAt", Instant.now().toString());

        db.collection("families")
                .document(familyId)
                .collection("shoppingLists")
                .document(listId)
                .collection("items")
                .document(itemId)
                .set(payload, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    private void watchProfile(String uid) {
        listeners.add(
                profileRef(uid).addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        emitError(error);
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) return;

                    String newFamilyId = string(snapshot.get("familyId"));
                    String newRole = string(snapshot.get("role"));
                    boolean changed = !newFamilyId.equals(familyId) || !newRole.equals(role);
                    familyId = newFamilyId;
                    role = newRole;

                    if (familyId.isBlank()) {
                        familyName = "";
                        ownerUid = "";
                        members = new ArrayList<>();
                        sharedLists = new ArrayList<>();
                        emitState();
                    } else if (changed) {
                        loadFamilyContext(familyId);
                    }
                })
        );
    }

    private void watchInvitations(String uid) {
        listeners.add(
                db.collection("familyRequests")
                        .whereEqualTo("targetUid", uid)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) {
                                emitError(error);
                                return;
                            }

                            List<FinanceRecord> result = new ArrayList<>();
                            if (snapshot != null) {
                                for (DocumentSnapshot document : snapshot.getDocuments()) {
                                    if (!"pending".equals(string(document.get("status")))) continue;
                                    Timestamp expires = document.getTimestamp("expiresAt");
                                    if (expires != null && expires.toDate().before(new Date())) continue;

                                    Map<String, Object> map = document.getData();
                                    if (map == null) map = new LinkedHashMap<>();
                                    map = new LinkedHashMap<>(map);
                                    map.put("id", document.getId());
                                    result.add(new FinanceRecord(document.getId(), map));
                                }
                            }
                            invitations = result;
                            emitState();
                        })
        );
    }

    private void loadFamilyContext(String id) {
        db.collection("families").document(id).get()
                .addOnSuccessListener(family -> {
                    if (!family.exists()) {
                        clearOwnProfile();
                        return;
                    }

                    familyName = string(family.get("name"));
                    ownerUid = string(family.get("ownerUid"));

                    db.collection("families")
                            .document(id)
                            .collection("members")
                            .get()
                            .addOnSuccessListener(memberSnapshot -> {
                                List<FinanceRecord> memberRecords = new ArrayList<>();
                                for (DocumentSnapshot document : memberSnapshot.getDocuments()) {
                                    Map<String, Object> map = document.getData();
                                    if (map == null) map = new LinkedHashMap<>();
                                    map = new LinkedHashMap<>(map);
                                    map.put("id", document.getId());
                                    memberRecords.add(new FinanceRecord(document.getId(), map));
                                }
                                members = memberRecords;
                                loadSharedLists(id);
                            })
                            .addOnFailureListener(this::emitError);
                })
                .addOnFailureListener(this::emitError);
    }

    private void loadSharedLists(String id) {
        db.collection("families")
                .document(id)
                .collection("shoppingLists")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<FinanceRecord> lists = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Map<String, Object> map = document.getData();
                        if (map == null) map = new LinkedHashMap<>();
                        map = new LinkedHashMap<>(map);
                        map.put("id", document.getId());
                        lists.add(new FinanceRecord(document.getId(), map));
                    }
                    sharedLists = lists;
                    emitState();
                })
                .addOnFailureListener(this::emitError);
    }

    private void ensureProfile(FirebaseUser user, ResultCallback<Void> callback) {
        DocumentReference ref = profileRef(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            String existingFamily = string(snapshot.get("familyId"));
            String existingRole = string(snapshot.get("role"));

            Map<String, Object> profile = userMap(user);
            profile.put("familyId", existingFamily);
            profile.put("role", existingRole);
            profile.put("updatedAt", FieldValue.serverTimestamp());

            WriteBatch batch = db.batch();
            batch.set(ref, profile, SetOptions.merge());
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                batch.set(
                        db.collection("userDirectory").document(emailHash(user.getEmail())),
                        Map.of(
                                "uid", user.getUid(),
                                "email", normalizeEmail(user.getEmail()),
                                "displayName", safe(user.getDisplayName()),
                                "photoURL", user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString(),
                                "updatedAt", FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                );
            }

            batch.commit()
                    .addOnSuccessListener(unused -> callback.onSuccess(null))
                    .addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }

    private void deleteOwnedFamilyDocument(
            DocumentReference familyRef,
            FirebaseUser user,
            ResultCallback<Void> callback
    ) {
        WriteBatch batch = db.batch();
        batch.delete(familyRef.collection("members").document(user.getUid()));
        batch.delete(familyRef);
        batch.set(
                profileRef(user.getUid()),
                Map.of(
                        "familyId", "",
                        "role", "",
                        "updatedAt", FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
        );
        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    private void clearOwnProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        profileRef(user.getUid()).set(
                Map.of(
                        "familyId", "",
                        "role", "",
                        "updatedAt", FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
        );
    }

    private DocumentReference profileRef(String uid) {
        return db.collection("users")
                .document(uid)
                .collection("profile")
                .document("main");
    }

    private Map<String, Object> userMap(FirebaseUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uid", user.getUid());
        map.put("displayName", safe(user.getDisplayName()));
        map.put("email", normalizeEmail(user.getEmail()));
        map.put("photoURL", user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString());
        map.put("updatedAt", FieldValue.serverTimestamp());
        return map;
    }

    private FirebaseUser requireUser(ResultCallback<?> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("Usuário não autenticado."));
            return null;
        }
        return user;
    }

    private void emitState() {
        if (listener == null) return;
        listener.onState(new FamilyState(
                familyId,
                familyName,
                ownerUid,
                role,
                members,
                invitations,
                sharedLists
        ));
    }

    private void emitError(Throwable error) {
        if (listener != null) listener.onError(error);
    }

    private String emailHash(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizeEmail(email).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte value : digest) out.append(String.format("%02x", value & 0xff));
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Não foi possível gerar o hash do e-mail.", error);
        }
    }

    private String normalizeEmail(String email) {
        return safe(email).trim().toLowerCase(Locale.ROOT);
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
