import XCTest
@testable import iosApp

private final class MemoryVaultStorage: VaultSecretStorage {
    var value: String?
    func read() -> String? { value }
    func write(_ value: String) -> Bool { self.value = value; return true }
    func clear() { value = nil }
}

final class VaultPinStoreTests: XCTestCase {
    private var clock = Date(timeIntervalSince1970: 1_000)

    private func makeStore(_ storage: MemoryVaultStorage = MemoryVaultStorage()) -> VaultPinStore {
        VaultPinStore(storage: storage, iterations: 1_000, now: { self.clock })
    }

    func testStoredValueDoesNotContainThePin() {
        let storage = MemoryVaultStorage()
        let store = makeStore(storage)
        XCTAssertTrue(store.setPin("4821"))
        XCTAssertFalse(storage.value?.contains("4821") ?? true)
        XCTAssertTrue(storage.value?.hasPrefix("v1:1000:") ?? false)
    }

    func testOnlyTheRightPinUnlocks() {
        let store = makeStore()
        XCTAssertFalse(store.hasPin)
        XCTAssertTrue(store.setPin("4821"))
        XCTAssertTrue(store.hasPin)
        XCTAssertEqual(store.verify("4821"), .unlocked)
        XCTAssertEqual(store.verify("4822"), .wrongPin)
        XCTAssertEqual(store.verify(""), .wrongPin)
    }

    func testRejectsMalformedPins() {
        let store = makeStore()
        for bad in ["123", "12345", "12a4", "", "١٢٣٤"] { XCTAssertFalse(store.setPin(bad), bad) }
        XCTAssertFalse(store.hasPin)
    }

    func testEqualPinsGetDifferentSalts() {
        let a = MemoryVaultStorage(), b = MemoryVaultStorage()
        _ = makeStore(a).setPin("1234"); _ = makeStore(b).setPin("1234")
        XCTAssertNotEqual(a.value, b.value)
    }

    func testFiveWrongPinsLockForThirtySecondsThenRecover() {
        let store = makeStore()
        _ = store.setPin("1234")
        for _ in 0..<5 { XCTAssertEqual(store.verify("0000"), .wrongPin) }
        XCTAssertEqual(store.verify("1234"), .locked(secondsRemaining: 30))
        clock = clock.addingTimeInterval(12)
        XCTAssertEqual(store.verify("1234"), .locked(secondsRemaining: 18))
        clock = clock.addingTimeInterval(18)
        XCTAssertEqual(store.verify("1234"), .unlocked)
    }

    func testCorruptStoredValueNeverUnlocks() {
        let storage = MemoryVaultStorage()
        storage.value = "v1:abc:AAAA:AAAA"
        XCTAssertEqual(makeStore(storage).verify("1234"), .wrongPin)
    }
}
