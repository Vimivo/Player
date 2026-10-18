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

    func testWidgetPlaybackSnapshotRoundTripsUnicodeMetadata() throws {
        let snapshot = WidgetPlaybackSnapshot(
            mediaID: "song-1",
            title: "বাংলা গান — Aurora",
            artist: "Vimivo Artist",
            artworkPath: "/tmp/artwork.jpg",
            isPlaying: true,
            positionSeconds: 12.5,
            durationSeconds: 180,
            updatedAt: Date(timeIntervalSince1970: 1234)
        )
        let data = try JSONEncoder().encode(snapshot)
        let decoded = try JSONDecoder().decode(WidgetPlaybackSnapshot.self, from: data)
        XCTAssertEqual(decoded, snapshot)
    }

#if canImport(ActivityKit)
    func testPlaybackActivityAttributesRoundTrip() throws {
        if #available(iOS 16.1, *) {
            let attrs = VimivoPlaybackActivityAttributes(mediaID: "media-99")
            XCTAssertEqual(attrs.mediaID, "media-99")

            let state = VimivoPlaybackActivityAttributes.ContentState(
                title: "আমার সোনার বাংলা",
                artist: "Vimivo KMP",
                isPlaying: true,
                positionSeconds: 45.0,
                durationSeconds: 190.0,
                updatedAt: Date(timeIntervalSince1970: 5678)
            )
            XCTAssertEqual(state.title, "আমার সোনার বাংলা")
            XCTAssertEqual(state.positionSeconds, 45.0)

            let data = try JSONEncoder().encode(state)
            let decoded = try JSONDecoder().decode(VimivoPlaybackActivityAttributes.ContentState.self, from: data)
            XCTAssertEqual(decoded, state)
        }
    }
#endif

    func testResolutionLabelsUseShortSide() {
        XCTAssertEqual(MediaFormatting.resolutionLabel(width: 3840, height: 2160), "4K")
        XCTAssertEqual(MediaFormatting.resolutionLabel(width: 2560, height: 1440), "1440p")
        XCTAssertEqual(MediaFormatting.resolutionLabel(width: 1080, height: 1920), "1080p")
        XCTAssertEqual(MediaFormatting.resolutionLabel(width: 1280, height: 720), "720p")
        XCTAssertEqual(MediaFormatting.resolutionLabel(width: 640, height: 480), "480p")
    }

    func testByteCountClockAndSpeedFormatting() {
        XCTAssertEqual(MediaFormatting.byteCount(512), "512 B")
        XCTAssertEqual(MediaFormatting.byteCount(217_481), "212 KB")
        XCTAssertEqual(MediaFormatting.byteCount(43_971_644), "41.9 MB")
        XCTAssertEqual(MediaFormatting.clock(millis: 65_000), "1:05")
        XCTAssertEqual(MediaFormatting.clock(millis: 3_723_000), "1:02:03")
        XCTAssertEqual(MediaFormatting.clock(millis: -500), "0:00")
        XCTAssertEqual(MediaFormatting.speed(1), "1.0×")
        XCTAssertEqual(MediaFormatting.speed(1.25), "1.25×")
        XCTAssertEqual(MediaFormatting.speed(0.5), "0.5×")
    }

    func testDetailLineMatchesAndroidRows() {
        let video = MediaItem(uri: "v", title: "V", kind: .video, durationMillis: 120_000, width: 1280, height: 720, sizeBytes: 43_971_644)
        XCTAssertEqual(video.detailLine, "2:00 · 720p · 41.9 MB")
        let bare = MediaItem(uri: "v2", title: "V2", kind: .video)
        XCTAssertEqual(bare.detailLine, "")
        let song = MediaItem(uri: "a", title: "A", kind: .audio, artist: "Calyx", album: "Lights")
        XCTAssertEqual(song.detailLine, "Calyx · Lights")
    }

    @MainActor
    func testDoubleTapSidesAccumulateAndCenterToggles() {
        let model = VideoPlayerModel(engine: AVPlayerPlaybackEngine.shared)
        model.handleDoubleTap(x: 300, width: 400)
        XCTAssertEqual(model.doubleTapSide, 1)
        XCTAssertEqual(model.doubleTapSeconds, 10)
        XCTAssertFalse(model.controlsVisible)

        // A further tap on the same side while the window is open keeps adding time.
        model.handleSingleTap(x: 350, width: 400)
        XCTAssertEqual(model.doubleTapSeconds, 20)

        // Switching sides restarts the count.
        model.handleDoubleTap(x: 20, width: 400)
        XCTAssertEqual(model.doubleTapSide, -1)
        XCTAssertEqual(model.doubleTapSeconds, 10)
        model.tearDown()
    }

    @MainActor
    func testLockedPlayerOnlyTogglesUnlockHint() {
        let model = VideoPlayerModel(engine: AVPlayerPlaybackEngine.shared)
        model.lockControls()
        XCTAssertTrue(model.locked)
        XCTAssertFalse(model.controlsVisible)
        model.handleSingleTap(x: 200, width: 400)
        XCTAssertFalse(model.lockHintVisible)
        XCTAssertFalse(model.controlsVisible)
        model.unlockControls()
        XCTAssertTrue(model.controlsVisible)
        model.tearDown()
    }

    @MainActor
    func testAspectCycleWrapsAndResetsZoom() {
        let model = VideoPlayerModel(engine: AVPlayerPlaybackEngine.shared)
        model.setZoom(2)
        model.cycleAspect()
        XCTAssertEqual(model.aspect, .crop)
        XCTAssertEqual(model.zoom, 1)
        for _ in 0..<(VideoAspect.allCases.count - 1) { model.cycleAspect() }
        XCTAssertEqual(model.aspect, .fit)
        model.tearDown()
    }
}
