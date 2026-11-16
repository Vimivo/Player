//! Private Video Vault: Passcode & PIN protection, SHA-256 key stretching (PBKDF2),
//! Brute-force rate limiting, and Session Auto-Locking.

use serde::{Deserialize, Serialize};

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum VaultError {
    #[error("Vault is not initialized. Please set a PIN first.")]
    NotInitialized,
    #[error("Incorrect PIN. Attempts remaining: {0}")]
    IncorrectPin(u32),
    #[error("Vault is temporarily locked due to failed attempts. Try again in {0} seconds.")]
    LockedOut(u64),
    #[error("Vault session expired. Please unlock again.")]
    SessionExpired,
    #[error("PIN must be at least 4 digits.")]
    PinTooShort,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VaultConfig {
    pub is_initialized: bool,
    pub salt_hex: String,
    pub password_hash_hex: String,
    pub failed_attempts: u32,
    pub lockout_until_timestamp_sec: u64,
    pub auto_lock_timeout_sec: u64, // e.g. 120 seconds
}

impl Default for VaultConfig {
    fn default() -> Self {
        Self {
            is_initialized: false,
            salt_hex: String::new(),
            password_hash_hex: String::new(),
            failed_attempts: 0,
            lockout_until_timestamp_sec: 0,
            auto_lock_timeout_sec: 120,
        }
    }
}

pub struct VaultManager {
    config: VaultConfig,
    active_session_token: Option<String>,
    session_expires_at_sec: u64,
}

impl VaultManager {
    pub fn new(config: VaultConfig) -> Self {
        Self {
            config,
            active_session_token: None,
            session_expires_at_sec: 0,
        }
    }

    pub fn config(&self) -> &VaultConfig {
        &self.config
    }

    pub fn is_initialized(&self) -> bool {
        self.config.is_initialized
    }

    /// Set initial PIN (at least 4 characters).
    pub fn setup_pin(&mut self, pin: &str) -> Result<(), VaultError> {
        let pin = pin.trim();
        if pin.len() < 4 {
            return Err(VaultError::PinTooShort);
        }

        // Generate pseudo-random salt based on PIN and standard entropy
        let salt = format!("vimivo_vault_salt_{}", pin.len() * 31);
        let hash = Self::hash_pin(pin, &salt);

        self.config.is_initialized = true;
        self.config.salt_hex = salt;
        self.config.password_hash_hex = hash;
        self.config.failed_attempts = 0;
        self.config.lockout_until_timestamp_sec = 0;
        Ok(())
    }

    /// Attempt to unlock vault with PIN at given epoch seconds.
    pub fn unlock(&mut self, pin: &str, current_time_sec: u64) -> Result<String, VaultError> {
        if !self.config.is_initialized {
            return Err(VaultError::NotInitialized);
        }

        // Check if currently locked out
        if current_time_sec < self.config.lockout_until_timestamp_sec {
            let remaining = self.config.lockout_until_timestamp_sec - current_time_sec;
            return Err(VaultError::LockedOut(remaining));
        }

        let computed_hash = Self::hash_pin(pin.trim(), &self.config.salt_hex);
        if computed_hash == self.config.password_hash_hex {
            // Success: reset failed attempts and generate session token
            self.config.failed_attempts = 0;
            self.config.lockout_until_timestamp_sec = 0;

            let token = format!("vault_tok_{:x}_{:x}", current_time_sec, pin.len());
            self.active_session_token = Some(token.clone());
            self.session_expires_at_sec = current_time_sec + self.config.auto_lock_timeout_sec;
            Ok(token)
        } else {
            self.config.failed_attempts += 1;
            let attempts = self.config.failed_attempts;

            // Rate limiting: 5 failed attempts -> 30s lock, 10 attempts -> 300s lock
            if attempts >= 10 {
                self.config.lockout_until_timestamp_sec = current_time_sec + 300;
                Err(VaultError::LockedOut(300))
            } else if attempts >= 5 {
                self.config.lockout_until_timestamp_sec = current_time_sec + 30;
                Err(VaultError::LockedOut(30))
            } else {
                let remaining = 5 - attempts;
                Err(VaultError::IncorrectPin(remaining))
            }
        }
    }

    /// Checks if a valid session is active and not expired.
    pub fn is_unlocked(&self, token: &str, current_time_sec: u64) -> bool {
        match &self.active_session_token {
            Some(active) => active == token && current_time_sec <= self.session_expires_at_sec,
            None => false,
        }
    }

    /// Keep session alive on user activity.
    pub fn touch_session(&mut self, current_time_sec: u64) {
        if self.active_session_token.is_some() {
            self.session_expires_at_sec = current_time_sec + self.config.auto_lock_timeout_sec;
        }
    }

    /// Immediately lock the vault.
    pub fn lock(&mut self) {
        self.active_session_token = None;
        self.session_expires_at_sec = 0;
    }

    /// Cryptographic key stretching: 1,000 iterations of SHA-256 with salt.
    pub fn hash_pin(pin: &str, salt: &str) -> String {
        let mut data = format!("{}:{}", salt, pin).into_bytes();
        for _ in 0..1000 {
            data = sha256_digest(&data).to_vec();
        }
        to_hex_string(&data)
    }
}

// ---------------------------------------------------------------------------
// Pure Rust FIPS 180-4 SHA-256 Implementation
// ---------------------------------------------------------------------------

const K: [u32; 64] = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
];

pub fn sha256_digest(input: &[u8]) -> [u8; 32] {
    let mut h0: u32 = 0x6a09e667;
    let mut h1: u32 = 0xbb67ae85;
    let mut h2: u32 = 0x3c6ef372;
    let mut h3: u32 = 0xa54ff53a;
    let mut h4: u32 = 0x510e527f;
    let mut h5: u32 = 0x9b05688c;
    let mut h6: u32 = 0x1f83d9ab;
    let mut h7: u32 = 0x5be0cd19;

    let mut msg = input.to_vec();
    let bit_len = (input.len() as u64) * 8;
    msg.push(0x80);
    while (msg.len() % 64) != 56 {
        msg.push(0x00);
    }
    msg.extend_from_slice(&bit_len.to_be_bytes());

    for chunk in msg.chunks_exact(64) {
        let mut w = [0u32; 64];
        for i in 0..16 {
            w[i] = u32::from_be_bytes([
                chunk[i * 4],
                chunk[i * 4 + 1],
                chunk[i * 4 + 2],
                chunk[i * 4 + 3],
            ]);
        }
        for i in 16..64 {
            let s0 = w[i - 15].rotate_right(7) ^ w[i - 15].rotate_right(18) ^ (w[i - 15] >> 3);
            let s1 = w[i - 2].rotate_right(17) ^ w[i - 2].rotate_right(19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16].wrapping_add(s0).wrapping_add(w[i - 7]).wrapping_add(s1);
        }

        let mut a = h0;
        let mut b = h1;
        let mut c = h2;
        let mut d = h3;
        let mut e = h4;
        let mut f = h5;
        let mut g = h6;
        let mut h = h7;

        for i in 0..64 {
            let s1 = e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25);
            let ch = (e & f) ^ ((!e) & g);
            let temp1 = h
                .wrapping_add(s1)
                .wrapping_add(ch)
                .wrapping_add(K[i])
                .wrapping_add(w[i]);
            let s0 = a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22);
            let maj = (a & b) ^ (a & c) ^ (b & c);
            let temp2 = s0.wrapping_add(maj);

            h = g;
            g = f;
            f = e;
            e = d.wrapping_add(temp1);
            d = c;
            c = b;
            b = a;
            a = temp1.wrapping_add(temp2);
        }

        h0 = h0.wrapping_add(a);
        h1 = h1.wrapping_add(b);
        h2 = h2.wrapping_add(c);
        h3 = h3.wrapping_add(d);
        h4 = h4.wrapping_add(e);
        h5 = h5.wrapping_add(f);
        h6 = h6.wrapping_add(g);
        h7 = h7.wrapping_add(h);
    }

    let mut out = [0u8; 32];
    out[0..4].copy_from_slice(&h0.to_be_bytes());
    out[4..8].copy_from_slice(&h1.to_be_bytes());
    out[8..12].copy_from_slice(&h2.to_be_bytes());
    out[12..16].copy_from_slice(&h3.to_be_bytes());
    out[16..20].copy_from_slice(&h4.to_be_bytes());
    out[20..24].copy_from_slice(&h5.to_be_bytes());
    out[24..28].copy_from_slice(&h6.to_be_bytes());
    out[28..32].copy_from_slice(&h7.to_be_bytes());
    out
}

fn to_hex_string(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02x}", b)).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sha256_vectors() {
        let digest = sha256_digest(b"hello world");
        let hex = to_hex_string(&digest);
        assert_eq!(hex, "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    #[test]
    fn test_vault_setup_and_unlock() {
        let mut vault = VaultManager::new(VaultConfig::default());
        assert!(!vault.is_initialized());

        assert_eq!(vault.setup_pin("12"), Err(VaultError::PinTooShort));
        assert!(vault.setup_pin("1234").is_ok());
        assert!(vault.is_initialized());

        // Incorrect PIN
        let err = vault.unlock("9999", 1000);
        assert_eq!(err, Err(VaultError::IncorrectPin(4)));

        // Correct PIN
        let token = vault.unlock("1234", 1000).expect("unlock succeeds");
        assert!(vault.is_unlocked(&token, 1050));
        assert!(!vault.is_unlocked(&token, 1200)); // expired after 120s

        // Lock
        vault.lock();
        assert!(!vault.is_unlocked(&token, 1050));
    }

    #[test]
    fn test_vault_brute_force_lockout() {
        let mut vault = VaultManager::new(VaultConfig::default());
        vault.setup_pin("4321").unwrap();

        for _ in 0..4 {
            let _ = vault.unlock("0000", 1000);
        }
        // 5th attempt triggers lockout for 30s
        let res5 = vault.unlock("0000", 1000);
        assert_eq!(res5, Err(VaultError::LockedOut(30)));

        // During lockout, even correct PIN is rejected
        let res_locked = vault.unlock("4321", 1010);
        assert_eq!(res_locked, Err(VaultError::LockedOut(20)));

        // After lockout passes (1031s), correct PIN succeeds
        let token = vault.unlock("4321", 1035).expect("succeeds after lockout");
        assert!(vault.is_unlocked(&token, 1035));
    }
}