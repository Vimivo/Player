//! Audio DSP: 10-Band Graphic Equalizer, Bass Boost, ReplayGain,
//! Stereo Virtualizer, Voice Clarity, Schroeder Reverb, Limiter, and FFT Spectrum Visualizer.

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
// Bass Boost (Low-Shelf Biquad Filter)
// ---------------------------------------------------------------------------

pub const MAX_BASS_BOOST_DB: f32 = 18.0;
pub const DEFAULT_BASS_CUTOFF_HZ: f32 = 100.0;

pub struct BassBoost {
    sample_rate: i32,
    channels: usize,
    gain_db: f32,
    cutoff_hz: f32,
    section: Option<Section>,
}

impl BassBoost {
    pub fn new(sample_rate: i32, channels: usize) -> Self {
        assert!(sample_rate > 0 && channels > 0, "Invalid audio parameters");
        Self {
            sample_rate,
            channels,
            gain_db: 0.0,
            cutoff_hz: DEFAULT_BASS_CUTOFF_HZ,
            section: None,
        }
    }

    pub fn set_gain_db(&mut self, gain_db: f32) {
        self.gain_db = gain_db.clamp(0.0, MAX_BASS_BOOST_DB);
        self.update_coefficients();
    }

    pub fn set_cutoff_hz(&mut self, cutoff_hz: f32) {
        self.cutoff_hz = cutoff_hz.clamp(40.0, 300.0);
        self.update_coefficients();
    }

    pub fn gain_db(&self) -> f32 {
        self.gain_db
    }

    pub fn is_enabled(&self) -> bool {
        self.gain_db > 0.01
    }

    pub fn process(&mut self, samples: &mut [f32], frames: usize) {
        if let Some(section) = &mut self.section {
            section.process(samples, frames);
        }
    }

    pub fn reset(&mut self) {
        if let Some(section) = &mut self.section {
            section.reset();
        }
    }

    fn update_coefficients(&mut self) {
        if self.gain_db <= 0.01 || self.cutoff_hz >= (self.sample_rate as f32 / 2.0) {
            self.section = None;
            return;
        }

        let a = 10.0f32.powf(self.gain_db / 40.0);
        let w0 = 2.0 * PI * self.cutoff_hz / (self.sample_rate as f32);
        let cos_w0 = w0.cos();
        let sin_w0 = w0.sin();
        let s = 1.0f32; // shelf slope
        let alpha = sin_w0 / 2.0 * ((a + 1.0 / a) * (1.0 / s - 1.0) + 2.0).sqrt();
        let two_sqrt_a_alpha = 2.0 * a.sqrt() * alpha;

        let a0 = (a + 1.0) + (a - 1.0) * cos_w0 + two_sqrt_a_alpha;
        let b0 = a * ((a + 1.0) - (a - 1.0) * cos_w0 + two_sqrt_a_alpha);
        let b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cos_w0);
        let b2 = a * ((a + 1.0) - (a - 1.0) * cos_w0 - two_sqrt_a_alpha);
        let a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cos_w0);
        let a2 = (a + 1.0) + (a - 1.0) * cos_w0 - two_sqrt_a_alpha;

        let coeffs = Coefficients {
            b0: b0 / a0,
            b1: b1 / a0,
            b2: b2 / a0,
            a1: a1 / a0,
            a2: a2 / a0,
        };

        self.section = Some(Section::new(coeffs, self.channels));
    }
}

// ---------------------------------------------------------------------------
// Soft-Knee Limiter / Saturation for Volume Boost (200%) & Bass Safety
// ---------------------------------------------------------------------------

pub struct SoftLimiter {
    threshold: f32,
}

impl Default for SoftLimiter {
    fn default() -> Self {
        Self::new(0.85)
    }
}

impl SoftLimiter {
    pub fn new(threshold: f32) -> Self {
        Self {
            threshold: threshold.clamp(0.5, 0.99),
        }
    }

    #[inline]
    pub fn process_sample(&self, x: f32) -> f32 {
        let abs_x = x.abs();
        if abs_x <= self.threshold {
            x
        } else {
            let margin = 1.0 - self.threshold;
            let compressed = self.threshold + margin * ((abs_x - self.threshold) / margin).tanh();
            if x >= 0.0 {
                compressed.min(1.0)
            } else {
                (-compressed).max(-1.0)
            }
        }
    }

    pub fn process(&self, samples: &mut [f32]) {
        for s in samples.iter_mut() {
            *s = self.process_sample(*s);
        }
    }
}

// ---------------------------------------------------------------------------
// Stereo Virtualizer / Spatializer (Width Enhancer)
// ---------------------------------------------------------------------------

pub struct StereoVirtualizer {
    width: f32, // 0.0 = mono, 1.0 = normal, 2.0 = ultra-wide
}

impl Default for StereoVirtualizer {
    fn default() -> Self {
        Self::new(1.0)
    }
}

impl StereoVirtualizer {
    pub fn new(width: f32) -> Self {
        Self {
            width: width.clamp(0.0, 2.0),
        }
    }

    pub fn set_width(&mut self, width: f32) {
        self.width = width.clamp(0.0, 2.0);
    }

    pub fn width(&self) -> f32 {
        self.width
    }

    pub fn process_stereo(&self, samples: &mut [f32]) {
        if (self.width - 1.0).abs() < 0.001 {
            return;
        }

        let w = self.width;
        let mut i = 0;
        while i + 1 < samples.len() {
            let l = samples[i];
            let r = samples[i + 1];

            let mid = 0.5 * (l + r);
            let side = 0.5 * (l - r) * w;

            samples[i] = mid + side;
            samples[i + 1] = mid - side;
            i += 2;
        }
    }
}

// ---------------------------------------------------------------------------
// Speech / Dialogue Clarity Enhancer
// ---------------------------------------------------------------------------

pub struct VoiceClarity {
    channels: usize,
    enabled: bool,
    section: Option<Section>,
}

impl VoiceClarity {
    pub fn new(sample_rate: i32, channels: usize) -> Self {
        let mut clarity = Self {
            channels,
            enabled: false,
            section: None,
        };
        clarity.configure(sample_rate);
        clarity
    }

    fn configure(&mut self, sample_rate: i32) {
        let centre_hz = 2200;
        let gain_db = 4.5f32;
        let q = 1.0f32;

        let a = 10.0f32.powf(gain_db / 40.0);
        let w0 = 2.0 * PI * (centre_hz as f32) / (sample_rate as f32);
        let alpha = w0.sin() / (2.0 * q);
        let a0 = 1.0 + alpha / a;

        let coeffs = Coefficients {
            b0: (1.0 + alpha * a) / a0,
            b1: (-2.0 * w0.cos()) / a0,
            b2: (1.0 - alpha * a) / a0,
            a1: (-2.0 * w0.cos()) / a0,
            a2: (1.0 - alpha / a) / a0,
        };

        self.section = Some(Section::new(coeffs, self.channels));
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    pub fn is_enabled(&self) -> bool {
        self.enabled
    }

    pub fn process(&mut self, samples: &mut [f32], frames: usize) {
        if self.enabled {
            if let Some(sec) = &mut self.section {
                sec.process(samples, frames);
            }
        }
    }

    pub fn reset(&mut self) {
        if let Some(sec) = &mut self.section {
            sec.reset();
        }
    }
}

// ---------------------------------------------------------------------------
// Freeverb / Schroeder Algorithmic Reverb
// ---------------------------------------------------------------------------

#[derive(Clone)]
struct CombFilter {
    buffer: Vec<f32>,
    buf_idx: usize,
    filter_store: f32,
    feedback: f32,
    damp1: f32,
    damp2: f32,
}

impl CombFilter {
    fn new(size: usize) -> Self {
        Self {
            buffer: vec![0.0; size],
            buf_idx: 0,
            filter_store: 0.0,
            feedback: 0.5,
            damp1: 0.5,
            damp2: 0.5,
        }
    }

    fn set_damp(&mut self, damp: f32) {
        self.damp1 = damp;
        self.damp2 = 1.0 - damp;
    }

    fn process(&mut self, input: f32) -> f32 {
        let output = self.buffer[self.buf_idx];
        self.filter_store = output * self.damp2 + self.filter_store * self.damp1;
        self.buffer[self.buf_idx] = input + self.filter_store * self.feedback;
        self.buf_idx = (self.buf_idx + 1) % self.buffer.len();
        output
    }

    fn mute(&mut self) {
        self.buffer.fill(0.0);
        self.filter_store = 0.0;
    }
}

#[derive(Clone)]
struct AllPassFilter {
    buffer: Vec<f32>,
    buf_idx: usize,
    feedback: f32,
}

impl AllPassFilter {
    fn new(size: usize) -> Self {
        Self {
            buffer: vec![0.0; size],
            buf_idx: 0,
            feedback: 0.5,
        }
    }

    fn process(&mut self, input: f32) -> f32 {
        let bufout = self.buffer[self.buf_idx];
        let output = -input + bufout;
        self.buffer[self.buf_idx] = input + (bufout * self.feedback);
        self.buf_idx = (self.buf_idx + 1) % self.buffer.len();
        output
    }

    fn mute(&mut self) {
        self.buffer.fill(0.0);
    }
}

pub struct ReverbEngine {
    enabled: bool,
    room_size: f32,
    damping: f32,
    wet: f32,
    dry: f32,
    combs_l: Vec<CombFilter>,
    combs_r: Vec<CombFilter>,
    allpasses_l: Vec<AllPassFilter>,
    allpasses_r: Vec<AllPassFilter>,
}

impl Default for ReverbEngine {
    fn default() -> Self {
        Self::new()
    }
}

impl ReverbEngine {
    pub fn new() -> Self {
        const COMB_TUNINGS_L: [usize; 8] = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617];
        const STEREO_SPREAD: usize = 23;
        const ALLPASS_TUNINGS_L: [usize; 4] = [556, 441, 341, 225];

        let combs_l = COMB_TUNINGS_L.iter().map(|&s| CombFilter::new(s)).collect();
        let combs_r = COMB_TUNINGS_L.iter().map(|&s| CombFilter::new(s + STEREO_SPREAD)).collect();
        let allpasses_l = ALLPASS_TUNINGS_L.iter().map(|&s| AllPassFilter::new(s)).collect();
        let allpasses_r = ALLPASS_TUNINGS_L.iter().map(|&s| AllPassFilter::new(s + STEREO_SPREAD)).collect();

        let mut reverb = Self {
            enabled: false,
            room_size: 0.5,
            damping: 0.5,
            wet: 0.33,
            dry: 0.7,
            combs_l,
            combs_r,
            allpasses_l,
            allpasses_r,
        };
        reverb.update_parameters();
        reverb
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    pub fn is_enabled(&self) -> bool {
        self.enabled
    }

    pub fn set_parameters(&mut self, room_size: f32, damping: f32, wet: f32, dry: f32) {
        self.room_size = room_size.clamp(0.0, 1.0);
        self.damping = damping.clamp(0.0, 1.0);
        self.wet = wet.clamp(0.0, 1.0);
        self.dry = dry.clamp(0.0, 1.0);
        self.update_parameters();
    }

    fn update_parameters(&mut self) {
        let feedback = self.room_size * 0.28 + 0.7;
        let damp = self.damping * 0.4;

        for comb in self.combs_l.iter_mut().chain(self.combs_r.iter_mut()) {
            comb.feedback = feedback;
            comb.set_damp(damp);
        }
    }

    pub fn process_stereo(&mut self, samples: &mut [f32]) {
        if !self.enabled {
            return;
        }

        let mut i = 0;
        while i + 1 < samples.len() {
            let in_l = samples[i];
            let in_r = samples[i + 1];
            let mono_in = (in_l + in_r) * 0.015;

            let mut out_l = 0.0;
            let mut out_r = 0.0;

            for comb in &mut self.combs_l {
                out_l += comb.process(mono_in);
            }
            for comb in &mut self.combs_r {
                out_r += comb.process(mono_in);
            }

            for ap in &mut self.allpasses_l {
                out_l = ap.process(out_l);
            }
            for ap in &mut self.allpasses_r {
                out_r = ap.process(out_r);
            }

            samples[i] = in_l * self.dry + out_l * self.wet;
            samples[i + 1] = in_r * self.dry + out_r * self.wet;
            i += 2;
        }
    }

    pub fn reset(&mut self) {
        for comb in self.combs_l.iter_mut().chain(self.combs_r.iter_mut()) {
            comb.mute();
        }
        for ap in self.allpasses_l.iter_mut().chain(self.allpasses_r.iter_mut()) {
            ap.mute();
        }
    }
}

// ---------------------------------------------------------------------------
// FFT Spectrum Visualizer (Pure Rust Radix-2 Cooley-Tukey + Log Binning)
// ---------------------------------------------------------------------------

pub struct SpectrumVisualizer {
    fft_size: usize,
    num_bands: usize,
    window: Vec<f32>,
    decay_rate: f32,
    current_levels: Vec<f32>,
}

impl SpectrumVisualizer {
    pub fn new(num_bands: usize) -> Self {
        let fft_size = 512;
        let mut window = Vec::with_capacity(fft_size);
        for i in 0..fft_size {
            let w = 0.5 * (1.0 - (2.0 * PI * i as f32 / (fft_size - 1) as f32).cos());
            window.push(w);
        }

        Self {
            fft_size,
            num_bands: num_bands.clamp(8, 64),
            window,
            decay_rate: 0.12,
            current_levels: vec![0.0; num_bands],
        }
    }

    pub fn process_samples(&mut self, samples: &[f32]) -> &[f32] {
        if samples.len() < self.fft_size {
            for val in &mut self.current_levels {
                *val = (*val - self.decay_rate).max(0.0);
            }
            return &self.current_levels;
        }

        let mut real = vec![0.0f32; self.fft_size];
        let mut imag = vec![0.0f32; self.fft_size];
        for i in 0..self.fft_size {
            real[i] = samples[i] * self.window[i];
        }

        Self::fft(&mut real, &mut imag);

        let half = self.fft_size / 2;
        let mut magnitudes = Vec::with_capacity(half);
        for i in 0..half {
            let mag = (real[i] * real[i] + imag[i] * imag[i]).sqrt() / (self.fft_size as f32 / 2.0);
            magnitudes.push(mag);
        }

        let bands = self.num_bands;
        for b in 0..bands {
            let start_fraction = (b as f32 / bands as f32).powf(2.0);
            let end_fraction = ((b + 1) as f32 / bands as f32).powf(2.0);

            let start_idx = (start_fraction * (half as f32 - 1.0)).floor() as usize;
            let end_idx = ((end_fraction * (half as f32 - 1.0)).ceil() as usize).max(start_idx + 1).min(half);

            let mut sum = 0.0;
            let count = (end_idx - start_idx).max(1);
            for i in start_idx..end_idx {
                sum += magnitudes[i];
            }
            let avg = sum / (count as f32);

            let db = 20.0 * (avg.max(1e-4)).log10();
            let target = ((db + 60.0) / 60.0).clamp(0.0, 1.0);

            if target > self.current_levels[b] {
                self.current_levels[b] = target;
            } else {
                self.current_levels[b] = (self.current_levels[b] - self.decay_rate).max(target);
            }
        }

        &self.current_levels
    }

    pub fn bands(&self) -> &[f32] {
        &self.current_levels
    }

    fn fft(real: &mut [f32], imag: &mut [f32]) {
        let n = real.len();
        assert!(n.is_power_of_two());

        let mut j = 0;
        for i in 0..n {
            if i < j {
                real.swap(i, j);
                imag.swap(i, j);
            }
            let mut bit = n >> 1;
            while bit >= 1 && j & bit != 0 {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;
        }

        let mut len = 2;
        while len <= n {
            let half = len / 2;
            let angle = -2.0 * PI / (len as f32);
            let w_step_real = angle.cos();
            let w_step_imag = angle.sin();

            let mut i = 0;
            while i < n {
                let mut w_real = 1.0;
                let mut w_imag = 0.0;
                for k in 0..half {
                    let u_real = real[i + k];
                    let u_imag = imag[i + k];

                    let v_real = real[i + k + half] * w_real - imag[i + k + half] * w_imag;
                    let v_imag = real[i + k + half] * w_imag + imag[i + k + half] * w_real;

                    real[i + k] = u_real + v_real;
                    imag[i + k] = u_imag + v_imag;
                    real[i + k + half] = u_real - v_real;
                    imag[i + k + half] = u_imag - v_imag;

                    let next_w_real = w_real * w_step_real - w_imag * w_step_imag;
                    let next_w_imag = w_real * w_step_imag + w_imag * w_step_real;
                    w_real = next_w_real;
                    w_imag = next_w_imag;
                }
                i += len;
            }
            len <<= 1;
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

    #[test]
    fn test_bass_boost_processing() {
        let mut bb = BassBoost::new(44100, 2);
        assert!(!bb.is_enabled());

        bb.set_gain_db(12.0);
        assert!(bb.is_enabled());
        assert_eq!(bb.gain_db(), 12.0);

        let mut samples = Vec::new();
        for i in 0..100 {
            let s = (2.0 * PI * 60.0 * i as f32 / 44100.0).sin();
            samples.push(s);
            samples.push(s);
        }
        let input_energy: f32 = samples.iter().map(|&x| x * x).sum();
        bb.process(&mut samples, 100);
        let output_energy: f32 = samples.iter().map(|&x| x * x).sum();

        assert!(output_energy > input_energy * 2.0);
    }

    #[test]
    fn test_soft_limiter_never_exceeds_bounds() {
        let limiter = SoftLimiter::default();
        let mut samples = vec![-3.0, -1.5, -0.5, 0.0, 0.5, 1.5, 3.0];
        limiter.process(&mut samples);

        for &s in &samples {
            assert!(s >= -1.0 && s <= 1.0, "Sample out of bounds: {}", s);
        }
        assert_eq!(samples[3], 0.0);
        assert_eq!(samples[4], 0.5);
    }

    #[test]
    fn test_stereo_virtualizer_modes() {
        let mut virt = StereoVirtualizer::new(0.0);
        let mut samples = vec![1.0, 0.0, 0.5, -0.5];
        virt.process_stereo(&mut samples);
        assert_eq!(samples[0], samples[1]);
        assert_eq!(samples[2], samples[3]);

        virt.set_width(2.0);
        let mut wide_samples = vec![1.0, 0.0];
        virt.process_stereo(&mut wide_samples);
        assert!(wide_samples[0] > 1.0);
        assert!(wide_samples[1] < 0.0);
    }

    #[test]
    fn test_reverb_engine_decay_tail() {
        let mut reverb = ReverbEngine::new();
        reverb.set_enabled(true);
        reverb.set_parameters(0.7, 0.3, 0.5, 0.5);

        let mut impulse = vec![0.0f32; 8192];
        impulse[0] = 1.0;
        impulse[1] = 1.0;
        reverb.process_stereo(&mut impulse);

        let tail_energy: f32 = impulse[2300..].iter().map(|&x| x * x).sum();
        assert!(tail_energy > 0.00001, "Tail energy was {}", tail_energy);
    }

    #[test]
    fn test_spectrum_visualizer_fft_bins() {
        let mut visualizer = SpectrumVisualizer::new(16);
        assert_eq!(visualizer.bands().len(), 16);

        let mut samples = Vec::new();
        for i in 0..512 {
            let s = (2.0 * PI * 1000.0 * i as f32 / 44100.0).sin();
            samples.push(s);
        }

        let bands = visualizer.process_samples(&samples);
        assert_eq!(bands.len(), 16);
        let peak = bands.iter().copied().fold(0.0f32, f32::max);
        assert!(peak > 0.5);
    }

    #[test]
    fn test_voice_clarity_processing() {
        let mut vc = VoiceClarity::new(44100, 2);
        assert!(!vc.is_enabled());
        vc.set_enabled(true);
        assert!(vc.is_enabled());

        let mut samples = Vec::new();
        for i in 0..100 {
            let s = (2.0 * PI * 2200.0 * i as f32 / 44100.0).sin();
            samples.push(s);
            samples.push(s);
        }
        let in_energy: f32 = samples.iter().map(|&x| x * x).sum();
        vc.process(&mut samples, 100);
        let out_energy: f32 = samples.iter().map(|&x| x * x).sum();
        assert!(out_energy > in_energy * 1.2);
    }
}