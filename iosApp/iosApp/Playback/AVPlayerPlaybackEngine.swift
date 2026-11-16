import Foundation
import AVFoundation
import VimivoCoreRust
import AVKit
import Combine
import UIKit

public final class AVPlayerPlaybackEngine: NSObject, ObservableObject, AudioSessionDelegate, RemoteCommandDelegate, AVPictureInPictureControllerDelegate {
    public static let shared = AVPlayerPlaybackEngine()

    @Published public internal(set) var state = PlaybackState()
    /// The title of the last item skipped because it could not be played, for a brief notice in the UI.
    @Published public var skippedItemTitle: String?
    private var consecutiveFailures = 0
    /// The order, position, shuffle and repeat of the queue are backed 100% by Rust (`FfiPlayQueue`).
    let queueModel = FfiPlayQueue()
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
    /// True while a video plays as audio only: the picture is switched off and the sound goes on.
    @Published public private(set) var listeningAsAudio = false
    /// True while libmpv (not AVPlayer) plays the current item: Matroska, AVI, WebM and the other files AVFoundation cannot open.
    @Published public internal(set) var usingMpv = false
    let mpv = MpvBackend.shared
    var mpvSubscriptions: [AnyCancellable] = []
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
        bindMpv()
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
        queueModel.replace(ids: items.map(\.id), startIndex: UInt32(max(0, start)))
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
            _ = queueModel.remove(index: UInt32(index))
        }
        syncQueue()
    }

    /// Moves a queued item to a new position; the one that is playing keeps playing.
    public func moveInQueue(fromOffsets offsets: IndexSet, toOffset destination: Int) {
        guard let from = offsets.first else { return }
        queueModel.moveItem(from: UInt32(from), to: UInt32(destination > from ? destination - 1 : destination))
        syncQueue()
    }

    public func playNext() {
        guard let id = queueModel.next() else {
            if queueModel.size() > 0 { stop() }
            return
        }
        syncQueue()
        if let item = itemsById[id] {
            loadItem(item)
        }
    }

    public func playPrevious() {
        guard queueModel.size() > 0 else { return }
        if state.positionMillis > 3_000 {
            seek(toMillis: 0)
            return
        }
        guard let id = queueModel.previous() else { return }
        syncQueue()
        if let item = itemsById[id] {
            loadItem(item)
        }
    }

    /// Copies the Rust core queue's order and position into the published values the UI reads, then saves it and queues
    /// the song that follows for a gapless start.
    func syncQueue(persist: Bool = true) {
        queue = queueModel.getIds().compactMap { itemsById[$0] }
        currentIndex = Int(queueModel.getCurrentIndex())
        state.isShuffled = queueModel.isShuffled()
        state.repeatMode = RepeatMode(queueModel.getRepeatMode())
        if persist { persistQueue() }
        prepareFollowingItem()
    }

    public func resume() {
        if usingMpv {
            AudioSessionController.shared.configureSession()
            mpv.play()
            state.status = .playing
            updateNowPlaying()
            return
        }
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
        if usingMpv { mpv.pause() }
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
        stopMpvIfActive()
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
        if usingMpv {
            mpv.seek(toSeconds: Double(millis) / 1000.0)
            state.positionMillis = millis
            updateNowPlaying()
            return
        }
        let seconds = Double(millis) / 1000.0
        let targetTime = CMTime(seconds: seconds, preferredTimescale: 600)
        player.seek(to: targetTime, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            self?.state.positionMillis = millis
            self?.updateNowPlaying()
        }
    }

    public func setPlaybackRate(_ rate: Float) {
        state.playbackRate = rate
        if usingMpv { mpv.setSpeed(Double(rate)) }
        // defaultRate makes later play() calls (resume, next item) keep the chosen speed.
        player.defaultRate = rate
        if state.status == .playing {
            player.rate = rate
        }
        updateNowPlaying()
    }

    /// Shuffles the remaining items behind the current one; turning it off restores the original order.
    public func toggleShuffle() {
        _ = queueModel.toggleShuffle()
        syncQueue()
    }

    /// An unplayable item never stops the queue: it is reported and the next item starts. Once every item
    /// has failed in a row, playback stops instead of looping.
    func skipFailedItem(message: String) {
        state.status = .error
        state.errorMessage = message
        updateNowPlaying()
        consecutiveFailures += 1
        let skippedTitle = state.currentItem?.title ?? ""
        if consecutiveFailures >= Int(queueModel.size()) {
            stop()
            return
        }
        guard let id = queueModel.next() else { return }
        skippedItemTitle = skippedTitle
        syncQueue()
        if let item = itemsById[id] {
            loadItem(item)
        }
    }

    public func cycleRepeatMode() {
        _ = queueModel.cycleRepeat()
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

        if MpvBackend.isAvailable && MpvFormats.prefersMpv(playUrl) {
            startMpv(item: item, url: playUrl, startMillis: startMillis)
            return
        }
        stopMpvIfActive()

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

    /// Hides or restores the picture of the current video without touching the sound.
    public func setListeningAsAudio(_ listening: Bool) {
        listeningAsAudio = listening
        if usingMpv { mpv.setVideoEnabled(!listening) }
        applyVideoEnabled(to: player.currentItem)
    }

    private func applyVideoEnabled(to item: AVPlayerItem?) {
        guard let item else { return }
        for track in item.tracks where track.assetTrack?.mediaType == .video {
            track.isEnabled = !listeningAsAudio
        }
    }

    private func observeReadiness(of playerItem: AVPlayerItem, startMillis: Int64) {
        itemStatusObservation = playerItem.observe(\.status, options: [.initial, .new]) { [weak self] observedItem, _ in
            DispatchQueue.main.async {
                guard let self, self.player.currentItem === observedItem else { return }
                switch observedItem.status {
                case .readyToPlay:
                    self.itemStatusObservation = nil
                    self.consecutiveFailures = 0
                    self.applyVideoEnabled(to: observedItem)
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
                    // AVFoundation could not open it; libmpv opens almost anything, so it gets the file before it is skipped.
                    if MpvBackend.isAvailable, let current = self.state.currentItem, !self.usingMpv,
                       let url = self.resolveURL(for: current) {
                        self.startMpv(item: current, url: url, startMillis: startMillis)
                        return
                    }
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

    func detachLegibleOutput() {
        legibleOutputItem?.remove(legibleOutput)
        legibleOutputItem = nil
    }

    private func setupTimeObserver() {
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserverToken = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            // Paused counts too: a frame step moves the position while nothing plays.
            guard let self = self, !self.usingMpv,
                  self.state.status == .playing || self.state.status == .paused else { return }
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
            if self.queueModel.getRepeatMode() == .one {
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

    func checkpoint() {
        guard let item = state.currentItem, state.positionMillis > 0, state.status != .error else { return }
        IOSDatabase.shared.savePosition(mediaId: item.id, positionMillis: state.positionMillis, completed: false)
        lastCheckpointMillis = state.positionMillis
    }

    // MARK: - Queue persistence

    private func persistQueue() {
        IOSDatabase.shared.saveQueue(mediaIds: queueModel.getIds(), currentIndex: Int(queueModel.getCurrentIndex()))
    }

    /// Restores the last queue paused, so nothing plays until the user asks.
    private func restoreQueue() {
        let saved = IOSDatabase.shared.loadQueue()
        guard !saved.mediaIds.isEmpty else { return }
        let library = Dictionary(IOSDatabase.shared.getAllMediaItems().map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let items = saved.mediaIds.compactMap { library[$0] }
        guard !items.isEmpty else { return }
        items.forEach { itemsById[$0.id] = $0 }
        queueModel.replace(ids: items.map(\.id), startIndex: UInt32(max(0, min(saved.currentIndex, items.count - 1))))
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
        let currentPos = state.positionMillis
        let existing = IOSDatabase.shared.bookmarks(mediaId: item.id)
        if existing.contains(where: { abs($0.positionMillis - currentPos) < 1000 }) {
            return nil
        }
        let count = existing.count + 1
        let bookmark = Bookmark(
            id: UUID().uuidString,
            mediaId: item.id,
            name: "Bookmark \(count)",
            positionMillis: currentPos,
            createdAtMillis: Int64(Date().timeIntervalSince1970 * 1000)
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


// MARK: - libmpv (Matroska, AVI, WebM, and everything AVFoundation cannot play)

extension AVPlayerPlaybackEngine {
    /// Mirrors libmpv's position, length and play state into `state` while it is the active player.
    func bindMpv() {
        mpv.$positionSeconds.receive(on: DispatchQueue.main).sink { [weak self] seconds in
            guard let self, self.usingMpv, seconds >= 0 else { return }
            self.state.positionMillis = Int64(seconds * 1000)
            if abs(self.state.positionMillis - self.lastCheckpointMillis) >= PlaybackPreferences.checkpointIntervalMillis {
                self.checkpoint()
            }
            self.updateNowPlaying()
        }.store(in: &mpvSubscriptions)
        mpv.$durationSeconds.receive(on: DispatchQueue.main).sink { [weak self] seconds in
            guard let self, self.usingMpv, seconds > 0 else { return }
            self.state.durationMillis = Int64(seconds * 1000)
        }.store(in: &mpvSubscriptions)
        mpv.$isPaused.receive(on: DispatchQueue.main).sink { [weak self] paused in
            guard let self, self.usingMpv, self.state.status != .buffering, self.state.status != .error else { return }
            self.state.status = paused ? .paused : .playing
            self.updateNowPlaying()
        }.store(in: &mpvSubscriptions)
        mpv.$isBuffering.receive(on: DispatchQueue.main).sink { [weak self] buffering in
            guard let self, self.usingMpv else { return }
            if buffering { self.state.status = .buffering } else if self.state.status == .buffering {
                self.state.status = self.mpv.isPaused ? .paused : .playing
            }
        }.store(in: &mpvSubscriptions)
        mpv.$videoSize.receive(on: DispatchQueue.main).sink { [weak self] size in
            guard let self, self.usingMpv, size.width > 0 else { return }
            self.state.videoWidth = Int(size.width)
            self.state.videoHeight = Int(size.height)
        }.store(in: &mpvSubscriptions)
    }

    /// Hands the current item to libmpv. AVPlayer is emptied so only one engine makes sound.
    func startMpv(item: MediaItem, url: URL, startMillis: Int64) {
        player.pause()
        clearPrepared()
        player.removeAllItems()
        currentPlayerItem = nil
        detachLegibleOutput()
        usingMpv = true
        itemLoads += 1
        AudioSessionController.shared.configureSession()
        mpv.onLoaded = { [weak self] in
            guard let self, self.usingMpv else { return }
            self.mpv.setSpeed(Double(self.state.playbackRate))
            self.state.status = self.mpv.isPaused ? .paused : .playing
            self.updateNowPlaying()
        }
        mpv.onFinished = { [weak self] failed, message in self?.mpvFinished(failed: failed, message: message) }
        let wantsVideo = item.kind == .video && !listeningAsAudio
        guard mpv.load(url, startSeconds: Double(startMillis) / 1000.0, wantsVideo: wantsVideo) else {
            usingMpv = false
            skipFailedItem(message: "The media engine could not start")
            return
        }
        state.status = .playing
        state.positionMillis = startMillis
        updateNowPlaying()
    }

    func stopMpvIfActive() {
        guard usingMpv else { return }
        mpv.onFinished = nil
        mpv.stop()
        usingMpv = false
    }

    /// libmpv reached the end of the file, or could not play it.
    func mpvFinished(failed: Bool, message: String?) {
        guard usingMpv else { return }
        if failed {
            usingMpv = false
            mpv.stop()
            skipFailedItem(message: message ?? "This file could not be played")
            return
        }
        if let item = state.currentItem {
            IOSDatabase.shared.savePosition(mediaId: item.id, positionMillis: state.durationMillis, completed: true)
            lastCheckpointMillis = state.durationMillis
        }
        if sleepTimer.consumeStopAfterCurrent() {
            mpv.pause()
            state.status = .paused
            updateNowPlaying()
            return
        }
        if queueModel.getRepeatMode() == .one {
            seek(toMillis: 0)
            resume()
        } else {
            playNext()
        }
    }
}
