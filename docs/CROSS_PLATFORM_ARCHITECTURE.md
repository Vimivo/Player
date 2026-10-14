# Cross-platform persistence architecture

## Milestone 2 dependency analysis

`shared/commonMain` already owns media models, library queries, playlist and organization contracts, settings models, and playback contracts. These remain reusable for macOS and the planned Windows adapter.

Android Room entities, DAOs, MediaStore scanning, SAF grants, permissions, and DataStore remain Android-only. They are not copied into common code or reused through Android types.

The desktop target uses a desktop-only SQLite JDBC adapter at `~/Library/Application Support/Vimivo/vimivo.sqlite` for direct-distribution builds. It enables foreign keys and WAL, performs explicit non-destructive migrations, and will back the existing shared repository contracts. Native folder pickers, security-scoped bookmarks, FSEvents, Finder events, and cache locations remain macOS-specific.

The first schema establishes library roots, indexed media items, and key/value settings. Playlists, favorites, history, positions, metadata tables, thumbnail cache, scanner, repositories, and watchers are intentionally deferred to subsequent verified increments.

## Identity and release limits

Desktop media identity will use a root-scoped normalized file URI plus lightweight file metadata, with a persistent filesystem identifier when macOS integration becomes available. A filename alone is never an identifier. Moved files and network volumes require reconciliation scans; ordinary scanning must not hash full files.

The libmpv bridge is a development dependency linked from Homebrew. It is not bundled, signed, or suitable for standalone macOS distribution.
