# Linux MPRIS 2 media player integration

Vimivo implements the Freedesktop Media Player Remote Interfacing Specification (MPRIS) version 2.2 over the D-Bus Session Bus. This enables native integration with GNOME Shell media controls, KDE Plasma media widgets, desktop media keys, headphone controls, and status bars.

## 1. D-Bus service identity

- **Bus name**: `org.mpris.MediaPlayer2.vimivo`
- **Object path**: `/org/mpris/MediaPlayer2`

## 2. MPRIS interfaces

### `org.mpris.MediaPlayer2`
Root interface exposing application identity and window controls:
- **Properties**:
  - `Identity`: `"Vimivo"`
  - `DesktopEntry`: `"dev.vimivo.desktop"`
  - `SupportedUriSchemes`: `["file", "http", "https"]`
  - `SupportedMimeTypes`: Supported audio and video MIME types
  - `CanQuit`: `true`
  - `CanRaise`: `true`
  - `HasTrackList`: `false`
- **Methods**:
  - `Quit()`: Requests application termination.
  - `Raise()`: Brings the Vimivo window to the foreground.

### `org.mpris.MediaPlayer2.Player`
Playback control and metadata interface:
- **Properties**:
  - `PlaybackStatus`: `"Playing"`, `"Paused"`, or `"Stopped"`.
  - `LoopStatus`: `"None"` (repeat off), `"Track"` (repeat one), `"Playlist"` (repeat all).
  - `Rate`: Float playback rate multiplier (e.g. 1.0).
  - `Shuffle`: Boolean indicating queue shuffle state.
  - `Metadata`: Dict containing MPRIS track metadata:
    - `mpris:trackid`: Unique D-Bus object path identifier (`/dev/vimivo/track/<sanitizedId>`).
    - `mpris:length`: Track duration in **microseconds** (int64).
    - `xesam:title`: Title string.
    - `xesam:artist`: Array of artist strings `[artist]`.
    - `xesam:album`: Album name string.
    - `mpris:artUrl`: File URI pointing to cached artwork (`file://...`).
    - `xesam:url`: File URI pointing to media location.
  - `Volume`: Double between 0.0 and 1.0.
  - `Position`: Current playback position in **microseconds** (int64).
  - `CanControl`: `true`.
  - `CanPlay`: `true`.
  - `CanPause`: `true`.
  - `CanSeek`: `true`.
  - `CanGoNext`: `true`.
  - `CanGoPrevious`: `true`.
- **Methods**:
  - `Next()`: Advances to the next queue item.
  - `Previous()`: Returns to the previous queue item.
  - `Pause()`: Pauses playback.
  - `PlayPause()`: Toggles play/pause.
  - `Stop()`: Stops playback and resets queue position.
  - `Play()`: Starts or resumes playback.
  - `Seek(Offset)`: Seeks relative offset in microseconds.
  - `SetPosition(TrackId, Position)`: Seeks absolute position in microseconds.
  - `OpenUri(Uri)`: Enqueues and plays the provided URI.

## 3. Signal notifications

- Emits `org.freedesktop.DBus.Properties.PropertiesChanged` on interface `org.mpris.MediaPlayer2.Player` whenever playback status, track metadata, shuffle, loop status, or volume changes.
- Emits `Seeked(PositionInMicroseconds)` on user or external seek actions.

## 4. Native binding and resilience

- `LinuxMprisService` uses JNA to bind directly to `libdbus-1.so.3`.
- `MprisServiceFactory` detects whether D-Bus is present and operational. If `libdbus-1` is missing, the connection fails, or the application is running in a minimal container, it cleanly returns `NoOpMprisService` without throwing exceptions or degrading the UI experience.
