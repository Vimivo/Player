import AVFoundation
import MediaToolbox
import VimivoShared

/// The audio effects a listener can set (V1 #19, #21, #24): the 10-band equalizer with its presets, bass, treble and
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

    public init() {}

    /// True when nothing would change the sound.
    public var isNeutral: Bool { !equalizerOn && replayGain == .off && boost == 1 }

    /// The linear gain and the ten band gains the shared chain should run with, for a file with these ReplayGain tags.
    func chain(tags: ReplayGainTagValues) -> (gain: Float, bandsDb: [Float]) {
        var bands = [Float](repeating: 0, count: 10)
        var preamp: Float = 1
        if equalizerOn {
            let centres = GraphicEqualizerBands.shared.CENTRES_HZ
            let shared = AudioEnhancementSettings(
                enabled: true, preset: preset,
                bands: zip(centres, customGains).map { EqualizerBand(centerFrequencyHz: $0.0.int32Value, gainDb: $0.1) },
                bassStrength: bass, trebleGainDb: treble, preampDb: min(0, preampDb), replayGainMode: replayGain,
                balance: 0, volumeNormalization: false, customPresetName: nil, customPresets: []
            )
            bands = EqualizerPresets.shared.resolved(settings: shared).map(\.floatValue)
            preamp = AudioDspChain.companion.linear(gainDb: min(0, preampDb))
        }
        let replay = ReplayGain.shared.linearGain(
            mode: replayGain,
            trackGainDb: tags.trackGainDb.map { KotlinFloat(float: $0) },
            albumGainDb: tags.albumGainDb.map { KotlinFloat(float: $0) },
            trackPeak: tags.trackPeak.map { KotlinFloat(float: $0) },
            albumPeak: tags.albumPeak.map { KotlinFloat(float: $0) }
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
        func gain(_ text: String?) -> Float? { ReplayGain.shared.parseGainDb(text: text)?.floatValue }
        func peak(_ text: String?) -> Float? { ReplayGain.shared.parsePeak(text: text)?.floatValue }
        return ReplayGainTagValues(
            trackGainDb: gain(value("REPLAYGAIN_TRACK_GAIN")), albumGainDb: gain(value("REPLAYGAIN_ALBUM_GAIN")),
            trackPeak: peak(value("REPLAYGAIN_TRACK_PEAK")), albumPeak: peak(value("REPLAYGAIN_ALBUM_PEAK"))
        )
    }
}

/// Runs the shared DSP chain on the audio of an `AVPlayerItem` through an audio-processing tap. Settings can change
/// while playing; the tap picks up the new ones at the start of its next buffer.
///
/// The chain is the same Kotlin code the other apps run (`AudioDspChain`); samples are copied into one preallocated
/// array per buffer. AVFoundation runs the tap on its own audio-processing thread, with buffers of a few thousand
/// frames, not on the real-time output thread.
public final class AudioEffectsController {
    private let lock = NSLock()
    private var settings = AudioEffectSettings()
    private var tags = ReplayGainTagValues()
    private var version = 0

    /// Called with the first channel of every buffer after the effects, so tests can measure what is really played.
    /// Nil in the app.
    public var tapObserver: (([Float], Double) -> Void)?

    /// The first few unusual things a tap met (source audio that could not be fetched, a format it passes through),
    /// so a silent or untouched render can be explained instead of guessed at.
    public private(set) var tapEvents: [String] = []

    fileprivate func record(_ event: String) {
        lock.lock(); defer { lock.unlock() }
        if tapEvents.count < 16 { tapEvents.append(event) }
    }

    public init() {
        _ = Self.kotlinWarmedUp
    }

    /// Touches everything the tap will use on the Kotlin side (the chain, its equalizer, the presets) once, from the
    /// thread that creates the controller. Otherwise the first use of that code, with its one-time runtime
    /// initialisation, happens on AVFoundation's audio-processing thread in the middle of playback.
    private static let kotlinWarmedUp: Void = {
        let chain = AudioDspChain(sampleRate: 44_100, channels: 1)
        let scratch = KotlinFloatArray(size: 64)
        chain.gain = 1.5
        chain.setEqualizerGainsDb(gains: Array(repeating: KotlinFloat(float: 0), count: 10))
        chain.process(samples: scratch, frames: 64)
        _ = AudioEffectSettings().chain(tags: ReplayGainTagValues())
    }()

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

    /// An audio mix that sends every audio track of [asset] through the effects, or nil if it has none.
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
                    // Nothing was delivered: say so (zero frames) rather than leave the count unset.
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

/// What one tap keeps between buffers: the stream format, the chain and the scratch array.
private final class TapState {
    private let controller: AudioEffectsController
    private var chain: AudioDspChain?
    private var scratch: KotlinFloatArray?
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
        guard isFloat else { // anything else passes through untouched
            controller.record("passing through: format flags \(format.mFormatFlags), \(format.mBitsPerChannel) bits")
            return
        }
        channels = Int(format.mChannelsPerFrame)
        interleaved = format.mFormatFlags & kAudioFormatFlagIsNonInterleaved == 0
        sampleRate = format.mSampleRate
        controller.record("tap prepared: \(maxFrames) frames, \(format.mChannelsPerFrame) channels, \(Int(format.mSampleRate)) Hz")
        chain = AudioDspChain(sampleRate: Int32(format.mSampleRate), channels: Int32(channels))
        scratch = KotlinFloatArray(size: Int32(maxFrames * channels))
        appliedVersion = -1
    }

    func unprepare() {
        chain = nil
        scratch = nil
    }

    func process(_ bufferList: UnsafeMutablePointer<AudioBufferList>, frames: Int) {
        guard let chain, let scratch, frames > 0 else { return }
        if !loggedFirstProcess {
            loggedFirstProcess = true
            controller.record("tap processing: first buffer \(frames) frames")
        }
        let current = controller.snapshot()
        if current.version != appliedVersion {
            appliedVersion = current.version
            let configuration = current.settings.chain(tags: current.tags)
            chain.gain = configuration.gain
            chain.setEqualizerGainsDb(gains: configuration.bandsDb.map { KotlinFloat(float: $0) })
        }
        if !chain.isNeutral { applyChain(chain, scratch, bufferList, frames) }
        if let observer = controller.tapObserver { observe(bufferList, frames, observer) }
    }

    private func observe(_ bufferList: UnsafeMutablePointer<AudioBufferList>, _ frames: Int, _ observer: ([Float], Double) -> Void) {
        let buffers = UnsafeMutableAudioBufferListPointer(bufferList)
        guard let data = buffers.first?.mData?.assumingMemoryBound(to: Float.self) else { return }
        let stride = interleaved ? channels : 1
        observer((0..<frames).map { data[$0 * stride] }, sampleRate)
    }

    private func applyChain(_ chain: AudioDspChain, _ scratch: KotlinFloatArray, _ bufferList: UnsafeMutablePointer<AudioBufferList>, _ frames: Int) {
        let buffers = UnsafeMutableAudioBufferListPointer(bufferList)
        guard frames * channels <= Int(scratch.size) else { return }
        if interleaved, let data = buffers.first?.mData?.assumingMemoryBound(to: Float.self) {
            for index in 0..<(frames * channels) { scratch.set(index: Int32(index), value: data[index]) }
            chain.process(samples: scratch, frames: Int32(frames))
            for index in 0..<(frames * channels) { data[index] = scratch.get(index: Int32(index)) }
        } else if buffers.count == channels {
            for (channel, buffer) in buffers.enumerated() {
                guard let data = buffer.mData?.assumingMemoryBound(to: Float.self) else { return }
                for frame in 0..<frames { scratch.set(index: Int32(frame * channels + channel), value: data[frame]) }
            }
            chain.process(samples: scratch, frames: Int32(frames))
            for (channel, buffer) in buffers.enumerated() {
                guard let data = buffer.mData?.assumingMemoryBound(to: Float.self) else { return }
                for frame in 0..<frames { data[frame] = scratch.get(index: Int32(frame * channels + channel)) }
            }
        }
    }
}

extension AVPlayerPlaybackEngine {
    /// Applies [settings] now and remembers them. The effects tap is only put on an item once something is on, so
    /// untouched sound keeps the system's own audio path.
    public func applyAudio(_ settings: AudioEffectSettings) {
        audioSettings = settings
        settings.save()
        audioEffects.update(settings: settings)
        if let item = player.currentItem { attachAudioEffects(to: item) }
    }

    /// Puts the effects on [item]'s audio when they are needed, and reads its ReplayGain tags.
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
