# Vimivo Community Feedback & In-App Reporting Architecture

## 1. Architectural Philosophy & Privacy Principles

Vimivo's feedback architecture is designed around three strict principles:
1. **100% Offline-First Composition**: Users can compose, edit, review, and persist feedback drafts without an active internet connection.
2. **Zero Automatic Network Actions**: Vimivo contains zero background upload daemons, zero analytics beacons, zero telemetry pings, and zero automatic remote issue submission. The user retains complete authority over when and where feedback is shared.
3. **Local Sanitization & Data Safety**: Diagnostic logs, error messages, and media metadata are processed locally through `SafeLogger` to strip credentials, filesystem paths, personal identifiers, and private URLs before any preview or export.

---

## 2. Shared Domain Models (`dev.vimivo.core.feedback`)

The portable feedback data models reside in `:shared/src/commonMain/kotlin/dev/vimivo/core/feedback/` to guarantee cross-platform consistency across Android, iOS/iPadOS, macOS, Windows, and Linux.

### Key Components

- **`FeedbackType`**:
  - `BUG_REPORT`: Unexpected errors, crashes, broken functionality, or behavioral discrepancies.
  - `FEATURE_REQUEST`: New capability suggestions, usability improvements, or format support requests.
  - `PLAYBACK_ISSUE`: Audio/video playback failure, codec incompatibility, hardware decoding freeze, or subtitle desynchronization.
  - `ACCESSIBILITY_ISSUE`: Screen-reader compatibility barriers, focus traps, touch target sizing, high-contrast themes.

- **`FeedbackCategory`**:
  - `PLAYBACK`, `USER_INTERFACE`, `LIBRARY_MANAGEMENT`, `PLAYLISTS_AND_QUEUE`, `PERFORMANCE`, `CRASH_OR_FREEZE`, `ACCESSIBILITY`, `INSTALLATION_OR_UPDATE`, `DATA_INTEGRITY`, `OTHER`.

- **`IssueFrequency`**:
  - `ALWAYS` (100% reproducible), `OFTEN` (frequent), `SOMETIMES` (intermittent), `ONCE` (isolated incident).

- **`MediaPlaybackContext`**:
  - Safe, technical-only media descriptors: container format (e.g. `MKV`, `MP4`, `FLAC`), audio codec, video codec, resolution, frame rate, subtitle format, hardware acceleration status, and active player engine.
  - Explicitly excludes file paths, personal filenames, embedded tags, or user stream credentials.

- **`FeedbackDraft`**:
  - Durable draft structure containing title, description, steps to reproduce, expected vs actual behavior, proposed solutions, media context, diagnostics inclusion toggle, and platform metadata.

- **`FeedbackValidator`**:
  - Enforces minimum/maximum string lengths (title: 5..120 chars; description: 10..5000 chars; steps: >= 10 chars for bugs and playback issues). Returns a `FeedbackValidationResult` (`Valid` or `Invalid(errors)`).

- **`FeedbackReportFormatter`**:
  - Formats clean, GitHub-flavored Markdown reports with explicit privacy notices, technical metadata, problem descriptions, and optional sanitized diagnostic excerpts.
  - Generates pre-populated GitHub Issue URLs formatted with RFC 3986 percent-encoding for seamless handoff to verified repository issue forms.

---

## 3. Platform Implementations

### Desktop (`desktopApp`)
- **UI**: `DesktopFeedbackDialog.kt` provides a 4-tab interface (Bug Report, Feature Request, Playback Issue, Saved Drafts).
- **Draft Persistence**: `DesktopFeedbackDraftRepository.kt` stores drafts as human-readable JSON files in `<appData>/feedback_drafts/<id>.json`. It does not touch the SQLite database schema, eliminating any migration or schema regression risks.
- **Export & Actions**:
  - Copy Sanitized Report to system clipboard.
  - Save Markdown report to local file (via native dialog or directory export).
  - Open pre-populated GitHub Issue in default web browser using `java.awt.Desktop`.
  - Save, retrieve, and delete persistent offline drafts.

### Android (`androidApp`)
- **UI**: `AndroidFeedbackDialog.kt` integrated directly into the `SettingsScreen` and error banners.
- **Actions**:
  - Copy Sanitized Markdown report to Android clipboard (`ClipboardManager`).
  - Share via system share sheet (`Intent.createChooser` with `ACTION_SEND`).
  - Open verified GitHub Issue URL in external browser (`Intent.ACTION_VIEW`).
- **Accessibility**: Full Material 3 semantic structure, TalkBack labels, high contrast adherence, minimum touch target sizing (>= 48dp).

---

## 4. Security & Privacy Boundary

```
[User Input & Diagnostics]
            │
            ▼
   [SafeLogger Sanitizer]
   • Strips tokens, passwords, API keys
   • Redacts absolute home directory paths
   • Strips sensitive query parameters
            │
            ▼
  [Local Feedback Draft]
   • Stored locally on user device
   • Zero automatic uploads
            │
            ▼
[Explicit User Action]
   ├─► Copy to Clipboard (Local only)
   ├─► Export Markdown File (User-selected location)
   └─► Open GitHub Issue in Browser (User reviews before submitting)
```
