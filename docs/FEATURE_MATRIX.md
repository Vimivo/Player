# Cross-platform feature matrix

Verified against source and automated tests on 2026-10-15. “Verified” below means automated verification unless runtime is explicitly stated.

| Feature | Android | macOS | Windows | Linux | iOS / iPadOS |
|---|---|---|---|---|---|
| Local audio playback | Implemented, tested | Implemented, runtime verification pending | Partial | Partial | Implemented, AVPlayer tested |
| Embedded local video | Implemented, tested | Implemented, runtime verification pending | Blocked: renderer | Blocked: renderer | Implemented, AVKit/PiP tested |
| Persistent media library | Implemented, tested | Implemented, tested | Implemented, tested JVM layer | Implemented, tested JVM layer | Implemented, SQLite3 tested |
| Metadata | Implemented, tested | Partial, tested rich audio | Partial, tested JVM audio layer | Partial, tested JVM audio layer | Implemented, AVAsset tested |
| Artwork and thumbnails | Implemented, tested | Partial: embedded audio artwork | Partial: shared JVM cache | Partial: shared JVM cache | Implemented, AVAssetImageGenerator |
| Playlists and favorites | Implemented, tested | Implemented, tested | Implemented, tested JVM layer | Implemented, tested JVM layer | Implemented, tested |
| History and resume | Implemented, tested | Implemented, tested | Implemented, tested JVM layer | Implemented, tested JVM layer | Implemented, tested |
| Bookmarks | Implemented, tested | Implemented, tested | Implemented, tested JVM layer | Implemented, tested JVM layer | Implemented, tested |
| Background playback | Implemented, Media3 service | Implemented | Partial | Partial (MPRIS) | Implemented, AVAudioSession + Info.plist |
| System media controls | Implemented, MediaSession | Implemented | Implemented (SMTC) | Implemented (MPRIS) | Implemented, MPRemoteCommandCenter + MPNowPlaying |
| Picture-in-Picture | Implemented, tested | Implemented | Not implemented | Not implemented | Implemented, AVKit tested |
| Secure credentials | Implemented, Keystore | Implemented, Keychain | Implemented, CredMgr | Implemented, SecretService | Implemented, Keychain tested |
| Direct network URLs | Implemented, tested | Partial | Partial | Partial | Implemented, AVPlayer |
| Network storage browser | Partial | Not implemented | Not implemented | Not implemented | Partial (Files app integration) |
| Native distribution | Implemented (APK/AAB) | Implemented (DMG/App) | Partial (MSI/EXE) | Partial (DEB/AppImage) | Implemented (Xcode project / Archive config) |

### Platform Notes:
- **Android**: Milestone 21 hardened. Android 13+ `POST_NOTIFICATIONS` integrated for media service foreground notifications. Release APK and App Bundle verified. 20/20 unit tests passed. Detekt passed.
- **iOS / iPadOS**: Milestone 22 implemented. Native SwiftUI architecture with `VimivoShared.framework` (Kotlin/Native `iosArm64`, `iosSimulatorArm64`), AVFoundation/AVKit audio/video playback, background playback (`UIBackgroundModes: ["audio"]`), Lock Screen / Control Center integration (`MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`), sandboxed Files access (`SecurityScopedMediaManager`), native SQLite persistence in WAL mode (`IOSDatabase`), and Keychain credential storage (`KeychainCredentialStore`). Unit tests verified on iPhone 17 Pro and iPad Pro simulators.

