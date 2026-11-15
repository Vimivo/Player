//! Vimivo FFI: UniFFI bindings for iOS, macOS, and Android.

use std::sync::Mutex;
use vimivo_core::audio::{ReplayGain, ReplayGainMode as CoreReplayGainMode};
use vimivo_core::db::VimivoDatabase;
use vimivo_core::model::{LibraryQuery as CoreQuery, MediaItem as CoreMediaItem, MediaKind as CoreMediaKind, QueueRepeatMode as CoreRepeatMode};
use vimivo_core::parser::{LrcLyricsParser, TitleParser};
use vimivo_core::queue::PlayQueue;

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum VimivoFfiError {
    #[error("Database error: {message}")]
    DatabaseError { message: String },
    #[error("Not found: {message}")]
    NotFound { message: String },
    #[error("Internal error: {message}")]
    InternalError { message: String },
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
            .map_err(|e| VimivoFfiError::DatabaseError { message: e.to_string() })?;
        Ok(Self { inner: Mutex::new(db) })
    }

    #[uniffi::constructor]
    pub fn open_in_memory() -> Result<Self, VimivoFfiError> {
        let db = VimivoDatabase::open_in_memory()
            .map_err(|e| VimivoFfiError::DatabaseError { message: e.to_string() })?;
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

        let db = self.inner.lock().unwrap();
        db.upsert_media_item(&core_item)
            .map_err(|e| VimivoFfiError::DatabaseError { message: e.to_string() })
    }

    pub fn get_item(&self, id: String) -> Result<Option<FfiMediaItem>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        let item = db.get_media_item(&id)
            .map_err(|e| VimivoFfiError::DatabaseError { message: e.to_string() })?;
        Ok(item.map(FfiMediaItem::from))
    }

    pub fn query_items(&self, search_text: String) -> Result<Vec<FfiMediaItem>, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        let mut q = CoreQuery::default();
        q.text = search_text;
        let page = db.query_media_items(&q)
            .map_err(|e| VimivoFfiError::DatabaseError { message: e.to_string() })?;
        Ok(page.items.into_iter().map(FfiMediaItem::from).collect())
    }

    pub fn set_favorite(&self, media_id: String, is_fav: bool) -> Result<(), VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.set_favorite(&media_id, is_fav)
            .map_err(|e| VimivoFfiError::DatabaseError { message: e.to_string() })
    }

    pub fn is_favorite(&self, media_id: String) -> Result<bool, VimivoFfiError> {
        let db = self.inner.lock().unwrap();
        db.is_favorite(&media_id)
            .map_err(|e| VimivoFfiError::DatabaseError { message: e.to_string() })
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
