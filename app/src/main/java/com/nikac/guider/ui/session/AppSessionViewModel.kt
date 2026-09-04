package com.nikac.guider.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikac.guider.domain.auth.AuthFailure
import com.nikac.guider.domain.auth.AuthIssue
import com.nikac.guider.domain.auth.AuthRepository
import com.nikac.guider.domain.auth.AuthUser
import com.nikac.guider.domain.settings.AppPreferencesStore
import com.nikac.guider.domain.settings.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SessionMessage {
    CONFIGURATION, NETWORK, NO_ACCOUNT, PROVIDER_UNAVAILABLE, SIGN_IN_FAILED,
    SIGN_OUT_CLEANUP, PREFERENCES_FAILED,
}

data class AppSessionState(
    val isLoading: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isGuest: Boolean = false,
    val user: AuthUser? = null,
    val canSignIn: Boolean = false,
    val isBusy: Boolean = false,
    val isSavingTheme: Boolean = false,
    val message: SessionMessage? = null,
) {
    val hasAccess: Boolean get() = !isLoading && (isGuest || user != null)
}

class AppSessionViewModel(
    private val preferences: AppPreferencesStore,
    private val auth: AuthRepository,
) : ViewModel() {
    val googleWebClientId: String get() = auth.webClientId
    private val mutableState = MutableStateFlow(AppSessionState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val saved = preferences.read()
                mutableState.update { it.copy(themeMode = saved.themeMode, isGuest = saved.continueAsGuest) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(message = SessionMessage.PREFERENCES_FAILED) }
            }
            try {
                auth.initialize()
                mutableState.update {
                    it.copy(
                        user = auth.user.value,
                        isGuest = it.isGuest && auth.user.value == null,
                        canSignIn = auth.isConfigured,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(message = SessionMessage.CONFIGURATION) }
            }
            mutableState.update { it.copy(isLoading = false) }
            auth.user.collect { user ->
                mutableState.update { it.copy(user = user, isGuest = it.isGuest && user == null) }
            }
        }
    }

    fun continueAsGuest() {
        if (state.value.isLoading || state.value.isBusy || state.value.user != null) return
        mutableState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            try {
                preferences.setContinueAsGuest(true)
                mutableState.update { it.copy(isGuest = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(message = SessionMessage.PREFERENCES_FAILED) }
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun signIn(requestGoogleIdToken: suspend () -> String) {
        if (state.value.isLoading || state.value.isBusy || state.value.user != null) return
        if (!state.value.canSignIn) {
            mutableState.update { it.copy(message = SessionMessage.CONFIGURATION) }
            return
        }
        mutableState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            try {
                auth.signInWithGoogle(requestGoogleIdToken())
                mutableState.update { it.copy(user = auth.user.value, isGuest = false) }
                try {
                    preferences.setContinueAsGuest(false)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    mutableState.update { it.copy(message = SessionMessage.PREFERENCES_FAILED) }
                }
            } catch (error: CancellationException) {
                // Dismissing Google's account picker is not a failed login.
                throw error
            } catch (error: AuthFailure) {
                mutableState.update { it.copy(message = error.issue.toMessage()) }
            } catch (_: Exception) {
                mutableState.update { it.copy(message = SessionMessage.SIGN_IN_FAILED) }
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun signOut() {
        if (state.value.isBusy || state.value.user == null) return
        mutableState.update { it.copy(isBusy = true, message = null, isGuest = false) }
        viewModelScope.launch {
            try {
                try {
                    preferences.setContinueAsGuest(false)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    mutableState.update { it.copy(message = SessionMessage.PREFERENCES_FAILED) }
                }
                auth.signOut()
            } catch (error: CancellationException) {
                throw error
            } catch (error: AuthFailure) {
                mutableState.update { it.copy(message = error.issue.toMessage()) }
            } catch (_: Exception) {
                mutableState.update { it.copy(message = SessionMessage.SIGN_OUT_CLEANUP) }
            } finally {
                mutableState.update { it.copy(user = auth.user.value, isBusy = false) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        val previous = state.value.themeMode
        if (state.value.isLoading || state.value.isSavingTheme || previous == mode) return
        mutableState.update { it.copy(themeMode = mode, isSavingTheme = true, message = null) }
        viewModelScope.launch {
            try {
                preferences.setThemeMode(mode)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(themeMode = previous, message = SessionMessage.PREFERENCES_FAILED) }
            } finally {
                mutableState.update { it.copy(isSavingTheme = false) }
            }
        }
    }

    override fun onCleared() {
        auth.close()
    }
}

private fun AuthIssue.toMessage(): SessionMessage = when (this) {
    AuthIssue.CONFIGURATION -> SessionMessage.CONFIGURATION
    AuthIssue.NETWORK -> SessionMessage.NETWORK
    AuthIssue.NO_ACCOUNT -> SessionMessage.NO_ACCOUNT
    AuthIssue.PROVIDER_UNAVAILABLE -> SessionMessage.PROVIDER_UNAVAILABLE
    AuthIssue.SIGN_IN_FAILED -> SessionMessage.SIGN_IN_FAILED
    AuthIssue.SIGN_OUT_CLEANUP -> SessionMessage.SIGN_OUT_CLEANUP
}
