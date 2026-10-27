import XCTest
@testable import iosApp

/// The queue's rules (order, position, shuffle, repeat, bookmarks) are the shared module's and tested there; these
/// check the engine's use of them: edits keep the playing item, the queue is saved, bookmarks follow the shared rule.
@MainActor
final class QueueEngineTests: XCTestCase {
    private func items(_ count: Int) -> [MediaItem] {
        (1...count).map { MediaItem(id: "q-\(UUID().uuidString)-\($0)", uri: "file:///nonexistent/\($0).mp3", title: "Song \($0)", kind: .audio) }
    }

    func testRemovingAnEarlierItemKeepsTheCurrentOneAndTheCurrentOneCannotBeRemoved() {
        let engine = AVPlayerPlaybackEngine()
        let queue = items(5)
        engine.replaceQueue(queue, currentIndex: 2)
        engine.removeFromQueue(atOffsets: IndexSet(integer: 0))
        XCTAssertEqual(engine.queue.count, 4)
        XCTAssertEqual(engine.queue[engine.currentIndex].id, queue[2].id)
        engine.removeFromQueue(atOffsets: IndexSet(integer: engine.currentIndex))
        XCTAssertEqual(engine.queue.count, 4, "the item that is playing stays")
    }

    func testMovingItemsKeepsThePlayingOneCurrent() {
        let engine = AVPlayerPlaybackEngine()
        let queue = items(4)
        engine.replaceQueue(queue, currentIndex: 1)
        engine.moveInQueue(fromOffsets: IndexSet(integer: 0), toOffset: 4) // the first item to the end
        XCTAssertEqual(engine.queue.map(\.id), [queue[1].id, queue[2].id, queue[3].id, queue[0].id])
        XCTAssertEqual(engine.queue[engine.currentIndex].id, queue[1].id)
    }

    func testPlayNextQueuesRightBehindTheCurrentItem() {
        let engine = AVPlayerPlaybackEngine()
        let queue = items(3)
        engine.replaceQueue(queue, currentIndex: 1)
        let extra = items(1)[0]
        engine.playNextInQueue(extra)
        XCTAssertEqual(engine.queue.map(\.id), [queue[0].id, queue[1].id, extra.id, queue[2].id])
    }

    func testShuffleAndRepeatShowInTheState() {
        let engine = AVPlayerPlaybackEngine()
        engine.replaceQueue(items(5), currentIndex: 0)
        engine.toggleShuffle()
        XCTAssertTrue(engine.state.isShuffled)
        engine.cycleRepeatMode()
        XCTAssertEqual(engine.state.repeatMode, .all)
        engine.cycleRepeatMode()
        XCTAssertEqual(engine.state.repeatMode, .one)
        engine.cycleRepeatMode()
        XCTAssertEqual(engine.state.repeatMode, .off)
    }

    func testTheQueueIsSavedWhenItChanges() {
        let saved = IOSDatabase.shared.loadQueue()
        defer { IOSDatabase.shared.saveQueue(mediaIds: saved.mediaIds, currentIndex: saved.currentIndex) }
        let engine = AVPlayerPlaybackEngine()
        let queue = items(3)
        engine.replaceQueue(queue, currentIndex: 2)
        let stored = IOSDatabase.shared.loadQueue()
        XCTAssertEqual(stored.mediaIds, queue.map(\.id))
        XCTAssertEqual(stored.currentIndex, 2)
    }

    func testABookmarkNextToAnExistingOneIsNotAddedTwice() {
        let engine = AVPlayerPlaybackEngine()
        let song = items(1)[0]
        engine.replaceQueue([song], currentIndex: 0)
        engine.state.currentItem = song
        defer { engine.bookmarksForCurrentItem().forEach(engine.deleteBookmark) }
        engine.state.positionMillis = 30_000
        let first = engine.addBookmarkAtCurrentPosition()
        XCTAssertEqual(first?.name, "0:30", "named by its position, by the shared rule")
        engine.state.positionMillis = 30_400
        XCTAssertNil(engine.addBookmarkAtCurrentPosition(), "within a second of the first")
        engine.state.positionMillis = 90_000
        XCTAssertNotNil(engine.addBookmarkAtCurrentPosition())
        XCTAssertEqual(engine.bookmarksForCurrentItem().map(\.positionMillis), [30_000, 90_000])
    }
}
