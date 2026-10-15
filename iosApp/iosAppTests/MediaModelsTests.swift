import XCTest
@testable import iosApp

final class MediaModelsTests: XCTestCase {
    func testMediaItemFormattedDuration() {
        let item1 = MediaItem(uri: "test1", title: "Song 1", kind: .audio, durationMillis: 65000)
        XCTAssertEqual(item1.formattedDuration, "1:05")

        let item2 = MediaItem(uri: "test2", title: "Movie 1", kind: .video, durationMillis: 3665000)
        XCTAssertEqual(item2.formattedDuration, "1:01:05")

        let item3 = MediaItem(uri: "test3", title: "Live Stream", kind: .stream, durationMillis: nil)
        XCTAssertEqual(item3.formattedDuration, "--:--")
    }

    func testPlaylistCreationAndAdd() {
        var playlist = Playlist(name: "Workout")
        XCTAssertEqual(playlist.name, "Workout")
        XCTAssertTrue(playlist.mediaIds.isEmpty)

        playlist.mediaIds.append("id-123")
        playlist.mediaIds.append("id-456")
        XCTAssertEqual(playlist.mediaIds.count, 2)
    }

    func testPlaybackStateProgress() {
        var state = PlaybackState(positionMillis: 30000, durationMillis: 60000)
        XCTAssertEqual(state.progress, 0.5, accuracy: 0.001)

        state.positionMillis = 90000
        XCTAssertEqual(state.progress, 1.0, accuracy: 0.001)

        state.durationMillis = 0
        XCTAssertEqual(state.progress, 0.0, accuracy: 0.001)
    }
}
