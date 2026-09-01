package com.example.stop_fgastos;

import android.app.Application;

import com.example.stop_fgastos.di.AppContainer;
import com.example.stop_fgastos.presentation.common.UiTheme;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public final class StopGastosApplication extends Application {
    private static final String FIREBASE_API_KEY = "AIzaSyCg9hI4SY9Uo1vrwdLSq-liiuGtgI0nmD8";
    private static final String FIREBASE_APPLICATION_ID = "1:363408500943:web:aa4d5ec09bb575fc2f2fba";
    private static final String FIREBASE_PROJECT_ID = "stopgastos";
    private static final String FIREBASE_STORAGE_BUCKET = "stopgastos.firebasestorage.app";
    private static final String FIREBASE_MESSAGING_SENDER_ID = "363408500943";

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        UiTheme.apply(this);
        ensureFirebaseInitialized();
        container = new AppContainer(this);
    }

    private void ensureFirebaseInitialized() {
        if (!FirebaseApp.getApps(this).isEmpty()) {
            return;
        }

        FirebaseOptions options = new FirebaseOptions.Builder()
                .setApiKey(FIREBASE_API_KEY)
                .setApplicationId(FIREBASE_APPLICATION_ID)
                .setProjectId(FIREBASE_PROJECT_ID)
                .setStorageBucket(FIREBASE_STORAGE_BUCKET)
                .setGcmSenderId(FIREBASE_MESSAGING_SENDER_ID)
                .build();

        FirebaseApp.initializeApp(this, options);
    }

    public AppContainer container() {
        return container;
    }
}
