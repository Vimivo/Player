# Engineering quality report

Date: 2026-10-13

## Scope

The pass reviewed MediaStore/SAF indexing, Room persistence and migrations, Media3/LibVLC playback and lifecycle ownership, MediaSession controls, audio focus/noisy routing, playlists and organization, subtitle/track handling, network streams, settings, local backup, permissions, URI handling, native packaging, and Compose presentation paths.

## Fixed findings

- Disabled Android platform backup. The product promises local-only personal data, so automatic OS/cloud backup of databases and preferences was inappropriate; users now control export exclusively through the documented SAF backup.
- Bounded pre-Android-10 artwork and video-frame decoding. The previous fallback could decode full-resolution embedded artwork or 4K frames before caching, producing avoidable allocation spikes on low-memory devices.
- Added ordered stream-history persistence instead of unordered set restoration.
- Added bounded reconnect restricted to transport failures, explicit server/manifest errors, and credential-safe URL validation.
- Added HLS and DASH Media3 modules instead of relying on progressive-source inference.
- Added transactional history cleanup before unavailable media is permanently removed.
- Applied persistent decoder, queue restoration, default shuffle/repeat, video gesture/seek/speed/aspect/rotation, theme, contrast, motion, and accessibility preferences.

## Performance findings

- Media scanning and metadata extraction run on IO dispatchers in bounded batches and support coroutine cancellation. MediaStore generation tokens avoid unchanged full scans.
- Room list queries are paged and indexed; search uses prefix matching so indexes remain useful for large libraries.
- Artwork has a 16 MiB memory cache and 256 px decode target. The legacy decode path is now sampled before allocation.
- Playback position UI updates at 250 ms. Higher-frequency frame-driven recomposition was not found.
- One service owns one active engine; engine switches close the prior engine, and both Media3 and LibVLC release players, surfaces, listeners, audio focus, and coroutine scopes.
- No benchmark-capable physical device was available, so cold-start time, playback CPU, battery drain, video startup latency, 4K throughput, and high-refresh rendering have not been numerically measured.

## Security and privacy findings

- Storage access remains MediaStore- and SAF-scoped; `MANAGE_EXTERNAL_STORAGE` is absent.
- Persisted SAF permissions are used only for selected documents and trees.
- URLs are sent only to the selected origin. Embedded credentials are rejected and no analytics or sensitive logging was found.
- Arbitrary user-selected LAN HTTP hosts cannot be expressed as a static Android domain allowlist. Cleartext remains app-enabled but playback requires explicit user action; HTTPS is preferred. This is a documented residual risk.
- Backups contain references and personal organization data but never media bytes, credentials, logs, or grants.
- Native packaging targets arm64-v8a, armeabi-v7a, and x86_64. Earlier ELF and zip alignment inspection found 16 KiB-compatible arm64 load alignment; runtime testing on a 16 KiB device remains outstanding.

## Prepared compatibility coverage

- API levels: minimum 26, current target 36; instrumented coverage should run on API 26, 29, 33, and 36.
- Layouts: phone portrait/landscape, compact phone, 7–8 inch tablet, 10+ inch tablet, font scales 1.0/1.3/2.0, and mdpi/xhdpi/xxxhdpi.
- Runtime: low-memory process recreation, 60/90/120 Hz displays, Bluetooth/wired route changes, notification controls, and background service lifecycle.
- Media: MP3, FLAC, AAC, WAV, MP4, MKV, AVI, HEVC, VP9, AV1, multi-audio, embedded/external subtitles, 1080p, supported 4K, HLS, and DASH. Use generated, owned, or officially authorized fixtures only.

See `MEDIA_TEST_MATRIX.md`, `NETWORK_STREAMING.md`, and `PLAYBACK_CAPABILITIES.md` for scenario-level expectations.

## Remaining known issues

- Device-only playback, notification, audio-route, PiP, power, 4K, HDR, hardware-decoder, 16 KiB runtime, and native-crash scenarios require physical devices or configured emulators.
- LibVLC reports a coarse error for some native demux/network failures because its Android event API does not always expose the underlying HTTP status.
- Extensionless adaptive manifests require correct server metadata; `.m3u8` and `.mpd` URLs are deterministic.
- Generic MediaStore/SAF metadata writing remains intentionally disabled because safe transactional write support cannot be guaranteed for arbitrary providers.
- Bengali resources cover the complete new settings experience; legacy feature screens still contain English UI literals and need incremental extraction before claiming whole-app Bengali localization.

## Release blockers

- Automated validation completed on 2026-10-13: debug and release unit tests passed, Android instrumentation sources compiled, Detekt passed, debug/release Android lint passed, debug APK assembly passed, and the minified/resource-shrunk release AAB built successfully. No Android device was connected, so instrumentation tests were compiled but not executed.
- The generated release AAB is intentionally unsigned because no protected signing credentials were supplied; it is not uploadable as-is.
- The default application ID and Vimivo working brand require final owner approval before the first Play application is created.
- LibVLC LGPL corresponding-source, relinking, and full-notice delivery must be completed and legally reviewed.
- Release should remain blocked until smoke testing is completed on at least API 26 and API 36, including background playback, notification controls, local scanning, restore from a real SAF provider, HLS/DASH, and a 16 KiB arm64 environment.
