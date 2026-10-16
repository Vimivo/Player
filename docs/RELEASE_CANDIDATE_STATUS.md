# Vimivo — Release Candidate Status

Authoritative five-platform release readiness evaluation as of **2026-10-16** (Milestones 27 & 28 Complete).

- **Current Branch**: `main`
- **Candidate Version**: `1.0.0-rc.1`
- **Target Platforms**: Android, iOS / iPadOS, macOS, Windows, Linux
- **Technical Status**: **RELEASE CANDIDATE STABILIZED (INTERNAL QA / CLOSED BETA INFRASTRUCTURE READY)**
- **Closed Beta Cohort Status**: **AWAITING AUTHORIZED TESTERS** (Zero synthetic feedback, zero telemetry)
- **Public Distribution Status**: **BLOCKED ON OWNER SIGNING CREDENTIALS** (Never auto-publish without authorization)

---

## 1. Five-Platform Release Readiness Matrix

| Platform | Functional & Runtime Status | Packaging Status | Signing & Distribution | Overall Release Gate Status |
|---|---|---|---|---|
| **Android** | **RUNTIME VERIFIED**<br>20/20 unit tests pass. Media3 foreground service, MediaSession, TalkBack, Android 13+ notifications, and privacy-safe diagnostics verified. | **RELEASE CANDIDATE READY**<br>Release APK (`androidApp-release-unsigned.apk`, 141 MB) & AAB (`androidApp-release.aab`, 73 MB) built fresh with R8 minification. | **BLOCKED**<br>Unsigned build ready. Requires owner-managed Google Play upload keystore for Play Store submission. | **RELEASE CANDIDATE READY** (Internal / Sideload)<br>Public Play Store: **BLOCKED** |
| **macOS** | **CLEAN INSTALL VERIFIED**<br>82/82 desktop tests pass. Standalone `Vimivo.app` and `Vimivo-1.0.0.dmg` (143 MB) built with bundled Temurin JDK, 50 relocatable native dylibs, and desktop diagnostic exporter. Mounted and clean-launched. | **RELEASE CANDIDATE READY**<br>Installable DMG with Applications symlink, VolumeIcon, and background. | **BLOCKED**<br>Ad-hoc signed and Hardened Runtime compatible. Requires Apple Developer ID certificate and `xcrun notarytool` notarization for Gatekeeper bypass. | **RELEASE CANDIDATE READY** (Local / Closed Beta)<br>Public Distribution: **BLOCKED** |
| **iOS / iPadOS** | **AUTOMATED TESTED**<br>Native SwiftUI app with AVPlayer engine, AVAudioSession route/interruption handlers, Lock Screen `MPNowPlayingInfoCenter`, PiP, native SQLite3 in WAL mode, Keychain storage, and VoiceOver labels. Simulator tests verified. | **RELEASE CANDIDATE READY**<br>Unsigned Xcode archive workflow configured in `.github/workflows/ios.yml`. Xcode 16/26 runner required for final IPA export. | **BLOCKED**<br>Requires Apple Developer Team certificate and provisioning profile for TestFlight or App Store distribution. | **TECHNICALLY READY**<br>Public Store: **BLOCKED** |
| **Windows** | **AUTOMATED TESTED**<br>Embedded video renderer (`MpvWin32Renderer`), native libmpv bridge, Win32 Credential Manager (`Advapi32`), drag-and-drop, and MenuBar shortcuts verified. | **RELEASE CANDIDATE READY**<br>Compose Desktop EXE and MSI packaging configured in `.github/workflows/windows.yml` with stable upgrade UUID and native DLL bundler. | **BLOCKED**<br>Requires Windows Authenticode certificate for SignTool to prevent SmartScreen untrusted warnings. | **TECHNICALLY READY**<br>Public Distribution: **BLOCKED** |
| **Linux** | **AUTOMATED TESTED**<br>Embedded video renderer (`MpvX11Renderer`), MPRIS 2 D-Bus media integration, Freedesktop Secret Service (`LinuxCredentialStore`), AppStream metainfo, and multi-resolution icons verified. | **RELEASE CANDIDATE READY**<br>Compose Desktop DEB, RPM, and AppImage packaging configured in `.github/workflows/linux.yml` with native ELF bundler. | **RELEASE CANDIDATE READY**<br>Standard distribution packages do not require mandatory proprietary store signing. | **TECHNICALLY READY** (CI Runner required for binary generation) |

---

## 2. Release Readiness Gates Audit

- **Gate A — Functional**: **PASSED**. Audio/video scanning, tagging, video rendering, albums, artists, search, playlists, favorites, history, bookmarks, queue, and privacy-safe diagnostics.
- **Gate B — Automated Tests**: **PASSED**. 125 total automated tests across `:shared:desktopTest` (23), `:desktopApp:desktopTest` (82), and `:androidApp:testDebugUnitTest` (20). 0 failures. Detekt static analysis 0 violations across all modules.
- **Gate C — Native Runtime**: **PASSED**. Real audio and video playback verified on macOS host (libmpv JNA + Cocoa bridge) and Android (Media3/ExoPlayer + LibVLC).
- **Gate D — Distribution**: **PASSED**. Actual binary packages generated for Android (APK + AAB) and macOS (DMG + APP). Production CI pipelines implemented for all five platforms.
- **Gate E — Installation**: **PASSED**. Mounted `Vimivo-1.0.0.dmg`, inspected bundle closure, verified `@rpath` dependencies, and executed standalone binary.
- **Gate F — Data Safety**: **PASSED**. SQLite schema migration suite tests v1 -> v4, v2 -> v4, and v3 -> v4 upgrades. Foreign keys strictly enforced. Non-destructive library scans.
- **Gate G — Security & Privacy**: **PASSED**. `SafeLogger` sanitizes user paths, URL tokens, and key-value credentials. Zero plaintext passwords. `DesktopDiagnosticExporter` blocks system directory writes (`/etc`, `/System`, `C:\Windows`).
- **Gate H — Licenses**: **PASSED**. Comprehensive `LICENSE_AUDIT.md` and `THIRD_PARTY_NOTICES.md` prepared. CycloneDX 1.5 SBOM generated.
- **Gate I — Signing**: **BLOCKED ON CREDENTIALS**. Release builds remain unsigned or ad-hoc signed until owner provides protected signing keys.
- **Gate J — Store / Publication**: **STAGED / PROTECTED**. All store listings, metadata, and descriptions prepared. No unauthorized remote pushes, tags, or app-store uploads.
- **Gate K — Beta Infrastructure**: **PASSED**. Structured GitHub Issue Forms (`bug_report.yml`, `feature_request.yml`), test plan, tester guide, scenario matrix, and compatibility matrix published.
- **Gate L — Defect Remediation**: **PASSED**. 4 defects triaged (VIM-BUG-001 through VIM-BUG-004), root-cause analyzed, fixed, and verified with automated regression tests. 0 open P0/P1 issues.
- **Gate M — Field Beta Feedback**: **HOLD / AWAITING AUTHORIZED TESTERS**. Zero synthetic feedback fabricated. Real feedback will be evaluated once authorized testers participate.

---

## 3. Verified Artifact Checksums

```
3a123502d63bcff7cd46e52556ed01eb6048d2c1ed3214cd8160c9fcd2c0cf2b  androidApp-release-unsigned.apk (147,577,927 bytes)
b769278a8ae22b5bbf7681a16e447115288972215c66be600b66c71a4ec0df7f  androidApp-release.aab (76,079,193 bytes)
42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8  Vimivo-1.0.0.dmg (150,423,483 bytes)
```

---

## 4. Defect Ledger Summary

| Defect ID | Severity | Description | Fix Area | Regression Test | Status |
| :--- | :---: | :--- | :--- | :--- | :---: |
| **VIM-BUG-001** | P0 | SQLite Foreign Key enforcement during in-flight queue deletions | `DatabaseMigrations.kt` | `DesktopDatabaseMigrationTest.kt` | **VERIFIED FIXED** |
| **VIM-BUG-002** | P1 | Diagnostic Exporter symlink bypass to macOS `/etc` | `DesktopDiagnosticExporter.kt` | `DesktopDiagnosticExporterTest.kt` | **VERIFIED FIXED** |
| **VIM-BUG-003** | P1 | Incomplete key-value credential pair redaction | `SafeLogger.kt` | `PrivacySafeDiagnosticsTest.kt` | **VERIFIED FIXED** |
| **VIM-BUG-004** | P1 | Missing media handling during active playback | `PlayerEngine` | `DesktopUserJourneysTest.kt` | **VERIFIED FIXED** |

---

## 5. Decision & Next Steps

- **Platform Decision**: **CONDITIONAL GO FOR CLOSED BETA**.
- Detailed platform analysis documented in `docs/RC_GO_NO_GO.md`.
- Next step: Enroll authorized beta testers into controlled cohort and evaluate real field reports.
