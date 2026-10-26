import XCTest
@testable import iosApp

/// Q1: lists play on in order, shuffle is reversible, and unplayable items never stop the queue.
final class ContinuousPlaybackTests: XCTestCase {
    private func item(_ name: String, kind: MediaKind = .video, folder: String = "/docs") -> MediaItem {
        MediaItem(id: name, uri: URL(fileURLWithPath: "\(folder)/\(name)").absoluteString, title: name, kind: kind)
    }

    func testOpenedFileQueuesSameKindSiblingsInNaturalOrder() {
        let opened = item("Show Ep 2.mp4")
        let library = [item("Show Ep 10.mp4"), opened, item("Show Ep 1.mp4"), item("theme.mp3", kind: .audio),
                       item("Other Ep 3.mp4", folder: "/elsewhere")]

        let siblings = AppRouter.folderSiblings(of: opened, among: library)

        XCTAssertEqual(siblings.map(\.title), ["Show Ep 1.mp4", "Show Ep 2.mp4", "Show Ep 10.mp4"])
    }

    func testUnshuffleRestoresOriginalOrderAndKeepsCurrentItem() {
        let engine = AVPlayerPlaybackEngine()
        let items = (1...6).map { item("missing-\($0).mp4", folder: "/nonexistent") }
        engine.replaceQueue(items, currentIndex: 2)

        engine.toggleShuffle()
        XCTAssertEqual(engine.queue.first?.id, items[2].id)
        engine.toggleShuffle()

        XCTAssertEqual(engine.queue.map(\.id), items.map(\.id))
        XCTAssertEqual(engine.currentIndex, 2)
    }

    /// Regression: a missing file used to stop the whole queue; now it is skipped, and a queue in which every
    /// item fails stops on the last one instead of looping.
    func testMissingFilesAreSkippedAndAnAllFailingQueueStops() {
        let engine = AVPlayerPlaybackEngine()
        let items = (1...3).map { item("gone-\($0).mp4", folder: "/nonexistent") }

        engine.playQueue(items: items, startIndex: 0)

        XCTAssertEqual(engine.currentIndex, 2)
        XCTAssertEqual(engine.state.status, .error)
        XCTAssertEqual(engine.skippedItemTitle, "gone-2.mp4")
    }
}
