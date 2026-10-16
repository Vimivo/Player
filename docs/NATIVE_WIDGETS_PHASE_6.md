# Native Widgets and Now Playing Phase 6

Date: 2026-10-16

## Authoritative-session audit

- Android owns playback in one `MediaLibraryService`/`MediaLibrarySession`; application UI, notification, system
  controls, and the new widget connect to that session rather than constructing another engine.
- iOS owns playback in the existing `AVPlayerPlaybackEngine`. `MPNowPlayingInfoCenter` and
  `MPRemoteCommandCenter` already delegate to it; queue index/count, opaque content identity, media kind, and live
  stream state are now published in addition to title, artist, album, artwork, duration, elapsed time, and rate.
- Linux uses the existing MPRIS service connected to the desktop engine and queue. Windows SMTC and macOS
  MediaPlayer command-center integration are not implemented and are not represented as working.

## Android widget

- Added one resizable RemoteViews playback widget with compact-to-large sizing, Bengali resources, track/artist
  text, Previous, Play/Pause, and Next.
- Every action builds a short-lived Media3 `MediaController` from the existing service `SessionToken`, sends the
  command to that session, and releases the controller. It never owns a player or exposes a source URL.
- The widget declares no periodic polling and no overlay permission. System notification and lock-screen controls
  remain provided by Media3.

## Apple platform boundaries

- Lock Screen, Control Center, accessories, and system-controlled compatible iPhone Now Playing presentation use
  Apple's official Now Playing APIs. Vimivo does not draw a Dynamic Island overlay and does not create a redundant
  Live Activity.
- The Xcode project currently has application and test targets only; it has no WidgetKit extension or shared App
  Group. Adding nonfunctional widget source without a signed extension target would be misleading, so WidgetKit
  widgets remain blocked pending project provisioning.
- Local Xcode/Swift verification is also blocked because the installed Xcode license has not been accepted. No iOS
  build, simulator run, device run, WidgetKit refresh, VoiceOver, Lock Screen, Control Center, or Dynamic Island
  result is claimed.

## Verification scope

- Android compilation, JVM tests, APK assembly, Detekt, shared tests, and desktop tests are required for this
  checkpoint. Widget interaction still requires a launcher/device and is not claimed from host-side compilation.
- Apple changes received source review only because `xcodebuild` and `swiftc` both terminate with Xcode license
  status 69 on this machine.

