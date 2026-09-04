package com.nikac.guider.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.nikac.guider.domain.auth.AuthFailure
import com.nikac.guider.domain.auth.AuthIssue
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

object GoogleCredentialProvider {
    suspend fun getIdToken(activity: Activity, webClientId: String): String {
        try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
            val credential = CredentialManager.create(activity)
                .getCredential(context = activity, request = request).credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                throw AuthFailure(AuthIssue.SIGN_IN_FAILED)
            }
            // Firebase validates this ID token; never log it or persist it in app preferences.
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (_: GetCredentialCancellationException) {
            throw CancellationException("Google sign-in dismissed")
        } catch (_: NoCredentialException) {
            throw AuthFailure(AuthIssue.NO_ACCOUNT)
        } catch (_: GetCredentialProviderConfigurationException) {
            throw AuthFailure(AuthIssue.PROVIDER_UNAVAILABLE)
        } catch (_: GetCredentialException) {
            throw AuthFailure(AuthIssue.SIGN_IN_FAILED)
        } catch (_: GoogleIdTokenParsingException) {
            throw AuthFailure(AuthIssue.SIGN_IN_FAILED)
        }
    }
}
