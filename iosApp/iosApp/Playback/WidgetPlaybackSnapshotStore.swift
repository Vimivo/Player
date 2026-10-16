import Foundation
import WidgetKit

struct WidgetPlaybackSnapshot: Codable, Equatable {
    let mediaID: String?
    let title: String
    let artist: String
    let artworkPath: String?
    let isPlaying: Bool
    let positionSeconds: Double
    let durationSeconds: Double
    let updatedAt: Date

    static let empty = WidgetPlaybackSnapshot(
        mediaID: nil,
        title: "Nothing Playing",
        artist: "Open Vimivo to choose local media",
        artworkPath: nil,
        isPlaying: false,
        positionSeconds: 0,
        durationSeconds: 0,
        updatedAt: .distantPast
    )
}

final class WidgetPlaybackSnapshotStore {
    static let shared = WidgetPlaybackSnapshotStore()
    static let suiteName = "group.dev.vimivo.shared"
    static let storageKey = "vimivo.widget.playback.v1"

    private let defaults = UserDefaults(suiteName: suiteName) ?? .standard
    private var lastSnapshot = WidgetPlaybackSnapshot.empty
    private var lastWrite = Date.distantPast

    func publish(
        item: MediaItem?,
        isPlaying: Bool,
        positionSeconds: Double,
        durationSeconds: Double
    ) {
        let snapshot = WidgetPlaybackSnapshot(
            mediaID: item?.id,
            title: item?.title ?? WidgetPlaybackSnapshot.empty.title,
            artist: item?.artist ?? item?.album ?? WidgetPlaybackSnapshot.empty.artist,
            artworkPath: item?.artworkUri,
            isPlaying: isPlaying,
            positionSeconds: max(positionSeconds, 0),
            durationSeconds: max(durationSeconds, 0),
            updatedAt: Date()
        )
        let stateChanged = snapshot.mediaID != lastSnapshot.mediaID || snapshot.isPlaying != lastSnapshot.isPlaying
        guard stateChanged || Date().timeIntervalSince(lastWrite) >= 15 else { return }
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: Self.storageKey)
        lastSnapshot = snapshot
        lastWrite = snapshot.updatedAt
        if stateChanged {
            WidgetCenter.shared.reloadTimelines(ofKind: "dev.vimivo.widget.now-playing")
        }
    }

    func clear() {
        defaults.removeObject(forKey: Self.storageKey)
        lastSnapshot = .empty
        lastWrite = .distantPast
        WidgetCenter.shared.reloadTimelines(ofKind: "dev.vimivo.widget.now-playing")
    }
}
