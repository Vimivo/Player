# Vimivo — iOS & iPadOS Development Guide

This guide details the native iOS and iPadOS architecture, SwiftUI integration, Kotlin Multiplatform framework dependencies, and build workflows.

---

## 1. Architecture Overview

Vimivo for iOS is architected as a native SwiftUI application consuming a Kotlin Multiplatform shared framework:
- **Shared Framework**: `VimivoShared.framework` compiled for `iosArm64` (physical devices) and `iosSimulatorArm64` (Simulators).
- **UI Framework**: 100% native SwiftUI with dynamic type, VoiceOver accessibility semantics, and dark/light mode adaption.
- **Audio & Video Engine**: Native Apple `AVFoundation` (`AVPlayer`, `AVPlayerLayer`, `AVAudioSession`).
- **Background Audio**: Enabled via `UIBackgroundModes: ["audio"]` in Info.plist.
- **Lock Screen & Control Center**: Native integration with `MPNowPlayingInfoCenter` and `MPRemoteCommandCenter`.
- **Picture-in-Picture (PiP)**: Implemented using `AVKit` / `AVPictureInPictureController`.
- **Filesystem Access**: Apple FileProvider architecture using security-scoped URLs and persistent bookmarks (`SecurityScopedMediaManager`).
- **Secure Credentials**: iOS Keychain services (`KeychainCredentialStore`).
- **Persistence**: Native SQLite with WAL (Write-Ahead Logging) mode.

---

## 2. Prerequisites & Setup

- **macOS Host**: macOS 13+ with Apple Silicon or Intel CPU.
- **Xcode**: Xcode 15 or 16.
- **CocoaPods / SPM**: Not required for core architecture; shared Kotlin framework is linked directly via Xcode build settings.

---

## 3. Building the Shared Framework

To generate or update the Kotlin shared framework for iOS:

```shell
# Build shared framework for iOS Simulator
./gradlew :shared:compileKotlinIosSimulatorArm64

# Build shared framework for physical device (Release)
./gradlew :shared:linkReleaseFrameworkIosArm64

# Build fat / universal XCFramework if needed
./gradlew :shared:assembleXCFramework
```

Framework output: `shared/build/bin/iosSimulatorArm64/debugFramework/VimivoShared.framework`

---

## 4. Running in Xcode

1. Open the Xcode project:
   ```shell
   open iosApp/VimivoApp.xcodeproj
   ```
2. Select target `VimivoApp` and choose an iOS Simulator (e.g., iPhone 17 Pro, iPad Pro 13-inch).
3. Press `Cmd + R` to build and launch.
4. Run automated XCTest unit tests:
   ```shell
   xcodebuild test -project iosApp/VimivoApp.xcodeproj -scheme VimivoApp -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
   ```

---

## 5. Sandboxed Document Access & Security-Scoped Bookmarks

iOS sandboxing prohibits direct arbitrary filesystem path access:
1. User selects media folders or files via native `UIDocumentPickerViewController`.
2. The app obtains temporary security-scoped access via `startAccessingSecurityScopedResource()`.
3. Permanent access across app launches is preserved using `bookmarkDataWithOptions(.withSecurityScope)`.
4. When accessing media during playback, `startAccessingSecurityScopedResource()` must be held active until playback stops or the player releases the asset.

---

## 6. Distribution & TestFlight Preparation

- **Archive Generation**:
  ```shell
  xcodebuild archive -project iosApp/VimivoApp.xcodeproj -scheme VimivoApp -archivePath build/VimivoApp.xcarchive
  ```
- **Signing & Team ID**: Set your Apple Developer Team ID in Xcode build settings under `Signing & Capabilities`.
- Unsigned CI verification pipeline is implemented in `.github/workflows/ios.yml`.
