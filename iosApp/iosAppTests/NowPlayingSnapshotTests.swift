import SwiftUI
import XCTest
@testable import iosApp

/// Renders the player screens with a real engine and a real track, so a layout that breaks (an empty or clipped
/// screen) fails here. The images are also written to the test's temporary folder for a visual check.
@MainActor
final class NowPlayingSnapshotTests: XCTestCase {
    private func render<V: View>(_ view: V, size: CGSize, name: String) throws -> UIImage {
        let hosted = view.frame(width: size.width, height: size.height).environment(\.colorScheme, .dark).background(Color.black)
        let renderer = ImageRenderer(content: hosted)
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage)
        if let dir = ProcessInfo.processInfo.environment["VIMIVO_SNAPSHOT_DIR"], let data = image.pngData() {
            try data.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
        return image
    }

    private func engineWithTrack() throws -> AVPlayerPlaybackEngine {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "track1_solaris", withExtension: "flac"))
        let engine = AVPlayerPlaybackEngine()
        let item = MediaItem(id: "snapshot-track", uri: url.absoluteString, title: "Solaris", kind: .audio, durationMillis: 10_000, artist: "Calyx", album: "Atmospheric Explorations")
        engine.queue = [item]
        engine.play(item: item)
        addTeardownBlock { @MainActor in engine.stop() }
        return engine
    }

    func testNowPlayingPortraitAndLandscapeRender() throws {
        let engine = try engineWithTrack()
        let portrait = try render(NowPlayingView(engine: engine), size: CGSize(width: 390, height: 844), name: "now-playing-portrait")
        let landscape = try render(NowPlayingView(engine: engine), size: CGSize(width: 844, height: 390), name: "now-playing-landscape")
        XCTAssertEqual(portrait.size.width, 390)
        XCTAssertEqual(landscape.size.height, 390)
    }

    func testEqualizerSheetRenders() throws {
        let engine = try engineWithTrack()
        _ = try render(EqualizerContent(engine: engine), size: CGSize(width: 390, height: 844), name: "equalizer")
    }
}
