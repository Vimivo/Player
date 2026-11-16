//! Touch Gesture Engine: Mobile player gestures (Brightness, Volume up to 200%,
//! Horizontal Seek Scrubbing, Pinch-to-Zoom & Pan, Double-Tap Seek, 2× Speed Long-Press, and Screen Lock).

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScreenHalf {
    Left,
    Right,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct SeekGestureResult {
    pub target_position_ms: i64,
    pub delta_seconds: i32,
    pub formatted_delta: String, // e.g. "+00:15" or "-00:10"
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct PinchZoomResult {
    pub zoom_scale: f32, // 1.0 to 4.0
    pub pan_x: f32,
    pub pan_y: f32,
}

pub struct PlayerGestureEngine;

impl PlayerGestureEngine {
    /// Calculates brightness adjustment from vertical swipe on left side of screen.
    /// `screen_height` is in logical points/pixels.
    pub fn calculate_brightness_swipe(current_brightness: f32, delta_y: f32, screen_height: f32) -> f32 {
        if screen_height <= 0.0 {
            return current_brightness;
        }
        // Swiping up (negative delta_y) increases brightness
        let change = -delta_y / screen_height;
        (current_brightness + change).clamp(0.0, 1.0)
    }

    /// Calculates volume adjustment from vertical swipe on right side of screen.
    /// Supports volume boost up to 200% (2.0).
    pub fn calculate_volume_swipe(current_volume: f32, delta_y: f32, screen_height: f32) -> f32 {
        if screen_height <= 0.0 {
            return current_volume;
        }
        // Swiping up (negative delta_y) increases volume
        let change = (-delta_y / screen_height) * 1.5;
        (current_volume + change).clamp(0.0, 2.0)
    }

    /// Calculates horizontal seek target and formatted time delta from horizontal swipe.
    pub fn calculate_seek_swipe(
        current_position_ms: i64,
        total_duration_ms: i64,
        delta_x: f32,
        screen_width: f32,
    ) -> SeekGestureResult {
        if screen_width <= 0.0 {
            return SeekGestureResult {
                target_position_ms: current_position_ms,
                delta_seconds: 0,
                formatted_delta: "+00:00".to_string(),
            };
        }

        // Swiping across full width maps to roughly 90 seconds of seek delta
        let max_seek_range_sec = 90.0f32;
        let delta_sec = ((delta_x / screen_width) * max_seek_range_sec) as i32;
        let target_ms = (current_position_ms + (delta_sec as i64 * 1000)).clamp(0, total_duration_ms);

        let abs_sec = delta_sec.abs();
        let m = abs_sec / 60;
        let s = abs_sec % 60;
        let sign = if delta_sec >= 0 { "+" } else { "-" };
        let formatted = format!("{}{:02}:{:02}", sign, m, s);

        SeekGestureResult {
            target_position_ms: target_ms,
            delta_seconds: delta_sec,
            formatted_delta: formatted,
        }
    }

    /// Calculates double-tap seek target position (±10 seconds).
    pub fn calculate_double_tap_seek(
        current_position_ms: i64,
        total_duration_ms: i64,
        side: ScreenHalf,
    ) -> i64 {
        let delta_ms = match side {
            ScreenHalf::Left => -10_000,
            ScreenHalf::Right => 10_000,
        };
        (current_position_ms + delta_ms).clamp(0, total_duration_ms)
    }

    /// Calculates pinch-to-zoom factor and 2D pan bounds.
    pub fn calculate_pinch_zoom(
        base_scale: f32,
        pinch_ratio: f32,
        delta_pan_x: f32,
        delta_pan_y: f32,
    ) -> PinchZoomResult {
        let new_scale = (base_scale * pinch_ratio).clamp(1.0, 4.0);
        let max_pan = (new_scale - 1.0) * 200.0; // Pan boundary proportionally limits offset

        PinchZoomResult {
            zoom_scale: new_scale,
            pan_x: delta_pan_x.clamp(-max_pan, max_pan),
            pan_y: delta_pan_y.clamp(-max_pan, max_pan),
        }
    }

    /// Returns 2.0x playback speed during long-press gesture, returning to 1.0x on release.
    pub fn get_long_press_speed(is_pressed: bool) -> f32 {
        if is_pressed {
            2.0
        } else {
            1.0
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_brightness_swipe() {
        // Swipe up by 500px on 1000px screen
        let b = PlayerGestureEngine::calculate_brightness_swipe(0.5, -500.0, 1000.0);
        assert_eq!(b, 1.0);

        // Clamp to 0
        let b_min = PlayerGestureEngine::calculate_brightness_swipe(0.2, 500.0, 1000.0);
        assert_eq!(b_min, 0.0);
    }

    #[test]
    fn test_volume_swipe_with_200_percent_boost() {
        // Initial 1.0 volume, swipe up
        let v = PlayerGestureEngine::calculate_volume_swipe(1.0, -500.0, 1000.0);
        assert!(v > 1.0); // boosted!
        assert!(v <= 2.0);

        // Max clamp
        let v_max = PlayerGestureEngine::calculate_volume_swipe(1.5, -1000.0, 1000.0);
        assert_eq!(v_max, 2.0);
    }

    #[test]
    fn test_seek_swipe_formatting() {
        let res = PlayerGestureEngine::calculate_seek_swipe(60_000, 300_000, 500.0, 1000.0);
        assert!(res.delta_seconds > 0);
        assert!(res.formatted_delta.starts_with('+'));
        assert_eq!(res.target_position_ms, 60_000 + (res.delta_seconds as i64 * 1000));
    }

    #[test]
    fn test_double_tap_seek() {
        // Left side -> -10s
        let left_seek = PlayerGestureEngine::calculate_double_tap_seek(25_000, 100_000, ScreenHalf::Left);
        assert_eq!(left_seek, 15_000);

        // Right side -> +10s
        let right_seek = PlayerGestureEngine::calculate_double_tap_seek(25_000, 100_000, ScreenHalf::Right);
        assert_eq!(right_seek, 35_000);
    }

    #[test]
    fn test_pinch_zoom_pan() {
        let res = PlayerGestureEngine::calculate_pinch_zoom(1.0, 2.0, 50.0, -30.0);
        assert_eq!(res.zoom_scale, 2.0);
        assert_eq!(res.pan_x, 50.0);
        assert_eq!(res.pan_y, -30.0);
    }

    #[test]
    fn test_long_press_2x_speed() {
        assert_eq!(PlayerGestureEngine::get_long_press_speed(true), 2.0);
        assert_eq!(PlayerGestureEngine::get_long_press_speed(false), 1.0);
    }
}