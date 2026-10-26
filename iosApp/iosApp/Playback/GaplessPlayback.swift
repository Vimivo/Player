import AVFoundation
import Foundation

/// The item queued behind the current one so that it starts without a gap.
struct PreparedNext {
    let index: Int
    let media: MediaItem
    let item: AVPlayerItem
    let securityUrl: URL?
}

/// Gapless audio (V1 #11): while a song plays, the next song is loaded behind it in the `AVQueuePlayer`, and when it
/// ends the player starts the next one by itself; the engine only follows (index, state, history).
extension AVPlayerPlaybackEngine {
    /// True when a next item is queued behind the current one.
    public var hasPreparedNext: Bool { preparedNext != nil }

    /// Where a playable file for [item] is (a security-scoped bookmark first, then its address), or nil.
    func resolveURL(for item: MediaItem) -> URL? {
        if let data = item.bookmarkData, let url = SecurityScopedMediaManager.shared.resolveBookmark(data: data) { return url }
        return URL(string: item.uri)
    }

    private func followingIndex() -> Int? {
        guard !queue.isEmpty, state.repeatMode != .one else { return nil }
        if currentIndex + 1 < queue.count { return currentIndex + 1 }
        return state.repeatMode == .all ? 0 : nil
    }

    /// Queues the following song behind the current one. Only song-to-song: video is not handed over this way, a
    /// repeat-one or a one-item queue has nothing following, and a song that should resume part-way is loaded the
    /// normal way when its turn comes. Anything else clears an earlier preparation.
    func prepareFollowingItem() {
        // Nothing is queued behind the track while "stop after this track" is armed.
        guard !sleepTimer.stopsAfterTrack else {
            clearPrepared()
            return
        }
        guard state.status == .playing || state.status == .paused,
              let current = currentPlayerItem, player.currentItem === current,
              let media = state.currentItem, media.kind == .audio,
              let index = followingIndex(), index != currentIndex, queue.indices.contains(index) else {
            clearPrepared()
            return
        }
        let next = queue[index]
        guard next.kind == .audio, next.id != media.id else {
            clearPrepared()
            return
        }
        if let prepared = preparedNext, prepared.index == index, prepared.media.id == next.id { return }
        clearPrepared()
        let startsAtBeginning = PlaybackPreferences.startPositionMillis(
            for: next, saved: IOSDatabase.shared.resumeState(mediaId: next.id)
        ) == 0
        guard startsAtBeginning, let url = resolveURL(for: next) else { return }
        var securityUrl: URL?
        if url.isFileURL {
            guard FileManager.default.fileExists(atPath: url.path) else { return }
            _ = SecurityScopedMediaManager.shared.startAccessing(url: url)
            securityUrl = url
        }
        let item = AVPlayerItem(url: url)
        item.audioTimePitchAlgorithm = .timeDomain
        attachAudioEffects(to: item)
        player.insert(item, after: current)
        preparedNext = PreparedNext(index: index, media: next, item: item, securityUrl: securityUrl)
    }

    func clearPrepared() {
        guard let prepared = preparedNext else { return }
        player.remove(prepared.item)
        if let url = prepared.securityUrl { SecurityScopedMediaManager.shared.stopAccessing(url: url) }
        preparedNext = nil
    }

    func observeQueueAdvance() {
        currentItemObservation = player.observe(\.currentItem, options: [.new]) { [weak self] _, change in
            DispatchQueue.main.async {
                guard let self, let prepared = self.preparedNext, let now = change.newValue ?? nil, now === prepared.item else { return }
                self.advance(to: prepared)
            }
        }
    }

    /// The player started the queued item on its own: move the engine's own record of what plays to it.
    private func advance(to prepared: PreparedNext) {
        if let finished = state.currentItem {
            IOSDatabase.shared.savePosition(mediaId: finished.id, positionMillis: state.durationMillis, completed: true)
        }
        if let url = activeSecurityUrl { SecurityScopedMediaManager.shared.stopAccessing(url: url) }
        activeSecurityUrl = prepared.securityUrl
        preparedNext = nil
        currentPlayerItem = prepared.item
        setABRepeat(nil)
        state.currentItem = prepared.media
        state.positionMillis = 0
        state.bufferedMillis = 0
        state.durationMillis = prepared.media.durationMillis ?? 0
        state.errorMessage = nil
        state.status = .playing
        lastCheckpointMillis = 0
        IOSDatabase.shared.recordPlayStart(mediaId: prepared.media.id)
        currentIndex = prepared.index // saves the queue and prepares the one after
        updateNowPlaying()
    }
}
