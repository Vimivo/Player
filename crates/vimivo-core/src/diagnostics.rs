//! Stats for Nerds & Technical Playback Diagnostics for Vimivo.
//!
//! Provides real-time metrics on bitrate, frame drops, active hardware decoders,
//! audio format specifications, and buffer health.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct MediaDiagnostics {
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

impl Default for MediaDiagnostics {
    fn default() -> Self {
        Self {
            file_name: "Unknown Media".to_string(),
            resolution: "1920x1080".to_string(),
            video_codec: "H.264 / AVC".to_string(),
            audio_codec: "AAC-LC".to_string(),
            audio_channels: 2,
            sample_rate_hz: 48000,
            decoder_mode: "Hardware (HW+)".to_string(),
            current_bitrate_kbps: 8500,
            dropped_frames: 0,
            total_frames: 1000,
            buffer_health_sec: 5.0,
        }
    }
}

impl MediaDiagnostics {
    pub fn dropped_frames_summary(&self) -> String {
        if self.total_frames == 0 {
            "0 / 0 (0.00%)".to_string()
        } else {
            let pct = (self.dropped_frames as f64 / self.total_frames as f64) * 100.0;
            format!("{} / {} ({:.2}%)", self.dropped_frames, self.total_frames, pct)
        }
    }

    pub fn audio_summary(&self) -> String {
        let khz = self.sample_rate_hz as f64 / 1000.0;
        let channel_desc = match self.audio_channels {
            1 => "Mono (1.0)",
            2 => "Stereo (2.0)",
            6 => "5.1 Surround",
            8 => "7.1 Surround",
            n => return format!("{} · {}ch {:.1} kHz", self.audio_codec, n, khz),
        };
        format!("{} · {} {:.1} kHz", self.audio_codec, channel_desc, khz)
    }

    pub fn video_summary(&self) -> String {
        format!("{} · {}", self.resolution, self.video_codec)
    }

    pub fn bitrate_summary(&self) -> String {
        if self.current_bitrate_kbps >= 1000 {
            format!("{:.1} Mbps", self.current_bitrate_kbps as f64 / 1000.0)
        } else {
            format!("{} kbps", self.current_bitrate_kbps)
        }
    }

    pub fn buffer_summary(&self) -> String {
        format!("{:.1} s", self.buffer_health_sec)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_media_diagnostics_formatting() {
        let diag = MediaDiagnostics {
            file_name: "interstellar_4k.mkv".to_string(),
            resolution: "3840x2160 (4K UHD)".to_string(),
            video_codec: "HEVC / H.265 Main 10".to_string(),
            audio_codec: "DTS-HD MA".to_string(),
            audio_channels: 6,
            sample_rate_hz: 48000,
            decoder_mode: "Hardware (HW+)".to_string(),
            current_bitrate_kbps: 18400,
            dropped_frames: 4,
            total_frames: 20000,
            buffer_health_sec: 7.2,
        };

        assert_eq!(diag.video_summary(), "3840x2160 (4K UHD) · HEVC / H.265 Main 10");
        assert_eq!(diag.audio_summary(), "DTS-HD MA · 5.1 Surround 48.0 kHz");
        assert_eq!(diag.bitrate_summary(), "18.4 Mbps");
        assert_eq!(diag.dropped_frames_summary(), "4 / 20000 (0.02%)");
        assert_eq!(diag.buffer_summary(), "7.2 s");
    }

    #[test]
    fn test_zero_total_frames_does_not_panic() {
        let diag = MediaDiagnostics {
            total_frames: 0,
            dropped_frames: 0,
            ..Default::default()
        };
        assert_eq!(diag.dropped_frames_summary(), "0 / 0 (0.00%)");
    }
}
