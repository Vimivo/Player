//! Audio DSP: 10-Band Graphic Equalizer and ReplayGain Processor.

use std::f32::consts::PI;

pub struct GraphicEqualizerBands;

impl GraphicEqualizerBands {
    pub const CENTRES_HZ: [i32; 10] = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
}

pub const MAX_GAIN_DB: f32 = 12.0;
const Q: f32 = 1.41421356; // sqrt(2) for 1-octave bandwidth

#[derive(Debug, Clone, Copy)]
struct Coefficients {
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
}

#[derive(Debug, Clone)]
struct ChannelState {
    x1: f32,
    x2: f32,
    y1: f32,
    y2: f32,
}

impl ChannelState {
    fn new() -> Self {
        Self {
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
        }
    }

    fn reset(&mut self) {
        self.x1 = 0.0;
        self.x2 = 0.0;
        self.y1 = 0.0;
        self.y2 = 0.0;
    }
}

#[derive(Debug, Clone)]
struct Section {
    coeffs: Coefficients,
    channels: Vec<ChannelState>,
}

impl Section {
    fn new(coeffs: Coefficients, num_channels: usize) -> Self {
        Self {
            coeffs,
            channels: vec![ChannelState::new(); num_channels],
        }
    }

    fn reset(&mut self) {
        for ch in &mut self.channels {
            ch.reset();
        }
    }

    fn process(&mut self, samples: &mut [f32], frames: usize) {
        let num_ch = self.channels.len();
        let c = self.coeffs;

        for frame in 0..frames {
            for ch_idx in 0..num_ch {
                let sample_idx = frame * num_ch + ch_idx;
                if sample_idx >= samples.len() {
                    break;
                }
                let x0 = samples[sample_idx];
                let ch = &mut self.channels[ch_idx];

                // Direct Form I
                let y0 = c.b0 * x0 + c.b1 * ch.x1 + c.b2 * ch.x2 - c.a1 * ch.y1 - c.a2 * ch.y2;
                ch.x2 = ch.x1;
                ch.x1 = x0;
                ch.y2 = ch.y1;
                ch.y1 = y0;

                samples[sample_idx] = y0;
            }
        }
    }
}

pub struct GraphicEqualizer {
    sample_rate: i32,
    channels: usize,
    gains_db: Vec<f32>,
    sections: Vec<Section>,
}

impl GraphicEqualizer {
    pub fn new(sample_rate: i32, channels: usize) -> Self {
        assert!(sample_rate > 0 && channels > 0, "Invalid audio parameters");
        Self {
            sample_rate,
            channels,
            gains_db: vec![0.0; GraphicEqualizerBands::CENTRES_HZ.len()],
            sections: Vec::new(),
        }
    }

    pub fn is_flat(&self) -> bool {
        self.gains_db.iter().all(|&g| g == 0.0)
    }

    pub fn set_gains_db(&mut self, gains: &[f32]) {
        self.gains_db = GraphicEqualizerBands::CENTRES_HZ
            .iter()
            .enumerate()
            .map(|(i, _)| {
                let g = gains.get(i).copied().unwrap_or(0.0);
                g.clamp(-MAX_GAIN_DB, MAX_GAIN_DB)
            })
            .collect();

        self.sections = GraphicEqualizerBands::CENTRES_HZ
            .iter()
            .enumerate()
            .filter(|(i, &centre_hz)| self.gains_db[*i] != 0.0 && centre_hz < self.sample_rate / 2)
            .map(|(i, &centre_hz)| {
                let coeffs = self.compute_coefficients(centre_hz, self.gains_db[i]);
                Section::new(coeffs, self.channels)
            })
            .collect();
    }

    pub fn process(&mut self, samples: &mut [f32], frames: usize) {
        if self.is_flat() {
            return;
        }
        for section in &mut self.sections {
            section.process(samples, frames);
        }
    }

    pub fn reset(&mut self) {
        for section in &mut self.sections {
            section.reset();
        }
    }

    fn compute_coefficients(&self, centre_hz: i32, gain_db: f32) -> Coefficients {
        let a = 10.0f32.powf(gain_db / 40.0);
        let w0 = 2.0 * PI * (centre_hz as f32) / (self.sample_rate as f32);
        let alpha = w0.sin() / (2.0 * Q);
        let a0 = 1.0 + alpha / a;

        Coefficients {
            b0: (1.0 + alpha * a) / a0,
            b1: (-2.0 * w0.cos()) / a0,
            b2: (1.0 - alpha * a) / a0,
            a1: (-2.0 * w0.cos()) / a0,
            a2: (1.0 - alpha / a) / a0,
        }
    }
}

// ---------------------------------------------------------------------------
// ReplayGain
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ReplayGainMode {
    Off,
    Track,
    Album,
}

pub struct ReplayGain;

impl ReplayGain {
    pub fn parse_gain_db(text: &str) -> Option<f32> {
        let cleaned = text
            .trim()
            .trim_end_matches("dB")
            .trim_end_matches("DB")
            .trim();
        cleaned.parse::<f32>().ok().filter(|f| f.is_finite())
    }

    pub fn parse_peak(text: &str) -> Option<f32> {
        text.trim()
            .parse::<f32>()
            .ok()
            .filter(|&f| f.is_finite() && f > 0.0)
    }

    pub fn linear_gain(
        mode: ReplayGainMode,
        track_gain_db: Option<f32>,
        album_gain_db: Option<f32>,
        track_peak: Option<f32>,
        album_peak: Option<f32>,
    ) -> f32 {
        if mode == ReplayGainMode::Off {
            return 1.0;
        }

        let use_album = mode == ReplayGainMode::Album && album_gain_db.is_some();
        let gain_db = if use_album {
            album_gain_db
        } else {
            track_gain_db.or(album_gain_db)
        };

        let gain_db = match gain_db {
            Some(g) => g,
            None => return 1.0,
        };

        let peak = if use_album {
            album_peak
        } else {
            track_peak.or(album_peak)
        };

        let linear = 10.0f32.powf(gain_db / 20.0);
        if let Some(p) = peak {
            if p * linear > 1.0 {
                return 1.0 / p;
            }
        }
        linear
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_equalizer_flat_leaves_samples_untouched() {
        let mut eq = GraphicEqualizer::new(44100, 2);
        let mut samples = vec![0.5f32, -0.5f32, 0.25f32, -0.25f32];
        let original = samples.clone();
        eq.process(&mut samples, 2);
        assert_eq!(samples, original);
    }

    #[test]
    fn test_replay_gain_parsing_and_linear_scale() {
        assert_eq!(ReplayGain::parse_gain_db("-6.0 dB"), Some(-6.0));
        let gain = ReplayGain::linear_gain(ReplayGainMode::Track, Some(-6.0), None, None, None);
        assert!((gain - 0.50118).abs() < 0.01);
    }
}
