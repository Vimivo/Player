import Foundation
import VimivoShared

/// The sleep timer: after a chosen time, or after the current track, playback fades out and pauses. The countdown, the
/// fade, the pause and the "after this track" request are the shared module's (`PlaybackSleepTimer`, the same code as
/// Android and desktop, on the wall clock so it stays right while the app is in the background with audio playing);
/// this only lets it use `AVPlayer`'s volume and the engine's pause.
public final class SleepTimerModel: ObservableObject {
    @Published public private(set) var remainingMillis: Int64?
    @Published public private(set) var stopsAfterTrack = false

    /// How long the sound fades out when the time is up.
    public var fadeSeconds = Int(SleepTimerOptions.shared.FADE_SECONDS)
    public static let minuteChoices = SleepTimerOptions.shared.MINUTES.map(\.intValue)

    private weak var engine: AVPlayerPlaybackEngine?
    private var timer: IosSleepTimer?

    public var isActive: Bool { remainingMillis != nil || stopsAfterTrack }

    /// A [clock] in milliseconds can be given for tests; the app uses the wall clock.
    init(clock: (() -> Int64)? = nil) {
        let player = EnginePlayer()
        enginePlayer = player
        let onChange: (SleepTimerState) -> Void = { [weak self] state in
            DispatchQueue.main.async {
                self?.remainingMillis = state.remainingMillis?.int64Value
                self?.stopsAfterTrack = state.stopAfterCurrent
            }
        }
        timer = clock.map { clock in
            IosSleepTimer(clock: { KotlinLong(longLong: clock()) }, player: player, onChange: onChange)
        } ?? IosSleepTimer(player: player, onChange: onChange)
    }

    private let enginePlayer: EnginePlayer

    func attach(_ engine: AVPlayerPlaybackEngine) {
        self.engine = engine
        enginePlayer.engine = engine
    }

    public func start(minutes: Int) {
        timer?.schedule(durationMinutes: Int32(minutes), fadeOutSeconds: Int32(fadeSeconds))
    }

    /// Stops when the current track ends. A song already queued behind it for a gapless start is taken out, or the
    /// player would carry on into it.
    public func stopAfterCurrentTrack() {
        timer?.afterCurrent()
        engine?.clearPrepared()
    }

    public func cancel() {
        timer?.cancel()
    }

    /// Asked by the engine as a track ends: true once when "stop after this track" was armed.
    func consumeStopAfterCurrent() -> Bool { timer?.consumeStopAfterCurrent() ?? false }

    /// Runs the end of the countdown now: fade out, pause, put the volume back.
    func expireNow(fadeSeconds: Int) {
        timer?.expireNow(fadeOutSeconds: Int32(fadeSeconds))
    }
}

/// The engine as the shared timer sees the player: `AVPlayer`'s volume, whether it plays, and pause.
private final class EnginePlayer: NSObject, SleepTimerPlayer {
    weak var engine: AVPlayerPlaybackEngine?

    func volume() -> Float { engine?.player.volume ?? 1 }

    func setVolume(volume_ volume: Float) { engine?.player.volume = volume }

    func isPlaying() -> Bool { engine?.state.status == .playing }

    func pause_() { engine?.pause() }
}
