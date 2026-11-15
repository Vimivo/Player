//! Rule-based Smart Playlists Evaluation Engine.

use crate::model::MediaItem;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum SmartRuleField {
    Title,
    Artist,
    Album,
    AlbumArtist,
    Genre,
    DurationMs,
    IsFavorite,
    ReleaseYear,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum SmartRuleOperator {
    Equals,
    Contains,
    StartsWith,
    GreaterThan,
    LessThan,
    IsTrue,
    IsFalse,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SmartRuleCondition {
    pub field: SmartRuleField,
    pub operator: SmartRuleOperator,
    pub value: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum SmartRuleMatchMode {
    All,
    Any,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SmartPlaylistDefinition {
    pub id: String,
    pub name: String,
    pub conditions: Vec<SmartRuleCondition>,
    pub match_mode: SmartRuleMatchMode,
    pub limit: Option<usize>,
}

pub struct SmartPlaylistEvaluator;

impl SmartPlaylistEvaluator {
    pub fn matches(item: &MediaItem, condition: &SmartRuleCondition) -> bool {
        match condition.field {
            SmartRuleField::Title => Self::eval_string(&item.title, condition.operator, &condition.value),
            SmartRuleField::Artist => Self::eval_opt_string(item.artist.as_deref(), condition.operator, &condition.value),
            SmartRuleField::Album => Self::eval_opt_string(item.album.as_deref(), condition.operator, &condition.value),
            SmartRuleField::AlbumArtist => Self::eval_opt_string(item.album_artist.as_deref(), condition.operator, &condition.value),
            SmartRuleField::Genre => Self::eval_opt_string(item.genre.as_deref(), condition.operator, &condition.value),
            SmartRuleField::DurationMs => Self::eval_opt_i64(item.duration_millis, condition.operator, &condition.value),
            SmartRuleField::IsFavorite => Self::eval_bool(item.is_favorite, condition.operator),
            SmartRuleField::ReleaseYear => Self::eval_opt_i32(item.release_year, condition.operator, &condition.value),
        }
    }

    pub fn evaluate(item: &MediaItem, playlist: &SmartPlaylistDefinition) -> bool {
        if playlist.conditions.is_empty() {
            return true;
        }

        match playlist.match_mode {
            SmartRuleMatchMode::All => playlist.conditions.iter().all(|c| Self::matches(item, c)),
            SmartRuleMatchMode::Any => playlist.conditions.iter().any(|c| Self::matches(item, c)),
        }
    }

    fn eval_string(val: &str, op: SmartRuleOperator, target: &str) -> bool {
        let v_lower = val.to_lowercase();
        let t_lower = target.to_lowercase();
        match op {
            SmartRuleOperator::Equals => v_lower == t_lower,
            SmartRuleOperator::Contains => v_lower.contains(&t_lower),
            SmartRuleOperator::StartsWith => v_lower.starts_with(&t_lower),
            _ => false,
        }
    }

    fn eval_opt_string(val: Option<&str>, op: SmartRuleOperator, target: &str) -> bool {
        val.map_or(false, |v| Self::eval_string(v, op, target))
    }

    fn eval_opt_i64(val: Option<i64>, op: SmartRuleOperator, target: &str) -> bool {
        let target_num = target.parse::<i64>().ok();
        match (val, target_num) {
            (Some(v), Some(t)) => match op {
                SmartRuleOperator::Equals => v == t,
                SmartRuleOperator::GreaterThan => v > t,
                SmartRuleOperator::LessThan => v < t,
                _ => false,
            },
            _ => false,
        }
    }

    fn eval_opt_i32(val: Option<i32>, op: SmartRuleOperator, target: &str) -> bool {
        let target_num = target.parse::<i32>().ok();
        match (val, target_num) {
            (Some(v), Some(t)) => match op {
                SmartRuleOperator::Equals => v == t,
                SmartRuleOperator::GreaterThan => v > t,
                SmartRuleOperator::LessThan => v < t,
                _ => false,
            },
            _ => false,
        }
    }

    fn eval_bool(val: bool, op: SmartRuleOperator) -> bool {
        match op {
            SmartRuleOperator::IsTrue => val,
            SmartRuleOperator::IsFalse => !val,
            _ => false,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::MediaKind;

    #[test]
    fn test_smart_playlist_evaluation() {
        let mut item = MediaItem::new("1", "file://1.mp3", "Solaris Horizon", MediaKind::Audio);
        item.artist = Some("Solaris Artist".into());
        item.release_year = Some(2024);
        item.duration_millis = Some(200_000);

        let playlist = SmartPlaylistDefinition {
            id: "sp-1".into(),
            name: "Recent Solaris".into(),
            conditions: vec![
                SmartRuleCondition {
                    field: SmartRuleField::Artist,
                    operator: SmartRuleOperator::Contains,
                    value: "solaris".into(),
                },
                SmartRuleCondition {
                    field: SmartRuleField::ReleaseYear,
                    operator: SmartRuleOperator::GreaterThan,
                    value: "2020".into(),
                },
            ],
            match_mode: SmartRuleMatchMode::All,
            limit: None,
        };

        assert!(SmartPlaylistEvaluator::evaluate(&item, &playlist));

        let mut item2 = item.clone();
        item2.release_year = Some(2018); // < 2020
        assert!(!SmartPlaylistEvaluator::evaluate(&item2, &playlist));
    }
}
