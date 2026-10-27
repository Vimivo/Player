import AVFoundation
import Foundation
import UIKit
import VimivoShared

/// Frame stepping, A–B repeat and snapshots (V1 #5, #6, #16) for the AVPlayer-based engine.
extension AVPlayerPlaybackEngine {
    // MARK: - Frame stepping

    /// Steps one decoded frame forward or back and leaves playback paused. AVFoundation can step only the items whose
    /// video allows it (`canStepForward`, `canStepBackward`); false means this item cannot, for example audio.
    @discardableResult
    public func stepFrame(forward: Bool) -> Bool {
        guard let item = player.currentItem, item.hasVideo else { return false }
        guard forward ? item.canStepForward : item.canStepBackward else { return false }
        pause()
        item.step(byCount: forward ? 1 : -1)
        return true
    }

    // MARK: - A–B repeat

    /// Repeats the part between A and B: when playback reaches B it jumps back to A, to the frame. Passing nil, or a
    /// region that is empty, clears it. Every item starts without a region.
    public func setABRepeat(_ region: ClosedRange<Int64>?) {
        if let token = abBoundaryToken {
            player.removeTimeObserver(token)
            abBoundaryToken = nil
        }
        guard let region, region.upperBound > region.lowerBound else {
            abRepeat = nil
            return
        }
        abRepeat = region
        let end = NSValue(time: CMTime(value: region.upperBound, timescale: 1000))
        abBoundaryToken = player.addBoundaryTimeObserver(forTimes: [end], queue: .main) { [weak self] in
            self?.seek(toMillis: region.lowerBound)
        }
    }

    // MARK: - Snapshots

    public enum SnapshotError: Error { case nothingToCapture }

    /// The frame on screen at full size, with the picture adjustments applied. Audio has no frame to capture.
    public func captureFrame() async throws -> CGImage {
        guard let item = player.currentItem, item.hasVideo else { throw SnapshotError.nothingToCapture }
        let generator = AVAssetImageGenerator(asset: item.asset)
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero
        generator.appliesPreferredTrackTransform = true
        generator.videoComposition = item.videoComposition
        return try await generator.image(at: player.currentTime()).image
    }

    /// Saves the frame on screen as a PNG in the app's Documents folder (shown in the Files app under Vimivo), named
    /// with the title, position and time, and returns where it went.
    public func saveSnapshot() async throws -> URL {
        let image = try await captureFrame()
        let title = state.currentItem?.title
        let name = SnapshotNaming.shared.fileName(
            title: title,
            positionMillis: state.positionMillis,
            timestampMillis: Int64(Date().timeIntervalSince1970 * 1000),
            format: .png
        )
        let folder = try FileManager.default
            .url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("Snapshots", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let url = folder.appendingPathComponent(name)
        guard let data = UIImage(cgImage: image).pngData() else { throw SnapshotError.nothingToCapture }
        try data.write(to: url, options: .atomic)
        return url
    }
}

private extension AVPlayerItem {
    /// True when the item has a video track (an audio file has nothing to step through or capture).
    var hasVideo: Bool { tracks.contains { $0.assetTrack?.mediaType == .video } }
}
