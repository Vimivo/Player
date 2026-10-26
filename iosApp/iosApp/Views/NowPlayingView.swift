import SwiftUI

public struct NowPlayingView: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    @Environment(\.dismiss) private var dismiss
    @State private var showQueue = false
    @State private var showEqualizer = false
    @State private var isFavorite = false
    @State private var showLyrics = false
    @State private var peaks: [Float]?
    @State private var importingLyrics = false
    @StateObject private var lyrics = LyricsModel()

    public init(engine: AVPlayerPlaybackEngine) {
        self.engine = engine
    }

    public var body: some View {
        GeometryReader { proxy in
            ZStack {
                LinearGradient(
                    colors: [Color.accentColor.opacity(0.42), Color(.systemBackground).opacity(0.94), Color(.systemBackground)],
                    startPoint: .top, endPoint: .bottom
                )
                .ignoresSafeArea()
                if proxy.size.width > proxy.size.height {
                    landscape(height: proxy.size.height)
                } else {
                    portrait
                }
            }
        }
        .sheet(isPresented: $showQueue) { QueueView(engine: engine) }
        .sheet(isPresented: $showEqualizer) { EqualizerSheet(engine: engine) }
        .fileImporter(isPresented: $importingLyrics, allowedContentTypes: [.plainText, .data]) { result in
            if case let .success(url) = result, let item = engine.state.currentItem { lyrics.load(from: url, for: item) }
        }
        .onAppear { refreshFavorite(); lyrics.refresh(for: engine.state.currentItem) }
        .onChange(of: engine.state.currentItem?.id) { _ in refreshFavorite(); lyrics.refresh(for: engine.state.currentItem) }
        .task(id: engine.state.currentItem?.id) { await loadPeaks() }
    }

    // MARK: Layouts

    private var portrait: some View {
        VStack(spacing: 0) {
            header
            Spacer(minLength: 12)
            artwork.padding(.horizontal, 24)
            Spacer(minLength: 16)
            info.padding(.horizontal, 24)
            scrubber.padding(.horizontal, 24).padding(.top, 12)
            transport.padding(.horizontal, 24).padding(.top, 8)
            shortcuts.padding(.top, 8).padding(.bottom, 8)
        }
    }

    private func landscape(height: CGFloat) -> some View {
        HStack(spacing: 24) {
            VStack {
                dismissButton
                artwork.frame(maxHeight: height - 80)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: height)
            VStack(spacing: 12) {
                Spacer(minLength: 0)
                info
                scrubber
                transport
                shortcuts
                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, 24)
    }

    // MARK: Pieces

    private var dismissButton: some View {
        Button(action: { dismiss() }) {
            Image(systemName: "chevron.down").font(.system(size: 18, weight: .bold)).foregroundColor(.primary)
                .frame(width: 44, height: 44)
        }
        .accessibilityLabel("Dismiss now playing")
    }

    private var header: some View {
        HStack {
            dismissButton
            Spacer()
            HStack(spacing: 6) {
                Circle().fill(Color.accentColor).frame(width: 6, height: 6)
                Text("Now Playing").font(.subheadline.weight(.semibold)).foregroundColor(.secondary)
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            Spacer()
            speedMenu(icon: "ellipsis")
        }
        .padding(.horizontal, 8)
    }

    @ViewBuilder private var artwork: some View {
        if showLyrics {
            LyricsPanel(
                lyrics: lyrics,
                positionMillis: engine.state.positionMillis,
                onSeek: { engine.seek(toMillis: $0) },
                onLoad: { importingLyrics = true }
            )
        } else if let current = engine.state.currentItem {
            ArtworkThumbnailView(uri: current.artworkUri, kind: current.kind, size: 320)
                .aspectRatio(1, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 24))
                .shadow(color: Color.accentColor.opacity(0.35), radius: 24, y: 10)
        }
    }

    @ViewBuilder private var info: some View {
        if let current = engine.state.currentItem {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(current.title).font(.title2.weight(.bold)).lineLimit(1)
                        Text(current.artist ?? "Unknown artist").font(.headline).foregroundColor(.secondary).lineLimit(1)
                    }
                    Spacer()
                    Button(action: toggleFavorite) {
                        Image(systemName: isFavorite ? "heart.fill" : "heart")
                            .font(.system(size: 22))
                            .foregroundColor(isFavorite ? .accentColor : .primary)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(isFavorite ? "Remove favorite" : "Add favorite")
                }
                if let format = formatText(for: current) { FormatBadge(text: format) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var scrubber: some View {
        VStack(spacing: 4) {
            WaveformSeekBar(
                progress: engine.state.progress,
                peaks: peaks,
                positionMillis: engine.state.positionMillis,
                durationMillis: engine.state.durationMillis
            ) { fraction in
                engine.seek(toMillis: Int64(fraction * Double(engine.state.durationMillis)))
            }
            HStack {
                Text(formatClock(millis: engine.state.positionMillis))
                Spacer()
                Text(formatClock(millis: engine.state.durationMillis))
            }
            .font(.caption.monospacedDigit())
            .foregroundColor(.secondary)
        }
    }

    private var transport: some View {
        HStack {
            Button(action: engine.toggleShuffle) {
                Image(systemName: "shuffle").font(.system(size: 20))
                    .foregroundColor(engine.state.isShuffled ? .accentColor : .secondary).frame(width: 48, height: 48)
            }
            .accessibilityLabel(engine.state.isShuffled ? "Shuffle on" : "Shuffle off")
            Spacer()
            Button(action: engine.playPrevious) {
                Image(systemName: "backward.fill").font(.system(size: 28)).foregroundColor(.primary).frame(width: 56, height: 56)
            }
            .accessibilityLabel("Previous track")
            Spacer()
            RingPlayButton(isPlaying: engine.state.status == .playing, action: engine.togglePlayPause)
            Spacer()
            Button(action: engine.playNext) {
                Image(systemName: "forward.fill").font(.system(size: 28)).foregroundColor(.primary).frame(width: 56, height: 56)
            }
            .accessibilityLabel("Next track")
            Spacer()
            Button(action: engine.cycleRepeatMode) {
                Image(systemName: engine.state.repeatMode.iconName).font(.system(size: 20))
                    .foregroundColor(engine.state.repeatMode != .off ? .accentColor : .secondary).frame(width: 48, height: 48)
            }
            .accessibilityLabel("Repeat mode: \(engine.state.repeatMode.rawValue)")
        }
    }

    private var shortcuts: some View {
        HStack {
            Spacer()
            PlayerShortcut(
                systemImage: "list.bullet", title: "Queue",
                badge: engine.queue.count > 1 ? "\(engine.queue.count)" : nil
            ) { showQueue = true }
            Spacer()
            PlayerShortcut(systemImage: "text.quote", title: "Lyrics", active: showLyrics) { showLyrics.toggle() }
            Spacer()
            PlayerShortcut(systemImage: "slider.vertical.3", title: "Equalizer", active: engine.audioSettings.equalizerOn) { showEqualizer = true }
            Spacer()
            speedMenu(icon: nil)
            Spacer()
        }
    }

    /// The speed choices; shown as the ellipsis in the header and as a "Speed" shortcut.
    private func speedMenu(icon: String?) -> some View {
        Menu {
            ForEach(PlaybackPreferences.speedPresets.map(Double.init), id: \.self) { speed in
                Button(action: { engine.setPlaybackRate(Float(speed)) }) {
                    HStack {
                        Text(String(format: "%.2fx", speed))
                        if abs(Double(engine.state.playbackRate) - speed) < 0.01 { Image(systemName: "checkmark") }
                    }
                }
            }
        } label: {
            if let icon {
                Image(systemName: icon).font(.system(size: 20, weight: .semibold)).foregroundColor(.primary).frame(width: 44, height: 44)
            } else {
                VStack(spacing: 4) {
                    Text(String(format: "%.2gx", engine.state.playbackRate)).font(.system(size: 17, weight: .semibold))
                    Text("Speed").font(.caption2)
                }
                .foregroundColor(.secondary).frame(minWidth: 64, minHeight: 44)
            }
        }
        .accessibilityLabel("Playback speed \(String(format: "%.2fx", engine.state.playbackRate))")
    }

    // MARK: Helpers

    private func formatText(for item: MediaItem) -> String? {
        let ext = URL(string: item.uri)?.pathExtension.uppercased() ?? ""
        return ext.isEmpty ? nil : ext
    }

    /// Reads the real peaks of the current track in the background; the seek bar is a plain line until they arrive.
    private func loadPeaks() async {
        peaks = nil
        guard let item = engine.state.currentItem, item.kind == .audio, let url = URL(string: item.uri) else { return }
        let loaded = await WaveformLoader.shared.peaks(for: url)
        if engine.state.currentItem?.id == item.id { peaks = loaded }
    }

    private func refreshFavorite() {
        guard let id = engine.state.currentItem?.id else { isFavorite = false; return }
        isFavorite = IOSDatabase.shared.getFavorites().contains(id)
    }

    private func toggleFavorite() {
        guard let id = engine.state.currentItem?.id else { return }
        isFavorite.toggle()
        IOSDatabase.shared.setFavorite(mediaId: id, isFavorite: isFavorite)
    }
}
