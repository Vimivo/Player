import Foundation
import VimivoCoreRust

/// User playback preferences and the bridge to the Rust core resume rules, so iOS starts media at the
/// exact same position Android, Windows, Linux, and macOS desktop would.
public enum PlaybackPreferences {
    public static let resumeVideoKey = "vimivo_resume_video"
    public static let resumeSongsKey = "vimivo_resume_songs"

    /// Speeds offered by every platform, defined once in the Rust core module.
    public static let speedPresets: [Float] = [0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0, 3.5, 4.0]
    public static let speedRange: ClosedRange<Double> = 0.25...4.0

    /// How often the current position is persisted while playing, matching the desktop save interval.
    public static let checkpointIntervalMillis: Int64 = 10_000

    public static func startPositionMillis(
        for item: MediaItem,
        saved: ResumeState?,
        defaults: UserDefaults = .standard
    ) -> Int64 {
        guard let saved, isResumeEnabled(for: item.kind, defaults: defaults) else { return 0 }
        let duration = saved.durationMillis ?? item.durationMillis
        let ffiKind: FfiMediaKind
        switch item.kind {
        case .audio: ffiKind = .audio
        case .video: ffiKind = .video
        case .stream: ffiKind = .stream
        }
        return calculateResumeStartPosition(
            kind: ffiKind,
            savedPositionMillis: saved.positionMillis,
            durationMillis: duration,
            completed: saved.completed,
            includeShortAudio: defaults.object(forKey: resumeSongsKey) as? Bool ?? false
        )
    }

    private static func isResumeEnabled(for kind: MediaKind, defaults: UserDefaults) -> Bool {
        if kind == .audio && defaults.object(forKey: resumeSongsKey) as? Bool == true { return true }
        return defaults.object(forKey: resumeVideoKey) as? Bool ?? true
    }
}

/// The last persisted position for a media item.
public struct ResumeState: Equatable {
    public let positionMillis: Int64
    public let durationMillis: Int64?
    public let completed: Bool

    public init(positionMillis: Int64, durationMillis: Int64? = nil, completed: Bool) {
        self.positionMillis = positionMillis
        self.durationMillis = durationMillis
        self.completed = completed
    }
}
