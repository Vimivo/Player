# Mobile player UX (Android and iOS)

The music and video players follow one layout on both phones, in portrait and landscape. This page says what exists,
how it was checked, and what is still missing.

## Music player

| Part | Android (`NowPlayingScreen`) | iOS (`NowPlayingView`) |
|---|---|---|
| Artwork, title, artist, favourite | yes | yes |
| Format badge | codec, bitrate and sample rate from the engine | file extension |
| Seek bar | bars you tap or drag (`WaveformSeekBar`); VoiceOver/TalkBack see a slider | same (`WaveformSeekBar`), adjustable action |
| Transport | shuffle, previous, play/pause in a ring, next, repeat (one/all) | same |
| Queue | shortcut with a count | shortcut with a count |
| Equalizer | panel over the player: on/off, 12 presets, ten vertical band sliders, preamp, bass, treble | sheet: same controls |
| Lyrics | synced LRC view with fading lines, load a file | not built (no lyrics support on iOS yet) |
| Sleep timer | 15/30/45/60 min, stop after track | not built on iOS |
| Landscape | artwork left, controls centre, shortcut list right | artwork left, controls right |

The bars of the seek bar are a **styled scrubber**: a smooth shape seeded by the track, not the audio's real amplitude.
With "audio visualizer" off in Android settings the bars are flat.

## Video player

Both platforms: a ring around the play button, a "Swipe up for settings" handle (tap or swipe up) that opens
*Playback settings* (speed, audio track, subtitles, loop, next up and the existing detailed sections), and a *Next up*
panel with a thumbnail per queued video. Existing controls are unchanged: gestures, lock, rotation lock, speed and
aspect pills, picture-in-picture where the OS has it.

## Evidence

- Android (emulator, API 36): screenshots of the music player in portrait and landscape, the equalizer panel over the
  player, the video controls, the settings panel and Next up; `AppNavigationTest` and the rest of the UI package pass.
- iOS (simulator): `NowPlayingSnapshotTests` renders portrait, landscape and the equalizer controls with a real engine
  and track and checks the seek-bar shape; the full suite passes (70 tests). SwiftUI's offscreen renderer shows system
  toggles, sliders and menus as placeholders, so those were not judged from the images.
- Not tested: a phone, VoiceOver/TalkBack walkthroughs, and the equalizer's sound on a device.

## Known gaps

- iOS: lyrics, sleep timer, and favourite/queue changes are not shared logic (they live in Swift).
- Both: the video "Next up" rows show no duration (the queue items do not carry one).
- Android colour controls for video are not available (see `VLC_FEATURE_GAP.md`, row 14).
