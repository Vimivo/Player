# Vimivo Closed Beta Tester Guide

Welcome to the Vimivo Closed Beta testing cohort!
Thank you for helping us ensure Vimivo delivers an uncompromising, privacy-respecting multimedia playback experience across mobile and desktop platforms.

---

## 1. About Vimivo

**Tagline**: *Every Sound. Every Frame.*
**Mission**: A universal, free, ad-free, login-free, privacy-first audio and video player.
- **No Accounts**: You will never be asked to sign up, log in, or provide personal details.
- **No Analytics / Telemetry**: Vimivo does not transmit your library contents, listening habits, or device telemetry to any remote server.
- **Local Media First**: Vimivo plays the media you own on your device storage, external SSDs, or authenticated private network shares.

> [!IMPORTANT]
> **Data Safety Guarantee**: Vimivo operates in a strictly read-only mode regarding your original audio and video files. It never edits tags, converts audio, overwrites, or deletes your original media files.

---

## 2. Beta Scope & Current Limitations

As a beta participant, you are testing pre-release software. While tested extensively in internal QA, edge cases may exist.

### Known Beta Limitations
- **macOS Gatekeeper**: Test builds distributed outside the Mac App Store prior to Apple Developer ID notarization require right-clicking the app and selecting **Open** on macOS Sequoia / Sonoma, or running `xattr -d com.apple.quarantine /Applications/Vimivo.app`.
- **Windows SmartScreen**: Unsigned candidate test binaries will display a standard Windows SmartScreen "Unknown Publisher" prompt. Click **More info -> Run anyway**.
- **Linux Wayland**: Hardware video rendering uses X11 / XWayland fallback depending on your GPU driver and compositor configuration.
- **iOS / iPadOS**: Currently distributed via internal ad-hoc builds or awaiting owner TestFlight team credentials.

---

## 3. Supported Operating Systems & Architectures

| Platform | Minimum Version | Architecture | Packaging Format |
| :--- | :--- | :--- | :--- |
| **Android** | Android 9.0 (API 28) | arm64-v8a, armeabi-v7a, x86_64 | APK, Android App Bundle (AAB) |
| **macOS** | macOS 12.0 (Monterey) | Apple Silicon (arm64), Intel (x86_64) | DMG, `.app` bundle |
| **Windows** | Windows 10 (Build 19041+), Windows 11 | x64 (64-bit Intel/AMD) | NSIS Installer `.exe`, MSI package |
| **Linux** | Ubuntu 22.04+, Debian 12+, Fedora 38+ | x86_64, aarch64 | `.deb`, `.rpm`, `.AppImage` |

---

## 4. Download Verification & Integrity

Before installing, verify the integrity of the downloaded file using SHA-256:

### macOS / Linux
```bash
shasum -a 256 <downloaded-file>
```

### Windows (PowerShell)
```powershell
Get-FileHash -Algorithm SHA256 <downloaded-file>
```

Compare the output hash against the official hashes published in `docs/SHA256SUMS.txt`.

---

## 5. Safe Installation & Setup

### Android
1. Download the `androidApp-release.apk` artifact.
2. Enable "Install unknown apps" for your browser/file manager if prompted.
3. Open the APK and tap **Install**.
4. Grant the requested **Audio** and **Video** media permissions when prompted on first launch so Vimivo can index your local library.

### macOS
1. Open the `Vimivo-1.0.0.dmg` disk image.
2. Drag the **Vimivo** icon into your **Applications** folder.
3. Eject the disk image.
4. Launch Vimivo from Applications. (If prompted by Gatekeeper, right-click -> Open).

### Windows
1. Run `Vimivo-Setup-1.0.0.exe`.
2. Follow the standard installation wizard (installs to `%LOCALAPPDATA%\Programs\Vimivo`).
3. Launch Vimivo from the Start Menu or Desktop shortcut.

### Linux
- **Debian / Ubuntu**:
  ```bash
  sudo dpkg -i vimivo_1.0.0_amd64.deb
  sudo apt-get install -f
  ```
- **Fedora / RHEL**:
  ```bash
  sudo rpm -i vimivo-1.0.0.x86_64.rpm
  ```
- **AppImage**:
  ```bash
  chmod +x Vimivo-1.0.0.AppImage
  ./Vimivo-1.0.0.AppImage
  ```

---

## 6. How to Test Core Features

### Adding Media & Library Browsing
- Click **Add Folder** or the **+** button in the Library view to select music or video directories.
- Ensure album artwork, artist names, track numbers, and durations load accurately.
- Test quick search across artists, albums, and video titles.

### Audio Playback & Persistent Queue
- Play individual tracks or click **Play All** on an album or playlist.
- Test playback controls: Play, Pause, Seek bar, Next, Previous, Shuffle, and Repeat mode.
- Close the application during playback. Re-open to verify your playback queue and resume position are preserved.

### Video Playback & Rendering
- Open various video formats (`.mp4`, `.mkv`, `.webm`, `.mov`).
- Verify smooth rendering, hardware acceleration, and audio synchronization.
- Test window resizing, full-screen mode toggle (`F` or double-click), and subtitle selection (`.srt`, `.vtt`, `.ass`).

### Playlists & Favorites
- Create custom playlists, re-order tracks via drag-and-drop, add/remove songs.
- Toggle favorite status (heart icon) and verify the Favorites tab updates immediately.

---

## 7. How to Export Privacy-Safe Diagnostics

If you encounter an issue, do not copy raw console output or system logs that might contain private filenames or username paths. Instead:

1. In Vimivo, navigate to **Settings -> Diagnostics & Privacy**.
2. Click **Export Diagnostic Report**.
3. Choose a destination folder (e.g., your Desktop or Downloads).
4. Review the generated `.md` file in any text editor.
   - All user account names (e.g. `/Users/yourname/` or `/home/yourname/`) are automatically replaced with `~/user/`.
   - All URL authorization query tokens are replaced with `[REDACTED]`.
   - No personal media filenames or library database records are included.

---

## 8. Reporting Problems

To report a bug:
1. Visit the repository: [https://github.com/Vimivo/Player/issues/new/choose](https://github.com/Vimivo/Player/issues/new/choose)
2. Select **Bug Report**.
3. Fill out the structured fields (Affected Platform, OS version, App Version, Steps to Reproduce, Expected vs Actual).
4. Attach your sanitized diagnostic export if relevant.

---

## 9. Updating or Leaving the Beta

### Updating
Simply install newer beta builds directly over your existing installation. Your SQLite database (playlists, favorites, history, queue) will be automatically and non-destructively migrated to the newer schema.

### Leaving the Beta & Cleanup
To uninstall:
- **Android**: Long-press app icon -> Uninstall.
- **macOS**: Move `/Applications/Vimivo.app` to Trash. To delete local cache: remove `~/Library/Application Support/Vimivo`.
- **Windows**: Windows Settings -> Apps -> Installed apps -> Vimivo -> Uninstall.
- **Linux**: `sudo apt remove vimivo` or `sudo rpm -e vimivo`.
*(Note: Uninstalling Vimivo will never remove your original music or video files).*
