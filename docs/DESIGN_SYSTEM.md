# Vimivo Aurora Glass Design System

This document specifies the design tokens, color architecture, component standards, typography, and responsive layouts for Vimivo across Android, iOS, macOS, Windows, and Linux.

## 1. Brand Identity & Principles

- **Tagline**: Every Sound. Every Frame.
- **Philosophy**: Privacy-first, offline-first, minimal, media-centric, accessible, and elegant.
- **Visual Tone**: Midnight graphite and frosted pearl surfaces, aurora-teal focus, system sans-serif typography, restrained warm accents, generous targets, and media-first hierarchy.
- **Material Rule**: Glass is chrome, not content. Lists and grids remain solid; navigation, player docks, queue sheets, and compact overlays may use the lightweight glass primitive.

---

## 2. Color Palette & Themes

Vimivo supports four theme modes across all platforms:
1. **System Default**: Follows host operating system preferences (light/dark transitions automatically).
2. **Light Theme**: High readability, clean off-white surfaces, crisp contrast ratios meeting WCAG 2.2 AA.
3. **Dark Theme**: Deep charcoal and slate surfaces (`#121212` / `#1E1E1E`), reduced eye strain for playback environments.
4. **AMOLED Theme**: Pure black background (`#000000`), maximum battery savings on OLED displays, infinite contrast.

### Color Tokens

| Token | Light Theme | Dark Theme | AMOLED Theme |
|---|---|---|---|
| `primary` | `#006B66` | `#71DBD2` | `#71DBD2` |
| `onPrimary` | `#FFFFFF` | `#003366` | `#000000` |
| `surface` | `#F6FAF9` | `#0D1212` | `#000000` |
| `surfaceContainer` | `#F2F4F7` | `#1E1E1E` | `#121212` |
| `surfaceContainerHigh` | `#E8EDF2` | `#282828` | `#1C1C1C` |
| `onSurface` | `#1A1C1E` | `#E2E2E6` | `#FFFFFF` |
| `onSurfaceVariant` | `#535A62` | `#A0A5AA` | `#A0A5AA` |
| `error` | `#BA1A1A` | `#FFB4AB` | `#FFB4AB` |
| `accent` | `#007F78` | `#71DBD2` | `#71DBD2` |

Contrast ratios between `onSurface` / `onSurfaceVariant` and their corresponding surfaces strictly exceed the 4.5:1 standard for body text and 3:1 for large headers and interactive components.

---

## 3. Typography Hierarchy

Platform-native font stacks are used to respect user system settings, accessibility Dynamic Type / Font Scaling, and regional scripts (including Unicode and Bengali):

- **Headlines / Display**: Bold, expressive titles for screen headers (`headlineLarge`: 32sp / 2rem, `titleLarge`: 22sp / 1.375rem).
- **Titles / Media Items**: Semi-bold, single-line truncated with ellipsis (`titleMedium`: 16sp / 1rem).
- **Body / Metadata**: Regular weight for artist, album, and duration labels (`bodyMedium`: 14sp, `bodySmall`: 12sp).
- **Time Indicators / Mono**: Numeric tabular displays for elapsed/remaining times (`0:00` / `00:00:00`).

---

## 4. Responsive Layouts & Breakpoints

### Mobile (Android & iOS)
- **Compact (< 600dp / iPhone)**:
  - Bottom navigation bar (`AppBottomBar` / `MainTabView`) with 5 core tabs.
  - Collapsible mini-player anchored above navigation bar.
  - Full-screen or modal sheet expandable Now Playing experience.
- **Medium & Expanded (>= 600dp / iPad / Foldable / Tablet)**:
  - Persistent vertical navigation rail or sidebar (`AppRail` / `NavigationSplitView`).
  - Adaptive 2-to-4 column grid for album and artist browsing.
  - Side-by-side queue and playback controls.

### Desktop (macOS, Windows, Linux)
- **Window Width < 820dp (Compact Mode)**:
  - Slim navigation sidebar (140dp width) with concise destination labels.
  - Content padding adjusted to 16dp to prevent horizontal clipping.
- **Window Width >= 820dp (Standard Mode)**:
  - Full navigation sidebar (192dp width).
  - Generous content padding (28dp).
  - Multi-column card grids with auto-fitting layouts.

---

## 5. Reusable Components

1. **`NowPlayingCard` / `MiniPlayer`**:
   - Displays current media artwork, title, artist, status badge, seekbar slider, and playback controls.
   - Merged accessibility semantics for screen readers.
2. **`MediaCard` / `MediaRow`**:
   - Standardized 16:9 ratio for video cards and 1:1 ratio for audio albums/artists.
   - Integrated favorite toggle, queue menu actions, and secondary metadata display.
3. **`EmptyState` & `ErrorState`**:
   - Informative messaging with contextual icons, clear problem explanations, and actionable recovery buttons (e.g., "Add Folder", "Try Again").
4. **`DesktopSettingsPanel` / `SettingsScreen`**:
   - Comprehensive user preferences: Theme mode selection, library statistics, folder management, privacy commitments, and cache tools.

5. **`VimivoGlassSurface`**:
   - Shared lightweight material for navigation, floating player, controls, sheets, and popovers.
   - Uses stable alpha and elevation rather than live backdrop blur.
   - Resolves to an opaque, stronger-border surface for reduced transparency and high contrast.

## 6. Motion

`VimivoMotion` defines 120 ms feedback, 160 ms small transitions, 240 ms navigation, 300 ms sheets, and 360 ms player expansion. Decorative motion is bypassed under `LocalReducedMotion`; playback actions are never delayed by animation.

See [Glass material system](GLASS_MATERIAL_SYSTEM.md), [Motion system](MOTION_DESIGN_SYSTEM.md), and [UI redesign blueprint](UI_REDESIGN_BLUEPRINT.md).
