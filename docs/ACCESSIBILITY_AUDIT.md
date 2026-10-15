# Vimivo Accessibility Audit & Conformance

Audit date: 2026-10-15.
Reference standards: **WCAG 2.2 AA**, Android Accessibility Guidelines, and Apple Accessibility Guidelines.

---

## 1. Executive Summary

Vimivo provides comprehensive accessible controls across mobile and desktop platforms. Every interactive element—including playback buttons, sliders, menus, and navigation tabs—exposes clear accessible names, roles, values, and states.

---

## 2. Platform-Specific Semantics

### Android (Jetpack Compose)
- **Seekbars & Sliders**:
  - `contentDescription = "Playback seekbar"`
  - `stateDescription = "${formatTime(position)} of ${formatTime(duration)}"`
  - Support for volume/hardware keys and TalkBack slider scrubbing.
- **Playback Controls**:
  - `Play/Pause`: Dynamic label switching ("Play" / "Pause") based on playback status.
  - `Shuffle`: Explicit state announcement ("Shuffle on" / "Shuffle off").
  - `Repeat`: Dynamic mode announcement ("Repeat One", "Repeat All", "Repeat Off").
  - `Favorite`: Descriptive actions ("Favorite [Title]" / "Remove favorite").
- **Touch Targets**:
  - Minimum touch target size adheres to 48x48dp (with user setting for larger touch targets up to 64dp).
- **Reduced Motion**:
  - `LocalReducedMotion` respects system animation settings; animated progress bars snap immediately without transitions when animations are disabled.

### iOS & iPadOS (SwiftUI / AVKit)
- **VoiceOver & Accessibility Inspector**:
  - `NowPlayingBar`: Explicit `.accessibilityLabel("Pause")` / `.accessibilityLabel("Play")` and `.accessibilityLabel("Next track")`.
  - `NowPlayingView`:
    - Slider: `.accessibilityLabel("Playback progress")` with formatted `.accessibilityValue("X:XX of Y:YY")`.
    - Buttons: Explicit labels for Shuffle, Repeat, Previous, Play/Pause, Next, Speed menu, and Queue sheet.
    - Context Menus: Full VoiceOver traversal for "Play Now", "Play Next", "Add to Queue", "Favorite", and "Remove".
- **Dynamic Type**: Text elements scale proportionally with system font sizes without text clipping or truncated critical controls.
- **AVPlayerViewController**: Full native VoiceOver and subtitles/caption routing built into AVKit.

### Desktop (Compose Multiplatform / macOS, Windows, Linux)
- **Keyboard Navigation & Shortcuts**:
  - MenuBar shortcuts: `Spacebar` (Play/Pause), `Ctrl+O` / `Cmd+O` (Open File), `Ctrl+Shift+O` / `Cmd+Shift+O` (Add Folder), `Left`/`Right` arrow keys (Seek backward/forward by 10s).
  - Tab order traverses sidebar destinations, open actions, now playing card, seekbar, and queue items logically.
- **Semantics**:
  - Now Playing Card has `mergeDescendants = true` with accessible title.
  - Slider announces elapsed and total duration.
  - Sidebar buttons announce active/selected destination state.

---

## 3. Accessibility Test Matrix

| Checkpoint | Android | iOS | macOS Desktop | Windows Desktop | Linux Desktop |
|---|---|---|---|---|---|
| Main navigation accessible | Verified | Verified | Verified | Verified | Verified |
| Play/Pause state accessible | Verified | Verified | Verified | Verified | Verified |
| Next/Previous accessible | Verified | Verified | Verified | Verified | Verified |
| Seekbar slider accessible | Verified | Verified | Verified | Verified | Verified |
| Shuffle/Repeat states | Verified | Verified | Verified | Verified | Verified |
| Queue actions accessible | Verified | Verified | Verified | Verified | Verified |
| Playlist management accessible | Verified | Verified | Verified | Verified | Verified |
| Search accessible | Verified | Verified | Verified | Verified | Verified |
| Settings screen accessible | Verified | Verified | Verified | Verified | Verified |
| Dialogs accessible | Verified | Verified | Verified | Verified | Verified |
| Contrast meets WCAG AA (4.5:1) | Verified | Verified | Verified | Verified | Verified |
| Dynamic text / Font scaling | Verified | Verified | Verified | Verified | Verified |
| Reduced motion respected | Verified | Verified | Verified | Verified | Verified |
