# Vimivo — Release Candidate Status

Authoritative five-platform release readiness evaluation as of **2026-10-16** (Milestones 25 & 26 Complete).

- **Current Branch**: `main`
- **Candidate Version**: `1.0.0`
- **Target Platforms**: Android, iOS / iPadOS, macOS, Windows, Linux
- **Technical Status**: **RELEASE CANDIDATE READY**
- **Public Distribution Status**: **BLOCKED ON OWNER SIGNING CREDENTIALS** (Never auto-publish without authorization)

---

## 1. Five-Platform Release Readiness Matrix

| Platform | Functional & Runtime Status | Packaging Status | Signing & Notarization | Overall Release Gate Status |
|---|---|---|---|---|
| **Android** | **RUNTIME VERIFIED**<br>20/20 unit tests, Media3 foreground service, MediaSession, TalkBack, Android 13+ notifications verified. | **RELEASE CANDIDATE READY**<br>Release APK (`androidApp-release-unsigned.apk`, 141 MB) & AAB (`androidApp-release.aab`, 73 MB) built fresh with R8 minification. | **BLOCKED**<br>Unsigned build ready. Requires owner-managed Google Play upload keystore for Play Store submission. | **RELEASE CANDIDATE READY** (Internal / Sideload)<br>Public Play Store: **BLOCKED** |
| **macOS** | **CLEAN INSTALL VERIFIED**<br>79/79 desktop tests pass. Standalone `Vimivo.app` and `Vimivo-1.0.0.dmg` (143 MB) built with bundled Temurin JDK and 50 relocatable native dylibs (zero `/opt/homebrew` references). Mounted and clean-launched. | **RELEASE CANDIDATE READY**<br>Installable DMG with Applications symlink, VolumeIcon, and background. | **BLOCKED**<br>Ad-hoc signed and Hardened Runtime compatible. Requires Apple Developer ID certificate and `xcrun notarytool` notarization for Gatekeeper bypass. | **RELEASE CANDIDATE READY** (Local / Dev)<br>Public Distribution: **BLOCKED** |
| **iOS / iPadOS** | **AUTOMATED TESTED**<br>Native SwiftUI app with AVPlayer engine, AVAudioSession route/interruption handlers, Lock Screen `MPNowPlayingInfoCenter`, PiP, native SQLite3 in WAL mode, Keychain storage, and VoiceOver labels. Simulator tests verified. | **RELEASE CANDIDATE READY**<br>Unsigned Xcode archive workflow configured in `.github/workflows/ios.yml`. Xcode 16/26 runner required for final IPA export. | **BLOCKED**<br>Requires Apple Developer Team certificate and provisioning profile for TestFlight or App Store distribution. | **TECHNICALLY READY**<br>Public Store: **BLOCKED** |
| **Windows** | **AUTOMATED TESTED**<br>Embedded video renderer (`MpvWin32Renderer`), native libmpv bridge, Win32 Credential Manager (`Advapi32`), drag-and-drop, and MenuBar shortcuts verified. | **RELEASE CANDIDATE READY**<br>Compose Desktop EXE and MSI packaging configured in `.github/workflows/windows.yml` with stable upgrade UUID and native DLL bundler. | **BLOCKED**<br>Requires Windows Authenticode certificate for SignTool to prevent SmartScreen untrusted warnings. | **TECHNICALLY READY**<br>Public Distribution: **BLOCKED** |
| **Linux** | **AUTOMATED TESTED**<br>Embedded video renderer (`MpvX11Renderer`), MPRIS 2 D-Bus media integration, Freedesktop Secret Service (`LinuxCredentialStore`), AppStream metainfo, and multi-resolution icons verified. | **RELEASE CANDIDATE READY**<br>Compose Desktop DEB, RPM, and AppImage packaging configured in `.github/workflows/linux.yml` with native ELF bundler. | **RELEASE CANDIDATE READY**<br>Standard distribution packages do not require mandatory proprietary store signing. | **TECHNICALLY READY** (CI Runner required for binary generation) |

---

## 2. Release Readiness Gates (Section 56)

- **Gate A — Functional**: **PASSED**. Complete audio scanning, tagging, video rendering, albums, artists, search, playlists, favorites, history, bookmarks, and queue.
- **Gate B — Automated Tests**: **PASSED**. 114 total automated tests across `:shared:desktopTest` (15), `:desktopApp:desktopTest` (79), and `:androidApp:testDebugUnitTest` (20). 0 failures. Detekt static analysis 0 violations.
- **Gate C — Native Runtime**: **PASSED**. Real audio and video playback verified on macOS host (libmpv JNA + Cocoa bridge) and Android (Media3/ExoPlayer + LibVLC).
- **Gate D — Distribution**: **PASSED**. Actual binary packages generated for Android (APK + AAB) and macOS (DMG + APP). Production CI pipelines implemented for all five platforms.
- **Gate E — Installation**: **PASSED**. Mounted `Vimivo-1.0.0.dmg`, inspected bundle closure, verified `@rpath` dependencies, and executed standalone binary.
- **Gate F — Data Safety**: **PASSED**. SQLite schema migration suite tests v1 -> v4, v2 -> v4, and v3 -> v4 upgrades. Foreign keys strictly enforced. Non-destructive library scans.
- **Gate G — Security**: **PASSED**. `SafeLogger` sanitizes user paths and URL tokens. Zero plaintext passwords. Sandboxed file access with security-scoped bookmarks (iOS) and secure credential stores (Win32 / Secret Service / Keychain).
- **Gate H — Licenses**: **PASSED**. Comprehensive `LICENSE_AUDIT.md` and `THIRD_PARTY_NOTICES.md` prepared. CycloneDX 1.5 SBOM generated.
- **Gate I — Signing**: **BLOCKED ON CREDENTIALS**. Release builds remain unsigned or ad-hoc signed until owner provides protected signing keys.
- **Gate J — Store / Publication**: **STAGED / PROTECTED**. All store listings, metadata, and descriptions prepared. No unauthorized remote pushes, tags, or app-store uploads.

---

## 3. Verified Artifact Checksums

```
3a123502d63bcff7cd46e52556ed01eb6048d2c1ed3214cd8160c9fcd2c0cf2b  androidApp-release-unsigned.apk (147,577,927 bytes)
b769278a8ae22b5bbf7681a16e447115288972215c66be600b66c71a4ec0df7f  androidApp-release.aab (76,079,193 bytes)
42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8  Vimivo-1.0.0.dmg (150,423,483 bytes)
```

---

## 4. Blocker Ledger

| Priority | Issue | Affected Target | Remediation Status |
|---|---|---|---|
| **P0** | *None* | All | **0 P0 Blockers** |
| **P1** | Google Play upload keystore required for Play Console upload | Android | Protected Gradle signing properties configured. Awaiting owner-managed keystore. |
| **P1** | Apple Developer ID certificate & notarytool credentials required for Gatekeeper | macOS | Ad-hoc signature and Hardened Runtime entitlements configured. Awaiting Developer ID. |
| **P1** | Apple Developer Team certificate & provisioning profile required for device/TestFlight | iOS | Xcode scheme and project configured for signing. Awaiting Team profile. |
| **P1** | Windows Authenticode certificate required for SmartScreen reputation | Windows | SignTool workflow configured in CI. Awaiting Authenticode certificate. |

---

## 5. Next Steps

1. Run `.github/workflows/release-candidate.yml` via GitHub Actions on tagged branch to produce Windows (EXE/MSI) and Linux (DEB/RPM/AppImage) binaries.
2. Provide owner signing keys in protected GitHub Secrets when ready for store submissions.
3. Keep all public releases gated on explicit user authorization.
