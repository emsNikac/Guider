# Firebase login and cloud progress

Guider works offline from its Room database. Guest data stays only in Room and is never
uploaded. Data owned by a Google/Firebase account is synchronized with Cloud Firestore and
can be restored after reinstalling the app or signing in on another device.

## Firebase console setup

1. Register the Android application `com.nikac.guider` in the Firebase project.
2. In Authentication → Sign-in method, enable Google and select a support email.
3. Add SHA-1 and SHA-256 fingerprints for debug, release, and Play App Signing certificates.
   `./gradlew :app:signingReport` prints the locally configured fingerprints.
4. Download the current `google-services.json` and place it at
   `app/google-services.json`. No Firebase values need to be copied into Kotlin code.
5. In Databases & Storage → Firestore, create the default Firestore Standard database.
6. Deploy the checked-in owner-only rules:

   ```shell
   firebase deploy --only firestore:rules
   ```

7. Enable App Check for the Android application before production release.

Never add a service-account key, OAuth client secret, or Admin SDK credential to the Android
application. The client configuration in `google-services.json` is expected to be packaged
with the APK; Firestore Security Rules are the data-access boundary.

## Data behavior

- Guider always writes progress to Room first. Signing in attaches cloud backup to the same
  on-device dataset; signing out detaches cloud backup without moving, hiding, or deleting data.
- While signed out, changes stay pending locally. They are uploaded automatically if the user
  signs back into the account connected to that dataset.
- A user who has never signed in is local-only. Uninstalling Guider or clearing its app data
  deletes that progress permanently.
- Android Auto Backup and device-transfer backup are disabled intentionally, preventing guest
  progress and authentication state from returning after reinstall.
- When a local-only user signs in, tasks, goals, habits, completions, sleep history, the current
  sleep session, money periods, and spendings are backed up to that Firebase account.
- Signed-in changes are written to Room first and then uploaded. Pending changes remain marked
  locally until Firestore acknowledges the write.
- Firestore listeners restore changes from other devices. A network-constrained WorkManager
  job retries synchronization periodically, and Settings also provides **Sync now**.
- Task and one-time-goal cleanup archives records instead of deleting their history. Restarting
  money tracking closes the current period and creates another instead of erasing spendings.
- The active device dataset remains visible after sign-out and continues in local-only mode.
  Firestore rules still isolate every cloud backup by Firebase user ID.
- Theme mode is saved locally and synchronized for signed-in users. Transient UI state,
  notification permissions, Firebase tokens, and WorkManager internals are not synchronized.

## Firestore layout

```text
users/{firebaseUid}/dailyTasks/{remoteId}
users/{firebaseUid}/goals/{remoteId}
users/{firebaseUid}/habits/{remoteId}
users/{firebaseUid}/habitCompletions/{remoteId}
users/{firebaseUid}/sleepRecords/{remoteId}
users/{firebaseUid}/moneyPeriods/{remoteId}
users/{firebaseUid}/spendings/{remoteId}
users/{firebaseUid}/state/activeSleep
users/{firebaseUid}/state/money
users/{firebaseUid}/state/sync
users/{firebaseUid}/preferences/app
```

Cloud documents use stable UUIDs, update timestamps, archive markers, and deletion tombstones.
Room retains its numeric IDs for fast joins; linked cloud records use UUIDs so separately
created data cannot collide across devices.

## Verification

Run:

```shell
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Before release, verify guest-only use, guest-to-account migration, offline edits, two-device
updates, record deletion, money restart history, sign-out/account switching, manual sync,
reinstall-and-restore, and rejected cross-user Firestore access. The Local Emulator Suite is
recommended for automated Firestore rules and multi-client synchronization tests.

Firebase BoM 34.4.0 remains pinned for the project's Kotlin 2.1 toolchain. Upgrade Firebase
alongside a tested Kotlin/KSP update rather than bypassing Kotlin metadata compatibility checks.
