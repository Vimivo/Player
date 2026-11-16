//! Video-to-Audio Converter Engine: MP3, FLAC, AAC, OPUS, and WAV audio extraction
//! options, FFmpeg argument generation, and output path formatting.

use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum AudioExtractFormat {
    Mp3,
    Flac,
    Aac,
    Opus,
    Wav,
}

impl AudioExtractFormat {
    pub fn extension(&self) -> &'static str {
        match self {
            Self::Mp3 => "mp3",
            Self::Flac => "flac",
            Self::Aac => "m4a",
            Self::Opus => "opus",
            Self::Wav => "wav",
        }
    }

    pub fn codec_name(&self) -> &'static str {
        match self {
            Self::Mp3 => "libmp3lame",
            Self::Flac => "flac",
            Self::Aac => "aac",
            Self::Opus => "libopus",
            Self::Wav => "pcm_s16le",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum AudioExtractBitrate {
    Kbps128,
    Kbps192,
    Kbps256,
    Kbps320,
}

impl AudioExtractBitrate {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Kbps128 => "128k",
            Self::Kbps192 => "192k",
            Self::Kbps256 => "256k",
            Self::Kbps320 => "320k",
        }
    }
}

pub struct VideoToAudioConverter;

impl VideoToAudioConverter {
    /// Generates recommended output audio file path with the new extension in the same directory.
    pub fn generate_output_path(video_path: &Path, format: AudioExtractFormat) -> PathBuf {
        let mut path = video_path.to_path_buf();
        path.set_extension(format.extension());
        path
    }

    /// Generates standard FFmpeg command line arguments for fast, high-quality audio extraction.
    pub fn generate_ffmpeg_args(
        input_video_path: &str,
        output_audio_path: &str,
        format: AudioExtractFormat,
        bitrate: AudioExtractBitrate,
    ) -> Vec<String> {
        let mut args = vec![
            "-y".to_string(), // Overwrite output
            "-i".to_string(),
            input_video_path.to_string(),
            "-vn".to_string(), // Strip video stream
            "-c:a".to_string(),
            format.codec_name().to_string(),
        ];

        // Lossless formats (FLAC, WAV) don't need bitrate parameter
        if format != AudioExtractFormat::Flac && format != AudioExtractFormat::Wav {
            args.push("-b:a".to_string());
            args.push(bitrate.as_str().to_string());
        }

        args.push(output_audio_path.to_string());
        args
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_output_path_generation() {
        let p = Path::new("/sdcard/Movies/Concert.mkv");
        let out_mp3 = VideoToAudioConverter::generate_output_path(p, AudioExtractFormat::Mp3);
        assert_eq!(out_mp3.to_str().unwrap(), "/sdcard/Movies/Concert.mp3");

        let out_flac = VideoToAudioConverter::generate_output_path(p, AudioExtractFormat::Flac);
        assert_eq!(out_flac.to_str().unwrap(), "/sdcard/Movies/Concert.flac");
    }

    #[test]
    fn test_ffmpeg_args_generation() {
        let args = VideoToAudioConverter::generate_ffmpeg_args(
            "/movies/video.mp4",
            "/music/audio.mp3",
            AudioExtractFormat::Mp3,
            AudioExtractBitrate::Kbps320,
        );
        assert!(args.contains(&"-vn".to_string()));
        assert!(args.contains(&"libmp3lame".to_string()));
        assert!(args.contains(&"320k".to_string()));
    }
}