//! Playback Policies: Resume Policy, Playback Speeds, Video Rotation, and Snapshot Naming.

use crate::model::MediaKind;
use serde::{Deserialize, Serialize};

// ---------------------------------------------------------------------------
// Resume Policy
// ---------------------------------------------------------------------------

pub struct ResumePolicy;

impl ResumePolicy {
    pub const MIN_POSITION_MILLIS: i64 = 10_000;
    pub const END_GUARD_MILLIS: i64 = 15_000;
    pub const COMPLETION_PERCENT: i64 = 95;
    pub const LONG_AUDIO_MILLIS: i64 = 20 * 60_000;

    pub fn start_position_millis(
        kind: MediaKind,
        saved_position_millis: i64,
        duration_millis: Option<i64>,
        completed: bool,
        include_short_audio: bool,
    ) -> i64 {
        let duration = duration_millis.filter(|&d| d > 0);
        let near_end = duration.map_or(false, |d| saved_position_millis >= d - Self::END_GUARD_MILLIS);
        let mostly_watched = duration.map_or(false, |d| saved_position_millis * 100 >= d * Self::COMPLETION_PERCENT);
        let finished = completed || near_end || mostly_watched;
        let eligible_kind = kind != MediaKind::Audio || include_short_audio || duration.unwrap_or(0) >= Self::LONG_AUDIO_MILLIS;

        if saved_position_millis < Self::MIN_POSITION_MILLIS || finished || !eligible_kind {
            0
        } else {
            saved_position_millis
        }
    }
}

// ---------------------------------------------------------------------------
// Playback Speeds
// ---------------------------------------------------------------------------

pub struct PlaybackSpeeds;

impl PlaybackSpeeds {
    pub const MIN: f32 = 0.25;
    pub const MAX: f32 = 4.0;
    pub const PRESETS: [f32; 11] = [0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0, 4.0];
}

// ---------------------------------------------------------------------------
// Video Rotation
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum VideoRotation {
    None,
    Clockwise90,
    Clockwise180,
    Clockwise270,
}

impl VideoRotation {
    pub fn degrees(&self) -> i32 {
        match self {
            VideoRotation::None => 0,
            VideoRotation::Clockwise90 => 90,
            VideoRotation::Clockwise180 => 180,
            VideoRotation::Clockwise270 => 270,
        }
    }

    pub fn next(&self) -> Self {
        match self {
            VideoRotation::None => VideoRotation::Clockwise90,
            VideoRotation::Clockwise90 => VideoRotation::Clockwise180,
            VideoRotation::Clockwise180 => VideoRotation::Clockwise270,
            VideoRotation::Clockwise270 => VideoRotation::None,
        }
    }

    pub fn is_sideways(&self) -> bool {
        matches!(self, VideoRotation::Clockwise90 | VideoRotation::Clockwise270)
    }

    pub fn fit_scale(&self, container_width: f32, container_height: f32, video_aspect: f32) -> f32 {
        if !self.is_sideways() || container_width <= 0.0 || container_height <= 0.0 || video_aspect <= 0.0 {
            return 1.0;
        }
        let (fitted_width, fitted_height) = if container_width / container_height > video_aspect {
            (container_height * video_aspect, container_height)
        } else {
            (container_width, container_width / video_aspect)
        };
        (container_width / fitted_height).min(container_height / fitted_width)
    }
}

// ---------------------------------------------------------------------------
// Snapshot Naming & Format
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum SnapshotFormat {
    Png,
    Jpeg,
}

impl SnapshotFormat {
    pub fn extension(&self) -> &'static str {
        match self {
            SnapshotFormat::Png => "png",
            SnapshotFormat::Jpeg => "jpg",
        }
    }
}

pub struct SnapshotNaming;

impl SnapshotNaming {
    pub fn file_name(title: Option<&str>, position_millis: i64, timestamp_millis: i64, format: SnapshotFormat) -> String {
        let unsafe_chars = regex::Regex::new(r#"[\\/:*?"<>|\x00-\x1F]"#).unwrap();
        let whitespace = regex::Regex::new(r"\s+").unwrap();

        let raw = title.unwrap_or("");
        let clean = unsafe_chars.replace_all(raw, " ");
        let collapsed = whitespace.replace_all(&clean, " ");
        let trimmed = collapsed.trim_matches(|c| c == ' ' || c == '.');
        let short = if trimmed.len() > 60 { &trimmed[..60] } else { trimmed };
        let base_title = short.trim_matches(|c| c == ' ' || c == '.');
        let final_title = if base_title.is_empty() { "vimivo" } else { base_title };

        let total_seconds = (position_millis.max(0) / 1000) as u64;
        let hours = total_seconds / 3600;
        let minutes = (total_seconds % 3600) / 60;
        let seconds = total_seconds % 60;
        let position = format!("{}h{:02}m{:02}s", hours, minutes, seconds);

        format!("{}-{}-{}.{}", final_title, position, timestamp_millis, format.extension())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_resume_policy() {
        // Less than 10s restarts at 0
        assert_eq!(ResumePolicy::start_position_millis(MediaKind::Video, 5_000, Some(100_000), false, false), 0);

        // Valid mid-point resumes exactly
        assert_eq!(ResumePolicy::start_position_millis(MediaKind::Video, 45_000, Some(100_000), false, false), 45_000);

        // Near end (> 95%) restarts at 0
        assert_eq!(ResumePolicy::start_position_millis(MediaKind::Video, 96_000, Some(100_000), false, false), 0);
    }

    #[test]
    fn test_video_rotation_cycle() {
        let mut rot = VideoRotation::None;
        assert_eq!(rot.degrees(), 0);
        rot = rot.next();
        assert_eq!(rot.degrees(), 90);
        assert!(rot.is_sideways());
        rot = rot.next();
        assert_eq!(rot.degrees(), 180);
        rot = rot.next();
        assert_eq!(rot.degrees(), 270);
        rot = rot.next();
        assert_eq!(rot.degrees(), 0);
    }

    #[test]
    fn test_snapshot_naming() {
        let name = SnapshotNaming::file_name(Some("Solaris Flare / 4K"), 75_000, 1700000000, SnapshotFormat::Png);
        assert_eq!(name, "Solaris Flare 4K-0h01m15s-1700000000.png");
    }
}
