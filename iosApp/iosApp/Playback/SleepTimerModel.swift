import Foundation
import VimivoCoreRust

/// The sleep timer: after a chosen time, or after the current track, playback fades out and pauses.
/// Monotonic fade calculations are powered 100% by Rust (`calculateSleepFadeVolume`).
public final class SleepTimerModel: ObservableObject {
    @Published public private(set) var remainingMillis: Int64?
    @Published public private(set) var stopsAfterTrack = false

    /// How long the sound fades out when the time is up.
    public var fadeSeconds: Int = 10
    public static let minuteChoices: [Int] = [5, 10, 15, 30, 45, 60, 90, 120]

    private weak var engine: AVPlayerPlaybackEngine?
    private var internalTimer: Timer?
    private var fadeTimer: Timer?
    private var targetEpochMillis: Int64?
    private var initialVolume: Float?
    private var isFading = false
    private let clock: () -> Int64

    public var isActive: Bool { remainingMillis != nil || stopsAfterTrack || isFading }

    public init(clock: (() -> Int64)? = nil) {
        self.clock = clock ?? { Int64(Date().timeIntervalSince1970 * 1000) }
    }

    public func attach(_ engine: AVPlayerPlaybackEngine) {
        self.engine = engine
    }

    public func start(minutes: Int) {
        cancel()
        let now = clock()
        let durationMillis = Int64(minutes) * 60 * 1000
        targetEpochMillis = now + durationMillis
        remainingMillis = durationMillis
        stopsAfterTrack = false
        initialVolume = currentVolume()

        internalTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            self?.tick()
        }
    }

    private func currentVolume() -> Float {
        if engine?.usingMpv == true {
            return Float((engine?.mpv.volumePercent ?? 100) / 100)
        }
        return engine?.player.volume ?? 1.0
    }

    private func applyVolume(_ vol: Float) {
        if engine?.usingMpv == true {
            engine?.mpv.setVolume(percent: Double(vol) * 100)
        } else {
            engine?.player.volume = vol
        }
    }

    private func tick() {
        guard let target = targetEpochMillis else { return }
        let now = clock()
        let remaining = target - now

        if remaining <= 0 {
            internalTimer?.invalidate()
            internalTimer = nil
            targetEpochMillis = nil
            remainingMillis = nil
            startFadeOut(durationSeconds: fadeSeconds)
            return
        }

        remainingMillis = remaining
    }

    private func startFadeOut(durationSeconds: Int) {
        guard !isFading else { return }
        isFading = true
        let baseVolume = initialVolume ?? currentVolume()
        initialVolume = baseVolume
        let fadeDurationMillis = Int64(max(1, durationSeconds) * 1000)
        let fadeStartEpochMillis = Date().timeIntervalSince1970 * 1000

        fadeTimer?.invalidate()
        fadeTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] timer in
            guard let self = self else {
                timer.invalidate()
                return
            }

            // If user or system paused playback during the fade, end fade and restore volume
            if self.engine?.state.status != .playing {
                timer.invalidate()
                self.fadeTimer = nil
                self.isFading = false
                self.applyVolume(baseVolume)
                self.initialVolume = nil
                return
            }

            let elapsedMillis = Int64(Date().timeIntervalSince1970 * 1000 - fadeStartEpochMillis)
            if elapsedMillis >= fadeDurationMillis {
                timer.invalidate()
                self.fadeTimer = nil
                self.isFading = false
                self.engine?.pause()
                self.applyVolume(baseVolume)
                self.initialVolume = nil
            } else {
                let fraction = calculateSleepFadeVolume(elapsedMillis: elapsedMillis, fadeMillis: fadeDurationMillis)
                self.applyVolume(baseVolume * fraction)
            }
        }
    }

    public func stopAfterCurrentTrack() {
        cancel()
        stopsAfterTrack = true
        engine?.clearPrepared()
    }

    public func cancel() {
        internalTimer?.invalidate()
        internalTimer = nil
        fadeTimer?.invalidate()
        fadeTimer = nil
        isFading = false
        targetEpochMillis = nil
        remainingMillis = nil
        stopsAfterTrack = false
        if let base = initialVolume {
            applyVolume(base)
            initialVolume = nil
        }
    }

    /// Asked by the engine as a track ends: true once when "stop after this track" was armed.
    func consumeStopAfterCurrent() -> Bool {
        if stopsAfterTrack {
            stopsAfterTrack = false
            return true
        }
        return false
    }

    /// Runs the end of the countdown now: fade out, pause, put the volume back.
    func expireNow(fadeSeconds: Int) {
        cancel()
        startFadeOut(durationSeconds: fadeSeconds)
    }
}
