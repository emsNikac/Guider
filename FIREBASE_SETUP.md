# Google login and theme settings

This change adds Firebase Authentication only. There is no Firestore/Realtime Database
dependency, cloud progress upload, synchronization, or per-account progress partitioning.
The existing Room database is unchanged. Signing in or out does not erase local progress;
all accounts and guest mode still see the same data on a device. The UI states this explicitly.

## Configure Google sign-in

The application ID is now `com.nikac.guider`. If you already registered the previous
package in Firebase, add a new Android app with this package in the same Firebase project
and use that app's configuration below. Recheck the signing fingerprints and Google OAuth
configuration. Changing the Android package also creates a separate installed app; local
progress and login preferences from the previous package are not migrated automatically.

1. Create/select your Firebase project and register an Android app with package
   `com.nikac.guider` (this is the current application ID).
2. In Authentication → Sign-in method, enable **Google** and choose a support email.
3. Add your build's SHA-1 and SHA-256 certificate fingerprints in Project settings →
   Your apps. `./gradlew :app:signingReport` prints fingerprints for configured signing
   variants. For distribution, also register the actual release/Play App Signing
   certificate, not just your debug or upload certificate.
4. Download the updated `google-services.json` from Firebase after enabling Google and
   copy it to `app/google-services.json`. The matching client in the file must have
   `client_info.android_client_info.package_name` equal to `com.nikac.guider`.
5. Rebuild and run on a device with Google Play services. Choose **Continue with Google**.
   Verify the account appears under Firebase Authentication → Users.

The Google Services Gradle plugin reads the API key, Firebase app ID, project ID, and Web
OAuth client ID from that one file. There are no configuration values to copy manually.
If `default_web_client_id` is missing after Google sign-in was enabled, download a fresh
JSON file from Firebase. You can confirm the Web application OAuth client in the Google
Cloud console's Credentials page. If your OAuth consent screen is in testing, add the
Google accounts you want to use as test users.

A database URL is not needed for authentication. Do not add a service-account JSON file,
private key, OAuth client secret, or Admin SDK credentials to this Android app. Firebase's
client configuration is embedded in the APK and is not a server-side secret.

Firebase BoM 34.4.0 (Auth 24.0.1) is pinned for this project's Kotlin 2.1 toolchain.
Auth 24.2.0 from BoM 34.18.0 requires Kotlin 2.3 metadata and does not compile with
the current compiler. Upgrade Firebase alongside a separately tested Kotlin/KSP toolchain
update; do not bypass Kotlin's metadata compatibility checks.

Without `app/google-services.json`, the project still builds and Google login stays disabled
with an explanation; guest mode, local progress, and theme settings remain usable. When the
file is present, the plugin validates that it contains a client for this package. A successful
build still cannot verify that the Google provider and signing certificate are configured;
that needs a real Google login after setup.

## Behavior and verification

- First launch presents the welcome screen. Continuing as guest is remembered on this device.
- Firebase restores an existing signed-in user across restarts. Firebase manages its tokens;
  the app does not log or copy them into its own preferences.
- Settings is available from the top-right of Daily tasks. Guests can sign in there later.
- Sign out returns to the welcome screen and clears Firebase and Credential Manager session
  state without changing local progress. Dismissing the Google picker does not sign in or
  disturb an existing guest session. Duplicate sign-in taps are ignored while busy.
- System / Light / Dark is saved locally and applies immediately, including system-bar icons.
  Follow-device mode continues to react to system appearance changes.
- No Firebase anonymous accounts are created for guests. Signing in creates only the normal
  Firebase Authentication user record; it does not create a progress document.

After adding configuration, check: new Google account, returning account, cancel picker,
offline failure/retry, guest-to-Google upgrade, sign-out/relaunch, switching accounts,
theme persistence/relaunch, system theme changes, and landscape/large-font layout on Android 12.
Check the new screens on a physical device before shipping. Existing navigation benchmarks
can dismiss the new welcome screen using guest mode without needing a Google account.

Host verification: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
After verification, `./gradlew clean` removes generated build outputs.

## Official references

- [Firebase Google authentication on Android](https://firebase.google.com/docs/auth/android/google-signin)
- [Android Credential Manager: Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)
- [Google Services Gradle plugin and JSON file](https://firebase.google.com/docs/android/google-services-plugin-and-file)
- [Google sign-in branding](https://developers.google.com/identity/branding-guidelines)
