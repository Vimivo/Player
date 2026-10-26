# Android video colour controls (V1 #14)

Brightness, contrast, saturation and hue for video on Android. Gamma is not available on this path.

## What is built (debug builds only)

- `Media3VideoColorEffects` (`androidApp/src/debug`) drives Media3's GL effects (`Brightness`, `Contrast`,
  `HslAdjustment`) through `ExoPlayer.setVideoEffects`. Release builds get `DisabledVideoColorEffects` and do not contain
  the `media3-effect` library (`debugImplementation`).
- Player ▸ ⋮ ▸ Playback settings ▸ **Colour (debug build)** shows four sliders (-100…100) and Reset when the engine
  reports the capability.
- Nothing starts the effect pipeline until a slider is moved, so playback that never touches them is unaffected.

What was learned about the Media3 pipeline (from its source and from the emulator):

1. Effects only take effect when set **before** `prepare()`. The first slider move therefore stops, sets the effects,
   re-prepares and seeks back to the same position (a short hiccup, once per playback session).
2. The pipeline needs the surface size, which only a `SurfaceHolder` reports, so the output is re-attached as a holder.
3. Once effects have been set, ExoPlayer keeps that renderer on the effect path for good, even for an empty list. The
   plain picture comes back only after the player is rebuilt (restart the app).
4. On the Android emulator (API 36, gfxstream GL) the pipeline produces no frames at all: audio plays, the video size
   stays 0, and there is no player error. A watchdog notices (no frame and no video size 4 s after the first change),
   marks the feature unsupported and the sheet says so. `AndroidVideoFramesTest.colourControlsEitherChangeThePicture
   OrReportThatTheyCannotRender` proves the detection on the emulator and, on a device whose GL works, that the picture
   gets darker.

## Test on a real phone (debug build)

1. Install the debug APK, play any video (H.264 MP4 is the easiest), open ⋮ ▸ Playback settings ▸ Colour.
2. Move **Brightness** down: the picture must get darker within a second and keep playing.
3. Move **Saturation** to -100: grey picture. **Hue** and **Contrast**: visible change. **Reset colour**: original.
4. Try a second video (next in the queue) and an HDR/10-bit clip if you have one, then a snapshot (Frames ▸ Save
   snapshot): does it contain the adjusted colours?
5. Report: phone model, Android version, GPU if known, and for each step ✅/❌. If the sheet shows "Colour effects
   rendered no video on this device", that is a ❌ for plan A on that phone (see below).

## If a real device also fails: alternatives

Decide on the evidence from step 5; do not build these before then.

**Plan B: libmpv does the colour work (recommended).** The desktop apps already set mpv's `brightness`, `contrast`,
`saturation`, `gamma` and `hue` properties (`MpvPlayerEngine.applyVideoAdjustments`), and the Android libmpv engine
renders into a `Surface` with its own EGL context, which works on the emulator (HEVC 10-bit and HLG play through it in
`FormatMatrixTest`). Work needed:
- implement `applyVideoAdjustments` in `LibmpvPlayerEngine` (a dozen lines, same property names; gamma comes for free);
- when the person touches a colour slider on a Media3 item, `PlaybackEngineManager` re-opens the item on the libmpv engine
  at the current position and keeps it there for that item (the engine contract already supports a per-item choice);
- accept the costs for that item: no Media3 session integration details (the adapter already fronts both engines), no
  ReplayGain parity until the shared DSP chain is wired to libmpv audio, battery use of libmpv's GL path.
- evidence: an emulator test like `FormatMatrixTest` that sets brightness and reads the PixelCopy snapshot.

**Plan C: our own GL shader on a `SurfaceView`/`TextureView`.** Decode into a `SurfaceTexture`, draw it with a fragment
shader (a 4×4 colour matrix covers brightness, contrast, saturation and hue), present on an `EGLSurface`.
- gives full control (gamma, LUTs, later tone mapping) and no dependency on Media3's effect graph;
- costs: we own the surface chain (resize, rotation, HDR/10-bit sampling, `PixelCopy` snapshots, PiP), about 400 lines
  of GL code that must be tested on several GPUs; it can fail on the same emulator GL stack, so it needs a phone too.

**Plan D: drop colour on Android.** Document the gap and the closest alternative (the system's display colour
settings, and the existing screen-brightness gesture), as `VLC_FEATURE_GAP.md` does for other gaps.
