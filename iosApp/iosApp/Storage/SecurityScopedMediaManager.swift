import Foundation
import AVFoundation
import UIKit

public final class SecurityScopedMediaManager {
    public static let shared = SecurityScopedMediaManager()
    private var activeUrls = Set<URL>()
    private let lock = NSLock()

    public func createBookmark(for url: URL) throws -> Data {
        guard url.startAccessingSecurityScopedResource() else {
            return try url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
        }
        defer { url.stopAccessingSecurityScopedResource() }
        return try url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
    }

    public func resolveBookmark(data: Data) -> URL? {
        var isStale = false
        do {
            let url = try URL(resolvingBookmarkData: data, options: .withoutUI, relativeTo: nil, bookmarkDataIsStale: &isStale)
            return url
        } catch {
            return nil
        }
    }

    public func startAccessing(url: URL) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        let success = url.startAccessingSecurityScopedResource()
        if success {
            activeUrls.insert(url)
        }
        return success
    }

    public func stopAccessing(url: URL) {
        lock.lock()
        defer { lock.unlock() }
        if activeUrls.contains(url) {
            url.stopAccessingSecurityScopedResource()
            activeUrls.remove(url)
        }
    }

    /// - Parameters:
    ///   - id: a stable identifier; files the app owns pass one so re-importing never duplicates them.
    ///   - createsBookmark: false for files inside the app container, which are re-resolved by path instead.
    public func extractMediaItem(from url: URL, bookmark: Data? = nil, id: String? = nil, createsBookmark: Bool = true) async -> MediaItem {
        let bookmarkData = bookmark ?? (createsBookmark ? try? createBookmark(for: url) : nil)
        let asset = AVURLAsset(url: url)
        var durationMillis: Int64? = nil
        if let duration = try? await asset.load(.duration) {
            let seconds = CMTimeGetSeconds(duration)
            if !seconds.isNaN && seconds > 0 {
                durationMillis = Int64(seconds * 1000)
            }
        }

        var isVideo = false
        var dimensions: (width: Int, height: Int)?
        if let tracks = try? await asset.loadTracks(withMediaType: .video), let track = tracks.first {
            isVideo = true
            dimensions = await Self.displayDimensions(of: track)
        }

        var title: String? = nil
        var artist: String? = nil
        var album: String? = nil
        var artworkUri: String? = nil

        if let metadata = try? await asset.load(.metadata) {
            for item in metadata {
                guard let key = item.commonKey?.rawValue else { continue }
                if key == AVMetadataKey.commonKeyTitle.rawValue, let val = try? await item.load(.stringValue) {
                    title = val
                } else if key == AVMetadataKey.commonKeyArtist.rawValue, let val = try? await item.load(.stringValue) {
                    artist = val
                } else if key == AVMetadataKey.commonKeyAlbumName.rawValue, let val = try? await item.load(.stringValue) {
                    album = val
                } else if key == AVMetadataKey.commonKeyArtwork.rawValue, let data = try? await item.load(.dataValue) {
                    artworkUri = saveArtwork(data: data, id: UUID().uuidString)
                }
            }
        }

        let fallbackTitle = url.deletingPathExtension().lastPathComponent
        return MediaItem(
            id: id ?? UUID().uuidString,
            uri: url.absoluteString,
            title: title ?? fallbackTitle,
            kind: isVideo ? .video : .audio,
            durationMillis: durationMillis,
            artist: artist,
            album: album,
            artworkUri: artworkUri,
            bookmarkData: bookmarkData,
            width: dimensions?.width,
            height: dimensions?.height,
            sizeBytes: Self.fileSize(of: url)
        )
    }

    /// Reads display dimensions and file size for a stored video, for libraries imported before
    /// those fields existed. Returns nil when the file can no longer be resolved.
    public func loadVideoMetadata(for item: MediaItem) async -> (width: Int?, height: Int?, sizeBytes: Int64?)? {
        guard let url = resolvedUrl(for: item) else { return nil }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let asset = AVURLAsset(url: url)
        var dimensions: (width: Int, height: Int)?
        if let track = try? await asset.loadTracks(withMediaType: .video).first {
            dimensions = await Self.displayDimensions(of: track)
        }
        return (dimensions?.width, dimensions?.height, Self.fileSize(of: url))
    }

    public func resolvedUrl(for item: MediaItem) -> URL? {
        if let data = item.bookmarkData, let url = resolveBookmark(data: data) {
            return url
        }
        return URL(string: item.uri)
    }

    private static func displayDimensions(of track: AVAssetTrack) async -> (width: Int, height: Int)? {
        guard let (size, transform) = try? await track.load(.naturalSize, .preferredTransform) else { return nil }
        let rect = CGRect(origin: .zero, size: size).applying(transform)
        let width = Int(abs(rect.width).rounded())
        let height = Int(abs(rect.height).rounded())
        return width > 0 && height > 0 ? (width, height) : nil
    }

    private static func fileSize(of url: URL) -> Int64? {
        guard url.isFileURL, let values = try? url.resourceValues(forKeys: [.fileSizeKey]), let size = values.fileSize else {
            return nil
        }
        return Int64(size)
    }

    private func saveArtwork(data: Data, id: String) -> String? {
        guard let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return nil }
        let dir = caches.appendingPathComponent("artwork", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let file = dir.appendingPathComponent("\(id).jpg")
        do {
            try data.write(to: file)
            return file.absoluteString
        } catch {
            return nil
        }
    }
}
