# Privacy-First Workflow Automation & Library Health

Vimivo features a local-first, user-controlled workflow automation engine designed to keep music and video collections organized, normalized, and healthy without cloud dependencies, external telemetry, or unreviewed file mutations.

---

## 1. Automation Principles

1. **User Control**: No automation rule ever mutates physical media files without explicit user review and approval.
2. **Deterministic & Typed**: Rules are defined via typed domain enums rather than fragile scripts or arbitrary expressions.
3. **Bounded & Non-Destructive**: Actions default to analysis, diagnostics, and database updates.
4. **Zero Background Daemons**: No unauthorized OS services or battery-draining background watchers. Tasks respect platform lifecycles (Android WorkManager constraints, macOS/Windows/Linux active user sessions).

---

## 2. Workflow Rule Architecture

Persisted in SQLite table `workflow_rules`:

```sql
CREATE TABLE workflow_rules (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    trigger_type TEXT NOT NULL,
    action_type TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    last_run_ms INTEGER,
    last_status TEXT
);
```

### Supported Triggers (`AutomationTrigger`)
- `MANUAL`: Triggered explicitly by the user from the Library Health / Automation screen.
- `AFTER_SCAN`: Runs after a folder scan finishes.
- `ON_MEDIA_INDEXED`: Evaluated when new media items are discovered.
- `ON_METADATA_CHANGE`: Evaluated after tags or overrides are updated.
- `ON_ORGANIZER_OPEN`: Evaluated when opening the library organization panel.

### Supported Actions (`AutomationActionType`)
- `REFRESH_SMART_COLLECTIONS`: Re-evaluates rule-based smart playlists against current library state.
- `IDENTIFY_MISSING_MEDIA`: Scans for broken file URIs and prompts candidate relinking.
- `IDENTIFY_DUPLICATES`: Groups size-identical and hash-identical media for non-destructive review.
- `CLEAN_METADATA_WHITESPACE`: Detects and cleans accidental leading/trailing whitespace in tags.
- `NORMALIZE_ALBUM_ARTISTS`: Identifies compilation tracks or albums with missing album artist tags.
- `HEALTH_CHECK`: Computes comprehensive library health metrics and diagnostics.

---

## 3. Non-Destructive Dry-Run Engine

Before any automation rule or batch job executes, `DesktopAutomationEngine.dryRun` generates a preview:

```kotlin
val result: DryRunResult = automationEngine.dryRun(rule, libraryItems)
// Returns:
// - ruleId & ruleName
// - plannedAction
// - estimatedAffectedItems (e.g., 42 items with trailing whitespace)
// - summary (e.g., "Found 42 items with leading/trailing whitespace in metadata.")
```

The dry run performs read-only checks, reporting exactly what would change without modifying files or database records.

---

## 4. Library Health Dashboard & Scoring

The Library Health Dashboard evaluates six vital metrics across all indexed media:

$$\text{Health Score} = \left(\frac{\text{Available}}{\text{Total}} \times 50\right) + \left(\frac{\text{With Complete Metadata}}{\text{Total}} \times 30\right) + \left(\frac{\text{With Artwork}}{\text{Total}} \times 20\right)$$

### Metric Definitions
1. **Available Media Count**: Physical files present and readable on accessible drives.
2. **Missing Media Count**: Files that have been unmounted, moved, or deleted externally.
3. **Missing Metadata Count**: Tracks lacking either an artist or album tag.
4. **Missing Artwork Count**: Media with no embedded cover art or user artwork override.
5. **Exact Duplicate Groups**: Multiple files sharing identical file sizes and SHA-256 hashes.
6. **Health Score (0-100%)**: Normalized quality score guiding user maintenance priorities.
