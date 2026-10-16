# Streaming Player UX & Controls (Milestone 53)

## 1. Unified Interface Principles

Vimivo provides an identical high-quality experience whether playing a local lossless audio file or a network stream.

### Open Network Stream Entry Points
1. **Application Menu**: `File` > `Open Network Stream…` (`Cmd+U` on macOS, `Ctrl+U` on Windows/Linux).
2. **Dedicated Navigation**: `Network` sidebar destination featuring protocol badges, privacy guarantee, and recent stream history.
3. **Player Bar Stream Input**: Direct inline URL field with automatic validation badge and instant playback.
4. **Android Navigation**: Direct "Network Stream" destination in `VimivoAndroidApp` powered by `NetworkViewModel`.

---

## 2. Desktop Stream Modal Dialog (`DesktopOpenStreamDialog`)

- **Interactive Validation**: As the user types or pastes, the dialog parses the input in real-time.
- **Protocol Badges**:
  - `[HTTP Progressive]`
  - `[HLS]`
  - `[DASH]`
  - `[RTSP]`
  - `[Internet Radio]`
  - `[LIVE]` (in red container) vs. `[VOD]`
- **Clipboard Paste**: One-click paste button accessing clipboard strictly upon user click.
- **Recent Streams**: Instant access to previous streams with quick "Play Now" and "Add to Queue" actions, plus a "Clear" button.
- **Action Buttons**:
  - `Play Now`: Immediately starts playback and registers the sanitized URL in local history.
  - `Add to Queue`: Appends the stream to the queue without interrupting active playback.
  - `Cancel`: Discards input.

---

## 3. Live Stream vs. VOD Semantics

| State Dimension | VOD Stream (Finite Duration) | Live Broadcast (Unbounded Duration) |
| :--- | :--- | :--- |
| **Duration Label** | Formatted time (e.g. `45:20`) | Displays `"Live broadcast"` |
| **Now Playing Badge**| None | High-visibility red `[LIVE]` badge |
| **Seekbar Slider** | Active, draggable | Disabled (`enabled = false`) |
| **Buffering Label** | `"Buffering stream..."` | `"Buffering stream..."` |
| **Status State** | `Playing` / `Paused` / `Completed` | `Playing` / `Paused` (no completion until stopped) |
