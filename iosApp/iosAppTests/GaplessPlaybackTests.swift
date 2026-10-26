import AVFoundation
import Combine
import XCTest
@testable import iosApp

/// V1 #11 on iOS with real audio: while a song plays the next one is queued behind it, and when the first ends the
/// player starts the second by itself. The engine follows without loading anything again, and no state between the
/// two songs says buffering, paused, idle or error.
@MainActor
final class GaplessPlaybackTests: XCTestCase {
    private var cancellables: Set<AnyCancellable> = []

    private func song(_ name: String, hz: Double, seconds: Double = 4, kind: MediaKind = .audio) throws -> MediaItem {
        let url = try TestMedia.sineWav(seconds: seconds, hz: hz)
        return MediaItem(id: "\(name)-\(UUID().uuidString)", uri: url.absoluteString, title: name, kind: kind)
    }

    private func waitUntil(_ timeout: TimeInterval = 10, _ condition: () -> Bool) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        return condition()
    }

    private func playing(_ items: [MediaItem], repeatAll: Bool = false) async -> AVPlayerPlaybackEngine {
        let engine = AVPlayerPlaybackEngine()
        if repeatAll { engine.cycleRepeatMode() }
        engine.playQueue(items: items, startIndex: 0)
        _ = await waitUntil { engine.player.currentItem?.status == .readyToPlay }
        return engine
    }

    func testTheNextSongIsQueuedAndTheEngineFollowsTheHandoverWithoutLoadingIt() async throws {
        let first = try song("one", hz: 440)
        let second = try song("two", hz: 880)
        let engine = await playing([first, second])
        let prepared = await waitUntil { engine.hasPreparedNext }
        XCTAssertTrue(prepared, "the second song should be queued behind the first")
        XCTAssertEqual(engine.player.items().count, 2)

        var statuses: [PlaybackStatus] = []
        engine.$state.sink { statuses.append($0.status) }.store(in: &cancellables)
        engine.seek(toMillis: 3_200)
        let advanced = await waitUntil(12) { engine.currentIndex == 1 }

        XCTAssertTrue(advanced, "the queue should move to the second song")
        XCTAssertEqual(engine.state.currentItem?.id, second.id)
        XCTAssertEqual(engine.itemLoads, 1, "the second song was started by the player, not loaded again")
        XCTAssertEqual(engine.state.status, .playing)
        XCTAssertTrue(statuses.allSatisfy { $0 == .playing }, "states in between: \(statuses)")
        XCTAssertFalse(engine.hasPreparedNext, "nothing follows the last song")
        XCTAssertEqual(engine.abRepeat, nil)
    }

    func testRepeatAllQueuesTheFirstSongBehindTheLast() async throws {
        let engine = await playing([try song("one", hz: 440), try song("two", hz: 880)], repeatAll: true)
        engine.playQueue(items: engine.queue, startIndex: 1)
        _ = await waitUntil { engine.player.currentItem?.status == .readyToPlay }
        let prepared = await waitUntil { engine.hasPreparedNext }
        XCTAssertTrue(prepared)
        XCTAssertEqual(engine.preparedNext?.index, 0)
    }

    func testNothingIsQueuedForRepeatOneAVideoOrASingleSong() async throws {
        let repeatOne = await playing([try song("a", hz: 440), try song("b", hz: 880)])
        repeatOne.cycleRepeatMode() // all
        repeatOne.cycleRepeatMode() // one
        XCTAssertEqual(repeatOne.state.repeatMode, .one)
        XCTAssertFalse(repeatOne.hasPreparedNext, "repeat one is handled by the engine")

        let beforeVideo = await playing([try song("a", hz: 440), try song("clip", hz: 880, kind: .video)])
        try await Task.sleep(nanoseconds: 500_000_000)
        XCTAssertFalse(beforeVideo.hasPreparedNext, "video is not handed over this way")

        let single = await playing([try song("only", hz: 440)])
        try await Task.sleep(nanoseconds: 500_000_000)
        XCTAssertFalse(single.hasPreparedNext)
    }

    func testChangingTheQueueReplacesTheQueuedSong() async throws {
        let one = try song("one", hz: 440)
        let two = try song("two", hz: 660)
        let three = try song("three", hz: 880)
        let engine = await playing([one, two, three])
        _ = await waitUntil { engine.hasPreparedNext }
        XCTAssertEqual(engine.preparedNext?.media.id, two.id)
        engine.queue = [one, three]
        XCTAssertEqual(engine.preparedNext?.media.id, three.id, "the song that follows changed")
        XCTAssertEqual(engine.player.items().count, 2, "the old one was taken out")
    }

    func testSkippingByHandStillWorksAndClearsTheQueuedSong() async throws {
        let engine = await playing([try song("one", hz: 440), try song("two", hz: 880), try song("three", hz: 660)])
        _ = await waitUntil { engine.hasPreparedNext }
        engine.playNext()
        XCTAssertEqual(engine.currentIndex, 1)
        XCTAssertEqual(engine.itemLoads, 2, "a manual skip loads the song")
        _ = await waitUntil { engine.player.currentItem?.status == .readyToPlay }
        let prepared = await waitUntil { engine.hasPreparedNext }
        XCTAssertTrue(prepared)
        XCTAssertEqual(engine.preparedNext?.index, 2)
    }
}
