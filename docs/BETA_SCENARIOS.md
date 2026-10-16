# Vimivo Closed Beta Testing Scenarios

This document outlines standard, repeatable test scenarios designed for both automated test harnesses and human testers.
Execute these scenarios to systematically evaluate stability, usability, and correctness.

---

## Scenario Group 1: First Launch & Initial Configuration

### 1.1 Fresh Install & Media Permission Handling
- **Platforms**: Android, iOS, macOS, Windows, Linux
- **Preconditions**: Fresh install with no existing SQLite database in Application Support / app storage.
- **Steps**:
  1. Launch the application.
  2. Verify edge-to-edge layout, correct theme adoption (System dark/light/AMOLED), and introductory empty states.
  3. (Mobile) When prompted for Audio/Video storage permissions:
     - Case A: Grant permissions -> Verify library automatically initiates initial scan.
     - Case B: Deny permissions -> Verify application explains permission denial gracefully and displays reduced functionality banner without crashing.
- **Expected Outcome**: Clean first launch; permission states handled without application termination.

### 1.2 Multi-Folder Addition & Indexing
- **Steps**:
  1. In Library view, add 2 separate directories containing audio and video.
  2. Include subfolders (nested album structure).
- **Expected Outcome**: Parser traverses directories recursively, extracts ID3/Vorbis/MP4 tags, album artwork, and registers media in SQLite database.

---

## Scenario Group 2: Audio Playback & Persistent Queue

### 2.1 Basic Playback & Transport Controls
- **Steps**:
  1. Select a music track from the Library.
  2. Verify playback starts within 250ms with correct audio routing.
  3. Test Transport buttons: Pause, Play, Seek to 50%, Seek backwards 10s, Seek forwards 30s.
  4. Verify track title, artist, album art, elapsed time, and remaining time display accurately.
- **Expected Outcome**: Immediate audio output, accurate time tracking, zero audio crackling or buffer underruns.

### 2.2 Queue Transitions & Shuffle/Repeat Modes
- **Steps**:
  1. Queue 10 diverse tracks (MP3, FLAC, M4A, Ogg, Opus).
  2. Let Track 1 finish playing naturally -> verify automatic advancement to Track 2.
  3. Enable **Shuffle** mode -> verify queue order changes unpredictably while keeping the current playing track active.
  4. Enable **Repeat One** -> verify current track restarts when duration reaches end.
  5. Enable **Repeat All** -> verify queue loops back to start after the last item.
- **Expected Outcome**: Seamless queue transitions with zero duplicate player instances or frozen UI states.

### 2.3 Rapid Interaction Stress Test
- **Steps**:
  1. Start audio playback.
  2. Rapidly click Play/Pause 10 times within 2 seconds.
  3. Rapidly click Next Track 10 times within 3 seconds.
  4. Drag the seek slider continuously back and forth across a 10-minute track.
- **Expected Outcome**: Engine remains stable; no native deadlocks, audio distortion, or unhandled exceptions.

---

## Scenario Group 3: Video Playback & Rendering

### 3.1 Format Decoding & Surface Recreation
- **Formats Tested**: MP4 (H.264/AAC), MKV (HEVC/FLAC), WebM (VP9/Opus), MOV (ProRes/AAC).
- **Steps**:
  1. Open a 1080p or 4K video file.
  2. Verify smooth video playback and audio-video lip sync.
  3. Toggle Fullscreen mode (`F` key or double click) 5 times in rapid succession.
  4. Resize window dynamically while video is playing.
- **Expected Outcome**: Embedded video surface resizes smoothly with no black-screen artifacts, memory leaks, or rendering context loss.

### 3.2 Subtitles & Multiple Audio Tracks
- **Steps**:
  1. Open an MKV video with multiple embedded audio streams (e.g. English, Bengali, Spanish).
  2. Switch active audio stream during playback.
  3. Open an external subtitle file (`.srt` / `.vtt`) or select an embedded ASS subtitle track.
  4. Verify subtitle text alignment, styling, and timing synchronization.
- **Expected Outcome**: Audio stream switches cleanly without stopping playback; subtitles render crisply.

### 3.3 Resume Playback
- **Steps**:
  1. Play a 60-minute video up to 24:15.
  2. Close the video player or quit the application entirely.
  3. Re-open the same video file.
- **Expected Outcome**: Player prompts to "Resume from 24:15" or automatically restores position based on user settings.

---

## Scenario Group 4: Playlists & Personal User Data

### 4.1 Playlist CRUD & Reordering
- **Steps**:
  1. Create a new playlist named "Acoustic Favorites".
  2. Add 8 songs from 3 different albums.
  3. Drag track 7 to position 1.
  4. Rename playlist to "Acoustic Gold".
  5. Duplicate playlist.
  6. Delete the duplicated playlist.
- **Expected Outcome**: All database operations persist immediately in SQLite with full transaction integrity.

### 4.2 History, Bookmarks, and Favorites
- **Steps**:
  1. Play 5 distinct songs.
  2. Check **History** view: Verify 5 tracks appear in reverse chronological order with exact timestamps.
  3. Mark 2 songs as **Favorites** (heart icon) -> Verify immediate appearance in Favorites view.
  4. While playing a podcast or long track, tap **Add Bookmark** at 12:40 -> Verify bookmark saved.
- **Expected Outcome**: Immediate UI state reflection; persistent across app restarts.

---

## Scenario Group 5: Native Platform & Background Integration

### 5.1 Background Playback & Lock-Screen Controls
- **Platforms**: Android, macOS, Linux, Windows
- **Steps**:
  1. Start playing a music playlist.
  2. Send application to background / lock screen.
  3. Use OS media controls (Android Media Notification, macOS Control Center / Touch Bar, Windows SMTC / media keys, Linux MPRIS):
     - Press Pause -> verify playback pauses.
     - Press Play -> verify playback resumes.
     - Press Next -> verify track changes and notification artwork updates.
- **Expected Outcome**: Foreground service remains alive without OS process killing; media keys fully synchronized.

### 5.2 Audio Routing & Interruption
- **Steps**:
  1. Play audio through Bluetooth headphones.
  2. Disconnect Bluetooth headphones -> Verify player pauses automatically to prevent accidental speaker broadcast (becoming noisy).
  3. (Mobile) Receive incoming phone call -> Verify playback pauses smoothly and resumes after call ends if configured.
- **Expected Outcome**: Clean audio focus and route transitions.

---

## Scenario Group 6: Fault Tolerance & Edge Cases

### 6.1 Disappearing / Missing Media
- **Steps**:
  1. Add a media file located on a removable USB flash drive.
  2. Unplug the USB drive while track is in the queue or playing.
- **Expected Outcome**: Player catches I/O failure gracefully, displays clear "File not found or storage disconnected" banner, skips to next available track, and NEVER deletes the database record or original file.

### 6.2 Corrupt & Zero-Byte Files
- **Steps**:
  1. Create an empty zero-byte `.mp3` file and a truncated `.mp4` file.
  2. Add them to the queue and press play.
- **Expected Outcome**: Extractor reports actionable format error; player does not crash; queue continues.
