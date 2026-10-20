# Linux manual test checklist (real hardware)

CI runs Linux under Xvfb with Mesa llvmpipe: no GPU, no compositor, no real audio device. This checklist covers what CI
cannot: the LGPL libmpv render-API video path (`MpvEglRenderer`, ADR-004) on real GPUs, VA-API on and off, X11 and
XWayland sessions, and PulseAudio and PipeWire-pulse.

Fill in one results table per machine at the bottom and commit it under `docs/evidence/V1/linux-hardware/`.

## 0. Setup (once per machine)

1. **Install** a build from CI (`vimivo-linux-*`):
   - `sudo apt install ./vimivo_1.0.0_amd64.deb`, or run the AppImage.
   - Note which one you used.
2. **Test media:**
   - `testmedia/` from the repository.
   - The format pack: `scripts/generate_test_media.sh` (needs a local ffmpeg). It creates `testmedia/pack/` with
     `h264.mp4`, `hdr10.mkv`, `av1.mkv`, `vp9.webm`, `interlaced.ts`, `multiaudio.mkv`, `truncated.mp4`, … .
3. **Record the machine** in the results table. Copy the output of these commands:
   ```sh
   lsb_release -ds; uname -r
   echo "$XDG_SESSION_TYPE"                    # x11 or wayland
   glxinfo -B | grep -E 'OpenGL renderer|OpenGL version'   # mesa-utils
   vainfo 2>&1 | grep -E 'Driver version|VAProfile(H264|HEVC|VP9|AV1)' | head   # vainfo package
   pactl info | grep 'Server Name'             # "PulseAudio" or "PulseAudio (on PipeWire …)"
   ```
4. **Logging:** start Vimivo with libmpv's log enabled; every check below reads it.
   ```sh
   VIMIVO_MPV_LOG_FILE=/tmp/vimivo-mpv.log /opt/vimivo/bin/Vimivo    # or ./Vimivo-x86_64.AppImage
   ```

## 1. Session × audio matrix

Run sections 2–5 once per combination available on the machine:

| Session | Audio server | How to get it |
|---|---|---|
| X11 (Xorg) | PulseAudio | Log in to an "Xorg" / "on Xorg" session; a distribution with plain PulseAudio (e.g. Ubuntu 22.04) |
| X11 (Xorg) | PipeWire-pulse | Xorg session on a PipeWire distribution (Ubuntu 22.10+, Fedora 34+) |
| Wayland → XWayland | PulseAudio | Wayland session (GNOME/KDE) on a PulseAudio system |
| Wayland → XWayland | PipeWire-pulse | Default GNOME/KDE session on a current distribution |

On Wayland, check that Vimivo runs through XWayland: `xlsclients | grep -i vimivo` lists it, or
`xprop` can click its window.

## 2. Video output (render API + EGL)

For each item, look at the picture and at the log (`grep -E 'GL_RENDERER|\]\[e\]' /tmp/vimivo-mpv.log`).

- [ ] **2.1** Open `testmedia/video_landscape.mp4`.
  - The picture appears in the window, not black and not offset, within 1 s.
  - The log shows `[libmpv_render] GL_RENDERER='<your GPU>'`, not llvmpipe.
- [ ] **2.2** The Media Info dialog's "Rendering" row names the GPU. It does not say "Software rendering — performance
  mode".
- [ ] **2.3** Resize the window continuously for 5 s. The picture follows the size: no stretching, stale frame or black
  band.
- [ ] **2.4** Pause, then resize. The paused frame is redrawn at the new size.
- [ ] **2.5** Fullscreen in and out three times (F / double-click). The picture is present each time; no black frame
  stays.
- [ ] **2.6** Move the window to a second monitor, if one is available, and back. The picture continues; play
  fullscreen on the second monitor.
- [ ] **2.7** Play `testmedia/video_portrait.mp4` and `testmedia/video_sample.mkv` (with subtitles).
  - Aspect ratio is correct.
  - Subtitles are drawn.
- [ ] **2.8** Play `testmedia/pack/hdr10.mkv`. The picture is tone-mapped: not washed-out grey, not crushed.
- [ ] **2.9** Close the window while a video plays.
  - No crash, and no `BadWindow` or `BadDrawable` in the terminal.
  - Reopen Vimivo and play again.
- [ ] **2.10** Suspend and resume the machine with a video paused. Press play: the picture comes back.

## 3. Hardware decoding (VA-API)

The desktop app has no hardware-acceleration setting yet (a V1 item); it uses `auto-safe`. For these checks, start
Vimivo with the diagnostic override `VIMIVO_HWDEC`, as well as `VIMIVO_MPV_LOG_FILE`:

```sh
VIMIVO_HWDEC=vaapi VIMIVO_MPV_LOG_FILE=/tmp/vimivo-mpv.log /opt/vimivo/bin/Vimivo   # 3.1
VIMIVO_HWDEC=no    VIMIVO_MPV_LOG_FILE=/tmp/vimivo-mpv.log /opt/vimivo/bin/Vimivo   # 3.2
VIMIVO_MPV_LOG_FILE=/tmp/vimivo-mpv.log /opt/vimivo/bin/Vimivo                      # 3.3 (default auto-safe)
```

- [ ] **3.1 VA-API on:** start with `VIMIVO_HWDEC=vaapi` and play `testmedia/pack/h264.mp4`, then `hdr10.mkv`
  (HEVC 10-bit).
  - The log shows `Using hardware decoding (vaapi)`.
  - CPU use (`top -p $(pgrep -f Vimivo)`) is clearly lower than in 3.2.
- [ ] **3.2 VA-API off:** start with `VIMIVO_HWDEC=no` and play the same files.
  - The log has no `vaapi` line.
  - Playback is still smooth for 1080p H.264.
- [ ] **3.3 Automatic:** start without `VIMIVO_HWDEC` and play `vp9.webm` and `av1.mkv`.
  - Codecs the GPU supports (see `vainfo`) decode with VA-API.
  - The others fall back to software without an error.
- [ ] **3.4 No render node:** run Vimivo as a user without access to `/dev/dri/renderD*`, or use
  `sudo setfacl -m u:$USER:- /dev/dri/renderD128` temporarily (undo with `setfacl -x u:$USER /dev/dri/renderD128`).
  - Video still plays, with software decoding.
  - There is no error dialog.
- [ ] **3.5** NVIDIA proprietary driver only: VA-API usually is not available (no `nvidia-vaapi-driver`). Video must
  still play, with software decoding. Note the result.

## 4. Audio

- [ ] **4.1** Play `testmedia/track1_solaris.flac` and `track2_lunar.mp3`. Sound comes out of the default device.
  The log shows `ao/pulse` (on PipeWire through pipewire-pulse).
- [ ] **4.2** Switch the output device in Vimivo's audio device menu while playing: sound moves to it.
- [ ] **4.3** Unplug or disable the output device while playing.
  - Playback continues or pauses; it doesn't hang.
  - Plug it back in and pick it.
- [ ] **4.4** Raise volume to 150 %. It sounds louder, without clipping crackle on the test tracks.
- [ ] **4.5** Turn the equalizer on with the Rock preset. The sound changes audibly, and turning it off restores the
  original sound.
- [ ] **4.6** MPRIS media keys (keyboard play/pause, next) and the desktop's media widget control Vimivo. The title and
  artist show up in the widget.

## 5. Robustness

- [ ] **5.1** Open `testmedia/pack/truncated.mp4` and `broken_index.avi`. They play as far as possible, or show a clear
  error; no hang or crash.
- [ ] **5.2** Library: add `testmedia/` as a folder. Videos get thumbnails, durations and resolutions (read through
  libmpv, with no ffmpeg installed on the system).
  - Check that no ffmpeg is installed: `command -v ffmpeg` prints nothing.
- [ ] **5.3** Media Info on `testmedia/video_sample.mkv` lists H.264 1280×720, AAC, and an English subrip track.
- [ ] **5.4** Close the main window during playback (Vimivo has no Ctrl+Q on Linux yet).
  - The process exits within 2 s.
  - The terminal shows no X error and no core dump.

## Results

Copy this table per machine. Use ✅, ❌ or "n/a", and add notes for anything that failed (attach
`/tmp/vimivo-mpv.log` and a screenshot).

| Machine | Distro / kernel | GPU / driver | VA-API (vainfo) |
|---|---|---|---|
| | | | |

| Check | X11 + PulseAudio | X11 + PipeWire-pulse | XWayland + PulseAudio | XWayland + PipeWire-pulse |
|---|---|---|---|---|
| 2.1–2.10 video | | | | |
| 3.1 VA-API on | | | | |
| 3.2 VA-API off | | | | |
| 3.3 automatic | | | | |
| 3.4 no render node | | | | |
| 4.1–4.6 audio | | | | |
| 5.1–5.4 robustness | | | | |
