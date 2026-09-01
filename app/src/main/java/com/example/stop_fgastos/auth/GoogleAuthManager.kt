package com.example.stop_fgastos.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.stop_fgastos.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object GoogleAuthManager {

    fun isConfigured(activity: Activity): Boolean {
        val clientId = activity.getString(R.string.default_web_client_id)
        return clientId.isNotBlank() && !clientId.startsWith("REPLACE_WITH_")
    }

    suspend fun signIn(activity: Activity): FirebaseUser {
        val firebaseCredential = googleFirebaseCredential(activity)
        return FirebaseAuth.getInstance()
            .signInWithCredential(firebaseCredential)
            .await()
            .user
            ?: error("O Firebase não retornou o usuário autenticado.")
    }

    suspend fun reauthenticate(activity: Activity): FirebaseUser {
        val user = FirebaseAuth.getInstance().currentUser
            ?: error("Entre com Google para continuar.")

        val firebaseCredential = googleFirebaseCredential(activity)
        user.reauthenticate(firebaseCredential).await()
        return user
    }

    private suspend fun googleFirebaseCredential(activity: Activity): AuthCredential {
        val clientId = activity.getString(R.string.default_web_client_id)

        check(isConfigured(activity)) {
            "Registre o aplicativo Android no Firebase, configure o SHA e use o Web/Server Client ID oficial."
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credential = CredentialManager.create(activity)
            .getCredential(context = activity, request = request)
            .credential

        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            "A credencial retornada pelo Google não é compatível."
        }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        return GoogleAuthProvider.getCredential(googleCredential.idToken, null)
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }
}