package com.dengerin.app.presentation.profile

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.dengerin.app.core.util.findActivityOrNull
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tombol "Masuk dengan Google" lewat Android Credential Manager.
 *
 * Server client ID tidak pernah di-hardcode: dibaca dari resource
 * `default_web_client_id` yang dihasilkan google-services plugin dari
 * `app/google-services.json`.
 */
class GoogleSignInHelper(context: Context) {
    private val context = context
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): Result<String> = withContext(Dispatchers.Main) {
        var credentialClass = "none"
        var credentialType = "none"

        runCatching {
            val activity = context.findActivityOrNull()
                ?: error("Layar masuk tidak tersedia. Buka profil dari dalam aplikasi ya.")
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(readServerClientId())
                        .setAutoSelectEnabled(false)
                        .build(),
                )
                .build()

            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            credentialClass = credential.javaClass.name
            credentialType = credential.type

            // Credential Manager mengembalikan CustomCredential, bukan instance
            // GoogleIdTokenCredential. Cocokkan lewat `type` lalu parse `data`-nya.
            if (credential !is CustomCredential || credential.type !in SUPPORTED_GOOGLE_TYPES) {
                Log.w(
                    TAG,
                    "Kredensial ditolak: class=$credentialClass, type=$credentialType",
                )
                error("Kredensial Google tidak dikenali. Coba lagi ya.")
            }

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken
            if (idToken.isBlank()) {
                Log.w(TAG, "Token Google kosong: class=$credentialClass, type=$credentialType")
                error("Token Google kosong. Coba lagi ya.")
            }
            idToken
        }.recoverCatching { error ->
            Log.e(
                TAG,
                "Masuk Google gagal (${error.javaClass.name}): class=$credentialClass, type=$credentialType",
                error,
            )
            when (error) {
                is NoCredentialException -> error("Belum ada akun Google di perangkat ini. Tambahkan akun dulu ya.")
                is GetCredentialException -> error(error.message ?: "Masuk Google dibatalkan.")
                else -> throw error
            }
        }
    }

    private fun readServerClientId(): String {
        val resourceId = appContext.resources.getIdentifier(
            "default_web_client_id",
            "string",
            appContext.packageName,
        )
        if (resourceId == 0) {
            error(
                "google-services.json belum punya OAuth client web. " +
                    "Unduh ulang file itu dari Firebase Console setelah Google Sign-In diaktifkan.",
            )
        }
        val value = appContext.getString(resourceId)
        if (value.isBlank() || value.startsWith("YOUR_")) {
            error("Web client ID kosong. Perbarui google-services.json dari Firebase Console.")
        }
        return value
    }

    private companion object {
        const val TAG = "GoogleSignInHelper"
        val SUPPORTED_GOOGLE_TYPES = setOf(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL,
        )
    }
}
