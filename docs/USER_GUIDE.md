# Vimivo — Complete User Guide

Welcome to Vimivo, your privacy-first universal music and video player. This guide explains how to get the most out of Vimivo on your desktop or mobile device.

---

## 1. Getting Started & Adding Media

1. **First Launch**: When you open Vimivo for the first time, your library is empty.
2. **Add a Folder**:
   - On Desktop: Go to **Settings** or **Music/Videos** and click **Add Folder**. Select your local music or movie directory.
   - On Mobile: Tap **Allow Access** when prompted for media permissions, or use the file picker to choose an authorized folder.
3. **Automatic Scanning**: Vimivo recursively scans the folder in the background. Audio tags, cover art, and video thumbnails appear automatically.

---

## 2. Browsing & Playing Media

- **Music Library**: Browse by Songs, Albums, or Artists.
- **Videos**: Browse movies, clips, or lectures. Video thumbnails are generated directly from local video frames.
- **Search**: Use the global search bar. Supports instant matching across titles, artists, albums, and filenames, with full Unicode NFC and Bengali script support.
- **Play All & Shuffle Play**: Click **Play All** on any album, artist, or playlist to start a continuous playback queue.

---

## 3. Playback Queue, Shuffle & Repeat

The bottom player bar controls your active queue:
- **Play / Pause**: Spacebar (Desktop) or center button.
- **Next / Previous**: Skip to next or return to previous track.
- **Shuffle**: Shuffles remaining tracks while keeping the currently playing track active.
- **Repeat Modes**:
  - `Off`: Stops after reaching the end of the queue.
  - `All`: Repeats the entire queue from the beginning.
  - `One`: Continuously loops the currently active song or video.
- **Queue Panel**: Click the **Queue** icon to view, reorder, or remove upcoming tracks.

---

## 4. Audio Controls & Enhancements

- **10-Band Graphic Equalizer**: Choose from 10 handcrafted presets (Flat, Bass Boost, Treble Boost, Vocal, Classical, Rock, Jazz, Pop, Acoustic) or customize sliders (-12 dB to +12 dB). Auto-attenuating preamp prevents audio clipping.
- **ReplayGain**: Normalizes volume across tracks so you never have to adjust volume between quiet classical recordings and loud modern tracks. Choose between **Track** and **Album** mode.
- **Synchronized Lyrics (LRC)**: Place an `.lrc` file with the same name next to your song, or let Vimivo discover sibling lyrics. Lyrics scroll and highlight active lines in real-time.
- **Sleep Timer**: Set a timer to automatically fade out volume and pause playback when you fall asleep.

---

## 5. Video Player Controls

When watching videos:
- **Aspect Ratio**: Toggle between `Fit` (letterbox), `Fill` (crop to window), `16:9`, `4:3`, and `21:9`.
- **Frame Screenshots**: Click the camera icon or press `S` to capture a crystal-clear native video frame directly to your Desktop.
- **Video Rotation**: Rotate videos in 90-degree steps (ideal for vertical smartphone clips).
- **GPU Adjustments**: Adjust brightness, contrast, saturation, and gamma in real-time.
- **Subtitles**: Load external `.srt`, `.vtt`, `.ass`, or `.ssa` subtitles. If subtitles are out of sync, use the delay slider (±5000ms) to sync audio and text perfectly.
- **A-B Repeat Looping**: Mark point A and point B to loop an exact scene, dance step, or language lesson.

---

## 6. Playlists & Smart Playlists

- **Custom Playlists**: Create playlists, add songs or videos, and drag to reorder.
- **M3U / M3U8 Export & Import**: Export playlists to standard `.m3u8` files or import existing playlists from other players.
- **Rule-Based Smart Playlists**:
  - Navigate to **Smart Playlists** in the sidebar.
  - Click **New Smart Playlist**.
  - Define rules: e.g., `Genre EQUALS Rock` AND `Release Year LESS_THAN 2000`.
  - Smart playlists dynamically re-evaluate as new songs are added to your library.

---

## 7. Metadata Editor & Album Artwork

Vimivo offers two distinct editing modes:
- **Mode A: Database Overrides (Safe Default)**:
  - Updates track title, artist, album, genre, year, or artwork in Vimivo's local SQLite database.
  - Your original audio file is 100% untouched.
  - Perfect for read-only drives, external drives, or unsupported formats.
- **Mode B: Embedded Tag Writing**:
  - For supported formats (MP3, FLAC, OGG, M4A, WAV), writes metadata and cover art directly into the file's ID3/Vorbis tags.
  - Includes automatic backup and verification before committing.

---

## 8. Batch Renaming & Missing Media Relinker

- **Batch Rename**: Rename multiple files using patterns like `{track} - {artist} - {title}`. Previews old vs new names, detects collisions, and reconciles database references so your playlists never break.
- **Missing Media Relinker**: If you rename or move a music folder on your hard drive, Vimivo identifies unavailable items and scans authorized folders to find candidate matches (by filename, title, or file size) to relink them with one click.

---

## 9. Library Health Dashboard

Open **Automation & Health** in the sidebar to review:
- Total media items, audio vs video breakdown.
- Count of missing or unavailable files.
- Media items missing artist or album tags.
- Detected duplicate file clusters.
- Library health score percentage.

---

## 10. Privacy & Offline Guarantees

- **100% Offline**: Vimivo never connects to analytics servers, advertisers, or third-party tracking services.
- **No Account Required**: All data (favorites, history, playlists, settings) is stored exclusively on your device in local SQLite databases.
- **Diagnostic Export**: When exporting diagnostic bundles for support, all personal usernames and folder paths are automatically masked.
