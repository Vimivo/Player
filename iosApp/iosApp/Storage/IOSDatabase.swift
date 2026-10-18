import Foundation
import SQLite3

public final class IOSDatabase {
    public static let shared = IOSDatabase()
    private var db: OpaquePointer?
    private let queue = DispatchQueue(label: "dev.vimivo.database", qos: .userInitiated)

    public init() {
        open()
        migrate()
    }

    deinit {
        if let db = db {
            sqlite3_close(db)
        }
    }

    private func open() {
        guard let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else { return }
        let dir = appSupport.appendingPathComponent("vimivo", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let dbPath = dir.appendingPathComponent("vimivo.db").path

        if sqlite3_open(dbPath, &db) != SQLITE_OK {
            print("Failed to open SQLite database at \(dbPath)")
        } else {
            sqlite3_exec(db, "PRAGMA journal_mode=WAL;", nil, nil, nil)
            sqlite3_exec(db, "PRAGMA foreign_keys=ON;", nil, nil, nil)
        }
    }

    private func migrate() {
        let sql = """
        CREATE TABLE IF NOT EXISTS media_items (
            id TEXT PRIMARY KEY,
            uri TEXT NOT NULL,
            title TEXT NOT NULL,
            kind TEXT NOT NULL,
            duration_millis INTEGER,
            artist TEXT,
            album TEXT,
            artwork_uri TEXT,
            album_artist TEXT,
            bookmark_data BLOB
        );
        CREATE TABLE IF NOT EXISTS playlists (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            created_at_millis INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS playlist_items (
            playlist_id TEXT NOT NULL,
            media_id TEXT NOT NULL,
            position INTEGER NOT NULL,
            PRIMARY KEY (playlist_id, position),
            FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS favorites (
            media_id TEXT PRIMARY KEY
        );
        CREATE TABLE IF NOT EXISTS playback_history (
            id TEXT PRIMARY KEY,
            media_id TEXT NOT NULL,
            last_played_at_millis INTEGER NOT NULL,
            position_millis INTEGER NOT NULL,
            completed INTEGER NOT NULL,
            play_count INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS bookmarks (
            id TEXT PRIMARY KEY,
            media_id TEXT NOT NULL,
            name TEXT NOT NULL,
            position_millis INTEGER NOT NULL,
            created_at_millis INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS queue_items (
            position INTEGER PRIMARY KEY,
            media_id TEXT NOT NULL
        );
        """
        sqlite3_exec(db, sql, nil, nil, nil)
        addMissingMediaColumns()
    }

    /// Non-destructive, additive migration: new nullable columns are added only when absent.
    private func addMissingMediaColumns() {
        var existing = Set<String>()
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, "PRAGMA table_info(media_items);", -1, &stmt, nil) == SQLITE_OK {
            while sqlite3_step(stmt) == SQLITE_ROW {
                if let name = sqlite3_column_text(stmt, 1) {
                    existing.insert(String(cString: name))
                }
            }
        }
        sqlite3_finalize(stmt)
        let columns = [
            ("width", "INTEGER"),
            ("height", "INTEGER"),
            ("size_bytes", "INTEGER"),
            ("date_added_millis", "INTEGER")
        ]
        for (name, type) in columns where !existing.contains(name) {
            if sqlite3_exec(db, "ALTER TABLE media_items ADD COLUMN \(name) \(type);", nil, nil, nil) != SQLITE_OK {
                let message = db.flatMap { sqlite3_errmsg($0) }.map { String(cString: $0) } ?? "unknown error"
                NSLog("Vimivo: failed to add media_items.%@: %@", name, message)
            }
        }
    }

    // MARK: - Media Items
    public func saveMediaItem(_ item: MediaItem) {
        queue.sync {
            let sql = "INSERT OR REPLACE INTO media_items (id, uri, title, kind, duration_millis, artist, album, artwork_uri, album_artist, bookmark_data, width, height, size_bytes, date_added_millis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                sqlite3_bind_text(stmt, 1, (item.id as NSString).utf8String, -1, nil)
                sqlite3_bind_text(stmt, 2, (item.uri as NSString).utf8String, -1, nil)
                sqlite3_bind_text(stmt, 3, (item.title as NSString).utf8String, -1, nil)
                sqlite3_bind_text(stmt, 4, (item.kind.rawValue as NSString).utf8String, -1, nil)
                if let dur = item.durationMillis {
                    sqlite3_bind_int64(stmt, 5, dur)
                } else {
                    sqlite3_bind_null(stmt, 5)
                }
                if let artist = item.artist {
                    sqlite3_bind_text(stmt, 6, (artist as NSString).utf8String, -1, nil)
                } else {
                    sqlite3_bind_null(stmt, 6)
                }
                if let album = item.album {
                    sqlite3_bind_text(stmt, 7, (album as NSString).utf8String, -1, nil)
                } else {
                    sqlite3_bind_null(stmt, 7)
                }
                if let art = item.artworkUri {
                    sqlite3_bind_text(stmt, 8, (art as NSString).utf8String, -1, nil)
                } else {
                    sqlite3_bind_null(stmt, 8)
                }
                if let aa = item.albumArtist {
                    sqlite3_bind_text(stmt, 9, (aa as NSString).utf8String, -1, nil)
                } else {
                    sqlite3_bind_null(stmt, 9)
                }
                if let bm = item.bookmarkData {
                    bm.withUnsafeBytes { ptr in
                        sqlite3_bind_blob(stmt, 10, ptr.baseAddress, Int32(bm.count), nil)
                    }
                } else {
                    sqlite3_bind_null(stmt, 10)
                }
                bindOptionalInt64(stmt, 11, item.width.map(Int64.init))
                bindOptionalInt64(stmt, 12, item.height.map(Int64.init))
                bindOptionalInt64(stmt, 13, item.sizeBytes)
                sqlite3_bind_int64(stmt, 14, item.dateAddedMillis ?? Int64(Date().timeIntervalSince1970 * 1000))
                sqlite3_step(stmt)
            }
            sqlite3_finalize(stmt)
        }
    }

    public func getAllMediaItems() -> [MediaItem] {
        queue.sync {
            var items: [MediaItem] = []
            let sql = "SELECT id, uri, title, kind, duration_millis, artist, album, artwork_uri, album_artist, bookmark_data, width, height, size_bytes, date_added_millis FROM media_items ORDER BY title ASC;"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                while sqlite3_step(stmt) == SQLITE_ROW {
                    let id = String(cString: sqlite3_column_text(stmt, 0))
                    let uri = String(cString: sqlite3_column_text(stmt, 1))
                    let title = String(cString: sqlite3_column_text(stmt, 2))
                    let kindStr = String(cString: sqlite3_column_text(stmt, 3))
                    let kind = MediaKind(rawValue: kindStr) ?? .audio

                    let dur: Int64? = sqlite3_column_type(stmt, 4) != SQLITE_NULL ? sqlite3_column_int64(stmt, 4) : nil
                    let artist = sqlite3_column_text(stmt, 5).map { String(cString: $0) }
                    let album = sqlite3_column_text(stmt, 6).map { String(cString: $0) }
                    let artworkUri = sqlite3_column_text(stmt, 7).map { String(cString: $0) }
                    let albumArtist = sqlite3_column_text(stmt, 8).map { String(cString: $0) }

                    var bookmarkData: Data? = nil
                    if sqlite3_column_type(stmt, 9) != SQLITE_NULL, let blob = sqlite3_column_blob(stmt, 9) {
                        let bytes = sqlite3_column_bytes(stmt, 9)
                        bookmarkData = Data(bytes: blob, count: Int(bytes))
                    }

                    items.append(MediaItem(
                        id: id,
                        uri: uri,
                        title: title,
                        kind: kind,
                        durationMillis: dur,
                        artist: artist,
                        album: album,
                        artworkUri: artworkUri,
                        albumArtist: albumArtist,
                        bookmarkData: bookmarkData,
                        width: columnOptionalInt64(stmt, 10).map(Int.init),
                        height: columnOptionalInt64(stmt, 11).map(Int.init),
                        sizeBytes: columnOptionalInt64(stmt, 12),
                        dateAddedMillis: columnOptionalInt64(stmt, 13)
                    ))
                }
            }
            sqlite3_finalize(stmt)
            return items
        }
    }

    /// Stores width, height, and file size discovered after import without touching other fields.
    public func updateVideoMetadata(id: String, width: Int?, height: Int?, sizeBytes: Int64?) {
        queue.sync {
            let sql = "UPDATE media_items SET width = COALESCE(?, width), height = COALESCE(?, height), size_bytes = COALESCE(?, size_bytes) WHERE id = ?;"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                bindOptionalInt64(stmt, 1, width.map(Int64.init))
                bindOptionalInt64(stmt, 2, height.map(Int64.init))
                bindOptionalInt64(stmt, 3, sizeBytes)
                sqlite3_bind_text(stmt, 4, (id as NSString).utf8String, -1, nil)
                sqlite3_step(stmt)
            }
            sqlite3_finalize(stmt)
        }
    }

    private func bindOptionalInt64(_ stmt: OpaquePointer?, _ index: Int32, _ value: Int64?) {
        if let value = value {
            sqlite3_bind_int64(stmt, index, value)
        } else {
            sqlite3_bind_null(stmt, index)
        }
    }

    private func columnOptionalInt64(_ stmt: OpaquePointer?, _ index: Int32) -> Int64? {
        sqlite3_column_type(stmt, index) != SQLITE_NULL ? sqlite3_column_int64(stmt, index) : nil
    }

    public func deleteMediaItem(id: String) {
        queue.sync {
            let sql = "DELETE FROM media_items WHERE id = ?;"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                sqlite3_bind_text(stmt, 1, (id as NSString).utf8String, -1, nil)
                sqlite3_step(stmt)
            }
            sqlite3_finalize(stmt)
        }
    }

    // MARK: - Playlists
    public func getAllPlaylists() -> [Playlist] {
        queue.sync {
            var playlists: [Playlist] = []
            let sql = "SELECT id, name, created_at_millis FROM playlists ORDER BY name ASC;"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                while sqlite3_step(stmt) == SQLITE_ROW {
                    let id = String(cString: sqlite3_column_text(stmt, 0))
                    let name = String(cString: sqlite3_column_text(stmt, 1))
                    let created = sqlite3_column_int64(stmt, 2)
                    playlists.append(Playlist(id: id, name: name, mediaIds: [], createdAtMillis: created))
                }
            }
            sqlite3_finalize(stmt)

            for i in 0..<playlists.count {
                var mediaIds: [String] = []
                let itemSql = "SELECT media_id FROM playlist_items WHERE playlist_id = ? ORDER BY position ASC;"
                var itemStmt: OpaquePointer?
                if sqlite3_prepare_v2(db, itemSql, -1, &itemStmt, nil) == SQLITE_OK {
                    sqlite3_bind_text(itemStmt, 1, (playlists[i].id as NSString).utf8String, -1, nil)
                    while sqlite3_step(itemStmt) == SQLITE_ROW {
                        mediaIds.append(String(cString: sqlite3_column_text(itemStmt, 0)))
                    }
                }
                sqlite3_finalize(itemStmt)
                playlists[i].mediaIds = mediaIds
            }
            return playlists
        }
    }

    public func savePlaylist(_ playlist: Playlist) {
        queue.sync {
            sqlite3_exec(db, "BEGIN TRANSACTION;", nil, nil, nil)
            let sql = "INSERT OR REPLACE INTO playlists (id, name, created_at_millis) VALUES (?, ?, ?);"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                sqlite3_bind_text(stmt, 1, (playlist.id as NSString).utf8String, -1, nil)
                sqlite3_bind_text(stmt, 2, (playlist.name as NSString).utf8String, -1, nil)
                sqlite3_bind_int64(stmt, 3, playlist.createdAtMillis)
                sqlite3_step(stmt)
            }
            sqlite3_finalize(stmt)

            let delSql = "DELETE FROM playlist_items WHERE playlist_id = ?;"
            var delStmt: OpaquePointer?
            if sqlite3_prepare_v2(db, delSql, -1, &delStmt, nil) == SQLITE_OK {
                sqlite3_bind_text(delStmt, 1, (playlist.id as NSString).utf8String, -1, nil)
                sqlite3_step(delStmt)
            }
            sqlite3_finalize(delStmt)

            for (idx, mediaId) in playlist.mediaIds.enumerated() {
                let insSql = "INSERT INTO playlist_items (playlist_id, media_id, position) VALUES (?, ?, ?);"
                var insStmt: OpaquePointer?
                if sqlite3_prepare_v2(db, insSql, -1, &insStmt, nil) == SQLITE_OK {
                    sqlite3_bind_text(insStmt, 1, (playlist.id as NSString).utf8String, -1, nil)
                    sqlite3_bind_text(insStmt, 2, (mediaId as NSString).utf8String, -1, nil)
                    sqlite3_bind_int(insStmt, 3, Int32(idx))
                    sqlite3_step(insStmt)
                }
                sqlite3_finalize(insStmt)
            }
            sqlite3_exec(db, "COMMIT;", nil, nil, nil)
        }
    }

    public func deletePlaylist(id: String) {
        queue.sync {
            let sql = "DELETE FROM playlists WHERE id = ?;"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                sqlite3_bind_text(stmt, 1, (id as NSString).utf8String, -1, nil)
                sqlite3_step(stmt)
            }
            sqlite3_finalize(stmt)
        }
    }

    // MARK: - Favorites
    public func getFavorites() -> Set<String> {
        queue.sync {
            var set = Set<String>()
            let sql = "SELECT media_id FROM favorites;"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                while sqlite3_step(stmt) == SQLITE_ROW {
                    set.insert(String(cString: sqlite3_column_text(stmt, 0)))
                }
            }
            sqlite3_finalize(stmt)
            return set
        }
    }

    public func setFavorite(mediaId: String, isFavorite: Bool) {
        queue.sync {
            if isFavorite {
                let sql = "INSERT OR IGNORE INTO favorites (media_id) VALUES (?);"
                var stmt: OpaquePointer?
                if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                    sqlite3_bind_text(stmt, 1, (mediaId as NSString).utf8String, -1, nil)
                    sqlite3_step(stmt)
                }
                sqlite3_finalize(stmt)
            } else {
                let sql = "DELETE FROM favorites WHERE media_id = ?;"
                var stmt: OpaquePointer?
                if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                    sqlite3_bind_text(stmt, 1, (mediaId as NSString).utf8String, -1, nil)
                    sqlite3_step(stmt)
                }
                sqlite3_finalize(stmt)
            }
        }
    }

    // MARK: - History & Resume
    public func recordPlayback(mediaId: String, positionMillis: Int64, completed: Bool) {
        queue.sync {
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let sql = """
            INSERT INTO playback_history (id, media_id, last_played_at_millis, position_millis, completed, play_count)
            VALUES (?, ?, ?, ?, ?, 1)
            ON CONFLICT(id) DO UPDATE SET
                last_played_at_millis = excluded.last_played_at_millis,
                position_millis = excluded.position_millis,
                completed = excluded.completed,
                play_count = play_count + 1;
            """
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                sqlite3_bind_text(stmt, 1, (mediaId as NSString).utf8String, -1, nil)
                sqlite3_bind_text(stmt, 2, (mediaId as NSString).utf8String, -1, nil)
                sqlite3_bind_int64(stmt, 3, now)
                sqlite3_bind_int64(stmt, 4, positionMillis)
                sqlite3_bind_int(stmt, 5, completed ? 1 : 0)
                sqlite3_step(stmt)
            }
            sqlite3_finalize(stmt)
        }
    }

    public func getHistory() -> [PlaybackHistory] {
        queue.sync {
            var list: [PlaybackHistory] = []
            let sql = "SELECT id, media_id, last_played_at_millis, position_millis, completed, play_count FROM playback_history ORDER BY last_played_at_millis DESC LIMIT 100;"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                while sqlite3_step(stmt) == SQLITE_ROW {
                    list.append(PlaybackHistory(
                        id: String(cString: sqlite3_column_text(stmt, 0)),
                        mediaId: String(cString: sqlite3_column_text(stmt, 1)),
                        lastPlayedAtMillis: sqlite3_column_int64(stmt, 2),
                        positionMillis: sqlite3_column_int64(stmt, 3),
                        completed: sqlite3_column_int(stmt, 4) != 0,
                        playCount: Int(sqlite3_column_int(stmt, 5))
                    ))
                }
            }
            sqlite3_finalize(stmt)
            return list
        }
    }
}
