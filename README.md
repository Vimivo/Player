# Vimivo

Vimivo is an early-stage, privacy-first universal music and video player. The current milestone establishes an Android application and a Kotlin Multiplatform foundation; it deliberately does not implement full playback engines yet.

## Current foundation

- Android 8.0+ (API 26), compiling and targeting API 36
- Kotlin Multiplatform shared models, repository contracts, settings, playback contracts, UI, and navigation
- Compose Multiplatform with Material 3, light/dark/AMOLED palettes, and edge-to-edge Android hosting
- Android MediaStore library adapter and DataStore settings adapter
- Working Media3 engine with controlled LibVLC fallback behind a shared `PlayerEngine` contract
- Koin dependency injection, Coroutines/Flow, Coil, Detekt, unit tests, and instrumentation-test setup

## Build

Install JDK 17 and Android SDK Platform 36, then set `sdk.dir` in `local.properties` or `ANDROID_HOME`.

```shell
./gradlew :androidApp:assembleDebug
./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest detekt
```

The debug APK is written below `androidApp/build/outputs/apk/debug/`.

## Application ID

Before the first public release, override the temporary ID with:

```shell
./gradlew :androidApp:assembleRelease -Pvimivo.applicationId=com.example.vimivo
```

Choose this once with care: stores and Android treat the published application ID as the application's permanent identity. Changing it after release creates a different app and breaks upgrades.

See [architecture](docs/ARCHITECTURE.md), [features](docs/FEATURES.md), [development](docs/DEVELOPMENT.md), and [decisions](docs/DECISIONS.md).

Playback details are documented in [engine capabilities](docs/PLAYBACK_CAPABILITIES.md) and the [media test matrix](docs/MEDIA_TEST_MATRIX.md).

# Player
