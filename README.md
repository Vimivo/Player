# Vimivo

Vimivo is a privacy-first universal music and video player. Android remains the primary application, with an incremental macOS Compose Desktop target backed by libmpv.

## Current foundation

- Android 8.0+ (API 26), compiling and targeting API 36
- Kotlin Multiplatform shared models, repository contracts, settings, playback contracts, UI, and navigation
- Compose Multiplatform with Material 3, light/dark/AMOLED palettes, and edge-to-edge Android hosting
- Android MediaStore library adapter and DataStore settings adapter
- Working Media3 engine with controlled LibVLC fallback behind a shared `PlayerEngine` contract
- macOS Compose Desktop application using the same shared contracts and a real JNA/libmpv adapter
- Koin dependency injection, Coroutines/Flow, Coil, Detekt, unit tests, and instrumentation-test setup

## Build

Install JDK 17 and Android SDK Platform 36, then set `sdk.dir` in `local.properties` or `ANDROID_HOME`.

```shell
./gradlew :androidApp:assembleDebug
./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest detekt
```

The debug APK is written below `androidApp/build/outputs/apk/debug/`.

For macOS development, install a current libmpv build and run:

```shell
./gradlew :desktopApp:run
./gradlew :desktopApp:packageDmg
```

The desktop adapter loads `libmpv` from the system library path. A custom absolute library path may be supplied with `-Dvimivo.mpv.library=/path/to/libmpv.dylib`. On macOS development builds, a small JAWT/Objective-C++ bridge uses libmpv's OpenGL render API inside a Compose `SwingPanel`; the bridge is compiled locally and is not yet packaged for redistribution. The current DMG is therefore not a standalone release. See [macOS distribution](docs/MACOS_DISTRIBUTION.md) for verified blockers and release requirements.

## Application ID

Before the first public release, override the temporary ID with:

```shell
./gradlew :androidApp:assembleRelease -Pvimivo.applicationId=com.example.vimivo
```

Choose this once with care: stores and Android treat the published application ID as the application's permanent identity. Changing it after release creates a different app and breaks upgrades.

See [architecture](docs/ARCHITECTURE.md), [features](docs/FEATURES.md), [development](docs/DEVELOPMENT.md), and [decisions](docs/DECISIONS.md).

The authorized Windows target reuses the Compose Desktop application and shared SQLite repositories. Windows persistence and packaging boundaries are documented in [Windows architecture](docs/WINDOWS_ARCHITECTURE.md). Native Windows video rendering and clean-machine installer validation are not yet complete.

Playback details are documented in [engine capabilities](docs/PLAYBACK_CAPABILITIES.md) and the [media test matrix](docs/MEDIA_TEST_MATRIX.md).

# Player
