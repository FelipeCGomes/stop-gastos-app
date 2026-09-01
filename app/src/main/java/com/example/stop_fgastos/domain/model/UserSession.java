package com.example.stop_fgastos.domain.model;

public final class UserSession {
    private final String uid;
    private final String displayName;
    private final String email;
    private final String photoUrl;

    public UserSession(String uid, String displayName, String email, String photoUrl) {
        this.uid = uid == null ? "" : uid;
        this.displayName = displayName == null ? "" : displayName;
        this.email = email == null ? "" : email;
        this.photoUrl = photoUrl == null ? "" : photoUrl;
    }

    public String uid() { return uid; }
    public String displayName() { return displayName; }
    public String email() { return email; }
    public String photoUrl() { return photoUrl; }
    public boolean signedIn() { return !uid.isBlank(); }
}
