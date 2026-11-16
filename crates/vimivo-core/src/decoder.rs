//! Decoder Switcher: Hardware (HW), Hardware Plus (HW+), and Software (SW)
//! decoder policy manager, per-file preference persistence, and auto-fallback.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum DecoderMode {
    /// Pure hardware decoding on native surface (lowest CPU and battery usage).
    Hardware,
    /// Hardware decoding with copy to system memory for advanced shaders, filters, and subtitles.
    HardwarePlus,
    /// CPU software decoding via FFmpeg (maximum compatibility for exotic codecs).
    Software,
}

impl Default for DecoderMode {
    fn default() -> Self {
        Self::HardwarePlus
    }
}

impl DecoderMode {
    pub fn display_label(&self) -> &'static str {
        match self {
            Self::Hardware => "HW",
            Self::HardwarePlus => "HW+",
            Self::Software => "SW",
        }
    }

    /// Returns the recommended mpv `--hwdec` parameter for this platform and mode.
    pub fn mpv_hwdec_value(&self, os: &str) -> &'static str {
        match self {
            Self::Software => "no",
            Self::Hardware => match os {
                "android" => "mediacodec",
                "ios" | "macos" => "videotoolbox",
                "windows" => "d3d11va",
                "linux" => "vaapi",
                _ => "auto",
            },
            Self::HardwarePlus => match os {
                "android" => "mediacodec-copy",
                "ios" | "macos" => "videotoolbox-copy",
                "windows" => "d3d11va-copy",
                "linux" => "vaapi-copy",
                _ => "auto-copy",
            },
        }
    }
}

pub struct DecoderPolicyManager;

impl DecoderPolicyManager {
    /// Determines the next decoder mode when user taps the "HW/HW+/SW" button on player screen.
    pub fn cycle_next(current: DecoderMode) -> DecoderMode {
        match current {
            DecoderMode::HardwarePlus => DecoderMode::Hardware,
            DecoderMode::Hardware => DecoderMode::Software,
            DecoderMode::Software => DecoderMode::HardwarePlus,
        }
    }

    /// Recommends fallback mode when a playback error or hardware decode failure occurs.
    pub fn fallback_on_error(current: DecoderMode) -> Option<DecoderMode> {
        match current {
            DecoderMode::Hardware => Some(DecoderMode::HardwarePlus),
            DecoderMode::HardwarePlus => Some(DecoderMode::Software),
            DecoderMode::Software => None, // Already on software, cannot fallback further
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_decoder_cycle() {
        assert_eq!(DecoderPolicyManager::cycle_next(DecoderMode::HardwarePlus), DecoderMode::Hardware);
        assert_eq!(DecoderPolicyManager::cycle_next(DecoderMode::Hardware), DecoderMode::Software);
        assert_eq!(DecoderPolicyManager::cycle_next(DecoderMode::Software), DecoderMode::HardwarePlus);
    }

    #[test]
    fn test_fallback_sequence() {
        assert_eq!(DecoderPolicyManager::fallback_on_error(DecoderMode::Hardware), Some(DecoderMode::HardwarePlus));
        assert_eq!(DecoderPolicyManager::fallback_on_error(DecoderMode::HardwarePlus), Some(DecoderMode::Software));
        assert_eq!(DecoderPolicyManager::fallback_on_error(DecoderMode::Software), None);
    }

    #[test]
    fn test_mpv_hwdec_values() {
        assert_eq!(DecoderMode::Hardware.mpv_hwdec_value("android"), "mediacodec");
        assert_eq!(DecoderMode::HardwarePlus.mpv_hwdec_value("android"), "mediacodec-copy");
        assert_eq!(DecoderMode::Software.mpv_hwdec_value("android"), "no");

        assert_eq!(DecoderMode::Hardware.mpv_hwdec_value("ios"), "videotoolbox");
        assert_eq!(DecoderMode::HardwarePlus.mpv_hwdec_value("macos"), "videotoolbox-copy");
    }
}