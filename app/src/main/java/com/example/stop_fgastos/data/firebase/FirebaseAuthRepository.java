package com.example.stop_fgastos.data.firebase;

import com.example.stop_fgastos.domain.model.UserSession;
import com.example.stop_fgastos.domain.repository.AuthRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public final class FirebaseAuthRepository implements AuthRepository {
    private final FirebaseAuth auth;

    public FirebaseAuthRepository(FirebaseAuth auth) {
        this.auth = auth;
    }

    @Override
    public UserSession currentUser() {
        return map(auth.getCurrentUser());
    }

    @Override
    public void signInWithGoogleToken(String idToken, ResultCallback<UserSession> callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> callback.onSuccess(map(result.getUser())))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void reauthenticateWithGoogleToken(String idToken, ResultCallback<UserSession> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("Usuário não autenticado."));
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        user.reauthenticate(credential)
                .addOnSuccessListener(result -> callback.onSuccess(map(user)))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void signOut() {
        auth.signOut();
    }

    @Override
    public void deleteCurrentUser(ResultCallback<Void> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onSuccess(null);
            return;
        }
        user.delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    private UserSession map(FirebaseUser user) {
        if (user == null) return new UserSession("", "", "", "");
        return new UserSession(
                user.getUid(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString()
        );
    }
}
