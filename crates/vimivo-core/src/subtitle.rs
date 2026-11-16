//! Subtitle Engine: Dual Subtitle Coordinator, SRT/VTT Parser,
//! Auto-Encoding Detector, Gesture Helper, and Styling System.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SubtitleCue {
    pub index: usize,
    pub start_ms: i64,
    pub end_ms: i64,
    pub text: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct SubtitleStyle {
    pub font_family: String,
    pub font_size_scale: f32, // 0.5 to 3.0 (1.0 = 100%)
    pub text_color_hex: String, // e.g. "#FFFFFF"
    pub outline_color_hex: String, // e.g. "#000000"
    pub outline_thickness: f32, // 0.0 to 8.0 px
    pub background_opacity: f32, // 0.0 (transparent) to 1.0 (black box)
    pub vertical_position_percent: f32, // 0.0 (top) to 100.0 (bottom)
    pub bold: bool,
    pub italic: bool,
}

impl Default for SubtitleStyle {
    fn default() -> Self {
        Self {
            font_family: "System".to_string(),
            font_size_scale: 1.0,
            text_color_hex: "#FFFFFF".to_string(),
            outline_color_hex: "#000000".to_string(),
            outline_thickness: 2.0,
            background_opacity: 0.35,
            vertical_position_percent: 90.0,
            bold: false,
            italic: false,
        }
    }
}

// ---------------------------------------------------------------------------
// Encoding Auto-Detector
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SubtitleEncoding {
    Utf8,
    Utf16Le,
    Utf16Be,
    Windows1252,
}

pub struct SubtitleEncodingDetector;

impl SubtitleEncodingDetector {
    /// Detects text encoding and converts bytes to valid UTF-8 String.
    pub fn decode(bytes: &[u8]) -> String {
        if bytes.is_empty() {
            return String::new();
        }

        // Check BOM for UTF-16LE / UTF-16BE
        if bytes.len() >= 2 {
            if bytes[0] == 0xFF && bytes[1] == 0xFE {
                // UTF-16LE
                let u16_slice: Vec<u16> = bytes[2..]
                    .chunks_exact(2)
                    .map(|c| u16::from_le_bytes([c[0], c[1]]))
                    .collect();
                return String::from_utf16_lossy(&u16_slice);
            } else if bytes[0] == 0xFE && bytes[1] == 0xFF {
                // UTF-16BE
                let u16_slice: Vec<u16> = bytes[2..]
                    .chunks_exact(2)
                    .map(|c| u16::from_be_bytes([c[0], c[1]]))
                    .collect();
                return String::from_utf16_lossy(&u16_slice);
            }
        }

        // Strip UTF-8 BOM if present
        let data = if bytes.len() >= 3 && bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF {
            &bytes[3..]
        } else {
            bytes
        };

        // Try UTF-8 first
        if let Ok(s) = std::str::from_utf8(data) {
            return s.to_string();
        }

        // Fallback: Windows-1252 / ISO-8859-1 mapping
        data.iter()
            .map(|&b| match b {
                0x80 => '€',
                0x82 => '‚',
                0x83 => 'ƒ',
                0x84 => '„',
                0x85 => '…',
                0x86 => '†',
                0x87 => '‡',
                0x88 => 'ˆ',
                0x89 => '‰',
                0x8A => 'Š',
                0x8B => '‹',
                0x8C => 'Œ',
                0x8E => 'Ž',
                0x91 => '‘',
                0x92 => '’',
                0x93 => '“',
                0x94 => '”',
                0x95 => '•',
                0x96 => '–',
                0x97 => '—',
                0x98 => '˜',
                0x99 => '™',
                0x9A => 'š',
                0x9B => '›',
                0x9C => 'œ',
                0x9E => 'ž',
                0x9F => 'Ÿ',
                _ => b as char,
            })
            .collect()
    }
}

// ---------------------------------------------------------------------------
// SRT and WebVTT Parser
// ---------------------------------------------------------------------------

pub struct SubtitleParser;

impl SubtitleParser {
    pub fn parse(content: &str) -> Vec<SubtitleCue> {
        let lines: Vec<&str> = content.lines().map(|l| l.trim()).collect();
        let mut cues = Vec::new();
        let mut i = 0;
        let mut cue_idx = 1;

        // Skip WebVTT header if present
        if !lines.is_empty() && lines[0].starts_with("WEBVTT") {
            i += 1;
        }

        while i < lines.len() {
            let line = lines[i];
            if line.is_empty() {
                i += 1;
                continue;
            }

            // Check if this line is an index or directly a timestamp line
            if let Some((start_ms, end_ms)) = Self::parse_timestamp_line(line) {
                i += 1;
                let mut text_lines = Vec::new();
                while i < lines.len() && !lines[i].is_empty() {
                    text_lines.push(lines[i]);
                    i += 1;
                }
                cues.push(SubtitleCue {
                    index: cue_idx,
                    start_ms,
                    end_ms,
                    text: text_lines.join("\n"),
                });
                cue_idx += 1;
            } else if i + 1 < lines.len() {
                // Might be an index followed by timestamp
                if let Some((start_ms, end_ms)) = Self::parse_timestamp_line(lines[i + 1]) {
                    i += 2;
                    let mut text_lines = Vec::new();
                    while i < lines.len() && !lines[i].is_empty() {
                        text_lines.push(lines[i]);
                        i += 1;
                    }
                    cues.push(SubtitleCue {
                        index: cue_idx,
                        start_ms,
                        end_ms,
                        text: text_lines.join("\n"),
                    });
                    cue_idx += 1;
                } else {
                    i += 1;
                }
            } else {
                i += 1;
            }
        }

        cues
    }

    fn parse_timestamp_line(line: &str) -> Option<(i64, i64)> {
        // Formats: "00:01:20,000 --> 00:01:23,500" or "00:01:20.000 --> 00:01:23.500"
        let parts: Vec<&str> = line.split("-->").collect();
        if parts.len() != 2 {
            return None;
        }
        let start_str = parts[0].trim();
        let end_str = parts[1].trim().split_whitespace().next()?; // take first token in case of VTT positioning tokens

        let start = Self::parse_timecode(start_str)?;
        let end = Self::parse_timecode(end_str)?;
        Some((start, end))
    }

    fn parse_timecode(tc: &str) -> Option<i64> {
        let tc = tc.replace(',', ".");
        let parts: Vec<&str> = tc.split(':').collect();
        if parts.len() == 3 {
            let h: i64 = parts[0].parse().ok()?;
            let m: i64 = parts[1].parse().ok()?;
            let sec_parts: Vec<&str> = parts[2].split('.').collect();
            let s: i64 = sec_parts[0].parse().ok()?;
            let ms: i64 = if sec_parts.len() > 1 {
                let ms_str = format!("{:0<3}", sec_parts[1]);
                ms_str[..3].parse().ok()?
            } else {
                0
            };
            Some((h * 3600 + m * 60 + s) * 1000 + ms)
        } else if parts.len() == 2 {
            let m: i64 = parts[0].parse().ok()?;
            let sec_parts: Vec<&str> = parts[1].split('.').collect();
            let s: i64 = sec_parts[0].parse().ok()?;
            let ms: i64 = if sec_parts.len() > 1 {
                let ms_str = format!("{:0<3}", sec_parts[1]);
                ms_str[..3].parse().ok()?
            } else {
                0
            };
            Some((m * 60 + s) * 1000 + ms)
        } else {
            None
        }
    }
}

// ---------------------------------------------------------------------------
// Dual Subtitle Coordinator
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Default)]
pub struct SubtitleTrack {
    pub name: String,
    pub language: String,
    pub cues: Vec<SubtitleCue>,
    pub delay_ms: i64,
    pub speed_multiplier: f64, // e.g. 1.0, 0.96, etc.
}

impl SubtitleTrack {
    pub fn new(name: &str, language: &str, cues: Vec<SubtitleCue>) -> Self {
        Self {
            name: name.to_string(),
            language: language.to_string(),
            cues,
            delay_ms: 0,
            speed_multiplier: 1.0,
        }
    }

    /// Finds the active cue at current playback timestamp (in ms) taking delay & speed into account.
    pub fn cue_at(&self, playback_ms: i64) -> Option<&SubtitleCue> {
        if self.cues.is_empty() {
            return None;
        }

        // Adjust timestamp according to track speed and delay
        let effective_ms = if (self.speed_multiplier - 1.0).abs() > 0.0001 && self.speed_multiplier > 0.0 {
            ((playback_ms - self.delay_ms) as f64 / self.speed_multiplier) as i64
        } else {
            playback_ms - self.delay_ms
        };

        // Binary search for cue where start_ms <= effective_ms <= end_ms
        let idx = match self.cues.binary_search_by_key(&effective_ms, |c| c.start_ms) {
            Ok(i) => i,
            Err(i) => {
                if i > 0 {
                    i - 1
                } else {
                    return None;
                }
            }
        };

        let cue = &self.cues[idx];
        if effective_ms >= cue.start_ms && effective_ms <= cue.end_ms {
            Some(cue)
        } else {
            None
        }
    }
}

pub struct DualSubtitleCoordinator {
    pub primary_track: Option<SubtitleTrack>,
    pub secondary_track: Option<SubtitleTrack>,
    pub primary_style: SubtitleStyle,
    pub secondary_style: SubtitleStyle,
}

impl Default for DualSubtitleCoordinator {
    fn default() -> Self {
        let mut secondary_style = SubtitleStyle::default();
        // Place secondary subtitle at the top of the video
        secondary_style.vertical_position_percent = 12.0;
        secondary_style.font_size_scale = 0.9;
        secondary_style.text_color_hex = "#FFE082".to_string(); // Soft yellow accent for distinction

        Self {
            primary_track: None,
            secondary_track: None,
            primary_style: SubtitleStyle::default(),
            secondary_style,
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct ActiveSubtitles {
    pub primary_text: Option<String>,
    pub secondary_text: Option<String>,
}

impl DualSubtitleCoordinator {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_primary(&mut self, track: Option<SubtitleTrack>) {
        self.primary_track = track;
    }

    pub fn set_secondary(&mut self, track: Option<SubtitleTrack>) {
        self.secondary_track = track;
    }

    pub fn active_subtitles_at(&self, playback_ms: i64) -> ActiveSubtitles {
        ActiveSubtitles {
            primary_text: self.primary_track.as_ref().and_then(|t| t.cue_at(playback_ms)).map(|c| c.text.clone()),
            secondary_text: self.secondary_track.as_ref().and_then(|t| t.cue_at(playback_ms)).map(|c| c.text.clone()),
        }
    }
}

// ---------------------------------------------------------------------------
// Subtitle Touch Gesture Helpers
// ---------------------------------------------------------------------------

pub struct SubtitleGestures;

impl SubtitleGestures {
    /// Adjusts vertical position % (0.0 to 100.0) based on vertical drag delta.
    pub fn calculate_drag_position(current_percent: f32, delta_y_pixels: f32, screen_height_pixels: f32) -> f32 {
        if screen_height_pixels <= 0.0 {
            return current_percent;
        }
        let delta_percent = (delta_y_pixels / screen_height_pixels) * 100.0;
        (current_percent + delta_percent).clamp(5.0, 95.0)
    }

    /// Adjusts font scale (0.5 to 3.0) based on pinch gesture scale factor.
    pub fn calculate_pinch_font_scale(base_scale: f32, pinch_factor: f32) -> f32 {
        (base_scale * pinch_factor).clamp(0.5, 3.0)
    }

    /// Adjusts subtitle delay (in ms) from horizontal drag delta.
    /// E.g. each 10 pixels is 50ms of delay adjustment.
    pub fn calculate_drag_delay(current_delay_ms: i64, delta_x_pixels: f32) -> i64 {
        let ms_step = (delta_x_pixels * 5.0) as i64;
        (current_delay_ms + ms_step).clamp(-30000, 30000)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encoding_detector_utf8_and_utf16() {
        // UTF-8
        let utf8_bytes = "আমি বাংলায় গান গাই".as_bytes();
        let decoded = SubtitleEncodingDetector::decode(utf8_bytes);
        assert_eq!(decoded, "আমি বাংলায় গান গাই");

        // UTF-16LE with BOM
        let mut u16_bytes = vec![0xFF, 0xFE];
        for c in "Hello Subtitles".encode_utf16() {
            u16_bytes.extend_from_slice(&c.to_le_bytes());
        }
        let decoded_u16 = SubtitleEncodingDetector::decode(&u16_bytes);
        assert_eq!(decoded_u16, "Hello Subtitles");
    }

    #[test]
    fn test_srt_parser() {
        let srt = r#"
1
00:00:01,000 --> 00:00:04,500
First subtitle line.
Second line.

2
00:00:05,000 --> 00:00:08,200
Next scene begins.
"#;
        let cues = SubtitleParser::parse(srt);
        assert_eq!(cues.len(), 2);
        assert_eq!(cues[0].start_ms, 1000);
        assert_eq!(cues[0].end_ms, 4500);
        assert_eq!(cues[0].text, "First subtitle line.\nSecond line.");
        assert_eq!(cues[1].start_ms, 5000);
        assert_eq!(cues[1].end_ms, 8200);
    }

    #[test]
    fn test_dual_subtitles_synchronization() {
        let english_cues = vec![
            SubtitleCue {
                index: 1,
                start_ms: 1000,
                end_ms: 5000,
                text: "Hello, welcome to Vimivo.".to_string(),
            },
        ];
        let bengali_cues = vec![
            SubtitleCue {
                index: 1,
                start_ms: 1000,
                end_ms: 5000,
                text: "হ্যালো, ভিমিভো-তে স্বাগতম।".to_string(),
            },
        ];

        let mut coordinator = DualSubtitleCoordinator::new();
        coordinator.set_primary(Some(SubtitleTrack {
            name: "English".to_string(),
            language: "en".to_string(),
            cues: english_cues,
            delay_ms: 0,
            speed_multiplier: 1.0,
        }));
        coordinator.set_secondary(Some(SubtitleTrack {
            name: "Bengali".to_string(),
            language: "bn".to_string(),
            cues: bengali_cues,
            delay_ms: 0,
            speed_multiplier: 1.0,
        }));

        let active = coordinator.active_subtitles_at(3000);
        assert_eq!(active.primary_text.as_deref(), Some("Hello, welcome to Vimivo."));
        assert_eq!(active.secondary_text.as_deref(), Some("হ্যালো, ভিমিভো-তে স্বাগতম।"));

        // Outside timestamp
        let outside = coordinator.active_subtitles_at(7000);
        assert_eq!(outside.primary_text, None);
        assert_eq!(outside.secondary_text, None);
    }

    #[test]
    fn test_subtitle_gesture_calculations() {
        // Drag position: start at 90%, drag up 100px on 1000px screen
        let new_pos = SubtitleGestures::calculate_drag_position(90.0, -100.0, 1000.0);
        assert_eq!(new_pos, 80.0);

        // Pinch scale
        let scale = SubtitleGestures::calculate_pinch_font_scale(1.0, 1.5);
        assert_eq!(scale, 1.5);

        // Delay adjustment
        let delay = SubtitleGestures::calculate_drag_delay(0, 50.0);
        assert_eq!(delay, 250);
    }
}