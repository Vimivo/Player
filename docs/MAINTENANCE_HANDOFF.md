# Vimivo — Engineering Maintenance Handoff Package

This document serves as the complete technical handoff package for engineers maintaining, updating, and releasing Vimivo across Android, iOS/iPadOS, macOS, Windows, and Linux.

---

## 1. Repository & Architecture Summary

- **Repository**: [https://github.com/Vimivo/Player](https://github.com/Vimivo/Player)
- **Product**: Offline-first universal music and video player.
- **Clean Architecture Pattern**:
  - `shared/src/commonMain/`: Portable Kotlin business logic, models (`MediaItem`, `Playlist`, `SmartPlaylistDefinition`), contracts (`PlayerEngine`, `UnifiedPlaybackQueue`, `LibraryRepository`), and zero platform classes.
  - `desktopApp/`: Compose Multiplatform Desktop for macOS, Windows, and Linux. Houses libmpv client API via JNA, native video render bridges, and SQLite database repository.
  - `androidApp/`: Jetpack Compose and AndroidX Media3 `MediaSessionService` foreground playback engine, Room persistence.
  - `iosApp/`: Native SwiftUI application consuming `VimivoShared.framework` with Apple AVFoundation and sandboxed Files integration.
- **Database Schema**: SQLite Version 6 with foreign-key enforcement, WAL mode, and forward-only non-destructive migrations.

---

## 2. Toolchain Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| **JDK** | 17 (Eclipse Temurin or Amazon Corretto) | Required for Gradle build and Compose Desktop packaging |
| **Android SDK** | Compile & Target API 36, Build-Tools 36.0.0 | Android builds |
| **Xcode** | Xcode 15+ | macOS native bridge compilation and iOS builds |
| **libmpv** | Version 0.35+ | Native multimedia playback and video rendering on desktop |
| **Gradle** | Managed via `./gradlew` wrapper | Project build automation |

---

## 3. Canonical Verification Commands

Every code change must satisfy the quality gate before merging:

```shell
# One-command full verification:
./scripts/verify_all.sh

# Or via Gradle:
./gradlew verifyAll

# Documentation link validation:
./scripts/validate_docs.sh
```

---

## 4. CI/CD Workflow Map

All workflows live in `.github/workflows/`:
- `shared.yml`: Validates shared multiplatform domain contracts and runs `:shared:desktopTest`.
- `macos.yml`: Builds macOS desktop target and packages standalone DMG.
- `android.yml`: Runs Android unit tests, Detekt, and builds release APK & App Bundle (AAB).
- `ios.yml`: Compiles shared iOS framework, runs simulator unit tests, and creates unsigned archive.
- `windows.yml`: Builds Windows desktop target and packages MSI / EXE installers.
- `linux.yml`: Builds Linux desktop target and packages DEB / RPM packages.
- `release-candidate.yml`: Orchestrates 5-platform release candidate builds, generates SHA256SUMS, SBOM, and release manifest.
- `publish-release.yml`: Protected publication workflow requiring explicit manual trigger and confirmation token (`YES_I_CONFIRM_OFFICIAL_RELEASE`).
- `hotfix-release.yml`: Rapid patch release workflow for critical hotfixes.
- `dependency-security.yml`: Pinned dependency auditing and vulnerability scanning.

---

## 5. Database Schema & Migration Guide

The SQLite database schema is defined in `desktopApp/src/desktopMain/kotlin/dev/vimivo/desktop/library/DesktopDatabase.kt`.
When adding tables or columns:
1. Increment `DesktopDatabase.SCHEMA_VERSION`.
2. Add a `migrateToVersionN(statement)` method in `DesktopDatabase.kt`.
3. Never modify or drop existing migrations (v1 through v6 must remain intact for backward compatibility).
4. Update `DesktopDatabaseMigrationTest.kt` with a test verifying that upgrading from `N-1` preserves all existing records, foreign keys, and indexes.

---

## 6. Native Dependency Maintenance

- **`libmpv`**:
  - macOS: Bundled in DMG under `Vimivo.app/Contents/Frameworks/` with `@rpath` loader commands. To re-bundle after mpv updates, run `desktopApp/src/desktopMain/native/macos/bundle_macos_mpv.sh`.
  - Windows: `mpv-2.dll` and dependencies bundled in MSI/EXE runtime path.
  - Linux: Dynamically linked against system `libmpv.so.1` or bundled in AppImage.
- **`jaudiotagger`**: Managed via Gradle in `desktopApp/build.gradle.kts`. Used for Mode B embedded audio tag writing (MP3, FLAC, OGG, M4A, WAV).

---

## 7. Emergency Runbooks & Incident Response

In the event of an urgent defect:
- Consult [Hotfix Runbook](HOTFIX_RUNBOOK.md) for step-by-step hotfix branching, testing, and release procedures.
- Consult [Incident Response Guide](INCIDENT_RESPONSE.md) for crash triage, database rollback policies, and severity classification.

---

## 8. Release Signing & Store Credentials

Production releases require owner-managed credentials injected via protected GitHub Secrets:
- **Android**: Google Play upload keystore (`VIMIVO_KEYSTORE_BASE64`, `VIMIVO_KEY_ALIAS`, `VIMIVO_KEY_PASSWORD`).
- **Apple (macOS / iOS)**: Apple Developer ID Application certificate and App Store Connect API keys for `notarytool` and TestFlight.
- **Windows**: Authenticode certificate for `signtool` signing.

---

## 9. Next Recommended Tasks

1. Expand platform-native file provider integrations for Android Storage Access Framework and iOS FileProvider for external SD/OTG storage.
2. Monitor private beta feedback forms under `.github/ISSUE_TEMPLATE/` as testers exercise real-world media libraries.
3. Conduct code signing and notarization verification once official owner certificates are provisioned in protected CI environments.
