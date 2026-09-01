package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.FamilyState;
import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.repository.FamilyRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

public final class FamilyUseCase {
    private final FamilyRepository repository;

    public FamilyUseCase(FamilyRepository repository) {
        this.repository = repository;
    }

    public void start(FamilyRepository.Listener listener) { repository.start(listener); }
    public void stop() { repository.stop(); }
    public void refresh() { repository.refresh(); }
    public void create(String name, ResultCallback<Void> cb) { repository.createFamily(name, cb); }
    public void invite(String email, ResultCallback<Void> cb) { repository.inviteByEmail(email, cb); }
    public void respond(String inviteId, boolean accept, ResultCallback<Void> cb) { repository.respondInvite(inviteId, accept, cb); }
    public void removeMember(String uid, ResultCallback<Void> cb) { repository.removeMember(uid, cb); }
    public void transferOwnership(String uid, ResultCallback<Void> cb) { repository.transferOwnership(uid, cb); }
    public void leave(ResultCallback<Void> cb) { repository.leaveFamily(cb); }
    public void createSharedList(String name, String store, ResultCallback<Void> cb) { repository.createSharedList(name, store, cb); }
    public void updateSharedList(FinanceRecord list, ResultCallback<Void> cb) { repository.updateSharedList(list, cb); }
    public void deleteSharedList(String id, ResultCallback<Void> cb) { repository.deleteSharedList(id, cb); }
    public void addSharedItem(String listId, FinanceRecord item, ResultCallback<Void> cb) { repository.addSharedItem(listId, item, cb); }
    public void updateSharedItem(String listId, FinanceRecord item, ResultCallback<Void> cb) { repository.updateSharedItem(listId, item, cb); }
    public void deleteSharedItem(String listId, String itemId, ResultCallback<Void> cb) { repository.deleteSharedItem(listId, itemId, cb); }
    public void loadSharedItems(String listId, ResultCallback<java.util.List<FinanceRecord>> cb) { repository.loadSharedItems(listId, cb); }
    public void deleteCurrentFamilyLinks(ResultCallback<Void> cb) { repository.deleteFamilyDataForCurrentUser(cb); }
}
