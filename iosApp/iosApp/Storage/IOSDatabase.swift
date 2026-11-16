import Foundation
import VimivoCoreRust

/// The app's persistent library backed 100% by Rust (`crates/vimivo-core` SQLite engine)
/// accessed via `FfiDatabase` from `VimivoCoreRust`.
public final class IOSDatabase {
    public static let shared = IOSDatabase()
    private let store: FfiDatabase

    public init(path: String? = nil) {
        store = IOSDatabase.openStore(at: path)
    }

    private static func openStore(at path: String?) -> FfiDatabase {
        do {
            if let path { return try FfiDatabase.open(path: path) }
            guard let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
                throw CocoaError(.fileNoSuchFile)
            }
            let directory = appSupport.appendingPathComponent("vimivo", isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            return try FfiDatabase.open(path: directory.appendingPathComponent("vimivo.db").path)
        } catch {
            NSLog("Vimivo: the library database could not be opened (%@); using a temporary one", "\(error)")
            return (try? FfiDatabase.openInMemory())!
        }
    }

    private func attempt(_ what: String, _ body: () throws -> Void) {
        do { try body() } catch { NSLog("Vimivo: %@ failed: %@", what, "\(error)") }
    }

    private func attempt<T>(_ what: String, fallback: T, _ body: () throws -> T) -> T {
        do { return try body() } catch {
            NSLog("Vimivo: %@ failed: %@", what, "\(error)")
            return fallback
        }
    }

    // MARK: - Media Items

    public func saveMediaItem(_ item: MediaItem) {
        attempt("media save") { try store.upsertItem(item: item.toFfi()) }
    }

    /// Re-points an app-owned file at its current container path, which changes when the app is updated.
    public func updateMediaUri(id: String, uri: String) {
        attempt("media uri update") { try store.updateMediaUri(id: id, uri: uri) }
    }

    public func getAllMediaItems() -> [MediaItem] {
        attempt("media read", fallback: []) {
            try store.getAllItems().map(MediaItem.init(ffi:))
        }
    }

    /// Stores width, height, and file size discovered after import without touching other fields.
    public func updateVideoMetadata(id: String, width: Int?, height: Int?, sizeBytes: Int64?) {
        attempt("video metadata update") {
            try store.updateVideoMetadata(
                id: id,
                width: width.map { Int32($0) },
                height: height.map { Int32($0) },
                sizeBytes: sizeBytes
            )
        }
    }

    public func deleteMediaItem(id: String) {
        attempt("media delete") { try store.deleteItem(id: id) }
    }

    // MARK: - Playlists

    public func getAllPlaylists() -> [Playlist] {
        attempt("playlist read", fallback: []) {
            try store.getPlaylists().map {
                Playlist(id: $0.id, name: $0.name, mediaIds: $0.mediaIds, createdAtMillis: $0.createdAtMillis)
            }
        }
    }

    public func savePlaylist(_ playlist: Playlist) {
        attempt("playlist save") {
            try store.savePlaylist(playlist: FfiPlaylist(
                id: playlist.id, name: playlist.name, mediaIds: playlist.mediaIds, createdAtMillis: playlist.createdAtMillis
            ))
        }
    }

    public func deletePlaylist(id: String) {
        attempt("playlist delete") { try store.deletePlaylist(id: id) }
    }

    // MARK: - Favorites

    public func getFavorites() -> Set<String> {
        attempt("favorites read", fallback: []) { Set(try store.getFavorites()) }
    }

    public func setFavorite(mediaId: String, isFavorite: Bool) {
        attempt("favorite update") { try store.setFavorite(mediaId: mediaId, isFav: isFavorite) }
    }

    // MARK: - Bookmarks

    public func addBookmark(_ bookmark: Bookmark) {
        attempt("bookmark write") {
            try store.addBookmark(bookmark: FfiBookmark(
                id: bookmark.id, mediaId: bookmark.mediaId, name: bookmark.name,
                positionMillis: bookmark.positionMillis, createdAtMillis: bookmark.createdAtMillis
            ))
        }
    }

    public func bookmarks(mediaId: String) -> [Bookmark] {
        attempt("bookmark read", fallback: []) {
            try store.getBookmarks(mediaId: mediaId).map {
                Bookmark(
                    id: $0.id, mediaId: $0.mediaId, name: $0.name,
                    positionMillis: $0.positionMillis, createdAtMillis: $0.createdAtMillis
                )
            }
        }
    }

    public func deleteBookmark(id: String) {
        attempt("bookmark delete") { try store.deleteBookmark(id: id) }
    }

    // MARK: - Lyrics

    /// Keeps the lyrics file a person loaded for a track, so it is still there next time.
    public func saveLyrics(mediaId: String, source: String, text: String) {
        attempt("lyrics save") { try store.saveLyrics(mediaId: mediaId, source: source, text: text) }
    }

    public func lyrics(mediaId: String) -> (source: String, text: String)? {
        attempt("lyrics read", fallback: nil) {
            try store.getLyricsRecord(mediaId: mediaId).map { ($0.source, $0.text) }
        }
    }

    // MARK: - Playback Queue

    /// Replaces the persisted queue atomically so a crash never leaves a half-written queue.
    public func saveQueue(mediaIds: [String], currentIndex: Int) {
        attempt("queue save") { try store.saveQueue(mediaIds: mediaIds, currentIndex: Int32(currentIndex)) }
    }

    public func loadQueue() -> (mediaIds: [String], currentIndex: Int) {
        attempt("queue read", fallback: ([], 0)) {
            let queue = try store.loadQueue()
            return (queue.mediaIds, Int(queue.currentIndex))
        }
    }

    // MARK: - History & Resume

    /// Counts a play and refreshes recency without touching the saved resume position.
    public func recordPlayStart(mediaId: String) {
        attempt("history write") {
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            try store.recordPlayStart(mediaId: mediaId, playedAtMillis: now)
        }
    }

    /// Persists the resume position; never counts as an additional play.
    public func savePosition(mediaId: String, positionMillis: Int64, completed: Bool) {
        attempt("history write") {
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            try store.savePosition(
                mediaId: mediaId,
                positionMillis: positionMillis,
                completed: completed,
                updatedAtMillis: now
            )
        }
    }

    public func resumeState(mediaId: String) -> ResumeState? {
        attempt("resume read", fallback: nil) {
            try store.getResumeState(mediaId: mediaId).map {
                ResumeState(positionMillis: $0.positionMillis, durationMillis: nil, completed: $0.completed)
            }
        }
    }

    public func getHistory() -> [PlaybackHistory] {
        attempt("history read", fallback: []) {
            try store.getHistory().map {
                PlaybackHistory(
                    id: $0.id, mediaId: $0.mediaId, lastPlayedAtMillis: $0.lastPlayedAtMillis,
                    positionMillis: $0.positionMillis, completed: $0.completed, playCount: Int($0.playCount)
                )
            }
        }
    }
}

// MARK: - Mapping between Rust FFI records and the app's models

extension MediaItem {
    init(ffi: FfiMediaItem) {
        let kind: MediaKind
        switch ffi.kind {
        case .audio: kind = .audio
        case .video: kind = .video
        case .stream: kind = .stream
        }

        self.init(
            id: ffi.id,
            uri: ffi.uri,
            title: ffi.title,
            kind: kind,
            durationMillis: ffi.durationMillis,
            artist: ffi.artist,
            album: ffi.album,
            artworkUri: ffi.artworkUri,
            albumArtist: ffi.albumArtist,
            bookmarkData: ffi.bookmarkData,
            width: ffi.width.map(Int.init),
            height: ffi.height.map(Int.init),
            sizeBytes: ffi.sizeBytes,
            dateAddedMillis: ffi.dateAddedMillis
        )
    }

    func toFfi() -> FfiMediaItem {
        let ffiKind: FfiMediaKind
        switch kind {
        case .audio: ffiKind = .audio
        case .video: ffiKind = .video
        case .stream: ffiKind = .stream
        }

        return FfiMediaItem(
            id: id,
            uri: uri,
            title: title,
            kind: ffiKind,
            durationMillis: durationMillis,
            artist: artist,
            album: album,
            artworkUri: artworkUri,
            albumArtist: albumArtist,
            bookmarkData: bookmarkData,
            width: width.map(Int32.init),
            height: height.map(Int32.init),
            sizeBytes: sizeBytes,
            dateAddedMillis: dateAddedMillis
        )
    }
}
