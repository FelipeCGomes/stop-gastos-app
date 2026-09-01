package com.example.stop_fgastos.di;

import android.content.Context;

import com.example.stop_fgastos.data.firebase.FirebaseAuthRepository;
import com.example.stop_fgastos.data.firebase.FirestoreFamilyRepository;
import com.example.stop_fgastos.data.firebase.FirestoreFinanceRepository;
import com.example.stop_fgastos.data.notification.FirebaseNotificationRepository;
import com.example.stop_fgastos.domain.repository.AuthRepository;
import com.example.stop_fgastos.domain.repository.FamilyRepository;
import com.example.stop_fgastos.domain.repository.FinanceRepository;
import com.example.stop_fgastos.domain.repository.NotificationRepository;
import com.example.stop_fgastos.domain.usecase.AuthUseCase;
import com.example.stop_fgastos.domain.usecase.DeleteAccountUseCase;
import com.example.stop_fgastos.domain.usecase.FamilyUseCase;
import com.example.stop_fgastos.domain.usecase.FinanceSummaryUseCase;
import com.example.stop_fgastos.domain.usecase.ManageFinanceUseCase;
import com.example.stop_fgastos.domain.usecase.NotificationUseCase;
import com.example.stop_fgastos.domain.usecase.ObserveFinanceUseCase;
import com.example.stop_fgastos.domain.usecase.RecurringUseCase;
import com.example.stop_fgastos.domain.usecase.ReportUseCase;
import com.example.stop_fgastos.domain.usecase.SaveTransactionUseCase;
import com.example.stop_fgastos.domain.usecase.ShoppingComparisonUseCase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public final class AppContainer {
    public final AuthUseCase auth;
    public final ObserveFinanceUseCase observeFinance;
    public final ManageFinanceUseCase manageFinance;
    public final SaveTransactionUseCase saveTransaction;
    public final RecurringUseCase recurring;
    public final FinanceSummaryUseCase summary;
    public final ShoppingComparisonUseCase shoppingComparison;
    public final FamilyUseCase family;
    public final NotificationUseCase notifications;
    public final ReportUseCase reports;
    public final DeleteAccountUseCase deleteAccount;

    public AppContainer(Context context) {
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        AuthRepository authRepository = new FirebaseAuthRepository(firebaseAuth);
        FinanceRepository financeRepository = new FirestoreFinanceRepository(firestore);
        FamilyRepository familyRepository = new FirestoreFamilyRepository(firestore, firebaseAuth);
        NotificationRepository notificationRepository = new FirebaseNotificationRepository(
                context.getApplicationContext(),
                firestore,
                firebaseAuth
        );

        auth = new AuthUseCase(authRepository);
        observeFinance = new ObserveFinanceUseCase(financeRepository);
        manageFinance = new ManageFinanceUseCase(financeRepository);
        saveTransaction = new SaveTransactionUseCase(financeRepository);
        recurring = new RecurringUseCase(financeRepository, saveTransaction);
        summary = new FinanceSummaryUseCase();
        shoppingComparison = new ShoppingComparisonUseCase();
        family = new FamilyUseCase(familyRepository);
        notifications = new NotificationUseCase(notificationRepository);
        reports = new ReportUseCase();
        deleteAccount = new DeleteAccountUseCase(
                authRepository,
                familyRepository,
                financeRepository
        );
    }
}
