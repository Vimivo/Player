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

#if canImport(ActivityKit)
import ActivityKit
#endif

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
        updateLiveActivity(
            item: item,
            isPlaying: isPlaying,
            positionSeconds: positionSeconds,
            durationSeconds: durationSeconds
        )
    }

    func clear() {
        defaults.removeObject(forKey: Self.storageKey)
        lastSnapshot = .empty
        lastWrite = .distantPast
        WidgetCenter.shared.reloadTimelines(ofKind: "dev.vimivo.widget.now-playing")
        endLiveActivity()
    }

    private func updateLiveActivity(
        item: MediaItem?,
        isPlaying: Bool,
        positionSeconds: Double,
        durationSeconds: Double
    ) {
#if canImport(ActivityKit)
        if #available(iOS 16.1, *) {
            guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

            guard let item = item else {
                endLiveActivity()
                return
            }

            let contentState = VimivoPlaybackActivityAttributes.ContentState(
                title: item.title,
                artist: item.artist ?? item.album ?? "Vimivo",
                isPlaying: isPlaying,
                positionSeconds: max(positionSeconds, 0),
                durationSeconds: max(durationSeconds, 0),
                updatedAt: Date()
            )

            let active = Activity<VimivoPlaybackActivityAttributes>.activities.first

            if let activity = active, activity.attributes.mediaID == item.id {
                Task {
                    if #available(iOS 16.2, *) {
                        await activity.update(ActivityContent(state: contentState, staleDate: nil))
                    } else {
                        await activity.update(using: contentState)
                    }
                }
            } else {
                if let oldActivity = active {
                    Task {
                        if #available(iOS 16.2, *) {
                            await oldActivity.end(nil, dismissalPolicy: .immediate)
                        } else {
                            await oldActivity.end(dismissalPolicy: .immediate)
                        }
                    }
                }
                let attributes = VimivoPlaybackActivityAttributes(mediaID: item.id)
                do {
                    if #available(iOS 16.2, *) {
                        _ = try Activity.request(
                            attributes: attributes,
                            content: ActivityContent(state: contentState, staleDate: nil),
                            pushType: nil
                        )
                    } else {
                        _ = try Activity.request(
                            attributes: attributes,
                            contentState: contentState,
                            pushType: nil
                        )
                    }
                } catch {
                    // Graceful fallback when permissions not granted
                }
            }
        }
#endif
    }

    private func endLiveActivity() {
#if canImport(ActivityKit)
        if #available(iOS 16.1, *) {
            for activity in Activity<VimivoPlaybackActivityAttributes>.activities {
                Task {
                    if #available(iOS 16.2, *) {
                        await activity.end(nil, dismissalPolicy: .immediate)
                    } else {
                        await activity.end(dismissalPolicy: .immediate)
                    }
                }
            }
        }
#endif
    }
}

