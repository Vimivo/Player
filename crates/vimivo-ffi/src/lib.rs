//! Vimivo FFI: UniFFI bindings for iOS, macOS, and Android.

use std::sync::Mutex;
use vimivo_core::audio::{ReplayGain, ReplayGainMode as CoreReplayGainMode};
use vimivo_core::db::{StoredBookmark, VimivoDatabase};
use vimivo_core::model::{ChapterInfo as CoreChapterInfo, LibraryQuery as CoreQuery, MediaItem as CoreMediaItem, MediaKind as CoreMediaKind, QueueRepeatMode as CoreRepeatMode};
use vimivo_core::parser::{LrcLyricsParser, PlaylistFormats, TitleParser};
use vimivo_core::queue::PlayQueue;
use vimivo_core::rename::RenameTemplate;
use vimivo_core::streaming::{StreamValidationResult, StreamValidator};
use vimivo_core::video::IntroSkip;

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum VimivoFfiError {
    #[error("Database error: {reason}")]
    DatabaseError { reason: String },
    #[error("Not found: {reason}")]
    NotFound { reason: String },
    #[error("Internal error: {reason}")]
    InternalError { reason: String },
}

// ---------------------------------------------------------------------------
// Title Parser
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct FfiCleanTitle {
    pub title: String,
    pub year: Option<i32>,
    pub season: Option<i32>,
    pub episode: Option<i32>,
    pub display: String,
}

#[uniffi::export]
pub fn clean_media_title(file_name: String) -> FfiCleanTitle {
    let clean = TitleParser::clean(&file_name);
    FfiCleanTitle {
        display: clean.display(),
        title: clean.title,
        year: clean.year,
        season: clean.season,
        episode: clean.episode,
    }
}

// ---------------------------------------------------------------------------
// Lyrics Parser
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct FfiLyricLine {
    pub time_millis: Option<i64>,
    pub text: String,
}

#[derive(uniffi::Record)]
pub struct FfiLyricsDocument {
    pub lines: Vec<FfiLyricLine>,
    pub synchronized: bool,
    pub offset_millis: i64,
}

#[uniffi::export]
pub fn parse_lrc_lyrics(content: String, source_uri: String) -> FfiLyricsDocument {
    let doc = LrcLyricsParser::parse(&content, &source_uri);
    FfiLyricsDocument {
        lines: doc
            .lines
            .into_iter()
            .map(|l| FfiLyricLine {
                time_millis: l.time_millis,
                text: l.text,
            })
            .collect(),
        synchronized: doc.synchronized,
        offset_millis: doc.offset_millis,
    }
}

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

#[derive(uniffi::Enum)]
pub enum FfiMediaKind {
    Audio,
    Video,
    Stream,
}

#[derive(uniffi::Enum)]
pub enum FfiQueueRepeatMode {
    Off,
    All,
    One,
}

#[derive(uniffi::Record)]
pub struct FfiMediaItem {
    pub id: String,
    pub uri: String,
    pub title: String,
    pub kind: FfiMediaKind,
    pub duration_millis: Option<i64>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub artwork_uri: Option<String>,
    pub width: Option<i32>,
    pub height: Option<i32>,
    pub size_bytes: Option<i64>,
}

#[derive(uniffi::Record)]
pub struct FfiPlaylist {
    pub id: String,
    pub name: String,
    pub media_ids: Vec<String>,
    pub created_at_millis: i64,
}

#[derive(uniffi::Record)]
pub struct FfiBookmark {
    pub id: String,
    pub media_id: String,
    pub name: String,
    pub position_millis: i64,
    pub created_at_millis: i64,
}

#[derive(uniffi::Record)]
pub struct FfiHistory {
    pub id: String,
    pub media_id: String,
    pub last_played_at_millis: i64,
    pub position_millis: i64,
    pub completed: bool,
    pub play_count: i32,
}

#[derive(uniffi::Record)]
pub struct FfiQueueState {
    pub media_ids: Vec<String>,
    pub current_index: i32,
}

impl From<CoreMediaItem> for FfiMediaItem {
    fn from(item: CoreMediaItem) -> Self {
        let kind = match item.kind {
            CoreMediaKind::Audio => FfiMediaKind::Audio,
            CoreMediaKind::Video => FfiMediaKind::Video,
            CoreMediaKind::Stream => FfiMediaKind::Stream,
        };
        FfiMediaItem {
            id: item.id.0,
            uri: item.uri,
            title: item.title,
            kind,
            duration_millis: item.duration_millis,
            artist: item.artist,
            album: item.album,
            artwork_uri: item.artwork_uri,
            width: item.width,
            height: item.height,
            size_bytes: item.size_bytes,
        }
    }
}

// ---------------------------------------------------------------------------
// Database Controller
// ---------------------------------------------------------------------------

#[derive(uniffi::Object)]
pub struct FfiDatabase {
    inner: Mutex<VimivoDatabase>,
}

#[uniffi::export]
impl FfiDatabase {
    #[uniffi::constructor]
    pub fn open(path: String) -> Result<Self, VimivoFfiError> {
        let db = VimivoDatabase::open(&path)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })?;
        Ok(Self { inner: Mutex::new(db) })
    }

    #[uniffi::constructor]
    pub fn open_in_memory() -> Result<Self, VimivoFfiError> {
        let db = VimivoDatabase::open_in_memory()
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })?;
        Ok(Self { inner: Mutex::new(db) })
    }

    pub fn upsert_item(&self, item: FfiMediaItem) -> Result<(), VimivoFfiError> {
        let kind = match item.kind {
            FfiMediaKind::Audio => CoreMediaKind::Audio,
            FfiMediaKind::Video => CoreMediaKind::Video,
            FfiMediaKind::Stream => CoreMediaKind::Stream,
        };
        let mut core_item = CoreMediaItem::new(item.id, item.uri, item.title, kind);
        core_item.duration_millis = item.duration_millis;
        core_item.artist = item.artist;
        core_item.album = item.album;
        core_item.artwork_uri = item.artwork_uri;
        core_item.width = item.width;
        core_item.height = item.height;
        core_item.size_bytes = item.size_bytes;

        let db = self.inner.lock().unwrap();
        db.upsert_media_item(&core_item)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn delete_item(&self, id: String) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.delete_media_item(&id)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn update_media_uri(&self, id: String, uri: String) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.update_media_uri(&id, &uri)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn update_video_metadata(
        &self,
        id: String,
        width: Option<i32>,
        height: Option<i32>,
        size_bytes: Option<i64>,
    ) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.update_video_metadata(&id, width, height, size_bytes)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn get_all_items(&self) -> Result<Vec<FfiMediaItem>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        let items = db.get_all_media_items()
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })?;
        Ok(items.into_iter().map(FfiMediaItem::from).collect())
    }

    pub fn get_item(&self, id: String) -> Result<Option<FfiMediaItem>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        let item = db.get_media_item(&id)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })?;
        Ok(item.map(FfiMediaItem::from))
    }

    pub fn query_items(&self, search_text: String) -> Result<Vec<FfiMediaItem>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        let mut q = CoreQuery::default();
        q.text = search_text;
        let page = db.query_media_items(&q)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })?;
        Ok(page.items.into_iter().map(FfiMediaItem::from).collect())
    }

    pub fn set_favorite(&self, media_id: String, is_fav: bool) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.set_favorite(&media_id, is_fav)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn is_favorite(&self, media_id: String) -> Result<bool, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.is_favorite(&media_id)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn get_favorites(&self) -> Result<Vec<String>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.get_all_favorites()
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn save_playlist(&self, playlist: FfiPlaylist) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.save_playlist(
            &playlist.id,
            &playlist.name,
            &playlist.media_ids,
            playlist.created_at_millis,
        )
        .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn delete_playlist(&self, id: String) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.delete_playlist(&id)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn get_playlists(&self) -> Result<Vec<FfiPlaylist>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        let list = db.get_all_playlists()
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })?;
        Ok(list
            .into_iter()
            .map(|p| FfiPlaylist {
                id: p.id,
                name: p.name,
                media_ids: p.media_ids,
                created_at_millis: p.created_at_millis,
            })
            .collect())
    }

    pub fn save_lyrics(&self, media_id: String, source: String, text: String) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.save_lyrics(&media_id, &source, &text)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn get_lyrics(&self, media_id: String) -> Result<Option<String>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.get_lyrics(&media_id)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn update_playback_position(
        &self,
        media_id: String,
        position_ms: i64,
        duration_ms: Option<i64>,
    ) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.update_playback_position(&media_id, position_ms, duration_ms)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn get_playback_position(&self, media_id: String) -> Result<Option<i64>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.get_playback_position(&media_id)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn add_bookmark(&self, bookmark: FfiBookmark) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.add_bookmark(&StoredBookmark {
            id: bookmark.id,
            media_id: bookmark.media_id,
            name: bookmark.name,
            position_millis: bookmark.position_millis,
            created_at_millis: bookmark.created_at_millis,
        })
        .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn get_bookmarks(&self, media_id: String) -> Result<Vec<FfiBookmark>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        let list = db.get_bookmarks(&media_id)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })?;
        Ok(list
            .into_iter()
            .map(|b| FfiBookmark {
                id: b.id,
                media_id: b.media_id,
                name: b.name,
                position_millis: b.position_millis,
                created_at_millis: b.created_at_millis,
            })
            .collect())
    }

    pub fn delete_bookmark(&self, id: String) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.delete_bookmark(&id)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn record_play_start(&self, media_id: String, played_at_millis: i64) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.record_play_start(&media_id, played_at_millis)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn save_position(
        &self,
        media_id: String,
        position_millis: i64,
        completed: bool,
        updated_at_millis: i64,
    ) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.save_position(&media_id, position_millis, completed, updated_at_millis)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn get_history(&self) -> Result<Vec<FfiHistory>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        let list = db.get_history()
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })?;
        Ok(list
            .into_iter()
            .map(|h| FfiHistory {
                id: h.id,
                media_id: h.media_id,
                last_played_at_millis: h.last_played_at_millis,
                position_millis: h.position_millis,
                completed: h.completed,
                play_count: h.play_count,
            })
            .collect())
    }

    pub fn save_queue(&self, media_ids: Vec<String>, current_index: i32) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.save_queue(&media_ids, current_index)
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })
    }

    pub fn load_queue(&self) -> Result<FfiQueueState, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        let (media_ids, current_index) = db.load_queue()
            .map_err(|e| VimivoFfiError::DatabaseError { reason: e.to_string() })?;
        Ok(FfiQueueState {
            media_ids,
            current_index,
        })
    }
}

// ---------------------------------------------------------------------------
// Play Queue Controller
// ---------------------------------------------------------------------------

#[derive(uniffi::Object)]
pub struct FfiPlayQueue {
    inner: Mutex<PlayQueue>,
}

#[uniffi::export]
impl FfiPlayQueue {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self { inner: Mutex::new(PlayQueue::new()) }
    }

    pub fn replace(&self, ids: Vec<String>, start_index: u32) {
        let mut q = self.inner.lock().unwrap();
        q.replace(ids, start_index as usize);
    }

    pub fn get_ids(&self) -> Vec<String> {
        let q = self.inner.lock().unwrap();
        q.ids().to_vec()
    }

    pub fn get_current_id(&self) -> Option<String> {
        let q = self.inner.lock().unwrap();
        q.current_id().map(|s| s.to_string())
    }

    pub fn get_current_index(&self) -> u32 {
        let q = self.inner.lock().unwrap();
        q.current_index() as u32
    }

    pub fn next(&self) -> Option<String> {
        let mut q = self.inner.lock().unwrap();
        q.next().map(|s| s.to_string())
    }

    pub fn previous(&self) -> Option<String> {
        let mut q = self.inner.lock().unwrap();
        q.previous().map(|s| s.to_string())
    }

    pub fn set_repeat_mode(&self, mode: FfiQueueRepeatMode) {
        let mut q = self.inner.lock().unwrap();
        let core_mode = match mode {
            FfiQueueRepeatMode::Off => CoreRepeatMode::Off,
            FfiQueueRepeatMode::All => CoreRepeatMode::All,
            FfiQueueRepeatMode::One => CoreRepeatMode::One,
        };
        q.set_repeat_mode(core_mode);
    }

    pub fn set_shuffled(&self, shuffled: bool) {
        let mut q = self.inner.lock().unwrap();
        q.set_shuffled(shuffled);
    }
}

// ---------------------------------------------------------------------------
// Audio ReplayGain & Equalizer Helpers
// ---------------------------------------------------------------------------

#[uniffi::export]
pub fn calculate_replay_gain_linear(gain_db: Option<f32>, peak: Option<f32>) -> f32 {
    ReplayGain::linear_gain(CoreReplayGainMode::Track, gain_db, None, peak, None)
}

// ---------------------------------------------------------------------------
// Streaming Validation
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct FfiStreamValidation {
    pub is_valid: bool,
    pub is_live: bool,
    pub display_title: String,
    pub error_message: Option<String>,
}

#[uniffi::export]
pub fn validate_stream_url(url: String) -> FfiStreamValidation {
    match StreamValidator::validate(&url) {
        StreamValidationResult::Valid { is_live, display_title, .. } => FfiStreamValidation {
            is_valid: true,
            is_live,
            display_title,
            error_message: None,
        },
        StreamValidationResult::Invalid { message, .. } => FfiStreamValidation {
            is_valid: false,
            is_live: false,
            display_title: String::new(),
            error_message: Some(message),
        },
    }
}

// ---------------------------------------------------------------------------
// Video & Intro Skip
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct FfiChapterInfo {
    pub index: i32,
    pub title: String,
    pub start_position_millis: i64,
}

#[uniffi::export]
pub fn calculate_intro_skip_target(
    position_millis: i64,
    chapters: Vec<FfiChapterInfo>,
    duration_millis: Option<i64>,
) -> i64 {
    let core_chapters: Vec<CoreChapterInfo> = chapters
        .into_iter()
        .map(|c| CoreChapterInfo {
            index: c.index,
            title: c.title,
            start_position_millis: c.start_position_millis,
        })
        .collect();

    IntroSkip::target(position_millis, &core_chapters, duration_millis)
}

// ---------------------------------------------------------------------------
// Rename Template Formatting
// ---------------------------------------------------------------------------

#[uniffi::export]
pub fn format_rename_template(template: String, item: FfiMediaItem) -> String {
    let mut core_item = CoreMediaItem::new(item.id, item.uri, item.title, CoreMediaKind::Audio);
    core_item.artist = item.artist;
    core_item.album = item.album;
    RenameTemplate::format(&template, &core_item)
}

// ---------------------------------------------------------------------------
// Playlist Parser (M3U, PLS, XSPF)
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct FfiPlaylistItem {
    pub uri: String,
    pub title: Option<String>,
    pub duration_seconds: Option<i32>,
}

#[derive(uniffi::Record)]
pub struct FfiParsedPlaylist {
    pub name: String,
    pub items: Vec<FfiPlaylistItem>,
}

#[uniffi::export]
pub fn parse_playlist_file(file_name: String, content: String) -> Option<FfiParsedPlaylist> {
    PlaylistFormats::parse(&file_name, &content).map(|p| FfiParsedPlaylist {
        name: p.name,
        items: p
            .entries
            .into_iter()
            .map(|e| FfiPlaylistItem {
                uri: e.uri,
                title: e.title,
                duration_seconds: e.duration_seconds,
            })
            .collect(),
    })
}
