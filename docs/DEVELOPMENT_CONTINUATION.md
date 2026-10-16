# Development continuation

Last verified on 2026-10-16 on macOS, branch `main`.
Milestones completed: **Milestone 25 (Final Five-Platform Quality Assurance) & Milestone 26 (Production Build Engineering & Release Candidate Preparation)**.

## Resume point

Implementation and quality gates are verified across all five target platforms (Android, iOS, macOS, Windows, Linux).

The latest completed increments are:

1. **Database Migration & Integrity Test Suite**: `DesktopDatabaseMigrationTest` verifies fresh database initialization (v0 -> v4), upgrade from older schemas (v1 -> v4, v2 -> v4, v3 -> v4), non-destructive migration of user data (playlists, favorites, history, bookmarks, queue, settings), strict foreign key constraint enforcement, and atomic transaction rollback on failure.
2. **End-to-End User Journeys**: `DesktopUserJourneysTest` covers all 7 production user scenarios:
   - Journey 1: Fresh install, media indexing, and recursive scanner discovery;
   - Journey 2: Music library operations, metadata search, favorites, playlists, and reordering;
   - Journey 3: Video playback lifecycle, duration tracking, resume positions, and bookmarks;
   - Journey 4: Continuous playback queue, next-track scheduling, shuffle, repeat, and paused restoration;
   - Journey 5: User data integrity, theme settings persistence, and `SafeLogger` path/token redaction;
   - Journey 6: Authorized network streaming and URL sanitization;
   - Journey 7: Fault recovery, graceful unavailable file reconciliation, and non-destructive deletions.
3. **Media Fixtures Suite**: Expanded `DesktopMediaMetadataExtractorTest` with zero-byte audio file handling, Bengali (`আমার গান`) and Unicode filename extraction, embedded artwork, and corrupted file resilience.
4. **Production Build Engineering**:
   - Android: Release APK (`androidApp-release-unsigned.apk`, 141 MB) and release App Bundle (`androidApp-release.aab`, 73 MB) built with R8 minification and resource shrinking against Android 16 (API 36).
   - macOS: Standalone `Vimivo.app` and DMG installer (`Vimivo-1.0.0.dmg`, 143 MB) built with self-contained Temurin JDK and 50 relocatable native dylibs (zero `/opt/homebrew` references). Mounted and clean-install launched.
5. **Cross-Platform CI/CD Workflows**:
   - `.github/workflows/macos.yml`: Automated macOS quality, native dylib bundling, DMG packaging, and SHA-256 verification.
   - `.github/workflows/ios.yml`: Automated iOS / iPadOS quality, Kotlin Multiplatform framework linking, and unsigned Xcode archive generation.
   - `.github/workflows/android.yml`: Enhanced with release APK and App Bundle generation and SHA-256 digests.
   - `.github/workflows/shared.yml`: PR validation and shared cross-platform tests.
   - `.github/workflows/release-candidate.yml`: Unified release candidate pipeline aggregating 5-platform artifacts, `SHA256SUMS.txt`, CycloneDX SBOM, and `release-manifest.json`.
6. **Supply-Chain Security & Manifest**:
   - `docs/SHA256SUMS.txt`: Cryptographic SHA-256 digests of built artifacts.
   - `docs/release-manifest.json`: Machine-readable build manifest with toolchain and artifact metadata.
   - `docs/sbom.json`: CycloneDX 1.5 Software Bill of Materials.
   - `docs/RELEASE_CANDIDATE_STATUS.md`: Authoritative release readiness assessment.

Do not rebuild these components. Continue from the backlog below.

## Next executable work

1. Execute `.github/workflows/release-candidate.yml` in GitHub Actions on an authorized tag/dispatch to generate remote Windows and Linux native binaries.
2. Configure Apple Developer signing certificate and provisioning profile in Xcode for App Store / TestFlight archive distribution when authorized.
3. Configure Google Play upload keystore for signing release Android App Bundle (`androidApp-release.aab`) when authorized.
4. Run clean-machine Linux and Windows runtime playback verification in physical or VM environments.

## External or hardware blockers

- Public Google Play store submission requires owner-managed upload keystore. Never commit signing keys.
- iOS physical device installation and TestFlight / App Store submission require owner-managed Apple Developer Team signing certificate and provisioning profile.
- Windows Authenticode certificate required for SignTool to establish SmartScreen reputation.
- macOS Gatekeeper bypass for public DMG distribution requires Apple Developer ID certificate and `xcrun notarytool` notarization.
- Linux and Windows packaging execution requires corresponding OS runners.

## Required verification

```shell
# Android
./gradlew :androidApp:testDebugUnitTest
./gradlew :androidApp:detekt
./gradlew :androidApp:assembleRelease :androidApp:bundleRelease

# macOS Desktop
./gradlew :desktopApp:desktopTest
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :desktopApp:packageDmg

# Shared & Static Analysis
./gradlew :shared:desktopTest
./gradlew detekt
```
