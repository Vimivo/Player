//! A-B Loop Repeat Engine for Vimivo Player.
//!
//! Provides millisecond-accurate segment looping for musicians, language learners,
//! video editors, and repetition practice without relying on buggy backend wrappers.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
pub enum AbLoopState {
    #[default]
    None,
    PointASet(i64),
    Active {
        a: i64,
        b: i64,
    },
}

#[derive(Debug, Clone, Default)]
pub struct AbLoop {
    state: AbLoopState,
}

impl AbLoop {
    pub fn new() -> Self {
        Self {
            state: AbLoopState::None,
        }
    }

    pub fn state(&self) -> AbLoopState {
        self.state
    }

    /// Advances the loop state based on current playback position in milliseconds.
    /// Cycle: None -> PointASet -> Active(A, B) -> None.
    pub fn step(&mut self, current_pos_millis: i64) -> AbLoopState {
        let pos = current_pos_millis.max(0);
        self.state = match self.state {
            AbLoopState::None => AbLoopState::PointASet(pos),
            AbLoopState::PointASet(a) => {
                if pos > a + 200 {
                    // Minimum 200ms valid loop duration
                    AbLoopState::Active { a, b: pos }
                } else {
                    // If tapped earlier than A or too close, update A to new position
                    AbLoopState::PointASet(pos)
                }
            }
            AbLoopState::Active { .. } => AbLoopState::None,
        };
        self.state
    }

    /// Checks if playback has crossed Point B; if so, returns Some(a) to seek back to A.
    pub fn check_loop(&self, current_pos_millis: i64) -> Option<i64> {
        match self.state {
            AbLoopState::Active { a, b } => {
                if current_pos_millis >= b {
                    Some(a)
                } else {
                    None
                }
            }
            _ => None,
        }
    }

    pub fn clear(&mut self) {
        self.state = AbLoopState::None;
    }

    pub fn set_range(&mut self, a: i64, b: i64) -> bool {
        if b > a + 200 {
            self.state = AbLoopState::Active {
                a: a.max(0),
                b: b.max(0),
            };
            true
        } else {
            false
        }
    }

    pub fn a_point(&self) -> Option<i64> {
        match self.state {
            AbLoopState::PointASet(a) => Some(a),
            AbLoopState::Active { a, .. } => Some(a),
            AbLoopState::None => None,
        }
    }

    pub fn b_point(&self) -> Option<i64> {
        match self.state {
            AbLoopState::Active { b, .. } => Some(b),
            _ => None,
        }
    }

    pub fn is_active(&self) -> bool {
        matches!(self.state, AbLoopState::Active { .. })
    }

    pub fn button_label(&self) -> String {
        match self.state {
            AbLoopState::None => "A-B Loop".to_string(),
            AbLoopState::PointASet(a) => format!("[A→B] (A: {})", format_time(a)),
            AbLoopState::Active { a, b } => {
                format!("A⇄B ({} - {})", format_time(a), format_time(b))
            }
        }
    }
}

fn format_time(millis: i64) -> String {
    let total_sec = (millis / 1000).max(0);
    let m = total_sec / 60;
    let s = total_sec % 60;
    format!("{:02}:{:02}", m, s)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ab_loop_cycle() {
        let mut loop_ctrl = AbLoop::new();
        assert_eq!(loop_ctrl.state(), AbLoopState::None);
        assert_eq!(loop_ctrl.button_label(), "A-B Loop");

        // Step 1: Set Point A at 5,000 ms
        let state1 = loop_ctrl.step(5000);
        assert_eq!(state1, AbLoopState::PointASet(5000));
        assert_eq!(loop_ctrl.a_point(), Some(5000));
        assert_eq!(loop_ctrl.b_point(), None);

        // Step 2: Set Point B at 15,000 ms
        let state2 = loop_ctrl.step(15000);
        assert_eq!(state2, AbLoopState::Active { a: 5000, b: 15000 });
        assert_eq!(loop_ctrl.a_point(), Some(5000));
        assert_eq!(loop_ctrl.b_point(), Some(15000));
        assert!(loop_ctrl.is_active());

        // Step 3: Clear loop
        let state3 = loop_ctrl.step(16000);
        assert_eq!(state3, AbLoopState::None);
        assert!(!loop_ctrl.is_active());
    }

    #[test]
    fn test_ab_loop_check_trigger() {
        let mut loop_ctrl = AbLoop::new();
        loop_ctrl.set_range(10000, 20000);

        // Playing between A and B
        assert_eq!(loop_ctrl.check_loop(12000), None);
        assert_eq!(loop_ctrl.check_loop(19999), None);

        // Reaching or crossing B
        assert_eq!(loop_ctrl.check_loop(20000), Some(10000));
        assert_eq!(loop_ctrl.check_loop(20500), Some(10000));
    }

    #[test]
    fn test_ab_loop_b_before_a_resets_a() {
        let mut loop_ctrl = AbLoop::new();
        loop_ctrl.step(10000); // A = 10,000

        // Trying to set B at 8,000 (before A) resets A to 8,000
        let state = loop_ctrl.step(8000);
        assert_eq!(state, AbLoopState::PointASet(8000));
    }
}
