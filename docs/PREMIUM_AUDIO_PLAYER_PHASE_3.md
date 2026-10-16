# Premium Audio Player Phase 3

Date: 2026-10-16

## Implemented

- Immersive Android Now Playing surface with large local artwork, a cached artwork-derived ambient gradient,
  readable title/artist/album hierarchy, and bounded Aurora Glass transport controls.
- Precise seek control with elapsed and remaining time, real volume control, animated play/pause, previous/next,
  shuffle, repeat, favorite, queue, and existing playback options routed to the authoritative Media3 player.
- Floating mini-player now includes artwork, metadata, play/pause, next, and progress without introducing another
  player state.
- Queue now includes a Now Playing summary, artwork thumbnails, current-track indication, accessible move up/down
  alternatives, remove operations, stable occurrence keys, and bottom transport controls.
- Local LRC lyrics use a bounded lazy viewport, active-line highlighting, synchronized seeking, automatic scrolling,
  reduced-motion behavior, and Unicode/Bengali-safe Compose text.
- Artwork dominant-color extraction is cached separately in a bounded 128-entry cache. No per-frame blur or palette
  extraction is performed.
- Shared component tokens define artwork, transport, queue-thumbnail, and lyrics viewport dimensions.

## Architecture

The redesign uses the existing `PlaybackViewModel`, connected Media3 `Player`, `PlaybackController`, local media
repositories, `LyricsRepository`, and queue. No second player, synthetic progress state, online lyrics, or online
catalog was added.

## Verification and limitations

- Shared design-system tests cover accessible transport sizing and bounded lyrics viewport dimensions.
- Existing `PlaybackQueueControllerTest` covers queue order and playback coordination; existing LRC parser tests cover
  synchronized local lyrics including Unicode/Bengali input.
- Android compilation, unit tests, Detekt, and debug assembly are required gates for this phase.
- No Android emulator or physical device was connected, so touch interaction, TalkBack, visible audio playback, and
  screenshot instrumentation are not claimed as run.
- Desktop native playback can be exercised with the repository WAV fixture on this macOS host, but it does not render
  the Android immersive player UI.
