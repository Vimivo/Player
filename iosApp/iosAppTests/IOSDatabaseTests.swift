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

        db.savePosition(mediaId: testId, positionMillis: 45000, completed: false)

        let history = db.getHistory()
        let found = history.first { $0.mediaId == testId }
        XCTAssertNotNil(found)
        XCTAssertEqual(found?.positionMillis, 45000)
        XCTAssertFalse(found?.completed ?? true)
    }

    /// Regression: starting playback used to overwrite the saved position with 0, so nothing could resume.
    func testPlayStartKeepsResumePositionAndCountsOnePlay() {
        let db = IOSDatabase.shared
        let testId = "resume-\(UUID().uuidString)"

        db.recordPlayStart(mediaId: testId)
        db.savePosition(mediaId: testId, positionMillis: 120_000, completed: false)
        db.savePosition(mediaId: testId, positionMillis: 130_000, completed: false)
        db.recordPlayStart(mediaId: testId)

        XCTAssertEqual(db.resumeState(mediaId: testId), ResumeState(positionMillis: 130_000, durationMillis: nil, completed: false))
        XCTAssertEqual(db.getHistory().first { $0.mediaId == testId }?.playCount, 2)
    }

    func testBookmarksPersistInPositionOrderAndDelete() {
        let db = IOSDatabase.shared
        let mediaId = "bm-\(UUID().uuidString)"
        let late = Bookmark(mediaId: mediaId, name: "Late", positionMillis: 90_000)
        let early = Bookmark(mediaId: mediaId, name: "Early", positionMillis: 10_000)
        db.addBookmark(late)
        db.addBookmark(early)

        XCTAssertEqual(db.bookmarks(mediaId: mediaId).map(\.name), ["Early", "Late"])
        db.deleteBookmark(id: early.id)
        XCTAssertEqual(db.bookmarks(mediaId: mediaId).map(\.id), [late.id])
    }

    func testQueueRoundTripsAndClampsIndex() {
        let db = IOSDatabase.shared
        let saved = db.loadQueue()
        defer { db.saveQueue(mediaIds: saved.mediaIds, currentIndex: saved.currentIndex) }

        db.saveQueue(mediaIds: ["a", "b", "c"], currentIndex: 1)
        XCTAssertEqual(db.loadQueue().mediaIds, ["a", "b", "c"])
        XCTAssertEqual(db.loadQueue().currentIndex, 1)

        db.saveQueue(mediaIds: ["a"], currentIndex: 5)
        XCTAssertEqual(db.loadQueue().currentIndex, 0)

        db.saveQueue(mediaIds: [], currentIndex: 0)
        XCTAssertTrue(db.loadQueue().mediaIds.isEmpty)
    }

    func testSpeedPresetsComeFromSharedModuleAndReachFourTimes() {
        XCTAssertEqual(PlaybackPreferences.speedPresets.first, 0.25)
        XCTAssertEqual(PlaybackPreferences.speedPresets.last, 4)
        XCTAssertEqual(PlaybackPreferences.speedRange.upperBound, 4)
    }

    func testResumeStartPositionUsesSharedPolicy() {
        let defaults = UserDefaults(suiteName: "resume-\(UUID().uuidString)")!
        let video = MediaItem(id: "v", uri: "file:///v.mp4", title: "V", kind: .video, durationMillis: 600_000)
        let song = MediaItem(id: "s", uri: "file:///s.mp3", title: "S", kind: .audio, durationMillis: 240_000)
        let midway = ResumeState(positionMillis: 120_000, durationMillis: nil, completed: false)

        XCTAssertEqual(PlaybackPreferences.startPositionMillis(for: video, saved: midway, defaults: defaults), 120_000)
        XCTAssertEqual(PlaybackPreferences.startPositionMillis(for: song, saved: midway, defaults: defaults), 0)
        XCTAssertEqual(PlaybackPreferences.startPositionMillis(for: video, saved: nil, defaults: defaults), 0)

        defaults.set(true, forKey: PlaybackPreferences.resumeSongsKey)
        XCTAssertEqual(PlaybackPreferences.startPositionMillis(for: song, saved: midway, defaults: defaults), 120_000)

        defaults.set(false, forKey: PlaybackPreferences.resumeVideoKey)
        XCTAssertEqual(PlaybackPreferences.startPositionMillis(for: video, saved: midway, defaults: defaults), 0)
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
