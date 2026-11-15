//! Playback Engine Contracts, State Machine, and libmpv Command Mapping.

use crate::model::{ChapterInfo, TrackInfo, VideoAdjustments, VideoAspectRatio};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum PlaybackStatus {
    Idle,
    Preparing,
    Playing,
    Paused,
    Completed,
    Error { message: String },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct PlaybackState {
    pub status: PlaybackStatus,
    pub position_millis: i64,
    pub duration_millis: Option<i64>,
    pub volume: f32, // 0.0 to 2.0 (200% software boost)
    pub speed: f32,  // 0.25 to 4.0
    pub aspect_ratio: VideoAspectRatio,
    pub adjustments: VideoAdjustments,
    pub tracks: Vec<TrackInfo>,
    pub chapters: Vec<ChapterInfo>,
}

impl Default for PlaybackState {
    fn default() -> Self {
        Self {
            status: PlaybackStatus::Idle,
            position_millis: 0,
            duration_millis: None,
            volume: 1.0,
            speed: 1.0,
            aspect_ratio: VideoAspectRatio::Original,
            adjustments: VideoAdjustments::default(),
            tracks: Vec::new(),
            chapters: Vec::new(),
        }
    }
}

/// Commands executed by a player engine.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum PlayerCommand {
    Open {
        uri: String,
        start_position_millis: i64,
    },
    Play,
    Pause,
    Stop,
    Seek {
        position_millis: i64,
    },
    SetVolume {
        volume: f32,
    },
    SetSpeed {
        speed: f32,
    },
    SetAspectRatio {
        ratio: VideoAspectRatio,
    },
    SetAdjustments {
        adjustments: VideoAdjustments,
    },
    SelectAudioTrack {
        track_id: String,
    },
    SelectSubtitleTrack {
        track_id: String,
    },
    SetAudioDelayMillis {
        delay_ms: i64,
    },
    SetSubtitleDelayMillis {
        delay_ms: i64,
    },
}

/// Formats commands and properties for libmpv C API bindings (`mpv_command`, `mpv_set_property_string`).
pub struct MpvCommandMapper;

impl MpvCommandMapper {
    pub fn load_file(uri: &str, start_position_millis: i64) -> Vec<String> {
        if start_position_millis > 0 {
            let start_sec = start_position_millis as f64 / 1000.0;
            vec![
                "loadfile".into(),
                uri.into(),
                "replace".into(),
                format!("start={:.3}", start_sec),
            ]
        } else {
            vec!["loadfile".into(), uri.into(), "replace".into()]
        }
    }

    pub fn aspect_ratio_property(ratio: VideoAspectRatio) -> (&'static str, &'static str) {
        let val = match ratio {
            VideoAspectRatio::Original => "-1",
            VideoAspectRatio::Fit => "no",
            VideoAspectRatio::Fill => "no", // Fill is mapped to panscan=1.0 in mpv
            VideoAspectRatio::Ratio16_9 => "16:9",
            VideoAspectRatio::Ratio4_3 => "4:3",
            VideoAspectRatio::Ratio21_9 => "21:9",
        };
        ("video-aspect-override", val)
    }

    pub fn volume_property(volume: f32) -> (&'static str, String) {
        // mpv volume 0 to 100 is 1.0; 2.0 is 200% with limiter
        let mpv_vol = (volume * 100.0).clamp(0.0, 200.0);
        ("volume", format!("{:.1}", mpv_vol))
    }

    pub fn speed_property(speed: f32) -> (&'static str, String) {
        let clamped = speed.clamp(0.25, 4.0);
        ("speed", format!("{:.2}", clamped))
    }

    pub fn delay_property(delay_ms: i64, is_subtitle: bool) -> (&'static str, String) {
        let prop = if is_subtitle { "sub-delay" } else { "audio-delay" };
        let sec = delay_ms as f64 / 1000.0;
        (prop, format!("{:.3}", sec))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mpv_command_mapper() {
        let load = MpvCommandMapper::load_file("file:///video.mkv", 4500);
        assert_eq!(load, vec!["loadfile", "file:///video.mkv", "replace", "start=4.500"]);

        let (prop, val) = MpvCommandMapper::aspect_ratio_property(VideoAspectRatio::Ratio16_9);
        assert_eq!(prop, "video-aspect-override");
        assert_eq!(val, "16:9");

        let (vol_prop, vol_val) = MpvCommandMapper::volume_property(1.5);
        assert_eq!(vol_prop, "volume");
        assert_eq!(vol_val, "150.0");

        let (delay_prop, delay_val) = MpvCommandMapper::delay_property(250, true);
        assert_eq!(delay_prop, "sub-delay");
        assert_eq!(delay_val, "0.250");
    }
}
