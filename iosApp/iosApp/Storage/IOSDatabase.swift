import Foundation
import VimivoShared

/// The app's persistent library. The schema, migrations and queries live in the shared module
/// (`LocalLibraryStore`, over the system SQLite); this class opens the database under Application Support, maps the
/// shared records to the app's own models, and logs a failure instead of crashing the UI.
public final class IOSDatabase {
    public static let shared = IOSDatabase()
    private let store: LocalLibraryStore

    public init(path: String? = nil) {
        store = IOSDatabase.openStore(at: path)
    }

    private static func openStore(at path: String?) -> LocalLibraryStore {
        do {
            if let path { return try IosLibrary.shared.open(path: path) }
            guard let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
                throw CocoaError(.fileNoSuchFile)
            }
            let directory = appSupport.appendingPathComponent("vimivo", isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            return try IosLibrary.shared.open(path: directory.appendingPathComponent("vimivo.db").path)
        } catch {
            // Without a database the app still runs, with a library that lasts until it is closed.
            NSLog("Vimivo: the library database could not be opened (%@); using a temporary one", "\(error)")
            // A shared in-memory database cannot fail to open.
            return (try? IosLibrary.shared.openInMemory())!
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
        attempt("media save") { try store.saveMedia(item: item.stored) }
    }

    /// Re-points an app-owned file at its current container path, which changes when the app is updated.
    public func updateMediaUri(id: String, uri: String) {
        attempt("media uri update") { try store.updateMediaUri(id: id, uri: uri) }
    }

    public func getAllMediaItems() -> [MediaItem] {
        attempt("media read", fallback: []) { try store.allMedia().map(MediaItem.init(stored:)) }
    }

    /// Stores width, height, and file size discovered after import without touching other fields.
    public func updateVideoMetadata(id: String, width: Int?, height: Int?, sizeBytes: Int64?) {
        attempt("video metadata update") {
            try store.updateVideoMetadata(
                id: id,
                width: width.map { KotlinInt(int: Int32($0)) },
                height: height.map { KotlinInt(int: Int32($0)) },
                sizeBytes: sizeBytes.map { KotlinLong(longLong: $0) }
            )
        }
    }

    public func deleteMediaItem(id: String) {
        attempt("media delete") { try store.deleteMedia(id: id) }
    }

    // MARK: - Playlists

    public func getAllPlaylists() -> [Playlist] {
        attempt("playlist read", fallback: []) {
            try store.playlists().map {
                Playlist(id: $0.id, name: $0.name, mediaIds: $0.mediaIds, createdAtMillis: $0.createdAtMillis)
            }
        }
    }

    public func savePlaylist(_ playlist: Playlist) {
        attempt("playlist save") {
            try store.savePlaylist(playlist: StoredPlaylist(
                id: playlist.id, name: playlist.name, mediaIds: playlist.mediaIds, createdAtMillis: playlist.createdAtMillis
            ))
        }
    }

    public func deletePlaylist(id: String) {
        attempt("playlist delete") { try store.deletePlaylist(id: id) }
    }

    // MARK: - Favorites

    public func getFavorites() -> Set<String> {
        attempt("favorites read", fallback: []) { Set(try store.favorites()) }
    }

    public func setFavorite(mediaId: String, isFavorite: Bool) {
        attempt("favorite update") { try store.setFavorite(mediaId: mediaId, favorite: isFavorite) }
    }

    // MARK: - Bookmarks

    public func addBookmark(_ bookmark: Bookmark) {
        attempt("bookmark write") {
            try store.addBookmark(bookmark: StoredBookmark(
                id: bookmark.id, mediaId: bookmark.mediaId, name: bookmark.name,
                positionMillis: bookmark.positionMillis, createdAtMillis: bookmark.createdAtMillis
            ))
        }
    }

    public func bookmarks(mediaId: String) -> [Bookmark] {
        attempt("bookmark read", fallback: []) {
            try store.bookmarks(mediaId: mediaId).map {
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
            try store.lyrics(mediaId: mediaId).map { ($0.source, $0.text) }
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
        attempt("history write") { try store.recordPlayStart(mediaId: mediaId) }
    }

    /// Persists the resume position; never counts as an additional play.
    public func savePosition(mediaId: String, positionMillis: Int64, completed: Bool) {
        attempt("history write") { try store.savePosition(mediaId: mediaId, positionMillis: positionMillis, completed: completed) }
    }

    public func resumeState(mediaId: String) -> ResumeState? {
        attempt("resume read", fallback: nil) {
            try store.resumeState(mediaId: mediaId).map {
                ResumeState(positionMillis: $0.positionMillis, durationMillis: nil, completed: $0.completed)
            }
        }
    }

    public func getHistory() -> [PlaybackHistory] {
        attempt("history read", fallback: []) {
            try store.history().map {
                PlaybackHistory(
                    id: $0.id, mediaId: $0.mediaId, lastPlayedAtMillis: $0.lastPlayedAtMillis,
                    positionMillis: $0.positionMillis, completed: $0.completed, playCount: Int($0.playCount)
                )
            }
        }
    }
}

// MARK: - Mapping between the shared records and the app's models

private extension MediaItem {
    init(stored: StoredMedia) {
        self.init(
            id: stored.id,
            uri: stored.uri,
            title: stored.title,
            kind: MediaKind(rawValue: stored.kind) ?? .audio,
            durationMillis: stored.durationMillis?.int64Value,
            artist: stored.artist,
            album: stored.album,
            artworkUri: stored.artworkUri,
            albumArtist: stored.albumArtist,
            bookmarkData: stored.bookmarkData?.data,
            width: stored.width.map { Int(truncating: $0) },
            height: stored.height.map { Int(truncating: $0) },
            sizeBytes: stored.sizeBytes?.int64Value,
            dateAddedMillis: stored.dateAddedMillis?.int64Value
        )
    }

    var stored: StoredMedia {
        StoredMedia(
            id: id,
            uri: uri,
            title: title,
            kind: kind.rawValue,
            durationMillis: durationMillis.map { KotlinLong(longLong: $0) },
            artist: artist,
            album: album,
            artworkUri: artworkUri,
            albumArtist: albumArtist,
            bookmarkData: bookmarkData.map(KotlinByteArray.init(data:)),
            width: width.map { KotlinInt(int: Int32($0)) },
            height: height.map { KotlinInt(int: Int32($0)) },
            sizeBytes: sizeBytes.map { KotlinLong(longLong: $0) },
            dateAddedMillis: dateAddedMillis.map { KotlinLong(longLong: $0) }
        )
    }
}
