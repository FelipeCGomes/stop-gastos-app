package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.UserSession;
import com.example.stop_fgastos.domain.repository.AuthRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

public final class AuthUseCase {
    private final AuthRepository repository;

    public AuthUseCase(AuthRepository repository) {
        this.repository = repository;
    }

    public UserSession currentUser() {
        return repository.currentUser();
    }

    public void signIn(String idToken, ResultCallback<UserSession> callback) {
        repository.signInWithGoogleToken(idToken, callback);
    }

    public void reauthenticate(String idToken, ResultCallback<UserSession> callback) {
        repository.reauthenticateWithGoogleToken(idToken, callback);
    }

    public void signOut() {
        repository.signOut();
    }

    public void deleteAuthenticatedUser(ResultCallback<Void> callback) {
        repository.deleteCurrentUser(callback);
    }
}
