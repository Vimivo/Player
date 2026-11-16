//! Sleep Timer Domain Logic and Volume Fade Calculation.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct SleepTimerState {
    pub deadline_epoch_millis: Option<i64>,
    pub stop_after_current: bool,
    pub fade_out_seconds: i32,
    pub remaining_millis: Option<i64>,
}

pub struct SleepFade;

impl SleepFade {
    /// Volume multiplier during sleep timer fade-out: 1.0 at start, 0.0 at end.
    pub fn volume_fraction(elapsed_millis: i64, fade_millis: i64) -> f32 {
        if fade_millis <= 0 {
            return 0.0;
        }
        (1.0 - (elapsed_millis as f32 / fade_millis as f32)).clamp(0.0, 1.0)
    }
}

pub struct SleepTimerOptions;

impl SleepTimerOptions {
    pub const DEFAULT_PRESET_MINUTES: [i32; 8] = [5, 10, 15, 30, 45, 60, 90, 120];
    pub const MAX_MINUTES: i32 = 24 * 60;
    pub const FADE_SECONDS: i32 = 10;

    pub fn custom_minutes(hours: &str, minutes: &str) -> Option<i32> {
        let parse_field = |text: &str| -> Option<i32> {
            let trimmed = text.trim();
            if trimmed.is_empty() {
                return Some(0);
            }
            if trimmed.len() > 4 || !trimmed.chars().all(|c| c.is_ascii_digit()) {
                return None;
            }
            trimmed.parse::<i32>().ok()
        };

        let h = parse_field(hours)?;
        let m = parse_field(minutes)?;
        let total = h * 60 + m;
        if (1..=Self::MAX_MINUTES).contains(&total) {
            Some(total)
        } else {
            None
        }
    }

    pub fn label(minutes: i32) -> String {
        if minutes < 60 {
            format!("{} min", minutes)
        } else if minutes % 60 == 0 {
            format!("{} h", minutes / 60)
        } else {
            format!("{} h {} min", minutes / 60, minutes % 60)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sleep_fade_volume_fraction() {
        assert_eq!(SleepFade::volume_fraction(0, 10_000), 1.0);
        assert_eq!(SleepFade::volume_fraction(5_000, 10_000), 0.5);
        assert_eq!(SleepFade::volume_fraction(10_000, 10_000), 0.0);
        assert_eq!(SleepFade::volume_fraction(15_000, 10_000), 0.0);
    }

    #[test]
    fn test_sleep_timer_options_custom_minutes() {
        assert_eq!(SleepTimerOptions::custom_minutes("1", "30"), Some(90));
        assert_eq!(SleepTimerOptions::custom_minutes("0", "45"), Some(45));
        assert_eq!(SleepTimerOptions::custom_minutes("", "20"), Some(20));
        assert_eq!(SleepTimerOptions::custom_minutes("2", ""), Some(120));
        assert_eq!(SleepTimerOptions::custom_minutes("invalid", "30"), None);
        assert_eq!(SleepTimerOptions::custom_minutes("0", "0"), None);
    }
}
