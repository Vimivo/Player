# Vimivo Safe Application Update Strategy

## 1. Core Principles & Philosophy

Software updates should be transparent, safe, and entirely controlled by the user.
- **Zero Automatic Silent Execution**: Vimivo will never download executable code or binaries in the background without explicit user request.
- **No Third-Party Update Trackers**: Update checks rely exclusively on direct HTTPS metadata from the official repository or release server.
- **Zero Account Infrastructure**: Checking for updates does not require registration, login, or personal identifiers.
- **Platform-Native Update Alignment**: Store-managed platforms (Google Play, Apple App Store) rely on official store delivery channels; desktop platforms provide user-initiated checks.

---

## 2. Update Mechanism by Platform Target

| Target Platform | Primary Update Channel | Secondary / Direct Channel | Mechanism & User Experience |
| :--- | :--- | :--- | :--- |
| **Android** | Google Play Store | GitHub Releases (APK Sideload) | Standard Play Store background update or direct download of signed APK replacement. |
| **iOS / iPadOS** | Apple App Store | TestFlight (Beta cohorts) | Native iOS App Store background update. |
| **macOS Desktop** | GitHub Releases / Website | Sparkle / Native DMG | User clicks "Check for Updates" in Settings -> notified of newer version -> downloads verified DMG. |
| **Windows Desktop**| GitHub Releases / Website | NSIS / MSI Installer | User initiates check -> downloads verified `.exe` / `.msi` -> installer upgrades binaries cleanly. |
| **Linux Desktop**  | Package Manager (`apt`/`dnf`) | Direct `.deb` / `.rpm` / AppImage | Updates distributed through distribution package repositories or manual package replacement. |

---

## 3. Update Manifest & Verification Architecture

For desktop targets, Vimivo implements a lightweight, secure update check engine:
- Shared Logic: `shared/src/commonMain/kotlin/dev/vimivo/core/update/UpdateChecker.kt`
- Data Model: `shared/src/commonMain/kotlin/dev/vimivo/core/update/UpdateManifest.kt`
- Desktop Service: `desktopApp/src/desktopMain/kotlin/dev/vimivo/desktop/update/DesktopUpdateService.kt`

```
┌────────────────────────────────────────────────────────┐
│             Desktop User Initiates Check               │
│               (Settings -> About/Update)               │
└──────────────────────────┬─────────────────────────────┘
                           │ HTTPS GET (Timeout: 5000ms)
                           ▼
┌────────────────────────────────────────────────────────┐
│             Official release-manifest.json             │
│            (Hosted on GitHub Raw / CDN)                │
└──────────────────────────┬─────────────────────────────┘
                           │ JSON Payload
                           ▼
┌────────────────────────────────────────────────────────┐
│                     UpdateChecker                      │
│  - Validates HTTPS protocol (Rejects HTTP)             │
│  - Checks for forbidden credential query params        │
│  - Parses SemanticVersion (1.0.0 > 1.0.0-rc.1)         │
│  - Matches platform & architecture (macos arm64)       │
└──────────────────────────┬─────────────────────────────┘
                           │ Result Evaluation
                           ▼
┌────────────────────────────────────────────────────────┐
│                      Desktop UI                        │
│  - Up to Date: Shows check timestamp                   │
│  - Update Available: Shows new version, release notes  │
│    link, SHA-256 hash, and download button             │
└────────────────────────────────────────────────────────┘
```

---

## 4. Semantic Version Comparison Rules

The version comparator (`SemanticVersion.kt`) adheres to SemVer 2.0 specifications:
1. **Core Precedence**: `major.minor.patch` comparison takes precedence (e.g., `1.0.1` > `1.0.0`).
2. **Prerelease Ordering**: A stable release is always newer than any prerelease candidate of the identical version tuple (e.g., `1.0.0` > `1.0.0-rc.2` > `1.0.0-rc.1` > `1.0.0-beta.3`).
3. **Prerelease Type Precedence**: `alpha` < `beta` < `rc`.
4. **Prerelease Number Precedence**: `1.0.0-rc.2` > `1.0.0-rc.1`.

---

## 5. Security & Network Safeguards

1. **Protocol Enforcement**: Update check URLs must start with `https://`. Plain `http://` requests are rejected immediately.
2. **Token Stripping**: Manifest URLs containing sensitive authentication query parameters (`token=`, `password=`, `secret=`) are rejected to prevent credential leakage.
3. **Integrity Verification**: Release manifests publish the cryptographic SHA-256 hash of each artifact. Users can verify downloaded packages prior to execution.
4. **Network Resilience**: Strict 5000ms connect and read timeouts prevent UI hangs during poor connectivity or offline usage.
