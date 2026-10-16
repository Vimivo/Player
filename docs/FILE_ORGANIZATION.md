# Media File Renaming & Organization

Vimivo provides an intelligent, non-destructive file renaming and library organization engine that restructures audio and video files while 100% preserving personal database references (playlists, favorites, playback history, resume positions, bookmarks, and queue state).

---

## 1. Template Grammar & Token Expansion

Users can specify rename and folder organization patterns using standard template tokens:

| Token | Replacement Value | Fallback / Format |
|---|---|---|
| `{artist}` | Track artist | `"Unknown Artist"` |
| `{albumArtist}` | Album artist | Falls back to track artist or `"Unknown Artist"` |
| `{album}` | Album title | `"Unknown Album"` |
| `{title}` | Track or video title | Trimmed title string |
| `{track}` | Track number | Two-digit zero-padded (`01`, `02`, ... `12`) |
| `{disc}` | Disc number | Two-digit zero-padded (`01`, `02`) |
| `{year}` | Release year | Four-digit year or `"Unknown Year"` |
| `{genre}` | Media genre | Cleaned genre or `"Unknown Genre"` |
| `{originalName}` | Original filename without extension | Raw base filename |

### Example Patterns
- Single Folder Rename: `{track} - {title}` $\rightarrow$ `01 - Mon Majhi.mp3`
- Full Music Organization: `{artist}/{album}/{track} - {title}` $\rightarrow$ `Lalon Fakir/Baul Gaan/01 - Mon Majhi.mp3`
- Multi-Disc Album: `{albumArtist}/{album}/Disc {disc}/{track} - {title}`

---

## 2. Filename Sanitization & Platform Protection

### Illegal Character Replacement
Characters prohibited on Windows, macOS, or Linux filesystems are replaced with underscores (`_`):
$$\text{Prohibited Characters: } \backslash, /, :, *, ?, ", <, >, |, \text{ ASCII } 0\text{--}31$$
Trailing dots and spaces are automatically trimmed to prevent Windows filesystem parsing defects.

### Windows Reserved Device Names
Filenames matching DOS/Windows device names are flagged as conflicts and rejected from batch renaming:
```
CON, PRN, AUX, NUL, COM1, COM2, COM3, COM4, COM5, COM6, COM7, COM8, COM9,
LPT1, LPT2, LPT3, LPT4, LPT5, LPT6, LPT7, LPT8, LPT9
```

### Collision Detection
- **In-Batch Collisions**: If multiple files expand to the same destination path, all colliding items are flagged.
- **On-Disk Collisions**: If a destination file already exists on disk (and is not the source itself), renaming is blocked.

### Case-Only Renames on Case-Insensitive Filesystems
On macOS APFS and Windows NTFS, renaming `song.mp3` to `Song.mp3` can fail or no-op with standard move calls. Vimivo stages case-only renames through a temporary intermediate file:
$$\text{source.mp3} \xrightarrow{\text{atomic move}} \text{vimivo\_tmp\_<timestamp>\_source.mp3} \xrightarrow{\text{atomic move}} \text{Source.mp3}$$

---

## 3. Database Reference Reconciliation

Renaming or moving a file on disk never resets or orphans library records. When `DesktopBatchRenameOrganizer.executeRename` or `executeOrganize` completes a filesystem move:
1. It updates `media_items.uri`, `file_name`, and `modified_ms` within an isolated SQLite transaction.
2. The stable primary key `media_items.id` remains **unchanged**.
3. All foreign key references across `playlists`, `favorites`, `playback_history`, `playback_positions`, `media_bookmarks`, and `playback_queue_items` remain 100% intact.

---

## 4. Missing Media Relinking

If an external drive is reconnected under a different mount point, or files were moved outside Vimivo:
- **Detection**: `DesktopMissingMediaRelinker.findMissingMedia` locates all items where `file.exists() == false`.
- **Candidate Discovery**: `findCandidates` scans authorized library roots up to a configurable depth (default 4) and suggests matches:
  - **Exact Filename Match**: High confidence.
  - **Exact Title Match**: High confidence.
  - **Exact File Size Match**: Medium confidence.
- **Atomic Relink**: User selects the confirmed match, updating `media_items.uri` and marking `available = 1`.
