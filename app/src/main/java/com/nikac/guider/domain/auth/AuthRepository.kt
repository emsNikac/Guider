package com.nikac.guider.domain.auth

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(val uid: String, val displayName: String?, val email: String?)

interface AuthRepository {
    val isConfigured: Boolean
    val webClientId: String
    val user: StateFlow<AuthUser?>
    suspend fun initialize()
    suspend fun signInWithGoogle(idToken: String)
    suspend fun signOut()
    fun close()
}

enum class AuthIssue {
    CONFIGURATION, NETWORK, NO_ACCOUNT, PROVIDER_UNAVAILABLE, SIGN_IN_FAILED, SIGN_OUT_CLEANUP,
}

class AuthFailure(val issue: AuthIssue) : Exception(issue.name)
