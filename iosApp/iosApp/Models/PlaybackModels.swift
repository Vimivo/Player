import Foundation
import VimivoShared

public enum PlaybackStatus: String, Codable {
    case idle
    case buffering
    case playing
    case paused
    case completed
    case error
}

public enum RepeatMode: String, Codable, CaseIterable {
    case off
    case one
    case all

    /// The shared queue's repeat mode as the app's.
    init(_ mode: QueueRepeatMode) {
        switch mode {
        case .one: self = .one
        case .all: self = .all
        default: self = .off
        }
    }

    public var iconName: String {
        switch self {
        case .off: return "repeat"
        case .one: return "repeat.1"
        case .all: return "repeat"
        }
    }
}

public struct PlaybackState {
    public var currentItem: MediaItem?
    public var status: PlaybackStatus
    public var positionMillis: Int64
    public var durationMillis: Int64
    public var playbackRate: Float
    public var isMuted: Bool
    public var volume: Float
    public var repeatMode: RepeatMode
    public var isShuffled: Bool
    public var errorMessage: String?
    public var bufferedMillis: Int64 = 0
    public var videoWidth: Int?
    public var videoHeight: Int?

    public init(
        currentItem: MediaItem? = nil,
        status: PlaybackStatus = .idle,
        positionMillis: Int64 = 0,
        durationMillis: Int64 = 0,
        playbackRate: Float = 1.0,
        isMuted: Bool = false,
        volume: Float = 1.0,
        repeatMode: RepeatMode = .off,
        isShuffled: Bool = false,
        errorMessage: String? = nil
    ) {
        self.currentItem = currentItem
        self.status = status
        self.positionMillis = positionMillis
        self.durationMillis = durationMillis
        self.playbackRate = playbackRate
        self.isMuted = isMuted
        self.volume = volume
        self.repeatMode = repeatMode
        self.isShuffled = isShuffled
        self.errorMessage = errorMessage
    }

    public var progress: Double {
        guard durationMillis > 0 else { return 0.0 }
        return min(max(Double(positionMillis) / Double(durationMillis), 0.0), 1.0)
    }
}
