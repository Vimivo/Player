# Vimivo Five-Platform Accessibility Matrix

This matrix provides an evidence-backed status of accessibility capabilities across all 5 supported operating systems.

---

## 1. Five-Platform Capability Matrix

| Accessibility Dimension | Android | iOS / iPadOS | macOS Desktop | Windows Desktop | Linux Desktop |
|---|---|---|---|---|---|
| **Screen Reader Technology** | Google TalkBack / Switch Access | Apple VoiceOver | Apple VoiceOver | NVDA / JAWS via Java Access Bridge | Native Orca AT-SPI (*Limited*) |
| **Toolkit Semantic Bridge** | Android Compose Semantics -> AccessibilityNodeInfo | SwiftUI / AVKit native accessibility properties | Compose Desktop -> macOS NSAccessibility | JVM `jdk.accessibility` -> Java Access Bridge | Skia canvas direct rendering (*No default AT-SPI bridge*) |
| **Keyboard Navigation** | Hardware keyboard Tab/Arrows | iPad Smart Keyboard | Full Tab, Shift+Tab, Enter, Space, Arrows | Full Tab, Shift+Tab, Enter, Space, Arrows | Full Tab, Shift+Tab, Enter, Space, Arrows |
| **Focus Indicator Visibility** | High-contrast focus outline | Native focus ring | 2dp high-contrast focus borders | 2dp high-contrast focus borders | 2dp high-contrast focus borders |
| **Accessible Seekbars** | Progress range + percentage announcements | Adjustable slider with value reading | Semantic slider with formatted "m:ss of m:ss" | Semantic slider with formatted "m:ss of m:ss" | Keyboard-navigable with incremental stepping |
| **Drag-and-Drop Alternatives** | Explicit Move Up/Down buttons | Explicit Move Up/Down actions | Dedicated Move Up/Down buttons + context menu | Dedicated Move Up/Down buttons + context menu | Dedicated Move Up/Down buttons + context menu |
| **Dynamic Text & Font Scaling** | Android system font scaling up to 200% | iOS Dynamic Type & Accessibility Sizes | OS display scaling (100% .. 300% Retina) | Windows DPI scaling (100% .. 250%) | Desktop scale factors (1x .. 2x) |
| **High Contrast Mode** | High contrast text / color inversion | High Contrast / Classic Invert | macOS Increase Contrast scheme | Windows High Contrast Black / White | Custom high-contrast light & dark themes (>= 7:1) |
| **Reduce Motion** | Window & transition animation scale = 0 | iOS Reduce Motion preference | macOS Reduce Motion setting | Windows Show animations in Windows = off | Disables slide transitions, eliminates continuous animation |
| **Touch Target Minimum** | >= 48x48 dp | >= 44x44 pt | >= 32x32 pt (pointer) | >= 32x32 pt (pointer) | >= 32x32 pt (pointer) |
| **Unicode & Regional Scripts** | Bengali, mixed scripts, emoji | Bengali, mixed scripts, emoji | Bengali, mixed scripts, emoji | Bengali, mixed scripts, emoji | Bengali, mixed scripts, emoji |

---

## 2. Truthful Reporting on Linux Compose Desktop Accessibility

### Technical Reality
JetBrains Compose Multiplatform for Desktop renders directly onto a Skia hardware-accelerated canvas. On Linux, the open-source toolkit does not yet include a completed out-of-the-box bridge between Compose semantic nodes and Linux AT-SPI (Assistive Technology Service Provider Interface) D-Bus interfaces. Consequently, Linux screen readers such as **Orca** cannot currently inspect the semantic tree of Compose Desktop applications without a specialized external accessibility wrapper.

### Vimivo Linux Accessibility Guarantee
Vimivo refuses to make false marketing claims regarding universal screen reader support on Linux. Instead, Vimivo implements all platform-independent accessibility guarantees:
1. **Complete Keyboard Traversal**: 100% of application destinations, playback actions, queue controls, sliders, and dialogs are navigable via Tab, Shift+Tab, Arrow keys, Space, and Enter.
2. **Visible Focus**: Clear, high-contrast focus rings ensure users with motor or low-vision needs always know where focus resides.
3. **Non-Drag Alternatives**: Reordering queues, editing playlists, and adjusting settings do not require precision mouse dragging.
4. **WCAG AAA Contrast**: Dedicated High Contrast Light and Dark modes provide >= 7.0:1 contrast ratios.
5. **No Gesture-Only Requirements**: Every playback action has a dedicated, clickable, and keyboard-activatable button.
