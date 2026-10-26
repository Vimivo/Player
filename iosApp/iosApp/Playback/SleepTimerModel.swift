import Foundation
import VimivoShared

/// The sleep timer: after a chosen time, or after the current track, playback fades out and pauses. The countdown and
/// the rules are the shared module's (`SleepTimer`, read from the wall clock so it stays right while the app is in the
/// background with audio playing); this fades `AVPlayer`'s volume and pauses the engine.
/// Used from the main thread only (the engine, SwiftUI and the shared timer's main-dispatcher callbacks all are).
public final class SleepTimerModel: ObservableObject {
    @Published public private(set) var remainingMillis: Int64?
    @Published public private(set) var stopsAfterTrack = false

    public static let defaultFadeSeconds = 10
    /// How long the sound fades out when the time is up.
    public var fadeSeconds = SleepTimerModel.defaultFadeSeconds
    private static let fadeStepMillis: UInt64 = 100

    private weak var engine: AVPlayerPlaybackEngine?
    private var timer: IosSleepTimer?
    private var fade: Task<Void, Never>?

    public var isActive: Bool { remainingMillis != nil || stopsAfterTrack }

    /// A [clock] in milliseconds can be given for tests; the app uses the wall clock.
    init(clock: (() -> Int64)? = nil) {
        let onChange: (SleepTimerState) -> Void = { [weak self] state in
            DispatchQueue.main.async {
                self?.remainingMillis = state.remainingMillis?.int64Value
                self?.stopsAfterTrack = state.stopAfterCurrent
            }
        }
        let onTrigger: (KotlinInt) -> Void = { [weak self] seconds in
            Task { @MainActor in await self?.expire(fadeSeconds: seconds.intValue) }
        }
        timer = clock.map { clock in
            IosSleepTimer(clock: { KotlinLong(longLong: clock()) }, onChange: onChange, onTrigger: onTrigger)
        } ?? IosSleepTimer(onChange: onChange, onTrigger: onTrigger)
    }

    func attach(_ engine: AVPlayerPlaybackEngine) { self.engine = engine }

    public func start(minutes: Int) {
        cancelFade()
        timer?.schedule(durationMinutes: Int32(minutes), fadeOutSeconds: Int32(fadeSeconds))
    }

    /// Stops when the current track ends. A song already queued behind it for a gapless start is taken out, or the
    /// player would carry on into it.
    public func stopAfterCurrentTrack() {
        cancelFade()
        timer?.afterCurrent(fadeOutSeconds: 0)
        engine?.clearPrepared()
    }

    public func cancel() {
        cancelFade()
        timer?.cancel()
    }

    /// Asked by the engine as a track ends: true once when "stop after this track" was armed.
    func consumeStopAfterCurrent() -> Bool { timer?.consumeStopAfterCurrent() ?? false }

    /// Time is up: fade the volume out, pause, put the volume back. Pressing play during the fade ends the fade.
    func expire(fadeSeconds: Int) async {
        guard let engine else { return }
        cancelFade()
        let original = engine.player.volume
        let task = Task { @MainActor in
            let total = Int64(fadeSeconds) * 1_000
            var elapsed: Int64 = 0
            while !Task.isCancelled, total > 0, elapsed < total, engine.state.status == .playing {
                engine.player.volume = original * SleepFade.shared.volumeFraction(elapsedMillis: elapsed, fadeMillis: total)
                try? await Task.sleep(nanoseconds: Self.fadeStepMillis * 1_000_000)
                elapsed += Int64(Self.fadeStepMillis)
            }
            if !Task.isCancelled, engine.state.status == .playing { engine.pause() }
            engine.player.volume = original
        }
        fade = task
        await task.value
        if fade == task { fade = nil }
    }

    private func cancelFade() {
        fade?.cancel()
        fade = nil
    }
}
