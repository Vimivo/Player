# Library Management & Automation Test Suite

This document records the automated regression test suites, fixtures, and verification results for Milestones 41 through 44.

---

## 1. Test Suite Inventory

| Test Class | Module | Test Count | Scope & Coverage |
|---|---|:---:|---|
| [`DesktopMetadataEditorTest`](file:///Users/ta-shanto/Coding/products/Vimivo/desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/library/DesktopMetadataEditorTest.kt) | `:desktopApp` | 6 | Mode A overrides, Mode B embedded write planning, partial field changes, format restriction flagging, artwork overrides. |
| [`DesktopBatchRenameTest`](file:///Users/ta-shanto/Coding/products/Vimivo/desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/library/DesktopBatchRenameTest.kt) | `:desktopApp` | 7 | Template token expansion, illegal character sanitization, Windows reserved names, collision detection, case-only renames, DB URI reconciliation. |
| [`DesktopMissingMediaTest`](file:///Users/ta-shanto/Coding/products/Vimivo/desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/library/DesktopMissingMediaTest.kt) | `:desktopApp` | 4 | Missing media identification, candidate scoring (exact filename, title, file size), atomic relinking, and `available = 1` recovery. |
| [`DesktopAutomationJournalTest`](file:///Users/ta-shanto/Coding/products/Vimivo/desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/library/DesktopAutomationJournalTest.kt) | `:desktopApp` | 6 | Workflow rule CRUD, persistent journal lifecycle (`PLANNED` -> `RUNNING` -> `COMMITTED`), dry-run evaluation, health diagnostic scoring. |
| [`DesktopMetadataCsvTest`](file:///Users/ta-shanto/Coding/products/Vimivo/desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/library/DesktopMetadataCsvTest.kt) | `:desktopApp` | 2 | CSV export with formula injection defense (`'` prefix), Bengali/Unicode preservation, CSV import preview, and database override application. |
| [`DesktopSmartPlaylistTest`](file:///Users/ta-shanto/Coding/products/Vimivo/desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/library/DesktopSmartPlaylistTest.kt) | `:desktopApp` | 5 | Typed AST rule conditions (`EQUALS`, `CONTAINS`, `GREATER_THAN`, `LESS_THAN`), match modes (`ALL`/`ANY`), sort orders, and item limits. |
| [`DesktopRecommendationInsightsTest`](file:///Users/ta-shanto/Coding/products/Vimivo/desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/library/DesktopRecommendationInsightsTest.kt) | `:desktopApp` | 5 | 100% offline recommendations, dismissals persistence, cold-start handling, listening & watching insights calculation. |
| [`DesktopSearchUnicodeBengaliTest`](file:///Users/ta-shanto/Coding/products/Vimivo/desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/library/DesktopSearchUnicodeBengaliTest.kt) | `:desktopApp` | 4 | Unicode NFC normalization, Bengali script query ranking (exact match > prefix > substring > artist > album). |

---

## 2. Key Edge Case Validations

1. **Windows Reserved Device Names**:
   Verified that `{title}` evaluating to `CON.mp3`, `NUL.mp3`, or `COM1.mp3` is flagged as a conflict (`"Filename uses a reserved system name"`) and prevented from renaming.
2. **Case-Only Renames on Case-Insensitive Filesystems**:
   Verified that renaming `lower.mp3` to `Lower.mp3` utilizes a unique intermediate temporary file to prevent filesystem no-ops.
3. **Database Reference Preservation**:
   Verified that renaming or relinking a media file preserves its primary key `media_items.id`. Playlists referencing that media item continue to play without interruption.
4. **CSV Formula Injection Defense**:
   Verified that a title starting with `=1+2"special,comma` or `@Formula` is safely escaped with a leading single quote (`'`), preventing arbitrary code execution when opened in spreadsheet software.
5. **Post-Write Audio Header Verification**:
   Verified that embedded tag writes verify the readability of the audio stream header after commit, automatically rolling back to the scratch backup if corruption is detected.

---

## 3. Verified Execution Summary

All tests executed via `./scripts/verify_all.sh` on macOS:
- `:shared:desktopTest`: 58 passed, 0 failed.
- `:desktopApp:desktopTest`: 145 passed, 0 failed (+39 new library management & automation tests).
- `:androidApp:testDebugUnitTest`: 20 passed, 0 failed.
- Total automated tests: **223 passed**, 0 failed.
- Detekt Static Analysis: **0 weighted violations**.
