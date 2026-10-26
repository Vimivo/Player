import XCTest
@testable import iosApp

/// The seek bar draws real peaks: a clip that is loud for two seconds and ten times quieter for two more must give tall
/// bars then short ones, cached by path, size and modified time and read again when the file changes.
final class WaveformLoaderTests: XCTestCase {
    private var folder: URL!

    override func setUpWithError() throws {
        folder = FileManager.default.temporaryDirectory.appendingPathComponent("waveform-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    }

    override func tearDown() { try? FileManager.default.removeItem(at: folder) }

    private func copyOfBundled(_ name: String, ext: String, as target: String) throws -> URL {
        let source = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: ext))
        let destination = folder.appendingPathComponent(target)
        try? FileManager.default.removeItem(at: destination)
        try FileManager.default.copyItem(at: source, to: destination)
        return destination
    }

    private func loader() -> WaveformLoader { WaveformLoader(cacheDirectory: folder.appendingPathComponent("cache")) }

    private func cacheFiles() -> [URL] {
        (try? FileManager.default.contentsOfDirectory(at: folder.appendingPathComponent("cache"), includingPropertiesForKeys: nil)) ?? []
    }

    func testALoudHalfThenAQuietHalfGivesTallBarsThenShortOnes() async throws {
        let url = try copyOfBundled("peaks_loud_then_quiet", ext: "flac", as: "clip.flac")
        let decoded = await loader().peaks(for: url)
        let peaks = try XCTUnwrap(decoded)
        XCTAssertEqual(peaks.count, 160)
        let loud = peaks.prefix(70).reduce(0, +) / 70
        let quiet = peaks.suffix(70).reduce(0, +) / 70
        XCTAssertGreaterThan(loud, 0.9)
        XCTAssertTrue((0.03...0.2).contains(quiet), "second half should be about a tenth, was \(quiet)")
        XCTAssertTrue(peaks.allSatisfy { $0 >= 0 && $0 <= 1 })
    }

    func testTheSecondCallComesFromTheCacheAndAChangedFileIsReadAgain() async throws {
        let url = try copyOfBundled("peaks_loud_then_quiet", ext: "flac", as: "song.flac")
        let loader = loader()
        let firstRead = await loader.peaks(for: url)
        let first = try XCTUnwrap(firstRead)
        XCTAssertEqual(cacheFiles().count, 1)
        let stamp = try XCTUnwrap(try cacheFiles()[0].resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate)
        let secondRead = await loader.peaks(for: url)
        let again = try XCTUnwrap(secondRead)
        XCTAssertEqual(first.count, again.count)
        let kept = try XCTUnwrap(try cacheFiles()[0].resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate)
        XCTAssertEqual(stamp, kept, "the cache file was not rewritten")

        // Replace it with other audio (different size and time): it needs its own peaks.
        _ = try copyOfBundled("track2_lunar", ext: "mp3", as: "song.flac")
        let thirdRead = await loader.peaks(for: url)
        let changed = try XCTUnwrap(thirdRead)
        XCTAssertEqual(cacheFiles().count, 2)
        XCTAssertNotEqual(first.map { Int($0 * 255) }, changed.map { Int($0 * 255) })
    }

    func testAFileThatIsNotAudioGivesNoPeaks() async throws {
        let junk = folder.appendingPathComponent("junk.mp3")
        try Data((0..<4096).map { UInt8(truncatingIfNeeded: $0 * 31) }).write(to: junk)
        let result = await loader().peaks(for: junk)
        XCTAssertNil(result)
    }

    func testOnlyLocalFilesAreRead() async {
        let result = await loader().peaks(for: URL(string: "https://example.com/a.mp3")!)
        XCTAssertNil(result)
    }
}
