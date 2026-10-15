# Windows Native Playback Architecture

This document describes the native multimedia playback engine and video rendering implementation for Vimivo on Windows.

## Playback Engine: libmpv JNA Client

Vimivo uses the `libmpv` C client API (not an external `mpv.exe` child process) via Java Native Access (JNA):
- Engine implementation: `MpvPlayerEngine` in `desktopApp/src/desktopMain/kotlin/dev/vimivo/desktop/playback/MpvPlayerEngine.kt`.
- Native binding: `MpvNative` interface binding `mpv_create`, `mpv_initialize`, `mpv_set_option_string`, `mpv_set_property_string`, `mpv_get_property`, `mpv_command`, and `mpv_terminate_destroy`.

### Options and initialization

On Windows startup:
1. `MpvPlayerEngine` creates a native mpv context via `mpv_create()`.
2. Sets options:
   - `config = no`: Prevents arbitrary user configs from hijacking player parameters.
   - `terminal = no`: Disables console logging.
   - `vo = gpu`: Selects the hardware-accelerated Direct3D / OpenGL video output for Windows.
   - `keep-open = yes`: Retains playback state at track completion to allow smooth queue transitions.
3. Initializes the context via `mpv_initialize()`.

## Embedded Video Rendering: MpvWin32Renderer

### HWND Surface Attachment

Compose Multiplatform Desktop on Windows runs atop Java AWT / Swing:
- The video viewport is rendered inside an AWT `java.awt.Canvas`.
- When displayed, the canvas is backed by a native Win32 window peer.
- `MpvWin32Renderer.attach(canvas, context)`:
  1. Calls `canvas.addNotify()` if not yet displayable to ensure the native peer is created.
  2. Resolves the Win32 `HWND` identifier via `com.sun.jna.Native.getComponentID(canvas)`.
  3. Sets the mpv property `"wid"` to the decimal string representation of the HWND.
  4. Video frames are rendered directly into the canvas surface by libmpv's Direct3D/GPU pipeline.

### Detach and Lifecycle

- When video completes or the canvas is unmounted, `MpvWin32Renderer.detach()` sets property `"wid"` to `"0"` and clears active context references.
- Native resources are explicitly freed on `engine.close()`, preventing handle leaks.

## Audio Playback

- Audio output on Windows automatically utilizes the default Windows Audio Session API (WASAPI) backend provided by libmpv.
- Supports volume control, mute, playback speed (0.25x to 4.0x with pitch correction), seeking, and audio track switching without modifying system master volume.
