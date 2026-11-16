//! Vimivo FFI: UniFFI bindings for iOS, macOS, and Android.

use std::sync::Mutex;
use vimivo_core::audio::{
    BassBoost, ReplayGain, ReplayGainMode as CoreReplayGainMode, SoftLimiter, SpectrumVisualizer,
    StereoVirtualizer, VoiceClarity,
};
use vimivo_core::batch::BatchEngine;
use vimivo_core::db::{StoredBookmark, VimivoDatabase};
use vimivo_core::model::{
    ChapterInfo as CoreChapterInfo, LibraryQuery as CoreQuery, MediaItem as CoreMediaItem,
    MediaKind as CoreMediaKind, QueueRepeatMode as CoreRepeatMode,
};
use vimivo_core::parser::{LrcLyricsParser, PlaylistFormats, TitleParser};
use vimivo_core::queue::PlayQueue;
use vimivo_core::rename::RenameTemplate;
use vimivo_core::streaming::{StreamValidationResult, StreamValidator};
use vimivo_core::subtitle::{
    DualSubtitleCoordinator, SubtitleCue, SubtitleEncodingDetector, SubtitleGestures,
    SubtitleParser, SubtitleTrack,
};
use vimivo_core::converter::{
    AudioExtractBitrate as CoreBitrate, AudioExtractFormat as CoreFormat, VideoToAudioConverter,
};
use vimivo_core::decoder::{DecoderMode as CoreDecoderMode, DecoderPolicyManager};
use vimivo_core::gesture::{PlayerGestureEngine, ScreenHalf};
use vimivo_core::network_share::{
    NetworkProtocol as CoreNetProtocol, NetworkShareConfig as CoreNetConfig, NetworkShareEngine,
};
use vimivo_core::vault::{VaultConfig, VaultManager};
use vimivo_core::video::IntroSkip;
use vimivo_core::wifi_transfer::{SharedFileInfo, WifiTransferEngine};
use vimivo_core::engine::{
    MpvCommandMapper as CoreMpvCommandMapper,
};
use vimivo_core::model::{VideoAspectRatio as CoreVideoAspectRatio};
use vimivo_core::audio::{
    EqualizerPreset as CoreEqPreset, EqualizerPresets as CoreEqPresets,
};
use vimivo_core::playback_policy::{
    ResumePolicy as CoreResumePolicy, VideoRotation as CoreVideoRotation,
    SnapshotNaming as CoreSnapshotNaming, SnapshotFormat as CoreSnapshotFormat,
};
use vimivo_core::sleep_timer::{
    SleepFade as CoreSleepFade, SleepTimerOptions as CoreSleepTimerOptions,
};

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

// ---------------------------------------------------------------------------
// Audio DSP
// ---------------------------------------------------------------------------

#[derive(uniffi::Object)]
pub struct FfiSpectrumVisualizer {
    inner: Mutex<SpectrumVisualizer>,
}

#[uniffi::export]
impl FfiSpectrumVisualizer {
    #[uniffi::constructor]
    pub fn new(num_bands: u32) -> Self {
        Self {
            inner: Mutex::new(SpectrumVisualizer::new(num_bands as usize)),
        }
    }

    pub fn process_samples(&self, samples: Vec<f32>) -> Vec<f32> {
        let mut visualizer = self.inner.lock().unwrap();
        visualizer.process_samples(&samples).to_vec()
    }

    pub fn bands(&self) -> Vec<f32> {
        let visualizer = self.inner.lock().unwrap();
        visualizer.bands().to_vec()
    }
}

#[derive(uniffi::Object)]
pub struct FfiBassBoost {
    inner: Mutex<BassBoost>,
}

#[uniffi::export]
impl FfiBassBoost {
    #[uniffi::constructor]
    pub fn new(sample_rate: i32, channels: u32) -> Self {
        Self {
            inner: Mutex::new(BassBoost::new(sample_rate, channels as usize)),
        }
    }

    pub fn set_gain_db(&self, gain_db: f32) {
        let mut bb = self.inner.lock().unwrap();
        bb.set_gain_db(gain_db);
    }

    pub fn set_cutoff_hz(&self, cutoff_hz: f32) {
        let mut bb = self.inner.lock().unwrap();
        bb.set_cutoff_hz(cutoff_hz);
    }

    pub fn gain_db(&self) -> f32 {
        let bb = self.inner.lock().unwrap();
        bb.gain_db()
    }

    pub fn is_enabled(&self) -> bool {
        let bb = self.inner.lock().unwrap();
        bb.is_enabled()
    }

    pub fn process(&self, mut samples: Vec<f32>, frames: u32) -> Vec<f32> {
        let mut bb = self.inner.lock().unwrap();
        bb.process(&mut samples, frames as usize);
        samples
    }
}

#[derive(uniffi::Object)]
pub struct FfiStereoVirtualizer {
    inner: Mutex<StereoVirtualizer>,
}

#[uniffi::export]
impl FfiStereoVirtualizer {
    #[uniffi::constructor]
    pub fn new(width: f32) -> Self {
        Self {
            inner: Mutex::new(StereoVirtualizer::new(width)),
        }
    }

    pub fn set_width(&self, width: f32) {
        let mut sv = self.inner.lock().unwrap();
        sv.set_width(width);
    }

    pub fn width(&self) -> f32 {
        let sv = self.inner.lock().unwrap();
        sv.width()
    }

    pub fn process_stereo(&self, mut samples: Vec<f32>) -> Vec<f32> {
        let sv = self.inner.lock().unwrap();
        sv.process_stereo(&mut samples);
        samples
    }
}

#[derive(uniffi::Object)]
pub struct FfiVoiceClarity {
    inner: Mutex<VoiceClarity>,
}

#[uniffi::export]
impl FfiVoiceClarity {
    #[uniffi::constructor]
    pub fn new(sample_rate: i32, channels: u32) -> Self {
        Self {
            inner: Mutex::new(VoiceClarity::new(sample_rate, channels as usize)),
        }
    }

    pub fn set_enabled(&self, enabled: bool) {
        let mut vc = self.inner.lock().unwrap();
        vc.set_enabled(enabled);
    }

    pub fn is_enabled(&self) -> bool {
        let vc = self.inner.lock().unwrap();
        vc.is_enabled()
    }

    pub fn process(&self, mut samples: Vec<f32>, frames: u32) -> Vec<f32> {
        let mut vc = self.inner.lock().unwrap();
        vc.process(&mut samples, frames as usize);
        samples
    }
}

#[derive(uniffi::Object)]
pub struct FfiSoftLimiter {
    inner: SoftLimiter,
}

#[uniffi::export]
impl FfiSoftLimiter {
    #[uniffi::constructor]
    pub fn new(threshold: f32) -> Self {
        Self {
            inner: SoftLimiter::new(threshold),
        }
    }

    pub fn process(&self, mut samples: Vec<f32>) -> Vec<f32> {
        self.inner.process(&mut samples);
        samples
    }
}

// ---------------------------------------------------------------------------
// Subtitles
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct FfiSubtitleCue {
    pub index: u32,
    pub start_ms: i64,
    pub end_ms: i64,
    pub text: String,
}

#[derive(uniffi::Record)]
pub struct FfiSubtitleStyle {
    pub font_family: String,
    pub font_size_scale: f32,
    pub text_color_hex: String,
    pub outline_color_hex: String,
    pub outline_thickness: f32,
    pub background_opacity: f32,
    pub vertical_position_percent: f32,
    pub bold: bool,
    pub italic: bool,
}

#[derive(uniffi::Record)]
pub struct FfiActiveSubtitles {
    pub primary_text: Option<String>,
    pub secondary_text: Option<String>,
}

#[uniffi::export]
pub fn decode_subtitle_bytes(bytes: Vec<u8>) -> String {
    SubtitleEncodingDetector::decode(&bytes)
}

#[uniffi::export]
pub fn parse_subtitle_content(content: String) -> Vec<FfiSubtitleCue> {
    SubtitleParser::parse(&content)
        .into_iter()
        .map(|c| FfiSubtitleCue {
            index: c.index as u32,
            start_ms: c.start_ms,
            end_ms: c.end_ms,
            text: c.text,
        })
        .collect()
}

#[uniffi::export]
pub fn calculate_subtitle_drag_position(current_percent: f32, delta_y_pixels: f32, screen_height_pixels: f32) -> f32 {
    SubtitleGestures::calculate_drag_position(current_percent, delta_y_pixels, screen_height_pixels)
}

#[uniffi::export]
pub fn calculate_subtitle_pinch_font_scale(base_scale: f32, pinch_factor: f32) -> f32 {
    SubtitleGestures::calculate_pinch_font_scale(base_scale, pinch_factor)
}

#[uniffi::export]
pub fn calculate_subtitle_drag_delay(current_delay_ms: i64, delta_x_pixels: f32) -> i64 {
    SubtitleGestures::calculate_drag_delay(current_delay_ms, delta_x_pixels)
}

#[derive(uniffi::Object)]
pub struct FfiDualSubtitleCoordinator {
    inner: Mutex<DualSubtitleCoordinator>,
}

#[uniffi::export]
impl FfiDualSubtitleCoordinator {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(DualSubtitleCoordinator::new()),
        }
    }

    pub fn set_primary_cues(&self, name: String, language: String, cues: Vec<FfiSubtitleCue>, delay_ms: i64, speed: f64) {
        let mut coord = self.inner.lock().unwrap();
        let core_cues = cues
            .into_iter()
            .map(|c| SubtitleCue {
                index: c.index as usize,
                start_ms: c.start_ms,
                end_ms: c.end_ms,
                text: c.text,
            })
            .collect();
        let mut track = SubtitleTrack::new(&name, &language, core_cues);
        track.delay_ms = delay_ms;
        track.speed_multiplier = speed;
        coord.set_primary(Some(track));
    }

    pub fn set_secondary_cues(&self, name: String, language: String, cues: Vec<FfiSubtitleCue>, delay_ms: i64, speed: f64) {
        let mut coord = self.inner.lock().unwrap();
        let core_cues = cues
            .into_iter()
            .map(|c| SubtitleCue {
                index: c.index as usize,
                start_ms: c.start_ms,
                end_ms: c.end_ms,
                text: c.text,
            })
            .collect();
        let mut track = SubtitleTrack::new(&name, &language, core_cues);
        track.delay_ms = delay_ms;
        track.speed_multiplier = speed;
        coord.set_secondary(Some(track));
    }

    pub fn active_subtitles_at(&self, playback_ms: i64) -> FfiActiveSubtitles {
        let coord = self.inner.lock().unwrap();
        let active = coord.active_subtitles_at(playback_ms);
        FfiActiveSubtitles {
            primary_text: active.primary_text,
            secondary_text: active.secondary_text,
        }
    }
}

// ---------------------------------------------------------------------------
// Private Video Vault
// ---------------------------------------------------------------------------

#[derive(uniffi::Object)]
pub struct FfiVaultManager {
    inner: Mutex<VaultManager>,
}

#[uniffi::export]
impl FfiVaultManager {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(VaultManager::new(VaultConfig::default())),
        }
    }

    pub fn is_initialized(&self) -> bool {
        let vm = self.inner.lock().unwrap();
        vm.is_initialized()
    }

    pub fn setup_pin(&self, pin: String) -> Result<(), VimivoFfiError> {
        let mut vm = self.inner.lock().unwrap();
        vm.setup_pin(&pin).map_err(|e| VimivoFfiError::InternalError {
            reason: e.to_string(),
        })
    }

    pub fn unlock(&self, pin: String, current_time_sec: u64) -> Result<String, VimivoFfiError> {
        let mut vm = self.inner.lock().unwrap();
        vm.unlock(&pin, current_time_sec)
            .map_err(|e| VimivoFfiError::InternalError {
                reason: e.to_string(),
            })
    }

    pub fn is_unlocked(&self, token: String, current_time_sec: u64) -> bool {
        let vm = self.inner.lock().unwrap();
        vm.is_unlocked(&token, current_time_sec)
    }

    pub fn touch_session(&self, current_time_sec: u64) {
        let mut vm = self.inner.lock().unwrap();
        vm.touch_session(current_time_sec);
    }

    pub fn lock(&self) {
        let mut vm = self.inner.lock().unwrap();
        vm.lock();
    }
}

// ---------------------------------------------------------------------------
// Wi-Fi Offline File Sharing
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct FfiSharedFileInfo {
    pub name: String,
    pub size_bytes: u64,
    pub mime_type: String,
}

#[derive(uniffi::Record)]
pub struct FfiHttpRange {
    pub start: u64,
    pub end: u64,
}

#[uniffi::export]
pub fn sanitize_transfer_filename(filename: String) -> String {
    WifiTransferEngine::sanitize_filename(&filename)
}

#[uniffi::export]
pub fn parse_http_range_header(header: String, total_size: u64) -> Option<FfiHttpRange> {
    WifiTransferEngine::parse_range_header(&header, total_size)
        .map(|(s, e)| FfiHttpRange { start: s, end: e })
}

#[uniffi::export]
pub fn detect_media_mime_type(filename: String) -> String {
    WifiTransferEngine::detect_mime_type(&filename).to_string()
}

#[uniffi::export]
pub fn render_wifi_transfer_html(device_name: String, files: Vec<FfiSharedFileInfo>) -> String {
    let core_files: Vec<SharedFileInfo> = files
        .into_iter()
        .map(|f| SharedFileInfo {
            name: f.name,
            size_bytes: f.size_bytes,
            mime_type: f.mime_type,
        })
        .collect();
    WifiTransferEngine::render_web_ui(&device_name, &core_files)
}

// ---------------------------------------------------------------------------
// Batch Operations & Badges
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct FfiBatchRenameResult {
    pub id: String,
    pub old_name: String,
    pub new_name: String,
}

#[derive(uniffi::Record)]
pub struct FfiFolderSummary {
    pub folder_path: String,
    pub folder_name: String,
    pub total_count: u32,
    pub unplayed_count: u32,
    pub new_count: u32,
    pub total_size_bytes: u64,
}

#[uniffi::export]
pub fn is_new_media_item(
    added_at_sec: i64,
    last_played_ms: Option<i64>,
    current_time_sec: i64,
    threshold_days: u32,
) -> bool {
    BatchEngine::is_new_item(added_at_sec, last_played_ms, current_time_sec, threshold_days)
}

#[uniffi::export]
pub fn generate_batch_sequential_rename(
    items: Vec<FfiBatchRenameResult>,
    prefix: String,
    start_index: u32,
    pad_digits: u32,
) -> Vec<FfiBatchRenameResult> {
    let input_pairs: Vec<(String, String)> = items.into_iter().map(|i| (i.id, i.old_name)).collect();
    BatchEngine::generate_sequential_rename(&input_pairs, &prefix, start_index, pad_digits as usize)
        .into_iter()
        .map(|r| FfiBatchRenameResult {
            id: r.id,
            old_name: r.old_name,
            new_name: r.new_name,
        })
        .collect()
}

// ---------------------------------------------------------------------------
// Hardware Decoder Switcher
// ---------------------------------------------------------------------------

#[derive(uniffi::Enum)]
pub enum FfiDecoderMode {
    Hardware,
    HardwarePlus,
    Software,
}

impl From<CoreDecoderMode> for FfiDecoderMode {
    fn from(c: CoreDecoderMode) -> Self {
        match c {
            CoreDecoderMode::Hardware => Self::Hardware,
            CoreDecoderMode::HardwarePlus => Self::HardwarePlus,
            CoreDecoderMode::Software => Self::Software,
        }
    }
}

impl From<FfiDecoderMode> for CoreDecoderMode {
    fn from(f: FfiDecoderMode) -> Self {
        match f {
            FfiDecoderMode::Hardware => Self::Hardware,
            FfiDecoderMode::HardwarePlus => Self::HardwarePlus,
            FfiDecoderMode::Software => Self::Software,
        }
    }
}

#[uniffi::export]
pub fn cycle_decoder_mode(current: FfiDecoderMode) -> FfiDecoderMode {
    DecoderPolicyManager::cycle_next(current.into()).into()
}

#[uniffi::export]
pub fn fallback_decoder_mode(current: FfiDecoderMode) -> Option<FfiDecoderMode> {
    DecoderPolicyManager::fallback_on_error(current.into()).map(|m| m.into())
}

#[uniffi::export]
pub fn get_mpv_hwdec_arg(mode: FfiDecoderMode, os: String) -> String {
    let core: CoreDecoderMode = mode.into();
    core.mpv_hwdec_value(&os).to_string()
}

// ---------------------------------------------------------------------------
// Touch Gestures
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct FfiSeekGestureResult {
    pub target_position_ms: i64,
    pub delta_seconds: i32,
    pub formatted_delta: String,
}

#[derive(uniffi::Record)]
pub struct FfiPinchZoomResult {
    pub zoom_scale: f32,
    pub pan_x: f32,
    pub pan_y: f32,
}

#[uniffi::export]
pub fn calculate_brightness_swipe(current: f32, delta_y: f32, screen_height: f32) -> f32 {
    PlayerGestureEngine::calculate_brightness_swipe(current, delta_y, screen_height)
}

#[uniffi::export]
pub fn calculate_volume_swipe(current: f32, delta_y: f32, screen_height: f32) -> f32 {
    PlayerGestureEngine::calculate_volume_swipe(current, delta_y, screen_height)
}

#[uniffi::export]
pub fn calculate_seek_swipe(
    current_position_ms: i64,
    total_duration_ms: i64,
    delta_x: f32,
    screen_width: f32,
) -> FfiSeekGestureResult {
    let res = PlayerGestureEngine::calculate_seek_swipe(current_position_ms, total_duration_ms, delta_x, screen_width);
    FfiSeekGestureResult {
        target_position_ms: res.target_position_ms,
        delta_seconds: res.delta_seconds,
        formatted_delta: res.formatted_delta,
    }
}

#[uniffi::export]
pub fn calculate_double_tap_seek(
    current_position_ms: i64,
    total_duration_ms: i64,
    is_right_side: bool,
) -> i64 {
    let side = if is_right_side { ScreenHalf::Right } else { ScreenHalf::Left };
    PlayerGestureEngine::calculate_double_tap_seek(current_position_ms, total_duration_ms, side)
}

#[uniffi::export]
pub fn calculate_pinch_zoom(
    base_scale: f32,
    pinch_ratio: f32,
    delta_pan_x: f32,
    delta_pan_y: f32,
) -> FfiPinchZoomResult {
    let res = PlayerGestureEngine::calculate_pinch_zoom(base_scale, pinch_ratio, delta_pan_x, delta_pan_y);
    FfiPinchZoomResult {
        zoom_scale: res.zoom_scale,
        pan_x: res.pan_x,
        pan_y: res.pan_y,
    }
}

#[uniffi::export]
pub fn get_long_press_playback_speed(is_pressed: bool) -> f32 {
    PlayerGestureEngine::get_long_press_speed(is_pressed)
}

// ---------------------------------------------------------------------------
// Video-to-Audio Converter
// ---------------------------------------------------------------------------

#[derive(uniffi::Enum)]
pub enum FfiAudioExtractFormat {
    Mp3,
    Flac,
    Aac,
    Opus,
    Wav,
}

#[derive(uniffi::Enum)]
pub enum FfiAudioExtractBitrate {
    Kbps128,
    Kbps192,
    Kbps256,
    Kbps320,
}

impl From<FfiAudioExtractFormat> for CoreFormat {
    fn from(f: FfiAudioExtractFormat) -> Self {
        match f {
            FfiAudioExtractFormat::Mp3 => Self::Mp3,
            FfiAudioExtractFormat::Flac => Self::Flac,
            FfiAudioExtractFormat::Aac => Self::Aac,
            FfiAudioExtractFormat::Opus => Self::Opus,
            FfiAudioExtractFormat::Wav => Self::Wav,
        }
    }
}

impl From<FfiAudioExtractBitrate> for CoreBitrate {
    fn from(b: FfiAudioExtractBitrate) -> Self {
        match b {
            FfiAudioExtractBitrate::Kbps128 => Self::Kbps128,
            FfiAudioExtractBitrate::Kbps192 => Self::Kbps192,
            FfiAudioExtractBitrate::Kbps256 => Self::Kbps256,
            FfiAudioExtractBitrate::Kbps320 => Self::Kbps320,
        }
    }
}

#[uniffi::export]
pub fn generate_audio_extraction_ffmpeg_args(
    video_path: String,
    out_path: String,
    format: FfiAudioExtractFormat,
    bitrate: FfiAudioExtractBitrate,
) -> Vec<String> {
    VideoToAudioConverter::generate_ffmpeg_args(&video_path, &out_path, format.into(), bitrate.into())
}

#[uniffi::export]
pub fn generate_audio_output_path(video_path: String, format: FfiAudioExtractFormat) -> String {
    let p = std::path::Path::new(&video_path);
    let out = VideoToAudioConverter::generate_output_path(p, format.into());
    out.to_string_lossy().to_string()
}

// ---------------------------------------------------------------------------
// Network Shares & NAS Protocols (SMB, SFTP, FTP, WebDAV, NFS)
// ---------------------------------------------------------------------------

#[derive(uniffi::Enum)]
pub enum FfiNetworkProtocol {
    Smb,
    Sftp,
    Ftp,
    WebDav,
    Nfs,
}

#[derive(uniffi::Record)]
pub struct FfiNetworkShareConfig {
    pub protocol: FfiNetworkProtocol,
    pub host: String,
    pub port: u16,
    pub username: Option<String>,
    pub password: Option<String>,
    pub share_path: String,
}

#[uniffi::export]
pub fn build_network_stream_uri(config: FfiNetworkShareConfig, file_subpath: String) -> String {
    let core_protocol = match config.protocol {
        FfiNetworkProtocol::Smb => CoreNetProtocol::Smb,
        FfiNetworkProtocol::Sftp => CoreNetProtocol::Sftp,
        FfiNetworkProtocol::Ftp => CoreNetProtocol::Ftp,
        FfiNetworkProtocol::WebDav => CoreNetProtocol::WebDav,
        FfiNetworkProtocol::Nfs => CoreNetProtocol::Nfs,
    };
    let core_cfg = CoreNetConfig {
        protocol: core_protocol,
        host: config.host,
        port: config.port,
        username: config.username,
        password: config.password,
        share_path: config.share_path,
    };
    NetworkShareEngine::build_stream_uri(&core_cfg, &file_subpath)
}

// ---------------------------------------------------------------------------
// A-B Loop Repeat Engine
// ---------------------------------------------------------------------------

use vimivo_core::ab_loop::{AbLoop, AbLoopState};

#[derive(uniffi::Enum)]
pub enum FfiAbLoopState {
    None,
    PointASet { a_ms: i64 },
    Active { a_ms: i64, b_ms: i64 },
}

#[derive(uniffi::Object)]
pub struct FfiAbLoop {
    inner: std::sync::Mutex<AbLoop>,
}

#[uniffi::export]
impl FfiAbLoop {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {
            inner: std::sync::Mutex::new(AbLoop::new()),
        }
    }

    pub fn step(&self, current_pos_ms: i64) -> FfiAbLoopState {
        let mut loop_ctrl = self.inner.lock().unwrap();
        match loop_ctrl.step(current_pos_ms) {
            AbLoopState::None => FfiAbLoopState::None,
            AbLoopState::PointASet(a) => FfiAbLoopState::PointASet { a_ms: a },
            AbLoopState::Active { a, b } => FfiAbLoopState::Active { a_ms: a, b_ms: b },
        }
    }

    pub fn check_loop(&self, current_pos_ms: i64) -> Option<i64> {
        let loop_ctrl = self.inner.lock().unwrap();
        loop_ctrl.check_loop(current_pos_ms)
    }

    pub fn clear(&self) {
        let mut loop_ctrl = self.inner.lock().unwrap();
        loop_ctrl.clear();
    }

    pub fn is_active(&self) -> bool {
        let loop_ctrl = self.inner.lock().unwrap();
        loop_ctrl.is_active()
    }

    pub fn button_label(&self) -> String {
        let loop_ctrl = self.inner.lock().unwrap();
        loop_ctrl.button_label()
    }

    pub fn set_range(&self, a_ms: i64, b_ms: i64) -> bool {
        let mut loop_ctrl = self.inner.lock().unwrap();
        loop_ctrl.set_range(a_ms, b_ms)
    }
}

// ---------------------------------------------------------------------------
// Stats for Nerds / Diagnostics
// ---------------------------------------------------------------------------

use vimivo_core::diagnostics::MediaDiagnostics;

#[derive(uniffi::Record)]
pub struct FfiMediaDiagnostics {
    pub file_name: String,
    pub resolution: String,
    pub video_codec: String,
    pub audio_codec: String,
    pub audio_channels: u16,
    pub sample_rate_hz: u32,
    pub decoder_mode: String,
    pub current_bitrate_kbps: u32,
    pub dropped_frames: u64,
    pub total_frames: u64,
    pub buffer_health_sec: f32,
}

#[uniffi::export]
pub fn format_diagnostics_video(diag: FfiMediaDiagnostics) -> String {
    let core = MediaDiagnostics {
        file_name: diag.file_name,
        resolution: diag.resolution,
        video_codec: diag.video_codec,
        audio_codec: diag.audio_codec,
        audio_channels: diag.audio_channels,
        sample_rate_hz: diag.sample_rate_hz,
        decoder_mode: diag.decoder_mode,
        current_bitrate_kbps: diag.current_bitrate_kbps,
        dropped_frames: diag.dropped_frames,
        total_frames: diag.total_frames,
        buffer_health_sec: diag.buffer_health_sec,
    };
    core.video_summary()
}

#[uniffi::export]
pub fn format_diagnostics_audio(diag: FfiMediaDiagnostics) -> String {
    let core = MediaDiagnostics {
        file_name: diag.file_name,
        resolution: diag.resolution,
        video_codec: diag.video_codec,
        audio_codec: diag.audio_codec,
        audio_channels: diag.audio_channels,
        sample_rate_hz: diag.sample_rate_hz,
        decoder_mode: diag.decoder_mode,
        current_bitrate_kbps: diag.current_bitrate_kbps,
        dropped_frames: diag.dropped_frames,
        total_frames: diag.total_frames,
        buffer_health_sec: diag.buffer_health_sec,
    };
    core.audio_summary()
}

#[uniffi::export]
pub fn format_diagnostics_dropped_frames(diag: FfiMediaDiagnostics) -> String {
    let core = MediaDiagnostics {
        file_name: diag.file_name,
        resolution: diag.resolution,
        video_codec: diag.video_codec,
        audio_codec: diag.audio_codec,
        audio_channels: diag.audio_channels,
        sample_rate_hz: diag.sample_rate_hz,
        decoder_mode: diag.decoder_mode,
        current_bitrate_kbps: diag.current_bitrate_kbps,
        dropped_frames: diag.dropped_frames,
        total_frames: diag.total_frames,
        buffer_health_sec: diag.buffer_health_sec,
    };
    core.dropped_frames_summary()
}

// ---------------------------------------------------------------------------
// Playback Engine & Command Mapper
// ---------------------------------------------------------------------------

#[derive(uniffi::Enum)]
pub enum FfiPlaybackStatus {
    Idle,
    Preparing,
    Playing,
    Paused,
    Completed,
    Error { message: String },
}

#[derive(uniffi::Enum)]
pub enum FfiVideoAspectRatio {
    Original,
    Fit,
    Fill,
    Ratio16x9,
    Ratio4x3,
    Ratio21x9,
}

#[derive(uniffi::Record)]
pub struct FfiMpvProperty {
    pub name: String,
    pub value: String,
}

#[uniffi::export]
pub fn mpv_load_file_command(uri: String, start_position_millis: i64) -> Vec<String> {
    CoreMpvCommandMapper::load_file(&uri, start_position_millis)
}

#[uniffi::export]
pub fn mpv_aspect_ratio_property(ratio: FfiVideoAspectRatio) -> FfiMpvProperty {
    let core_ratio = match ratio {
        FfiVideoAspectRatio::Original => CoreVideoAspectRatio::Original,
        FfiVideoAspectRatio::Fit => CoreVideoAspectRatio::Fit,
        FfiVideoAspectRatio::Fill => CoreVideoAspectRatio::Fill,
        FfiVideoAspectRatio::Ratio16x9 => CoreVideoAspectRatio::Ratio16_9,
        FfiVideoAspectRatio::Ratio4x3 => CoreVideoAspectRatio::Ratio4_3,
        FfiVideoAspectRatio::Ratio21x9 => CoreVideoAspectRatio::Ratio21_9,
    };
    let (name, val) = CoreMpvCommandMapper::aspect_ratio_property(core_ratio);
    FfiMpvProperty {
        name: name.to_string(),
        value: val.to_string(),
    }
}

#[uniffi::export]
pub fn mpv_volume_property(volume: f32) -> FfiMpvProperty {
    let (name, val) = CoreMpvCommandMapper::volume_property(volume);
    FfiMpvProperty {
        name: name.to_string(),
        value: val,
    }
}

#[uniffi::export]
pub fn mpv_speed_property(speed: f32) -> FfiMpvProperty {
    let (name, val) = CoreMpvCommandMapper::speed_property(speed);
    FfiMpvProperty {
        name: name.to_string(),
        value: val,
    }
}

#[uniffi::export]
pub fn mpv_delay_property(delay_ms: i64, is_subtitle: bool) -> FfiMpvProperty {
    let (name, val) = CoreMpvCommandMapper::delay_property(delay_ms, is_subtitle);
    FfiMpvProperty {
        name: name.to_string(),
        value: val,
    }
}

// ---------------------------------------------------------------------------
// Resume Policy & Playback Preferences
// ---------------------------------------------------------------------------

#[uniffi::export]
pub fn calculate_resume_start_position(
    kind: FfiMediaKind,
    saved_position_millis: i64,
    duration_millis: Option<i64>,
    completed: bool,
    include_short_audio: bool,
) -> i64 {
    let core_kind = match kind {
        FfiMediaKind::Audio => CoreMediaKind::Audio,
        FfiMediaKind::Video => CoreMediaKind::Video,
        FfiMediaKind::Stream => CoreMediaKind::Stream,
    };
    CoreResumePolicy::start_position_millis(
        core_kind,
        saved_position_millis,
        duration_millis,
        completed,
        include_short_audio,
    )
}

// ---------------------------------------------------------------------------
// Equalizer Presets
// ---------------------------------------------------------------------------

#[derive(uniffi::Enum)]
pub enum FfiEqualizerPreset {
    Flat,
    Rock,
    Pop,
    Classical,
    Jazz,
    Vocal,
    BassBoost,
    TrebleBoost,
    Acoustic,
    Electronic,
    Podcast,
    Custom,
}

#[uniffi::export]
pub fn get_equalizer_preset_gains(preset: FfiEqualizerPreset) -> Vec<f32> {
    let core_preset = match preset {
        FfiEqualizerPreset::Flat => CoreEqPreset::Flat,
        FfiEqualizerPreset::Rock => CoreEqPreset::Rock,
        FfiEqualizerPreset::Pop => CoreEqPreset::Pop,
        FfiEqualizerPreset::Classical => CoreEqPreset::Classical,
        FfiEqualizerPreset::Jazz => CoreEqPreset::Jazz,
        FfiEqualizerPreset::Vocal => CoreEqPreset::Vocal,
        FfiEqualizerPreset::BassBoost => CoreEqPreset::BassBoost,
        FfiEqualizerPreset::TrebleBoost => CoreEqPreset::TrebleBoost,
        FfiEqualizerPreset::Acoustic => CoreEqPreset::Acoustic,
        FfiEqualizerPreset::Electronic => CoreEqPreset::Electronic,
        FfiEqualizerPreset::Podcast => CoreEqPreset::Podcast,
        FfiEqualizerPreset::Custom => CoreEqPreset::Custom,
    };
    CoreEqPresets::gains(core_preset).to_vec()
}

#[uniffi::export]
pub fn resolve_equalizer_gains(
    preset: FfiEqualizerPreset,
    custom_gains: Option<Vec<f32>>,
    bass_strength: f32,
    treble_gain_db: f32,
) -> Vec<f32> {
    let core_preset = match preset {
        FfiEqualizerPreset::Flat => CoreEqPreset::Flat,
        FfiEqualizerPreset::Rock => CoreEqPreset::Rock,
        FfiEqualizerPreset::Pop => CoreEqPreset::Pop,
        FfiEqualizerPreset::Classical => CoreEqPreset::Classical,
        FfiEqualizerPreset::Jazz => CoreEqPreset::Jazz,
        FfiEqualizerPreset::Vocal => CoreEqPreset::Vocal,
        FfiEqualizerPreset::BassBoost => CoreEqPreset::BassBoost,
        FfiEqualizerPreset::TrebleBoost => CoreEqPreset::TrebleBoost,
        FfiEqualizerPreset::Acoustic => CoreEqPreset::Acoustic,
        FfiEqualizerPreset::Electronic => CoreEqPreset::Electronic,
        FfiEqualizerPreset::Podcast => CoreEqPreset::Podcast,
        FfiEqualizerPreset::Custom => CoreEqPreset::Custom,
    };
    let mut custom_array = [0.0f32; 10];
    if let Some(cg) = custom_gains {
        for (i, val) in cg.into_iter().take(10).enumerate() {
            custom_array[i] = val;
        }
    }
    CoreEqPresets::resolved(core_preset, Some(&custom_array), bass_strength, treble_gain_db).to_vec()
}

// ---------------------------------------------------------------------------
// Video Rotation
// ---------------------------------------------------------------------------

#[derive(uniffi::Enum)]
pub enum FfiVideoRotation {
    None,
    Clockwise90,
    Clockwise180,
    Clockwise270,
}

#[uniffi::export]
pub fn get_video_rotation_degrees(rot: FfiVideoRotation) -> i32 {
    match rot {
        FfiVideoRotation::None => 0,
        FfiVideoRotation::Clockwise90 => 90,
        FfiVideoRotation::Clockwise180 => 180,
        FfiVideoRotation::Clockwise270 => 270,
    }
}

#[uniffi::export]
pub fn get_next_video_rotation(rot: FfiVideoRotation) -> FfiVideoRotation {
    match rot {
        FfiVideoRotation::None => FfiVideoRotation::Clockwise90,
        FfiVideoRotation::Clockwise90 => FfiVideoRotation::Clockwise180,
        FfiVideoRotation::Clockwise180 => FfiVideoRotation::Clockwise270,
        FfiVideoRotation::Clockwise270 => FfiVideoRotation::None,
    }
}

#[uniffi::export]
pub fn calculate_video_rotation_fit_scale(
    rot: FfiVideoRotation,
    container_width: f32,
    container_height: f32,
    video_aspect: f32,
) -> f32 {
    let core_rot = match rot {
        FfiVideoRotation::None => CoreVideoRotation::None,
        FfiVideoRotation::Clockwise90 => CoreVideoRotation::Clockwise90,
        FfiVideoRotation::Clockwise180 => CoreVideoRotation::Clockwise180,
        FfiVideoRotation::Clockwise270 => CoreVideoRotation::Clockwise270,
    };
    core_rot.fit_scale(container_width, container_height, video_aspect)
}

// ---------------------------------------------------------------------------
// Snapshot Naming
// ---------------------------------------------------------------------------

#[uniffi::export]
pub fn generate_snapshot_file_name(
    title: Option<String>,
    position_millis: i64,
    timestamp_millis: i64,
    is_jpeg: bool,
) -> String {
    let fmt = if is_jpeg {
        CoreSnapshotFormat::Jpeg
    } else {
        CoreSnapshotFormat::Png
    };
    CoreSnapshotNaming::file_name(title.as_deref(), position_millis, timestamp_millis, fmt)
}

// ---------------------------------------------------------------------------
// Sleep Timer
// ---------------------------------------------------------------------------

#[uniffi::export]
pub fn calculate_sleep_fade_volume(elapsed_millis: i64, fade_millis: i64) -> f32 {
    CoreSleepFade::volume_fraction(elapsed_millis, fade_millis)
}

#[uniffi::export]
pub fn parse_custom_sleep_timer_minutes(hours: String, minutes: String) -> Option<i32> {
    CoreSleepTimerOptions::custom_minutes(&hours, &minutes)
}

#[uniffi::export]
pub fn format_sleep_timer_label(minutes: i32) -> String {
    CoreSleepTimerOptions::label(minutes)
}


