import SwiftUI

public struct HomeView: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    public let onSelectTab: (Int) -> Void
    private let onOpenVideo: OpenVideoAction
    @State private var continueWatching: [(item: MediaItem, history: PlaybackHistory)] = []
    @State private var recentItems: [MediaItem] = []
    @State private var recentMusic: [MediaItem] = []
    @State private var recentVideos: [MediaItem] = []
    @State private var favoriteItems: [MediaItem] = []
    @State private var favorites = Set<String>()
    @State private var playlists: [Playlist] = []
    @State private var showFileImporter = false

    public init(engine: AVPlayerPlaybackEngine, onSelectTab: @escaping (Int) -> Void, onOpenVideo: @escaping OpenVideoAction) {
        self.engine = engine
        self.onSelectTab = onSelectTab
        self.onOpenVideo = onOpenVideo
    }

    private var isEmpty: Bool {
        continueWatching.isEmpty && recentItems.isEmpty && recentMusic.isEmpty
            && recentVideos.isEmpty && favoriteItems.isEmpty && playlists.isEmpty
    }

    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Good to see you")
                            .font(.title.weight(.semibold))
                        Text("Your music and videos, private and on this device.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 20)

                    if !continueWatching.isEmpty {
                        section("Continue watching") {
                            horizontalRow {
                                ForEach(Array(continueWatching.enumerated()), id: \.element.item.id) { index, entry in
                                    VideoCard(item: entry.item, width: 220, progress: progress(entry)) {
                                        onOpenVideo(continueWatching.map(\.item), index, entry.history.positionMillis)
                                    }
                                }
                            }
                        }
                    }

                    if !recentItems.isEmpty {
                        section("Recently played") { mediaCards(recentItems) }
                    }
                    if !recentMusic.isEmpty {
                        section("Recently added music") { mediaCards(recentMusic) }
                    }
                    if !recentVideos.isEmpty {
                        section("Recently added videos") { mediaCards(recentVideos) }
                    }

                    if !favoriteItems.isEmpty {
                        section("Favorites") {
                            VStack(spacing: 0) {
                                ForEach(favoriteItems.prefix(5)) { item in
                                    MediaRow(
                                        item: item,
                                        isPlaying: engine.state.currentItem?.id == item.id,
                                        isFavorite: true,
                                        onPlay: { play(item, in: favoriteItems) },
                                        onToggleFavorite: { unfavorite(item) }
                                    )
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)

                                    if item.id != favoriteItems.prefix(5).last?.id {
                                        Divider().padding(.leading, 16)
                                    }
                                }
                            }
                            .background(Color(uiColor: .secondarySystemGroupedBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                            .padding(.horizontal, 20)
                        }
                    }

                    if !playlists.isEmpty {
                        section("Quick playlists") {
                            horizontalRow {
                                ForEach(playlists) { playlist in
                                    Button(action: { onSelectTab(3) }) {
                                        VStack(alignment: .leading, spacing: 20) {
                                            Image(systemName: "music.note.list")
                                                .font(.title3)
                                                .foregroundColor(.accentColor)
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(playlist.name)
                                                    .font(.headline)
                                                    .lineLimit(1)
                                                Text("\(playlist.mediaIds.count) items")
                                                    .font(.caption)
                                                    .foregroundColor(.secondary)
                                            }
                                        }
                                        .padding(18)
                                        .frame(width: 180, alignment: .leading)
                                        .background(Color(uiColor: .secondarySystemGroupedBackground))
                                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }

                    importCard

                    if isEmpty {
                        VStack(spacing: 10) {
                            Image(systemName: "smallcircle.filled.circle")
                                .font(.system(size: 44))
                                .foregroundColor(.accentColor)
                            Text("Your home is ready")
                                .font(.headline)
                            Text("Media and playback activity will appear here as you use Vimivo.")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 32)
                        .padding(.top, 24)
                    }
                }
                .padding(.vertical, 8)
                .padding(.bottom, 72)
            }
            .navigationTitle("Home")
            .navigationBarTitleDisplayMode(.inline)
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [.audio, .movie, .video],
                allowsMultipleSelection: true
            ) { result in
                handleImport(result: result)
            }
            .onAppear(perform: loadData)
            .onChange(of: engine.state.currentItem?.id) { _ in loadData() }
        }
        .navigationViewStyle(.stack)
    }

    private var importCard: some View {
        Button(action: { showFileImporter = true }) {
            HStack(spacing: 16) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.accentColor)
                    .frame(width: 44, height: 44)
                    .background(Color.accentColor.opacity(0.15), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text("Import media")
                        .font(.headline)
                    Text("Add audio and video from Files or iCloud Drive")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.secondary)
            }
            .padding(16)
            .background(Color(uiColor: .secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 20)
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.title3.weight(.semibold))
                .padding(.horizontal, 20)
                .accessibilityAddTraits(.isHeader)
            content()
        }
    }

    private func horizontalRow<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: 14) {
                content()
            }
            .padding(.horizontal, 20)
        }
    }

    @ViewBuilder
    private func mediaCards(_ items: [MediaItem]) -> some View {
        horizontalRow {
            ForEach(items) { item in
                if item.kind == .video {
                    VideoCard(item: item, width: 196) { play(item, in: items) }
                } else {
                    Button(action: { play(item, in: items) }) {
                        VStack(alignment: .leading, spacing: 6) {
                            ArtworkThumbnailView(uri: item.artworkUri, kind: item.kind, size: 140)
                            Text(item.title)
                                .font(.subheadline.weight(.medium))
                                .foregroundColor(.primary)
                                .lineLimit(1)
                            Text(item.artist ?? item.album ?? " ")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                        .frame(width: 140, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Play \(item.title)")
                }
            }
        }
    }

    private func progress(_ entry: (item: MediaItem, history: PlaybackHistory)) -> Double? {
        guard let duration = entry.item.durationMillis, duration > 0 else { return nil }
        return Double(entry.history.positionMillis) / Double(duration)
    }

    /// Videos open the full-screen player with their kind-mates queued; audio plays in place.
    private func play(_ item: MediaItem, in list: [MediaItem]) {
        let sameKind = list.filter { $0.kind == item.kind }
        let index = sameKind.firstIndex(where: { $0.id == item.id }) ?? 0
        if item.kind == .video {
            onOpenVideo(sameKind, index, 0)
        } else {
            engine.playQueue(items: sameKind, startIndex: index)
        }
    }

    private func unfavorite(_ item: MediaItem) {
        IOSDatabase.shared.setFavorite(mediaId: item.id, isFavorite: false)
        loadData()
    }

    private func loadData() {
        let allItems = IOSDatabase.shared.getAllMediaItems()
        let itemMap = Dictionary(allItems.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let history = IOSDatabase.shared.getHistory()

        continueWatching = history
            .filter { $0.positionMillis > 0 && !$0.completed }
            .compactMap { entry in itemMap[entry.mediaId].flatMap { $0.kind == .video ? ($0, entry) : nil } }
            .prefix(10)
            .map { $0 }
        recentItems = Array(history.compactMap { itemMap[$0.mediaId] }.prefix(12))

        let newestFirst = allItems.sorted { ($0.dateAddedMillis ?? 0) > ($1.dateAddedMillis ?? 0) }
        recentMusic = Array(newestFirst.filter { $0.kind == .audio }.prefix(12))
        recentVideos = Array(newestFirst.filter { $0.kind == .video }.prefix(12))

        favorites = IOSDatabase.shared.getFavorites()
        favoriteItems = allItems.filter { favorites.contains($0.id) }
        playlists = IOSDatabase.shared.getAllPlaylists()
    }

    private func handleImport(result: Result<[URL], Error>) {
        guard case .success(let urls) = result else { return }
        Task {
            for url in urls {
                let item = await SecurityScopedMediaManager.shared.extractMediaItem(from: url)
                await MainActor.run {
                    IOSDatabase.shared.saveMediaItem(item)
                    loadData()
                }
            }
        }
    }
}
