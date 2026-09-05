package com.nikac.guider.ui.session

import androidx.lifecycle.ViewModelStore
import com.nikac.guider.domain.auth.AuthFailure
import com.nikac.guider.domain.auth.AuthIssue
import com.nikac.guider.domain.auth.AuthRepository
import com.nikac.guider.domain.auth.AuthUser
import com.nikac.guider.domain.settings.AppPreferences
import com.nikac.guider.domain.settings.AppPreferencesStore
import com.nikac.guider.domain.settings.ThemeMode
import com.nikac.guider.domain.sync.CloudSyncStatus
import com.nikac.guider.domain.sync.DataOwner
import com.nikac.guider.domain.sync.UserDataSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppSessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val preferences = FakePreferences()
    private val auth = FakeAuth()
    private val sync = FakeUserDataSync()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private fun model(): AppSessionViewModel = AppSessionViewModel(preferences, auth, sync).also {
        store.put("session", it)
    }

    @Test fun `first launch requires a choice without needing Firebase configuration`() = runTest {
        auth.isConfigured = false
        val model = model()
        advanceUntilIdle()
        assertFalse(model.state.value.isLoading)
        assertFalse(model.state.value.hasAccess)
        assertFalse(model.state.value.canSignIn)
        model.continueAsGuest()
        advanceUntilIdle()
        assertTrue(model.state.value.hasAccess)
        assertTrue(preferences.saved.continueAsGuest)
        assertEquals(0, auth.signInCalls)
    }

    @Test fun `saved guest and theme restore without prompting again`() = runTest {
        preferences.saved = AppPreferences(ThemeMode.DARK, continueAsGuest = true)
        val model = model()
        advanceUntilIdle()
        assertTrue(model.state.value.hasAccess)
        assertEquals(ThemeMode.DARK, model.state.value.themeMode)
    }

    @Test fun `Firebase session restores and auth changes are observed`() = runTest {
        auth.user.value = testUser
        val model = model()
        advanceUntilIdle()
        assertEquals(testUser, model.state.value.user)
        assertTrue(model.state.value.hasAccess)
        auth.user.value = null
        runCurrent()
        assertFalse(model.state.value.hasAccess)
    }

    @Test fun `guest can upgrade to Google and guest flag is cleared`() = runTest {
        preferences.saved = AppPreferences(continueAsGuest = true)
        val model = model()
        advanceUntilIdle()
        model.signIn { "test-id-token" }
        advanceUntilIdle()
        assertEquals(testUser, model.state.value.user)
        assertFalse(model.state.value.isGuest)
        assertFalse(preferences.saved.continueAsGuest)
        assertTrue(model.state.value.hasAccess)
        assertFalse(model.state.value.isBusy)
    }

    @Test fun `cancelled Google picker leaves guest session unchanged`() = runTest {
        preferences.saved = AppPreferences(continueAsGuest = true)
        val model = model()
        advanceUntilIdle()
        model.signIn { throw CancellationException("Picker dismissed") }
        advanceUntilIdle()
        assertTrue(model.state.value.isGuest)
        assertTrue(preferences.saved.continueAsGuest)
        assertNull(model.state.value.message)
        assertFalse(model.state.value.isBusy)
        assertEquals(0, auth.signInCalls)
    }

    @Test fun `network failure does not grant access and can be retried`() = runTest {
        auth.signInFailure = AuthFailure(AuthIssue.NETWORK)
        val model = model()
        advanceUntilIdle()
        model.signIn { "test-id-token" }
        advanceUntilIdle()
        assertFalse(model.state.value.hasAccess)
        assertEquals(SessionMessage.NETWORK, model.state.value.message)
        assertFalse(model.state.value.isBusy)
        auth.signInFailure = null
        model.signIn { "test-id-token" }
        advanceUntilIdle()
        assertTrue(model.state.value.hasAccess)
        assertNull(model.state.value.message)
    }

    @Test fun `double tapping sign in and guest during sign in cannot race`() = runTest {
        val token = CompletableDeferred<String>()
        val model = model()
        advanceUntilIdle()
        model.signIn { token.await() }
        model.signIn { error("Second picker must not open") }
        model.continueAsGuest()
        runCurrent()
        assertTrue(model.state.value.isBusy)
        assertFalse(preferences.saved.continueAsGuest)
        token.complete("test-id-token")
        advanceUntilIdle()
        assertEquals(1, auth.signInCalls)
        assertEquals(testUser, model.state.value.user)
    }

    @Test fun `missing configuration never invokes the account picker`() = runTest {
        auth.isConfigured = false
        val model = model()
        advanceUntilIdle()
        model.signIn { error("Picker must not open") }
        advanceUntilIdle()
        assertEquals(SessionMessage.CONFIGURATION, model.state.value.message)
        assertEquals(0, auth.signInCalls)
    }

    @Test fun `initialization failure does not block guest mode`() = runTest {
        auth.initializationFailure = IOException()
        val model = model()
        advanceUntilIdle()
        assertFalse(model.state.value.isLoading)
        assertFalse(model.state.value.canSignIn)
        model.continueAsGuest()
        advanceUntilIdle()
        assertTrue(model.state.value.hasAccess)
    }

    @Test fun `sign out returns to welcome even when provider cleanup fails`() = runTest {
        auth.user.value = testUser
        preferences.saved = AppPreferences(continueAsGuest = true)
        auth.signOutFailure = AuthFailure(AuthIssue.SIGN_OUT_CLEANUP)
        val model = model()
        advanceUntilIdle()
        model.signOut()
        advanceUntilIdle()
        assertNull(model.state.value.user)
        assertFalse(model.state.value.hasAccess)
        assertFalse(preferences.saved.continueAsGuest)
        assertEquals(SessionMessage.SIGN_OUT_CLEANUP, model.state.value.message)
    }

    @Test fun `preference failure must not prevent Firebase sign out`() = runTest {
        auth.user.value = testUser
        val model = model()
        advanceUntilIdle()
        preferences.failWrites = true
        model.signOut()
        advanceUntilIdle()
        assertFalse(model.state.value.hasAccess)
        assertNull(auth.user.value)
        assertEquals(SessionMessage.PREFERENCES_FAILED, model.state.value.message)
    }

    @Test fun `theme changes immediately and saves without changing the guest session`() = runTest {
        preferences.saved = AppPreferences(continueAsGuest = true)
        val model = model()
        advanceUntilIdle()
        model.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, model.state.value.themeMode)
        advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT, preferences.saved.themeMode)
        assertTrue(preferences.saved.continueAsGuest)
        assertTrue(model.state.value.hasAccess)
    }

    @Test fun `failed theme save restores the old theme and reports error`() = runTest {
        val model = model()
        advanceUntilIdle()
        preferences.failWrites = true
        model.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(ThemeMode.SYSTEM, model.state.value.themeMode)
        assertFalse(model.state.value.isSavingTheme)
        assertEquals(SessionMessage.PREFERENCES_FAILED, model.state.value.message)
    }

    @Test fun `failed guest save keeps welcome screen available`() = runTest {
        val model = model()
        advanceUntilIdle()
        preferences.failWrites = true
        model.continueAsGuest()
        advanceUntilIdle()
        assertFalse(model.state.value.hasAccess)
        assertFalse(model.state.value.isBusy)
        assertEquals(SessionMessage.PREFERENCES_FAILED, model.state.value.message)
    }

    @Test fun `clearing the viewmodel removes the auth listener`() = runTest {
        model()
        advanceUntilIdle()
        store.clear()
        assertTrue(auth.closed)
    }

    private class FakePreferences : AppPreferencesStore {
        var saved = AppPreferences()
        var failWrites = false
        override suspend fun read() = saved
        override suspend fun setThemeMode(mode: ThemeMode) {
            if (failWrites) throw IOException()
            saved = saved.copy(themeMode = mode)
        }
        override suspend fun setContinueAsGuest(guest: Boolean) {
            if (failWrites) throw IOException()
            saved = saved.copy(continueAsGuest = guest)
        }
    }

    private class FakeAuth : AuthRepository {
        override var isConfigured = true
        override val webClientId = "test-client.apps.googleusercontent.com"
        override val user = MutableStateFlow<AuthUser?>(null)
        var initializationFailure: Exception? = null
        var signInFailure: Exception? = null
        var signOutFailure: Exception? = null
        var signInCalls = 0
        var closed = false
        override suspend fun initialize() { initializationFailure?.let { throw it } }
        override suspend fun signInWithGoogle(idToken: String) {
            signInCalls++
            assertEquals("test-id-token", idToken)
            signInFailure?.let { throw it }
            user.value = testUser
        }
        override suspend fun signOut() {
            user.value = null
            signOutFailure?.let { throw it }
        }
        override fun close() { closed = true }
    }

    private class FakeUserDataSync : UserDataSync {
        override val owner = MutableStateFlow(DataOwner.Guest)
        override val status = MutableStateFlow(CloudSyncStatus.LOCAL_ONLY)
        override val restoredThemes = MutableSharedFlow<ThemeMode>()
        override suspend fun activateGuest() {
            owner.value = DataOwner.Guest
            status.value = CloudSyncStatus.LOCAL_ONLY
        }
        override suspend fun activateAccount(firebaseUid: String, migrateGuestData: Boolean) {
            owner.value = DataOwner.account(firebaseUid)
            status.value = CloudSyncStatus.SYNCED
        }
        override fun requestUpload() = Unit
        override suspend fun syncNow() = Unit
        override fun saveTheme(mode: ThemeMode) = Unit
        override fun close() = Unit
    }

    private companion object {
        val testUser = AuthUser("test-user-id", "Test user", "test@example.com")
    }
}
