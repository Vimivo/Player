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

    public func extractMediaItem(from url: URL, bookmark: Data? = nil) async -> MediaItem {
        let bookmarkData = bookmark ?? (try? createBookmark(for: url))
        let asset = AVURLAsset(url: url)
        var durationMillis: Int64? = nil
        if let duration = try? await asset.load(.duration) {
            let seconds = CMTimeGetSeconds(duration)
            if !seconds.isNaN && seconds > 0 {
                durationMillis = Int64(seconds * 1000)
            }
        }

        var isVideo = false
        if let tracks = try? await asset.loadTracks(withMediaType: .video), !tracks.isEmpty {
            isVideo = true
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
            id: UUID().uuidString,
            uri: url.absoluteString,
            title: title ?? fallbackTitle,
            kind: isVideo ? .video : .audio,
            durationMillis: durationMillis,
            artist: artist,
            album: album,
            artworkUri: artworkUri,
            bookmarkData: bookmarkData
        )
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
