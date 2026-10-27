import AVFoundation
import XCTest
@testable import iosApp

/// V1 #5, #6 and #16 on iOS with real media made by `TestMedia` and the real AVPlayer: frame stepping lands on exactly
/// one frame per step, the A–B region keeps playback inside it, and a snapshot is the picture that is on screen.
@MainActor
final class VideoToolsTests: XCTestCase {
    private func item(_ url: URL, kind: MediaKind, title: String = "Test clip") -> MediaItem {
        MediaItem(id: "tools-\(UUID().uuidString)", uri: url.absoluteString, title: title, kind: kind)
    }

    private func waitUntil(_ timeout: TimeInterval = 10, _ condition: () -> Bool) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        return condition()
    }

    /// Keeps a player layer on screen for the test, as the app's video view does: without a video output AVPlayer
    /// does not render individual frames, so stepping would not be representative.
    private var layers: [AVPlayerLayer] = []

    private func start(_ engine: AVPlayerPlaybackEngine, _ media: MediaItem) async {
        let layer = AVPlayerLayer(player: engine.player)
        layer.frame = CGRect(x: 0, y: 0, width: 320, height: 180)
        layers.append(layer)
        engine.play(item: media)
        let ready = await waitUntil { engine.player.currentItem?.status == .readyToPlay }
        XCTAssertTrue(ready, "the item did not become ready")
    }

    private func frameIndex(_ engine: AVPlayerPlaybackEngine) async throws -> Int? {
        TestMedia.blockIndex(in: try await engine.captureFrame())
    }

    func testEachStepMovesExactlyOneFrameForwardAndBack() async throws {
        let url = try await TestMedia.countingVideo(frames: 30)
        let engine = AVPlayerPlaybackEngine()
        await start(engine, item(url, kind: .video))
        engine.pause()
        engine.seek(toMillis: 500)
        let atFive = await waitUntil { abs(CMTimeGetSeconds(engine.player.currentTime()) - 0.5) < 0.001 }
        XCTAssertTrue(atFive)
        let start = try await frameIndex(engine)
        XCTAssertEqual(start, 5, "the first picture is frame 5")

        for expected in 6...8 {
            XCTAssertTrue(engine.stepFrame(forward: true))
            let moved = await waitUntil { abs(CMTimeGetSeconds(self.engine(engine, at: expected))) < 0.001 }
            XCTAssertTrue(moved, "the player did not reach frame \(expected), it is at \(CMTimeGetSeconds(engine.player.currentTime())) s")
            let shown = try await frameIndex(engine)
            XCTAssertEqual(shown, expected)
        }
        for expected in stride(from: 7, through: 4, by: -1) {
            XCTAssertTrue(engine.stepFrame(forward: false))
            let moved = await waitUntil { abs(CMTimeGetSeconds(self.engine(engine, at: expected))) < 0.001 }
            XCTAssertTrue(moved, "the player did not reach frame \(expected), it is at \(CMTimeGetSeconds(engine.player.currentTime())) s")
            let shown = try await frameIndex(engine)
            XCTAssertEqual(shown, expected)
        }
        XCTAssertNotEqual(engine.state.status, .playing, "stepping leaves the video paused")
    }

    /// The difference between the player's time and the time of `frame` (10 frames per second).
    private func engine(_ engine: AVPlayerPlaybackEngine, at frame: Int) -> CMTime {
        CMTimeSubtract(engine.player.currentTime(), CMTime(value: CMTimeValue(frame), timescale: 10))
    }

    func testSteppingAnAudioFileIsRefused() async throws {
        let engine = AVPlayerPlaybackEngine()
        await start(engine, item(try TestMedia.sineWav(seconds: 3), kind: .audio))
        XCTAssertFalse(engine.stepFrame(forward: true))
    }

    func testASnapshotIsThePictureOnScreenAndIsSavedAsAPng() async throws {
        let url = try await TestMedia.countingVideo(frames: 20, background: .red)
        let engine = AVPlayerPlaybackEngine()
        await start(engine, item(url, kind: .video, title: "Red / clip"))
        engine.pause()
        engine.seek(toMillis: 300)
        _ = await waitUntil { abs(CMTimeGetSeconds(engine.player.currentTime()) - 0.3) < 0.001 }

        let saved = try await engine.saveSnapshot()
        defer { try? FileManager.default.removeItem(at: saved) }

        XCTAssertEqual(saved.pathExtension, "png")
        XCTAssertTrue(saved.lastPathComponent.hasPrefix("Red clip-0h00m00s-"), saved.lastPathComponent)
        let image = try XCTUnwrap(UIImage(contentsOfFile: saved.path)?.cgImage)
        XCTAssertEqual(image.width, 320)
        let pixel = try XCTUnwrap(TestMedia.pixel(in: image, x: 0.9, y: 0.5))
        XCTAssertGreaterThan(pixel.r, 200)
        XCTAssertLessThan(pixel.g, 60)
        XCTAssertLessThan(pixel.b, 60)
        XCTAssertEqual(TestMedia.blockIndex(in: image), 3, "the snapshot is frame 3, at 0.3 s")
    }

    func testAudioHasNothingToSnapshot() async throws {
        let engine = AVPlayerPlaybackEngine()
        await start(engine, item(try TestMedia.sineWav(seconds: 3), kind: .audio))
        do {
            _ = try await engine.captureFrame()
            XCTFail("an audio file has no picture")
        } catch {
            XCTAssertTrue(error is AVPlayerPlaybackEngine.SnapshotError)
        }
    }

    private func sampleSeconds(_ engine: AVPlayerPlaybackEngine, for duration: TimeInterval) async -> [Double] {
        var samples: [Double] = []
        let end = Date().addingTimeInterval(duration)
        while Date() < end {
            samples.append(CMTimeGetSeconds(engine.player.currentTime()))
            try? await Task.sleep(nanoseconds: 30_000_000)
        }
        return samples
    }

    func testPlaybackStaysInsideTheABRegionAndWrapsToA() async throws {
        let engine = AVPlayerPlaybackEngine()
        await start(engine, item(try TestMedia.sineWav(seconds: 20), kind: .audio))
        engine.setABRepeat(2_000...4_000)
        XCTAssertEqual(engine.abRepeat, 2_000...4_000)
        engine.seek(toMillis: 2_000)
        _ = await waitUntil { CMTimeGetSeconds(engine.player.currentTime()) >= 1.9 }

        let samples = await sampleSeconds(engine, for: 7)
        XCTAssertLessThanOrEqual(samples.max() ?? 0, 4.4, "left the region")
        XCTAssertGreaterThanOrEqual(samples.min() ?? 0, 1.5)
        let wraps = zip(samples, samples.dropFirst()).filter { $1 < $0 - 0.5 }.count
        XCTAssertGreaterThanOrEqual(wraps, 2, "expected at least two wraps in 7 s")
    }

    func testClearingTheRegionLetsPlaybackRunPastB() async throws {
        let engine = AVPlayerPlaybackEngine()
        await start(engine, item(try TestMedia.sineWav(seconds: 20), kind: .audio))
        engine.setABRepeat(1_000...2_000)
        engine.seek(toMillis: 1_000)
        _ = await waitUntil { CMTimeGetSeconds(engine.player.currentTime()) >= 0.9 }
        _ = await sampleSeconds(engine, for: 2)
        engine.setABRepeat(nil)
        XCTAssertNil(engine.abRepeat)
        let samples = await sampleSeconds(engine, for: 4)
        XCTAssertGreaterThan(samples.max() ?? 0, 2.6, "control: playback should pass B")
    }

    func testANewItemStartsWithoutARegionAndAnEmptyRegionIsIgnored() async throws {
        let engine = AVPlayerPlaybackEngine()
        await start(engine, item(try TestMedia.sineWav(seconds: 5), kind: .audio))
        engine.setABRepeat(1_000...2_000)
        engine.setABRepeat(3_000...3_000)
        XCTAssertNil(engine.abRepeat)
        engine.setABRepeat(1_000...2_000)
        engine.play(item: item(try TestMedia.sineWav(seconds: 5), kind: .audio))
        XCTAssertNil(engine.abRepeat, "a region belongs to one item")
    }
}
