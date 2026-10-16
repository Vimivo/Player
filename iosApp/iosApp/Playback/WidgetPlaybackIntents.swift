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
