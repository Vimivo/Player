# Development continuation

Last verified on 2026-10-15 on macOS, branch `main`.

## Resume point

Implementation is verified through Milestone 19 and 20 foundations (`feat(linux): embedded video renderer, MPRIS media controls, secure credentials, and distribution packaging`).

The latest completed desktop increments are:

1. bounded Java Sound metadata extraction for supported audio files;
2. SQLite schema version 3, including persisted extracted titles and searchable artist/album metadata;
3. real metadata returned to the desktop music/video library UI and playback model;
4. playback history and conservative resume-position persistence for indexed video and long-form audio;
5. reactive desktop playlists, favorites, history, and bookmarks, including persistent mutations and destructive-action confirmations;
6. Jaudiotagger-backed rich audio metadata, extraction-version invalidation, bounded embedded-artwork caching, and artwork UI;
7. a shared unified queue with continuous playlist playback, next/previous, missing-item skipping, shuffle, repeat, and queue editing;
8. SQLite schema version 4 queue persistence, snapshot restoration without autoplay, and queue actions across app startup;
9. licensed generated test fixtures for tagged MP3, FLAC, M4A, Ogg, Opus, WAV, and AIFF verifying parser output, duration, Unicode/Bengali tags, and invalidation;
10. pure-Kotlin Opus metadata parser for Ogg Opus containers and embedded tags without external dependencies;
11. argument-safe video inspection and frame thumbnail generation using isolated tool locators (ffprobe/ffmpeg) with bounded memory/timeout;
12. video frame thumbnail caching with atomic writes, aspect-ratio preservation, and invalidation;
13. album and artist database projections with multi-disc/track ordering, album artist fallbacks, and compilation support;
14. real Compose screens for Albums, Artists, Album Details, and Artist Details with artwork/thumbnail rendering;
15. Play All, Shuffle Play, Play Next, and Add to Queue integration across all collection views (Songs, Albums, Artists, Playlists, Favorites, History);
16. continuous queue integration for multiple Finder open-file selections preserving ordering;
17. automated bundling of 50 native dylibs (libmpv, FFmpeg, etc.) into `Vimivo.app/Contents/Frameworks/` with `@rpath` rewriting and zero Homebrew runtime dependencies;
18. self-contained JVM packaging with required runtime modules and verified installable `Vimivo-1.0.0.dmg`;
19. Windows embedded video renderer (`MpvWin32Renderer`) attaching AWT Canvas Win32 HWND directly to libmpv (`wid`) with `vo = gpu` and capability reporting;
20. Windows secure DLL directory resolution (`MpvNative.findBundledWindowsDllDir()`) with cross-platform JNA library path management;
21. Windows Credential Manager integration (`WindowsCredentialStore`) with Win32 `Advapi32` APIs and safe in-memory fallback;
22. Windows desktop integration including Drag-and-Drop (`DropTarget`), MenuBar keyboard shortcuts (`Ctrl+O`, `Ctrl+Shift+O`, `Space`, `Left`/`Right`), and `%LOCALAPPDATA%\Vimivo` persistence;
23. Windows standalone packaging configuration in `build.gradle.kts` for EXE and MSI formats, native dependency bundling script `bundle_windows_mpv.py`, official 256x256 `.ico` icon, and `.github/workflows/windows.yml` CI pipeline;
24. Linux embedded video renderer (`MpvX11Renderer`) attaching AWT Canvas X11/XWayland Window ID (`XID`) to libmpv (`wid`) with `vo = gpu` and capability reporting (`localVideo = true`, `hdr = true`);
25. Linux dynamic library locator (`MpvNative.findBundledLinuxLibDir()`) resolving bundled and system `libmpv.so*`;
26. Linux MPRIS 2 media player subsystem over Session D-Bus (`org.mpris.MediaPlayer2.vimivo`), tracking playback state, queue, microsecond positions, track object paths, and reactive `PropertiesChanged` notifications with `NoOpMprisService` fallback;
27. Linux Secret Service integration (`LinuxCredentialStore`) binding `libsecret-1.so.0` with `MemoryCredentialStore` fallback;
28. Linux desktop integration including multi-resolution Hicolor icons (16px to 512px), freedesktop `.desktop` launcher, and AppStream metainfo specification;
29. Linux packaging configuration in `build.gradle.kts` for DEB and RPM formats, standalone AppImage packaging script `build_appimage.sh`, Flatpak sandboxed manifest `dev.vimivo.desktop.json`, native ELF bundler `bundle_linux_mpv.py`, and Linux CI workflow in `.github/workflows/linux.yml`;
30. Android 13+ `POST_NOTIFICATIONS` integration, MediaSession, foreground playback service, release APK (`androidApp-release-unsigned.apk`), and release App Bundle (`androidApp-release.aab`) verified;
31. Native iOS and iPadOS application (`iosApp`) with adaptive SwiftUI navigation (`MainTabView` on iPhone, `NavigationSplitView` on iPad), Kotlin Multiplatform shared framework integration (`iosArm64`, `iosSimulatorArm64`), AVFoundation playback engine (`AVPlayerPlaybackEngine`), audio session and interruption management (`AudioSessionController`), Lock Screen and Control Center integration (`MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`), Picture-in-Picture (`AVKit`), sandboxed Files importing with security-scoped bookmarks (`SecurityScopedMediaManager`), native SQLite persistence in WAL mode (`IOSDatabase`), Keychain credential storage (`KeychainCredentialStore`), and verified simulator unit tests (7/7 tests passed on iPhone 17 Pro and iPad Pro).

Do not rebuild these components. Continue from the first executable item in the backlog below.

## Next executable work

1. Physical device testing on real Android and iOS devices (audio output, lock-screen controls, bluetooth audio disconnect).
2. Configure Apple Developer signing certificate and provisioning profile in Xcode for App Store / TestFlight archive distribution.
3. Configure Google Play upload keystore for signing release Android App Bundle (`androidApp-release.aab`).
4. Execute Linux GitHub Actions CI pipeline on an Ubuntu runner to generate genuine DEB, RPM, and AppImage packages.
5. Execute Windows GitHub Actions CI pipeline on a Windows runner to generate genuine EXE/MSI binaries.
6. Run clean-machine Linux and Windows runtime playback verification.

## External or hardware blockers

- Public Google Play store submission requires owner-managed upload keystore. Never commit signing keys.
- iOS physical device installation and TestFlight / App Store submission require owner-managed Apple Developer Team signing certificate and provisioning profile.
- Linux packaging (`jpackage` creating DEB/RPM and AppImage) requires a Linux host/runner.
- Windows packaging (`jpackage` creating EXE/MSI) requires a Windows host/runner.

## Required verification

For mobile and cross-platform verification run:

```shell
# Android
./gradlew :androidApp:testDebugUnitTest
./gradlew :androidApp:detekt
./gradlew :androidApp:assembleRelease
./gradlew :androidApp:bundleRelease

# iOS
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build CODE_SIGNING_ALLOWED=NO
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17 Pro' CODE_SIGNING_ALLOWED=NO
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPad Pro 11-inch (M5)' CODE_SIGNING_ALLOWED=NO

# Desktop & Shared
./gradlew :desktopApp:desktopTest
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :desktopApp:detekt
./gradlew :shared:desktopTest
./gradlew detekt
```
