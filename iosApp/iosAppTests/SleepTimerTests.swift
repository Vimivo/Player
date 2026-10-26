import AVFoundation
import XCTest
@testable import iosApp

/// The sleep timer with real audio: the time running out fades the sound and pauses (volume put back), pressing pause
/// during the fade ends it cleanly, and "stop after this track" takes the queued next song out and stays on the track.
@MainActor
final class SleepTimerTests: XCTestCase {
    private func song(_ name: String, hz: Double, seconds: Double = 4) throws -> MediaItem {
        let url = try TestMedia.sineWav(seconds: seconds, hz: hz)
        return MediaItem(id: "\(name)-\(UUID().uuidString)", uri: url.absoluteString, title: name, kind: .audio)
    }

    private func waitUntil(_ timeout: TimeInterval = 10, _ condition: () -> Bool) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        return condition()
    }

    private func playing(_ items: [MediaItem]) async -> AVPlayerPlaybackEngine {
        let engine = AVPlayerPlaybackEngine()
        addTeardownBlock { @MainActor in engine.stop() }
        engine.playQueue(items: items, startIndex: 0)
        _ = await waitUntil { engine.player.currentItem?.status == .readyToPlay && engine.state.status == .playing }
        return engine
    }

    func testWhenTheTimeIsUpTheSoundFadesOutThenPlaybackPausesAndTheVolumeIsRestored() async throws {
        let engine = await playing([try song("one", hz: 440, seconds: 30)])
        var now: Int64 = 1_000_000
        let timer = SleepTimerModel(clock: { now })
        timer.attach(engine)
        timer.fadeSeconds = 2
        timer.start(minutes: 1)
        let counting = await waitUntil(3) { timer.remainingMillis != nil }
        XCTAssertTrue(counting, "the countdown starts")

        now += 61_000
        let faded = await waitUntil(5) { engine.player.volume < 0.95 && engine.state.status == .playing }
        XCTAssertTrue(faded, "volume goes down while still playing, was \(engine.player.volume)")
        let paused = await waitUntil(6) { engine.state.status == .paused }
        XCTAssertTrue(paused, "playback pauses when the fade ends")
        XCTAssertEqual(engine.player.volume, 1, accuracy: 0.001, "the volume is put back so the next play is audible")
        XCTAssertFalse(timer.isActive, "the timer is spent")
    }

    func testPausingDuringTheFadeEndsItAndRestoresTheVolume() async throws {
        let engine = await playing([try song("one", hz: 440, seconds: 30)])
        let timer = SleepTimerModel(clock: { 0 })
        timer.attach(engine)
        async let expiring: Void = timer.expire(fadeSeconds: 4)
        let started = await waitUntil(3) { engine.player.volume < 0.9 }
        XCTAssertTrue(started)
        engine.pause()
        await expiring
        XCTAssertEqual(engine.state.status, .paused)
        XCTAssertEqual(engine.player.volume, 1, accuracy: 0.001)
    }

    func testCancellingTheTimerLeavesPlaybackAlone() async throws {
        let engine = await playing([try song("one", hz: 440, seconds: 30)])
        let timer = SleepTimerModel(clock: { 0 })
        timer.attach(engine)
        timer.start(minutes: 15)
        _ = await waitUntil(3) { timer.remainingMillis != nil }
        timer.cancel()
        let cleared = await waitUntil(3) { !timer.isActive }
        XCTAssertTrue(cleared)
        XCTAssertEqual(engine.state.status, .playing)
    }

    func testStopAfterTheCurrentTrackTakesTheNextSongOutAndStaysOnTheTrack() async throws {
        let first = try song("one", hz: 440)
        let second = try song("two", hz: 880)
        let engine = await playing([first, second])
        let prepared = await waitUntil { engine.hasPreparedNext }
        XCTAssertTrue(prepared, "the second song is queued behind the first")

        engine.sleepTimer.stopAfterCurrentTrack()
        XCTAssertFalse(engine.hasPreparedNext, "nothing may be queued behind the track")
        engine.seek(toMillis: 3_300)
        let stopped = await waitUntil(12) { engine.state.status == .paused }
        XCTAssertTrue(stopped, "playback stops when the track ends")
        XCTAssertEqual(engine.state.currentItem?.id, first.id, "it did not move on to the next song")
        XCTAssertFalse(engine.sleepTimer.isActive, "the request is used up")
    }
}
