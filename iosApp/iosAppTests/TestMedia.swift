import AVFoundation
import UIKit

/// Media made on the fly for tests, so no binary fixtures are needed in the app bundle.
enum TestMedia {
    static let blockWidth = 8

    /// A mono 16-bit WAV with a sine tone.
    static func sineWav(seconds: Double, hz: Double = 440, sampleRate: Double = 44_100) throws -> URL {
        let frames = Int(seconds * sampleRate)
        var data = Data()
        func append<T: FixedWidthInteger>(_ value: T) { withUnsafeBytes(of: value.littleEndian) { data.append(contentsOf: $0) } }
        data.append(contentsOf: Array("RIFF".utf8)); append(UInt32(36 + frames * 2))
        data.append(contentsOf: Array("WAVEfmt ".utf8)); append(UInt32(16)); append(UInt16(1)); append(UInt16(1))
        append(UInt32(sampleRate)); append(UInt32(sampleRate) * 2); append(UInt16(2)); append(UInt16(16))
        data.append(contentsOf: Array("data".utf8)); append(UInt32(frames * 2))
        for frame in 0..<frames {
            append(Int16(8_000 * sin(2 * Double.pi * hz * Double(frame) / sampleRate)))
        }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("tone-\(UUID().uuidString).wav")
        try data.write(to: url)
        return url
    }

    /// An H.264 MP4 in which frame `i` is the `background` colour with a white block at x = `i * blockWidth`, so a
    /// picture taken at any time says which frame it is (see `blockIndex`).
    static func countingVideo(
        frames: Int,
        fps: Int32 = 10,
        size: CGSize = CGSize(width: 320, height: 180),
        background: UIColor = .red
    ) async throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("counting-\(UUID().uuidString).mp4")
        let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: Int(size.width),
            AVVideoHeightKey: Int(size.height),
        ])
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: Int(size.width),
            kCVPixelBufferHeightKey as String: Int(size.height),
        ])
        writer.add(input)
        guard writer.startWriting() else { throw writer.error ?? CocoaError(.fileWriteUnknown) }
        writer.startSession(atSourceTime: .zero)
        for index in 0..<frames {
            while !input.isReadyForMoreMediaData { try await Task.sleep(nanoseconds: 5_000_000) }
            let buffer = try pixelBuffer(for: index, size: size, background: background, pool: adaptor.pixelBufferPool)
            adaptor.append(buffer, withPresentationTime: CMTime(value: CMTimeValue(index), timescale: fps))
        }
        input.markAsFinished()
        await writer.finishWriting()
        if let error = writer.error { throw error }
        return url
    }

    private static func pixelBuffer(for index: Int, size: CGSize, background: UIColor, pool: CVPixelBufferPool?) throws -> CVPixelBuffer {
        var buffer: CVPixelBuffer?
        guard let pool, CVPixelBufferPoolCreatePixelBuffer(nil, pool, &buffer) == kCVReturnSuccess, let buffer else {
            throw CocoaError(.fileWriteUnknown)
        }
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        guard let context = CGContext(
            data: CVPixelBufferGetBaseAddress(buffer), width: Int(size.width), height: Int(size.height),
            bitsPerComponent: 8, bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
        ) else { throw CocoaError(.fileWriteUnknown) }
        context.setFillColor(background.cgColor)
        context.fill(CGRect(origin: .zero, size: size))
        context.setFillColor(UIColor.white.cgColor)
        context.fill(CGRect(x: index * blockWidth, y: 0, width: blockWidth, height: Int(size.height)))
        return buffer
    }

    /// Which frame a picture from `countingVideo` shows: the position of the white block, found on a row of pixels.
    static func blockIndex(in image: CGImage) -> Int? {
        guard let pixels = rgba(of: image) else { return nil }
        let row = image.height / 2
        for x in 0..<image.width {
            let offset = (row * image.width + x) * 4
            if pixels[offset] > 200, pixels[offset + 1] > 200, pixels[offset + 2] > 200 {
                // The picture may have been scaled; map back to the video's 8-pixel blocks.
                return Int((Double(x) / Double(image.width) * 320) / Double(blockWidth) + 0.25)
            }
        }
        return nil
    }

    /// The colour of the pixel at a fraction of the picture's width and height.
    static func pixel(in image: CGImage, x: Double, y: Double) -> (r: Int, g: Int, b: Int)? {
        guard let pixels = rgba(of: image) else { return nil }
        let column = min(image.width - 1, Int(Double(image.width) * x))
        let row = min(image.height - 1, Int(Double(image.height) * y))
        let offset = (row * image.width + column) * 4
        return (Int(pixels[offset]), Int(pixels[offset + 1]), Int(pixels[offset + 2]))
    }

    private static func rgba(of image: CGImage) -> [UInt8]? {
        var pixels = [UInt8](repeating: 0, count: image.width * image.height * 4)
        let drawn = pixels.withUnsafeMutableBytes { raw -> Bool in
            guard let context = CGContext(
                data: raw.baseAddress, width: image.width, height: image.height, bitsPerComponent: 8,
                bytesPerRow: image.width * 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
            return true
        }
        return drawn ? pixels : nil
    }
}
