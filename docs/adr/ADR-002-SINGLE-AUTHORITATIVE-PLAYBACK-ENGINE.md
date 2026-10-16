# ADR-002: Single Authoritative Playback Engine Architecture

## Status
Accepted

## Context
Multimedia applications frequently suffer from state desynchronization, race conditions, audio popping, and duplicated history events when multiple independent player state holders or dual playback queues exist. Vimivo requires seamless playback transitions, unified queueing, repeat/shuffle modes, A-B repeat looping, chapter navigation, and video transformations across all five target operating systems.

## Decision
1. **Single Authoritative PlayerEngine**: Exactly one `PlayerEngine` instance is active at any time. UI components and platform integrations observe a single immutable `StateFlow<PlaybackState>`.
2. **Capability-Aware Engine Contracts**: Instead of broad interfaces that force every platform to fabricate unsupported controls, `PlayerCapabilities` explicitly declares what the underlying engine supports (`localVideo`, `hdr`, `pitchCorrection`, `equalizer`, `replayGain`, `gapless`, `chapters`, `abRepeat`, `videoAdjustments`, `screenshots`).
3. **Queue State Machine Ownership**: `PlaybackQueueController` exclusively owns queue ordering, occurrence identity (`QueueItem.queueId`), duplicate item handling, and repeat/shuffle logic. It delegates decoding to `PlayerEngine` without maintaining a separate decoder pipeline.
4. **Non-Blocking Callback Synchronization**: Native engine callbacks (from libmpv event loops or Media3 player listeners) are dispatched into application-owned coroutine scopes with `Mutex` synchronization, preventing race conditions or main-thread blocking.

## Consequences & Tradeoffs
- **Positive**: Guaranteed deterministic state transitions. No duplicate completion events or stale playback updates. Safe paused queue restoration without unsolicited autoplay.
- **Negative**: True crossfade is disabled by default because genuine mixing requires dual active decoders which would compromise single-engine resource guarantees.

## Validation
Verified by `PlaybackQueueControllerTest` (9 tests), `MpvAdvancedPlaybackTest` (24 tests), and `DesktopReliabilityTest` (15x sequential instantiate/teardown cycles without leaks).
