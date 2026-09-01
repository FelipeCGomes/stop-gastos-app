package com.example.stop_fgastos.domain.repository;

import com.example.stop_fgastos.domain.model.FamilyState;
import com.example.stop_fgastos.domain.model.FinanceRecord;

public interface FamilyRepository {
    interface Listener {
        void onState(FamilyState state);
        void onError(Throwable error);
    }

    void start(Listener listener);
    void stop();
    void refresh();
    void createFamily(String name, ResultCallback<Void> callback);
    void inviteByEmail(String email, ResultCallback<Void> callback);
    void respondInvite(String inviteId, boolean accept, ResultCallback<Void> callback);
    void removeMember(String uid, ResultCallback<Void> callback);
    void transferOwnership(String uid, ResultCallback<Void> callback);
    void leaveFamily(ResultCallback<Void> callback);

    void createSharedList(String name, String store, ResultCallback<Void> callback);
    void updateSharedList(FinanceRecord list, ResultCallback<Void> callback);
    void deleteSharedList(String listId, ResultCallback<Void> callback);
    void addSharedItem(String listId, FinanceRecord item, ResultCallback<Void> callback);
    void updateSharedItem(String listId, FinanceRecord item, ResultCallback<Void> callback);
    void deleteSharedItem(String listId, String itemId, ResultCallback<Void> callback);
    void loadSharedItems(String listId, ResultCallback<java.util.List<FinanceRecord>> callback);
    void deleteFamilyDataForCurrentUser(ResultCallback<Void> callback);
}
