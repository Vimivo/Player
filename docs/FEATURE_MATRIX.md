# Cross-platform feature matrix

Verified against source, automated test suites, and simulator runs on 2026-10-15. “Verified” below indicates automated verification and/or simulator verification unless physical runtime is explicitly stated.

| Feature | Android | macOS | Windows | Linux | iOS / iPadOS |
|---|---|---|---|---|---|
| Local audio playback | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Embedded local video | Implemented, tested | Implemented, tested | Implemented (Win32 HWND) | Implemented (X11 XID) | Implemented, AVKit/PiP tested |
| Persistent media library | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Metadata extraction | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Artwork and thumbnails | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Playlists and favorites | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| History and resume | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Bookmarks | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Background playback | Implemented, Media3 | Implemented | Partial | Partial (MPRIS) | Implemented, AVAudioSession |
| System media controls | Implemented, MediaSession | Implemented | Implemented (SMTC) | Implemented (MPRIS) | Implemented, MPRemoteCommandCenter |
| Picture-in-Picture | Implemented, tested | Implemented | Not implemented | Not implemented | Implemented, AVKit tested |
| Secure credentials | Implemented, Keystore | Implemented, Keychain | Implemented, CredMgr | Implemented, SecretService | Implemented, Keychain tested |
| Direct network URLs | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Accessibility (WCAG AA) | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Settings & Theme support | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Diagnostic log redaction | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Native distribution | Implemented (APK/AAB) | Implemented (DMG/App) | Configured (MSI/EXE) | Configured (DEB/RPM/AppImage) | Implemented (Xcode Archive) |

### Milestone 23 & 24 Highlights:
- **Design System & Theme Selection**: Unified theme token system across Android, iOS, and Desktop supporting Light, Dark, AMOLED, and System Default themes. Real settings panel on Desktop.
- **Accessibility & WCAG 2.2 AA**: Comprehensive `contentDescription`, `stateDescription`, and SwiftUI `.accessibilityLabel` / `.accessibilityValue` across all playback controls, sliders, navigation tabs, and queue rows.
- **Privacy & Security**: Built `SafeLogger` redacting user filesystem paths (`/Users/...`, `/home/...`, `C:\Users\...`) and URL query authentication tokens. Zero analytics, zero ad SDKs, zero user data loss.
