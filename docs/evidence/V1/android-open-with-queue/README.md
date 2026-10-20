# Android "Open with" queues the folder (2026-10-19)

Emulator `Medium_Phone_API_36.1` (Android 16). Folder `/sdcard/Movies/Series` holds `Episode 1.mp4`, `Episode 2.mp4`,
`Episode 10.mp4` (5 s each; plain text order would put 10 before 2).

| Step | Result (`dumpsys media_session`) |
|---|---|
| `am start -a VIEW -d content://media/external/video/media/30` ("Episode 2") | queue `size=3`, active item `id=1`: Episode 2, so the queue is Episode 1, Episode 2, Episode 10 (`episode2-opened.png`) |
| after the 5 s clip | active item `id=2`: Episode 10, playing (`episode10-auto-advanced.png`) |
| after Episode 10 | `STOPPED` at 5028 ms (end of queue) |

Ordering is the shared `SiblingQueues` (`shared/commonMain`, `SiblingQueuesTest`), also used by desktop `LaunchPlanner`.

**Not run:** file-manager (DocumentsProvider) URIs. `adb shell am start --grant-read-uri-permission` cannot grant
`com.android.externalstorage.documents` URIs (ActivityManager security exception). The code maps them with
`MediaStore.getMediaUri`; verifying needs the Files app UI or a device.
