# Vimivo Official Five-Platform Distribution Plan

## 1. Distribution Philosophy & Boundary

Vimivo provides transparent, auditable, and self-contained distribution packages across all five supported operating systems.
- **Privacy Assurance**: Release packages contain zero embedded telemetry, tracking SDKs, or cloud analytics.
- **Data Safety**: Installers and package managers update executable binaries without modifying existing user libraries, database files, or media.
- **Approval Boundary**: All automated pipelines stage release candidates internally; no build is published to public package registries or app stores without explicit authorization.

---

## 2. Platform Distribution Engineering

### 2.1 Android
- **Target Formats**:
  1. **Direct Sideloading APK** (`androidApp-release-unsigned.apk` / signed): For users who prefer direct installation without app stores.
  2. **Android App Bundle (AAB)** (`androidApp-release.aab`): For Google Play Store delivery.
- **Toolchain & Standards**:
  - Pinned Android Gradle Plugin 8.13.2, Compile SDK 36, Target SDK 36 (2026 store requirements), Min SDK 26 (Android 8.0 Oreo).
  - R8 minification and resource shrinking enabled to reduce bundle footprint.
- **Signing Pipeline**:
  - Sideload APKs signed with standard release keystore.
  - Google Play AAB signed with owner-managed Play upload key.
- **Rollout Strategy**:
  - Stage 1: Google Play Internal Testing track (team verification).
  - Stage 2: Closed Testing track (12 opted-in testers for 14 continuous days where required by Play policy).
  - Stage 3: Phased Production Rollout (10% -> 25% -> 50% -> 100%).

---

### 2.2 macOS Desktop
- **Target Format**: Apple Disk Image (`.dmg`) and standalone application bundle (`.app`).
- **Runtime Closure**:
  - Bundled Eclipse Temurin Java 17 Runtime Environment (`lib/runtime`).
  - Bundled `libmpv.2.dylib` (2.0.0) with complete transitive closure of 50 relocatable native dylibs (FFmpeg, libass, libplacebo, dav1d, Little-CMS2).
  - Zero external dependencies: requires no Homebrew installation or system Java.
- **Security & Signing**:
  - Nested native dylib code signing via `codesign --force --sign "Developer ID Application: ..."` with Hardened Runtime enabled (`--options runtime`).
  - Entitlements: `com.apple.security.cs.allow-jit`, `com.apple.security.cs.allow-unsigned-executable-memory`, `com.apple.security.device.audio-input`.
- **Notarization & Gatekeeper**:
  - Submission via `xcrun notarytool submit Vimivo-1.0.0.dmg --keychain-profile ... --wait`.
  - Stapling ticket via `xcrun stapler staple Vimivo-1.0.0.dmg`.

---

### 2.3 Windows Desktop
- **Target Formats**:
  1. **NSIS Executable Installer** (`Vimivo-Setup-1.0.0.exe`): Full GUI installer with Start Menu shortcuts and Desktop icon.
  2. **Windows Installer Package** (`Vimivo-1.0.0.msi`): Enterprise-ready MSI with stable Upgrade Code GUID.
- **Runtime Closure**:
  - Bundled Temurin JDK 17 runtime in `app/runtime`.
  - Bundled `mpv-2.dll` and transitive Windows DLLs placed directly beside executable for secure loading (Safe DLL search mode).
- **Signing & Reputation**:
  - Authenticode signing via `signtool.exe sign /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 ...`.
  - SmartScreen reputation built through valid certificate and Microsoft Defender submission.

---

### 2.4 Linux Desktop
- **Target Formats**:
  1. **Debian Package** (`.deb`): For Ubuntu, Debian, Linux Mint, Pop!_OS (`/opt/vimivo`).
  2. **Red Hat Package** (`.rpm`): For Fedora, RHEL, openSUSE (`/opt/vimivo`).
  3. **AppImage** (`.AppImage`): Self-contained single-file binary for any Linux distribution with GLIBC 2.35+.
  4. **Flatpak**: Sandboxed desktop manifest configured for Flathub submission.
- **Desktop Integration**:
  - Standard `.desktop` entry (`dev.vimivo.desktop.desktop`).
  - Multi-resolution PNG and SVG icons placed in `/usr/share/icons/hicolor/`.
  - AppStream metadata specification (`dev.vimivo.desktop.metainfo.xml`).
  - MPRIS 2 D-Bus media controls and Freedesktop Secret Service credential integration.

---

### 2.5 iOS / iPadOS
- **Target Format**: Unsigned Xcode Archive (`.xcarchive`) and Signed Application Package (`.ipa`).
- **Store Preparation**:
  - SwiftUI native shell linking Kotlin Multiplatform shared framework (`VimivoShared.framework`).
  - Configured for manual release in App Store Connect upon App Review approval.
  - Blocked on owner Apple Developer Team certificate and provisioning profile configuration.

---

## 3. Cryptographic Verification & Manifests

Every release build generates an authoritative artifact record:
1. `docs/SHA256SUMS.txt`: SHA-256 cryptographic hashes generated immediately following build completion.
2. `docs/release-manifest.json`: Machine-readable metadata record detailing file sizes, commit SHA, toolchains, and compatibility.
3. `docs/sbom.json`: CycloneDX 1.5 Software Bill of Materials detailing full dependency provenance.
