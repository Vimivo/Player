# Vimivo Bug Reporting Guide & Reproducibility Protocol

## 1. Introduction

Vimivo relies on reproducible, high-signal bug reports from community members and internal testers. Because Vimivo is an offline-first and privacy-focused media player, reports must never include sensitive personal data, copyright-encumbered media files, or private directory structures.

---

## 2. In-App Bug Reporting Workflow

1. **Access Entry Points**:
   - **Settings** -> **Help & Community Feedback**
   - **Playback Error Banner** -> **Report Playback Issue**
   - **Keyboard Shortcut**: `Ctrl+Shift+F` (or `Cmd+Shift+F` on macOS)
2. **Select Report Type**:
   - `Bug Report`: For UI glitches, unexpected crashes, queue errors, or broken buttons.
   - `Playback / Codec Issue`: For audio/video decoding issues, frame drops, or subtitle sync failures.
   - `Accessibility Issue`: For screen-reader issues, focus traps, keyboard navigation barriers.
3. **Fill Form Fields**:
   - **Title**: Short, descriptive summary (e.g., `Playback controls overlap with ASS subtitles on macOS`).
   - **Category**: Select the impacted functional area.
   - **Description**: What you were doing, what you expected, and what actually occurred.
   - **Steps to Reproduce**: Step-by-step numbered instructions starting from application launch.
   - **Frequency**: Every time (100%), Often, Sometimes, or Happened once.
4. **Review Technical Context**:
   - Check the **Include sanitized diagnostic excerpt** toggle to attach error logs and codec information.
   - Click **Preview Report** to inspect the exact Markdown payload before saving or copying.
5. **Export or Submit**:
   - **Copy to Clipboard**: Copies the sanitized report for easy pasting.
   - **Export File**: Saves the report to a `.md` file in a directory of your choice.
   - **Open GitHub Issues**: Opens the pre-filled issue form in your browser. You can edit further before clicking "Submit new issue".
   - **Save Draft**: Saves your work locally so you can return to it later.

---

## 3. High-Signal Reproduction Checklist

A high-signal bug report contains:
- [x] **Exact App Version & Build**: e.g., `1.0.0-rc.1 (build 49f80f5)`
- [x] **Platform & OS Version**: e.g., `macOS Sequoia 15.0 (arm64)`, `Android 15 (API 35)`
- [x] **Reproduction Steps**: Step-by-step instructions that allow another engineer to witness the issue.
- [x] **Expected vs Actual Behavior**: Clear statement of contrast.
- [x] **Media Fixture Details** (if media-related): Container format, audio codec, video codec, bitrate, resolution, subtitle format. Do NOT attach private media. Use public domain or synthetic sample files (e.g. Big Buck Bunny, Tears of Steel, or generated FFmpeg tone/color test files).
- [x] **Sanitized Logs**: Ensure all paths are redacted by `SafeLogger`.

---

## 4. Privacy Pledge

When submitting bug reports:
- **Never submit**: Passwords, auth tokens, cookies, private streaming keys, full user home directory paths, or unredacted filesystem trees.
- **Never submit**: Private family media or copyrighted audio/video files.
- All in-app reports are sanitized automatically by `SafeLogger` before preview or export.
