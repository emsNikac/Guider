package com.nikac.guider.domain.sync

import com.nikac.guider.data.database.GUEST_OWNER_ID
import com.nikac.guider.domain.settings.ThemeMode
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class DataOwner(
    val localId: String,
    val firebaseUid: String? = null,
) {
    val usesCloud: Boolean get() = firebaseUid != null

    companion object {
        val Guest = DataOwner(localId = GUEST_OWNER_ID)
        fun account(uid: String) = DataOwner(localId = "firebase:$uid", firebaseUid = uid)
    }
}

enum class CloudSyncStatus {
    LOCAL_ONLY,
    SYNCING,
    SYNCED,
    OFFLINE,
    FAILED,
}

interface UserDataSync {
    val owner: StateFlow<DataOwner>
    val status: StateFlow<CloudSyncStatus>
    val restoredThemes: SharedFlow<ThemeMode>

    suspend fun activateGuest()
    suspend fun activateAccount(firebaseUid: String, migrateGuestData: Boolean)
    fun requestUpload()
    suspend fun syncNow()
    fun saveTheme(mode: ThemeMode)
    fun close()
}
