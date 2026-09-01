package com.example.stop_fgastos.data.firebase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.model.FinanceState;
import com.example.stop_fgastos.domain.repository.FinanceRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FirestoreFinanceRepository implements FinanceRepository {
    private final FirebaseFirestore db;
    private final List<ListenerRegistration> listeners = new CopyOnWriteArrayList<>();
    private String uid = "";
    private Listener listener;
    private FinanceState state = new FinanceState();

    public FirestoreFinanceRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void start(String uid, Listener listener) {
        stop();
        this.uid = uid == null ? "" : uid;
        this.listener = listener;
        this.state = new FinanceState();

        if (this.uid.isBlank()) {
            listener.onError(new IllegalStateException("Usuário não autenticado."));
            return;
        }

        for (FinanceSection section : FinanceSection.values()) {
            DocumentReference ref = sectionRef(section);
            ListenerRegistration registration = ref.addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    if (this.listener != null) this.listener.onError(error);
                    return;
                }
                List<FinanceRecord> records = FirebaseRecordMapper.readRecords(snapshot);
                state = state.withSection(section, records);
                if (this.listener != null) this.listener.onState(state);
            });
            listeners.add(registration);
        }
    }

    @Override
    public void stop() {
        for (ListenerRegistration registration : listeners) registration.remove();
        listeners.clear();
        uid = "";
        listener = null;
        state = new FinanceState();
    }

    @Override
    public void upsert(
            FinanceSection section,
            FinanceRecord record,
            ResultCallback<Void> callback
    ) {
        mutateSection(section, list -> upsertInMemory(list, record), callback);
    }

    @Override
    public void upsertAll(
            FinanceSection section,
            List<FinanceRecord> records,
            ResultCallback<Void> callback
    ) {
        mutateSection(section, list -> {
            for (FinanceRecord record : records) upsertInMemory(list, record);
        }, callback);
    }

    @Override
    public void delete(
            FinanceSection section,
            String id,
            ResultCallback<Void> callback
    ) {
        mutateSection(section, list -> list.removeIf(record -> record.id().equals(id)), callback);
    }

    @Override
    public void replaceTransactionPlan(
            FinanceRecord existing,
            List<FinanceRecord> replacement,
            ResultCallback<Void> callback
    ) {
        mutateSection(FinanceSection.TRANSACTIONS, list -> {
            String group = existing.text("installmentGroup");
            if (!group.isBlank()) {
                list.removeIf(record -> group.equals(record.text("installmentGroup")));
            } else {
                list.removeIf(record -> record.id().equals(existing.id()));
            }
            list.addAll(replacement);
        }, callback);
    }

    @Override
    public void replaceSourceTransactions(
            FinanceSection sourceSection,
            FinanceRecord source,
            String monthKeyOrNull,
            List<FinanceRecord> replacement,
            ResultCallback<Void> callback
    ) {
        if (uid.isBlank()) {
            callback.onError(new IllegalStateException("Usuário não autenticado."));
            return;
        }

        DocumentReference sourceRef = sectionRef(sourceSection);
        DocumentReference txRef = sectionRef(FinanceSection.TRANSACTIONS);

        db.runTransaction(transaction -> {
            List<FinanceRecord> sources = FirebaseRecordMapper.readRecords(transaction.get(sourceRef));
            List<FinanceRecord> transactions = FirebaseRecordMapper.readRecords(transaction.get(txRef));

            upsertInMemory(sources, source);
            transactions.removeIf(record -> {
                boolean sameSource = source.id().equals(record.text("sourceRecurringId"));
                if (!sameSource) return false;
                return monthKeyOrNull == null || record.text("date").startsWith(monthKeyOrNull);
            });
            transactions.addAll(replacement);

            transaction.set(
                    sourceRef,
                    Map.of(
                            "value", FirebaseRecordMapper.toMaps(sources),
                            "updatedAt", FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
            );
            transaction.set(
                    txRef,
                    Map.of(
                            "value", FirebaseRecordMapper.toMaps(transactions),
                            "updatedAt", FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
            );
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void deleteAllUserData(String uid, ResultCallback<Void> callback) {
        if (uid == null || uid.isBlank()) {
            callback.onSuccess(null);
            return;
        }

        db.collection("users").document(uid).collection("data").get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = db.batch();
                    snapshot.getDocuments().forEach(document -> batch.delete(document.getReference()));
                    batch.delete(db.collection("users").document(uid).collection("state").document("main"));
                    batch.delete(db.collection("users").document(uid).collection("profile").document("main"));
                    batch.commit()
                            .addOnSuccessListener(unused -> deleteDevices(uid, callback))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    private void deleteDevices(String uid, ResultCallback<Void> callback) {
        db.collection("users").document(uid).collection("devices").get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = db.batch();
                    snapshot.getDocuments().forEach(document -> batch.delete(document.getReference()));
                    batch.commit()
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    private interface SectionMutation {
        void apply(List<FinanceRecord> records);
    }

    private void mutateSection(
            FinanceSection section,
            SectionMutation mutation,
            ResultCallback<Void> callback
    ) {
        if (uid.isBlank()) {
            callback.onError(new IllegalStateException("Usuário não autenticado."));
            return;
        }

        DocumentReference ref = sectionRef(section);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            List<FinanceRecord> records = new ArrayList<>(FirebaseRecordMapper.readRecords(snapshot));
            mutation.apply(records);

            Object value;
            if (section == FinanceSection.SETTINGS && records.size() == 1) {
                value = records.get(0).toMap();
            } else {
                value = FirebaseRecordMapper.toMaps(records);
            }

            transaction.set(
                    ref,
                    Map.of(
                            "value", value,
                            "updatedAt", FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
            );
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    private DocumentReference sectionRef(FinanceSection section) {
        return db.collection("users")
                .document(uid)
                .collection("data")
                .document(section.firestoreId());
    }

    private void upsertInMemory(List<FinanceRecord> list, FinanceRecord record) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(record.id())) {
                list.set(i, record);
                return;
            }
        }
        list.add(record);
    }
}
