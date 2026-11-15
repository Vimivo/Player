//! Title, Lyrics, and Playlist Parsers.

use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

// ---------------------------------------------------------------------------
// 1. Title Parser
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CleanTitle {
    pub title: String,
    pub year: Option<i32>,
    pub season: Option<i32>,
    pub episode: Option<i32>,
}

impl CleanTitle {
    pub fn new(title: impl Into<String>) -> Self {
        Self {
            title: title.into(),
            year: None,
            season: None,
            episode: None,
        }
    }

    /// Formatted display: "The Movie (2019)", "Show · S01E02", or cleaned title.
    pub fn display(&self) -> String {
        if let (Some(s), Some(e)) = (self.season, self.episode) {
            format!("{} · S{:02}E{:02}", self.title, s, e)
        } else if let Some(y) = self.year {
            format!("{} ({})", self.title, y)
        } else {
            self.title.clone()
        }
    }
}

pub struct TitleParser;

impl TitleParser {
    pub fn clean(file_name: &str) -> CleanTitle {
        let name = file_name
            .rsplit('/')
            .next()
            .unwrap_or(file_name)
            .rsplit('\\')
            .next()
            .unwrap_or(file_name);

        let ext_re = Regex::new(r"\.[A-Za-z0-9]{2,4}$").unwrap();
        let name_without_ext = ext_re.replace(name, "").to_string();
        let raw = name_without_ext.clone();

        let noise_re = Regex::new(
            r"(?i)\b(2160p|1440p|1080p|1080i|720p|576p|480p|4k|uhd|hdr10?|dolby ?vision|bluray|blu-ray|bdrip|brrip|webrip|web-dl|webdl|hdrip|dvdrip|dvdscr|hdtv|remux|x264|x265|h ?264|h ?265|hevc|avc|xvid|divx|aac|ac3|eac3|dts|truehd|atmos|flac|10bit|8bit|proper|repack|extended|unrated|remastered|imax|multi|dual[ -]?audio|subbed|dubbed|yify|yts|rarbg|ettv|eztv)\b",
        ).unwrap();

        let episode_re = Regex::new(r"(?i)\bS(\d{1,2})[ ._-]?E(\d{1,3})\b").unwrap();
        let alt_episode_re = Regex::new(r"(?i)\b(\d{1,2})x(\d{2,3})\b").unwrap();
        let year_re = Regex::new(r"\b(19[2-9][0-9]|20[0-4][0-9])\b").unwrap();
        let brackets_re = Regex::new(r"[\[({][^\])}]*[\])}]").unwrap();
        let separators_re = Regex::new(r"[._]+").unwrap();
        let spaces_re = Regex::new(r"\s+").unwrap();

        // If handwritten with normal spaces and no release tags, keep as is
        let dotted = raw.contains('.') || raw.contains('_');
        if !dotted && !noise_re.is_match(&raw) && !episode_re.is_match(&raw) {
            return CleanTitle::new(raw.trim());
        }

        let mut working = raw.clone();
        let mut season = None;
        let mut episode = None;

        if let Some(caps) = episode_re.captures(&working) {
            season = caps.get(1).and_then(|m| m.as_str().parse().ok());
            episode = caps.get(2).and_then(|m| m.as_str().parse().ok());
        } else if let Some(caps) = alt_episode_re.captures(&working) {
            season = caps.get(1).and_then(|m| m.as_str().parse().ok());
            episode = caps.get(2).and_then(|m| m.as_str().parse().ok());
        }

        let ep_pos = episode_re
            .find(&working)
            .or_else(|| alt_episode_re.find(&working))
            .map(|m| m.start());

        working = brackets_re.replace_all(&working, " ").to_string();
        working = separators_re.replace_all(&working, " ").to_string();

        let cut_pos = match (noise_re.find(&working).map(|m| m.start()), ep_pos) {
            (Some(n), Some(e)) => Some(n.min(e)),
            (Some(n), None) => Some(n),
            (None, Some(e)) => Some(e),
            (None, None) => None,
        };

        let year_matches: Vec<_> = year_re.find_iter(&working).collect();
        let valid_year_match = year_matches
            .iter()
            .rev()
            .find(|m| cut_pos.map_or(true, |c| m.start() < c));

        let end_pos = match (cut_pos, valid_year_match) {
            (Some(c), Some(y)) => c.min(y.start()),
            (Some(c), None) => c,
            (None, Some(y)) => y.start(),
            (None, None) => working.len(),
        };

        let title_slice = if end_pos <= working.len() {
            &working[..end_pos]
        } else {
            &working
        };

        let cleaned_title = spaces_re
            .replace_all(title_slice, " ")
            .trim_matches(|c: char| c.is_whitespace() || c == '-' || c == '.')
            .to_string();

        let final_title = if cleaned_title.is_empty() {
            separators_re.replace_all(&raw, " ").trim().to_string()
        } else {
            cleaned_title
        };

        let year = if season.is_none() {
            valid_year_match.and_then(|m| m.as_str().parse().ok())
        } else {
            None
        };

        CleanTitle {
            title: final_title,
            year,
            season,
            episode,
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Lyrics Parser (LRC)
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct LyricLine {
    pub time_millis: Option<i64>,
    pub text: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct LyricsDocument {
    pub lines: Vec<LyricLine>,
    pub synchronized: bool,
    pub metadata: HashMap<String, String>,
    pub offset_millis: i64,
    pub source_uri: String,
}

impl LyricsDocument {
    /// Binary search to find the active lyric line index for given playback position
    pub fn find_active_line_index(&self, position_millis: i64) -> Option<usize> {
        if !self.synchronized || self.lines.is_empty() {
            return None;
        }

        let mut low = 0;
        let mut high = self.lines.len() as isize - 1;
        let mut candidate = None;

        while low <= high {
            let mid = ((low + high) / 2) as usize;
            if let Some(line_time) = self.lines[mid].time_millis {
                if line_time <= position_millis {
                    candidate = Some(mid);
                    low = mid as isize + 1;
                } else {
                    high = mid as isize - 1;
                }
            } else {
                high = mid as isize - 1;
            }
        }

        candidate
    }
}

pub struct LrcLyricsParser;

impl LrcLyricsParser {
    pub fn parse(text: &str, source_uri: &str) -> LyricsDocument {
        let timestamp_re = Regex::new(r"\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]").unwrap();
        let meta_re = Regex::new(r"(?i)^\[(ti|ar|al|by|offset|length|re|ve):(.*)\]$").unwrap();

        let mut metadata = HashMap::new();
        let mut offset_millis = 0i64;
        let mut parsed_lines = Vec::new();
        let mut is_synchronized = false;

        for line in text.lines() {
            let trimmed = line.trim();
            if trimmed.is_empty() {
                continue;
            }

            if let Some(caps) = meta_re.captures(trimmed) {
                let key = caps[1].to_lowercase();
                let val = caps[2].trim().to_string();
                if key == "offset" {
                    offset_millis = val.parse().unwrap_or(0);
                }
                metadata.insert(key, val);
                continue;
            }

            let mut timestamps = Vec::new();
            for caps in timestamp_re.captures_iter(trimmed) {
                let min: i64 = caps[1].parse().unwrap_or(0);
                let sec: i64 = caps[2].parse().unwrap_or(0);
                let frac: i64 = match caps.get(3) {
                    Some(m) => {
                        let s = m.as_str();
                        match s.len() {
                            1 => s.parse::<i64>().unwrap_or(0) * 100,
                            2 => s.parse::<i64>().unwrap_or(0) * 10,
                            _ => s.parse::<i64>().unwrap_or(0),
                        }
                    }
                    None => 0,
                };
                let ms = (min * 60 + sec) * 1000 + frac;
                timestamps.push(ms);
            }

            let pure_text = timestamp_re.replace_all(trimmed, "").trim().to_string();

            if timestamps.is_empty() {
                parsed_lines.push(LyricLine {
                    time_millis: None,
                    text: pure_text,
                });
            } else {
                is_synchronized = true;
                for ts in timestamps {
                    parsed_lines.push(LyricLine {
                        time_millis: Some(ts),
                        text: pure_text.clone(),
                    });
                }
            }
        }

        if is_synchronized {
            parsed_lines.sort_by_key(|l| l.time_millis.unwrap_or(0));
            if offset_millis != 0 {
                for line in &mut parsed_lines {
                    if let Some(ts) = line.time_millis {
                        line.time_millis = Some((ts + offset_millis).max(0));
                    }
                }
            }
        }

        LyricsDocument {
            lines: parsed_lines,
            synchronized: is_synchronized,
            metadata,
            offset_millis,
            source_uri: source_uri.to_string(),
        }
    }
}

// ---------------------------------------------------------------------------
// 3. M3U / M3U8 Playlist Parser
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct PlaylistItemEntry {
    pub uri: String,
    pub title: Option<String>,
    pub duration_seconds: Option<i32>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ParsedPlaylist {
    pub name: String,
    pub entries: Vec<PlaylistItemEntry>,
}

pub struct PlaylistM3uHandler;

impl PlaylistM3uHandler {
    pub fn parse(content: &str, default_name: &str) -> ParsedPlaylist {
        let extinf_re = Regex::new(r"^#EXTINF:(-?\d+)\s*,\s*(.*)$").unwrap();
        let mut entries = Vec::new();
        let mut pending_title = None;
        let mut pending_duration = None;
        let mut playlist_name = default_name.to_string();

        for line in content.lines() {
            let trimmed = line.trim();
            if trimmed.is_empty() {
                continue;
            }

            if trimmed.starts_with('#') {
                if trimmed.to_uppercase().starts_with("#EXTINF:") {
                    if let Some(caps) = extinf_re.captures(trimmed) {
                        pending_duration = caps[1].parse().ok();
                        let title = caps[2].trim().to_string();
                        if !title.is_empty() {
                            pending_title = Some(title);
                        }
                    }
                } else if trimmed.to_uppercase().starts_with("#PLAYLIST:") {
                    playlist_name = trimmed["#PLAYLIST:".len()..].trim().to_string();
                }
                continue;
            }

            entries.push(PlaylistItemEntry {
                uri: trimmed.to_string(),
                title: pending_title.take(),
                duration_seconds: pending_duration.take(),
            });
        }

        ParsedPlaylist {
            name: playlist_name,
            entries,
        }
    }
}

// ---------------------------------------------------------------------------
// 4. PLS Playlist Parser
// ---------------------------------------------------------------------------

pub struct PlaylistPlsHandler;

impl PlaylistPlsHandler {
    pub fn is_pls(file_name: &str) -> bool {
        file_name.to_lowercase().ends_with(".pls")
    }

    pub fn parse(content: &str, default_name: &str) -> ParsedPlaylist {
        let key_re = Regex::new(r"(?i)^(File|Title|Length)(\d+)$").unwrap();
        let mut files: HashMap<i32, String> = HashMap::new();
        let mut titles: HashMap<i32, String> = HashMap::new();
        let mut lengths: HashMap<i32, i32> = HashMap::new();

        for line in content.lines() {
            let trimmed = line.trim();
            if !trimmed.contains('=') {
                continue;
            }

            let mut parts = trimmed.splitn(2, '=');
            let key_str = parts.next().unwrap_or("").trim();
            let val_str = parts.next().unwrap_or("").trim();

            if let Some(caps) = key_re.captures(key_str) {
                if let Ok(idx) = caps[2].parse::<i32>() {
                    match caps[1].to_lowercase().as_str() {
                        "file" => {
                            if !val_str.is_empty() {
                                files.insert(idx, val_str.to_string());
                            }
                        }
                        "title" => {
                            if !val_str.is_empty() {
                                titles.insert(idx, val_str.to_string());
                            }
                        }
                        "length" => {
                            if let Ok(l) = val_str.parse::<i32>() {
                                if l >= 0 {
                                    lengths.insert(idx, l);
                                }
                            }
                        }
                        _ => {}
                    }
                }
            }
        }

        let mut sorted_keys: Vec<i32> = files.keys().cloned().collect();
        sorted_keys.sort();

        let entries = sorted_keys
            .into_iter()
            .map(|idx| PlaylistItemEntry {
                uri: files.remove(&idx).unwrap(),
                title: titles.remove(&idx),
                duration_seconds: lengths.remove(&idx),
            })
            .collect();

        ParsedPlaylist {
            name: default_name.to_string(),
            entries,
        }
    }
}

// ---------------------------------------------------------------------------
// 5. XSPF Playlist Parser
// ---------------------------------------------------------------------------

pub struct PlaylistXspfHandler;

impl PlaylistXspfHandler {
    pub fn is_xspf(file_name: &str) -> bool {
        file_name.to_lowercase().ends_with(".xspf")
    }

    pub fn parse(content: &str, default_name: &str) -> Result<ParsedPlaylist, String> {
        let lower = content.to_lowercase();
        if lower.contains("<!doctype") || lower.contains("<!entity") {
            return Err("XSPF playlists with DTD or entity declarations are rejected for security".into());
        }
        if !lower.contains("<playlist") {
            return Err("Not an XSPF playlist".into());
        }

        let track_list_re = Regex::new(r"(?is)<trackList\b[^>]*>(.*?)</trackList>").unwrap();
        let track_re = Regex::new(r"(?is)<track\b[^>]*>(.*?)</track>").unwrap();

        let track_list_content = track_list_re
            .captures(content)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str())
            .unwrap_or(content);

        let title_re = Regex::new(r"(?is)<title\b[^>]*>(.*?)</title>").unwrap();
        let loc_re = Regex::new(r"(?is)<location\b[^>]*>(.*?)</location>").unwrap();
        let dur_re = Regex::new(r"(?is)<duration\b[^>]*>(.*?)</duration>").unwrap();

        let playlist_name = title_re
            .captures(&content[..track_list_re.find(content).map(|m| m.start()).unwrap_or(content.len())])
            .and_then(|c| c.get(1))
            .map(|m| Self::unescape_xml(m.as_str().trim()))
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| default_name.to_string());

        let mut entries = Vec::new();
        for track_match in track_re.captures_iter(track_list_content) {
            let track_body = &track_match[1];
            if let Some(loc_cap) = loc_re.captures(track_body) {
                let uri = Self::unescape_xml(loc_cap[1].trim());
                if uri.is_empty() {
                    continue;
                }
                let title = title_re
                    .captures(track_body)
                    .map(|c| Self::unescape_xml(c[1].trim()))
                    .filter(|s| !s.is_empty());

                let duration_seconds = dur_re
                    .captures(track_body)
                    .and_then(|c| c[1].trim().parse::<i64>().ok())
                    .filter(|&ms| ms >= 0)
                    .map(|ms| (ms / 1000) as i32);

                entries.push(PlaylistItemEntry {
                    uri,
                    title,
                    duration_seconds,
                });
            }
        }

        Ok(ParsedPlaylist {
            name: playlist_name,
            entries,
        })
    }

    fn unescape_xml(text: &str) -> String {
        text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}

// ---------------------------------------------------------------------------
// 6. Unified PlaylistFormats Detector
// ---------------------------------------------------------------------------

pub struct PlaylistFormats;

impl PlaylistFormats {
    pub fn parse(file_name: &str, content: &str) -> Option<ParsedPlaylist> {
        let ext = file_name.rsplit('.').next().unwrap_or("").to_lowercase();
        let default_name = file_name.rsplit('/').next().unwrap_or(file_name);
        let default_name = default_name.rsplit('\\').next().unwrap_or(default_name);
        let name_without_ext = default_name.rsplit_once('.').map(|(n, _)| n).unwrap_or(default_name);

        match ext.as_str() {
            "m3u" | "m3u8" => Some(PlaylistM3uHandler::parse(content, name_without_ext)),
            "pls" => Some(PlaylistPlsHandler::parse(content, name_without_ext)),
            "xspf" => PlaylistXspfHandler::parse(content, name_without_ext).ok(),
            _ => None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_pls_parsing() {
        let pls = "[playlist]\nFile1=http://stream.org:8000/live\nTitle1=Ambient Live\nLength1=300\nNumberOfEntries=1\n";
        let parsed = PlaylistPlsHandler::parse(pls, "Radio");
        assert_eq!(parsed.entries.len(), 1);
        assert_eq!(parsed.entries[0].uri, "http://stream.org:8000/live");
        assert_eq!(parsed.entries[0].title.as_deref(), Some("Ambient Live"));
        assert_eq!(parsed.entries[0].duration_seconds, Some(300));
    }

    #[test]
    fn test_xspf_parsing() {
        let xspf = r#"<?xml version="1.0" encoding="UTF-8"?>
<playlist version="1" xmlns="http://xspf.org/ns/0/">
  <title>Chill Beats</title>
  <trackList>
    <track>
      <location>file:///music/song.mp3</location>
      <title>Evening Chill</title>
      <duration>180000</duration>
    </track>
  </trackList>
</playlist>"#;
        let parsed = PlaylistXspfHandler::parse(xspf, "Default").unwrap();
        assert_eq!(parsed.name, "Chill Beats");
        assert_eq!(parsed.entries.len(), 1);
        assert_eq!(parsed.entries[0].uri, "file:///music/song.mp3");
        assert_eq!(parsed.entries[0].title.as_deref(), Some("Evening Chill"));
        assert_eq!(parsed.entries[0].duration_seconds, Some(180));
    }

    #[test]
    fn test_title_parser_release_tag_cleaning() {
        let res = TitleParser::clean("The.Great.Film.2019.1080p.BluRay.x264-GRP.mkv");
        assert_eq!(res.title, "The Great Film");
        assert_eq!(res.year, Some(2019));
        assert_eq!(res.display(), "The Great Film (2019)");

        let tv = TitleParser::clean("Cosmos.S01E03.720p.HDTV.mkv");
        assert_eq!(tv.title, "Cosmos");
        assert_eq!(tv.season, Some(1));
        assert_eq!(tv.episode, Some(3));
        assert_eq!(tv.display(), "Cosmos · S01E03");
    }

    #[test]
    fn test_lrc_lyrics_parser() {
        let lrc = "[ti:Test Song]\n[00:10.50]Line one\n[00:20.00]Line two\n";
        let doc = LrcLyricsParser::parse(lrc, "test.lrc");
        assert!(doc.synchronized);
        assert_eq!(doc.lines.len(), 2);
        assert_eq!(doc.find_active_line_index(5000), None);
        assert_eq!(doc.find_active_line_index(12000), Some(0));
        assert_eq!(doc.find_active_line_index(22000), Some(1));
    }
}
