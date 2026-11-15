//! Universal Stream URL Validation and Reconnection Policies.

use serde::{Deserialize, Serialize};

pub const MAX_URL_LENGTH: usize = 4096;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum StreamProtocol {
    Http,
    Https,
    Hls,
    Dash,
    Rtsp,
    Rtp,
    Udp,
    Mms,
    Smb,
    Sftp,
    Ftp,
    Nfs,
    Capture,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum StreamType {
    Live,
    Vod,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum StreamFailureReason {
    InvalidUrl,
    UnsupportedProtocol,
    WebpageRejected,
    ControlCharacters,
    LengthExceeded,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum StreamValidationResult {
    Valid {
        protocol: StreamProtocol,
        stream_type: StreamType,
        normalized_url: String,
        is_live: bool,
        display_title: String,
    },
    Invalid {
        reason: StreamFailureReason,
        message: String,
    },
}

pub struct StreamValidator;

impl StreamValidator {
    const WEBPAGE_DOMAINS: &'static [&'static str] = &[
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
        "vimeo.com", "www.vimeo.com",
        "tiktok.com", "www.tiktok.com",
        "facebook.com", "www.facebook.com", "fb.watch",
        "instagram.com", "www.instagram.com",
        "twitter.com", "x.com",
        "dailymotion.com", "www.dailymotion.com",
        "reddit.com", "www.reddit.com",
    ];

    const WEBPAGE_EXTENSIONS: &'static [&'static str] = &[
        "html", "htm", "php", "asp", "aspx", "jsp",
    ];

    pub fn validate(raw_url: &str) -> StreamValidationResult {
        let trimmed = raw_url.trim();

        if trimmed.is_empty() {
            return StreamValidationResult::Invalid {
                reason: StreamFailureReason::InvalidUrl,
                message: "The URL cannot be blank.".into(),
            };
        }

        if trimmed.len() > MAX_URL_LENGTH {
            return StreamValidationResult::Invalid {
                reason: StreamFailureReason::LengthExceeded,
                message: format!("The provided URL exceeds maximum supported length of {} characters.", MAX_URL_LENGTH),
            };
        }

        if trimmed.chars().any(|c| c.is_control()) {
            return StreamValidationResult::Invalid {
                reason: StreamFailureReason::ControlCharacters,
                message: "The URL contains forbidden control characters.".into(),
            };
        }

        let scheme_candidate = trimmed
            .split("://")
            .next()
            .unwrap_or(trimmed)
            .split(':')
            .next()
            .unwrap_or(trimmed)
            .to_lowercase();

        if !trimmed.contains(':') || scheme_candidate == trimmed || scheme_candidate.contains('/') || scheme_candidate.contains('.') {
            return StreamValidationResult::Invalid {
                reason: StreamFailureReason::InvalidUrl,
                message: "Invalid URL format. Please include protocol (e.g. https:// or rtsp://).".into(),
            };
        }

        let scheme = scheme_candidate.as_str();

        if matches!(scheme, "file" | "content" | "javascript" | "data" | "about") {
            return StreamValidationResult::Invalid {
                reason: StreamFailureReason::UnsupportedProtocol,
                message: format!("Protocol '{}' cannot be opened as a network media stream.", scheme),
            };
        }

        let device_schemes = ["avfoundation", "dshow", "v4l2", "lavfi", "gdigrab", "dvd", "bd", "cdda"];
        let is_device = device_schemes.contains(&scheme);

        if !is_device && !matches!(scheme, "http" | "https" | "rtsp" | "rtsps" | "rtp" | "udp" | "mms" | "smb" | "sftp" | "ftp" | "nfs") {
            return StreamValidationResult::Invalid {
                reason: StreamFailureReason::UnsupportedProtocol,
                message: format!("Protocol '{}' is not supported.", scheme),
            };
        }

        if is_device {
            let title = match scheme {
                "avfoundation" => if trimmed.contains("default") { "Screen Capture" } else { "Camera Capture" },
                "dshow" => "DirectShow Video Capture",
                "v4l2" => "V4L2 Video Device",
                "gdigrab" | "lavfi" => "Screen Capture (Display)",
                "dvd" => "DVD Optical Disc",
                "bd" => "Blu-ray Disc",
                "cdda" => "Audio CD",
                _ => "Capture Device",
            };
            let is_live = !matches!(scheme, "dvd" | "bd" | "cdda");
            return StreamValidationResult::Valid {
                protocol: StreamProtocol::Capture,
                stream_type: if is_live { StreamType::Live } else { StreamType::Vod },
                normalized_url: trimmed.to_string(),
                is_live,
                display_title: title.to_string(),
            };
        }

        // Host and domain check
        let after_scheme = match trimmed.split_once("://") {
            Some((_, rest)) => rest,
            None => return StreamValidationResult::Invalid {
                reason: StreamFailureReason::InvalidUrl,
                message: "Expected '://' after protocol scheme.".into(),
            },
        };

        let host = after_scheme.split('/').next().unwrap_or(after_scheme).split(':').next().unwrap_or("").to_lowercase();
        for &webpage_domain in Self::WEBPAGE_DOMAINS {
            if host == webpage_domain || host.ends_with(&format!(".{}", webpage_domain)) {
                return StreamValidationResult::Invalid {
                    reason: StreamFailureReason::WebpageRejected,
                    message: "Web page URLs from platforms like YouTube/Vimeo/TikTok are not supported directly. Please provide a direct stream link.".into(),
                };
            }
        }

        // Path and extension check
        let path = after_scheme.split('?').next().unwrap_or(after_scheme);
        let ext = path.rsplit('.').next().unwrap_or("").to_lowercase();

        for &web_ext in Self::WEBPAGE_EXTENSIONS {
            if ext == web_ext {
                return StreamValidationResult::Invalid {
                    reason: StreamFailureReason::WebpageRejected,
                    message: format!("URLs pointing to '.{}' web documents cannot be played as media streams.", web_ext),
                };
            }
        }

        let (protocol, is_live) = match scheme {
            "rtsp" | "rtsps" => (StreamProtocol::Rtsp, true),
            "rtp" => (StreamProtocol::Rtp, true),
            "udp" => (StreamProtocol::Udp, true),
            "mms" => (StreamProtocol::Mms, true),
            "smb" => (StreamProtocol::Smb, false),
            "sftp" => (StreamProtocol::Sftp, false),
            "ftp" => (StreamProtocol::Ftp, false),
            "nfs" => (StreamProtocol::Nfs, false),
            _ => {
                if ext == "m3u8" {
                    (StreamProtocol::Hls, true)
                } else if ext == "mpd" {
                    (StreamProtocol::Dash, true)
                } else if scheme == "https" {
                    (StreamProtocol::Https, false)
                } else {
                    (StreamProtocol::Http, false)
                }
            }
        };

        let display_title = path.rsplit('/').next().unwrap_or(host.as_str());
        let display_title = if display_title.is_empty() { host.clone() } else { display_title.to_string() };

        StreamValidationResult::Valid {
            protocol,
            stream_type: if is_live { StreamType::Live } else { StreamType::Vod },
            normalized_url: trimmed.to_string(),
            is_live,
            display_title,
        }
    }
}

// ---------------------------------------------------------------------------
// StreamReconnectionPolicy
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct StreamReconnectionPolicy {
    pub max_retries: u32,
    pub initial_delay_ms: u64,
    pub max_delay_ms: u64,
    pub backoff_multiplier: u64,
}

impl Default for StreamReconnectionPolicy {
    fn default() -> Self {
        Self {
            max_retries: 5,
            initial_delay_ms: 1000,
            max_delay_ms: 16000,
            backoff_multiplier: 2,
        }
    }
}

impl StreamReconnectionPolicy {
    pub fn calculate_delay_ms(&self, attempt: u32) -> Option<u64> {
        if attempt >= self.max_retries {
            return None;
        }
        let exp = self.backoff_multiplier.pow(attempt);
        let delay = (self.initial_delay_ms * exp).min(self.max_delay_ms);
        Some(delay)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_stream_urls() {
        let hls = StreamValidator::validate("https://example.com/live/stream.m3u8");
        assert!(matches!(hls, StreamValidationResult::Valid { protocol: StreamProtocol::Hls, is_live: true, .. }));

        let rtsp = StreamValidator::validate("rtsp://192.168.1.100:554/cam");
        assert!(matches!(rtsp, StreamValidationResult::Valid { protocol: StreamProtocol::Rtsp, is_live: true, .. }));
    }

    #[test]
    fn test_reject_webpages() {
        let yt = StreamValidator::validate("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assert!(matches!(yt, StreamValidationResult::Invalid { reason: StreamFailureReason::WebpageRejected, .. }));

        let html = StreamValidator::validate("https://example.com/video.html");
        assert!(matches!(html, StreamValidationResult::Invalid { reason: StreamFailureReason::WebpageRejected, .. }));
    }

    #[test]
    fn test_reconnection_policy_backoff() {
        let policy = StreamReconnectionPolicy::default();
        assert_eq!(policy.calculate_delay_ms(0), Some(1000));
        assert_eq!(policy.calculate_delay_ms(1), Some(2000));
        assert_eq!(policy.calculate_delay_ms(2), Some(4000));
        assert_eq!(policy.calculate_delay_ms(5), None); // Exceeds max retries
    }
}
