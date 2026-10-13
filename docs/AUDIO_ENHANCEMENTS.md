# Audio enhancements

Vimivo exposes an audio control only when the active engine reports that it is operational. Capabilities can change when the engine, Android audio session, device, or output route changes.

## Implemented behavior

- Media3: Android session graphic equalizer with device-reported bands and gain limits, Flat/Rock/Pop/Classical/Jazz/Vocal/Bass Boost/Custom curves, optional BassBoost when the device reports strength control, upper-band treble adjustment, and attenuation-only preamp.
- Clipping protection: positive curve peaks are offset by automatic headroom. Preamp is limited to `-12..0 dB`; Vimivo does not offer unsafe positive digital preamp gain.
- Route handling: speaker, wired, Bluetooth, USB, and HDMI routes are identified from Android output devices. Effects are released and recreated when the audio session or reported route changes.
- LibVLC: Vimivo disables its Android session effects. The current LibVLC integration does not expose a stable audio-session contract on which these effects can be guaranteed.
- Playback speed: supported by both engines within their declared ranges. Pitch preservation is delegated to each engine and Android audio sink.
- Sleep timer: durations, stop-after-current, cancellation, and an optional bounded fade before pause. Timers are process-local and do not unexpectedly restart playback after process death.
- Lyrics: UTF-8 local LRC and plain text through SAF, multiple timestamps per line, synchronization, auto-advancing display, and tap-to-seek. The selected document permission is persisted when the provider allows it.
- Visualizer: a low-rate playback activity animation driven by existing position updates. It is intentionally not advertised as a spectrum or waveform and requires no microphone/capture permission.

## Not currently exposed

- ReplayGain and normalization: scanner/player metadata does not yet provide verified gain/peak tags end-to-end.
- Gapless and crossfade: the authoritative queue opens each item through the engine manager; it does not maintain a prepared adjacent Media3 window.
- Stereo balance and mono downmix: not provided by a reliable cross-device Android session effect in the current pipeline.
- User-selectable output routing: Android and connected-device policy own routing; Vimivo observes routes but does not force them.
- Embedded lyrics: MediaStore and the shared metadata model do not provide a reliable portable lyrics field. Local LRC selection is supported; embedded extraction remains a documented gap.
- Bit-perfect/high-resolution output: Android mixing, resampling, effects, Bluetooth codecs, firmware, and hardware paths can alter output. Vimivo makes no bit-perfect claim.

## Test targets

Use owned/generated 44.1 kHz and 48 kHz PCM/FLAC sweeps, a high-resolution FLAC, clipped and headroom-safe tones, Bluetooth, wired, speaker, and USB routes. Verify session recreation, engine fallback, rapid enable/disable, route removal, preset changes, timer fade/cancel, malformed LRC, repeated timestamps, and process death. Record output externally for frequency/gain verification; UI observation alone is not DSP validation.
