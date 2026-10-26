import XCTest
import VimivoShared
@testable import iosApp

@MainActor
final class LyricsModelTests: XCTestCase {
    private var folder: URL!

    override func setUpWithError() throws {
        folder = FileManager.default.temporaryDirectory.appendingPathComponent("lyrics-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    }

    override func tearDown() { try? FileManager.default.removeItem(at: folder) }

    private func database() -> IOSDatabase { IOSDatabase(path: folder.appendingPathComponent("lib.db").path) }

    private func item(_ name: String) throws -> iosApp.MediaItem {
        let url = folder.appendingPathComponent(name)
        try Data().write(to: url)
        return iosApp.MediaItem(id: "id-\(name)", uri: url.absoluteString, title: name, kind: .audio, durationMillis: 1000)
    }

    func testASidecarBesideTheFileIsFoundAndThePositionPicksTheLine() throws {
        let song = try item("Song.mp3")
        try "[00:01.00]One\n[00:03.00]Two".write(to: folder.appendingPathComponent("Song.lrc"), atomically: true, encoding: .utf8)
        let model = LyricsModel(database: database())
        model.refresh(for: song)
        XCTAssertEqual(model.document?.lines.map(\.text), ["One", "Two"])
        XCTAssertEqual(model.activeIndex(positionMillis: 500), -1)
        XCTAssertEqual(model.activeIndex(positionMillis: 1_500), 0)
        XCTAssertEqual(model.activeIndex(positionMillis: 3_000), 1)
    }

    func testNoSidecarMeansNoLyrics() throws {
        let model = LyricsModel(database: database())
        model.refresh(for: try item("Alone.mp3"))
        XCTAssertNil(model.document)
    }

    func testALoadedFileIsRememberedForTheTrackAcrossModels() throws {
        let db = database()
        let song = try item("Other.mp3")
        let picked = folder.appendingPathComponent("picked.txt")
        try "[00:02.00]Hello".write(to: picked, atomically: true, encoding: .utf8)
        let first = LyricsModel(database: db)
        first.load(from: picked, for: song)
        XCTAssertEqual(first.document?.lines.first?.text, "Hello")

        let second = LyricsModel(database: db)
        second.refresh(for: song)
        XCTAssertEqual(second.document?.lines.first?.text, "Hello", "the choice survives a new model, as after a relaunch")
    }

    func testAFileWithNothingReadableIsRefusedWithAMessage() throws {
        let song = try item("Empty.mp3")
        let blank = folder.appendingPathComponent("blank.lrc")
        try "[ar:Nobody]\n\n".write(to: blank, atomically: true, encoding: .utf8)
        let model = LyricsModel(database: database())
        model.load(from: blank, for: song)
        XCTAssertNil(model.document)
        XCTAssertNotNil(model.errorMessage)
    }
}
