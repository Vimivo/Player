# Vimivo Keyboard Shortcut Reference

This document catalogs the complete keyboard shortcut mapping for Vimivo across macOS, Windows, and Linux desktop targets.

---

## 1. Global & MenuBar Shortcuts

| Action | macOS Shortcut | Windows & Linux Shortcut | Scope |
|---|---|---|---|
| **Play / Pause Toggle** | `Space` | `Space` | Global when player is active |
| **Seek Backward (5s)** | `Left Arrow` | `Left Arrow` | When media is loaded |
| **Seek Backward / Forward (10s)** | `J` / `L` | `J` / `L` | When media is loaded |
| **Play / Pause (alternate)** | `K` | `K` | When media is loaded |
| **Seek Forward (5s)** | `Right Arrow` | `Right Arrow` | When media is loaded |
| **Seek Backward (30s)** | `Shift + Left Arrow` | `Shift + Left Arrow` | Jump backward in media |
| **Seek Forward (30s)** | `Shift + Right Arrow` | `Shift + Right Arrow` | Jump forward in media |
| **Volume Up (+5%)** | `Up Arrow` | `Up Arrow` | Audio & video playback |
| **Volume Down (-5%)** | `Down Arrow` | `Down Arrow` | Audio & video playback |
| **Mute / Unmute** | `M` | `M` | Audio & video playback |
| **Previous Track** | `Cmd + Left Arrow` | `Ctrl + Left Arrow` | Queue navigation |
| **Next Track** | `Cmd + Right Arrow` | `Ctrl + Right Arrow` | Queue navigation |
| **Toggle Shuffle** | `S` | `S` | Queue modifier |
| **Cycle Repeat Mode** | `R` | `R` | Off -> One -> All |
| **Playback Speed Up** | `]` | `]` | Next step on 0.25x…4x ladder |
| **Playback Speed Down** | `[` | `[` | Previous step (min 0.25x) |
| **Reset Playback Speed** | `Backspace` or `=` | `Backspace` or `=` | 1.0x normal |
| **Toggle Fullscreen** | `F` or double-click video | `F` or double-click video | Video playback |
| **Toggle Fullscreen (menu)** | `Cmd + Shift + F` | `Ctrl + Shift + F` | View menu |
| **Exit Fullscreen / Dismiss** | `Escape` | `Escape` | Fullscreen, dialogs, overlays |
| **Open Network Stream** | `Cmd + U` | `Ctrl + U` | File menu |
| **Media Information** | `Cmd + I` or `I` | `Ctrl + I` or `I` | Codec and track details |
| **Open File Dialog** | `Cmd + O` | `Ctrl + O` | System file picker |
| **Add Media Folder** | `Cmd + Shift + O` | `Ctrl + Shift + O` | Directory picker |
| **Open Settings** | `Cmd + ,` | `Ctrl + ,` | Settings navigation |
| **Close Application** | `Cmd + Q` | `Alt + F4` / `Ctrl + Q` | Clean application shutdown |

---

## 2. Video, Audio & Subtitle Shortcuts

| Action | Key | Notes |
|---|---|---|
| **Next Frame** | `E` | Pauses on the next frame |
| **Previous Frame** | `Shift + E` | Pauses on the previous frame |
| **Take Snapshot** | `Shift + S` | Saved to `~/Pictures/Vimivo` |
| **A-B Loop** | `Shift + L` | Press for A, again for B, again to clear |
| **Cycle Aspect Ratio** | `A` | Original → Fit → Fill → 16:9 → 4:3 → 21:9 |
| **Toggle Deinterlacing** | `D` | |
| **Cycle Audio Track** | `B` | |
| **Cycle Subtitle Track** | `V` | Includes "off" |
| **Subtitle Delay −/+ 100 ms** | `G` / `H` | |
| **Audio Delay −/+ 50 ms** | `Shift + G` / `Shift + H` | |

Single-key shortcuts only fire when no text field has focus. Menu shortcuts use `Cmd` on macOS and `Ctrl` on Windows and Linux; Play/Pause in the menu stays on `Ctrl + Space` because `Cmd + Space` is reserved by macOS.

---

## 3. Accessible Focus & Traversal Keys

- **Tab**: Move keyboard focus forward to the next interactive component (buttons, inputs, sliders, table rows).
- **Shift + Tab**: Move keyboard focus backward to the preceding interactive component.
- **Enter / Space**: Activate the currently focused button, link, checkbox, or switch.
- **Up / Down Arrow**: Select adjacent item in lists, adjust sliders, or traverse context menu items.
- **Escape**: Close open popups, context menus, bottom sheets, or search dialogs, returning focus to the trigger element.
