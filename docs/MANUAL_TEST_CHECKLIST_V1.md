# Manual test checklist (V1): Android phone and Mac

Things that cannot be proven on an emulator or in CI. Mark ✅ / ❌ and note the device. Install the **debug** APK on the
phone (the colour controls exist only there).

## Android phone

**Colour controls (the open question, V1 #14)** — see `ANDROID_COLOUR_CONTROLS.md`
1. Play an H.264 MP4. ⋮ ▸ Playback settings ▸ **Colour (debug build)**.
2. Brightness down: darker within a second, still playing. Saturation -100: grey. Hue, Contrast: visible. Reset: original.
3. Next video in the queue: still adjusted? Take a snapshot (Frames ▸ Save snapshot): does it carry the colours?
4. If the sheet says "Colour effects rendered no video on this device", note phone model, Android version, GPU: that
   decides plan B (libmpv) or C (own GL shader) in the same document.

**Open with (V1, from the earlier agreement)**: from the Files app open a video and an audio file with Vimivo; the folder
queue is offered; the player opens.

**Music player**
5. Play a track: the seek bar is a plain line first, then real bars appear within a few seconds (long files take longer).
   Drag it: seeks. Rotate to landscape: artwork left, shortcuts right.
6. Lyrics: put `Song.lrc` beside `Song.mp3`; Now Playing ▸ Lyrics shows synced lines; tap a line seeks. Load a file by hand.
7. Equalizer: switch on, choose Rock, drag a band: the sound changes; Reset. Sleep: 15 min / "after current track".
8. Screen off with a sleep timer of 15 min: audio fades and stops at the end (set 15 min, wait).

**Video player**
9. Swipe up from the bottom controls: Playback settings. Next up shows thumbnails **and durations**; tap a row jumps.
10. Crop ▸ Auto on a letterboxed video removes the bars (check the picture, not only the menu).
11. Picture-in-picture from the top bar; back to the app.

## Mac (desktop app)

12. Open an audio file: the timeline shows faint real peaks behind the line after a moment (none for video).
13. Play a video to the end, drag the timeline back and press play: it plays again (note if the picture stays on the last frame).
14. Hover the timeline: time and frame preview. Menu bar items for View ▸ Always on top.
15. Growing file: copy a large video with Finder while playing the partial file; playback keeps going until the copy ends.
16. `./scripts/verify_all.sh` on the Mac: all steps green (this includes the smoke test, 9 checks).

## iPhone (if you have one)

17. Now Playing: seek line → bars, lyrics (file picker, sidecar), equalizer, **sleep timer** with the phone locked: fades and pauses.
18. Queue: swipe-delete is disabled for the playing row; drag to reorder keeps the playing song.
