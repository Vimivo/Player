import AVFoundation
import XCTest
@testable import iosApp

/// V1 #4 on iOS with a real AVPlayer: each speed across the range is applied, so media time advances at that multiple
/// of wall-clock time, and the item corrects pitch (a time-domain algorithm that is valid for 0.25× to 4×).
@MainActor
final class PlaybackSpeedTests: XCTestCase {
    private func wait(_ seconds: TimeInterval) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func measuredSpeed(_ engine: AVPlayerPlaybackEngine) async -> Double {
        await wait(0.6)
        let startTime = CMTimeGetSeconds(engine.player.currentTime())
        let startClock = Date()
        await wait(2)
        let seconds = CMTimeGetSeconds(engine.player.currentTime()) - startTime
        return seconds / Date().timeIntervalSince(startClock)
    }

    func testMediaTimeAdvancesAtTheChosenSpeedAndPitchIsCorrected() async throws {
        let engine = AVPlayerPlaybackEngine()
        let url = try TestMedia.sineWav(seconds: 60)
        engine.play(item: MediaItem(id: "speed-\(UUID().uuidString)", uri: url.absoluteString, title: "Tone", kind: .audio))
        for _ in 0..<500 where engine.player.currentItem?.status != .readyToPlay { await wait(0.02) }
        XCTAssertEqual(engine.player.currentItem?.status, .readyToPlay)
        XCTAssertEqual(engine.player.currentItem?.audioTimePitchAlgorithm, .timeDomain)

        for speed: Float in [0.5, 1, 2, 3] {
            engine.setPlaybackRate(speed)
            XCTAssertEqual(engine.state.playbackRate, speed)
            XCTAssertEqual(engine.player.rate, speed, accuracy: 0.001)
            let measured = await measuredSpeed(engine)
            XCTAssertEqual(measured, Double(speed), accuracy: Double(speed) * 0.15, "speed \(speed)")
        }
    }

    func testTheSupportedRangeIsQuarterToFourTimes() {
        XCTAssertEqual(PlaybackPreferences.speedRange.lowerBound, 0.25)
        XCTAssertEqual(PlaybackPreferences.speedRange.upperBound, 4)
        XCTAssertEqual(PlaybackPreferences.speedPresets.first, 0.25)
        XCTAssertEqual(PlaybackPreferences.speedPresets.last, 4)
    }
}
