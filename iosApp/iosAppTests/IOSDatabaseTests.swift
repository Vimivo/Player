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

    func testVideoMetadataRoundTripAndBackfill() {
        let db = IOSDatabase.shared
        let testId = "video-\(UUID().uuidString)"
        let item = MediaItem(id: testId, uri: "file:///test/clip.mp4", title: "Clip", kind: .video, durationMillis: 5000)
        db.saveMediaItem(item)

        let saved = db.getAllMediaItems().first { $0.id == testId }
        XCTAssertNil(saved?.width)
        XCTAssertNotNil(saved?.dateAddedMillis)

        db.updateVideoMetadata(id: testId, width: 1920, height: 1080, sizeBytes: 2_048)
        let updated = db.getAllMediaItems().first { $0.id == testId }
        XCTAssertEqual(updated?.width, 1920)
        XCTAssertEqual(updated?.height, 1080)
        XCTAssertEqual(updated?.sizeBytes, 2_048)
        XCTAssertEqual(updated?.dateAddedMillis, saved?.dateAddedMillis)

        // A nil value must not erase metadata that is already stored.
        db.updateVideoMetadata(id: testId, width: nil, height: nil, sizeBytes: nil)
        XCTAssertEqual(db.getAllMediaItems().first { $0.id == testId }?.width, 1920)

        db.deleteMediaItem(id: testId)
    }
}
