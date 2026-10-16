# Bulk Edit Safety Policy & Workflow

Vimivo enforces rigorous safety invariants across all bulk metadata operations to prevent accidental data loss, unreviewed file mutations, or corruption of large media collections.

---

## 1. Three-Tier Operation Classification

| Category | Level | Actions | User Confirmation Required? |
|---|---|---|---|
| **Category A** | **Non-Destructive** | Search, filter, metadata analysis, preview generation, duplicate detection, smart playlist queries, database-only overrides. | No. Safe to execute immediately within application boundaries. |
| **Category B** | **User-Confirmed** | Writing embedded tags, replacing cover art, renaming files, moving media to organized folders, applying bulk metadata overrides. | **Yes.** Requires preflight review and explicit in-app confirmation before execution. |
| **Category C** | **High-Risk** | Deleting original media, overwriting destination files, removing source files after cross-volume copies. | **Strictly Prohibited Automatically.** Vimivo never automatically deletes media files. |

---

## 2. Bulk Editing Invariants

### 1. Partial Field Mutability
Only fields explicitly marked for modification in `MetadataChangeset` are altered. If an album has 50 tracks with individual titles and track numbers, updating the `Artist` or `Genre` will leave all 50 unique titles and track numbers untouched:
```
FieldAction.Unchanged -> Original tag preserved bit-for-bit
FieldAction.Set(v)    -> Only this specific field updated
FieldAction.Clear     -> Only this specific field removed
```

### 2. Mixed Values Representation
When multiple items with differing values are selected, form fields display `(Multiple Values)` and default to `FieldAction.Unchanged`. A blank form input is never interpreted as an instruction to erase metadata unless the user explicitly chooses the "Clear Field" action.

### 3. Preflight Planning & Revalidation
Every bulk edit produces a `MetadataEditBatchPlan`:
- Evaluates each file's existence, write permissions, and format compatibility.
- Revalidates file state immediately prior to commit to avoid time-of-check/time-of-use (TOCTOU) races.
- Reports clear status per item: `Ready`, `File is read-only`, `Source file unavailable`, or `Format not supported`.

---

## 3. CSV / Spreadsheet Safety

When exporting or importing metadata via CSV:
- **Formula Injection Mitigation**: Any field starting with `=`, `+`, `-`, `@`, `\t`, or `\r` is automatically prefixed with `'` during export to prevent formula execution in Microsoft Excel or LibreOffice Calc.
- **Unicode Preservation**: Files are encoded in UTF-8 with BOM to guarantee that Bengali (`বাংলা`), Cyrillic, and Asian scripts display without mojibake.
- **Bounded Input**: CSV import rejects files exceeding 10 MB to prevent out-of-memory denial-of-service.
- **Schema Validation**: Requires matching `media_id` and verifies header integrity before generating import plans.
