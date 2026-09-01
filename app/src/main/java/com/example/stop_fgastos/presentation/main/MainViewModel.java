package com.example.stop_fgastos.presentation.main;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.stop_fgastos.di.AppContainer;
import com.example.stop_fgastos.domain.model.FamilyState;
import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.model.FinanceState;
import com.example.stop_fgastos.domain.model.MarketComparison;
import com.example.stop_fgastos.domain.model.MonthlySummary;
import com.example.stop_fgastos.domain.model.TransactionInput;
import com.example.stop_fgastos.domain.model.UserSession;
import com.example.stop_fgastos.domain.repository.FamilyRepository;
import com.example.stop_fgastos.domain.repository.FinanceRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public final class MainViewModel extends ViewModel {
    private final AppContainer container;

    private final MutableLiveData<UserSession> user = new MutableLiveData<>(new UserSession("", "", "", ""));
    private final MutableLiveData<FinanceState> finance = new MutableLiveData<>(new FinanceState());
    private final MutableLiveData<FamilyState> family = new MutableLiveData<>(new FamilyState());
    private final MutableLiveData<String> error = new MutableLiveData<>("");
    private final MutableLiveData<String> sync = new MutableLiveData<>("Local");

    public MainViewModel(AppContainer container) {
        this.container = container;
        UserSession current = container.auth.currentUser();
        user.setValue(current);
        if (current.signedIn()) attach(current);
    }

    public LiveData<UserSession> user() { return user; }
    public LiveData<FinanceState> finance() { return finance; }
    public LiveData<FamilyState> family() { return family; }
    public LiveData<String> error() { return error; }
    public LiveData<String> sync() { return sync; }

    public void signIn(String idToken, ResultCallback<UserSession> callback) {
        sync.setValue("Entrando");
        container.auth.signIn(idToken, new ResultCallback<UserSession>() {
            @Override
            public void onSuccess(UserSession session) {
                user.setValue(session);
                attach(session);
                sync.setValue("Sincronizado");
                callback.onSuccess(session);
            }

            @Override
            public void onError(Throwable throwable) {
                setError(throwable);
                callback.onError(throwable);
            }
        });
    }

    public void signOut() {
        container.observeFinance.stop();
        container.family.stop();
        container.auth.signOut();
        user.setValue(new UserSession("", "", "", ""));
        finance.setValue(new FinanceState());
        family.setValue(new FamilyState());
        sync.setValue("Local");
    }

    public void saveTransaction(FinanceRecord existing, TransactionInput input) {
        FinanceState state = state();
        sync.setValue("Sincronizando");
        ResultCallback<Void> cb = writeCallback();
        try {
            if (existing == null) container.saveTransaction.create(state, input, cb);
            else container.saveTransaction.update(state, existing, input, cb);
        } catch (Throwable error) {
            setError(error);
        }
    }

    public void deleteTransaction(FinanceRecord record) {
        sync.setValue("Sincronizando");
        container.saveTransaction.delete(record, writeCallback());
    }

    public void payBill(FinanceRecord bill, double paidAmount) {
        sync.setValue("Sincronizando");
        container.payBill.execute(bill, paidAmount, writeCallback());
    }

    public void undoBillPayment(FinanceRecord bill) {
        sync.setValue("Sincronizando");
        container.payBill.undo(bill, writeCallback());
    }

    public void payTransaction(FinanceRecord transaction, double paidAmount) {
        sync.setValue("Sincronizando");
        container.transactionPayment.pay(transaction, paidAmount, writeCallback());
    }

    public void undoTransactionPayment(FinanceRecord transaction) {
        sync.setValue("Sincronizando");
        container.transactionPayment.undo(transaction, writeCallback());
    }

    public void saveRecord(FinanceSection section, FinanceRecord record) {
        sync.setValue("Sincronizando");
        container.manageFinance.save(section, record, writeCallback());
    }

    public void deleteRecord(FinanceSection section, FinanceRecord record) {
        sync.setValue("Sincronizando");
        container.manageFinance.delete(section, record, writeCallback());
    }

    public void saveRecurring(
            FinanceRecord existing,
            Map<String, Object> values
    ) {
        sync.setValue("Sincronizando");
        container.recurring.saveRecurringExpense(state(), existing, values, writeCallback());
    }

    public void saveIncomeSource(
            FinanceRecord existing,
            Map<String, Object> values
    ) {
        sync.setValue("Sincronizando");
        container.recurring.saveIncomeSource(state(), existing, values, writeCallback());
    }

    public void ensureMonth(YearMonth month) {
        container.recurring.ensureMonth(state(), month, new ResultCallback<Void>() {
            @Override public void onSuccess(Void value) {}
            @Override public void onError(Throwable throwable) { setError(throwable); }
        });
    }

    public MonthlySummary summary(YearMonth month) {
        return container.summary.monthly(state(), month);
    }

    public double accountBalance(String accountId) {
        return container.summary.accountBalance(state(), accountId);
    }

    public Map<String, Double> categoryExpenses(YearMonth month) {
        return container.summary.expenseByCategory(state(), month);
    }

    public MarketComparison compareShopping(List<FinanceRecord> lists) {
        return container.shoppingComparison.compare(lists);
    }

    public String reportCsv(YearMonth month) {
        return container.reports.csv(state(), month);
    }

    public void createFamily(String name) {
        container.family.create(name, writeCallback());
    }

    public void inviteFamily(String email) {
        container.family.invite(email, writeCallback());
    }

    public void respondInvite(String id, boolean accept) {
        container.family.respond(id, accept, writeCallback());
    }

    public void removeMember(String uid) {
        container.family.removeMember(uid, writeCallback());
    }

    public void transferOwnership(String uid) {
        container.family.transferOwnership(uid, writeCallback());
    }

    public void leaveFamily() {
        container.family.leave(writeCallback());
    }

    public void createSharedList(String name, String store) {
        container.family.createSharedList(name, store, writeCallback());
    }

    public void updateSharedList(FinanceRecord list) {
        container.family.updateSharedList(list, writeCallback());
    }

    public void deleteSharedList(String id) {
        container.family.deleteSharedList(id, writeCallback());
    }

    public void addSharedItem(String listId, FinanceRecord item) {
        container.family.addSharedItem(listId, item, writeCallback());
    }

    public void updateSharedItem(String listId, FinanceRecord item) {
        container.family.updateSharedItem(listId, item, writeCallback());
    }

    public void deleteSharedItem(String listId, String itemId) {
        container.family.deleteSharedItem(listId, itemId, writeCallback());
    }

    public void loadSharedItems(String listId, ResultCallback<List<FinanceRecord>> callback) {
        container.family.loadSharedItems(listId, callback);
    }

    public void refreshFamily() {
        container.family.refresh();
    }

    public void enableNotifications(ResultCallback<Void> callback) {
        container.notifications.enable(callback);
    }

    public void disableNotifications(ResultCallback<Void> callback) {
        container.notifications.disable(callback);
    }

    public void deleteAccount(String googleIdToken, ResultCallback<Void> callback) {
        UserSession current = user.getValue();
        if (current == null || !current.signedIn()) {
            callback.onSuccess(null);
            return;
        }

        sync.setValue("Excluindo conta");
        container.deleteAccount.execute(googleIdToken, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                container.observeFinance.stop();
                container.family.stop();
                user.setValue(new UserSession("", "", "", ""));
                finance.setValue(new FinanceState());
                family.setValue(new FamilyState());
                sync.setValue("Local");
                callback.onSuccess(null);
            }

            @Override
            public void onError(Throwable throwable) {
                setError(throwable);
                callback.onError(throwable);
            }
        });
    }

    private void attach(UserSession session) {
        sync.setValue("Sincronizando");
        container.observeFinance.start(session.uid(), new FinanceRepository.Listener() {
            @Override
            public void onState(FinanceState state) {
                finance.setValue(state);
                sync.setValue("Sincronizado");
            }

            @Override
            public void onError(Throwable throwable) {
                setError(throwable);
            }
        });

        container.family.start(new FamilyRepository.Listener() {
            @Override
            public void onState(FamilyState state) {
                family.setValue(state);
            }

            @Override
            public void onError(Throwable throwable) {
                setError(throwable);
            }
        });
    }

    private FinanceState state() {
        FinanceState value = finance.getValue();
        return value == null ? new FinanceState() : value;
    }

    private ResultCallback<Void> writeCallback() {
        return new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                sync.setValue("Sincronizado");
            }

            @Override
            public void onError(Throwable throwable) {
                setError(throwable);
            }
        };
    }

    private void setError(Throwable throwable) {
        sync.setValue("Erro");
        error.setValue(throwable.getMessage() == null
                ? "Falha ao processar a operação."
                : throwable.getMessage());
    }

    @Override
    protected void onCleared() {
        container.observeFinance.stop();
        container.family.stop();
        super.onCleared();
    }

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer container;

        public Factory(AppContainer container) {
            this.container = container;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(MainViewModel.class)) {
                return (T) new MainViewModel(container);
            }
            throw new IllegalArgumentException("ViewModel desconhecido: " + modelClass.getName());
        }
    }
}
