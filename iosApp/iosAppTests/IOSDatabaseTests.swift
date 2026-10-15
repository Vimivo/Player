import XCTest
@testable import iosApp

final class IOSDatabaseTests: XCTestCase {
    func testMediaItemSaveAndRetrieve() {
        let db = IOSDatabase.shared
        let testId = "test-item-\(UUID().uuidString)"
        let item = MediaItem(id: testId, uri: "file:///test/song.mp3", title: "Test Song", kind: .audio, durationMillis: 120000, artist: "Test Artist", album: "Test Album")

        db.saveMediaItem(item)

        let all = db.getAllMediaItems()
        let found = all.first { $0.id == testId }
        XCTAssertNotNil(found)
        XCTAssertEqual(found?.title, "Test Song")
        XCTAssertEqual(found?.artist, "Test Artist")

        db.deleteMediaItem(id: testId)
        let afterDelete = db.getAllMediaItems().first { $0.id == testId }
        XCTAssertNil(afterDelete)
    }

    func testFavoritesPersistence() {
        let db = IOSDatabase.shared
        let testId = "fav-\(UUID().uuidString)"

        db.setFavorite(mediaId: testId, isFavorite: true)
        XCTAssertTrue(db.getFavorites().contains(testId))

        db.setFavorite(mediaId: testId, isFavorite: false)
        XCTAssertFalse(db.getFavorites().contains(testId))
    }

    func testPlaylistPersistence() {
        let db = IOSDatabase.shared
        let testId = "pl-\(UUID().uuidString)"
        let pl = Playlist(id: testId, name: "Chill Tracks", mediaIds: ["m1", "m2", "m3"])

        db.savePlaylist(pl)

        let all = db.getAllPlaylists()
        let found = all.first { $0.id == testId }
        XCTAssertNotNil(found)
        XCTAssertEqual(found?.name, "Chill Tracks")
        XCTAssertEqual(found?.mediaIds, ["m1", "m2", "m3"])

        db.deletePlaylist(id: testId)
        let afterDelete = db.getAllPlaylists().first { $0.id == testId }
        XCTAssertNil(afterDelete)
    }

    func testPlaybackHistoryRecord() {
        let db = IOSDatabase.shared
        let testId = "hist-\(UUID().uuidString)"

        db.recordPlayback(mediaId: testId, positionMillis: 45000, completed: false)

        let history = db.getHistory()
        let found = history.first { $0.mediaId == testId }
        XCTAssertNotNil(found)
        XCTAssertEqual(found?.positionMillis, 45000)
        XCTAssertFalse(found?.completed ?? true)
    }
}
