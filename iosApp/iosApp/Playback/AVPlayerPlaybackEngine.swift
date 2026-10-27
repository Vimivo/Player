import Foundation
import AVFoundation
import VimivoShared
import AVKit
import Combine
import UIKit

public final class AVPlayerPlaybackEngine: NSObject, ObservableObject, AudioSessionDelegate, RemoteCommandDelegate, AVPictureInPictureControllerDelegate {
    public static let shared = AVPlayerPlaybackEngine()

    @Published public internal(set) var state = PlaybackState()
    /// The title of the last item skipped because it could not be played, for a brief notice in the UI.
    @Published public var skippedItemTitle: String?
    private var consecutiveFailures = 0
    /// The order, position, shuffle and repeat of the queue are the shared module's; the engine maps its ids to items.
    let queueModel = PlayQueue()
    private var itemsById: [String: MediaItem] = [:]
    @Published public private(set) var queue: [MediaItem] = []
    @Published public private(set) var currentIndex: Int = 0

    /// An `AVQueuePlayer`, so the next audio item can be loaded behind the current one and start without a gap.
    public let player = AVQueuePlayer()
    /// Pauses after a chosen time or after the current track, with a fade-out.
    public let sleepTimer = SleepTimerModel()
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
    var activeSecurityUrl: URL?
    private var pipController: AVPictureInPictureController?
    private var itemStatusObservation: NSKeyValueObservation?
    private var lifecycleObservers: [NSObjectProtocol] = []
    var lastCheckpointMillis: Int64 = 0
    /// The A–B region being repeated, in milliseconds (V1 #6); cleared whenever another item loads.
    public internal(set) var abRepeat: ClosedRange<Int64>?
    var abBoundaryToken: Any?
    /// The item `player` is playing for `state.currentItem`, and the item queued behind it for a gapless start.
    var currentPlayerItem: AVPlayerItem?
    var preparedNext: PreparedNext?
    var currentItemObservation: NSKeyValueObservation?
    /// How many items were loaded from scratch; a gapless advance does not count (tests read this).
    public internal(set) var itemLoads = 0
    /// Equalizer, volume boost and ReplayGain (V1 #19, #21, #24), run on every item's audio through a tap.
    public let audioEffects = AudioEffectsController()
    @Published public internal(set) var audioSettings = AudioEffectSettings.load()
    /// The colour and crop applied to the picture (V1 #12, #14); applied to each item as it loads.
    @Published public internal(set) var pictureSettings = PictureSettings()

    public override init() {
        super.init()
        sleepTimer.attach(self)
        AudioSessionController.shared.delegate = self
        AudioSessionController.shared.configureSession()
        RemoteCommandController.shared.delegate = self
        RemoteCommandController.shared.configureCommands()
        audioEffects.update(settings: audioSettings)
        setupTimeObserver()
        setupEndObserver()
        observeQueueAdvance()
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
        if !queueModel.jumpToId(id: item.id) {
            replaceQueue([item], currentIndex: 0)
        }
        syncQueue()
        loadItem(item)
    }

    public func playQueue(items: [MediaItem], startIndex: Int = 0) {
        guard !items.isEmpty else { return }
        replaceQueue(items, currentIndex: startIndex)
        loadItem(queue[currentIndex])
    }

    /// Replaces the queue without starting anything; a shuffled queue is shuffled again behind the chosen item.
    func replaceQueue(_ items: [MediaItem], currentIndex start: Int) {
        items.forEach { itemsById[$0.id] = $0 }
        queueModel.replace(ids: items.map(\.id), startIndex: Int32(start))
        syncQueue()
    }

    public func appendToQueue(item: MediaItem) {
        itemsById[item.id] = item
        queueModel.append(id: item.id)
        syncQueue()
        if state.currentItem == nil {
            _ = queueModel.jumpTo(index: 0)
            syncQueue()
            loadItem(queue[currentIndex])
        }
    }

    /// Queues [item] right behind the one that is playing.
    public func playNextInQueue(_ item: MediaItem) {
        itemsById[item.id] = item
        queueModel.insertNext(id: item.id)
        syncQueue()
    }

    /// Removes queued items; the one that is playing stays.
    public func removeFromQueue(atOffsets offsets: IndexSet) {
        for index in offsets.sorted(by: >) where index != currentIndex {
            _ = queueModel.remove(index: Int32(index))
        }
        syncQueue()
    }

    /// Moves a queued item to a new position; the one that is playing keeps playing.
    public func moveInQueue(fromOffsets offsets: IndexSet, toOffset destination: Int) {
        guard let from = offsets.first else { return }
        queueModel.move(from: Int32(from), to: Int32(destination > from ? destination - 1 : destination))
        syncQueue()
    }

    public func playNext() {
        guard let index = queueModel.next(automatic: false)?.intValue else {
            if queueModel.size > 0 { stop() }
            return
        }
        syncQueue()
        loadItem(queue[index])
    }

    public func playPrevious() {
        guard queueModel.size > 0 else { return }
        let moved = Int(queueModel.previous(positionMillis: state.positionMillis, restartAfterMillis: PlayQueue.companion.DEFAULT_RESTART_AFTER_MILLIS))
        if moved == Int(PlayQueue.companion.RESTART) {
            seek(toMillis: 0)
        } else {
            syncQueue()
            loadItem(queue[moved])
        }
    }

    /// Copies the shared queue's order and position into the published values the UI reads, then saves it and queues
    /// the song that follows for a gapless start.
    func syncQueue(persist: Bool = true) {
        queue = queueModel.ids.compactMap { itemsById[$0] }
        currentIndex = Int(queueModel.currentIndex)
        state.isShuffled = queueModel.shuffled
        state.repeatMode = RepeatMode(queueModel.repeat)
        if persist { persistQueue() }
        prepareFollowingItem()
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
        clearPrepared()
        player.removeAllItems()
        currentPlayerItem = nil
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
        queueModel.toggleShuffle()
        syncQueue()
    }

    /// An unplayable item never stops the queue: it is reported and the next item starts. Once every item
    /// has failed in a row, playback stops instead of looping.
    private func skipFailedItem(message: String) {
        state.status = .error
        state.errorMessage = message
        updateNowPlaying()
        consecutiveFailures += 1
        let skippedTitle = state.currentItem?.title ?? ""
        guard let index = queueModel.skipFailed(consecutiveFailures: Int32(consecutiveFailures))?.intValue else { return }
        skippedItemTitle = skippedTitle
        syncQueue()
        loadItem(queue[index])
    }

    public func cycleRepeatMode() {
        queueModel.cycleRepeat()
        syncQueue()
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

        guard let playUrl = resolveURL(for: item) else {
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
        attachAudioEffects(to: playerItem)
        clearPrepared()
        player.removeAllItems()
        player.insert(playerItem, after: nil)
        currentPlayerItem = playerItem
        itemLoads += 1
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
                    self.prepareFollowingItem()
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
        ) { [weak self] notification in
            guard let self = self else { return }
            // An item that already handed over (gapless) finishing late is not this item finishing.
            guard (notification.object as? AVPlayerItem) === self.currentPlayerItem else { return }
            if let item = self.state.currentItem {
                IOSDatabase.shared.savePosition(mediaId: item.id, positionMillis: self.state.durationMillis, completed: true)
                self.lastCheckpointMillis = self.state.durationMillis
            }
            // "Stop after this track": stay on the finished track, paused, instead of moving on.
            if self.sleepTimer.consumeStopAfterCurrent() {
                self.player.pause()
                self.state.status = .paused
                self.updateNowPlaying()
                return
            }
            // With the next item queued the player moves on by itself and `advanceToPrepared` follows it.
            if self.preparedNext != nil { return }
            if self.queueModel.repeat == .one {
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

    func updateNowPlaying() {
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
        let snapshot = queueModel.snapshot()
        IOSDatabase.shared.saveQueue(mediaIds: snapshot.ids, currentIndex: Int(snapshot.currentIndex))
    }

    /// Restores the last queue paused, so nothing plays until the user asks.
    private func restoreQueue() {
        let saved = IOSDatabase.shared.loadQueue()
        guard !saved.mediaIds.isEmpty else { return }
        let library = Dictionary(IOSDatabase.shared.getAllMediaItems().map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let items = saved.mediaIds.compactMap { library[$0] }
        guard !items.isEmpty else { return }
        items.forEach { itemsById[$0.id] = $0 }
        queueModel.restore(savedIds: saved.mediaIds, savedIndex: Int32(saved.currentIndex), available: Set(library.keys))
        syncQueue(persist: false)
        guard queue.indices.contains(currentIndex) else { return }
        let current = queue[currentIndex]
        state.currentItem = current
        state.status = .paused
        state.durationMillis = current.durationMillis ?? 0
    }

    // MARK: - Bookmarks

    @discardableResult
    public func addBookmarkAtCurrentPosition() -> Bookmark? {
        guard let item = state.currentItem else { return nil }
        // Naming and the "already bookmarked here" rule are the shared module's.
        let existing = IOSDatabase.shared.bookmarks(mediaId: item.id).map {
            StoredBookmark(id: $0.id, mediaId: $0.mediaId, name: $0.name, positionMillis: $0.positionMillis, createdAtMillis: $0.createdAtMillis)
        }
        guard let created = Bookmarks.shared.create(
            mediaId: item.id,
            positionMillis: state.positionMillis,
            existing: existing,
            id: UUID().uuidString,
            createdAtMillis: Int64(Date().timeIntervalSince1970 * 1000)
        ) else { return nil }
        let bookmark = Bookmark(id: created.id, mediaId: created.mediaId, name: created.name, positionMillis: created.positionMillis, createdAtMillis: created.createdAtMillis)
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
