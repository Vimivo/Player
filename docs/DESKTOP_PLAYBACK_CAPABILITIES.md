# Desktop playback capability audit

Audit environment: macOS, mpv/libmpv 0.41.0, FFmpeg 9.0.2. The current development bridge links against Homebrew libmpv and is not yet suitable for standalone distribution.

## Implemented

| Capability | Implementation | Notes |
|---|---|---|
| Local and network playback | libmpv client API | File, HTTP, HTTPS, HLS, DASH, and RTSP decoding remains dependent on the bundled libmpv/FFmpeg build. |
| Playback speed | mpv `speed` property | mpv enables pitch correction by default through `scaletempo2`. |
| Audio/video/subtitle selection | mpv track properties | Track availability is reported from the active file. |
| Subtitle and audio delay | mpv delay properties | Runtime changes are supported. |
| External subtitles | `sub-add` client command | User-authorized local subtitle files only. |
| Graphic equalizer | mpv `af` property with FFmpeg `equalizer` filters | Ten fixed bands from 31 Hz through 16 kHz. |
| Bass and treble shaping | Safely folded into low/high EQ bands | Values are clamped before filter construction. |
| Preamp attenuation | FFmpeg `volume` filter | Positive preamp is rejected. Positive EQ gain automatically adds equivalent attenuation headroom. |

The installed development build was checked with a headless generated sine source. The combined libavfilter equalizer and attenuation graph initialized successfully without an external media file.

## Not enabled

- ReplayGain needs an explicit shared mode setting and tagged-media tests before it is exposed.
- Output-device selection requires structured parsing and observation of mpv's `audio-device-list` property.
- Gapless behavior is engine- and format-dependent and has not been verified with the Vimivo queue.
- Crossfade requires overlapping decoded streams; a volume fade is not reported as crossfade.
- Normalization, stereo balance, and mono downmix are not exposed until their filter paths have dedicated validation.
- High-resolution and exclusive output depend on CoreAudio hardware and configuration. Vimivo does not claim bit-perfect playback.
- Screenshot, frame stepping, chapter navigation, A-B repeat, and video filters have documented mpv paths but are not marked implemented until wired through shared contracts and tested.

The implementation uses property/command arguments through libmpv. It never constructs a shell command from media paths or metadata.
