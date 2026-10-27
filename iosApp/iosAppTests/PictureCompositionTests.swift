import AVFoundation
import CoreImage
import UIKit
import VimivoShared
import XCTest
@testable import iosApp

/// V1 #12 and #14 on iOS: the colour controls and crop change the picture by the expected amount (measured on the
/// pixels, per frame and through a real player), and black bars are found with the shared detector.
@MainActor
final class PictureCompositionTests: XCTestCase {
    private let context = CIContext()
    private let size = CGSize(width: 64, height: 36)

    private func solid(_ color: UIColor, size: CGSize? = nil) -> CIImage {
        CIImage(color: CIColor(color: color)).cropped(to: CGRect(origin: .zero, size: size ?? self.size))
    }

    private func pixel(_ image: CIImage, x: Int, y: Int) -> (r: Int, g: Int, b: Int) {
        var bytes = [UInt8](repeating: 0, count: 4)
        context.render(
            image, toBitmap: &bytes, rowBytes: 4, bounds: CGRect(x: x, y: y, width: 1, height: 1),
            format: .RGBA8, colorSpace: CGColorSpaceCreateDeviceRGB()
        )
        return (Int(bytes[0]), Int(bytes[1]), Int(bytes[2]))
    }

    private func settings(_ edit: (inout PictureSettings) -> Void) -> PictureSettings {
        var value = PictureSettings()
        edit(&value)
        return value
    }

    // MARK: Per frame

    func testNeutralSettingsLeaveThePictureAlone() {
        XCTAssertTrue(PictureSettings().isNeutral)
        let out = PictureComposition.apply(PictureSettings(), to: solid(UIColor(white: 0.5, alpha: 1)), renderSize: size)
        XCTAssertEqual(pixel(out, x: 5, y: 5).r, 128, accuracy: 2)
    }

    func testBrightnessAddsAndSubtracts() {
        let gray = solid(UIColor(white: 0.5, alpha: 1))
        let brighter = pixel(PictureComposition.apply(settings { $0.brightness = 40 }, to: gray, renderSize: size), x: 5, y: 5)
        let darker = pixel(PictureComposition.apply(settings { $0.brightness = -40 }, to: gray, renderSize: size), x: 5, y: 5)
        XCTAssertGreaterThan(brighter.r, 200)
        XCTAssertLessThan(darker.r, 60)
    }

    func testContrastSpreadsDarkAndLightAwayFromMiddleGray() {
        let dark = solid(UIColor(white: 0.3, alpha: 1))
        let flat = pixel(dark, x: 1, y: 1).r
        let more = pixel(PictureComposition.apply(settings { $0.contrast = 60 }, to: dark, renderSize: size), x: 5, y: 5).r
        let less = pixel(PictureComposition.apply(settings { $0.contrast = -60 }, to: dark, renderSize: size), x: 5, y: 5).r
        XCTAssertLessThan(more, flat)
        XCTAssertGreaterThan(less, flat)
    }

    func testFullDesaturationMakesGrayAndTheNeutralPixelKeepsItsColour() {
        let red = solid(.red)
        let gray = pixel(PictureComposition.apply(settings { $0.saturation = -100 }, to: red, renderSize: size), x: 5, y: 5)
        XCTAssertLessThan(abs(gray.r - gray.g), 8)
        XCTAssertLessThan(abs(gray.g - gray.b), 8)
        XCTAssertGreaterThan(pixel(red, x: 5, y: 5).r, 240)
        XCTAssertLessThan(pixel(red, x: 5, y: 5).g, 15)
    }

    func testAHalfTurnOfHueTurnsRedToCyan() {
        let turned = pixel(PictureComposition.apply(settings { $0.hue = 100 }, to: solid(.red), renderSize: size), x: 5, y: 5)
        XCTAssertLessThan(turned.r, 80)
        XCTAssertGreaterThan(turned.g, 150)
        XCTAssertGreaterThan(turned.b, 150)
    }

    func testGammaLightensOrDarkensTheMiddleTonesButNotBlackOrWhite() {
        let gray = solid(UIColor(white: 0.5, alpha: 1))
        let lighter = pixel(PictureComposition.apply(settings { $0.gamma = 50 }, to: gray, renderSize: size), x: 5, y: 5).r
        let darker = pixel(PictureComposition.apply(settings { $0.gamma = -50 }, to: gray, renderSize: size), x: 5, y: 5).r
        XCTAssertGreaterThan(lighter, 150)
        XCTAssertLessThan(darker, 110)
        XCTAssertGreaterThan(pixel(PictureComposition.apply(settings { $0.gamma = 50 }, to: solid(.white), renderSize: size), x: 5, y: 5).r, 250)
    }

    func testACropKeepsTheChosenPartAndFillsTheFrame() {
        // Left half red, right half blue; keeping the right half must leave only blue, scaled to fill.
        let picture = solid(.red).composited(over: CIImage(color: .black).cropped(to: CGRect(origin: .zero, size: size)))
            .cropped(to: CGRect(x: 0, y: 0, width: 32, height: 36))
            .composited(over: solid(.blue))
        let crop = CropRect(x: 32, y: 0, width: 32, height: 36)
        let out = PictureComposition.apply(settings { $0.crop = crop }, to: picture, renderSize: size)
        for x in [2, 30, 61] {
            let color = pixel(out, x: x, y: 18)
            XCTAssertGreaterThan(color.b, 200, "x \(x)")
            XCTAssertLessThan(color.r, 40, "x \(x)")
        }
    }

    // MARK: Through a real player

    private var layers: [AVPlayerLayer] = []

    private func play(_ url: URL) async -> AVPlayerPlaybackEngine {
        let engine = AVPlayerPlaybackEngine()
        let layer = AVPlayerLayer(player: engine.player)
        layer.frame = CGRect(x: 0, y: 0, width: 320, height: 180)
        layers.append(layer)
        engine.play(item: MediaItem(id: "picture-\(UUID().uuidString)", uri: url.absoluteString, title: "Clip", kind: .video))
        for _ in 0..<500 where engine.player.currentItem?.status != .readyToPlay { try? await Task.sleep(nanoseconds: 20_000_000) }
        engine.pause()
        engine.seek(toMillis: 300)
        try? await Task.sleep(nanoseconds: 600_000_000)
        return engine
    }

    func testTheColourControlsChangeWhatTheSnapshotShows() async throws {
        let engine = await play(try await TestMedia.countingVideo(frames: 20, background: .red))
        let before = try await engine.captureFrame()
        XCTAssertGreaterThan(try XCTUnwrap(TestMedia.pixel(in: before, x: 0.9, y: 0.5)).r, 200)

        var gray = PictureSettings()
        gray.saturation = -100
        await engine.applyPicture(gray)
        let after = try await engine.captureFrame()
        let pixel = try XCTUnwrap(TestMedia.pixel(in: after, x: 0.9, y: 0.5))
        XCTAssertLessThan(abs(pixel.r - pixel.g), 12, "the red picture is gray now: \(pixel)")

        await engine.applyPicture(PictureSettings())
        let restored = try await engine.captureFrame()
        XCTAssertGreaterThan(try XCTUnwrap(TestMedia.pixel(in: restored, x: 0.9, y: 0.5)).r, 200, "neutral settings restore it")
    }

    func testACropPresetCutsTheSideBarsOfAWidePicture() async throws {
        // Red left half, blue right half; a 1:1 crop of the 16:9 picture keeps the middle, so both colours stay but
        // the shown part is narrower: the left edge of the snapshot is red and the far right is blue either way, so
        // check the proportions instead: the colour boundary stays in the middle while the block (frame 3) moves out.
        let engine = await play(try await TestMedia.countingVideo(frames: 20, background: .red, rightHalf: .blue))
        await engine.chooseCrop(CropPreset.ratio11)
        XCTAssertEqual(engine.pictureSettings.crop, CropRect(x: 70, y: 0, width: 180, height: 180))
        let image = try await engine.captureFrame()
        XCTAssertEqual(image.width, 320)
        // Frame 3's white block sat at x 24...32, which lies outside the kept 70...250 range: it is gone.
        XCTAssertNil(TestMedia.blockIndex(in: image), "the block is cropped away")
        await engine.chooseCrop(CropPreset.none)
        XCTAssertNil(engine.pictureSettings.crop)
        let restored = try await engine.captureFrame()
        XCTAssertEqual(TestMedia.blockIndex(in: restored), 3)
    }

    func testAutoCropFindsTheBlackBarsOfALetterboxedClip() async throws {
        let engine = await play(try await TestMedia.countingVideo(frames: 20, background: .red, barRows: 30))
        await engine.chooseCrop(CropPreset.auto_)
        let crop = try XCTUnwrap(engine.pictureSettings.crop, "bars should be found")
        XCTAssertEqual(Int(crop.y), 30, accuracy: 4)
        XCTAssertEqual(Int(crop.height), 120, accuracy: 8)
        XCTAssertEqual(Int(crop.width), 320, accuracy: 4)
    }

    func testAutoCropOnAClipWithoutBarsDoesNothing() async throws {
        let engine = await play(try await TestMedia.countingVideo(frames: 20, background: .red))
        await engine.chooseCrop(CropPreset.auto_)
        XCTAssertNil(engine.pictureSettings.crop)
    }
}
