# Cross-platform feature matrix

Verified against source and automated tests on 2026-10-14. “Verified” below means automated verification unless runtime is explicitly stated.

| Feature | Android | macOS | Windows | Linux | iOS |
|---|---|---|---|---|---|
| Local audio playback | Implemented | Implemented, runtime verification pending | Partial | Partial | Not implemented |
| Embedded local video | Implemented | Implemented, runtime verification pending | Blocked: renderer | Blocked: renderer | Not implemented |
| Persistent media library | Implemented | Implemented, tested | Implemented, tested JVM layer | Implemented, tested JVM layer | Not implemented |
| Metadata | Partial | Partial, tested | Partial, tested JVM layer | Partial, tested JVM layer | Not implemented |
| Artwork and thumbnails | Partial | Not implemented | Not implemented | Not implemented | Not implemented |
| Playlists and favorites | Implemented | Implemented, tested | Implemented, tested JVM layer | Implemented, tested JVM layer | Not implemented |
| History and resume | Implemented | Implemented, tested | Implemented, tested JVM layer | Implemented, tested JVM layer | Not implemented |
| Bookmarks | Implemented | Implemented, tested | Implemented, tested JVM layer | Implemented, tested JVM layer | Not implemented |
| Direct network URLs | Implemented | Partial | Partial | Partial | Not implemented |
| Network storage browser | Partial | Not implemented | Not implemented | Not implemented | Not implemented |
| Native distribution | Partial | Partial | Partial | Partial | Not implemented |

iOS was explicitly requested by the production directive, but no iOS application exists yet. Its architecture and implementation must not reuse JVM-only SQLite, Java Sound, Swing/AWT, JNA, or desktop native binaries.

