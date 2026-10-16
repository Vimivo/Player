# Cinematic Video Player Phase 4

Date: 2026-10-16

## Implemented and audited

- Android keeps a single stable `PlayerView` outside the animated overlay subtree, preventing renderer recreation
  during ordinary control fades.
- Near-black canvas, reduced-motion-aware overlay fade, lightweight translucent top and bottom control surfaces,
  buffered timeline, accurate seek slider, readable metadata, and real play/pause/stop/previous/next actions.
- Tap toggles controls; double-tap seeks by the configured interval; horizontal drag provides a seek preview; pinch
  zoom remains bounded from 1x to 3x.
- Vertical gesture volume adjustment is confined to the right half. The former left-side application brightness
  mutation was removed so the player cannot accidentally change global/display brightness.
- Picture-in-Picture is only shown when the Android device reports the platform feature.
- Audio/subtitle track controls, external local subtitles, subtitle/audio delay, subtitle appearance, playback speed,
  aspect ratio, A-B repeat, decoder information, and gesture sensitivity remain capability- or engine-gated.
- No fake quality selector or unsupported screenshot control was introduced on Android.
- Desktop retains native libmpv subtitle, chapter, A-B repeat, aspect, rotation, zoom, screenshot, diagnostics, and
  keyboard seeking paths. A native View menu now exposes a real full-screen toggle and shortcut.

## Verification scope

- Existing desktop tests cover renderer attachment, source switching, advanced playback controls, queue races,
  failure injection, and native resource cleanup contracts.
- Android compilation, unit tests, Detekt, and APK assembly are required before checkpoint completion.
- Actual Android touch, subtitles, PiP, and visible-frame testing requires a connected supported device and is not
  claimed in this checkpoint.
- macOS visible-frame playback can be inspected with the repository's local `sample.mp4`; that evidence does not
  substitute for Windows, Linux, Android, or iOS native testing.
