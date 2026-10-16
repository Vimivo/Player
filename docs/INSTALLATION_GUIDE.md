# Vimivo Official Installation & Upgrade Guide

## 1. Overview & Verification

Vimivo delivers self-contained, standalone binaries for desktop and native packages for mobile.
Before installing, verify that your downloaded package matches the official cryptographic hash listed in `docs/SHA256SUMS.txt`.

---

## 2. Platform Installation Procedures

### 2.1 Android (APK & App Bundle)

#### Sideloading Direct APK
1. Download `androidApp-release.apk` from the official release page.
2. In your device Settings, ensure **Install Unknown Apps** is granted for your browser or file manager.
3. Open the downloaded APK and tap **Install**.
4. Launch Vimivo and grant storage permissions when prompted.

#### Google Play Store
1. Open the official Google Play Store listing.
2. Tap **Install**; updates will be delivered automatically through Google Play.

---

### 2.2 macOS Desktop (`.dmg`)

1. Download `Vimivo-1.0.0.dmg`.
2. Double-click the `.dmg` file to mount the disk image.
3. In the Finder window that opens, drag the **Vimivo** application icon into the **Applications** folder shortcut.
4. Eject the disk image.
5. Launch Vimivo from **Applications** or via Spotlight (`Cmd + Space` -> Vimivo).

> [!NOTE]
> **Pre-release Gatekeeper Notice**: On pre-release candidate builds awaiting owner Apple Developer ID notarization, macOS will display an alert: *"Vimivo can't be opened because Apple cannot check it for malicious software"*.
> - To open: Right-click `Vimivo.app` in `/Applications` and select **Open**, then click **Open** in the confirmation dialog.
> - Or in Terminal: `xattr -d com.apple.quarantine /Applications/Vimivo.app`.

---

### 2.3 Windows Desktop (`.exe` / `.msi`)

#### NSIS Installer (`Vimivo-Setup-1.0.0.exe`)
1. Download `Vimivo-Setup-1.0.0.exe`.
2. Double-click the installer to launch the Setup Wizard.
3. Choose your install location (defaults to `%LOCALAPPDATA%\Programs\Vimivo`).
4. Complete the wizard to create Start Menu and Desktop shortcuts.

#### Windows Installer (`Vimivo-1.0.0.msi`)
1. Double-click `Vimivo-1.0.0.msi` for automated per-user or administrative enterprise deployment.

> [!NOTE]
> **Windows SmartScreen Notice**: For pre-release builds without an established Authenticode reputation, Windows Defender SmartScreen will display *"Windows protected your PC"*.
> - Click **More info** -> **Run anyway**.

---

### 2.4 Linux Desktop (DEB, RPM, AppImage)

#### Debian / Ubuntu (`.deb`)
```bash
sudo dpkg -i vimivo_1.0.0_amd64.deb
sudo apt-get install -f # Fixes any missing runtime dependencies (libmpv2, libsecret)
```

#### Fedora / RHEL (`.rpm`)
```bash
sudo rpm -ivh vimivo-1.0.0.x86_64.rpm
# Or with dnf:
sudo dnf install ./vimivo-1.0.0.x86_64.rpm
```

#### Universal AppImage (`.AppImage`)
```bash
chmod +x Vimivo-1.0.0.AppImage
./Vimivo-1.0.0.AppImage
```

---

## 3. Safe Upgrades & Data Preservation

1. **In-Place Upgrades**: Installing a newer version over an older version automatically preserves your SQLite database (`vimivo.db`), playlists, favorites, play history, bookmarks, and settings.
2. **Database Migration Guarantee**: When launched, the newer build automatically runs any necessary forward migrations (e.g. v0/v1/v2/v3 -> v4) inside atomic SQLite transactions.
3. **No Media Modification**: Vimivo never touches or moves your original media files during installation, upgrade, or uninstallation.
