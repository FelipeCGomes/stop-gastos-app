package com.example.stop_fgastos.data.notification;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.presentation.main.MainActivity;
import com.example.stop_fgastos.domain.repository.ResultCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class StopGastosMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || uid.isBlank()) return;

        new FirebaseNotificationRepository(
                getApplicationContext(),
                com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                FirebaseAuth.getInstance()
        ).saveToken(uid, token, new ResultCallback<Void>() {
            @Override public void onSuccess(Void value) {}
            @Override public void onError(Throwable error) {}
        });
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);

        FirebaseNotificationRepository notifications = new FirebaseNotificationRepository(
                getApplicationContext(),
                com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                FirebaseAuth.getInstance()
        );
        notifications.ensureChannel();

        String title = message.getNotification() != null
                ? message.getNotification().getTitle()
                : message.getData().getOrDefault("title", "Stop Gastos");
        String body = message.getNotification() != null
                ? message.getNotification().getBody()
                : message.getData().getOrDefault("body", "Você tem uma nova atualização.");

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                message.getMessageId() == null ? 0 : message.getMessageId().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this,
                FirebaseNotificationRepository.CHANNEL_ID
        )
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat.from(this).notify(
                    message.getMessageId() == null
                            ? (int) System.currentTimeMillis()
                            : message.getMessageId().hashCode(),
                    builder.build()
            );
        } catch (SecurityException ignored) {
        }
    }
}
