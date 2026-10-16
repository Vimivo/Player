# Batch Operation Journaling, Recovery & Undo

Vimivo implements a persistent operation journal to ensure batch file operations, tag writes, and library reorganization jobs remain fault-tolerant against sudden power loss, app restarts, or drive disconnections.

---

## 1. Persistent Operation Journal

Every multi-file operation is recorded in SQLite table `batch_operation_journal`:

```sql
CREATE TABLE batch_operation_journal (
    job_id TEXT PRIMARY KEY,
    operation_type TEXT NOT NULL,
    stage TEXT NOT NULL,
    affected_count INTEGER NOT NULL DEFAULT 0,
    details TEXT NOT NULL,
    created_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL
);
CREATE INDEX batch_journal_created ON batch_operation_journal(created_at_ms DESC);
```

### Operation Stages (`OperationStage`)
```
+-------------+      +-------------+      +---------------+
|   PLANNED   | ---> |   RUNNING   | ---> |   COMMITTED   |
+-------------+      +-------------+      +---------------+
                            |                     |
                            v                     v
                     +-------------+       +--------------+
                     |   FAILED    |       |   VERIFIED   |
                     +-------------+       +--------------+
                            |
                            v
                     [ CANCELLED ]
```

1. **`PLANNED`**: Preflight checks complete; awaiting user confirmation.
2. **`RUNNING`**: Filesystem operations actively executing in bounded batches.
3. **`COMMITTED`**: Filesystem mutations committed; database reconciliation in progress.
4. **`VERIFIED`**: Post-write verification succeeded; database indexes updated.
5. **`FAILED`**: Operation encountered an error; automatic rollback attempted.
6. **`CANCELLED`**: User aborted the operation cooperatively; in-flight step finished safely.

---

## 2. Crash Recovery & Idempotency

### Interrupted Tag Writes
When writing embedded tags (`applyEmbeddedTagWrite`):
1. A timestamped backup is created in the scratch directory:
   `<filename>_backup_<timestamp>.<ext>`
2. Jaudiotagger writes to the target file.
3. Post-write verification inspects `audioHeader.trackLength` and tag integrity.
4. **On Crash or Error**: The catch block immediately restores the original file from the backup:
   ```kotlin
   if (backupPath != null && Files.exists(backupPath)) {
       Files.copy(backupPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
   }
   ```

### Interrupted Renames
- Because `Files.move(..., ATOMIC_MOVE)` is used on POSIX/Windows filesystems, individual file renames either fully succeed or fail without leaving corrupted fragments.
- If the application is terminated before SQLite updates, the Missing Media Relinker detects the moved file on the next scan and reconciles the URI automatically.

---

## 3. Undo Capabilities & Boundaries

| Operation Type | Undo Feasibility | Mechanism |
|---|:---:|---|
| **Database Overrides (Mode A)** | **100% Reversible** | `DELETE FROM user_metadata_overrides WHERE media_id = ?` instantly restores original extracted metadata. |
| **Artwork Overrides** | **100% Reversible** | `DELETE FROM user_artwork_overrides WHERE media_id = ?` restores extracted cover art. |
| **Same-Volume File Rename** | **Reversible** | Inverting the `RenamePlanItem` (swapping `sourceUri` and `proposedUri`) moves files back. |
| **Embedded Tag Writes (Mode B)** | **Conditional** | Available while scratch backup files are retained. If scratch files are cleaned, tag edits cannot be undone automatically without a full media backup. |
| **Cross-Volume File Moves** | **Guarded / Copy Preferred** | Cross-volume operations default to **Copy** to guarantee the source media remains intact. True moves across drives are not guaranteed atomic. |
