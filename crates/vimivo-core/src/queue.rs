//! Pure Logic Playback Queue.

use crate::model::QueueRepeatMode;
use rand::seq::SliceRandom;
use rand::thread_rng;

#[derive(Debug, Clone)]
pub struct PlayQueue {
    items: Vec<String>,
    original_order: Option<Vec<String>>,
    current_index: usize,
    shuffled: bool,
    repeat: QueueRepeatMode,
}

impl Default for PlayQueue {
    fn default() -> Self {
        Self::new()
    }
}

impl PlayQueue {
    pub fn new() -> Self {
        Self {
            items: Vec::new(),
            original_order: None,
            current_index: 0,
            shuffled: false,
            repeat: QueueRepeatMode::Off,
        }
    }

    pub fn ids(&self) -> &[String] {
        &self.items
    }

    pub fn size(&self) -> usize {
        self.items.len()
    }

    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    pub fn current_index(&self) -> usize {
        self.current_index
    }

    pub fn current_id(&self) -> Option<&str> {
        self.items.get(self.current_index).map(|s| s.as_str())
    }

    pub fn is_shuffled(&self) -> bool {
        self.shuffled
    }

    pub fn repeat_mode(&self) -> QueueRepeatMode {
        self.repeat
    }

    pub fn set_repeat_mode(&mut self, mode: QueueRepeatMode) {
        self.repeat = mode;
    }

    pub fn replace(&mut self, ids: Vec<String>, start_index: usize) {
        self.items = ids;
        self.original_order = None;
        if self.items.is_empty() {
            self.current_index = 0;
        } else {
            self.current_index = start_index.min(self.items.len() - 1);
        }
        if self.shuffled {
            self.shuffle_behind_current();
        }
    }

    pub fn jump_to(&mut self, index: usize) -> bool {
        if index < self.items.len() {
            self.current_index = index;
            true
        } else {
            false
        }
    }

    pub fn jump_to_id(&mut self, id: &str) -> bool {
        if let Some(pos) = self.items.iter().position(|x| x == id) {
            self.current_index = pos;
            true
        } else {
            false
        }
    }

    pub fn append(&mut self, id: String) {
        if let Some(orig) = &mut self.original_order {
            orig.push(id.clone());
        }
        self.items.push(id);
    }

    pub fn insert_next(&mut self, id: String) {
        let insert_at = if self.items.is_empty() {
            0
        } else {
            (self.current_index + 1).min(self.items.len())
        };
        self.items.insert(insert_at, id.clone());
        if let Some(orig) = &mut self.original_order {
            orig.push(id);
        }
    }

    pub fn remove(&mut self, index: usize) -> bool {
        if index >= self.items.len() {
            return false;
        }
        let removed = self.items.remove(index);
        if let Some(orig) = &mut self.original_order {
            orig.retain(|id| id != &removed);
        }
        let was_current = index == self.current_index;
        if index < self.current_index {
            self.current_index = self.current_index.saturating_sub(1);
        } else if self.current_index >= self.items.len() {
            self.current_index = self.items.len().saturating_sub(1);
        }
        was_current
    }

    pub fn move_item(&mut self, from: usize, to: usize) {
        if from >= self.items.len() || to >= self.items.len() || from == to {
            return;
        }
        let current_id = self.current_id().map(|s| s.to_string());
        let item = self.items.remove(from);
        self.items.insert(to, item);
        if let Some(id) = current_id {
            self.current_index = self.items.iter().position(|x| x == &id).unwrap_or(0);
        }
    }

    pub fn set_shuffled(&mut self, shuffled: bool) {
        if self.shuffled == shuffled {
            return;
        }
        self.shuffled = shuffled;
        if shuffled {
            self.shuffle_behind_current();
        } else if let Some(orig) = self.original_order.take() {
            let current_id = self.current_id().map(|s| s.to_string());
            self.items = orig;
            if let Some(id) = current_id {
                self.current_index = self.items.iter().position(|x| x == &id).unwrap_or(0);
            }
        }
    }

    fn shuffle_behind_current(&mut self) {
        if self.items.len() <= 1 {
            return;
        }
        if self.original_order.is_none() {
            self.original_order = Some(self.items.clone());
        }
        let current_id = self.items[self.current_index].clone();
        let mut rest: Vec<String> = self
            .items
            .iter()
            .enumerate()
            .filter(|(i, _)| *i != self.current_index)
            .map(|(_, id)| id.clone())
            .collect();

        let mut rng = thread_rng();
        rest.shuffle(&mut rng);

        self.items.clear();
        self.items.push(current_id);
        self.items.extend(rest);
        self.current_index = 0;
    }

    /// Determines the next item ID to play based on current index and repeat mode.
    pub fn next(&mut self) -> Option<&str> {
        if self.items.is_empty() {
            return None;
        }

        match self.repeat {
            QueueRepeatMode::One => self.current_id(),
            QueueRepeatMode::All => {
                self.current_index = (self.current_index + 1) % self.items.len();
                self.current_id()
            }
            QueueRepeatMode::Off => {
                if self.current_index + 1 < self.items.len() {
                    self.current_index += 1;
                    self.current_id()
                } else {
                    None
                }
            }
        }
    }

    /// Determines the previous item ID.
    pub fn previous(&mut self) -> Option<&str> {
        if self.items.is_empty() {
            return None;
        }

        match self.repeat {
            QueueRepeatMode::One => self.current_id(),
            QueueRepeatMode::All => {
                if self.current_index == 0 {
                    self.current_index = self.items.len() - 1;
                } else {
                    self.current_index -= 1;
                }
                self.current_id()
            }
            QueueRepeatMode::Off => {
                if self.current_index > 0 {
                    self.current_index -= 1;
                    self.current_id()
                } else {
                    self.current_id()
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_queue_navigation_and_repeat() {
        let mut q = PlayQueue::new();
        q.replace(vec!["1".into(), "2".into(), "3".into()], 0);
        assert_eq!(q.current_id(), Some("1"));

        assert_eq!(q.next(), Some("2"));
        assert_eq!(q.next(), Some("3"));
        assert_eq!(q.next(), None); // Off mode stops at end

        q.set_repeat_mode(QueueRepeatMode::All);
        assert_eq!(q.next(), Some("1")); // Wraps to beginning
    }

    #[test]
    fn test_queue_insert_next_and_remove() {
        let mut q = PlayQueue::new();
        q.replace(vec!["A".into(), "C".into()], 0);
        q.insert_next("B".into());
        assert_eq!(q.ids(), &["A", "B", "C"]);

        q.jump_to(1); // On B
        assert!(q.remove(1)); // Remove current
        assert_eq!(q.current_id(), Some("C"));
    }
}
