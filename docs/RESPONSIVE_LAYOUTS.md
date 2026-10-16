# Vimivo Responsive & Adaptive Layout Architecture

This document describes the multi-window, multi-device adaptive layout strategy for Vimivo across Android, iOS, iPadOS, macOS, Windows, and Linux.

---

## 1. Window Size Class Hierarchy

Rather than relying on brittle physical device checks or operating-system flags, Vimivo evaluates available viewport dimensions via `WindowSizeClass`:

| Size Class | Viewport Width | Typical Target Environments | Primary Navigation Strategy |
|---|---|---|---|
| **COMPACT** | `< 600 dp` | Portrait phones (Android / iPhone), narrow split-screen | Bottom `NavigationBar` / `MainTabView`, compact mini-player, full-screen player sheet |
| **MEDIUM** | `600 dp .. 839 dp` | Foldables (unfolded), landscape phones, small tablets, iPad Split View | Vertical `NavigationRail` or compact sidebar, 2-column media grid, docked player |
| **EXPANDED** | `>= 840 dp` | Desktop windows (macOS, Windows, Linux), full tablets (iPad Pro), desktop large displays | Persistent navigation sidebar (192dp+), multi-column grid (3-6 columns), dual-pane queue/player |

---

## 2. Adaptive Navigation Transitions

- **Compact Layouts**:
  - Root layout utilizes `Scaffold` with bottom `NavigationBar`.
  - Tab items expose explicit `Role.Tab` semantics with current selection state.
  - Media cards adapt to 1-to-2 column lazy grids or vertical lists.
- **Medium & Expanded Layouts**:
  - Automatically transitions to a left-anchored `NavigationRail` or persistent desktop sidebar without recreating playback engines or resetting navigation history.
  - Preserves user scroll position, search queries, active queue state, and current playback position.
- **Short Viewports / High DPI**:
  - The desktop main viewport employs `.verticalScroll(rememberScrollState())` to ensure that on small notebook screens or 200% DPI scaling, media cards, video playback controls, and diagnostics never clip or render off-screen.

---

## 3. Platform-Specific Layout Behavior

### Android
- Supports dynamic fold/unfold transitions, multi-window mode, and orientation changes.
- Player engine and playback queue controller live in singleton / service scope and are detached from Activity recreation.

### iOS & iPadOS
- Respects safe area insets on iPhone notches, Dynamic Island, and iPad multitasking Home indicators.
- Supports iPad Stage Manager and Split View seamlessly.

### Desktop (macOS, Windows, Linux)
- Resizable desktop windows from minimum 640x480 up to 4K/8K displays.
- Persistent sidebar with instant tab navigation.
- Video playback overlay controls with auto-hide timeout and non-blocking pointer discovery.
- Technical diagnostics panel provides real-time codec, renderer, dropped frames, FPS, drift, and cache status without layout shifts.
