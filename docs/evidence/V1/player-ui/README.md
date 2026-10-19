# Desktop player UI: Steps 2–4 (2026-10-19)

Captured on macOS (Apple M4) with `./gradlew :desktopApp:run --args="--smoke-test --ui-capture <dir> testmedia/pack/colorbars.mp4"`:
the real app drives each surface and saves what the screen shows (its own windows; no Screen Recording permission).

| File | What it shows |
|---|---|
| `01-windowed.png` | Dark title bar with the file's proxy icon and name; 48 dp control bar ⏮ ⏯ ⏭ ■ ≡ · timeline · `00:01 / 00:20` · volume · ⚙ · ⛶ |
| `02-timeline-hover.png` | See "Not captured" |
| `03…07-panel-*.png` | Side panel tabs Video, Audio, Subtitles, Sync, Bookmarks: 120 dp labels, dropdowns, segmented choices, steppers, sliders with values; no per-letter wrapping |
| `08-panel-narrow.png` | The panel in a 760 px window |
| `09-fullscreen-controller.png` | Fullscreen floating controller: time · file · total, thin timeline with thumb, volume left, transport centre, ⚙ and exit right |
| `10-fullscreen-idle.png` | The controller (and cursor) hidden after 2 s without mouse movement |
| `11-windowed-auto-hidden.png` | Optional View ▸ Auto-hide Controls in Window (off by default): the bar hides while the pointer rests |

Tests: `PlaybackClockTest` (shared: smooth playhead, blending, seeks), `TooltipAnchorTest`, `PlayerControlsVisibilityTest`,
`MpvThumbnailerTest`
(live libmpv preview frames), the existing desktop suite and the smoke gate (7/7, pixel-checked).

**Not captured here:**

- Timeline hover tooltip and frame preview: macOS lets `Robot` move the pointer only with Accessibility permission,
  which this machine's terminal does not have. The placement is unit-tested and the preview decoding is live-tested.
- The menu bar (macOS draws it outside the app's windows).
- Windows and Linux title bars (dark mode via DWM and `_GTK_THEME_VARIANT`): check on those systems.

**Known differences from the spec:**

- The title is left-aligned: macOS 26 left-aligns window titles by design (an expanded toolbar style did not change
  it), so Vimivo follows the system rather than the spec's "centred".
- The glass is a 72 % tint without background blur. Real blur needs `NSVisualEffectView` / DWM acrylic.
