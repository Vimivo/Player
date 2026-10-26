import AVFoundation
import Foundation
import VimivoShared

/// The real peaks of a track for the seek bar. The audio is read in the background with `AVAssetReader` (downsampled
/// to mono, so it is quick), the shared module turns it into peaks, and the result is cached by path, size and modified
/// time under Caches, so a changed file is read again and an unchanged one never is.
actor WaveformLoader {
    static let shared = WaveformLoader()

    private let cacheDirectory: URL
    private var inFlight: [String: Task<[Float]?, Never>] = [:]
    private static let readRate = 8_000.0

    init(cacheDirectory: URL? = nil) {
        self.cacheDirectory = cacheDirectory
            ?? FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("waveforms", isDirectory: true)
    }

    /// The peaks of [url], or nil when it cannot be read or is silent.
    func peaks(for url: URL) async -> [Float]? {
        guard url.isFileURL else { return nil }
        let key = Self.cacheKey(for: url)
        if let key, let cached = readCache(key) { return cached }
        let id = key ?? url.path
        if let running = inFlight[id] { return await running.value }
        let task = Task.detached(priority: .utility) { await Self.decode(url) }
        inFlight[id] = task
        let result = await task.value
        inFlight[id] = nil
        if let key, let result { writeCache(key, result) }
        return result
    }

    /// The cache name for a file: its path, size and modified time (nil when they cannot be read).
    nonisolated static func cacheKey(for url: URL) -> String? {
        // FileManager, not URL.resourceValues: a URL object caches its resource values, so a replaced file would keep
        // its old size and date.
        guard let attributes = try? FileManager.default.attributesOfItem(atPath: url.path),
              let size = (attributes[.size] as? NSNumber)?.int64Value, size > 0 else { return nil }
        let modified = Int64(((attributes[.modificationDate] as? Date)?.timeIntervalSince1970 ?? 0) * 1000)
        return WaveformKey.shared.of(path: url.absoluteString, sizeBytes: size, modifiedMillis: modified)
    }

    private func cacheFile(_ key: String) -> URL { cacheDirectory.appendingPathComponent("\(key).peaks") }

    private func readCache(_ key: String) -> [Float]? {
        guard let data = try? Data(contentsOf: cacheFile(key)),
              let peaks = PeakCodec.shared.decode(bytes: KotlinByteArray(data: data)) else { return nil }
        return (0..<Int(peaks.size)).map { peaks.get(index: Int32($0)) }
    }

    private func writeCache(_ key: String, _ peaks: [Float]) {
        let floats = KotlinFloatArray(size: Int32(peaks.count))
        for (index, value) in peaks.enumerated() { floats.set(index: Int32(index), value: value) }
        let bytes = PeakCodec.shared.encode(peaks: floats)
        try? FileManager.default.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
        try? bytes.data.write(to: cacheFile(key), options: .atomic)
    }

    // MARK: Decoding

    private static func decode(_ url: URL) async -> [Float]? {
        let asset = AVURLAsset(url: url)
        guard let track = try? await asset.loadTracks(withMediaType: .audio).first,
              let reader = try? AVAssetReader(asset: asset) else { return nil }
        let output = AVAssetReaderTrackOutput(track: track, outputSettings: [
            AVFormatIDKey: kAudioFormatLinearPCM,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsNonInterleaved: false,
            AVNumberOfChannelsKey: 1,
            AVSampleRateKey: readRate,
        ])
        guard reader.canAdd(output) else { return nil }
        reader.add(output)
        guard reader.startReading() else { return nil }
        let accumulator = PeakAccumulator(chunkFrames: 64)
        var scratch = KotlinShortArray(size: 0)
        while !Task.isCancelled, let buffer = output.copyNextSampleBuffer() {
            defer { CMSampleBufferInvalidate(buffer) }
            guard let block = CMSampleBufferGetDataBuffer(buffer) else { continue }
            let length = CMBlockBufferGetDataLength(block)
            var bytes = [UInt8](repeating: 0, count: length)
            guard CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: length, destination: &bytes) == kCMBlockBufferNoErr else { continue }
            let count = length / 2
            if Int(scratch.size) < count { scratch = KotlinShortArray(size: Int32(count)) }
            for index in 0..<count {
                scratch.set(index: Int32(index), value: Int16(bitPattern: UInt16(bytes[2 * index]) | UInt16(bytes[2 * index + 1]) << 8))
            }
            accumulator.add(samples: scratch, count: Int32(count), channels: 1)
        }
        guard reader.status != .failed, let peaks = accumulator.finish(buckets: WaveformPeaks.shared.BUCKETS) else { return nil }
        return (0..<Int(peaks.size)).map { peaks.get(index: Int32($0)) }
    }
}

// MARK: - Kotlin array conversions

extension KotlinByteArray {
    convenience init(data: Data) {
        self.init(size: Int32(data.count))
        for (index, byte) in data.enumerated() { set(index: Int32(index), value: Int8(bitPattern: byte)) }
    }

    var data: Data {
        var bytes = Data(count: Int(size))
        for index in 0..<Int(size) { bytes[index] = UInt8(bitPattern: get(index: Int32(index))) }
        return bytes
    }
}
