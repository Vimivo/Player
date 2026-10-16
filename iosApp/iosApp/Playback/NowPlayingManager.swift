import Foundation
import MediaPlayer
import UIKit

public final class NowPlayingManager {
    public static let shared = NowPlayingManager()

    public func update(
        item: MediaItem?,
        isPlaying: Bool,
        positionSeconds: Double,
        durationSeconds: Double,
        playbackRate: Float = 1.0,
        queueIndex: Int = 0,
        queueCount: Int = 1
    ) {
        guard let item = item else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: item.title,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: positionSeconds,
            MPMediaItemPropertyPlaybackDuration: durationSeconds > 0 ? durationSeconds : 0,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? Double(playbackRate) : 0.0,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: 1.0,
            MPNowPlayingInfoPropertyPlaybackQueueIndex: max(queueIndex, 0),
            MPNowPlayingInfoPropertyPlaybackQueueCount: max(queueCount, 1),
            MPNowPlayingInfoPropertyExternalContentIdentifier: item.id,
            MPNowPlayingInfoPropertyIsLiveStream: item.kind == .stream,
            MPMediaItemPropertyMediaType: item.kind == .video
                ? MPMediaType.anyVideo.rawValue
                : MPMediaType.music.rawValue
        ]

        if let artist = item.artist {
            info[MPMediaItemPropertyArtist] = artist
        }
        if let album = item.album {
            info[MPMediaItemPropertyAlbumTitle] = album
        }

        if let artUri = item.artworkUri {
            let url = URL(string: artUri) ?? URL(fileURLWithPath: artUri)
            if let image = UIImage(contentsOfFile: url.path) {
                let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                info[MPMediaItemPropertyArtwork] = artwork
            }
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    public func clear() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
}
