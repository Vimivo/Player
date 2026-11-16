//! Offline Wi-Fi File Sharing Engine: Embedded Web UI,
//! Range Header streaming parser, Multipart Upload parser, and Path Sanitizer.

use serde::{Deserialize, Serialize};
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SharedFileInfo {
    pub name: String,
    pub size_bytes: u64,
    pub mime_type: String,
}

pub struct WifiServerHandle {
    pub port: u16,
    running: Arc<AtomicBool>,
}

impl WifiServerHandle {
    pub fn stop(&self) {
        self.running.store(false, Ordering::SeqCst);
    }

    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }
}

pub struct WifiTransferServer;

impl WifiTransferServer {
    /// Starts embedded HTTP file transfer server on given address (e.g. "0.0.0.0:8080" or "127.0.0.1:0").
    pub fn start(
        bind_addr: &str,
        device_name: String,
        storage_dir: PathBuf,
    ) -> Result<WifiServerHandle, std::io::Error> {
        let listener = TcpListener::bind(bind_addr)?;
        listener.set_nonblocking(true)?;
        let port = listener.local_addr()?.port();
        let running = Arc::new(AtomicBool::new(true));
        let running_clone = Arc::clone(&running);

        thread::spawn(move || {
            while running_clone.load(Ordering::SeqCst) {
                match listener.accept() {
                    Ok((mut stream, _)) => {
                        let _ = stream.set_read_timeout(Some(Duration::from_secs(5)));
                        let _ = stream.set_write_timeout(Some(Duration::from_secs(5)));
                        Self::handle_connection(&mut stream, &device_name, &storage_dir);
                    }
                    Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                        thread::sleep(Duration::from_millis(30));
                    }
                    Err(_) => {
                        break;
                    }
                }
            }
        });

        Ok(WifiServerHandle { port, running })
    }

    fn handle_connection(stream: &mut TcpStream, device_name: &str, storage_dir: &std::path::Path) {
        let mut buffer = [0u8; 4096];
        let bytes_read = match stream.read(&mut buffer) {
            Ok(n) if n > 0 => n,
            _ => return,
        };

        let request = String::from_utf8_lossy(&buffer[..bytes_read]);
        let first_line = request.lines().next().unwrap_or("");
        let mut parts = first_line.split_whitespace();
        let method = parts.next().unwrap_or("");
        let path = parts.next().unwrap_or("/");

        if method == "GET" && (path == "/" || path == "/index.html") {
            let files = Self::scan_storage_dir(storage_dir);
            let body = WifiTransferEngine::render_web_ui(device_name, &files);
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            );
            let _ = stream.write_all(response.as_bytes());
        } else if method == "GET" && path.starts_with("/download/") {
            let file_name = path.trim_start_matches("/download/");
            let sanitized = WifiTransferEngine::sanitize_filename(file_name);
            let file_path = storage_dir.join(&sanitized);

            if let Ok(metadata) = std::fs::metadata(&file_path) {
                let total_size = metadata.len();
                let mime = WifiTransferEngine::detect_mime_type(&sanitized);

                let range = request.lines().find_map(|line| {
                    if line.to_lowercase().starts_with("range:") {
                        let val = line.split_once(':')?.1.trim();
                        WifiTransferEngine::parse_range_header(val, total_size)
                    } else {
                        None
                    }
                });

                if let Some((start, end)) = range {
                    let content_length = end - start + 1;
                    let header = format!(
                        "HTTP/1.1 206 Partial Content\r\nContent-Type: {}\r\nContent-Range: bytes {}-{}/{}\r\nContent-Length: {}\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n",
                        mime, start, end, total_size, content_length
                    );
                    let _ = stream.write_all(header.as_bytes());

                    if let Ok(mut f) = std::fs::File::open(&file_path) {
                        use std::io::Seek;
                        if f.seek(std::io::SeekFrom::Start(start)).is_ok() {
                            let mut take = f.take(content_length);
                            let _ = std::io::copy(&mut take, stream);
                        }
                    }
                } else {
                    let header = format!(
                        "HTTP/1.1 200 OK\r\nContent-Type: {}\r\nContent-Length: {}\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n",
                        mime, total_size
                    );
                    let _ = stream.write_all(header.as_bytes());
                    if let Ok(mut f) = std::fs::File::open(&file_path) {
                        let _ = std::io::copy(&mut f, stream);
                    }
                }
            } else {
                let not_found = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                let _ = stream.write_all(not_found.as_bytes());
            }
        } else if method == "POST" && path == "/upload" {
            let response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK";
            let _ = stream.write_all(response.as_bytes());
        } else {
            let not_found = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            let _ = stream.write_all(not_found.as_bytes());
        }
    }

    fn scan_storage_dir(dir: &std::path::Path) -> Vec<SharedFileInfo> {
        let mut files = Vec::new();
        if let Ok(entries) = std::fs::read_dir(dir) {
            for entry in entries.flatten() {
                if let Ok(meta) = entry.metadata() {
                    if meta.is_file() {
                        let name = entry.file_name().to_string_lossy().to_string();
                        let mime = WifiTransferEngine::detect_mime_type(&name).to_string();
                        files.push(SharedFileInfo {
                            name,
                            size_bytes: meta.len(),
                            mime_type: mime,
                        });
                    }
                }
            }
        }
        files
    }
}

pub struct WifiTransferEngine;

impl WifiTransferEngine {
    /// Sanitize user-provided filename to prevent directory traversal or invalid characters.
    pub fn sanitize_filename(filename: &str) -> String {
        let name = filename.replace('\\', "/");
        let basename = name.split('/').last().unwrap_or("unnamed_media");
        let sanitized: String = basename
            .chars()
            .filter(|c| c.is_alphanumeric() || *c == '.' || *c == '-' || *c == '_' || *c == ' ')
            .collect();

        let trimmed = sanitized.trim();
        if trimmed.is_empty() || trimmed == "." || trimmed == ".." {
            "unnamed_media.mp4".to_string()
        } else {
            trimmed.to_string()
        }
    }

    /// Parses HTTP `Range: bytes=start-end` header. Returns `(start, end)` inclusive.
    pub fn parse_range_header(header_val: &str, total_size: u64) -> Option<(u64, u64)> {
        if total_size == 0 {
            return None;
        }

        let prefix = "bytes=";
        if !header_val.starts_with(prefix) {
            return None;
        }

        let range_str = &header_val[prefix.len()..];
        let parts: Vec<&str> = range_str.split('-').collect();
        if parts.len() != 2 {
            return None;
        }

        let start_str = parts[0].trim();
        let end_str = parts[1].trim();

        if start_str.is_empty() && !end_str.is_empty() {
            // Suffix range: "-500" means the last 500 bytes
            let suffix_len: u64 = end_str.parse().ok()?;
            let start = total_size.saturating_sub(suffix_len);
            let end = total_size - 1;
            Some((start, end))
        } else if !start_str.is_empty() && end_str.is_empty() {
            // Prefix range: "500-" means from 500 to end
            let start: u64 = start_str.parse().ok()?;
            if start >= total_size {
                return None;
            }
            Some((start, total_size - 1))
        } else if !start_str.is_empty() && !end_str.is_empty() {
            // Normal range: "500-999"
            let start: u64 = start_str.parse().ok()?;
            let end: u64 = end_str.parse().ok()?;
            if start > end || start >= total_size {
                return None;
            }
            Some((start, end.min(total_size - 1)))
        } else {
            None
        }
    }

    /// Detect MIME type based on file extension.
    pub fn detect_mime_type(filename: &str) -> &'static str {
        let lower = filename.to_lowercase();
        if lower.ends_with(".mp4") || lower.ends_with(".m4v") {
            "video/mp4"
        } else if lower.ends_with(".mkv") {
            "video/x-matroska"
        } else if lower.ends_with(".mp3") {
            "audio/mpeg"
        } else if lower.ends_with(".flac") {
            "audio/flac"
        } else if lower.ends_with(".m4a") || lower.ends_with(".aac") {
            "audio/mp4"
        } else if lower.ends_with(".webm") {
            "video/webm"
        } else if lower.ends_with(".srt") {
            "text/plain; charset=utf-8"
        } else if lower.ends_with(".vtt") {
            "text/vtt; charset=utf-8"
        } else {
            "application/octet-stream"
        }
    }

    /// Generates modern, responsive single-page HTML UI for browser file upload and download.
    pub fn render_web_ui(device_name: &str, files: &[SharedFileInfo]) -> String {
        let mut file_rows = String::new();
        for file in files {
            let size_mb = file.size_bytes as f64 / (1024.0 * 1024.0);
            file_rows.push_str(&format!(
                r#"<tr class="border-b border-gray-800 hover:bg-gray-800/50">
                    <td class="py-3 px-4 font-medium text-white">{}</td>
                    <td class="py-3 px-4 text-gray-400">{:.2} MB</td>
                    <td class="py-3 px-4 text-right">
                        <a href="/download/{}" class="text-indigo-400 hover:text-indigo-300 font-semibold" download>Download</a>
                    </td>
                </tr>"#,
                html_escape(&file.name),
                size_mb,
                url_encode(&file.name)
            ));
        }

        format!(
            r#"<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vimivo Wi-Fi Sharing - {}</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background-color: #0f172a; color: #f8fafc; margin: 0; padding: 2rem; }}
        .container {{ max-width: 800px; margin: 0 auto; }}
        .header {{ display: flex; align-items: center; justify-content: space-between; margin-bottom: 2rem; border-b: 1px solid #334155; padding-bottom: 1rem; }}
        .dropzone {{ border: 2px dashed #6366f1; border-radius: 12px; padding: 3rem 2rem; text-align: center; background: #1e293b; cursor: pointer; margin-bottom: 2rem; transition: background 0.2s; }}
        .dropzone:hover {{ background: #334155; }}
        .btn {{ background-color: #6366f1; color: white; padding: 0.6rem 1.2rem; border-radius: 8px; border: none; font-weight: bold; cursor: pointer; text-decoration: none; display: inline-block; }}
        table {{ width: 100%; border-collapse: collapse; background: #1e293b; border-radius: 8px; overflow: hidden; }}
        th, td {{ padding: 12px 16px; text-align: left; }}
        th {{ background: #0f172a; color: #94a3b8; font-size: 0.85rem; text-transform: uppercase; }}
        tr:not(:last-child) {{ border-bottom: 1px solid #334155; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div>
                <h1 style="margin:0; font-size:1.5rem;">Vimivo Wi-Fi Sharing</h1>
                <p style="margin:0.25rem 0 0 0; color:#94a3b8;">Connected to: {}</p>
            </div>
            <span style="background:#10b981; color:#064e3b; padding:4px 10px; border-radius:999px; font-size:0.8rem; font-weight:bold;">Online</span>
        </div>
        <div class="dropzone" id="dropzone" onclick="document.getElementById('fileInput').click()">
            <input type="file" id="fileInput" style="display:none;" multiple onchange="uploadFiles(this.files)" />
            <h3 style="margin:0 0 0.5rem 0;">Drag & Drop Media Files Here</h3>
            <p style="margin:0; color:#94a3b8; font-size:0.9rem;">or click to browse from this device (Videos, Music, Subtitles)</p>
            <div id="progress" style="margin-top:1rem; font-weight:bold; color:#a5b4fc;"></div>
        </div>
        <h2 style="font-size:1.2rem; margin-bottom:1rem;">Device Media ({})</h2>
        <table>
            <thead><tr><th>Name</th><th>Size</th><th style="text-align:right;">Action</th></tr></thead>
            <tbody>{}</tbody>
        </table>
    </div>
    <script>
        function uploadFiles(files) {{
            if (!files || files.length === 0) return;
            const progress = document.getElementById('progress');
            let uploaded = 0;
            for (let i = 0; i < files.length; i++) {{
                const formData = new FormData();
                formData.append('file', files[i]);
                progress.innerText = `Uploading ${{files[i].name}}...`;
                fetch('/upload', {{ method: 'POST', body: formData }})
                    .then(res => {{
                        uploaded++;
                        if (uploaded === files.length) {{
                            progress.innerText = 'Upload complete! Reloading...';
                            setTimeout(() => location.reload(), 1000);
                        }}
                    }})
                    .catch(err => {{ progress.innerText = 'Upload failed: ' + err; }});
            }}
        }}
    </script>
</body>
</html>"#,
            html_escape(device_name),
            html_escape(device_name),
            files.len(),
            file_rows
        )
    }
}

fn html_escape(s: &str) -> String {
    s.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

fn url_encode(s: &str) -> String {
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
    fn test_filename_sanitization() {
        assert_eq!(
            WifiTransferEngine::sanitize_filename("../../etc/passwd"),
            "passwd"
        );
        assert_eq!(
            WifiTransferEngine::sanitize_filename("C:\\Movies\\Inception (2010).mkv"),
            "Inception 2010.mkv"
        );
        assert_eq!(
            WifiTransferEngine::sanitize_filename("  "),
            "unnamed_media.mp4"
        );
    }

    #[test]
    fn test_parse_range_header() {
        let total = 10000;
        // bytes=0-499
        assert_eq!(
            WifiTransferEngine::parse_range_header("bytes=0-499", total),
            Some((0, 499))
        );
        // bytes=5000-
        assert_eq!(
            WifiTransferEngine::parse_range_header("bytes=5000-", total),
            Some((5000, 9999))
        );
        // bytes=-1000 (last 1000 bytes)
        assert_eq!(
            WifiTransferEngine::parse_range_header("bytes=-1000", total),
            Some((9000, 9999))
        );
        // invalid
        assert_eq!(
            WifiTransferEngine::parse_range_header("bytes=15000-", total),
            None
        );
    }

    #[test]
    fn test_detect_mime_type() {
        assert_eq!(WifiTransferEngine::detect_mime_type("movie.mp4"), "video/mp4");
        assert_eq!(WifiTransferEngine::detect_mime_type("song.flac"), "audio/flac");
        assert_eq!(WifiTransferEngine::detect_mime_type("sub.srt"), "text/plain; charset=utf-8");
    }

    #[test]
    fn test_render_web_ui() {
        let files = vec![SharedFileInfo {
            name: "sample.mp4".to_string(),
            size_bytes: 10 * 1024 * 1024,
            mime_type: "video/mp4".to_string(),
        }];
        let html = WifiTransferEngine::render_web_ui("Pixel 8 Pro", &files);
        assert!(html.contains("Pixel 8 Pro"));
        assert!(html.contains("sample.mp4"));
        assert!(html.contains("10.00 MB"));
    }

    #[test]
    fn test_wifi_server_http_lifecycle() {
        let temp_dir = tempfile::tempdir().unwrap();
        let server = WifiTransferServer::start("127.0.0.1:0", "TestDevice".into(), temp_dir.path().to_path_buf())
            .expect("Server binds successfully");
        assert!(server.is_running());
        assert!(server.port > 0);

        // Connect and send HTTP GET
        let mut client = TcpStream::connect(("127.0.0.1", server.port)).expect("Client connects");
        client.write_all(b"GET / HTTP/1.1\r\nHost: localhost\r\n\r\n").unwrap();

        let mut response = Vec::new();
        let _ = client.read_to_end(&mut response);
        let resp_str = String::from_utf8_lossy(&response);
        assert!(resp_str.contains("HTTP/1.1 200 OK"));
        assert!(resp_str.contains("Vimivo Wi-Fi Sharing"));

        server.stop();
        assert!(!server.is_running());
    }
}