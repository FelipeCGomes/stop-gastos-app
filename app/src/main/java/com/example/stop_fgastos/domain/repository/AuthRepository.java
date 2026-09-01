package com.example.stop_fgastos.domain.repository;

import com.example.stop_fgastos.domain.model.UserSession;

public interface AuthRepository {
    UserSession currentUser();
    void signInWithGoogleToken(String idToken, ResultCallback<UserSession> callback);
    void reauthenticateWithGoogleToken(String idToken, ResultCallback<UserSession> callback);
    void signOut();
    void deleteCurrentUser(ResultCallback<Void> callback);
}
