# D1 black video on macOS: root cause and fix (2026-10-19)

**Symptom:** the player window stayed black while audio played and the clock advanced.

**Root cause:** `desktopApp/src/desktopMain/native/macos/MpvCocoaRenderer.mm`, `drawInCGLContext`, told libmpv to render
into framebuffer `0`. Core Animation binds the `CAOpenGLLayer`'s own IOSurface-backed framebuffer before calling that
method; it is `0` only when the layer is created at its final size. Since the player/library window split (`a0262fd`)
the layer attaches while the canvas is still 0×0, so every frame failed with `GL_INVALID_FRAMEBUFFER_OPERATION` (1286)
and the layer stayed transparent over Compose's black SwingPanel fill.

**How it was found:**

1. A new pixel probe (`DesktopVideoProbe`) reads the screen under the video canvas from inside the app.
2. Bisect with that probe: `c944c64` shows the picture (`lit=1.00`), `a0262fd` does not (`lit=0.00`).
3. Native logging: the layer draws ~30 fps with the right frame and nothing covers it (only the title bar sits above);
   `glGetError()` after `mpv_render_context_render` = 1286, read-back pixel `0,0,0,0`.

**Fix:** render into `GL_DRAW_FRAMEBUFFER_BINDING` (what mpv's own `cocoa-cb` does).

**Regression gate:** `--smoke-test` now fails a video unless the picture is on screen (≥10 % lit, ≥3 colours), and
re-checks after fullscreen enter, fullscreen exit and resize. It runs in `scripts/verify_all.sh` and in the packaged
smoke tests of `macos.yml`, `windows.yml` and `linux.yml`. With the old bridge line restored it fails with `lit=0.00`.

| File | What |
|---|---|
| `smoke-after-fix.log` | audio → colour bars → fullscreen enter/exit → resize, all PASS with pixel figures |
| `screens/*.png` | what the probe saw (opt-in `VIMIVO_SMOKE_CAPTURE_DIR`), downscaled |

The same run on the reporter's `park.mp4` passed (`lit=0.83 colours=23`); those frames are personal footage and are not
committed.
