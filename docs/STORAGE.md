# Local media and storage

## Access model

Vimivo indexes metadata and content URIs; it never copies complete media files into app storage. Android `MediaStore` is the source for the shared audio/video library. The Storage Access Framework (SAF) is the opt-in path for an individual document or a user-selected folder, including compatible removable storage providers.

The app never declares `MANAGE_EXTERNAL_STORAGE`.

| Android version | Shared audio | Shared video | Picker access |
| --- | --- | --- | --- |
| 13+ | `READ_MEDIA_AUDIO` | `READ_MEDIA_VIDEO` | No library permission |
| 14+ selected access | `READ_MEDIA_AUDIO` | video permission plus `READ_MEDIA_VISUAL_USER_SELECTED`; partial state is explicit | No library permission |
| 8–12L | `READ_EXTERNAL_STORAGE` (declaration capped at API 32) | Same | No library permission |

Permission state is recomputed from the platform after a request. Denial or revocation does not remove SAF grants or prevent the system pickers. Persistable URI permission is requested only for the URI the user selected.

## Indexing

- MediaStore volumes are discovered through `getExternalVolumeNames` on Android 10+, including available removable volumes exposed by MediaStore.
- Version and generation tokens drive incremental refresh. A changed provider version forces a full collection pass.
- Content observers debounce automatic updates. Manual rescan performs a full pass and marks unseen rows unavailable.
- SAF trees are walked on `Dispatchers.IO`, in cancellable batches. Selected single files are revalidated during cleanup.
- Batches of 250 bound memory usage. Stable IDs and a unique URI index prevent duplicates; user fields survive metadata upserts.
- Missing records are first hidden as unavailable, then removed only by explicit cleanup.

Metadata is best-effort. MediaStore supplies shared-library fields; SAF imports supplement document metadata with `MediaMetadataRetriever`. Missing, malformed, negative, or sentinel values become `null`. Unsupported documents are skipped by MIME type/known extension; actual decoder compatibility remains the playback engine's responsibility.

## Persistence and performance

Room 2.8.5 backs the Android adapter. The database stores media metadata, favorites, playback history/resume positions, playlists and ordering, scan tokens, SAF grants, and folder exclusions. Search and category columns are indexed. The UI requests pages (100 rows by default); artwork/video thumbnails use a bounded memory cache and platform thumbnail APIs rather than being stored as full images in Room.

## Known platform limits

- MediaStore metadata depends on the device provider and parser; genre, disc, bitrate, frame rate, and artwork can be absent.
- Android's selected-media access can expose only a subset of videos; the UI reports partial access.
- A provider can revoke a SAF grant or an SD card can disappear. The next rescan marks its rows unavailable and reports read failures.
- Incremental scans discover additions and modifications. A manual full rescan is the authoritative missing-file reconciliation path.
