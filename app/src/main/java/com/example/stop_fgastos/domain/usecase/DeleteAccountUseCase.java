package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.UserSession;
import com.example.stop_fgastos.domain.repository.AuthRepository;
import com.example.stop_fgastos.domain.repository.FamilyRepository;
import com.example.stop_fgastos.domain.repository.FinanceRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

public final class DeleteAccountUseCase {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;
    private final FinanceRepository financeRepository;

    public DeleteAccountUseCase(
            AuthRepository authRepository,
            FamilyRepository familyRepository,
            FinanceRepository financeRepository
    ) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;
        this.financeRepository = financeRepository;
    }

    public void execute(String googleIdToken, ResultCallback<Void> callback) {
        UserSession current = authRepository.currentUser();
        if (!current.signedIn()) {
            callback.onSuccess(null);
            return;
        }

        authRepository.reauthenticateWithGoogleToken(googleIdToken, new ResultCallback<UserSession>() {
            @Override
            public void onSuccess(UserSession value) {
                familyRepository.deleteFamilyDataForCurrentUser(new ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        financeRepository.deleteAllUserData(current.uid(), new ResultCallback<Void>() {
                            @Override
                            public void onSuccess(Void ignoredAgain) {
                                authRepository.deleteCurrentUser(callback);
                            }

                            @Override
                            public void onError(Throwable error) {
                                callback.onError(error);
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                callback.onError(error);
            }
        });
    }
}
