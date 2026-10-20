# Linux native playback architecture

Vimivo uses `libmpv` as its primary native multimedia engine on Linux, accessed directly via JNA in-process bindings (`MpvNative`).

## 1. Native library loading

On Linux, Vimivo searches for `libmpv` in the following order:
1. Bundled application native directory:
   - AppImage / standalone directory: `appDir/lib/libmpv.so.2`, `appDir/lib/libmpv.so.1`, or `appDir/lib/libmpv.so`.
   - Packaged deb/rpm location: `/opt/vimivo/lib/libmpv.so*`.
2. System library path: standard Linux dynamic linker resolution paths (`/usr/lib64`, `/usr/lib/x86_64-linux-gnu`, etc.).

Dynamic discovery ensures that packaged standalone builds use relocatable dependencies bundled in the package image, while development and distribution builds can utilize the host's native libmpv when present.

## 2. Embedded video rendering: `MpvEglRenderer` (libmpv render API)

Vimivo ships an LGPL-only libmpv (ADR-004). mpv's X11 code is GPL, so that build has **no X11 video output** and
cannot embed through `wid`. Linux therefore uses the same model as macOS: Vimivo owns the GL context and libmpv only
renders into it.

1. A `java.awt.Canvas` hosts the video. Once it is displayable, `Native.getComponentID(canvas)` returns its X11 window
   (XWayland on Wayland sessions).
2. `MpvEglRenderer` starts one render thread. On it, the renderer:
   - opens its own X connection;
   - creates an EGL display, a config matching the window's visual, a window surface and an OpenGL context (OpenGL ES 2
     as a fallback). This is all JNA, with no compiled bridge.
3. libmpv runs with `vo=libmpv`. The renderer creates an `mpv_render_context`:
   - `get_proc_address` comes from `eglGetProcAddress`.
   - When a DRM render node (`/dev/dri/renderD*`) can be opened, it is handed over as `MPV_RENDER_PARAM_DRM_DISPLAY_V2`,
     so VA-API decodes on the GPU (EGL dma-buf interop). Without one, decoding falls back to software.
4. libmpv's update callback wakes the thread. It renders into the default framebuffer (Y flipped), swaps, and reports
   the swap.
5. Window size is re-read every iteration; a resize redraws the current frame even while paused.
6. `detach()` stops the thread, which frees the render context *before* the libmpv handle is destroyed, then the EGL
   objects and the X connection.

libmpv no longer touches Xlib, so AWT's process-wide X error handler stays installed. The old restore workaround is
gone. A software renderer (Mesa llvmpipe in VMs and CI) is still detected from libmpv's `GL_RENDERER` log line and
switches to the `fast` profile.

## 3. Audio output pipeline

libmpv handles Linux audio automatically using available system backends:
- **PipeWire**: Used directly or via PulseAudio emulation layer (`ao=pipewire,pulse`).
- **PulseAudio**: Direct pulse sink output.
- **ALSA**: Direct hardware output fallback.

Audio features supported:
- Track switching (multi-language audio tracks).
- Volume control and mute.
- Playback speed adjustment (0.25x to 3.0x with pitch correction via `scaletempo`).
- Gapless queue progression.

## 4. Hardware video acceleration

Hardware acceleration is configured with graceful fallbacks:
- `hwdec = auto-safe` (utilizes VA-API on Intel/AMD GPUs or VDPAU/NVDEC on NVIDIA).
- If hardware decoding initialization fails or the codec is unsupported, libmpv falls back seamlessly to software decoding without dropping playback or crashing the process.

Manual verification on real hardware (GPUs, VA-API, X11/XWayland, PulseAudio/PipeWire): [LINUX_MANUAL_TEST_CHECKLIST.md](LINUX_MANUAL_TEST_CHECKLIST.md).
