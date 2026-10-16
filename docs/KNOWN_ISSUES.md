# Vimivo Known Issues & Technical Limitations

This register documents current technical limitations, edge cases, and distribution prerequisites for Vimivo Release Candidate 1 (`1.0.0-rc.1`).

---

## 1. Platform Distribution & Signing Status

### macOS Desktop
- **Issue**: Pre-release DMG builds generated in local or CI environments without an active Apple Developer ID certificate will trigger macOS Gatekeeper: *"Vimivo can't be opened because Apple cannot check it for malicious software."*
- **Workaround / Resolution**: Testers can right-click `Vimivo.app` in `/Applications` and select **Open**, or run:
  ```bash
  xattr -d com.apple.quarantine /Applications/Vimivo.app
  ```
- **Release Blocker Status**: Notarization requires owner Developer ID credentials (`APPLE_ID`, `TEAM_ID`, `APP_SPECIFIC_PASSWORD`). Distribution remains internal candidate until credentials are provided.

### Windows Desktop
- **Issue**: Unsigned NSIS `.exe` and `.msi` installers trigger Windows Defender SmartScreen: *"Windows protected your PC"*.
- **Workaround / Resolution**: Click **More info -> Run anyway**.
- **Release Blocker Status**: Authenticode signing requires an OV/EV Code Signing Certificate or Azure Trusted Signing key.

### Linux Desktop
- **Issue**: Some Wayland compositors (e.g. Sway, Hyprland) may exhibit flickering when using hardware OpenGL sub-windows if XWayland is disabled.
- **Workaround / Resolution**: libmpv utilizes X11 / XWayland embedding as the default fallback for 100% stability.

### iOS / iPadOS
- **Issue**: iOS archive and IPA creation requires active Apple Developer Team provisioning profiles.
- **Workaround / Resolution**: Framework and KMP compilation verified in CI; physical TestFlight distribution awaits team enrollment.

### Android
- **Issue**: Google Play Store policy requires 12 opted-in testers for 14 continuous days for new personal developer accounts before production publishing.
- **Workaround / Resolution**: Standalone signed APK is provided for direct sideloading and internal testing.

---

## 2. Playback & Codec Limitations

- **Apple AVFoundation Containers**: Native iOS AVPlayer does not decode `.mkv` or `.avi` files natively without remuxing.
- **DRM Content**: Protected media formats (Widevine, FairPlay) are intentionally not supported. Vimivo plays local and user-authorized non-DRM media.
- **DVD Menus**: ISO/VOB DVD interactive menus are not supported; raw VOB video streams play sequentially.
