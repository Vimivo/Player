import AVFoundation
import MediaToolbox
import CoreAudio
import VimivoCoreRust

public enum EqualizerPreset: String, CaseIterable, Identifiable {
    case flat, rock, pop, classical, jazz, vocal, bassBoost, trebleBoost, acoustic, electronic, podcast, custom

    public var id: String { rawValue }
    public var name: String { rawValue.uppercased() }

    public static var entries: [EqualizerPreset] { allCases }

    public var ffiPreset: FfiEqualizerPreset {
        switch self {
        case .flat: return .flat
        case .rock: return .rock
        case .pop: return .pop
        case .classical: return .classical
        case .jazz: return .jazz
        case .vocal: return .vocal
        case .bassBoost: return .bassBoost
        case .trebleBoost: return .trebleBoost
        case .acoustic: return .acoustic
        case .electronic: return .electronic
        case .podcast: return .podcast
        case .custom: return .custom
        }
    }
}

public enum ReplayGainMode: String, CaseIterable, Identifiable {
    case off, track, album

    public var id: String { rawValue }
    public var name: String { rawValue }

    public static var entries: [ReplayGainMode] { allCases }
}

/// The audio effects a listener can set: the 10-band equalizer with its presets, bass, treble and
/// preamp, a volume boost up to 200 % that is limited instead of clipping, and ReplayGain.
public struct AudioEffectSettings: Equatable {
    public var equalizerOn = false
    public var preset: EqualizerPreset = .flat
    /// The ten band gains in dB, used when `preset` is custom.
    public var customGains: [Float] = Array(repeating: 0, count: 10)
    public var bass: Float = 0
    public var treble: Float = 0
    public var preampDb: Float = 0
    public var replayGain: ReplayGainMode = .off
    /// 1 leaves the level alone, 2 is 200 %.
    public var boost: Float = 1
    public var monoDownmix = false
    public var bassBoost: Float = 0
    public var virtualizer: Float = 0
    public var reverbPreset = "OFF"

    public init() {}

    /// True when nothing would change the sound.
    public var isNeutral: Bool { !equalizerOn && replayGain == .off && boost == 1 && !monoDownmix }

    /// The linear gain and the ten band gains the shared chain should run with, for a file with these ReplayGain tags.
    func chain(tags: ReplayGainTagValues) -> (gain: Float, bandsDb: [Float]) {
        var bands = [Float](repeating: 0, count: 10)
        var preamp: Float = 1
        if equalizerOn {
            bands = resolveEqualizerGains(preset: preset.ffiPreset, customGains: customGains, bassStrength: bass, trebleGainDb: treble)
            preamp = pow(10.0, min(0, preampDb) / 20.0)
        }
        let ffiReplayMode: FfiReplayGainMode
        switch replayGain {
        case .off: ffiReplayMode = .off
        case .track: ffiReplayMode = .track
        case .album: ffiReplayMode = .album
        }
        let replay = calculateReplayGainModeLinear(
            mode: ffiReplayMode,
            trackGainDb: tags.trackGainDb,
            albumGainDb: tags.albumGainDb,
            trackPeak: tags.trackPeak,
            albumPeak: tags.albumPeak
        )
        return (boost * preamp * replay, bands)
    }
}

extension AudioEffectSettings {
    private enum Key {
        static let equalizerOn = "vimivo_eq_on"
        static let preset = "vimivo_eq_preset"
        static let customGains = "vimivo_eq_custom_gains"
        static let bass = "vimivo_eq_bass"
        static let treble = "vimivo_eq_treble"
        static let preamp = "vimivo_eq_preamp"
        static let replayGain = "vimivo_replay_gain"
        static let boost = "vimivo_volume_boost"
    }

    /// The settings the listener chose last time; sound is untouched until they choose something.
    public static func load(from defaults: UserDefaults = .standard) -> AudioEffectSettings {
        var settings = AudioEffectSettings()
        settings.equalizerOn = defaults.bool(forKey: Key.equalizerOn)
        settings.preset = EqualizerPreset.entries.first { $0.name == defaults.string(forKey: Key.preset) } ?? .flat
        if let gains = defaults.array(forKey: Key.customGains) as? [Double], gains.count == 10 {
            settings.customGains = gains.map(Float.init)
        }
        settings.bass = defaults.float(forKey: Key.bass)
        settings.treble = defaults.float(forKey: Key.treble)
        settings.preampDb = defaults.float(forKey: Key.preamp)
        settings.replayGain = ReplayGainMode.entries.first { $0.name == defaults.string(forKey: Key.replayGain) } ?? .off
        settings.boost = defaults.object(forKey: Key.boost) == nil ? 1 : min(2, max(1, defaults.float(forKey: Key.boost)))
        return settings
    }

    public func save(to defaults: UserDefaults = .standard) {
        defaults.set(equalizerOn, forKey: Key.equalizerOn)
        defaults.set(preset.name, forKey: Key.preset)
        defaults.set(customGains.map(Double.init), forKey: Key.customGains)
        defaults.set(bass, forKey: Key.bass)
        defaults.set(treble, forKey: Key.treble)
        defaults.set(preampDb, forKey: Key.preamp)
        defaults.set(replayGain.name, forKey: Key.replayGain)
        defaults.set(boost, forKey: Key.boost)
    }
}

/// ReplayGain values found in a file's tags (dB, and linear peaks).
public struct ReplayGainTagValues: Equatable {
    public var trackGainDb: Float?
    public var albumGainDb: Float?
    public var trackPeak: Float?
    public var albumPeak: Float?

    public init(trackGainDb: Float? = nil, albumGainDb: Float? = nil, trackPeak: Float? = nil, albumPeak: Float? = nil) {
        self.trackGainDb = trackGainDb
        self.albumGainDb = albumGainDb
        self.trackPeak = trackPeak
        self.albumPeak = albumPeak
    }

    /// Reads iTunes `----` atoms and ID3 `TXXX` frames, matching the tag names without regard to case.
    public static func read(from asset: AVAsset) async -> ReplayGainTagValues {
        var found: [String: String] = [:]
        guard let formats = try? await asset.load(.availableMetadataFormats) else { return ReplayGainTagValues() }
        for format in formats {
            guard let items = try? await asset.loadMetadata(for: format) else { continue }
            for item in items {
                let name = (item.key as? String) ?? item.identifier?.rawValue ?? ""
                let text = (try? await item.load(.stringValue)) ?? nil
                let described = (item.extraAttributes?[AVMetadataExtraAttributeKey(rawValue: "info")] as? String) ?? ""
                for candidate in [name, described] where candidate.uppercased().contains("REPLAYGAIN_") {
                    if let text { found[candidate.uppercased()] = text }
                }
            }
        }
        func value(_ tag: String) -> String? { found.first { $0.key.hasSuffix(tag) }?.value }
        return ReplayGainTagValues(
            trackGainDb: parseReplayGainDb(text: value("REPLAYGAIN_TRACK_GAIN")),
            albumGainDb: parseReplayGainDb(text: value("REPLAYGAIN_ALBUM_GAIN")),
            trackPeak: parseReplayGainPeak(text: value("REPLAYGAIN_TRACK_PEAK")),
            albumPeak: parseReplayGainPeak(text: value("REPLAYGAIN_ALBUM_PEAK"))
        )
    }
}

// MARK: - Native Swift DSP Chain (Biquad Equalizer & Limiter)

public struct GraphicEqualizerBands {
    public static let centresHz: [Int] = [31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000]
}

final class PeakLimiter {
    private let releaseCoefficient: Float
    private let channels: Int
    private let ceiling: Float
    private var gain: Float = 1.0

    init(sampleRate: Int, channels: Int, ceiling: Float = 0.97, releaseMillis: Float = 80.0) {
        self.channels = max(1, channels)
        self.ceiling = ceiling
        self.releaseCoefficient = exp(-1.0 / Float((Double(releaseMillis) / 1000.0) * Double(sampleRate)))
    }

    func process(samples: UnsafeMutablePointer<Float>, frames: Int) {
        for frame in 0..<frames {
            let start = frame * channels
            var peak: Float = 0
            for ch in 0..<channels { peak = max(peak, abs(samples[start + ch])) }
            let limit: Float = peak > ceiling ? ceiling / peak : 1.0
            gain = min(1.0 - (1.0 - gain) * releaseCoefficient, limit)
            if gain < 1.0 {
                for ch in 0..<channels { samples[start + ch] *= gain }
            }
        }
    }

    func reset() { gain = 1.0 }
}

final class GraphicEqualizer {
    private let sampleRate: Int
    private let channels: Int
    private let centresHz: [Int]
    private var gainsDb: [Float]
    private var sections: [Section] = []

    init(sampleRate: Int, channels: Int, centresHz: [Int] = GraphicEqualizerBands.centresHz) {
        self.sampleRate = max(1, sampleRate)
        self.channels = max(1, channels)
        self.centresHz = centresHz
        self.gainsDb = [Float](repeating: 0, count: centresHz.count)
    }

    var isFlat: Bool { gainsDb.allSatisfy { $0 == 0 } }

    func setGainsDb(_ gains: [Float]) {
        gainsDb = centresHz.indices.map { i in
            let g = i < gains.count ? gains[i] : 0
            return min(12.0, max(-12.0, g))
        }
        sections = centresHz.indices.compactMap { i -> Section? in
            guard gainsDb[i] != 0, centresHz[i] < sampleRate / 2 else { return nil }
            return Section(coefficients: coefficients(centreHz: centresHz[i], gainDb: gainsDb[i]), channels: channels)
        }
    }

    func process(samples: UnsafeMutablePointer<Float>, frames: Int) {
        guard !isFlat else { return }
        for section in sections { section.process(samples: samples, frames: frames) }
    }

    func reset() { for section in sections { section.reset() } }

    private func coefficients(centreHz: Int, gainDb: Float) -> (b0: Float, b1: Float, b2: Float, a1: Float, a2: Float) {
        let a = pow(10.0, Double(gainDb) / 40.0)
        let w0 = 2.0 * Double.pi * Double(centreHz) / Double(sampleRate)
        let alpha = sin(w0) / 2.0 // Q = 1.0
        let a0 = 1.0 + alpha / a
        return (
            Float((1.0 + alpha * a) / a0),
            Float((-2.0 * cos(w0)) / a0),
            Float((1.0 - alpha * a) / a0),
            Float((-2.0 * cos(w0)) / a0),
            Float((1.0 - alpha / a) / a0)
        )
    }

    private final class Section {
        let b0, b1, b2, a1, a2: Float
        let channels: Int
        var z1: [Float]
        var z2: [Float]

        init(coefficients c: (b0: Float, b1: Float, b2: Float, a1: Float, a2: Float), channels: Int) {
            self.b0 = c.b0; self.b1 = c.b1; self.b2 = c.b2; self.a1 = c.a1; self.a2 = c.a2
            self.channels = channels
            self.z1 = [Float](repeating: 0, count: channels)
            self.z2 = [Float](repeating: 0, count: channels)
        }

        func process(samples: UnsafeMutablePointer<Float>, frames: Int) {
            for frame in 0..<frames {
                for ch in 0..<channels {
                    let idx = frame * channels + ch
                    let x = samples[idx]
                    let y = b0 * x + z1[ch]
                    z1[ch] = b1 * x - a1 * y + z2[ch]
                    z2[ch] = b2 * x - a2 * y
                    samples[idx] = y
                }
            }
        }

        func reset() {
            z1 = [Float](repeating: 0, count: channels)
            z2 = [Float](repeating: 0, count: channels)
        }
    }
}

final class AudioDspChain {
    let equalizer: GraphicEqualizer
    private let limiter: PeakLimiter
    private var equalizerRaisesLevel = false
    var gain: Float = 1.0
    var monoDownmix = false
    private let channels: Int

    init(sampleRate: Int, channels: Int) {
        self.channels = max(1, channels)
        self.equalizer = GraphicEqualizer(sampleRate: sampleRate, channels: channels)
        self.limiter = PeakLimiter(sampleRate: sampleRate, channels: channels)
    }

    var isNeutral: Bool { gain == 1.0 && equalizer.isFlat && !monoDownmix }

    func setEqualizerGainsDb(_ gains: [Float]) {
        equalizer.setGainsDb(gains)
        equalizerRaisesLevel = gains.contains { $0 > 0 }
    }

    func process(samples: UnsafeMutablePointer<Float>, frames: Int) {
        guard !isNeutral, frames > 0 else { return }
        let count = frames * channels
        if gain != 1.0 {
            for i in 0..<count { samples[i] *= gain }
        }
        if monoDownmix && channels >= 2 {
            for f in 0..<frames {
                let start = f * channels
                var sum: Float = 0
                for ch in 0..<channels { sum += samples[start + ch] }
                let mono = sum / Float(channels)
                for ch in 0..<channels { samples[start + ch] = mono }
            }
        }
        equalizer.process(samples: samples, frames: frames)
        if gain > 1.0 || equalizerRaisesLevel {
            limiter.process(samples: samples, frames: frames)
        }
    }

    func reset() {
        equalizer.reset()
        limiter.reset()
    }
}

// MARK: - Tap & Controller

public final class AudioEffectsController {
    private let lock = NSLock()
    private var settings = AudioEffectSettings()
    private var tags = ReplayGainTagValues()
    private var version = 0

    public var tapObserver: (([Float], Double) -> Void)?
    public private(set) var tapEvents: [String] = []

    fileprivate func record(_ event: String) {
        lock.lock(); defer { lock.unlock() }
        if tapEvents.count < 16 { tapEvents.append(event) }
    }

    public init() {}

    public func update(settings: AudioEffectSettings) {
        lock.lock(); defer { lock.unlock() }
        self.settings = settings
        version += 1
    }

    public func update(tags: ReplayGainTagValues) {
        lock.lock(); defer { lock.unlock() }
        self.tags = tags
        version += 1
    }

    public var currentSettings: AudioEffectSettings {
        lock.lock(); defer { lock.unlock() }
        return settings
    }

    fileprivate func snapshot() -> (version: Int, settings: AudioEffectSettings, tags: ReplayGainTagValues) {
        lock.lock(); defer { lock.unlock() }
        return (version, settings, tags)
    }

    public func mix(for asset: AVAsset) async -> AVAudioMix? {
        guard let tracks = try? await asset.loadTracks(withMediaType: .audio), !tracks.isEmpty else { return nil }
        let mix = AVMutableAudioMix()
        mix.inputParameters = tracks.compactMap { track in
            let parameters = AVMutableAudioMixInputParameters(track: track)
            parameters.audioTapProcessor = makeTap()
            return parameters
        }
        return mix
    }

    private func makeTap() -> MTAudioProcessingTap? {
        var callbacks = MTAudioProcessingTapCallbacks(
            version: kMTAudioProcessingTapCallbacksVersion_0,
            clientInfo: Unmanaged.passRetained(self).toOpaque(),
            init: { _, clientInfo, storage in
                let controller = Unmanaged<AudioEffectsController>.fromOpaque(clientInfo!).takeUnretainedValue()
                storage.pointee = Unmanaged.passRetained(TapState(controller: controller)).toOpaque()
            },
            finalize: { tap in
                let storage = MTAudioProcessingTapGetStorage(tap)
                let state = Unmanaged<TapState>.fromOpaque(storage)
                state.release()
            },
            prepare: { tap, maxFrames, format in
                let state = Unmanaged<TapState>.fromOpaque(MTAudioProcessingTapGetStorage(tap)).takeUnretainedValue()
                state.prepare(maxFrames: Int(maxFrames), format: format.pointee)
            },
            unprepare: { tap in
                let state = Unmanaged<TapState>.fromOpaque(MTAudioProcessingTapGetStorage(tap)).takeUnretainedValue()
                state.unprepare()
            },
            process: { tap, frames, flags, buffers, framesOut, flagsOut in
                let state = Unmanaged<TapState>.fromOpaque(MTAudioProcessingTapGetStorage(tap)).takeUnretainedValue()
                let status = MTAudioProcessingTapGetSourceAudio(tap, frames, buffers, flagsOut, nil, framesOut)
                guard status == noErr else {
                    framesOut.pointee = 0
                    state.record("source audio status \(status) for \(frames) frames")
                    return
                }
                state.process(buffers, frames: Int(framesOut.pointee))
            }
        )
        var tap: MTAudioProcessingTap?
        let status = MTAudioProcessingTapCreate(kCFAllocatorDefault, &callbacks, kMTAudioProcessingTapCreationFlag_PostEffects, &tap)
        return status == noErr ? tap : nil
    }
}

private final class TapState {
    private let controller: AudioEffectsController
    private var chain: AudioDspChain?
    private var channels = 0
    private var interleaved = false
    private var appliedVersion = -1
    private var loggedFirstProcess = false
    private var sampleRate = 44_100.0

    init(controller: AudioEffectsController) {
        self.controller = controller
    }

    func record(_ event: String) { controller.record(event) }

    func prepare(maxFrames: Int, format: AudioStreamBasicDescription) {
        let isFloat = format.mFormatID == kAudioFormatLinearPCM
            && format.mFormatFlags & kAudioFormatFlagIsFloat != 0 && format.mBitsPerChannel == 32
        guard isFloat else {
            controller.record("passing through: format flags \(format.mFormatFlags), \(format.mBitsPerChannel) bits")
            return
        }
        channels = Int(format.mChannelsPerFrame)
        interleaved = format.mFormatFlags & kAudioFormatFlagIsNonInterleaved == 0
        sampleRate = format.mSampleRate
        chain = AudioDspChain(sampleRate: Int(format.mSampleRate), channels: channels)
        appliedVersion = -1
    }

    func unprepare() {
        chain = nil
    }

    func process(_ bufferList: UnsafeMutablePointer<AudioBufferList>, frames: Int) {
        guard let chain, frames > 0 else { return }
        if !loggedFirstProcess {
            loggedFirstProcess = true
            controller.record("tap processing: first buffer \(frames) frames")
        }
        let current = controller.snapshot()
        if current.version != appliedVersion {
            appliedVersion = current.version
            let configuration = current.settings.chain(tags: current.tags)
            chain.gain = configuration.gain
            chain.setEqualizerGainsDb(configuration.bandsDb)
        }
        if !chain.isNeutral { applyChain(chain, bufferList, frames) }
        if let observer = controller.tapObserver { observe(bufferList, frames, observer) }
    }

    private func observe(_ bufferList: UnsafeMutablePointer<AudioBufferList>, _ frames: Int, _ observer: ([Float], Double) -> Void) {
        let buffers = UnsafeMutableAudioBufferListPointer(bufferList)
        guard let data = buffers.first?.mData?.assumingMemoryBound(to: Float.self) else { return }
        let stride = interleaved ? channels : 1
        observer((0..<frames).map { data[$0 * stride] }, sampleRate)
    }

    private func applyChain(_ chain: AudioDspChain, _ bufferList: UnsafeMutablePointer<AudioBufferList>, _ frames: Int) {
        let buffers = UnsafeMutableAudioBufferListPointer(bufferList)
        if interleaved, let data = buffers.first?.mData?.assumingMemoryBound(to: Float.self) {
            chain.process(samples: data, frames: frames)
        } else if buffers.count == channels {
            // Non-interleaved planar: interleave into scratch, process, de-interleave
            var scratch = [Float](repeating: 0, count: frames * channels)
            for (channel, buffer) in buffers.enumerated() {
                guard let data = buffer.mData?.assumingMemoryBound(to: Float.self) else { return }
                for frame in 0..<frames { scratch[frame * channels + channel] = data[frame] }
            }
            scratch.withUnsafeMutableBufferPointer { ptr in
                chain.process(samples: ptr.baseAddress!, frames: frames)
            }
            for (channel, buffer) in buffers.enumerated() {
                guard let data = buffer.mData?.assumingMemoryBound(to: Float.self) else { return }
                for frame in 0..<frames { data[frame] = scratch[frame * channels + channel] }
            }
        }
    }
}

extension AVPlayerPlaybackEngine {
    public func applyAudio(_ settings: AudioEffectSettings) {
        audioSettings = settings
        settings.save()
        audioEffects.update(settings: settings)
        if let item = player.currentItem { attachAudioEffects(to: item) }
    }

    func attachAudioEffects(to item: AVPlayerItem) {
        audioEffects.update(tags: ReplayGainTagValues())
        guard !audioSettings.isNeutral else { return }
        let effects = audioEffects
        let wantsTags = audioSettings.replayGain != .off
        Task {
            if item.audioMix == nil { item.audioMix = await effects.mix(for: item.asset) }
            if wantsTags { effects.update(tags: await ReplayGainTagValues.read(from: item.asset)) }
        }
    }
}
