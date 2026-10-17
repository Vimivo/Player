import AppIntents

@available(iOS 17.0, *)
struct ToggleWidgetPlaybackIntent: AudioPlaybackIntent {
    static let title: LocalizedStringResource = "Play or pause Vimivo"
    static let description = IntentDescription("Controls the authoritative Vimivo player.")

    @MainActor
    func perform() async throws -> some IntentResult {
#if !VIMIVO_WIDGET_EXTENSION
        AVPlayerPlaybackEngine.shared.togglePlayPause()
#endif
        return .result()
    }
}

@available(iOS 17.0, *)
struct NextWidgetTrackIntent: AudioPlaybackIntent {
    static let title: LocalizedStringResource = "Play next in Vimivo"

    @MainActor
    func perform() async throws -> some IntentResult {
#if !VIMIVO_WIDGET_EXTENSION
        AVPlayerPlaybackEngine.shared.playNext()
#endif
        return .result()
    }
}

@available(iOS 17.0, *)
struct PreviousWidgetTrackIntent: AudioPlaybackIntent {
    static let title: LocalizedStringResource = "Play previous in Vimivo"

    @MainActor
    func perform() async throws -> some IntentResult {
#if !VIMIVO_WIDGET_EXTENSION
        AVPlayerPlaybackEngine.shared.playPrevious()
#endif
        return .result()
    }
}

#if canImport(ActivityKit)
import ActivityKit

@available(iOS 16.1, *)
public struct VimivoPlaybackActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var title: String
        public var artist: String
        public var isPlaying: Bool
        public var positionSeconds: Double
        public var durationSeconds: Double
        public var updatedAt: Date

        public init(
            title: String,
            artist: String,
            isPlaying: Bool,
            positionSeconds: Double,
            durationSeconds: Double,
            updatedAt: Date = Date()
        ) {
            self.title = title
            self.artist = artist
            self.isPlaying = isPlaying
            self.positionSeconds = positionSeconds
            self.durationSeconds = durationSeconds
            self.updatedAt = updatedAt
        }
    }

    public var mediaID: String

    public init(mediaID: String) {
        self.mediaID = mediaID
    }
}
#endif
