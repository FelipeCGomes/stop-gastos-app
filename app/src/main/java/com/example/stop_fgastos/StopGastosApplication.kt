package com.example.stop_fgastos

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class StopGastosApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyCg9hI4SY9Uo1vrwdLSq-liiuGtgI0nmD8")
                .setApplicationId("1:363408500943:web:aa4d5ec09bb575fc2f2fba")
                .setProjectId("stopgastos")
                .setStorageBucket("stopgastos.firebasestorage.app")
                .setGcmSenderId("363408500943")
                .build()

            FirebaseApp.initializeApp(this, options)
        }
    }
}