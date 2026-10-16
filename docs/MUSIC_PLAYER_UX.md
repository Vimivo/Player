# Vimivo Music Player Experience

This document details the music playback architecture, control hierarchy, synchronized mini/full player states, seekbar interaction, and queue management in Vimivo.

---

## 1. Single Authoritative Playback State

- **Zero Duplicate Engines**: All UI representations (desktop cards, mobile mini-player, full-screen player, lockscreen media controls, MPRIS) read from the same underlying `PlayerEngine` and `PlaybackQueueController` `StateFlow`.
- **Zero Loss of Seek Position**: Expanding from mini-player to full player or switching navigation destinations observes the existing playback progress without initiating a seek or reload.
- **Synchronized State**:
  - `status`: Playing, Paused, Buffering, Stopped, Completed.
  - `positionMillis` and `durationMillis`: Microsecond-accurate timekeeping synchronized with the audio output clock.
  - `volume` and `speed`: Hardware-accurate volume scaling and pitch-compensated playback rates (0.5x .. 2.0x).

---

## 2. Interactive Control Hierarchy

1. **Primary Playback Transport**:
   - Play/Pause toggle with real-time semantic labeling ("Start playback" / "Pause playback").
   - Previous Track: Restarts track if position > 3 seconds, otherwise moves to preceding queue item.
   - Next Track: Advances to next queue item with gapless transition.
   - Stop: Halts playback and resets position to beginning.
2. **Queue Mode Modifiers**:
   - Shuffle: Toggles random non-repeating queue permutation.
   - Repeat Mode: Cycles through Off -> Repeat Track (1) -> Repeat Queue (All).
3. **Volume & Mute**:
   - Volume slider with percentage state announcements (e.g. `stateDescription = "75%"`).
   - Dedicated Mute/Unmute button with memory of pre-mute volume level.
4. **Time & Seeking**:
   - Accessible seekbar with discrete stepping via keyboard (Left/Right arrows for ±5s, Shift+Arrows for ±30s).
   - Screen-reader announcement provides readable elapsed and total time (e.g., "1:15 of 3:45").
   - No excessive database writes during rapid scrubbing; database persistence occurs on seek completion and checkpoint intervals.
5. **Lyrics & Bookmarks**:
   - Synchronized LRC and static lyrics display with timestamp highlighting.
   - Timestamped bookmark cards allowing instant jumps to saved moments.

---

## 3. Queue & Playlist Management

- **Non-Drag Reorder Alternatives**:
  - Every queue and playlist item features accessible "Move Up" and "Move Down" buttons.
  - Boundary-checked: Top item disables "Move Up", bottom item disables "Move Down".
  - Dedicated "Remove" action per item.
  - Full keyboard traversal and screen-reader labeling ("Move [Title] up in queue").
