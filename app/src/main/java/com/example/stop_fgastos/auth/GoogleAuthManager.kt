package com.example.stop_fgastos.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.stop_fgastos.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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
        val clientId = activity.getString(R.string.default_web_client_id)

        check(isConfigured(activity)) {
            "Configure o default_web_client_id do projeto Android no Firebase antes de entrar com Google."
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
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)

        return FirebaseAuth.getInstance()
            .signInWithCredential(firebaseCredential)
            .await()
            .user
            ?: error("O Firebase não retornou o usuário autenticado.")
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }
}