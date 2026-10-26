import AVFoundation
import XCTest
@testable import iosApp

/// V1 #18 and #22 on iOS with a real file: two audio tracks (a 440 Hz tone in English and a 1000 Hz tone in Spanish)
/// and an English subtitle track. The player model lists them and selecting one enables that track in the player item
/// and no other.
@MainActor
final class TrackSwitchingTests: XCTestCase {
    private func clip() throws -> URL {
        try XCTUnwrap(Bundle(for: TrackSwitchingTests.self).url(forResource: "multitrack_tiny", withExtension: "mp4"))
    }

    private func playing() async throws -> (AVPlayerPlaybackEngine, VideoPlayerModel) {
        let engine = AVPlayerPlaybackEngine()
        engine.play(item: MediaItem(id: "tracks-\(UUID().uuidString)", uri: try clip().absoluteString, title: "Tracks", kind: .video))
        for _ in 0..<500 where engine.player.currentItem?.status != .readyToPlay { try await Task.sleep(nanoseconds: 20_000_000) }
        let model = VideoPlayerModel(engine: engine)
        await model.loadMediaOptions()
        return (engine, model)
    }

    /// The pitch of the audio being played, in Hz, from the zero crossings of what leaves the effects tap.
    private final class ToneMeter {
        private let lock = NSLock()
        private var crossings = 0
        private var samples = 0
        private var sampleRate = 44_100.0
        private var last: Float = 0

        func observe(_ buffer: [Float], rate: Double) {
            lock.lock(); defer { lock.unlock() }
            sampleRate = rate
            for value in buffer {
                if (last < 0) != (value < 0) { crossings += 1 }
                last = value
            }
            samples += buffer.count
        }

        func reset() { lock.lock(); crossings = 0; samples = 0; lock.unlock() }

        var hertz: Double {
            lock.lock(); defer { lock.unlock() }
            guard samples > 0 else { return 0 }
            return Double(crossings) / 2 / (Double(samples) / sampleRate)
        }

        var seconds: Double {
            lock.lock(); defer { lock.unlock() }
            return Double(samples) / sampleRate
        }
    }

    private func measuredTone(_ meter: ToneMeter, minimumSeconds: Double = 0.8) async -> Double {
        meter.reset()
        for _ in 0..<300 where meter.seconds < minimumSeconds { try? await Task.sleep(nanoseconds: 20_000_000) }
        return meter.hertz
    }

    func testBothAudioTracksAreListedWithTheirNames() async throws {
        let (_, model) = try await playing()
        XCTAssertEqual(model.audioOptions.count, 2)
        XCTAssertEqual(model.audioOptions.map(\.displayName), ["English", "Spanish"], "AVFoundation names MP4 tracks by language; it does not expose their titles")
        XCTAssertEqual(model.audioOptions.compactMap { $0.locale?.language.languageCode?.identifier }, ["en", "es"])
        XCTAssertEqual(model.selectedAudio?.displayName, "English", "the default track is the first")
    }

    func testSelectingTheSecondAudioTrackChangesTheTonePlayed() async throws {
        let (engine, model) = try await playing()
        let meter = ToneMeter()
        engine.audioEffects.tapObserver = { meter.observe($0, rate: $1) }
        var barelyChanged = AudioEffectSettings()
        barelyChanged.boost = 1.001 // keeps the effects tap on the audio so the output can be measured
        engine.applyAudio(barelyChanged)
        engine.seek(toMillis: 0)
        engine.resume()
        let first = await measuredTone(meter)
        // Zero crossings over a window that includes the encoder's start-up silence read a little low for 440 Hz.
        XCTAssertGreaterThan(first, 300, "the first track is the low tone: \(first) Hz")
        XCTAssertLessThan(first, 600, "the first track is the low tone: \(first) Hz")

        model.selectAudio(model.audioOptions[1])
        XCTAssertEqual(model.selectedAudio?.displayName, "Spanish")
        try await Task.sleep(nanoseconds: 800_000_000) // the switch applies and the old buffers drain
        let second = await measuredTone(meter)
        XCTAssertEqual(second, 1_000, accuracy: 60, "the second track is the 1000 Hz tone")

        model.selectAudio(model.audioOptions[0])
        try await Task.sleep(nanoseconds: 800_000_000)
        let back = await measuredTone(meter)
        XCTAssertLessThan(back, 600, "back on the low tone: \(back) Hz")
        XCTAssertGreaterThan(back, 300, "back on the low tone: \(back) Hz")
        engine.applyAudio(AudioEffectSettings())
    }

    func testTheSubtitleTrackIsListedAndShowsItsTextWhenChosen() async throws {
        let (engine, model) = try await playing()
        let named = model.subtitleOptions.map(\.displayName)
        XCTAssertTrue(named.contains("English"), "subtitle options: \(named)")
        engine.pause()
        model.startSubtitleRendering()
        model.selectSubtitle(model.subtitleOptions.first { $0.displayName == "English" })
        engine.seek(toMillis: 1_000)
        engine.resume()
        for _ in 0..<250 where model.subtitleText == nil { try await Task.sleep(nanoseconds: 20_000_000) }
        XCTAssertEqual(model.subtitleText, "Vimivo: Every Sound. Every Frame.")
        model.selectSubtitle(nil)
        XCTAssertNil(model.subtitleText)
    }
}
