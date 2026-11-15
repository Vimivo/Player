//! Video processing tools: Letterbox/Black-bar detection, cropping math, and chapter intro skipping.

use crate::model::ChapterInfo;
use regex::Regex;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct CropRect {
    pub x: i32,
    pub y: i32,
    pub width: i32,
    pub height: i32,
}

impl CropRect {
    pub fn new(x: i32, y: i32, width: i32, height: i32) -> Result<Self, &'static str> {
        if width <= 0 || height <= 0 {
            return Err("Crop width and height must be positive");
        }
        if x < 0 || y < 0 {
            return Err("Crop origin must be non-negative");
        }
        Ok(Self { x, y, width, height })
    }

    pub fn to_mpv_value(&self) -> String {
        format!("{}x{}+{}+{}", self.width, self.height, self.x, self.y)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum CropPreset {
    None,
    Auto,
    Ratio16_9,
    Ratio21_9,
    Ratio4_3,
    Ratio1_1,
}

impl CropPreset {
    pub fn ratio(&self) -> Option<f64> {
        match self {
            CropPreset::None | CropPreset::Auto => None,
            CropPreset::Ratio16_9 => Some(16.0 / 9.0),
            CropPreset::Ratio21_9 => Some(21.0 / 9.0),
            CropPreset::Ratio4_3 => Some(4.0 / 3.0),
            CropPreset::Ratio1_1 => Some(1.0),
        }
    }
}

pub struct VideoCropMath;

impl VideoCropMath {
    pub const MIN_BAR_PIXELS: i32 = 4;

    fn even(v: i32) -> i32 {
        v & !1
    }

    pub fn centered(source_width: i32, source_height: i32, ratio: f64) -> Option<CropRect> {
        if source_width <= 0 || source_height <= 0 || ratio <= 0.0 {
            return None;
        }

        let source_ratio = source_width as f64 / source_height as f64;
        let (width, height) = if ratio < source_ratio {
            (Self::even((source_height as f64 * ratio) as i32), Self::even(source_height))
        } else {
            (Self::even(source_width), Self::even((source_width as f64 / ratio) as i32))
        };

        let rect = CropRect {
            x: Self::even((source_width - width) / 2),
            y: Self::even((source_height - height) / 2),
            width,
            height,
        };

        let no_bars = (source_width - rect.width < Self::MIN_BAR_PIXELS)
            && (source_height - rect.height < Self::MIN_BAR_PIXELS);

        if no_bars {
            None
        } else {
            Some(rect)
        }
    }

    pub fn from_content(source_width: i32, source_height: i32, content: &CropRect) -> Option<CropRect> {
        let x = Self::even(content.x);
        let y = Self::even(content.y);
        let width = Self::even((content.width).min(source_width - x));
        let height = Self::even((content.height).min(source_height - y));

        if width <= 0 || height <= 0 {
            return None;
        }

        let cropped = CropRect { x, y, width, height };
        let bars_across = source_width - cropped.width;
        let bars_down = source_height - cropped.height;

        if bars_across < Self::MIN_BAR_PIXELS && bars_down < Self::MIN_BAR_PIXELS {
            None
        } else {
            Some(cropped)
        }
    }
}

// ---------------------------------------------------------------------------
// BlackBarDetector (Luma plane scanning)
// ---------------------------------------------------------------------------

pub struct BlackBarDetector;

impl BlackBarDetector {
    pub const BLACK_LEVEL: u8 = 40;
    pub const NOISE_FRACTION: f64 = 0.01;

    pub fn content_bounds(width: i32, height: i32, luma: &[u8]) -> Option<CropRect> {
        if width <= 0 || height <= 0 || luma.len() < (width * height) as usize {
            return None;
        }

        let w = width as usize;
        let h = height as usize;

        let bright = |x: usize, y: usize| -> bool {
            luma[y * w + x] > Self::BLACK_LEVEL
        };

        let row_has_content = |y: usize| -> bool {
            let count = (0..w).filter(|&x| bright(x, y)).count();
            (count as f64) > (w as f64 * Self::NOISE_FRACTION)
        };

        let top = (0..h).find(|&y| row_has_content(y))?;
        let bottom = (top..h).rfind(|&y| row_has_content(y))?;

        let col_has_content = |x: usize| -> bool {
            let count = (top..=bottom).filter(|&y| bright(x, y)).count();
            (count as f64) > ((bottom - top + 1) as f64 * Self::NOISE_FRACTION)
        };

        let left = (0..w).find(|&x| col_has_content(x))?;
        let right = (left..w).rfind(|&x| col_has_content(x))?;

        Some(CropRect {
            x: left as i32,
            y: top as i32,
            width: (right - left + 1) as i32,
            height: (bottom - top + 1) as i32,
        })
    }

    pub fn union(bounds: &[CropRect]) -> Option<CropRect> {
        if bounds.is_empty() {
            return None;
        }

        let left = bounds.iter().map(|b| b.x).min().unwrap();
        let top = bounds.iter().map(|b| b.y).min().unwrap();
        let right = bounds.iter().map(|b| b.x + b.width).max().unwrap();
        let bottom = bounds.iter().map(|b| b.y + b.height).max().unwrap();

        Some(CropRect {
            x: left,
            y: top,
            width: right - left,
            height: bottom - top,
        })
    }
}

// ---------------------------------------------------------------------------
// IntroSkip (Chapter detection and fallback jump)
// ---------------------------------------------------------------------------

pub struct IntroSkip;

impl IntroSkip {
    pub const FALLBACK_JUMP_MILLIS: i64 = 85_000;
    const MAX_INTRO_END_MILLIS: i64 = 5 * 60_000;
    const MIN_CHAPTERS_FOR_SHORT_FIRST: usize = 3;
    const END_MARGIN_MILLIS: i64 = 1_000;

    pub fn target(position_millis: i64, chapters: &[ChapterInfo], duration_millis: Option<i64>) -> i64 {
        let intro_title_re = Regex::new(r"(?i)\b(intro|opening|op|recap|previously|prologue|cold open)\b").unwrap();

        let mut sorted = chapters.to_vec();
        sorted.sort_by_key(|c| c.start_position_millis);

        let current_idx = sorted
            .iter()
            .rposition(|c| c.start_position_millis <= position_millis);

        if let Some(idx) = current_idx {
            if idx + 1 < sorted.len() {
                let next_start = sorted[idx + 1].start_position_millis;
                let in_intro = intro_title_re.is_match(&sorted[idx].title);
                let short_first = idx == 0
                    && next_start <= Self::MAX_INTRO_END_MILLIS
                    && sorted.len() >= Self::MIN_CHAPTERS_FOR_SHORT_FIRST;

                if (in_intro || short_first) && next_start > position_millis {
                    return next_start;
                }
            }
        }

        let jump = position_millis + Self::FALLBACK_JUMP_MILLIS;
        if let Some(duration) = duration_millis.filter(|&d| d > 0) {
            let limit = duration - Self::END_MARGIN_MILLIS;
            jump.min(limit).max(position_millis)
        } else {
            jump
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_black_bar_detector_finds_bars() {
        let width = 100;
        let height = 100;
        let mut luma = vec![0u8; (width * height) as usize];

        // Fill inner 80x60 with white (255)
        for y in 20..80 {
            for x in 10..90 {
                luma[y * width + x] = 255;
            }
        }

        let bounds = BlackBarDetector::content_bounds(width as i32, height as i32, &luma).unwrap();
        assert_eq!(bounds.x, 10);
        assert_eq!(bounds.y, 20);
        assert_eq!(bounds.width, 80);
        assert_eq!(bounds.height, 60);
    }

    #[test]
    fn test_intro_skip_chapter_and_fallback() {
        let chapters = vec![
            ChapterInfo { index: 0, title: "Opening Theme".into(), start_position_millis: 0 },
            ChapterInfo { index: 1, title: "Episode Start".into(), start_position_millis: 90_000 },
            ChapterInfo { index: 2, title: "Ending".into(), start_position_millis: 1_200_000 },
        ];

        // While in intro (at 10s), jumps to 90s (next chapter)
        assert_eq!(IntroSkip::target(10_000, &chapters, Some(1_300_000)), 90_000);

        // While in episode (at 150s), falls back to +85s jump -> 235s
        assert_eq!(IntroSkip::target(150_000, &chapters, Some(1_300_000)), 235_000);
    }
}
