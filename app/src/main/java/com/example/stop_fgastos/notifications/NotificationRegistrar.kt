package com.example.stop_fgastos.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

object NotificationRegistrar {

    const val CHANNEL_ID = "stop_gastos_general"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Stop Gastos",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alertas financeiros, convites familiares e lembretes do Stop Gastos."
        }
        manager.createNotificationChannel(channel)
    }

    fun registerCurrentDevice(
        context: Context,
        onResult: (Result<Unit>) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult(Result.failure(IllegalStateException("Entre com Google para ativar notificações.")))
            return
        }

        ensureChannel(context)

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isBlank()) {
                    onResult(Result.failure(IllegalStateException("O Firebase não retornou um token FCM.")))
                    return@addOnSuccessListener
                }

                saveToken(context, user.uid, token, onResult)
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun saveToken(
        context: Context,
        uid: String,
        token: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        val deviceId = deviceId(context)
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("devices")
            .document(deviceId)
            .set(
                mapOf(
                    "token" to token,
                    "enabled" to true,
                    "platform" to "android",
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "sdkInt" to Build.VERSION.SDK_INT,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun disableCurrentDevice(
        context: Context,
        onResult: (Result<Unit>) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult(Result.success(Unit))
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("devices")
            .document(deviceId(context))
            .set(
                mapOf(
                    "enabled" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                FirebaseMessaging.getInstance().deleteToken()
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun deviceId(context: Context): String {
        val secure = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()

        return if (secure.isBlank()) {
            "android-" + Build.MODEL.replace(" ", "_") + "-" + Build.VERSION.SDK_INT
        } else {
            "android-$secure"
        }
    }
}
