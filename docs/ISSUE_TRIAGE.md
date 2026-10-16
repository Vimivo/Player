# Vimivo Issue Triage & Taxonomy Guide

## 1. Triage Workflow & Responsibilities

The triage process transforms raw incoming reports into structured, actionable, and reproducible engineering tasks.

```
Incoming Issue
      │
      ▼
Check Completeness (App version, platform, reproduction steps present?)
      ├─► No: Label `status:needs-information` -> Request specific details
      └─► Yes: Attempt reproduction in clean test environment
            │
            ├─► Unreproduced: Label `status:needs-reproduction`
            └─► Reproduced:
                  ├─► Check duplicates: Link and close duplicate with explanation
                  └─► New defect confirmed:
                        • Label `status:confirmed`
                        • Assign Severity (P0, P1, P2, P3)
                        • Assign Component area label (e.g., `area:playback`)
                        • Link to tracking backlog
```

---

## 2. Standard Label Taxonomy

### Type Labels
- `type:bug`: Confirmed defect or regression in implemented functionality.
- `type:enhancement`: Feature request or UX improvement.
- `type:accessibility`: Assistive technology barrier or navigation limitation.
- `type:playback`: Codec, container, subtitle, or rendering issue.
- `type:packaging`: Installer, DMG, MSI, AppImage, or APK packaging defect.
- `type:security`: Vulnerability, privacy violation, or credential exposure.
- `type:documentation`: Guide, README, or API documentation update.

### Platform Labels
- `platform:android`: Android mobile and tablet targets.
- `platform:ios`: iOS and iPadOS targets.
- `platform:macos`: macOS (Apple Silicon arm64 & Intel x86_64).
- `platform:windows`: Windows 10/11 x64.
- `platform:linux`: Linux distributions (AppImage, deb, rpm).
- `platform:all`: Cross-platform shared Kotlin/Compose logic.

### Component Area Labels
- `area:playback`: Native playback engines (libmpv, Media3), decoders, renderers.
- `area:queue`: Playback queue state machine, shuffle, repeat, resume logic.
- `area:library`: Media indexing, tag parsing, metadata editing, search.
- `area:database`: SQLite migrations, Room, data models, persistence integrity.
- `area:ui`: Compose Multiplatform layouts, themes, animations, typography.
- `area:feedback`: In-app feedback composer, diagnostic exporter, reporting.

### Status Labels
- `status:needs-triage`: Awaiting initial engineering review.
- `status:needs-information`: Reporter must provide missing versions, logs, or steps.
- `status:needs-reproduction`: Cannot reproduce with provided information; test fixture needed.
- `status:confirmed`: Defect verified by an engineer in a local test environment.
- `status:in-progress`: Actively being fixed with an open branch or pull request.
- `status:verified`: Resolved in codebase with an automated regression test.
- `status:released`: Included in an official release distribution artifact.
