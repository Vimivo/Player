# Linux native playback architecture

Vimivo uses `libmpv` as its primary native multimedia engine on Linux, accessed directly via JNA in-process bindings (`MpvNative`).

## 1. Native library loading

On Linux, Vimivo searches for `libmpv` in the following order:
1. Bundled application native directory:
   - AppImage / standalone directory: `appDir/lib/libmpv.so.2`, `appDir/lib/libmpv.so.1`, or `appDir/lib/libmpv.so`.
   - Packaged deb/rpm location: `/opt/vimivo/lib/libmpv.so*`.
2. System library path: standard Linux dynamic linker resolution paths (`/usr/lib64`, `/usr/lib/x86_64-linux-gnu`, etc.).

Dynamic discovery ensures that packaged standalone builds use relocatable dependencies bundled in the package image, while development and distribution builds can utilize the host's native libmpv when present.

## 2. Embedded video rendering: `MpvX11Renderer`

Compose Desktop runs on the JVM using AWT/Swing under the hood. On Linux:
- Under X11, windows are native X11 windows with 32-bit/64-bit `Window` IDs (`XID`).
- Under Wayland, Compose Desktop runs via XWayland by default, or connects through standard Wayland AWT canvas surfaces where native XIDs are exposed by the toolkit.

`MpvX11Renderer` embeds video directly into the Compose layout:
1. In Compose, an `AwtWindow` or `SwingPanel` hosts a standard `java.awt.Canvas`.
2. Once the canvas peer is materialized (`canvas.addNotify()`), JNA's `Native.getComponentID(canvas)` returns the native window handle `XID`.
3. The renderer sets libmpv's `wid` (window ID) property to this `XID`.
4. libmpv configures its video output backend:
   ```c
   mpv_set_option_string(mpv, "vo", "gpu,x11");
   mpv_set_option_string(mpv, "wid", widString);
   ```
5. When the user resizes the window, exits fullscreen, or switches between songs and videos, libmpv handles surface coordinate updates automatically through the window ID lifecycle.
6. Upon detaching or closing, `wid` is reset to 0 and native resources are cleanly released.

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
