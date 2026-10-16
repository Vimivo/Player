# Vimivo — Developer Getting Started Guide

This document is the authoritative onboarding guide for developing, testing, and debugging the Vimivo media player across all target platforms.

---

## 1. System Requirements & Toolchains

### General (All Platforms)
- **JDK 17**: Eclipse Temurin or Amazon Corretto 17. Ensure `JAVA_HOME` points to JDK 17.
- **Git**: 2.30+ with CRLF handling set appropriately.
- **Gradle**: Managed via project wrapper (`./gradlew`). Do not use arbitrary global Gradle installations.

### Android
- **Android SDK Platform 36**: Compile SDK 36, Target SDK 36, Minimum SDK 26 (Android 8.0 Oreo).
- **Build Tools**: `36.0.0` or higher.
- **Android Studio**: Ladybug / Meerkat or compatible with Android Gradle Plugin 8.13.
- Set `ANDROID_HOME` in your shell environment or create `local.properties` in project root:
  ```properties
  sdk.dir=/Users/username/Library/Android/sdk
  ```

### macOS Desktop & iOS
- **macOS**: macOS 13+ (Ventura, Sonoma, Sequoia) running on Apple Silicon or Intel x86_64.
- **Xcode**: Xcode 15+ with Command Line Tools (`xcode-select --install`).
- **libmpv**: Required for desktop runtime and video rendering bridge. Can be provided via Homebrew (`brew install mpv`) or via custom path: `-Dvimivo.mpv.library=/path/to/libmpv.dylib`.

### Windows Desktop
- **Windows**: Windows 10+ (64-bit).
- **libmpv**: `mpv-2.dll` and dependencies in system PATH or bundled alongside application binaries.

### Linux Desktop
- **Linux**: Ubuntu 20.04+, Fedora 38+, Arch Linux, or Debian 11+ with GLIBC 2.31+.
- **System Libraries**: `libmpv.so.1` or `libmpv.so.2`, `libsecret-1.so.0`, X11 / XCB libraries.

---

## 2. One-Command Developer Validation

Before pushing commits or opening pull requests, always execute the canonical developer quality gate:

```shell
./scripts/verify_all.sh
```

Or via Gradle directly:

```shell
./gradlew verifyAll
```

This runs:
1. `./gradlew detekt` (0 violations required)
2. `./gradlew :shared:desktopTest` (domain contracts & models)
3. `./gradlew :desktopApp:compileKotlinDesktop` (desktop compile check)
4. `./gradlew :desktopApp:desktopTest` (all 143+ desktop, database, queue, metadata, and regression tests)
5. `./gradlew :androidApp:testDebugUnitTest` (Android unit tests)
6. `./gradlew :androidApp:assembleDebug` (Android debug APK build)

---

## 3. Platform Build Cheatsheet

### Shared Module (`shared`)
```shell
# Run portable shared unit tests
./gradlew :shared:desktopTest

# Check Kotlin Multiplatform compilation
./gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid
```

### Desktop Application (`desktopApp`)
```shell
# Run desktop tests
./gradlew :desktopApp:desktopTest

# Launch desktop app locally
./gradlew :desktopApp:run

# Package installable macOS DMG (requires macOS host)
./gradlew :desktopApp:packageDmg

# Package Windows MSI / EXE (requires Windows host)
./gradlew :desktopApp:packageMsi :desktopApp:packageExe

# Package Linux DEB / RPM (requires Linux host)
./gradlew :desktopApp:packageDeb :desktopApp:packageRpm
```

### Android Application (`androidApp`)
```shell
# Run unit tests
./gradlew :androidApp:testDebugUnitTest

# Assemble debug APK
./gradlew :androidApp:assembleDebug
# Output: androidApp/build/outputs/apk/debug/androidApp-debug.apk

# Assemble release bundle (requires release keystore)
./gradlew :androidApp:bundleRelease
```

### iOS Application (`iosApp`)
```shell
# Compile shared framework for iOS Simulator
./gradlew :shared:compileKotlinIosSimulatorArm64

# Open Xcode workspace for native SwiftUI app
open iosApp/VimivoApp.xcodeproj
```

---

## 4. Code & Architecture Rules

- **Platform Neutrality in Common Code**: Keep `shared/src/commonMain/` 100% free of platform-specific imports (`java.io.*`, `java.sql.*`, `android.*`, `UIKit.*`, `AVFoundation.*`).
- **Immutable UI State**: Expose immutable UI state via `StateFlow<T>`; keep `MutableStateFlow` strictly private inside state holders or controllers.
- **Authoritative Playback Queue**: The application has exactly one authoritative playback queue managed by `UnifiedPlaybackQueue`. Never create duplicate or parallel queue implementations.
- **Two Metadata Modes**:
  - *Mode A (Overrides)*: Store metadata corrections in SQLite (`user_metadata_overrides`), leaving original file bytes intact.
  - *Mode B (Embedded Writes)*: Modify file tags with `DesktopMetadataEditorService` only for verified audio containers (MP3, FLAC, OGG, M4A, WAV), with automatic backup and post-write verification.
- **Non-Destructive Defaults**: Never silently delete duplicate files or relocate user media without explicit in-app user confirmation.
- **Accessibility**: All interactive elements (sliders, playback buttons, navigation items) must supply accessible semantics (`contentDescription`, `stateDescription`, VoiceOver accessibility labels).

---

## 5. Troubleshooting & Diagnostics

### Missing `libmpv` on macOS
If running `./gradlew :desktopApp:run` fails with `UnsatisfiedLinkError: Unable to load library 'mpv'`:
```shell
brew install mpv
# Or specify explicit dylib path:
./gradlew :desktopApp:run -Dvimivo.mpv.library=$(brew --prefix mpv)/lib/libmpv.dylib
```

### Android SDK Missing or Wrong Version
Ensure `ANDROID_HOME` or `local.properties` is configured correctly:
```shell
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### SQLite Schema Migration Errors in Tests
If developing new database tables or columns:
1. Always update `DesktopDatabase.SCHEMA_VERSION`.
2. Add non-destructive migration statements in `DesktopDatabase.migrate()`.
3. Update `DesktopDatabaseMigrationTest` with forward-upgrade tests preserving user data and foreign keys.
4. Never delete or alter previous version migration blocks.

### Diagnostic Log Export
Vimivo includes privacy-safe logging via `SafeLogger` and `PrivacySafeDiagnostics`. Sensitive file paths (e.g., `/Users/username/...`) and credentials are automatically redacted with `[REDACTED]`.
Export diagnostic reports directly from Desktop Settings or via `./gradlew verifyAll`.
