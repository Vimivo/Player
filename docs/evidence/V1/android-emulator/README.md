# Android emulator pass (2026-10-19)

Device: emulator `Medium_Phone_API_36.1` (Android 16), debug APK from `1ae63f5`. Media from
`scripts/generate_test_media.sh` and `testmedia/`, pushed to `/sdcard/Movies/vimivo` and `/sdcard/Music/vimivo`, indexed by
MediaStore. State read from `adb shell dumpsys media_session`; screenshots with `adb exec-out screencap`.

| Check | How | Result |
|---|---|---|
| Video plays ("Open with") | `am start -a VIEW -d content://media/external/video/media/<id> -t video/mp4` on `colorbars.mp4` | `PLAYING`, position 3244 ms after 6 s; picture on screen (`video-colorbars-playing.png`) |
| Audio plays ("Open with") | VIEW intent on `track1_solaris.flac` | `PLAYING` at 3133 ms, ends at 10010 ms (`STOPPED`) |
| Auto-advance | Music ▸ All songs, tap `hires` (6 s) | t=3 s `hires` PLAYING; t=9 s `Lunar Horizon` PLAYING at 16 ms (`audio-auto-advanced.png`) |
| Background audio | `input keyevent KEYCODE_HOME` while playing | t=13 s and t=17 s still `PLAYING`, position advancing; `VimivoPlaybackService` `isForeground=true`, media notification `category=transport` |

**Gap found:** a file opened through "Open with" is a one-item queue on Android, while desktop queues the folder's other
files (`LaunchPlanner`). Recorded against matrix #45.

**Setup note:** pushing the same files twice left duplicate MediaStore rows, so the library lists those songs twice.
That is the test setup, not a scanner bug.
