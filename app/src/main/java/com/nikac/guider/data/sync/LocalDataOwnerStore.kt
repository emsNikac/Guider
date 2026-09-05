package com.nikac.guider.data.sync

import androidx.room.withTransaction
import com.nikac.guider.data.database.AppMetadataEntity
import com.nikac.guider.data.database.GUEST_OWNER_ID
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.domain.sync.DataOwner

internal class LocalDataOwnerStore(private val database: GuiderDatabase) {
    suspend fun resolve(preferredFirebaseUid: String? = null): String {
        val metadata = database.appMetadataDao()
        metadata.getValue(ACTIVE_LOCAL_OWNER)?.takeIf(String::isNotBlank)?.let { return it }

        val availableOwners = database.ownershipDao().activeContentOwnerIds().toSet()
        val preferredLegacyOwner = preferredFirebaseUid?.let {
            DataOwner.legacyAccountLocalId(it)
        }
        val previouslyMigratedOwner = metadata.getValue(PRE_PARTITION_DATA_PENDING)
            ?.removePrefix(MIGRATED_PREFIX)
            ?.takeIf { it.isNotBlank() }
            ?.let { DataOwner.legacyAccountLocalId(it) }
        val selectedOwner = when {
            preferredLegacyOwner != null && preferredLegacyOwner in availableOwners ->
                preferredLegacyOwner
            previouslyMigratedOwner != null && previouslyMigratedOwner in availableOwners ->
                previouslyMigratedOwner
            GUEST_OWNER_ID in availableOwners -> GUEST_OWNER_ID
            availableOwners.size == 1 -> availableOwners.first()
            else -> GUEST_OWNER_ID
        }
        metadata.set(AppMetadataEntity(ACTIVE_LOCAL_OWNER, selectedOwner))
        return selectedOwner
    }

    suspend fun bindToAccount(localOwnerId: String, firebaseUid: String) {
        val metadata = database.appMetadataDao()
        if (metadata.getValue(BOUND_FIREBASE_UID) == firebaseUid) return
        database.withTransaction {
            val ownership = database.ownershipDao()
            ownership.markGoalsPending(localOwnerId)
            ownership.markTasksPending(localOwnerId)
            ownership.markHabitsPending(localOwnerId)
            ownership.markHabitCompletionsPending(localOwnerId)
            ownership.markSleepRecordsPending(localOwnerId)
            ownership.markActiveSleepPending(localOwnerId)
            ownership.markMoneyPeriodsPending(localOwnerId)
            ownership.markSpendingsPending(localOwnerId)
            ownership.markMoneyStatePending(localOwnerId)
            metadata.set(AppMetadataEntity(BOUND_FIREBASE_UID, firebaseUid))
        }
    }

    private companion object {
        const val ACTIVE_LOCAL_OWNER = "active_local_owner"
        const val BOUND_FIREBASE_UID = "bound_firebase_uid"
        const val PRE_PARTITION_DATA_PENDING = "pre_partition_data_pending"
        const val MIGRATED_PREFIX = "migrated:"
    }
}
