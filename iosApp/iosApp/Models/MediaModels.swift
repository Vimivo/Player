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
    public let width: Int?
    public let height: Int?
    public let sizeBytes: Int64?
    public let dateAddedMillis: Int64?

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
        bookmarkData: Data? = nil,
        width: Int? = nil,
        height: Int? = nil,
        sizeBytes: Int64? = nil,
        dateAddedMillis: Int64? = nil
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
        self.width = width
        self.height = height
        self.sizeBytes = sizeBytes
        self.dateAddedMillis = dateAddedMillis
    }

    /// Returns a copy carrying technical video metadata discovered after import.
    public func withVideoMetadata(width: Int?, height: Int?, sizeBytes: Int64?) -> MediaItem {
        MediaItem(
            id: id, uri: uri, title: title, kind: kind, durationMillis: durationMillis,
            artist: artist, album: album, artworkUri: artworkUri, albumArtist: albumArtist,
            bookmarkData: bookmarkData, width: width ?? self.width, height: height ?? self.height,
            sizeBytes: sizeBytes ?? self.sizeBytes, dateAddedMillis: dateAddedMillis
        )
    }

    /// Short quality label such as "1080p" or "4K", matching the Android library rows.
    public var resolutionLabel: String? {
        guard let width = width, let height = height, width > 0, height > 0 else { return nil }
        return MediaFormatting.resolutionLabel(width: width, height: height)
    }

    /// "duration · resolution · size" for video rows; "artist · album" for audio.
    public var detailLine: String {
        let parts: [String?]
        if kind == .video {
            parts = [
                durationMillis.flatMap { $0 > 0 ? formattedDuration : nil },
                resolutionLabel,
                sizeBytes.map(MediaFormatting.byteCount)
            ]
        } else {
            parts = [artist, album]
        }
        return parts.compactMap { $0 }.joined(separator: " · ")
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

public enum MediaFormatting {
    public static func resolutionLabel(width: Int, height: Int) -> String {
        let shortSide = min(width, height)
        switch shortSide {
        case 2_000...: return "4K"
        case 1_400..<2_000: return "1440p"
        case 1_000..<1_400: return "1080p"
        case 700..<1_000: return "720p"
        default: return "\(shortSide)p"
        }
    }

    public static func byteCount(_ bytes: Int64) -> String {
        let units = ["B", "KB", "MB", "GB", "TB"]
        var value = Double(max(bytes, 0))
        var unit = 0
        while value >= 1024, unit < units.count - 1 {
            value /= 1024
            unit += 1
        }
        if unit == 0 { return "\(Int(value)) B" }
        return String(format: value >= 100 ? "%.0f %@" : "%.1f %@", value, units[unit])
    }

    /// Clock-style time used by the player: 1:05, 12:09, 1:02:03.
    public static func clock(millis: Int64) -> String {
        let total = max(Int(millis / 1000), 0)
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let seconds = total % 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, seconds)
            : String(format: "%d:%02d", minutes, seconds)
    }

    public static func speed(_ rate: Float) -> String {
        if rate.truncatingRemainder(dividingBy: 1) == 0 { return String(format: "%.1f×", rate) }
        var text = String(format: "%.2f", rate)
        while text.hasSuffix("0") { text.removeLast() }
        return text + "×"
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
