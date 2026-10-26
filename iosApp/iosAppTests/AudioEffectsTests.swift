import AVFoundation
import VimivoShared
import XCTest
@testable import iosApp

/// V1 #19, #21 and #24 on iOS with real audio: a 440 Hz tone is decoded with the effects tap in the audio mix (the same
/// mix the player uses) and the level that comes out is measured. Boost, an equalizer band and ReplayGain each change it
/// by the expected amount, a loud boost is limited, and the plain tone is the control.
@MainActor
final class AudioEffectsTests: XCTestCase {
    private struct Level {
        let rms: Double
        let peak: Double
    }

    /// One second of the tone, half a second in (so the filters have settled), decoded through the effects.
    private func render(
        _ url: URL, settings: AudioEffectSettings = AudioEffectSettings(), tags: ReplayGainTagValues = ReplayGainTagValues()
    ) async throws -> Level {
        let asset = AVURLAsset(url: url)
        let controller = AudioEffectsController()
        controller.update(settings: settings)
        controller.update(tags: tags)
        let tracks = try await asset.loadTracks(withMediaType: .audio)
        let track = try XCTUnwrap(tracks.first)
        let reader = try AVAssetReader(asset: asset)
        let output = AVAssetReaderAudioMixOutput(audioTracks: [track], audioSettings: [
            AVFormatIDKey: kAudioFormatLinearPCM, AVLinearPCMBitDepthKey: 32, AVLinearPCMIsFloatKey: true,
            AVLinearPCMIsNonInterleaved: false, AVLinearPCMIsBigEndianKey: false, AVNumberOfChannelsKey: 1,
            AVSampleRateKey: 44_100,
        ])
        if !settings.isNeutral { output.audioMix = await controller.mix(for: asset) }
        reader.add(output)
        XCTAssertTrue(reader.startReading())
        var samples: [Float] = []
        while let buffer = output.copyNextSampleBuffer() {
            guard let block = CMSampleBufferGetDataBuffer(buffer) else { continue }
            var length = 0
            var pointer: UnsafeMutablePointer<CChar>?
            CMBlockBufferGetDataPointer(block, atOffset: 0, lengthAtOffsetOut: nil, totalLengthOut: &length, dataPointerOut: &pointer)
            guard let pointer else { continue }
            pointer.withMemoryRebound(to: Float.self, capacity: length / 4) { samples += Array(UnsafeBufferPointer(start: $0, count: length / 4)) }
        }
        let window = Array(samples.dropFirst(22_050).prefix(44_100))
        XCTAssertGreaterThan(window.count, 40_000, "the tone was too short to measure")
        let rms = (window.map { Double($0 * $0) }.reduce(0, +) / Double(window.count)).squareRoot()
        return Level(rms: rms, peak: Double(window.map { abs($0) }.max() ?? 0))
    }

    private func tone(amplitude: Double = 8_000) throws -> URL {
        try TestMedia.sineWav(seconds: 3, hz: 440, amplitude: amplitude)
    }

    func testAPlainToneIsTheControl() async throws {
        let plain = try await render(try tone())
        XCTAssertEqual(plain.peak, 8_000.0 / 32_768, accuracy: 0.005)
    }

    func testABoostToTwiceTheVolumeDoublesTheLevel() async throws {
        let url = try tone()
        let plain = try await render(url)
        var boosted = AudioEffectSettings()
        boosted.boost = 2
        let louder = try await render(url, settings: boosted)
        XCTAssertEqual(louder.rms / plain.rms, 2.0, accuracy: 0.1)
    }

    func testALoudBoostIsLimitedUnderTheCeilingInsteadOfClipping() async throws {
        var boosted = AudioEffectSettings()
        boosted.boost = 2
        let level = try await render(try tone(amplitude: 30_000), settings: boosted)
        XCTAssertLessThanOrEqual(level.peak, 0.97 + 0.01)
        XCTAssertGreaterThan(level.peak, 0.9, "the limiter swallowed the signal")
    }

    func testAnEqualizerBandRaisesTheToneInItsBand() async throws {
        let url = try tone()
        let plain = try await render(url)
        var settings = AudioEffectSettings()
        settings.equalizerOn = true
        settings.preset = .custom
        settings.customGains = [0, 0, 0, 0, 12, 0, 0, 0, 0, 0] // the 500 Hz band; the tone is 440 Hz
        let raised = try await render(url, settings: settings)
        let ratio = raised.rms / plain.rms
        XCTAssertGreaterThan(ratio, 3.3)
        XCTAssertLessThan(ratio, 4.2)
    }

    func testAFlatEqualizerChangesNothing() async throws {
        let url = try tone()
        let plain = try await render(url)
        var settings = AudioEffectSettings()
        settings.equalizerOn = true
        let flat = try await render(url, settings: settings)
        XCTAssertEqual(flat.rms / plain.rms, 1.0, accuracy: 0.02)
    }

    func testReplayGainLowersATaggedFileAndLeavesAnUntaggedOneAlone() async throws {
        let url = try tone()
        let plain = try await render(url)
        var settings = AudioEffectSettings()
        settings.replayGain = ReplayGainMode.track
        let tagged = try await render(url, settings: settings, tags: ReplayGainTagValues(trackGainDb: -6))
        XCTAssertEqual(tagged.rms / plain.rms, 0.5, accuracy: 0.04)
        let untagged = try await render(url, settings: settings)
        XCTAssertEqual(untagged.rms / plain.rms, 1.0, accuracy: 0.02)
    }

    func testTheNeutralSettingsNeedNoTap() {
        XCTAssertTrue(AudioEffectSettings().isNeutral)
        var boosted = AudioEffectSettings()
        boosted.boost = 1.5
        XCTAssertFalse(boosted.isNeutral)
    }

    func testSettingsAreRememberedBetweenLaunches() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "effects-\(UUID().uuidString)"))
        XCTAssertTrue(AudioEffectSettings.load(from: defaults).isNeutral, "sound is untouched until the listener chooses")

        var chosen = AudioEffectSettings()
        chosen.equalizerOn = true
        chosen.preset = .custom
        chosen.customGains = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        chosen.bass = 0.5
        chosen.treble = -3
        chosen.preampDb = -6
        chosen.replayGain = ReplayGainMode.album
        chosen.boost = 1.5
        chosen.save(to: defaults)

        XCTAssertEqual(AudioEffectSettings.load(from: defaults), chosen)
    }

    func testTheEnginePutsTheEffectsOnTheItemOnlyOnceTheyAreOn() async throws {
        let engine = AVPlayerPlaybackEngine()
        engine.applyAudio(AudioEffectSettings())
        engine.play(item: MediaItem(
            id: "fx-\(UUID().uuidString)", uri: try TestMedia.sineWav(seconds: 5).absoluteString, title: "Tone", kind: .audio
        ))
        for _ in 0..<500 where engine.player.currentItem?.status != .readyToPlay { try await Task.sleep(nanoseconds: 20_000_000) }
        try await Task.sleep(nanoseconds: 300_000_000)
        XCTAssertNil(engine.player.currentItem?.audioMix, "untouched sound keeps the system's audio path")

        var boosted = AudioEffectSettings()
        boosted.boost = 2
        engine.applyAudio(boosted)
        for _ in 0..<250 where engine.player.currentItem?.audioMix == nil { try await Task.sleep(nanoseconds: 20_000_000) }
        XCTAssertNotNil(engine.player.currentItem?.audioMix)
        XCTAssertEqual(engine.audioSettings.boost, 2)
        engine.applyAudio(AudioEffectSettings()) // leave the shared defaults as they were
    }
}

