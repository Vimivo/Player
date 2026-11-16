//! Batch Operations & Media Badges: Multi-select operations (delete, add to playlist,
//! vault, favorites, sequence rename) and "NEW" badge & unplayed counter calculations.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FolderMediaSummary {
    pub folder_path: String,
    pub folder_name: String,
    pub total_count: usize,
    pub unplayed_count: usize,
    pub new_count: usize,
    pub total_size_bytes: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BatchRenameItem {
    pub id: String,
    pub old_name: String,
    pub new_name: String,
}

pub struct BatchEngine;

impl BatchEngine {
    /// Determines whether a media item should display the "NEW" badge.
    /// An item is "NEW" if it has not been played (last_played_ms == 0 or None)
    /// and was added to the device within `threshold_days` (default 7 days).
    pub fn is_new_item(added_at_sec: i64, last_played_ms: Option<i64>, current_time_sec: i64, threshold_days: u32) -> bool {
        let has_been_played = last_played_ms.map(|t| t > 0).unwrap_or(false);
        if has_been_played {
            return false;
        }

        let threshold_sec = (threshold_days as i64) * 86400;
        let age_sec = current_time_sec.saturating_sub(added_at_sec);
        age_sec <= threshold_sec
    }

    /// Generates sequential renamed names for a batch of media items.
    /// Pattern: e.g. "Breaking Bad S01E" -> "Breaking Bad S01E01.mkv", "Breaking Bad S01E02.mkv"
    pub fn generate_sequential_rename(
        items: &[(String, String)], // (id, original_filename)
        prefix: &str,
        start_index: u32,
        pad_digits: usize,
    ) -> Vec<BatchRenameItem> {
        let mut results = Vec::with_capacity(items.len());
        for (idx, (id, original_name)) in items.iter().enumerate() {
            let extension = original_name
                .rsplit_once('.')
                .map(|(_, ext)| format!(".{}", ext))
                .unwrap_or_default();

            let number = start_index + idx as u32;
            let formatted_num = format!("{:0width$}", number, width = pad_digits);
            let new_name = format!("{}{}{}", prefix, formatted_num, extension);

            results.push(BatchRenameItem {
                id: id.clone(),
                old_name: original_name.clone(),
                new_name,
            });
        }
        results
    }

    /// Computes summary statistics for a folder given its media items.
    pub fn compute_folder_summary(
        folder_path: &str,
        items: &[(u64, i64, Option<i64>)], // (size_bytes, added_at_sec, last_played_ms)
        current_time_sec: i64,
        new_threshold_days: u32,
    ) -> FolderMediaSummary {
        let folder_name = folder_path
            .trim_end_matches('/')
            .split('/')
            .last()
            .unwrap_or("Media")
            .to_string();

        let total_count = items.len();
        let mut unplayed_count = 0;
        let mut new_count = 0;
        let mut total_size_bytes = 0;

        for &(size, added_at, last_played) in items {
            total_size_bytes += size;
            let played = last_played.map(|t| t > 0).unwrap_or(false);
            if !played {
                unplayed_count += 1;
                if Self::is_new_item(added_at, last_played, current_time_sec, new_threshold_days) {
                    new_count += 1;
                }
            }
        }

        FolderMediaSummary {
            folder_path: folder_path.to_string(),
            folder_name,
            total_count,
            unplayed_count,
            new_count,
            total_size_bytes,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_is_new_item_logic() {
        let now = 1_700_000_000;
        let added_2_days_ago = now - (2 * 86400);
        let added_10_days_ago = now - (10 * 86400);

        // Added 2 days ago, unplayed -> NEW
        assert!(BatchEngine::is_new_item(added_2_days_ago, None, now, 7));
        assert!(BatchEngine::is_new_item(added_2_days_ago, Some(0), now, 7));

        // Added 2 days ago, but played -> NOT NEW
        assert!(!BatchEngine::is_new_item(added_2_days_ago, Some(15000), now, 7));

        // Added 10 days ago, unplayed -> NOT NEW (threshold is 7 days)
        assert!(!BatchEngine::is_new_item(added_10_days_ago, None, now, 7));
    }

    #[test]
    fn test_sequential_rename() {
        let items = vec![
            ("id_1".to_string(), "ep1_raw.mp4".to_string()),
            ("id_2".to_string(), "ep2_raw.mp4".to_string()),
        ];
        let renamed = BatchEngine::generate_sequential_rename(&items, "Attack on Titan S04E", 1, 2);
        assert_eq!(renamed.len(), 2);
        assert_eq!(renamed[0].new_name, "Attack on Titan S04E01.mp4");
        assert_eq!(renamed[1].new_name, "Attack on Titan S04E02.mp4");
    }

    #[test]
    fn test_folder_summary_computation() {
        let now = 1_700_000_000;
        let items = vec![
            (1000, now - 86400, None),          // unplayed, new (1MB)
            (2000, now - 86400, Some(50000)),   // played (2MB)
            (3000, now - 20 * 86400, None),     // unplayed, old (3MB)
        ];

        let summary = BatchEngine::compute_folder_summary("/sdcard/Movies/Anime", &items, now, 7);
        assert_eq!(summary.folder_name, "Anime");
        assert_eq!(summary.total_count, 3);
        assert_eq!(summary.unplayed_count, 2);
        assert_eq!(summary.new_count, 1);
        assert_eq!(summary.total_size_bytes, 6000);
    }
}