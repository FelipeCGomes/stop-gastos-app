package com.example.stop_fgastos.data.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.example.stop_fgastos.domain.repository.NotificationRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FirebaseNotificationRepository implements NotificationRepository {
    public static final String CHANNEL_ID = "stop_gastos_general";

    private final Context context;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public FirebaseNotificationRepository(
            Context context,
            FirebaseFirestore db,
            FirebaseAuth auth
    ) {
        this.context = context.getApplicationContext();
        this.db = db;
        this.auth = auth;
        ensureChannel();
    }

    @Override
    public void enable(ResultCallback<Void> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("Usuário não autenticado."));
            return;
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> saveToken(user.getUid(), token, callback))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void disable(ResultCallback<Void> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onSuccess(null);
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .collection("devices")
                .document(deviceId())
                .set(
                        Map.of(
                                "enabled", false,
                                "updatedAt", FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                )
                .addOnSuccessListener(unused -> {
                    FirebaseMessaging.getInstance().deleteToken();
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    public void saveToken(String uid, String token, ResultCallback<Void> callback) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("enabled", true);
        payload.put("platform", "android");
        payload.put("manufacturer", Build.MANUFACTURER);
        payload.put("model", Build.MODEL);
        payload.put("sdkInt", Build.VERSION.SDK_INT);
        payload.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(uid)
                .collection("devices")
                .document(deviceId())
                .set(payload, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void ensureChannel() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Stop Gastos",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Alertas financeiros, convites e lembretes.");
        manager.createNotificationChannel(channel);
    }

    private String deviceId() {
        String value = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
        return value == null || value.isBlank()
                ? "android-" + Build.MODEL.replace(" ", "_")
                : "android-" + value;
    }
}
