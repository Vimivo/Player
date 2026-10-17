# Vimivo — Playback Architecture & Lifecycle Guide

This document specifies the end-to-end multimedia playback lifecycle, engine contracts, native adapters, queue coordination, audio DSP, video rendering, and state flows.

---

## 1. End-to-End Playback Data Flow

```text
User Interaction (Tap Song / Click Play)
          │
          ▼
Presentation Layer (Compose Desktop / Jetpack Compose / SwiftUI)
          │ Invokes controller method
          ▼
Playback Queue Controller (UnifiedPlaybackQueue)
          │ Sets active item, computes next/previous
          ▼
PlayerEngine Contract (`dev.vimivo.core.playback.PlayerEngine`)
          │ Dispatches commands
          ▼
Platform-Native Engine Adapter:
  ├── macOS / Windows / Linux: `DesktopMpvPlayerEngine` (libmpv JNA client)
  ├── Android: `Media3PlayerEngine` (ExoPlayer foreground service)
  └── iOS: `AVFoundationPlayerEngine` (AVPlayer / AVQueuePlayer)
          │
          ▼
Native Hardware Decoders & Audio/Video Sinks
          │ Emits native callbacks (position, buffering, completion, state)
          ▼
Reactive State Flows (`StateFlow<PlaybackState>`)
          │
          ├── Updated in UI (Seekbar, Now Playing Card, Mini Player)
          ├── History Tracker (records 1 playcount when elapsed > 30s or completed)
          └── Resume Checkpoints (persisted to SQLite every 10 seconds)
```

---

## 2. The `PlayerEngine` Interface Contract

Every platform engine implements `dev.vimivo.core.playback.PlayerEngine`:
- `load(uri: String, initialPositionMillis: Long = 0, autoplay: Boolean = true): Result<Unit>`
- `play(): Result<Unit>`
- `pause(): Result<Unit>`
- `seekTo(positionMillis: Long): Result<Unit>`
- `stop(): Result<Unit>`
- `setVolume(volume: Float): Result<Unit>` (0.0 to 1.0)
- `setSpeed(speed: Float): Result<Unit>` (0.5x to 2.0x, with pitch preservation)
- `close()`: Releases native engine instances, decoders, render surfaces, and memory.
- `state: StateFlow<PlaybackState>`: Exposes current state (`IDLE`, `BUFFERING`, `PLAYING`, `PAUSED`, `COMPLETED`, `ERROR`).
- `capabilities: PlayerCapabilities`: Reports supported capabilities (`canSeek`, `canChangeSpeed`, `supportsEqualizer`, `supportsVideo`, etc.).

---

## 3. Native Engine Implementations

### Desktop (`DesktopMpvPlayerEngine`)
- Uses `libmpv`'s C API via JNA.
- Sets client properties:
  - `audio-pitch-correction=yes` (pitch-invariant speed scaling).
  - `gapless-audio=yes` (smooth track transitions).
  - `replaygain=track` or `replaygain=album` with peak limiting.
  - 10-band equalizer via `af` filter expressions (`equalizer=f=...:g=...`).
- Video output configured dynamically:
  - macOS: Uses `MpvCocoaRenderer.mm` (OpenGL context attached to `SwingPanel`).
  - Windows: Uses `MpvWin32Renderer` (`wid` attached to Win32 Canvas HWND).
  - Linux: Uses `MpvX11Renderer` (`wid` attached to X11 Canvas XID).

### Android (`VimivoMediaService` & `Media3PlayerEngine`)
- Runs as an Android Foreground Service using AndroidX Media3 `MediaSessionService`.
- MediaSession integrates with notification shade, lock screen, and Bluetooth AVRCP.
- Audio focus is managed automatically using `AudioAttributes.Builder()` with `USAGE_MEDIA`.

### iOS (`AVPlayer` Adapter)
- Wraps `AVPlayer` and `AVPlayerItem`.
- Sets `AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)`.
- Updates `MPNowPlayingInfoCenter.default().nowPlayingInfo` on position/track changes.

---

## 4. Track Advancement & Queue Safety

1. **Natural Track Completion**:
   - The native engine emits a `COMPLETED` event.
   - `UnifiedPlaybackQueue` catches the event and checks `repeatMode`:
     - `ONE`: Re-seeks current track to 0 and re-plays.
     - `ALL` or `OFF`: Advances to `nextItem()`. If at end of queue in `OFF` mode, transitions to `PAUSED` at position 0.
2. **Manual Skipping (Next / Previous)**:
   - Atomic state transitions prevent rapid-skipping race conditions.
   - Unavailable or corrupted tracks are cleanly skipped with user-friendly warnings rather than aborting the entire queue.
3. **Queue Restorations**:
   - On application startup, the saved queue is restored from SQLite `playback_queue_items`.
   - The engine loads the last active media item and position but **remains paused** until the user explicitly hits Play.

---

## 5. Audio DSP & Video Adjustments

- **10-Band Graphic Equalizer**: Frequencies: 31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz. Range: -12 dB to +12 dB.
  - **12 Presets**: `Flat`, `Bass Boost`, `Treble Boost`, `Vocal`, `Classical`, `Jazz`, `Rock`, `Electronic`, `Podcast`, `Acoustic`, `Custom`.
  - **Custom Presets**: Full custom curve adjustment with named persistence, safe defaults, and reset capabilities.
  - **Auto-Attenuating Preamp Headroom**: Prevents digital clipping by automatically offsetting the maximum positive band boost down to -24 dB.
- **Hardware Audio Effects**:
  - **Bass Boost**: 0% to 100% strength enhancement.
  - **Treble Adjustment**: -12 dB to +12 dB boost/cut.
  - **Stereo Balance**: -1.0 (100% Left) to +1.0 (100% Right) with centered neutral default (0.0).
  - **ReplayGain**: Supports `Track` and `Album` modes with peak limiting and clipping protection.
  - **Audio Output Routing**: Dynamic device enumeration and selection (`getAudioOutputDevices()`, `selectAudioOutputDevice()`).
- **Video Adjustments & Subtitles**:
  - **Multi-Track Audio Selection**: Dynamic inspection and switching between audio renditions (language, codec, channels) without player restart.
  - **Aspect Ratio & Display Modes**: `Fit`, `Fill`, `Original`, `16:9`, `4:3`, `21:9` with 90-degree step rotation.
  - **Real-Time GPU Adjustments**: Dynamic hardware adjustments for `brightness`, `contrast`, `saturation`, and `gamma`.
  - **External Subtitles**: Real-time delay adjustment (±5000ms in ±100ms or ±250ms steps) supporting `.srt`, `.vtt`, `.ass`, and `.ssa` with UTF-8 Bengali/Unicode text rendering.
  - **Subtitle Styling**: Dynamic font size (18sp–36sp), text color (#FFFFFF, #FFFF00, #00FFFF), and safe margins.

---

## 6. Troubleshooting Playback Issues

| Symptom | Cause | Resolution |
|---|---|---|
| No video output on Desktop | Missing GPU driver or incompatible OpenGL context | Verify graphics drivers support OpenGL 3.3+; check `libmpv` log output. |
| Speed change sounds high-pitched | Pitch preservation disabled | Ensure `audio-pitch-correction=yes` is set on the active engine. |
| Android notification controls unresponsive | Background service killed by aggressive battery manager | Ensure `POST_NOTIFICATIONS` is granted and app is exempted from battery optimization. |
| iOS audio pauses when screen locks | Background audio capability missing | Verify `UIBackgroundModes: ["audio"]` is present in `Info.plist`. |
