# Linux: black video and exit crash (2026-10-19)

Reproduced locally in an Ubuntu 24.04 container that matches `linux.yml`: JDK 17, distro libmpv 0.37, Xvfb, Mesa
llvmpipe (no GPU). Files: `Dockerfile`, `smoke.sh` (the CI smoke sequence: two videos, two songs, then fullscreen
enter/exit and resize), `mpvcli.sh` (plain mpv CLI measurements).

## 1. Black video: every frame dropped on a software renderer

**Symptom:** in about half the runs one or more videos stayed black while audio played. Which file failed varied
from run to run.

**What was ruled out:**

- *Render-target size:* the libmpv log shows the right sizes ("Resize: 960x467"…).
- *Window stacking:* `XQueryTree` showed the video canvas as the topmost sibling every time.
- *AWT painting over the video:* the window tree and `xwd` captures show nothing above mpv.

**Decisive capture** (`xwd`, a separate X client, every 0.5 s; `before-*.png`):

- During the failing file, the root window is black.
- **mpv's own window is black too** (`before-mpv-window.png`, rows 2–3).
- At some sizes, mpv's window shows the picture through a black grid.

**Same result with the plain mpv CLI, no Vimivo involved** (mpv 0.37, `--vo=gpu --gpu-sw=yes`, 960×467 window,
share of lit pixels, 3 tries each):

| Options | lit |
|---|---|
| defaults | 0, 0.001, 0.001 |
| `--framedrop=no` | 0.66, 0.42, 0.61 |
| `--profile=fast` | 0.66, 0.66, 0.66 |
| `--gpu-dumb-mode=yes` | 0.66, 0.66, 0.66 |

**Root cause:**

- libmpv's default high-quality scalers are too slow on a CPU-only OpenGL renderer.
- Every frame is late and dropped, so the window stays black; with frame dropping off, the picture appears.
- This affects any machine without GPU drivers (VMs, remote desktops, CI), not only CI.

**Fix:**

- The engine reads libmpv's warnings through the client API (`mpv_request_log_messages`, `mpv_wait_event`).
- When libmpv reports "Suspected software renderer", it applies mpv's built-in `fast` profile.
- Real GPUs keep the defaults.
- The media diagnostics show "software OpenGL, fast profile".
- Tests: `MpvLogEventsTest`.

## 2. Exit crash: `X Error … BadWindow (X_GetProperty)`

**Root cause:**

- Xlib's error handler is process-wide.
- AWT installs one that ignores harmless errors (the JDK never calls Xlib's default handler, "may just terminate
  the process").
- libmpv installs its own while its video output runs, then leaves Xlib's default handler installed when that output
  stops (an audio file, closing).
- From then on, any harmless X error exits the app, for example AWT querying a just-destroyed window during exit.

**Evidence:**

- After an audio file and after closing, the installed handler was at libX11 offset `0x44e20`, the address
  `XSetErrorHandler` reports for `_XDefaultError` (checked with ctypes in the same image).
- The toolkit handler captured at attach was a different address.

**Fix:** `MpvX11Renderer` records AWT's handler at attach and reinstalls it after libmpv may have changed it (every
poll, and after the engine closes).

## Result

Five consecutive local runs of the CI smoke sequence with both fixes: **35/35 checks pass, 0 exit crashes**.
Before: failures in most runs, and an exit crash in 1–4 of 5 runs.

`DesktopVideoCanvas` (no AWT painting) and `MpvPlaybackErrors` (error mapping moved out of the engine) came from the
same investigation. Neither changes behaviour.
