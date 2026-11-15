//! Batch File Renaming and Organization Templates.

use crate::model::MediaItem;

pub struct RenameTemplate;

impl RenameTemplate {
    pub const FORBIDDEN_CHARS: &'static [char] = &['<', '>', ':', '"', '/', '\\', '|', '?', '*'];

    pub fn format(template: &str, item: &MediaItem) -> String {
        let artist = item.artist.as_deref().unwrap_or("Unknown Artist");
        let album = item.album.as_deref().unwrap_or("Unknown Album");
        let album_artist = item.album_artist.as_deref().unwrap_or(artist);
        let title = &item.title;
        let track = item.track_number.map_or("00".to_string(), |t| format!("{:02}", t));
        let disc = item.disc_number.map_or("1".to_string(), |d| d.to_string());
        let year = item.release_year.map_or("".to_string(), |y| y.to_string());
        let genre = item.genre.as_deref().unwrap_or("");
        let original_name = item.file_name.as_deref().unwrap_or("");

        let mut res = template.to_string();
        res = res.replace("{artist}", artist);
        res = res.replace("{albumArtist}", album_artist);
        res = res.replace("{album}", album);
        res = res.replace("{title}", title);
        res = res.replace("{track}", &track);
        res = res.replace("{disc}", &disc);
        res = res.replace("{year}", &year);
        res = res.replace("{genre}", genre);
        res = res.replace("{originalName}", original_name);

        Self::sanitize(&res)
    }

    pub fn sanitize(name: &str) -> String {
        name.chars()
            .map(|c| if Self::FORBIDDEN_CHARS.contains(&c) { '_' } else { c })
            .collect::<String>()
            .trim()
            .to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::MediaKind;

    #[test]
    fn test_rename_template_formatting_and_sanitization() {
        let mut item = MediaItem::new("1", "file://1.mp3", "Song: Title / Test", MediaKind::Audio);
        item.artist = Some("Rock Band".into());
        item.album = Some("Album*Name".into());
        item.track_number = Some(3);
        item.release_year = Some(2025);

        let formatted = RenameTemplate::format("{track} - {artist} - {title} ({year})", &item);
        assert_eq!(formatted, "03 - Rock Band - Song_ Title _ Test (2025)");
    }
}
