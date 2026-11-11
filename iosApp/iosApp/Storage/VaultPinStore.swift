import CommonCrypto
import Foundation
import Security

/// Where the vault PIN hash lives. The Keychain implementation is used in the app; tests use memory.
protocol VaultSecretStorage {
    func read() -> String?
    func write(_ value: String) -> Bool
    func clear()
}

struct KeychainVaultStorage: VaultSecretStorage {
    private let key = "private_vault_pin_v1"

    func read() -> String? { KeychainCredentialStore.shared.get(key: key) }
    func write(_ value: String) -> Bool { KeychainCredentialStore.shared.save(key: key, secret: value) }
    func clear() { KeychainCredentialStore.shared.delete(key: key) }
}

enum VaultUnlockResult: Equatable {
    case unlocked
    case wrongPin
    case locked(secondsRemaining: Int)
}

/// Private-vault PIN. The PIN itself is never stored: only `v1:<iterations>:<salt>:<hash>` from
/// PBKDF2-HMAC-SHA256 with a random salt, kept in the Keychain. Guessing is rate limited.
final class VaultPinStore {
    static let pinLength = 4
    private static let legacyDefaultsKey = "vimivo_private_vault_pin"

    /// The shared store. A PIN saved by an earlier version in plain `UserDefaults` is moved into the Keychain
    /// (hashed) and the plain copy is deleted.
    static let shared: VaultPinStore = {
        let store = VaultPinStore(storage: KeychainVaultStorage())
        let defaults = UserDefaults.standard
        if let legacy = defaults.string(forKey: legacyDefaultsKey) {
            if !store.hasPin, !legacy.isEmpty { _ = store.setPin(legacy) }
            defaults.removeObject(forKey: legacyDefaultsKey)
        }
        return store
    }()

    private let storage: VaultSecretStorage
    private let iterations: Int
    private let now: () -> Date
    private let maxAttempts: Int
    private let lockSeconds: TimeInterval
    private var failures = 0
    private var lockedUntil = Date.distantPast

    init(
        storage: VaultSecretStorage,
        iterations: Int = 120_000,
        maxAttempts: Int = 5,
        lockSeconds: TimeInterval = 30,
        now: @escaping () -> Date = Date.init
    ) {
        self.storage = storage
        self.iterations = iterations
        self.maxAttempts = maxAttempts
        self.lockSeconds = lockSeconds
        self.now = now
    }

    var hasPin: Bool { storage.read() != nil }

    func isValidPin(_ pin: String) -> Bool {
        pin.count == Self.pinLength && pin.allSatisfy { $0.isASCII && $0.isNumber }
    }

    /// Stores a new PIN. False if it is not exactly four digits or could not be saved.
    @discardableResult
    func setPin(_ pin: String) -> Bool {
        guard isValidPin(pin) else { return false }
        var salt = [UInt8](repeating: 0, count: 16)
        guard SecRandomCopyBytes(kSecRandomDefault, salt.count, &salt) == errSecSuccess,
              let hash = Self.derive(pin: pin, salt: salt, iterations: iterations) else { return false }
        let encoded = "v1:\(iterations):\(Data(salt).base64EncodedString()):\(Data(hash).base64EncodedString())"
        failures = 0
        lockedUntil = .distantPast
        return storage.write(encoded)
    }

    func verify(_ pin: String) -> VaultUnlockResult {
        let remaining = lockedUntil.timeIntervalSince(now())
        if remaining > 0 { return .locked(secondsRemaining: Int(remaining.rounded(.up))) }
        guard isValidPin(pin), let stored = storage.read(), Self.matches(pin: pin, stored: stored) else {
            failures += 1
            if failures >= maxAttempts {
                failures = 0
                lockedUntil = now().addingTimeInterval(lockSeconds)
            }
            return .wrongPin
        }
        failures = 0
        return .unlocked
    }

    func removePin() { storage.clear() }

    private static func matches(pin: String, stored: String) -> Bool {
        let parts = stored.components(separatedBy: ":")
        guard parts.count == 4, parts[0] == "v1",
              let rounds = Int(parts[1]), (1...2_000_000).contains(rounds),
              let salt = Data(base64Encoded: parts[2]),
              let expected = Data(base64Encoded: parts[3]),
              let actual = derive(pin: pin, salt: [UInt8](salt), iterations: rounds) else { return false }
        guard expected.count == actual.count else { return false }
        // Constant-time comparison.
        return zip(expected, actual).reduce(UInt8(0)) { $0 | ($1.0 ^ $1.1) } == 0
    }

    private static func derive(pin: String, salt: [UInt8], iterations: Int) -> [UInt8]? {
        let password = Array(pin.utf8)
        var output = [UInt8](repeating: 0, count: 32)
        let status = password.withUnsafeBufferPointer { pwd in
            CCKeyDerivationPBKDF(
                CCPBKDFAlgorithm(kCCPBKDF2),
                UnsafeRawPointer(pwd.baseAddress!).assumingMemoryBound(to: Int8.self), password.count,
                salt, salt.count,
                CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                UInt32(iterations),
                &output, output.count
            )
        }
        return status == kCCSuccess ? output : nil
    }
}
