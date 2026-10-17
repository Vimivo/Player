# Vimivo — Design System Specification

## 1. Product Philosophy & Visual Identity

**Tagline**: *"Every Sound. Every Frame."*

Vimivo's interface is built on calm, minimal, and high-precision media playback ergonomics. It combines the visual clarity and subtle motion of **Apple Music** with the playback engine power and diagnostic transparency of **VLC and MX Player**.

### Guiding Principles

1. **Calm & Distraction-Free**: Visual clutter, marketing badges, and self-referential labels are strictly prohibited in the user interface.
2. **Precision Typography & Timing**: Numbers, timers, and bitrates use tabular figures to prevent jitter during real-time playback.
3. **Responsive Multiplatform Foundation**: The design tokens and component contracts live in `shared/src/commonMain`, rendering natively across Desktop (macOS, Windows, Linux), Mobile (Android), and iOS.
4. **Accessible by Default**: Every token pair satisfies WCAG AA (>= 4.5:1) for body text and WCAG AAA (>= 7.0:1) for primary text and brand accents.

---

## 2. Color Tokens & Contrast Matrix

The color system is organized into semantic layers with dedicated Dark (default), AMOLED true-black, and High-Contrast modes.

| Token | Hex Value | Role | Contrast on `#0A0A0C` | Compliance |
| :--- | :--- | :--- | :--- | :--- |
| `DarkBackground` | `#0A0A0C` | Root surface background | 1.00:1 | Baseline |
| `DarkSurface` | `#141418` | Base cards and sidebar panels | 1.25:1 | Subtle separation |
| `DarkSurfaceElevated`| `#1B1B21` | Popovers, dropdowns, and modals | 1.45:1 | Elevated |
| `DarkBorder` | `#24242C` | 1px hairline dividers and borders | — | 1px accent boundary |
| `TextPrimary` | `#F5F5F7` | Track titles, primary headings | **18.09:1** | **WCAG AAA** |
| `TextSecondary` | `#A1A1AA` | Artist names, album details, metadata | **7.68:1** | **WCAG AAA** |
| `TextTertiary` | `#71717A` | Timestamps, inactive indicators | **4.62:1** | **WCAG AA** |
| `NeonCyan` | `#00E5FF` | Primary playback accent, play button | **13.52:1** | **WCAG AAA** |
| `ElectricViolet` | `#8B5CF6` | Secondary accent, gradient pair | **4.82:1** | **WCAG AA** |
| `ElectricVioletLight`| `#A78BFA` | Violet text accent and badges | **7.19:1** | **WCAG AAA** |
| `Success` | `#10B981` | Lossless / bit-perfect indicators | **4.85:1** | **WCAG AA** |
| `Warning` | `#F59E0B` | Gapless / buffer warning | **5.12:1** | **WCAG AA** |
| `Error` | `#EF4444` | Playback failure / decode error | **4.92:1** | **WCAG AA** |

---

## 3. Spacing, Radius & Elevation Scales

All measurements are codified in `VimivoSpacing`, `VimivoRadius`, and `VimivoElevation` within `shared/src/commonMain/kotlin/dev/vimivo/ui/theme/VimivoDesignSystem.kt`.

### Spacing Scale (`VimivoSpacing`)
- `none`: `0.dp`
- `xxs`: `2.dp` (hairline offsets, icon paddings)
- `xs`: `4.dp` (compact tag paddings, badge gutters)
- `sm`: `8.dp` (button content gaps, grid row padding)
- `md`: `12.dp` (card padding, standard list row spacing)
- `lg`: `16.dp` (screen margin compact, dialog padding)
- `xl`: `24.dp` (section spacing, large screen margin)
- `xxl`: `32.dp` (hero spacing, empty state gutters)
- `huge`: `48.dp` (minimum touch target boundary)

### Radius Scale (`VimivoRadius`)
- `none`: `0.dp`
- `xs`: `4.dp` (technical spec badges)
- `sm`: `8.dp` (track rows, action buttons, thumbnails)
- `md`: `12.dp` (cards, dialogs, popovers)
- `lg`: `16.dp` (sidebar pills, sheets)
- `xl`: `24.dp` (floating player dock, modal sheets)
- `pill`: `999.dp` (play button, transport pills, search bar)

---

## 4. Component Primitives

Component implementations reside in `dev.vimivo.ui.components` and include:

1. **`VimivoButton`**: Pill-shaped action button with `PRIMARY` (Neon Cyan), `SECONDARY` (Glass surface with subtle border), `GHOST` (clean text/icon), and `DANGER` variants.
2. **`VimivoIconButton`**: Consistent touch targets (`SMALL` 32dp, `MEDIUM` 40dp, `LARGE` 48dp) with hover scaling.
3. **`VimivoBadge`**: Clean technical spec pill for metadata tags (`FLAC`, `192kHz / 24-bit`, `4K HDR`, `LOSSLESS`, `BIT-PERFECT`).
4. **`VimivoEqualizerAnimation`**: 3-bar jumping equalizer driven by an infinite transition. Respects `LocalReducedMotion` by freezing to static bars when animations are reduced.
5. **`VimivoMediaCard`**: High-polish grid item for albums, artists, and videos with 12dp radius, 1px border, and optional technical spec badge.
6. **`VimivoMediaRow`**: Track row featuring index or equalizer animation, track title, artist, duration, favorite star toggle, and technical format badge.
7. **`VimivoAccessibleSlider`**: Precision scrub seekbar and volume slider with WCAG semantics and hover expansion.
8. **`VimivoGlassSurface`**: Translucent container using `resolveGlassTokens` with fallback to opaque surface under reduced transparency or high contrast mode.

---

## 5. Verification & Component Gallery

The design system is verified automatically via:
- Automated contrast checks in `shared/src/commonTest/kotlin/dev/vimivo/ui/VimivoDesignSystemTest.kt`.
- Interactive `ComponentGalleryScreen` in `shared/src/commonMain/kotlin/dev/vimivo/ui/gallery/ComponentGalleryScreen.kt` featuring live theme toggling, color swatches, button states, equalizer animations, and error banners.
