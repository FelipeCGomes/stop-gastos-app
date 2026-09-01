package com.example.stop_fgastos.notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.stop_fgastos.MainActivity
import com.example.stop_fgastos.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class StopGastosMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        NotificationRegistrar.saveToken(
            context = applicationContext,
            uid = uid,
            token = token
        )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        NotificationRegistrar.ensureChannel(applicationContext)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Stop Gastos"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Você tem uma nova atualização."

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("notificationType", message.data["type"].orEmpty())
            putExtra("familyId", message.data["familyId"].orEmpty())
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationRegistrar.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(this)
                .notify(message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
        }
    }
}
