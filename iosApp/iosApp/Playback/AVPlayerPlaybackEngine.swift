import Foundation
import AVFoundation
import AVKit
import Combine

public final class AVPlayerPlaybackEngine: NSObject, ObservableObject, AudioSessionDelegate, RemoteCommandDelegate, AVPictureInPictureControllerDelegate {
    public static let shared = AVPlayerPlaybackEngine()

    @Published public private(set) var state = PlaybackState()
    @Published public var queue: [MediaItem] = []
    @Published public var currentIndex: Int = 0

    public let player = AVPlayer()
    private var timeObserverToken: Any?
    private var itemEndObserver: Any?
    private var activeSecurityUrl: URL?
    private var pipController: AVPictureInPictureController?

    public override init() {
        super.init()
        AudioSessionController.shared.delegate = self
        AudioSessionController.shared.configureSession()
        RemoteCommandController.shared.delegate = self
        RemoteCommandController.shared.configureCommands()
        setupTimeObserver()
        setupEndObserver()
    }

    deinit {
        if let token = timeObserverToken {
            player.removeTimeObserver(token)
        }
        if let obs = itemEndObserver {
            NotificationCenter.default.removeObserver(obs)
        }
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
        player.pause()
        player.replaceCurrentItem(with: nil)
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
        if state.status == .playing {
            player.rate = rate
        }
        updateNowPlaying()
    }

    public func toggleShuffle() {
        state.isShuffled.toggle()
        if state.isShuffled {
            guard !queue.isEmpty else { return }
            let current = queue[currentIndex]
            var remaining = queue
            remaining.remove(at: currentIndex)
            remaining.shuffle()
            queue = [current] + remaining
            currentIndex = 0
        }
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
        if let url = activeSecurityUrl {
            SecurityScopedMediaManager.shared.stopAccessing(url: url)
            activeSecurityUrl = nil
        }

        state.currentItem = item
        state.status = .buffering
        state.errorMessage = nil
        state.positionMillis = 0
        state.durationMillis = item.durationMillis ?? 0

        var resolvedUrl: URL?
        if let bookmarkData = item.bookmarkData {
            resolvedUrl = SecurityScopedMediaManager.shared.resolveBookmark(data: bookmarkData)
        }
        if resolvedUrl == nil {
            resolvedUrl = URL(string: item.uri)
        }

        guard let playUrl = resolvedUrl else {
            state.status = .error
            state.errorMessage = "Could not resolve media URL"
            return
        }

        if playUrl.isFileURL {
            if !FileManager.default.fileExists(atPath: playUrl.path) {
                state.status = .error
                state.errorMessage = "Media file not found: \(playUrl.lastPathComponent)"
                return
            }
            _ = SecurityScopedMediaManager.shared.startAccessing(url: playUrl)
            activeSecurityUrl = playUrl
        }

        let playerItem = AVPlayerItem(url: playUrl)
        player.replaceCurrentItem(with: playerItem)
        AudioSessionController.shared.configureSession()
        player.play()
        state.status = .playing
        updateNowPlaying()

        IOSDatabase.shared.recordPlayback(mediaId: item.id, positionMillis: 0, completed: false)
    }

    private func setupTimeObserver() {
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserverToken = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self, self.state.status == .playing else { return }
            let seconds = CMTimeGetSeconds(time)
            if !seconds.isNaN && seconds >= 0 {
                self.state.positionMillis = Int64(seconds * 1000)
            }
            if let currentItem = self.player.currentItem {
                let durSeconds = CMTimeGetSeconds(currentItem.duration)
                if !durSeconds.isNaN && durSeconds > 0 {
                    self.state.durationMillis = Int64(durSeconds * 1000)
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
                IOSDatabase.shared.recordPlayback(mediaId: item.id, positionMillis: self.state.durationMillis, completed: true)
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
            self.state.status = .error
            self.state.errorMessage = "Media playback failed"
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
        if let item = state.currentItem, state.positionMillis > 0 {
            IOSDatabase.shared.recordPlayback(mediaId: item.id, positionMillis: state.positionMillis, completed: false)
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
