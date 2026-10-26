import Foundation
import AVFoundation
import AVKit
import Combine
import UIKit

public final class AVPlayerPlaybackEngine: NSObject, ObservableObject, AudioSessionDelegate, RemoteCommandDelegate, AVPictureInPictureControllerDelegate {
    public static let shared = AVPlayerPlaybackEngine()

    @Published public private(set) var state = PlaybackState()
    /// The title of the last item skipped because it could not be played, for a brief notice in the UI.
    @Published public var skippedItemTitle: String?
    private var unshuffledQueue: [MediaItem]?
    private var consecutiveFailures = 0
    @Published public var queue: [MediaItem] = [] {
        didSet { persistQueue() }
    }
    @Published public var currentIndex: Int = 0 {
        didSet { persistQueue() }
    }
    private var isRestoringQueue = false

    public let player = AVPlayer()
    /// Delivers subtitle cues for custom rendering. It is attached to every item before playback
    /// starts because AVFoundation does not reliably push cues to outputs added mid-playback.
    public let legibleOutput: AVPlayerItemLegibleOutput = {
        let output = AVPlayerItemLegibleOutput()
        output.suppressesPlayerRendering = true
        return output
    }()
    private var legibleOutputItem: AVPlayerItem?
    private var timeObserverToken: Any?
    private var itemEndObserver: Any?
    private var activeSecurityUrl: URL?
    private var pipController: AVPictureInPictureController?
    private var itemStatusObservation: NSKeyValueObservation?
    private var lifecycleObservers: [NSObjectProtocol] = []
    private var lastCheckpointMillis: Int64 = 0
    /// The A–B region being repeated, in milliseconds (V1 #6); cleared whenever another item loads.
    public internal(set) var abRepeat: ClosedRange<Int64>?
    var abBoundaryToken: Any?
    /// The colour and crop applied to the picture (V1 #12, #14); applied to each item as it loads.
    @Published public internal(set) var pictureSettings = PictureSettings()

    public override init() {
        super.init()
        AudioSessionController.shared.delegate = self
        AudioSessionController.shared.configureSession()
        RemoteCommandController.shared.delegate = self
        RemoteCommandController.shared.configureCommands()
        setupTimeObserver()
        setupEndObserver()
        setupLifecycleObservers()
        restoreQueue()
    }

    deinit {
        if let token = timeObserverToken {
            player.removeTimeObserver(token)
        }
        if let obs = itemEndObserver {
            NotificationCenter.default.removeObserver(obs)
        }
        lifecycleObservers.forEach(NotificationCenter.default.removeObserver)
        itemStatusObservation?.invalidate()
        if let url = activeSecurityUrl {
            SecurityScopedMediaManager.shared.stopAccessing(url: url)
        }
    }

    // MARK: - Queue & Playback Management

    public func play(item: MediaItem) {
        if let idx = queue.firstIndex(where: { $0.id == item.id }) {
            currentIndex = idx
        } else {
            queue = [item]
            currentIndex = 0
        }
        loadItem(item)
    }

    public func playQueue(items: [MediaItem], startIndex: Int = 0) {
        guard !items.isEmpty else { return }
        queue = items
        currentIndex = min(max(startIndex, 0), items.count - 1)
        loadItem(queue[currentIndex])
    }

    public func appendToQueue(item: MediaItem) {
        queue.append(item)
        if state.currentItem == nil {
            currentIndex = 0
            loadItem(item)
        }
    }

    public func playNext() {
        guard !queue.isEmpty else { return }
        if currentIndex + 1 < queue.count {
            currentIndex += 1
            loadItem(queue[currentIndex])
        } else if state.repeatMode == .all {
            currentIndex = 0
            loadItem(queue[currentIndex])
        } else {
            stop()
        }
    }

    public func playPrevious() {
        guard !queue.isEmpty else { return }
        if state.positionMillis > 3000 {
            seek(toMillis: 0)
        } else if currentIndex > 0 {
            currentIndex -= 1
            loadItem(queue[currentIndex])
        } else if state.repeatMode == .all {
            currentIndex = queue.count - 1
            loadItem(queue[currentIndex])
        } else {
            seek(toMillis: 0)
        }
    }

    public func resume() {
        // A queue restored at launch has no player item yet; load it so resume rules apply.
        if player.currentItem == nil, let item = state.currentItem {
            loadItem(item)
            return
        }
        AudioSessionController.shared.configureSession()
        player.play()
        state.status = .playing
        updateNowPlaying()
    }

    public func pause() {
        player.pause()
        state.status = .paused
        updateNowPlaying()
        checkpoint()
    }

    public func togglePlayPause() {
        if state.status == .playing {
            pause()
        } else {
            resume()
        }
    }

    public func stop() {
        checkpoint()
        itemStatusObservation?.invalidate()
        itemStatusObservation = nil
        player.pause()
        player.replaceCurrentItem(with: nil)
        detachLegibleOutput()
        state.status = .idle
        state.positionMillis = 0
        state.durationMillis = 0
        state.currentItem = nil
        state.errorMessage = nil
        if let url = activeSecurityUrl {
            SecurityScopedMediaManager.shared.stopAccessing(url: url)
            activeSecurityUrl = nil
        }
        NowPlayingManager.shared.clear()
    }

    public func seek(toMillis millis: Int64) {
        let seconds = Double(millis) / 1000.0
        let targetTime = CMTime(seconds: seconds, preferredTimescale: 600)
        player.seek(to: targetTime, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            self?.state.positionMillis = millis
            self?.updateNowPlaying()
        }
    }

    public func setPlaybackRate(_ rate: Float) {
        state.playbackRate = rate
        // defaultRate makes later play() calls (resume, next item) keep the chosen speed.
        player.defaultRate = rate
        if state.status == .playing {
            player.rate = rate
        }
        updateNowPlaying()
    }

    /// Shuffles the remaining items behind the current one; turning it off restores the original order.
    public func toggleShuffle() {
        state.isShuffled.toggle()
        guard !queue.isEmpty else { return }
        let current = queue[currentIndex]
        if state.isShuffled {
            unshuffledQueue = queue
            var remaining = queue
            remaining.remove(at: currentIndex)
            remaining.shuffle()
            queue = [current] + remaining
            currentIndex = 0
        } else if let original = unshuffledQueue {
            // Items added while shuffled are kept, after the original order.
            queue = original + queue.filter { item in !original.contains { $0.id == item.id } }
            currentIndex = queue.firstIndex { $0.id == current.id } ?? 0
            unshuffledQueue = nil
        }
    }

    /// An unplayable item never stops the queue: it is reported and the next item starts. Once every item
    /// has failed in a row, playback stops instead of looping.
    private func skipFailedItem(message: String) {
        state.status = .error
        state.errorMessage = message
        updateNowPlaying()
        consecutiveFailures += 1
        let skippedTitle = state.currentItem?.title ?? ""
        guard consecutiveFailures < queue.count, currentIndex + 1 < queue.count || state.repeatMode == .all else { return }
        skippedItemTitle = skippedTitle
        currentIndex = currentIndex + 1 < queue.count ? currentIndex + 1 : 0
        loadItem(queue[currentIndex])
    }

    public func cycleRepeatMode() {
        switch state.repeatMode {
        case .off: state.repeatMode = .all
        case .all: state.repeatMode = .one
        case .one: state.repeatMode = .off
        }
    }

    // MARK: - Internal Loading

    private func loadItem(_ item: MediaItem) {
        // Persist where the outgoing item stopped before its state is replaced.
        checkpoint()
        itemStatusObservation?.invalidate()
        itemStatusObservation = nil
        if let url = activeSecurityUrl {
            SecurityScopedMediaManager.shared.stopAccessing(url: url)
            activeSecurityUrl = nil
        }

        setABRepeat(nil)
        state.currentItem = item
        state.status = .buffering
        state.errorMessage = nil
        state.positionMillis = 0
        state.bufferedMillis = 0
        state.videoWidth = item.width
        state.videoHeight = item.height
        state.durationMillis = item.durationMillis ?? 0

        var resolvedUrl: URL?
        if let bookmarkData = item.bookmarkData {
            resolvedUrl = SecurityScopedMediaManager.shared.resolveBookmark(data: bookmarkData)
        }
        if resolvedUrl == nil {
            resolvedUrl = URL(string: item.uri)
        }

        guard let playUrl = resolvedUrl else {
            skipFailedItem(message: "Could not resolve media URL")
            return
        }

        if playUrl.isFileURL {
            if !FileManager.default.fileExists(atPath: playUrl.path) {
                skipFailedItem(message: "Media file not found: \(playUrl.lastPathComponent)")
                return
            }
            _ = SecurityScopedMediaManager.shared.startAccessing(url: playUrl)
            activeSecurityUrl = playUrl
        }

        let startMillis = PlaybackPreferences.startPositionMillis(
            for: item,
            saved: IOSDatabase.shared.resumeState(mediaId: item.id)
        )
        IOSDatabase.shared.recordPlayStart(mediaId: item.id)
        lastCheckpointMillis = startMillis

        let playerItem = AVPlayerItem(url: playUrl)
        // Pitch-corrected and valid for the whole 0.25×–4× range; low-latency algorithms stop at 2×.
        playerItem.audioTimePitchAlgorithm = .timeDomain
        attachLegibleOutput(to: playerItem)
        if !pictureSettings.isNeutral {
            let settings = pictureSettings
            Task { playerItem.videoComposition = try? await PictureComposition.make(for: playerItem.asset, settings: settings) }
        }
        player.replaceCurrentItem(with: playerItem)
        AudioSessionController.shared.configureSession()
        observeReadiness(of: playerItem, startMillis: startMillis)
        if startMillis > 0 {
            // Seeking is only reliable once the item is ready, so playback starts from the observer.
            state.positionMillis = startMillis
        } else {
            player.play()
            state.status = .playing
        }
        updateNowPlaying()
    }

    private func observeReadiness(of playerItem: AVPlayerItem, startMillis: Int64) {
        itemStatusObservation = playerItem.observe(\.status, options: [.initial, .new]) { [weak self] observedItem, _ in
            DispatchQueue.main.async {
                guard let self, self.player.currentItem === observedItem else { return }
                switch observedItem.status {
                case .readyToPlay:
                    self.itemStatusObservation = nil
                    self.consecutiveFailures = 0
                    guard startMillis > 0 else { return }
                    let target = CMTime(seconds: Double(startMillis) / 1000.0, preferredTimescale: 600)
                    self.player.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
                        guard let self, self.player.currentItem === observedItem else { return }
                        self.player.play()
                        self.state.status = .playing
                        self.updateNowPlaying()
                    }
                case .failed:
                    self.itemStatusObservation = nil
                    self.skipFailedItem(message: observedItem.error?.localizedDescription ?? "This file could not be played")
                    self.updateNowPlaying()
                default:
                    break
                }
            }
        }
    }

    /// Re-attaches the subtitle output to the current item. AVFoundation stops pushing cues to an
    /// attached output after a mid-playback subtitle switch; re-attaching resumes delivery.
    public func reattachLegibleOutput() {
        guard let item = player.currentItem else { return }
        attachLegibleOutput(to: item)
    }

    /// An output can belong to only one item; adding it to a second one raises an exception.
    private func attachLegibleOutput(to item: AVPlayerItem) {
        detachLegibleOutput()
        item.add(legibleOutput)
        legibleOutputItem = item
    }

    private func detachLegibleOutput() {
        legibleOutputItem?.remove(legibleOutput)
        legibleOutputItem = nil
    }

    private func setupTimeObserver() {
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserverToken = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            // Paused counts too: a frame step moves the position while nothing plays.
            guard let self = self, self.state.status == .playing || self.state.status == .paused else { return }
            let seconds = CMTimeGetSeconds(time)
            if !seconds.isNaN && seconds >= 0 {
                self.state.positionMillis = Int64(seconds * 1000)
            }
            if abs(self.state.positionMillis - self.lastCheckpointMillis) >= PlaybackPreferences.checkpointIntervalMillis {
                self.checkpoint()
            }
            if let currentItem = self.player.currentItem {
                let durSeconds = CMTimeGetSeconds(currentItem.duration)
                if !durSeconds.isNaN && durSeconds > 0 {
                    self.state.durationMillis = Int64(durSeconds * 1000)
                }
                if let range = currentItem.loadedTimeRanges.last?.timeRangeValue {
                    let end = CMTimeGetSeconds(CMTimeRangeGetEnd(range))
                    if !end.isNaN && end >= 0 {
                        self.state.bufferedMillis = Int64(end * 1000)
                    }
                }
                let size = currentItem.presentationSize
                if size.width > 0 && size.height > 0 {
                    self.state.videoWidth = Int(size.width)
                    self.state.videoHeight = Int(size.height)
                }
            }
            self.updateNowPlaying()
        }
    }

    private func setupEndObserver() {
        itemEndObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self = self else { return }
            if let item = self.state.currentItem {
                IOSDatabase.shared.savePosition(mediaId: item.id, positionMillis: self.state.durationMillis, completed: true)
                self.lastCheckpointMillis = self.state.durationMillis
            }
            if self.state.repeatMode == .one {
                self.seek(toMillis: 0)
                self.resume()
            } else {
                self.playNext()
            }
        }
        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self = self else { return }
            self.skipFailedItem(message: "Media playback failed")
            self.updateNowPlaying()
        }
    }

    private func updateNowPlaying() {
        let posSec = Double(state.positionMillis) / 1000.0
        let durSec = Double(state.durationMillis) / 1000.0
        NowPlayingManager.shared.update(
            item: state.currentItem,
            isPlaying: state.status == .playing,
            positionSeconds: posSec,
            durationSeconds: durSec,
            playbackRate: state.playbackRate,
            queueIndex: currentIndex,
            queueCount: queue.count
        )
    }

    private func checkpoint() {
        guard let item = state.currentItem, state.positionMillis > 0, state.status != .error else { return }
        IOSDatabase.shared.savePosition(mediaId: item.id, positionMillis: state.positionMillis, completed: false)
        lastCheckpointMillis = state.positionMillis
    }

    // MARK: - Queue persistence

    private func persistQueue() {
        guard !isRestoringQueue else { return }
        IOSDatabase.shared.saveQueue(mediaIds: queue.map(\.id), currentIndex: currentIndex)
    }

    /// Restores the last queue paused, so nothing plays until the user asks.
    private func restoreQueue() {
        let saved = IOSDatabase.shared.loadQueue()
        guard !saved.mediaIds.isEmpty else { return }
        let library = Dictionary(IOSDatabase.shared.getAllMediaItems().map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let items = saved.mediaIds.compactMap { library[$0] }
        guard !items.isEmpty else { return }
        let currentId = saved.mediaIds[saved.currentIndex]
        isRestoringQueue = true
        queue = items
        currentIndex = items.firstIndex { $0.id == currentId } ?? 0
        isRestoringQueue = false
        let current = items[currentIndex]
        state.currentItem = current
        state.status = .paused
        state.durationMillis = current.durationMillis ?? 0
    }

    // MARK: - Bookmarks

    @discardableResult
    public func addBookmarkAtCurrentPosition() -> Bookmark? {
        guard let item = state.currentItem else { return nil }
        let bookmark = Bookmark(
            mediaId: item.id,
            name: MediaFormatting.clock(millis: state.positionMillis),
            positionMillis: state.positionMillis
        )
        IOSDatabase.shared.addBookmark(bookmark)
        return bookmark
    }

    public func bookmarksForCurrentItem() -> [Bookmark] {
        guard let item = state.currentItem else { return [] }
        return IOSDatabase.shared.bookmarks(mediaId: item.id)
    }

    public func deleteBookmark(_ bookmark: Bookmark) {
        IOSDatabase.shared.deleteBookmark(id: bookmark.id)
    }

    /// The app can be suspended or terminated without warning once backgrounded, so save the position first.
    private func setupLifecycleObservers() {
        let center = NotificationCenter.default
        lifecycleObservers = [UIApplication.willResignActiveNotification, UIApplication.didEnterBackgroundNotification].map { name in
            center.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in self?.checkpoint() }
        }
    }

    // MARK: - Delegates
    public func handleAudioInterruption(began: Bool, shouldResume: Bool) {
        if began {
            pause()
        } else if shouldResume {
            resume()
        }
    }

    public func handleHeadphonesUnplugged() {
        pause()
    }

    public func remotePlay() { resume() }
    public func remotePause() { pause() }
    public func remoteTogglePlayPause() { togglePlayPause() }
    public func remoteNext() { playNext() }
    public func remotePrevious() { playPrevious() }
    public func remoteSeek(to positionSeconds: Double) { seek(toMillis: Int64(positionSeconds * 1000)) }
}
