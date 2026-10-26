import AVFoundation
import CoreImage
import UIKit
import VimivoShared

/// Colour and crop choices for the picture (V1 #12, #14). The numbers use the same -100…100 scale as the other
/// platforms; `crop` is the rectangle of the decoded picture to keep, and `cropPreset` is the choice that produced it.
public struct PictureSettings: Equatable {
    public var brightness = 0
    public var contrast = 0
    public var saturation = 0
    public var gamma = 0
    public var hue = 0
    public var cropPreset: CropPreset = .none
    public var crop: CropRect?

    public init() {}

    public var isNeutral: Bool {
        brightness == 0 && contrast == 0 && saturation == 0 && gamma == 0 && hue == 0 && crop == nil
    }
}

/// Applies `PictureSettings` to every frame with Core Image through an `AVVideoComposition`, which AVPlayer, the
/// snapshot generator and Picture in Picture all honour.
enum PictureComposition {
    /// One frame: crop and scale to fill the frame, then colour. Pure, so it is tested without a player.
    static func apply(_ settings: PictureSettings, to image: CIImage, renderSize: CGSize) -> CIImage {
        var output = image
        if let crop = settings.crop {
            // CropRect counts from the top-left of the picture; Core Image counts from the bottom-left.
            let rect = CGRect(
                x: CGFloat(crop.x), y: image.extent.height - CGFloat(crop.y + crop.height),
                width: CGFloat(crop.width), height: CGFloat(crop.height)
            )
            let scale = max(renderSize.width / rect.width, renderSize.height / rect.height)
            let offsetX = (renderSize.width - rect.width * scale) / 2
            let offsetY = (renderSize.height - rect.height * scale) / 2
            output = output.cropped(to: rect).transformed(
                by: CGAffineTransform(translationX: -rect.minX, y: -rect.minY)
                    .concatenating(CGAffineTransform(scaleX: scale, y: scale))
                    .concatenating(CGAffineTransform(translationX: offsetX, y: offsetY))
            )
        }
        if settings.brightness != 0 || settings.contrast != 0 || settings.saturation != 0 {
            output = output.applyingFilter("CIColorControls", parameters: [
                kCIInputBrightnessKey: Double(settings.brightness) / 100,
                kCIInputContrastKey: 1 + Double(settings.contrast) / 100,
                kCIInputSaturationKey: 1 + Double(settings.saturation) / 100,
            ])
        }
        if settings.hue != 0 {
            output = output.applyingFilter("CIHueAdjust", parameters: [kCIInputAngleKey: Double(settings.hue) / 100 * Double.pi])
        }
        if settings.gamma != 0 {
            // Positive values lighten the middle tones, as on the other platforms.
            output = output.applyingFilter("CIGammaAdjust", parameters: ["inputPower": pow(2.0, -Double(settings.gamma) / 50)])
        }
        return output.cropped(to: CGRect(origin: .zero, size: renderSize))
    }

    /// The composition for [asset], or nil when [settings] change nothing.
    static func make(for asset: AVAsset, settings: PictureSettings) async throws -> AVVideoComposition? {
        guard !settings.isNeutral else { return nil }
        let context = CIContext()
        return try await AVVideoComposition(asset: asset) { request in
            let image = apply(settings, to: request.sourceImage.clampedToExtent(), renderSize: request.renderSize)
            request.finish(with: image, context: context)
        }
    }
}

extension AVPlayerPlaybackEngine {
    /// Applies [settings] to the picture now and to every item that loads while they are set.
    public func applyPicture(_ settings: PictureSettings) async {
        pictureSettings = settings
        guard let item = player.currentItem else { return }
        item.videoComposition = try? await PictureComposition.make(for: item.asset, settings: settings)
    }

    /// Crop to a preset's aspect ratio, or (Auto) to the picture without black bars; None removes the crop.
    public func chooseCrop(_ preset: CropPreset) async {
        var settings = pictureSettings
        settings.cropPreset = preset
        settings.crop = nil
        if preset == CropPreset.auto_ {
            settings.crop = await detectBlackBars()
        } else if let ratio = preset.ratio?.doubleValue, let size = player.currentItem?.presentationSize, size.width > 0 {
            settings.crop = VideoCropMath.shared.centered(
                sourceWidth: Int32(size.width), sourceHeight: Int32(size.height), ratio: ratio
            )
        }
        await applyPicture(settings)
    }

    /// Samples four frames (as the desktop does), finds the bars of each with the shared detector and crops to the
    /// union, or returns nil when there are no bars worth removing.
    func detectBlackBars() async -> CropRect? {
        guard let item = player.currentItem, let seconds = Optional(CMTimeGetSeconds(item.duration)), seconds > 0 else { return nil }
        let generator = AVAssetImageGenerator(asset: item.asset)
        generator.appliesPreferredTrackTransform = true
        var bounds: [CropRect] = []
        var picture = CGSize.zero
        for fraction in [0.2, 0.4, 0.6, 0.8] {
            guard let image = try? await generator.image(at: CMTime(seconds: seconds * fraction, preferredTimescale: 600)).image else { continue }
            picture = CGSize(width: image.width, height: image.height)
            if let found = Self.contentBounds(of: image) { bounds.append(found) }
        }
        guard picture.width > 0, let union = BlackBarDetector.shared.union(bounds: bounds) else { return nil }
        return VideoCropMath.shared.fromContent(
            sourceWidth: Int32(picture.width), sourceHeight: Int32(picture.height), content: union
        )
    }

    private static func contentBounds(of image: CGImage) -> CropRect? {
        var luma = [UInt8](repeating: 0, count: image.width * image.height)
        let drawn = luma.withUnsafeMutableBytes { raw -> Bool in
            guard let context = CGContext(
                data: raw.baseAddress, width: image.width, height: image.height, bitsPerComponent: 8,
                bytesPerRow: image.width, space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue
            ) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
            return true
        }
        guard drawn else { return nil }
        let bytes = KotlinByteArray(size: Int32(luma.count))
        for (index, value) in luma.enumerated() { bytes.set(index: Int32(index), value: Int8(bitPattern: value)) }
        return BlackBarDetector.shared.contentBounds(width: Int32(image.width), height: Int32(image.height), luma: bytes)
    }
}
