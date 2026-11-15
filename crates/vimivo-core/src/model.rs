//! Vimivo Core Domain Models.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct MediaId(pub String);

impl From<&str> for MediaId {
    fn from(s: &str) -> Self {
        MediaId(s.to_string())
    }
}

impl From<String> for MediaId {
    fn from(s: String) -> Self {
        MediaId(s)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum MediaKind {
    Audio,
    Video,
    Stream,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum MediaSource {
    MediaStore,
    SafFile,
    SafTree,
    Network,
    LocalFile,
}

impl Default for MediaSource {
    fn default() -> Self {
        MediaSource::LocalFile
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct MediaItem {
    pub id: MediaId,
    pub uri: String,
    pub title: String,
    pub kind: MediaKind,
    pub duration_millis: Option<i64>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub artwork_uri: Option<String>,
    pub album_artist: Option<String>,
    pub genre: Option<String>,
    pub size_bytes: Option<i64>,
    pub mime_type: Option<String>,
    pub file_name: Option<String>,
    pub date_added_epoch_seconds: Option<i64>,
    pub date_modified_epoch_seconds: Option<i64>,
    pub width: Option<i32>,
    pub height: Option<i32>,
    pub frame_rate: Option<f32>,
    pub bitrate: Option<i32>,
    pub track_number: Option<i32>,
    pub disc_number: Option<i32>,
    pub release_year: Option<i32>,
    pub folder_name: Option<String>,
    pub folder_path: Option<String>,
    pub source: MediaSource,
    pub volume_name: Option<String>,
    pub is_favorite: Boolean,
    pub is_available: Boolean,
    pub last_playback_position_millis: i64,
    pub last_played_at_epoch_millis: Option<i64>,
}

type Boolean = bool;

impl MediaItem {
    pub fn new(id: impl Into<MediaId>, uri: impl Into<String>, title: impl Into<String>, kind: MediaKind) -> Self {
        Self {
            id: id.into(),
            uri: uri.into(),
            title: title.into(),
            kind,
            duration_millis: None,
            artist: None,
            album: None,
            artwork_uri: None,
            album_artist: None,
            genre: None,
            size_bytes: None,
            mime_type: None,
            file_name: None,
            date_added_epoch_seconds: None,
            date_modified_epoch_seconds: None,
            width: None,
            height: None,
            frame_rate: None,
            bitrate: None,
            track_number: None,
            disc_number: None,
            release_year: None,
            folder_name: None,
            folder_path: None,
            source: MediaSource::LocalFile,
            volume_name: None,
            is_favorite: false,
            is_available: true,
            last_playback_position_millis: 0,
            last_played_at_epoch_millis: None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum LibrarySort {
    Title,
    Artist,
    Album,
    Duration,
    DateAdded,
    DateModified,
    FileSize,
    TrackNumber,
    LastPlayed,
}

impl Default for LibrarySort {
    fn default() -> Self {
        LibrarySort::Title
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct LibraryQuery {
    pub text: String,
    pub kind: Option<MediaKind>,
    pub album: Option<String>,
    pub artist: Option<String>,
    pub album_artist: Option<String>,
    pub genre: Option<String>,
    pub folder_path: Option<String>,
    pub excluded_folder_paths: Vec<String>,
    pub favorites_only: bool,
    pub recently_watched_only: bool,
    pub recently_played_only: bool,
    pub continue_watching_only: bool,
    pub minimum_duration_millis: Option<i64>,
    pub maximum_duration_millis: Option<i64>,
    pub mime_type: Option<String>,
    pub sort: LibrarySort,
    pub descending: bool,
    pub offset: u32,
    pub limit: u32,
}

impl Default for LibraryQuery {
    fn default() -> Self {
        Self {
            text: String::new(),
            kind: None,
            album: None,
            artist: None,
            album_artist: None,
            genre: None,
            folder_path: None,
            excluded_folder_paths: Vec::new(),
            favorites_only: false,
            recently_watched_only: false,
            recently_played_only: false,
            continue_watching_only: false,
            minimum_duration_millis: None,
            maximum_duration_millis: None,
            mime_type: None,
            sort: LibrarySort::Title,
            descending: false,
            offset: 0,
            limit: 100,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct LibraryPage {
    pub items: Vec<MediaItem>,
    pub offset: u32,
    pub total_count: u32,
}

impl LibraryPage {
    pub fn has_more(&self) -> bool {
        self.offset + (self.items.len() as u32) < self.total_count
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct LibraryCategory {
    pub name: String,
    pub item_count: u32,
    pub artwork_uri: Option<String>,
    pub new_count: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum LibraryCategoryField {
    Album,
    Artist,
    AlbumArtist,
    Genre,
    Folder,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum QueueRepeatMode {
    Off,
    All,
    One,
}

impl Default for QueueRepeatMode {
    fn default() -> Self {
        QueueRepeatMode::Off
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TrackType {
    Audio,
    Video,
    Subtitle,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TrackOrigin {
    Embedded,
    External,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TrackInfo {
    pub id: String,
    pub track_type: TrackType,
    pub label: String,
    pub language: Option<String>,
    pub mime_type: Option<String>,
    pub codec: Option<String>,
    pub bitrate: Option<i32>,
    pub channels: Option<i32>,
    pub sample_rate: Option<i32>,
    pub width: Option<i32>,
    pub height: Option<i32>,
    pub frame_rate: Option<f32>,
    pub is_selected: bool,
    pub origin: TrackOrigin,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ExternalSubtitle {
    pub uri: String,
    pub label: String,
    pub language: Option<String>,
    pub mime_type: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ChapterInfo {
    pub index: i32,
    pub title: String,
    pub start_position_millis: i64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum VideoAspectRatio {
    Original,
    Fit,
    Fill,
    Ratio16_9,
    Ratio4_3,
    Ratio21_9,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct VideoAdjustments {
    pub brightness: i32,
    pub contrast: i32,
    pub saturation: i32,
    pub gamma: i32,
    pub hue: i32,
    pub rotation_degrees: i32,
    pub zoom: f32,
    pub pan_x: f32,
    pub pan_y: f32,
}

impl Default for VideoAdjustments {
    fn default() -> Self {
        Self {
            brightness: 0,
            contrast: 0,
            saturation: 0,
            gamma: 0,
            hue: 0,
            rotation_degrees: 0,
            zoom: 0.0,
            pan_x: 0.0,
            pan_y: 0.0,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct ABRepeatRange {
    pub point_a: Option<i64>,
    pub point_b: Option<i64>,
    pub enabled: bool,
}
