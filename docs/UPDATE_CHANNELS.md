# Vimivo — Platform-Native Update Channels

This document defines the release channel taxonomy, channel isolation rules, and platform-specific software distribution architectures for Vimivo.

---

## 1. Release Channel Taxonomy

| Channel | Target Audience | Stability Requirement | Auto-Check Default | Promotion Gate |
| :--- | :--- | :--- | :--- | :--- |
| **STABLE** | All end users | 100% verified release candidate, zero known P0/P1 defects | Manual or Opt-in Periodic | Manual maintainer sign-off & signed tag |
| **RELEASE_CANDIDATE (RC)** | Staging & QA testers | Feature-complete, passed full regression gate | Manual check only | Automated test suite + unified verification script |
| **BETA** | Community testers | Feature-complete preview builds | Opt-in Beta track | Continuous integration green build |
| **DEVELOPMENT (DEV)** | Internal developers | Experimental / nightly builds | Disabled | Branch builds |

---

## 2. Channel Isolation & Safety Rules

1. **No Silent Migration**: Users on the STABLE channel are never automatically switched to BETA or DEVELOPMENT channels.
2. **Database Invariants**: Experimental or developmental schema changes must never mutate stable database files without an explicit non-reversible warning and automatic backup.
3. **Packaging Identity Separation**: If development builds are installed alongside stable builds, they must use distinct application IDs (e.g. `dev.vimivo.player.dev` vs `dev.vimivo.player`) to prevent data corruption.

---

## 3. Platform Distribution Architectures

### 3.1 Android Distribution
- **Google Play Store**: Updates are managed entirely through the Google Play Store client via standard APK/AAB store delivery. Vimivo does not install background APKs over Google Play installations.
- **Direct APK Distribution**: Direct APK downloads from authorized repository releases check compatibility against existing package signing keys. Android Package Installer enforces certificate matching before performing an in-place upgrade.

### 3.2 iOS & iPadOS Distribution
- **App Store**: Distributed exclusively through Apple App Store and TestFlight.
- **Policy**: Per Apple Developer Guidelines and iOS sandbox constraints, self-updating executable code is strictly prohibited. App updates are delivered natively via the App Store.

### 3.3 macOS Distribution
- **Direct Developer ID Distribution**: Signed with Apple Developer ID Application certificate, Hardened Runtime enabled, and notarized by Apple Ticket Service. Delivered as a read-only DMG mounting image.
- **Mac App Store**: Managed via Apple App Store distribution where applicable.

### 3.4 Windows Distribution
- **Windows Installer (MSI / EXE)**: Authenticode-signed installer with upgrade GUID preservation to ensure clean in-place replacement while preserving user database files under `%LOCALAPPDATA%\Vimivo`.

### 3.5 Linux Distribution
- **System Package Repositories**: Debian/Ubuntu (`.deb` via APT repository), Fedora/RHEL (`.rpm` via DNF repository). Updates are managed natively by the OS package manager.
- **Flatpak**: Distributed via Flathub remote with sandbox isolation.
- **AppImage**: Standalone executable with user-level execution; verified update downloads replace the user-level AppImage atomically.
