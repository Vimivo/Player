# Vimivo Video Player Experience

This document describes the video playback UX, overlay controls, fullscreen transitions, subtitle and audio track selection, and hardware rendering controls in Vimivo.

---

## 1. Video Surface & Rendering Pipeline

- **Zero Duplicate Surfaces**: Native video surfaces (macOS OpenGL/Metal via mpv, Android SurfaceView via Media3, iOS AVPlayerLayer) are tied directly to the active `PlayerEngine`. Resizing windows or entering fullscreen re-targets the existing native swapchain without re-initializing decoders.
- **Hardware Acceleration**: Automatic hardware acceleration preference (`AUTO` -> `HARDWARE_PREFERRED` -> single-decision `SOFTWARE` fallback) ensures 4K/60fps and 8K video playback without frame drops.

---

## 2. Video Controls Overlay Architecture

- **Visibility Management**:
  - Overlays appear on pointer movement, touch tap, keyboard interaction, or pause.
  - Overlays automatically hide after 3.5 seconds of inactivity during active playback.
  - **Accessibility Protection**: Overlays NEVER auto-dismiss while an interactive menu (subtitles, audio tracks, chapters, A-B repeat) has user focus.
- **Available Video Controls**:
  - **Aspect Ratio Selector**: Auto, 16:9, 4:3, 21:9 (Ultrawide), 1:1, Fill/Stretch. Radio-button semantics inform screen readers of the selected aspect ratio.
  - **Rotation**: 90-degree step rotation for portrait-recorded or misoriented videos.
  - **Screenshot Capture**: Non-destructive, frame-accurate snapshot saved to user's Pictures folder with visual confirmation toast.
  - **Subtitles Management**:
    - Embedded subtitle track picker.
    - External subtitle loader (`.srt`, `.vtt`, `.ass`, `.sub`).
    - Subtitle delay timing adjustment (±250ms steps with 0ms reset).
  - **Audio Synchronization**: Audio delay stepping (±100ms) to remediate Bluetooth audio latency.
  - **Chapter Navigation**: Step to previous/next chapter with chapter title display.
  - **A-B Loop Repeat**: Set point A, set point B, and toggle continuous segment repetition.
  - **Technical Diagnostics**: Live monitoring card displaying active video codec, audio codec, hardware decoder mode, renderer type, container FPS, dropped frames count, and A/V sync drift in milliseconds.
