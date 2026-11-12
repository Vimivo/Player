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

## Added 2026-11-12

- **iOS: no Wi-Fi file receive.** The previous sheet was non-functional and was removed. Use the Files app, AirDrop or
  "Open in Vimivo". Android and desktop have a real, token-protected receive server.
- **Desktop: no casting, no transcode.** Both were non-functional and were removed from the menu and command palette.
  Casting needs real SSDP/DLNA discovery and a local media server; transcode needs an LGPL transcoder in the media
  package (ADR-004).
- **No online subtitle search.** Removed by decision. Local files, auto-load and embedded tracks work.
- **Android private vault:** no biometric unlock, and it stays unlocked while the app is in the background until the
  library screen is recreated. The PIN is four digits with a 5-attempt / 30 s limiter, which stops casual guessing but
  is not strong protection against someone who can read the device's app data.
- **iOS private vault:** PIN only (no Face ID); Settings "Change PIN" re-asks the current PIN but cannot yet set a new one.
- **Android Auto / CarPlay:** Android exposes a browse tree but is not registered for Android Auto; CarPlay needs an
  Apple entitlement the project does not have.
- **Android Chromecast:** not implemented (no Cast SDK); iOS AirPlay uses the system route picker.
- **Desktop capture (webcam / screen):** URL forms fixed and unit-tested only; not run against real devices. Windows needs the
  DirectShow device name typed in. Wayland screen capture is not supported.
- **Local development builds** link Homebrew's GPL libmpv. Distributable builds must set `VIMIVO_MPV_PREFIX`; packaging
  tasks now fail without it.

## Added 2026-11-12 (second round)

- **Casting is DLNA / UPnP only** (desktop and Android). Chromecast needs Google's SDK and AirPlay is a closed protocol;
  neither is built. Casting has only been tested against a simulated renderer on loopback, not a real TV. Android stops the
  cast when you leave the video player.
- **Video rotation** turns the picture in 90° steps without turning the phone (all platforms). On Android it rotates the
  video view, so a libmpv/SurfaceView pipeline that ignores view rotation would need an engine-side rotation. Check on a device.
- **Listen as audio** switches the video track off while the sound keeps playing (Android, iOS; desktop has audio-only).
  After it, opening the video again restores the picture.
- **Sleep timer** has custom hours/minutes on all platforms and is reachable from the video player on Android and iOS.
- **Desktop video quality** (scaler, deband, smooth motion, HDR tone mapping, exclusive audio) is applied for the current
  session only; the choices are not saved yet.
- **Biometric vault unlock**: Android 10+ (platform prompt) and iOS (Face ID / Touch ID). Not tried on a device.
- **Localization is much narrower than documented.** Android has 96 strings in English and Bangla, but about 400 UI
  strings are written directly in the code in English. iOS has no localization files at all. "EN + বাংলা" is true only for
  the Android strings that use resources.
- **Still missing:** SMB / SFTP / WebDAV sources (the LGPL libmpv has no `smb://` or `sftp://`; a client library and local
  proxy are needed), iOS MKV/AVI (needs libmpv for iOS), poster and metadata library, Android TV / tvOS, Dolby Vision profile
  handling, crossfade, Android colour Plan B.

## Added 2026-11-12 (third round: network shares, convert, desktop)

- **Network shares (SMB, SFTP, WebDAV)** are read by pure-Java clients (smbj, sshj) and handed to the player through a
  private `127.0.0.1` link, so the LGPL libmpv needs no `smb://` or `sftp://` support. Desktop has all three with a
  folder browser. Android has SMB and WebDAV(https); Android SFTP is off until a first-use server-key confirmation
  screen exists. **iOS has none of them.** Nothing was run against a real server. Digest authentication for WebDAV is not
  supported (Basic only). On Android, smbj and sshj bring BouncyCastle, which may clash with the platform copy at runtime:
  check on a device before relying on it.
- **Convert Media (desktop)** uses libmpv's encoder path: AAC, FLAC, WAV, MP3 audio and MKV/MP4 stream-copy. The LGPL
  media package has no x264/x265, so there is no video re-encode, and MP3 only works if the bundled FFmpeg includes the
  LAME encoder (the dialog shows libmpv's own error if not). Not run.
- **Desktop preferences** (toolbar buttons, video quality) are saved in `preferences.properties` in the app data folder.
  The hotkey preset is still not saved.
- **Desktop memory:** the packaged app now starts with the serial collector, a shrinking heap (768 MB cap), and capped
  metaspace and code cache. Not measured; check real memory use on each OS.
- **iOS MKV/AVI is not done.** It needs LGPL libmpv/FFmpeg frameworks built for iOS from `scripts/build_media`
  (ADR-004, open item) and a second playback surface in the SwiftUI player. That cannot be produced without building and
  trying it on a device, so it was not attempted blind.

