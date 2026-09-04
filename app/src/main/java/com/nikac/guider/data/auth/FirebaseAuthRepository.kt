package com.nikac.guider.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.nikac.guider.domain.auth.AuthFailure
import com.nikac.guider.domain.auth.AuthIssue
import com.nikac.guider.domain.auth.AuthRepository
import com.nikac.guider.domain.auth.AuthUser
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseAuthRepository(context: Context) : AuthRepository {
    private val appContext = context.applicationContext
    val config = FirebaseConfig.fromResources(appContext)
    override val isConfigured = config.isConfigured
    override val webClientId = config.webClientId
    private var auth: FirebaseAuth? = null
    private val mutableUser = MutableStateFlow<AuthUser?>(null)
    override val user = mutableUser.asStateFlow()
    private val listener = FirebaseAuth.AuthStateListener { updateUser(it) }

    override suspend fun initialize() {
        if (!isConfigured || auth != null) return
        val initializedAuth = withContext(Dispatchers.IO) {
            val app = FirebaseApp.getApps(appContext).firstOrNull {
                it.name == FirebaseApp.DEFAULT_APP_NAME
            } ?: FirebaseApp.initializeApp(appContext)
                ?: throw AuthFailure(AuthIssue.CONFIGURATION)
            FirebaseAuth.getInstance(app)
        }
        auth = initializedAuth
        initializedAuth.addAuthStateListener(listener)
        updateUser(initializedAuth)
    }

    override suspend fun signInWithGoogle(idToken: String) {
        if (!isConfigured) throw AuthFailure(AuthIssue.CONFIGURATION)
        initialize()
        try {
            checkNotNull(auth).signInWithCredential(
                GoogleAuthProvider.getCredential(idToken, null),
            ).await()
            updateUser(checkNotNull(auth))
        } catch (_: FirebaseNetworkException) {
            throw AuthFailure(AuthIssue.NETWORK)
        } catch (_: FirebaseAuthException) {
            throw AuthFailure(AuthIssue.SIGN_IN_FAILED)
        }
    }

    override suspend fun signOut() {
        // Local progress is intentionally untouched. Only Firebase owns persisted auth tokens.
        auth?.signOut()
        mutableUser.value = null
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            // Firebase is already signed out, even if the provider could not clear its picker state.
            throw AuthFailure(AuthIssue.SIGN_OUT_CLEANUP)
        }
    }

    private fun updateUser(firebaseAuth: FirebaseAuth) {
        mutableUser.value = firebaseAuth.currentUser?.let {
            AuthUser(uid = it.uid, displayName = it.displayName, email = it.email)
        }
    }

    override fun close() {
        auth?.removeAuthStateListener(listener)
    }
}
