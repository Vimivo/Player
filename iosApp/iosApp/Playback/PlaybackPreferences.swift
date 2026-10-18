import Foundation
import VimivoShared

/// User playback preferences and the bridge to the shared Kotlin resume rules, so iOS starts media at the
/// same position Android and desktop would.
public enum PlaybackPreferences {
    public static let resumeVideoKey = "vimivo_resume_video"
    public static let resumeSongsKey = "vimivo_resume_songs"

    /// Speeds offered by every platform, defined once in the shared Kotlin module.
    public static let speedPresets: [Float] = PlaybackSpeeds.shared.PRESETS.map { $0.floatValue }
    public static let speedRange: ClosedRange<Double> = Double(PlaybackSpeeds.shared.MIN)...Double(PlaybackSpeeds.shared.MAX)

    /// How often the current position is persisted while playing, matching the desktop save interval.
    public static let checkpointIntervalMillis: Int64 = 10_000

    public static func startPositionMillis(
        for item: MediaItem,
        saved: ResumeState?,
        defaults: UserDefaults = .standard
    ) -> Int64 {
        guard let saved, isResumeEnabled(for: item.kind, defaults: defaults) else { return 0 }
        let duration = saved.durationMillis ?? item.durationMillis
        return ResumePolicy.shared.startPositionMillis(
            kind: item.kind.shared,
            savedPositionMillis: saved.positionMillis,
            durationMillis: duration.map { KotlinLong(value: $0) },
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
}

extension MediaKind {
    var shared: VimivoShared.MediaKind {
        switch self {
        case .audio: return .audio
        case .video: return .video
        case .stream: return .stream
        }
    }
}
