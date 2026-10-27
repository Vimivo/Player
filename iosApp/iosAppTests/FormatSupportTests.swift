import AVFoundation
import XCTest

/// Which containers and codecs AVFoundation plays on iOS, measured on real files, and what happens with damaged ones.
/// The formats it cannot open (for example Matroska) are the reason the libmpv fallback engine is planned (ADR-005).
final class FormatSupportTests: XCTestCase {
    private func bundled(_ name: String) throws -> URL {
        let parts = name.split(separator: ".", maxSplits: 1).map(String.init)
        return try XCTUnwrap(Bundle(for: Self.self).url(forResource: parts[0], withExtension: parts[1]), "missing \(name)")
    }

    private func describe(_ url: URL) async -> (playable: Bool, video: Int, audio: Int, seconds: Double) {
        let asset = AVURLAsset(url: url)
        let playable = (try? await asset.load(.isPlayable)) ?? false
        let video = (try? await asset.loadTracks(withMediaType: .video).count) ?? 0
        let audio = (try? await asset.loadTracks(withMediaType: .audio).count) ?? 0
        let seconds = (try? await asset.load(.duration).seconds) ?? 0
        return (playable, video, audio, seconds)
    }

    func testSupportedContainersAndCodecsOpenWithTheirTracks() async throws {
        for name in ["track1_solaris.flac", "track2_lunar.mp3"] {
            let info = await describe(try bundled(name))
            XCTAssertTrue(info.playable, name)
            XCTAssertEqual(info.audio, 1, name)
            XCTAssertGreaterThan(info.seconds, 1, name)
        }
        for name in ["video_landscape.mp4", "hdr10_tiny.mp4"] {
            let info = await describe(try bundled(name))
            XCTAssertTrue(info.playable, name)
            XCTAssertEqual(info.video, 1, name)
            XCTAssertGreaterThan(info.seconds, 0.5, name)
        }
    }

    func testHdr10ClipIsRecognisedAsHighDynamicRange() async throws {
        let asset = AVURLAsset(url: try bundled("hdr10_tiny.mp4"))
        let tracks = try await asset.loadTracks(withMediaType: .video)
        let track = try XCTUnwrap(tracks.first)
        let characteristics = try await track.load(.mediaCharacteristics)
        XCTAssertTrue(characteristics.contains(.containsHDRVideo), "characteristics: \(characteristics)")
    }

    func testMatroskaIsNotPlayableWithoutTheFallbackEngine() async throws {
        let info = await describe(try bundled("video_sample.mkv"))
        XCTAssertFalse(info.playable)
    }

    func testDamagedFilesFailCleanlyInsteadOfCrashing() async throws {
        let source = try Data(contentsOf: try bundled("video_landscape.mp4"))
        let dir = FileManager.default.temporaryDirectory
        let truncated = dir.appendingPathComponent("truncated-\(UUID().uuidString).mp4")
        let garbage = dir.appendingPathComponent("garbage-\(UUID().uuidString).mp4")
        let empty = dir.appendingPathComponent("empty-\(UUID().uuidString).mp3")
        try source.prefix(source.count / 10).write(to: truncated)
        try Data((0..<4096).map { UInt8(($0 &* 31) & 0xFF) }).write(to: garbage)
        try Data().write(to: empty)
        defer { [truncated, garbage, empty].forEach { try? FileManager.default.removeItem(at: $0) } }

        for url in [truncated, garbage, empty] {
            let info = await describe(url)
            XCTAssertFalse(info.playable, url.lastPathComponent)
            XCTAssertEqual(info.video + info.audio, 0, url.lastPathComponent)
        }
    }
}
