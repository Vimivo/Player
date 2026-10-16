# Vimivo UX & Accessibility Release Gates

This document formalizes the release gating criteria for Milestones 47 and 48.

---

## 1. Release Gate Criteria

### Milestone 47: Final UX Evolution
- [x] **Complete Screen Inventory**: 100% of screens audited with route, loading, empty, and error states.
- [x] **Consolidated Design Tokens**: Unified spacing (`VimivoSpacing`), corner radii (`VimivoRadius`), elevations (`VimivoElevation`), icon sizing (`VimivoIconSize`), and semantic colors (`VimivoColors`).
- [x] **Responsive Viewports**: Tested across compact (<600dp), medium (600..839dp), and expanded (>=840dp) window size classes.
- [x] **Connected Controls**: Zero dead buttons or decorative mock controls; all transport, volume, mute, speed, repeat, shuffle, and diagnostics controls communicate with live engines.
- [x] **Safe Destructive Operations**: Explicit confirmation dialogs for playlist deletion, media removal, tag writing, and history clearing.

### Milestone 48: Accessibility & Hardening
- [x] **Compose Semantics**: Every interactive icon, button, slider, and tab exposes descriptive contentDescription, role, and stateDescription.
- [x] **Platform Screen Readers**: Verified with TalkBack (Android), VoiceOver (iOS/iPadOS/macOS), and Java Access Bridge (Windows).
- [x] **Honest Linux Reporting**: Linux Compose Desktop's lack of native AT-SPI bridge is truthfully disclosed without false claims.
- [x] **Keyboard Navigation**: 100% of core actions and destinations accessible via Tab, Shift+Tab, Arrows, Space, and Enter with visible focus rings.
- [x] **Non-Drag Alternatives**: Queue and playlist items feature accessible "Move Up" and "Move Down" controls.
- [x] **WCAG AA / AAA Contrast**: Standard themes exceed 4.5:1 (WCAG AA); high-contrast themes exceed 7.0:1 (WCAG AAA).
- [x] **Reduce Motion**: Respects OS animation preferences by bypassing layout transitions and animated progress bars.
- [x] **Bengali & Unicode**: Tested complex ligatures, mixed-script strings, and RTL metadata.
- [x] **Automated Regression Suite**: Detekt, unit tests, and compilation pass with zero errors.
