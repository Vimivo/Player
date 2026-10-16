# Vimivo — Installation & System Requirements

This document outlines the official installation procedures and hardware/OS system requirements for Vimivo across all five supported platforms.

---

## 1. System Requirements

| Platform | Minimum OS Version | Recommended OS Version | Supported Architectures | Minimum RAM | Disk Space |
|---|---|---|---|---|---|
| **Android** | Android 8.0 (Oreo, API 26) | Android 12+ (API 31+) | ARM64 (`arm64-v8a`), ARMv7 (`armeabi-v7a`), x86_64 | 2 GB | 200 MB |
| **iOS / iPadOS** | iOS 16.0 / iPadOS 16.0 | iOS 17+ / iPadOS 17+ | ARM64 (iPhone, iPad) | 3 GB | 150 MB |
| **macOS** | macOS 12 (Monterey) | macOS 14 (Sonoma) / 15 (Sequoia) | Apple Silicon (`arm64`), Intel (`x86_64`) | 4 GB | 500 MB |
| **Windows** | Windows 10 (64-bit, 1809+) | Windows 11 (64-bit) | x86_64 (AMD64) | 4 GB | 500 MB |
| **Linux** | GLIBC 2.31+ (Ubuntu 20.04+, Fedora 36+) | Ubuntu 22.04+ / Fedora 39+ | x86_64 (AMD64) | 4 GB | 500 MB |

---

## 2. Platform Installation Instructions

### Android
- **Direct APK Installation**: Download `androidApp-release.apk`, open in file manager, confirm installation prompt, and launch.
- **Google Play Store**: Install directly from the official store listing with automatic background updates.

### iOS & iPadOS
- **TestFlight / App Store**: Install via official TestFlight invitation or App Store distribution. Requires iOS 16.0 or later.

### macOS Desktop
1. Download `Vimivo-1.0.0.dmg`.
2. Double-click the `.dmg` to mount the disk image.
3. Drag the **Vimivo** app icon into the **Applications** folder shortcut.
4. Eject the disk image and launch Vimivo from `/Applications` or Spotlight.

### Windows Desktop
1. Download `Vimivo-1.0.0.msi` or `Vimivo-Setup-1.0.0.exe`.
2. Run the installer to perform a clean per-user install to `%LOCALAPPDATA%\Programs\Vimivo`.
3. Shortcuts are automatically placed in the Start Menu and Desktop.

### Linux Desktop
- **Debian / Ubuntu**: `sudo dpkg -i vimivo_1.0.0-1_amd64.deb`
- **Fedora / RHEL**: `sudo rpm -ivh vimivo-1.0.0-1.x86_64.rpm`
- **AppImage**: `chmod +x Vimivo-1.0.0-x86_64.AppImage && ./Vimivo-1.0.0-x86_64.AppImage`

For detailed enterprise deployment, silent MSI flags, and checksum verification, see [Installation Guide](INSTALLATION_GUIDE.md).
