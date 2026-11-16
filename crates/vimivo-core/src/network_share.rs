//! Network Shares & NAS Protocol Client Engine: SMB, SFTP, FTP, WebDAV, and NFS
//! credential management, streaming URI construction, and remote file descriptors.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum NetworkProtocol {
    Smb,
    Sftp,
    Ftp,
    WebDav,
    Nfs,
}

impl NetworkProtocol {
    pub fn default_port(&self) -> u16 {
        match self {
            Self::Smb => 445,
            Self::Sftp => 22,
            Self::Ftp => 21,
            Self::WebDav => 80,
            Self::Nfs => 2049,
        }
    }

    pub fn scheme(&self) -> &'static str {
        match self {
            Self::Smb => "smb",
            Self::Sftp => "sftp",
            Self::Ftp => "ftp",
            Self::WebDav => "http",
            Self::Nfs => "nfs",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NetworkShareConfig {
    pub protocol: NetworkProtocol,
    pub host: String,
    pub port: u16,
    pub username: Option<String>,
    pub password: Option<String>,
    pub share_path: String, // e.g. "Media/Movies"
}

pub struct NetworkShareEngine;

impl NetworkShareEngine {
    /// Constructs authenticated streaming URI for direct playback in libmpv / native player.
    /// Example: `smb://user:pass@192.168.1.50:445/Media/Movies/Inception.mkv`
    pub fn build_stream_uri(config: &NetworkShareConfig, file_subpath: &str) -> String {
        let clean_share = config.share_path.trim_matches('/');
        let clean_file = file_subpath.trim_matches('/');

        let auth = match (&config.username, &config.password) {
            (Some(user), Some(pass)) => format!("{}:{}@", urlencoding_encode(user), urlencoding_encode(pass)),
            (Some(user), None) => format!("{}@", urlencoding_encode(user)),
            _ => String::new(),
        };

        let full_path = if clean_share.is_empty() {
            clean_file.to_string()
        } else if clean_file.is_empty() {
            clean_share.to_string()
        } else {
            format!("{}/{}", clean_share, clean_file)
        };

        format!(
            "{}://{}{}:{}/{}",
            config.protocol.scheme(),
            auth,
            config.host,
            config.port,
            full_path
        )
    }
}

fn urlencoding_encode(s: &str) -> String {
    s.chars()
        .map(|c| match c {
            'a'..='z' | 'A'..='Z' | '0'..='9' | '-' | '_' | '.' | '~' => c.to_string(),
            _ => format!("%{:02X}", c as u32),
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_smb_uri_construction() {
        let config = NetworkShareConfig {
            protocol: NetworkProtocol::Smb,
            host: "192.168.1.100".to_string(),
            port: 445,
            username: Some("admin".to_string()),
            password: Some("p@ss 123".to_string()),
            share_path: "NAS/Movies".to_string(),
        };

        let uri = NetworkShareEngine::build_stream_uri(&config, "Interstellar (2014).mkv");
        assert!(uri.starts_with("smb://admin:p%40ss%20123@192.168.1.100:445/NAS/Movies/Interstellar (2014).mkv"));
    }

    #[test]
    fn test_sftp_and_ftp_default_ports() {
        assert_eq!(NetworkProtocol::Smb.default_port(), 445);
        assert_eq!(NetworkProtocol::Sftp.default_port(), 22);
        assert_eq!(NetworkProtocol::Ftp.default_port(), 21);
        assert_eq!(NetworkProtocol::Nfs.default_port(), 2049);
    }
}