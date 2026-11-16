import AVFoundation
import CoreImage
import UIKit
import VimivoCoreRust

public struct CropRect: Equatable {
    public let x: Int
    public let y: Int
    public let width: Int
    public let height: Int

    public init(x: Int, y: Int, width: Int, height: Int) {
        self.x = x
        self.y = y
        self.width = width
        self.height = height
    }

    init(ffi: FfiCropRect) {
        self.x = Int(ffi.x)
        self.y = Int(ffi.y)
        self.width = Int(ffi.width)
        self.height = Int(ffi.height)
    }

    var ffi: FfiCropRect {
        FfiCropRect(x: Int32(x), y: Int32(y), width: Int32(width), height: Int32(height))
    }
}

public enum CropPreset: String, CaseIterable, Identifiable {
    case none, auto, ratio16x9, ratio21x9, ratio4x3, ratio1x1

    public var id: String { rawValue }
    public var name: String { rawValue }
    public static var ratio11: CropPreset { .ratio1x1 }
    public static var auto_: CropPreset { .auto }
    public static var entries: [CropPreset] { allCases }

    public var label: String {
        switch self {
        case .none: return "Off"
        case .auto: return "Auto (crop black bars)"
        case .ratio16x9: return "16:9"
        case .ratio21x9: return "21:9"
        case .ratio4x3: return "4:3"
        case .ratio1x1: return "1:1"
        }
    }

    public var ratio: Double? {
        switch self {
        case .none, .auto: return nil
        case .ratio16x9: return 16.0 / 9.0
        case .ratio21x9: return 21.0 / 9.0
        case .ratio4x3: return 4.0 / 3.0
        case .ratio1x1: return 1.0
        }
    }
}

/// Colour and crop choices for the picture.
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

/// Applies `PictureSettings` to every frame with Core Image through an `AVVideoComposition`.
enum PictureComposition {
    /// One frame: crop and scale to fill the frame, then colour.
    static func apply(_ settings: PictureSettings, to image: CIImage, renderSize: CGSize) -> CIImage {
        var output = image
        if let crop = settings.crop {
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
        if preset == .auto {
            settings.crop = await detectBlackBars()
        } else if let ratio = preset.ratio, let size = player.currentItem?.presentationSize, size.width > 0 {
            settings.crop = calculateCropCentered(
                sourceWidth: Int32(size.width), sourceHeight: Int32(size.height), ratio: ratio
            ).map(CropRect.init(ffi:))
        }
        await applyPicture(settings)
    }

    /// Samples four frames, finds the black bars using Rust's BlackBarDetector and crops to the union.
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
        guard picture.width > 0, let union = unionCropBounds(bounds: bounds.map(\.ffi)) else { return nil }
        return calculateCropFromContent(
            sourceWidth: Int32(picture.width), sourceHeight: Int32(picture.height), content: union
        ).map(CropRect.init(ffi:))
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
        return detectBlackBarBounds(width: Int32(image.width), height: Int32(image.height), luma: Data(luma)).map(CropRect.init(ffi:))
    }
}
