# Vimivo Aurora Glass UI redesign blueprint

Status: Phase 1 in progress. Verified against Git `bd06079be0aec363c037447d84256eaa2a0f5a6f` on 2026-10-16.

## Product direction

Aurora Glass is an original, local-first media interface. It uses an aurora-teal accent, system typography, artwork-led player surfaces, restrained translucent chrome, and solid content lists. It does not add an online catalog, analytics, accounts, DRM workarounds, or a second playback controller.

The visual references described in the assignment were not available as image files. Their written qualities informed hierarchy and layout; no visual comparison or pixel matching was performed.

## Verified current state

- Android has real playback, MediaSession, mini-player, Now Playing, queue, advanced video, local library, and direct URL/WebDAV surfaces.
- Desktop has real libmpv playback, native render surfaces, persistent library features, and stable queue identities.
- iOS has AVPlayer, basic library/player/queue screens, MPNowPlayingInfoCenter, MPRemoteCommandCenter, and a PiP foundation.
- Shared Compose navigation remains an empty-state shell and is not the production Android/Desktop UI.

## Confirmed gaps

1. Desktop lacks a contextual top toolbar, fixed bottom player dock, toggleable right queue, responsive video overlay, and grid/list switch.
2. iOS queue mutations bypass engine commands and are not persisted; WidgetKit/App Intents and UI-test targets do not exist.
3. iOS settings expose gapless/hardware toggles that are not wired to the engine.
4. Android needs reusable Aurora materials across navigation, queue, Now Playing, search, and settings.
5. Reduced transparency is not persisted across platform settings.
6. macOS and Windows system media-control adapters are absent; Linux MPRIS is the only desktop system integration.
7. Existing accessibility and performance documents overstate verification in places; desktop tests do not exercise a live Compose semantics tree.

## Execution phases

1. **Foundation:** semantic colors, motion, breakpoints, system typography, lightweight glass and truthful documentation.
2. **Shells:** adaptive Android navigation, desktop toolbar/sidebar/content/queue/player regions, and native SwiftUI materials/navigation.
3. **Audio:** artwork-led Now Playing, authoritative mini-player, safe queue commands, lyrics follow mode and accessible reorder.
4. **Video:** near-black canvas, capability-gated overlays/options, fullscreen, real PiP and visible gesture alternatives.
5. **Library/settings:** adaptive list/grid, stable keys, search/sort/filter and persisted accessibility/effect preferences.
6. **Native surfaces:** hardened system Now Playing, real platform widgets, macOS/Windows controls and preserved Linux MPRIS.
7. **Verification:** real UI tests, deterministic screenshots, large-text/reduced-effects matrices and measured performance evidence.

## Completion rule

A surface is complete only when it uses real state, every enabled action reaches the authoritative engine/repository, accessibility alternatives exist, reduced effects work, automated checks pass, and any required native runtime exercise is recorded.
