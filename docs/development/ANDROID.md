# Vimivo — Android Development Guide

This guide details the Android architecture, toolchain requirements, permissions, and build procedures for Vimivo.

---

## 1. Module Overview & Technology Stack

- **Module Path**: `androidApp`
- **Compile SDK**: 36
- **Target SDK**: 36
- **Minimum SDK**: 26 (Android 8.0 Oreo)
- **AGP Version**: 8.13
- **Kotlin**: 2.2.0 with Kotlin Multiplatform integration
- **UI Framework**: Jetpack Compose with Material 3 tokens
- **Playback Engine**: AndroidX Media3 (`MediaSessionService`, `ExoPlayer`, `MediaController`) with libmpv controlled fallback
- **Persistence**: Room Database with SQLite, Android DataStore Preferences
- **System Media Controls**: Lock Screen controls, Notification shade controls, Bluetooth AVRCP metadata synchronization, Android 13+ `POST_NOTIFICATIONS` runtime permission handling.

---

## 2. Prerequisites & Setup

1. **Android Studio**: Android Studio Ladybug or newer.
2. **Android SDK**: Install Platform 36 and Build-Tools `36.0.0`.
3. Set environment variable:
   ```shell
   export ANDROID_HOME="/path/to/android-sdk"
   ```
   Or create `local.properties`:
   ```properties
   sdk.dir=/Users/username/Library/Android/sdk
   ```

---

## 3. Storage & Permissions

Vimivo supports scoped storage and media permission grants:
- **API 33+ (Android 13+)**:
  - `READ_MEDIA_AUDIO` for music files.
  - `READ_MEDIA_VIDEO` for video files.
  - `POST_NOTIFICATIONS` for background media playback controls.
- **API 26–32 (Android 8.0–12L)**:
  - `READ_EXTERNAL_STORAGE` for legacy storage indexing.
- **Storage Access Framework (SAF)**:
  - Document pickers via `ACTION_OPEN_DOCUMENT_TREE` and `ACTION_OPEN_DOCUMENT` for authorized access to external SD cards and OTG drives.
  - Persisted URI permission grants via `takePersistableUriPermission()`.

---

## 4. Build & Test Commands

```shell
# Run static analysis
./gradlew :androidApp:detekt

# Run local unit tests
./gradlew :androidApp:testDebugUnitTest

# Assemble debug APK
./gradlew :androidApp:assembleDebug
# Output: androidApp/build/outputs/apk/debug/androidApp-debug.apk

# Assemble release APK (unsigned)
./gradlew :androidApp:assembleRelease

# Assemble release App Bundle (AAB for Google Play)
./gradlew :androidApp:bundleRelease
# Output: androidApp/build/outputs/bundle/release/androidApp-release.aab
```

---

## 5. Background Playback & MediaSession

The media playback service is implemented as a foreground service using AndroidX Media3:
- Service Class: `dev.vimivo.platform.android.playback.VimivoMediaService`
- Exposes `MediaSession` allowing the Android operating system to display lock-screen album art, track information, playback progress, and Bluetooth hardware controls.
- Automatic audio focus ducking/pause on phone calls and wired headset unplug (`ACTION_AUDIO_BECOMING_NOISY`).

---

## 6. Release Signing Configuration

For official production distribution, signing configuration is injected via protected environment variables in CI:
- `VIMIVO_KEYSTORE_BASE64`: Base64 encoded upload keystore.
- `VIMIVO_KEYSTORE_PASSWORD`: Keystore passphrase.
- `VIMIVO_KEY_ALIAS`: Key alias.
- `VIMIVO_KEY_PASSWORD`: Key passphrase.

Never commit production keystore files or passwords into version control.
