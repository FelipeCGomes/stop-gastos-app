package com.example.stop_fgastos.domain.repository;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.model.FinanceState;

import java.util.List;

public interface FinanceRepository {
    interface Listener {
        void onState(FinanceState state);
        void onError(Throwable error);
    }

    void start(String uid, Listener listener);
    void stop();

    void upsert(FinanceSection section, FinanceRecord record, ResultCallback<Void> callback);
    void upsertAll(FinanceSection section, List<FinanceRecord> records, ResultCallback<Void> callback);
    void delete(FinanceSection section, String id, ResultCallback<Void> callback);

    void replaceTransactionPlan(
            FinanceRecord existing,
            List<FinanceRecord> replacement,
            ResultCallback<Void> callback
    );

    void replaceSourceTransactions(
            FinanceSection sourceSection,
            FinanceRecord source,
            String monthKeyOrNull,
            List<FinanceRecord> replacement,
            ResultCallback<Void> callback
    );

    void saveBillWithTransaction(
            FinanceRecord bill,
            FinanceRecord transaction,
            ResultCallback<Void> callback
    );

    void deleteAllUserData(String uid, ResultCallback<Void> callback);
}
