# Vimivo — iOS Development & Target Setup

This guide documents the setup, architecture, and verification workflows for the Vimivo iOS target (`iosApp`).

---

## 1. Prerequisites

- **Host Machine**: macOS (Apple Silicon recommended)
- **Xcode**: Xcode 16.0 or newer with iOS 17+ SDK installed
- **JDK**: Java Development Kit 17 (Eclipse Temurin or Zulu)
- **Kotlin Multiplatform**: Kotlin 2.3+ with CocoaPods or direct framework linking

---

## 2. Architecture & Target Integration

Vimivo's iOS target combines shared Kotlin Multiplatform business logic and data structures with a high-performance native iOS playback engine:

```text
┌────────────────────────────────────────────────────────┐
│                        iosApp                          │
│        SwiftUI 5 / NavigationStack / AVPlayer          │
│   • Views (HomeView, MusicView, VideoView, Settings)   │
│   • Playback (AVPlayerPlaybackEngine, AudioSession)    │
│   • Widgets (VimivoWidgets, AppIntents)                │
└───────────────────────────┬────────────────────────────┘
                            │ Links
                            ▼
┌────────────────────────────────────────────────────────┐
│                     VimivoShared                       │
│      shared/build/bin/iosSimulatorArm64/debugFramework │
│   • Domain Models (MediaItem, Playlist, Queue)         │
│   • Concurrency (PlatformLock via NSLock)              │
│   • Future shared Compose Multiplatform UI screens    │
└────────────────────────────────────────────────────────┘
```

---

## 3. Building the Shared Framework

To compile the shared framework for the iOS Simulator:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

To compile for physical iOS devices (arm64):

```bash
./gradlew :shared:linkReleaseFrameworkIosArm64
```

The resulting framework is placed in:
- `shared/build/bin/iosSimulatorArm64/debugFramework/VimivoShared.framework`
- `shared/build/bin/iosArm64/releaseFramework/VimivoShared.framework`

---

## 4. Building & Running with Xcode

Open the Xcode project located at:
```bash
open iosApp/iosApp.xcodeproj
```

Select the **iosApp** scheme and target an iOS 17+ simulator (e.g., iPhone 17 or iPhone 16 Pro). Press **Cmd + R** to build and run.

### Command Line Build & Simulator Run

1. List available simulator devices:
   ```bash
   xcrun simctl list devices | grep iPhone
   ```

2. Build the app using `xcodebuild`:
   ```bash
   xcodebuild -project iosApp/iosApp.xcodeproj \
     -scheme iosApp \
     -destination 'platform=iOS Simulator,name=iPhone 17' \
     -configuration Debug build
   ```

3. Boot simulator and install:
   ```bash
   xcrun simctl boot <SIMULATOR_UDID>
   xcrun simctl install <SIMULATOR_UDID> <PATH_TO_BUILT_APP>/iosApp.app
   xcrun simctl launch <SIMULATOR_UDID> dev.vimivo.ios
   ```

4. Capture a simulator screenshot:
   ```bash
   xcrun simctl io <SIMULATOR_UDID> screenshot screenshot.png
   ```

---

## 5. Quality Gate

All code must pass the standard quality gate before commits:
```bash
./scripts/verify_all.sh
```

When changing iOS build files, verify compilation directly with:
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17' build
```
