# Changelog

All notable changes to Vimivo are documented in this file.

## [1.0.0-rc.1] - 2026-10-16

### Added
- **Five-Platform Release Architecture**: Cross-platform support for Android, iOS / iPadOS, macOS, Windows, and Linux built upon Kotlin Multiplatform and Compose Multiplatform.
- **Production Package Generation**:
  - **Android**: Release APK (`androidApp-release-unsigned.apk`, 141 MB) and Google Play App Bundle (`androidApp-release.aab`, 73 MB) with R8 minification, resource shrinking, and Android 16 (API 36) target SDK.
  - **macOS**: Standalone `Vimivo.app` and DMG installer (`Vimivo-1.0.0.dmg`, 143 MB) bundling Eclipse Temurin OpenJDK 17 and 50 relocatable native dylibs (libmpv, FFmpeg, libplacebo, dav1d, libass) with zero external `/opt/homebrew` dependencies. Clean install and launch verified.
  - **iOS / iPadOS**: Native SwiftUI application with Kotlin Multiplatform shared framework (`VimivoShared.framework`), AVPlayer engine, background audio playback (`UIBackgroundModes: ["audio"]`), Lock Screen / Control Center integration (`MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`), PiP, sandboxed Files integration, and native SQLite3 persistence. Unsigned Xcode archive pipeline implemented in `.github/workflows/ios.yml`.
  - **Windows**: Compose Desktop EXE and MSI installers with stable upgrade UUID, start menu shortcuts, Win32 Credential Manager integration, embedded video rendering (`MpvWin32Renderer`), and automated CI pipeline in `.github/workflows/windows.yml`.
  - **Linux**: Compose Desktop DEB and RPM packages, standalone AppImage, MPRIS 2 D-Bus media integration, Freedesktop Secret Service credential store, embedded video rendering (`MpvX11Renderer`), and automated CI pipeline in `.github/workflows/linux.yml`.
- **Quality Assurance & Verification**:
  - Automated SQLite database migration test suite (`DesktopDatabaseMigrationTest`) validating fresh initialization (v0 -> v4), older schema migrations (v1/v2/v3 -> v4), user data preservation, strict foreign key enforcement, and atomic transaction rollbacks.
  - End-to-end user journeys test suite (`DesktopUserJourneysTest`) validating 7 production scenarios: fresh install and recursive scanner discovery, library search/favorites/playlists, video lifecycle/resume positions/bookmarks, continuous queue scheduling, user settings and `SafeLogger` redaction, network streaming, and fault recovery.
  - Media fixture validation for zero-byte audio files, corrupted streams, and Bengali (`আমার গান`) and Unicode tags and filenames.
  - 114 automated tests passing across shared, desktop, and Android modules with 0 failures.
  - Detekt static analysis passing with 0 violations.
- **Software Supply-Chain & Security**:
  - CycloneDX 1.5 Software Bill of Materials (`docs/sbom.json`).
  - Machine-readable release manifest (`docs/release-manifest.json`).
  - Cryptographic SHA-256 checksums (`docs/SHA256SUMS.txt`).
  - Protected GitHub Actions workflows (`macos.yml`, `ios.yml`, `android.yml`, `shared.yml`, `release-candidate.yml`) establishing strict boundaries between build, test, package, and publication.
