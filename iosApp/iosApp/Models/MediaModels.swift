import Foundation
import VimivoShared

public enum MediaKind: String, Codable, CaseIterable {
    case audio
    case video
    case stream

    public var displayName: String {
        switch self {
        case .audio: return "Music"
        case .video: return "Video"
        case .stream: return "Stream"
        }
    }
}

public struct MediaItem: Identifiable, Hashable, Codable {
    public let id: String
    public let uri: String
    public let title: String
    public let kind: MediaKind
    public let durationMillis: Int64?
    public let artist: String?
    public let album: String?
    public let artworkUri: String?
    public let albumArtist: String?
    public let bookmarkData: Data?

    public init(
        id: String = UUID().uuidString,
        uri: String,
        title: String,
        kind: MediaKind,
        durationMillis: Int64? = nil,
        artist: String? = nil,
        album: String? = nil,
        artworkUri: String? = nil,
        albumArtist: String? = nil,
        bookmarkData: Data? = nil
    ) {
        self.id = id
        self.uri = uri
        self.title = title
        self.kind = kind
        self.durationMillis = durationMillis
        self.artist = artist
        self.album = album
        self.artworkUri = artworkUri
        self.albumArtist = albumArtist
        self.bookmarkData = bookmarkData
    }

    public var formattedDuration: String {
        guard let ms = durationMillis, ms > 0 else { return "--:--" }
        let totalSeconds = Int(ms / 1000)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        let hours = minutes / 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes % 60, seconds)
        } else {
            return String(format: "%d:%02d", minutes, seconds)
        }
    }
}

public struct Playlist: Identifiable, Hashable, Codable {
    public let id: String
    public var name: String
    public var mediaIds: [String]
    public let createdAtMillis: Int64

    public init(id: String = UUID().uuidString, name: String, mediaIds: [String] = [], createdAtMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        self.id = id
        self.name = name
        self.mediaIds = mediaIds
        self.createdAtMillis = createdAtMillis
    }
}

public struct Bookmark: Identifiable, Hashable, Codable {
    public let id: String
    public let mediaId: String
    public var name: String
    public let positionMillis: Int64
    public let createdAtMillis: Int64

    public init(id: String = UUID().uuidString, mediaId: String, name: String, positionMillis: Int64, createdAtMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        self.id = id
        self.mediaId = mediaId
        self.name = name
        self.positionMillis = positionMillis
        self.createdAtMillis = createdAtMillis
    }
}

public struct PlaybackHistory: Identifiable, Hashable, Codable {
    public let id: String
    public let mediaId: String
    public let lastPlayedAtMillis: Int64
    public var positionMillis: Int64
    public var completed: Bool
    public var playCount: Int

    public init(id: String = UUID().uuidString, mediaId: String, lastPlayedAtMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000), positionMillis: Int64 = 0, completed: Bool = false, playCount: Int = 1) {
        self.id = id
        self.mediaId = mediaId
        self.lastPlayedAtMillis = lastPlayedAtMillis
        self.positionMillis = positionMillis
        self.completed = completed
        self.playCount = playCount
    }
}
