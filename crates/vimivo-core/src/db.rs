//! SQLite Database and Schema Migrations for Vimivo Core.

use crate::model::*;
use rusqlite::{params, Connection, OptionalExtension, Result};
use std::path::Path;

pub struct VimivoDatabase {
    conn: Connection,
}

pub const CURRENT_SCHEMA_VERSION: i32 = 7;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StoredResumeState {
    pub position_millis: i64,
    pub completed: bool,
}

impl VimivoDatabase {
    pub fn open(path: impl AsRef<Path>) -> Result<Self> {
        let conn = Connection::open(path)?;
        let mut db = Self { conn };
        db.migrate()?;
        Ok(db)
    }

    pub fn open_in_memory() -> Result<Self> {
        let conn = Connection::open_in_memory()?;
        let mut db = Self { conn };
        db.migrate()?;
        Ok(db)
    }

    pub fn version(&self) -> Result<i32> {
        self.conn
            .query_row("PRAGMA user_version", [], |row| row.get(0))
    }

    pub fn set_version(&self, version: i32) -> Result<()> {
        self.conn
            .execute(&format!("PRAGMA user_version = {}", version), [])?;
        Ok(())
    }

    pub fn migrate(&mut self) -> Result<()> {
        let from = self.version()?;

        if from < 1 {
            let tx = self.conn.transaction()?;
            tx.execute_batch(
                r#"
                CREATE TABLE IF NOT EXISTS media_items (
                    id TEXT PRIMARY KEY,
                    uri TEXT NOT NULL UNIQUE,
                    title TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    duration_millis INTEGER,
                    artist TEXT,
                    album TEXT,
                    artwork_uri TEXT,
                    album_artist TEXT,
                    genre TEXT,
                    size_bytes INTEGER,
                    mime_type TEXT,
                    file_name TEXT,
                    date_added_epoch_seconds INTEGER,
                    date_modified_epoch_seconds INTEGER,
                    width INTEGER,
                    height INTEGER,
                    frame_rate REAL,
                    bitrate INTEGER,
                    track_number INTEGER,
                    disc_number INTEGER,
                    release_year INTEGER,
                    folder_name TEXT,
                    folder_path TEXT,
                    source TEXT NOT NULL DEFAULT 'LOCAL_FILE',
                    volume_name TEXT,
                    bookmark_data BLOB,
                    is_available INTEGER NOT NULL DEFAULT 1
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
                    media_id TEXT PRIMARY KEY,
                    created_at_millis INTEGER NOT NULL DEFAULT 0
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
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                "#,
            )?;
            Self::ensure_media_columns(&tx)?;
            tx.execute("PRAGMA user_version = 1", [])?;
            tx.commit()?;
        }

        if from < 2 {
            let tx = self.conn.transaction()?;
            tx.execute_batch(
                r#"
                CREATE TABLE IF NOT EXISTS lyrics (
                    media_id TEXT PRIMARY KEY,
                    source TEXT NOT NULL,
                    text TEXT NOT NULL,
                    saved_at_millis INTEGER NOT NULL
                );
                "#,
            )?;
            tx.execute("PRAGMA user_version = 2", [])?;
            tx.commit()?;
        }

        if from < 3 {
            let tx = self.conn.transaction()?;
            tx.execute_batch(
                r#"
                CREATE TABLE IF NOT EXISTS playback_positions (
                    media_id TEXT PRIMARY KEY,
                    position_ms INTEGER NOT NULL,
                    duration_ms INTEGER,
                    completed INTEGER NOT NULL DEFAULT 0,
                    updated_at_ms INTEGER NOT NULL
                );
                "#,
            )?;
            tx.execute("PRAGMA user_version = 3", [])?;
            tx.commit()?;
        }

        if from < 4 {
            let tx = self.conn.transaction()?;
            tx.execute_batch(
                r#"
                CREATE TABLE IF NOT EXISTS user_metadata_overrides (
                    media_id TEXT PRIMARY KEY,
                    title TEXT,
                    artist TEXT,
                    album TEXT,
                    updated_at_ms INTEGER NOT NULL
                );
                "#,
            )?;
            tx.execute("PRAGMA user_version = 4", [])?;
            tx.commit()?;
        }

        if from < 5 {
            let tx = self.conn.transaction()?;
            Self::ensure_media_columns(&tx)?;
            tx.execute_batch(
                r#"
                CREATE INDEX IF NOT EXISTS idx_media_artist ON media_items(artist);
                CREATE INDEX IF NOT EXISTS idx_media_album ON media_items(album);
                CREATE INDEX IF NOT EXISTS idx_media_folder ON media_items(folder_path);
                "#,
            )?;
            tx.execute("PRAGMA user_version = 5", [])?;
            tx.commit()?;
        }

        if from < 6 {
            let tx = self.conn.transaction()?;
            tx.execute_batch(
                r#"
                CREATE TABLE IF NOT EXISTS smart_playlists (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    rule_json TEXT NOT NULL,
                    created_at_ms INTEGER NOT NULL
                );
                "#,
            )?;
            tx.execute("PRAGMA user_version = 6", [])?;
            tx.commit()?;
        }

        if from < 7 {
            let tx = self.conn.transaction()?;
            let has_col: bool = tx
                .query_row(
                    "SELECT COUNT(*) FROM pragma_table_info('media_items') WHERE name = 'bookmark_data'",
                    [],
                    |r| r.get::<_, i64>(0),
                )
                .map(|count| count > 0)
                .unwrap_or(false);
            if !has_col {
                tx.execute_batch("ALTER TABLE media_items ADD COLUMN bookmark_data BLOB;")?;
            }
            tx.execute("PRAGMA user_version = 7", [])?;
            tx.commit()?;
        }

        Ok(())
    }

    // -----------------------------------------------------------------------
    // Media Items
    // -----------------------------------------------------------------------

    pub fn upsert_media_item(&self, item: &MediaItem) -> Result<()> {
        let kind_str = match item.kind {
            MediaKind::Audio => "AUDIO",
            MediaKind::Video => "VIDEO",
            MediaKind::Stream => "STREAM",
        };
        let source_str = match item.source {
            MediaSource::MediaStore => "MEDIA_STORE",
            MediaSource::SafFile => "SAF_FILE",
            MediaSource::SafTree => "SAF_TREE",
            MediaSource::Network => "NETWORK",
            MediaSource::LocalFile => "LOCAL_FILE",
        };

        self.conn.execute(
            r#"
            INSERT INTO media_items (
                id, uri, title, kind, duration_millis, artist, album, artwork_uri,
                album_artist, genre, size_bytes, mime_type, file_name,
                date_added_epoch_seconds, date_modified_epoch_seconds,
                width, height, frame_rate, bitrate, track_number, disc_number,
                release_year, folder_name, folder_path, source, volume_name, bookmark_data, is_available
            ) VALUES (
                ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13,
                ?14, ?15, ?16, ?17, ?18, ?19, ?20, ?21, ?22, ?23, ?24, ?25, ?26, ?27, ?28
            )
            ON CONFLICT(id) DO UPDATE SET
                uri = excluded.uri,
                title = excluded.title,
                kind = excluded.kind,
                duration_millis = excluded.duration_millis,
                artist = excluded.artist,
                album = excluded.album,
                artwork_uri = excluded.artwork_uri,
                album_artist = excluded.album_artist,
                genre = excluded.genre,
                size_bytes = excluded.size_bytes,
                mime_type = excluded.mime_type,
                file_name = excluded.file_name,
                width = excluded.width,
                height = excluded.height,
                frame_rate = excluded.frame_rate,
                bitrate = excluded.bitrate,
                release_year = excluded.release_year,
                folder_path = excluded.folder_path,
                bookmark_data = COALESCE(excluded.bookmark_data, media_items.bookmark_data),
                is_available = excluded.is_available
            "#,
            params![
                item.id.0,
                item.uri,
                item.title,
                kind_str,
                item.duration_millis,
                item.artist,
                item.album,
                item.artwork_uri,
                item.album_artist,
                item.genre,
                item.size_bytes,
                item.mime_type,
                item.file_name,
                item.date_added_epoch_seconds,
                item.date_modified_epoch_seconds,
                item.width,
                item.height,
                item.frame_rate,
                item.bitrate,
                item.track_number,
                item.disc_number,
                item.release_year,
                item.folder_name,
                item.folder_path,
                source_str,
                item.volume_name,
                item.bookmark_data.as_deref(),
                if item.is_available { 1 } else { 0 },
            ],
        )?;

        Ok(())
    }

    pub fn get_media_item(&self, id: &str) -> Result<Option<MediaItem>> {
        self.conn
            .query_row(
                "SELECT * FROM media_items WHERE id = ?1",
                params![id],
                |row| Self::row_to_media_item(row),
            )
            .optional()
    }

    pub fn query_media_items(&self, query: &LibraryQuery) -> Result<LibraryPage> {
        let mut sql = String::from("SELECT * FROM media_items WHERE 1=1 ");
        let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

        if !query.text.is_empty() {
            sql.push_str("AND (title LIKE ? OR artist LIKE ? OR album LIKE ?) ");
            let pattern = format!("%{}%", query.text);
            params_vec.push(Box::new(pattern.clone()));
            params_vec.push(Box::new(pattern.clone()));
            params_vec.push(Box::new(pattern));
        }

        if let Some(kind) = query.kind {
            sql.push_str("AND kind = ? ");
            let kind_str = match kind {
                MediaKind::Audio => "AUDIO",
                MediaKind::Video => "VIDEO",
                MediaKind::Stream => "STREAM",
            };
            params_vec.push(Box::new(kind_str));
        }

        if let Some(artist) = &query.artist {
            sql.push_str("AND artist = ? ");
            params_vec.push(Box::new(artist.clone()));
        }

        if let Some(album) = &query.album {
            sql.push_str("AND album = ? ");
            params_vec.push(Box::new(album.clone()));
        }

        if let Some(folder) = &query.folder_path {
            sql.push_str("AND folder_path = ? ");
            params_vec.push(Box::new(folder.clone()));
        }

        // Sorting
        let sort_col = match query.sort {
            LibrarySort::Title => "title",
            LibrarySort::Artist => "artist",
            LibrarySort::Album => "album",
            LibrarySort::Duration => "duration_millis",
            LibrarySort::DateAdded => "date_added_epoch_seconds",
            LibrarySort::DateModified => "date_modified_epoch_seconds",
            LibrarySort::FileSize => "size_bytes",
            LibrarySort::TrackNumber => "track_number",
            LibrarySort::LastPlayed => "date_modified_epoch_seconds",
        };

        let dir = if query.descending { "DESC" } else { "ASC" };
        sql.push_str(&format!("ORDER BY {} COLLATE NOCASE {} ", sort_col, dir));

        // Total count
        let count_sql = format!("SELECT COUNT(*) FROM ({})", sql);
        let params_refs: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|b| b.as_ref()).collect();
        let total_count: u32 = self.conn.query_row(&count_sql, params_refs.as_slice(), |r| r.get(0))?;

        // Limit & offset
        sql.push_str("LIMIT ? OFFSET ?");
        params_vec.push(Box::new(query.limit));
        params_vec.push(Box::new(query.offset));

        let params_refs: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|b| b.as_ref()).collect();
        let mut stmt = self.conn.prepare(&sql)?;
        let rows = stmt.query_map(params_refs.as_slice(), |row| Self::row_to_media_item(row))?;

        let mut items = Vec::new();
        for r in rows {
            items.push(r?);
        }

        Ok(LibraryPage {
            items,
            offset: query.offset,
            total_count,
        })
    }

    fn ensure_media_columns(conn: &rusqlite::Connection) -> Result<()> {
        Self::ensure_column(conn, "media_items", "genre", "TEXT")?;
        Self::ensure_column(conn, "media_items", "size_bytes", "INTEGER")?;
        Self::ensure_column(conn, "media_items", "mime_type", "TEXT")?;
        Self::ensure_column(conn, "media_items", "file_name", "TEXT")?;
        Self::ensure_column(conn, "media_items", "date_added_epoch_seconds", "INTEGER")?;
        Self::ensure_column(conn, "media_items", "date_modified_epoch_seconds", "INTEGER")?;
        Self::ensure_column(conn, "media_items", "width", "INTEGER")?;
        Self::ensure_column(conn, "media_items", "height", "INTEGER")?;
        Self::ensure_column(conn, "media_items", "frame_rate", "REAL")?;
        Self::ensure_column(conn, "media_items", "bitrate", "INTEGER")?;
        Self::ensure_column(conn, "media_items", "track_number", "INTEGER")?;
        Self::ensure_column(conn, "media_items", "disc_number", "INTEGER")?;
        Self::ensure_column(conn, "media_items", "release_year", "INTEGER")?;
        Self::ensure_column(conn, "media_items", "folder_name", "TEXT")?;
        Self::ensure_column(conn, "media_items", "folder_path", "TEXT")?;
        Self::ensure_column(conn, "media_items", "source", "TEXT NOT NULL DEFAULT 'LOCAL_FILE'")?;
        Self::ensure_column(conn, "media_items", "volume_name", "TEXT")?;
        Self::ensure_column(conn, "media_items", "bookmark_data", "BLOB")?;
        Self::ensure_column(conn, "media_items", "is_available", "INTEGER NOT NULL DEFAULT 1")?;
        Self::ensure_column(conn, "favorites", "created_at_millis", "INTEGER NOT NULL DEFAULT 0")?;
        Ok(())
    }

    fn ensure_column(
        conn: &rusqlite::Connection,
        table: &str,
        column: &str,
        col_type_sql: &str,
    ) -> Result<()> {
        let mut stmt = conn.prepare(&format!("PRAGMA table_info({})", table))?;
        let mut rows = stmt.query([])?;
        let mut exists = false;
        while let Some(row) = rows.next()? {
            let name: String = row.get(1)?;
            if name.eq_ignore_ascii_case(column) {
                exists = true;
                break;
            }
        }
        if !exists {
            conn.execute(
                &format!("ALTER TABLE {} ADD COLUMN {} {}", table, column, col_type_sql),
                [],
            )?;
        }
        Ok(())
    }

    fn row_to_media_item(row: &rusqlite::Row) -> rusqlite::Result<MediaItem> {
        let kind_str: String = row.get("kind")?;
        let kind = match kind_str.to_uppercase().as_str() {
            "AUDIO" => MediaKind::Audio,
            "VIDEO" => MediaKind::Video,
            _ => MediaKind::Stream,
        };

        let source_str: String = row
            .get::<_, Option<String>>("source")?
            .unwrap_or_else(|| "LOCAL_FILE".to_string());
        let source = match source_str.to_uppercase().as_str() {
            "MEDIA_STORE" => MediaSource::MediaStore,
            "SAF_FILE" => MediaSource::SafFile,
            "SAF_TREE" => MediaSource::SafTree,
            "NETWORK" => MediaSource::Network,
            _ => MediaSource::LocalFile,
        };

        let is_available_int: i32 = row.get::<_, Option<i32>>("is_available")?.unwrap_or(1);

        Ok(MediaItem {
            id: MediaId(row.get("id")?),
            uri: row.get("uri")?,
            title: row.get("title")?,
            kind,
            duration_millis: row.get("duration_millis")?,
            artist: row.get("artist")?,
            album: row.get("album")?,
            artwork_uri: row.get("artwork_uri")?,
            album_artist: row.get("album_artist")?,
            genre: row.get("genre")?,
            size_bytes: row.get("size_bytes")?,
            mime_type: row.get("mime_type")?,
            file_name: row.get("file_name")?,
            date_added_epoch_seconds: row.get("date_added_epoch_seconds")?,
            date_modified_epoch_seconds: row.get("date_modified_epoch_seconds")?,
            width: row.get("width")?,
            height: row.get("height")?,
            frame_rate: row.get("frame_rate")?,
            bitrate: row.get("bitrate")?,
            track_number: row.get("track_number")?,
            disc_number: row.get("disc_number")?,
            release_year: row.get("release_year")?,
            folder_name: row.get("folder_name")?,
            folder_path: row.get("folder_path")?,
            source,
            volume_name: row.get("volume_name")?,
            is_favorite: false,
            is_available: is_available_int != 0,
            bookmark_data: row.get("bookmark_data").ok().flatten(),
            last_playback_position_millis: 0,
            last_played_at_epoch_millis: None,
        })
    }

    // -----------------------------------------------------------------------
    // Favorites & Playlists
    // -----------------------------------------------------------------------

    pub fn set_favorite(&self, media_id: &str, is_fav: bool) -> Result<()> {
        if is_fav {
            self.conn.execute(
                "INSERT OR IGNORE INTO favorites (media_id, created_at_millis) VALUES (?1, ?2)",
                params![media_id, 0],
            )?;
        } else {
            self.conn.execute(
                "DELETE FROM favorites WHERE media_id = ?1",
                params![media_id],
            )?;
        }
        Ok(())
    }

    pub fn is_favorite(&self, media_id: &str) -> Result<bool> {
        let count: i32 = self.conn.query_row(
            "SELECT COUNT(*) FROM favorites WHERE media_id = ?1",
            params![media_id],
            |r| r.get(0),
        )?;
        Ok(count > 0)
    }

    pub fn create_playlist(&self, id: &str, name: &str, created_at_millis: i64) -> Result<()> {
        self.conn.execute(
            "INSERT INTO playlists (id, name, created_at_millis) VALUES (?1, ?2, ?3)",
            params![id, name, created_at_millis],
        )?;
        Ok(())
    }

    pub fn add_to_playlist(&self, playlist_id: &str, media_id: &str, position: i32) -> Result<()> {
        self.conn.execute(
            "INSERT OR REPLACE INTO playlist_items (playlist_id, media_id, position) VALUES (?1, ?2, ?3)",
            params![playlist_id, media_id, position],
        )?;
        Ok(())
    }

    pub fn get_playlist_items(&self, playlist_id: &str) -> Result<Vec<MediaItem>> {
        let mut stmt = self.conn.prepare(
            r#"
            SELECT m.* FROM media_items m
            INNER JOIN playlist_items p ON m.id = p.media_id
            WHERE p.playlist_id = ?1
            ORDER BY p.position ASC
            "#,
        )?;
        let rows = stmt.query_map(params![playlist_id], |row| Self::row_to_media_item(row))?;
        let mut items = Vec::new();
        for r in rows {
            items.push(r?);
        }
        Ok(items)
    }

    // -----------------------------------------------------------------------
    // Lyrics & Playback Positions
    // -----------------------------------------------------------------------

    pub fn save_lyrics(&self, media_id: &str, source: &str, text: &str) -> Result<()> {
        self.conn.execute(
            r#"
            INSERT OR REPLACE INTO lyrics (media_id, source, text, saved_at_millis)
            VALUES (?1, ?2, ?3, ?4)
            "#,
            params![media_id, source, text, 0],
        )?;
        Ok(())
    }

    pub fn get_lyrics(&self, media_id: &str) -> Result<Option<String>> {
        self.conn
            .query_row(
                "SELECT text FROM lyrics WHERE media_id = ?1",
                params![media_id],
                |r| r.get(0),
            )
            .optional()
    }

    pub fn get_lyrics_record(&self, media_id: &str) -> Result<Option<(String, String)>> {
        self.conn
            .query_row(
                "SELECT source, text FROM lyrics WHERE media_id = ?1",
                params![media_id],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .optional()
    }

    pub fn update_playback_position(&self, media_id: &str, position_ms: i64, duration_ms: Option<i64>) -> Result<()> {
        self.conn.execute(
            r#"
            INSERT OR REPLACE INTO playback_positions (media_id, position_ms, duration_ms, completed, updated_at_ms)
            VALUES (?1, ?2, ?3, 0, ?4)
            "#,
            params![media_id, position_ms, duration_ms, 0],
        )?;
        Ok(())
    }

    pub fn get_playback_position(&self, media_id: &str) -> Result<Option<i64>> {
        self.conn
            .query_row(
                "SELECT position_ms FROM playback_positions WHERE media_id = ?1",
                params![media_id],
                |r| r.get(0),
            )
            .optional()
    }

    // -----------------------------------------------------------------------
    // Bookmarks
    // -----------------------------------------------------------------------

    pub fn add_bookmark(&self, bookmark: &StoredBookmark) -> Result<()> {
        self.conn.execute(
            "INSERT INTO bookmarks (id, media_id, name, position_millis, created_at_millis)
             VALUES (?1, ?2, ?3, ?4, ?5)
             ON CONFLICT(id) DO UPDATE SET name = excluded.name, position_millis = excluded.position_millis",
            params![
                bookmark.id,
                bookmark.media_id,
                bookmark.name,
                bookmark.position_millis,
                bookmark.created_at_millis
            ],
        )?;
        Ok(())
    }

    pub fn get_bookmarks(&self, media_id: &str) -> Result<Vec<StoredBookmark>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, media_id, name, position_millis, created_at_millis FROM bookmarks WHERE media_id = ?1 ORDER BY position_millis ASC",
        )?;
        let rows = stmt.query_map(params![media_id], |r| {
            Ok(StoredBookmark {
                id: r.get(0)?,
                media_id: r.get(1)?,
                name: r.get(2)?,
                position_millis: r.get(3)?,
                created_at_millis: r.get(4)?,
            })
        })?;
        let mut list = Vec::new();
        for r in rows {
            list.push(r?);
        }
        Ok(list)
    }

    pub fn delete_bookmark(&self, id: &str) -> Result<()> {
        self.conn.execute("DELETE FROM bookmarks WHERE id = ?1", params![id])?;
        Ok(())
    }

    // -----------------------------------------------------------------------
    // Playback History & Resume State
    // -----------------------------------------------------------------------

    pub fn record_play_start(&self, media_id: &str, played_at_millis: i64) -> Result<()> {
        let existing: Option<(String, i32)> = self
            .conn
            .query_row(
                "SELECT id, play_count FROM playback_history WHERE media_id = ?1",
                params![media_id],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .optional()?;

        if let Some((id, count)) = existing {
            self.conn.execute(
                "UPDATE playback_history SET last_played_at_millis = ?1, play_count = ?2 WHERE id = ?3",
                params![played_at_millis, count + 1, id],
            )?;
        } else {
            let id = format!("hist-{}", media_id);
            self.conn.execute(
                "INSERT INTO playback_history (id, media_id, last_played_at_millis, position_millis, completed, play_count) VALUES (?1, ?2, ?3, 0, 0, 1)",
                params![id, media_id, played_at_millis],
            )?;
        }
        Ok(())
    }

    pub fn save_position(&self, media_id: &str, position_millis: i64, completed: bool, updated_at_millis: i64) -> Result<()> {
        self.update_playback_position(media_id, position_millis, None)?;
        let existing: Option<(String,)> = self
            .conn
            .query_row(
                "SELECT id FROM playback_history WHERE media_id = ?1",
                params![media_id],
                |r| Ok((r.get(0)?,)),
            )
            .optional()?;

        if let Some((id,)) = existing {
            self.conn.execute(
                "UPDATE playback_history SET position_millis = ?1, completed = ?2, last_played_at_millis = ?3 WHERE id = ?4",
                params![position_millis, if completed { 1 } else { 0 }, updated_at_millis, id],
            )?;
        } else {
            let id = format!("hist-{}", media_id);
            self.conn.execute(
                "INSERT INTO playback_history (id, media_id, last_played_at_millis, position_millis, completed, play_count) VALUES (?1, ?2, ?3, ?4, ?5, 0)",
                params![id, media_id, updated_at_millis, position_millis, if completed { 1 } else { 0 }],
            )?;
        }
        Ok(())
    }

    pub fn get_resume_state(&self, media_id: &str) -> Result<Option<StoredResumeState>> {
        self.conn
            .query_row(
                "SELECT position_millis, completed FROM playback_history WHERE media_id = ?1",
                params![media_id],
                |r| {
                    let position_millis: i64 = r.get(0)?;
                    let completed_int: i32 = r.get(1)?;
                    Ok(StoredResumeState {
                        position_millis,
                        completed: completed_int != 0,
                    })
                },
            )
            .optional()
    }

    pub fn get_history(&self) -> Result<Vec<StoredHistory>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, media_id, last_played_at_millis, position_millis, completed, play_count FROM playback_history ORDER BY last_played_at_millis DESC",
        )?;
        let rows = stmt.query_map([], |r| {
            let completed_int: i32 = r.get(4)?;
            Ok(StoredHistory {
                id: r.get(0)?,
                media_id: r.get(1)?,
                last_played_at_millis: r.get(2)?,
                position_millis: r.get(3)?,
                completed: completed_int != 0,
                play_count: r.get(5)?,
            })
        })?;
        let mut list = Vec::new();
        for r in rows {
            list.push(r?);
        }
        Ok(list)
    }

    // -----------------------------------------------------------------------
    // Settings & Queue Persistence
    // -----------------------------------------------------------------------

    pub fn set_setting(&self, key: &str, value: &str) -> Result<()> {
        self.conn.execute(
            "INSERT INTO settings (key, value) VALUES (?1, ?2) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            params![key, value],
        )?;
        Ok(())
    }

    pub fn get_setting(&self, key: &str) -> Result<Option<String>> {
        self.conn
            .query_row("SELECT value FROM settings WHERE key = ?1", params![key], |r| r.get(0))
            .optional()
    }

    pub fn save_queue(&self, media_ids: &[String], current_index: i32) -> Result<()> {
        let clamped = if media_ids.is_empty() {
            0
        } else if current_index < 0 || current_index as usize >= media_ids.len() {
            0
        } else {
            current_index
        };
        self.set_setting("queue_ids", &media_ids.join("\n"))?;
        self.set_setting("queue_index", &clamped.to_string())?;
        Ok(())
    }

    pub fn load_queue(&self) -> Result<(Vec<String>, i32)> {
        let ids_str = self.get_setting("queue_ids")?.unwrap_or_default();
        let index_str = self.get_setting("queue_index")?.unwrap_or_else(|| "0".into());
        let ids: Vec<String> = if ids_str.is_empty() {
            Vec::new()
        } else {
            ids_str.lines().map(|s| s.to_string()).collect()
        };
        let mut index = index_str.parse::<i32>().unwrap_or(0);
        if ids.is_empty() {
            index = 0;
        } else if index < 0 || index as usize >= ids.len() {
            index = 0;
        }
        Ok((ids, index))
    }

    pub fn delete_media_item(&self, id: &str) -> Result<()> {
        self.conn.execute("DELETE FROM media_items WHERE id = ?1", params![id])?;
        self.conn.execute("DELETE FROM favorites WHERE media_id = ?1", params![id])?;
        self.conn.execute("DELETE FROM playlist_items WHERE media_id = ?1", params![id])?;
        self.conn.execute("DELETE FROM playback_positions WHERE media_id = ?1", params![id])?;
        self.conn.execute("DELETE FROM lyrics WHERE media_id = ?1", params![id])?;
        Ok(())
    }

    pub fn update_media_uri(&self, id: &str, uri: &str) -> Result<()> {
        self.conn.execute(
            "UPDATE media_items SET uri = ?1 WHERE id = ?2",
            params![uri, id],
        )?;
        Ok(())
    }

    pub fn update_video_metadata(
        &self,
        id: &str,
        width: Option<i32>,
        height: Option<i32>,
        size_bytes: Option<i64>,
    ) -> Result<()> {
        let mut clauses = Vec::new();
        let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

        if let Some(w) = width {
            clauses.push("width = ?");
            params_vec.push(Box::new(w));
        }
        if let Some(h) = height {
            clauses.push("height = ?");
            params_vec.push(Box::new(h));
        }
        if let Some(s) = size_bytes {
            clauses.push("size_bytes = ?");
            params_vec.push(Box::new(s));
        }
        if clauses.is_empty() {
            return Ok(());
        }

        let sql = format!("UPDATE media_items SET {} WHERE id = ?", clauses.join(", "));
        params_vec.push(Box::new(id.to_string()));

        let params_refs: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|b| b.as_ref()).collect();
        self.conn.execute(&sql, params_refs.as_slice())?;
        Ok(())
    }

    pub fn get_all_media_items(&self) -> Result<Vec<MediaItem>> {
        let mut stmt = self.conn.prepare("SELECT * FROM media_items ORDER BY title COLLATE NOCASE ASC")?;
        let rows = stmt.query_map([], |row| Self::row_to_media_item(row))?;
        let mut items = Vec::new();
        for r in rows {
            items.push(r?);
        }
        Ok(items)
    }

    pub fn get_all_favorites(&self) -> Result<Vec<String>> {
        let mut stmt = self.conn.prepare("SELECT media_id FROM favorites")?;
        let rows = stmt.query_map([], |row| row.get(0))?;
        let mut favs = Vec::new();
        for r in rows {
            favs.push(r?);
        }
        Ok(favs)
    }

    pub fn save_playlist(
        &self,
        id: &str,
        name: &str,
        media_ids: &[String],
        created_at_millis: i64,
    ) -> Result<()> {
        let tx = self.conn.unchecked_transaction()?;
        tx.execute(
            "INSERT INTO playlists (id, name, created_at_millis) VALUES (?1, ?2, ?3)
             ON CONFLICT(id) DO UPDATE SET name = excluded.name",
            params![id, name, created_at_millis],
        )?;
        tx.execute("DELETE FROM playlist_items WHERE playlist_id = ?1", params![id])?;
        for (idx, mid) in media_ids.iter().enumerate() {
            tx.execute(
                "INSERT INTO playlist_items (playlist_id, media_id, position) VALUES (?1, ?2, ?3)",
                params![id, mid, idx as i32],
            )?;
        }
        tx.commit()?;
        Ok(())
    }

    pub fn delete_playlist(&self, id: &str) -> Result<()> {
        let tx = self.conn.unchecked_transaction()?;
        tx.execute("DELETE FROM playlist_items WHERE playlist_id = ?1", params![id])?;
        tx.execute("DELETE FROM playlists WHERE id = ?1", params![id])?;
        tx.commit()?;
        Ok(())
    }

    pub fn get_all_playlists(&self) -> Result<Vec<StoredPlaylist>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, name, created_at_millis FROM playlists ORDER BY created_at_millis DESC",
        )?;
        let rows = stmt.query_map([], |r| {
            Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?, r.get::<_, i64>(2)?))
        })?;

        let mut playlists = Vec::new();
        for r in rows {
            let (pid, name, created_at) = r?;
            let mut item_stmt = self.conn.prepare(
                "SELECT media_id FROM playlist_items WHERE playlist_id = ?1 ORDER BY position ASC",
            )?;
            let item_rows = item_stmt.query_map(params![&pid], |ir| ir.get::<_, String>(0))?;
            let mut media_ids = Vec::new();
            for mid in item_rows {
                media_ids.push(mid?);
            }
            playlists.push(StoredPlaylist {
                id: pid,
                name,
                media_ids,
                created_at_millis: created_at,
            });
        }
        Ok(playlists)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StoredPlaylist {
    pub id: String,
    pub name: String,
    pub media_ids: Vec<String>,
    pub created_at_millis: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StoredBookmark {
    pub id: String,
    pub media_id: String,
    pub name: String,
    pub position_millis: i64,
    pub created_at_millis: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StoredHistory {
    pub id: String,
    pub media_id: String,
    pub last_played_at_millis: i64,
    pub position_millis: i64,
    pub completed: bool,
    pub play_count: i32,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_database_creation_and_migrations() {
        let db = VimivoDatabase::open_in_memory().unwrap();
        assert_eq!(db.version().unwrap(), CURRENT_SCHEMA_VERSION);
    }

    #[test]
    fn test_media_item_crud_and_query() {
        let db = VimivoDatabase::open_in_memory().unwrap();
        let item = MediaItem::new("item-1", "/path/to/song.mp3", "Solaris", MediaKind::Audio);
        db.upsert_media_item(&item).unwrap();

        let fetched = db.get_media_item("item-1").unwrap();
        assert!(fetched.is_some());
        assert_eq!(fetched.unwrap().title, "Solaris");

        let mut q = LibraryQuery::default();
        q.text = "Sola".into();
        let page = db.query_media_items(&q).unwrap();
        assert_eq!(page.total_count, 1);
        assert_eq!(page.items[0].id.0, "item-1");
    }

    #[test]
    fn test_playlist_and_favorites() {
        let db = VimivoDatabase::open_in_memory().unwrap();
        let item = MediaItem::new("item-1", "/path/to/song.mp3", "Solaris", MediaKind::Audio);
        db.upsert_media_item(&item).unwrap();

        db.set_favorite("item-1", true).unwrap();
        assert!(db.is_favorite("item-1").unwrap());

        db.create_playlist("pl-1", "Chill Mix", 1000).unwrap();
        db.add_to_playlist("pl-1", "item-1", 0).unwrap();

        let items = db.get_playlist_items("pl-1").unwrap();
        assert_eq!(items.len(), 1);
        assert_eq!(items[0].title, "Solaris");
    }

    #[test]
    fn test_bookmarks_and_history_and_queue() {
        let db = VimivoDatabase::open_in_memory().unwrap();

        // Bookmarks
        let bm = StoredBookmark {
            id: "bm-1".into(),
            media_id: "m-1".into(),
            name: "Best Drop".into(),
            position_millis: 45000,
            created_at_millis: 100000,
        };
        db.add_bookmark(&bm).unwrap();
        let bms = db.get_bookmarks("m-1").unwrap();
        assert_eq!(bms.len(), 1);
        assert_eq!(bms[0].name, "Best Drop");

        db.delete_bookmark("bm-1").unwrap();
        assert!(db.get_bookmarks("m-1").unwrap().is_empty());

        // History
        db.record_play_start("m-1", 1000).unwrap();
        db.save_position("m-1", 30000, false, 1050).unwrap();
        let hist = db.get_history().unwrap();
        assert_eq!(hist.len(), 1);
        assert_eq!(hist[0].media_id, "m-1");
        assert_eq!(hist[0].position_millis, 30000);
        assert_eq!(hist[0].play_count, 1);

        // Queue
        let ids = vec!["m-1".into(), "m-2".into()];
        db.save_queue(&ids, 1).unwrap();
        let (loaded_ids, loaded_idx) = db.load_queue().unwrap();
        assert_eq!(loaded_ids, ids);
        assert_eq!(loaded_idx, 1);
    }
}
