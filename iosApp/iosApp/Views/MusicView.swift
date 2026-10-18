import SwiftUI

enum MusicFilter: String, CaseIterable {
    case songs = "All songs"
    case albums = "Albums"
    case artists = "Artists"
    case recentlyAdded = "Recently added"
    case recentlyPlayed = "Recently played"
    case favorites = "Favorites"
}

public struct MusicView: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    @State private var items: [MediaItem] = []
    @State private var favorites = Set<String>()
    @State private var searchText = ""
    @State private var filter: MusicFilter = .songs
    @State private var history: [PlaybackHistory] = []
    @State private var showFileImporter = false

    public init(engine: AVPlayerPlaybackEngine) {
        self.engine = engine
    }

    private var filteredItems: [MediaItem] {
        let musicItems = searchedItems
        switch filter {
        case .recentlyAdded:
            return musicItems.sorted { ($0.dateAddedMillis ?? 0) > ($1.dateAddedMillis ?? 0) }
        case .recentlyPlayed:
            let byId = Dictionary(musicItems.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            return history.compactMap { byId[$0.mediaId] }
        case .favorites:
            return musicItems.filter { favorites.contains($0.id) }
        case .songs, .albums, .artists:
            return musicItems
        }
    }

    private var searchedItems: [MediaItem] {
        let musicItems = items.filter { $0.kind == .audio }
        if searchText.isEmpty {
            return musicItems
        }
        return musicItems.filter {
            $0.title.localizedCaseInsensitiveContains(searchText) ||
            ($0.artist?.localizedCaseInsensitiveContains(searchText) ?? false) ||
            ($0.album?.localizedCaseInsensitiveContains(searchText) ?? false)
        }
    }

    private var albums: [String: [MediaItem]] {
        Dictionary(grouping: filteredItems) { $0.album ?? "Unknown Album" }
    }

    private var artists: [String: [MediaItem]] {
        Dictionary(grouping: filteredItems) { $0.artist ?? "Unknown Artist" }
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                FilterChipRow(options: MusicFilter.allCases, selection: $filter) { $0.rawValue }
                    .padding(.vertical, 4)

                if filteredItems.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "music.note")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text(items.isEmpty ? "No music imported" : "Nothing here yet")
                            .font(.headline)
                        Text(items.isEmpty ? "Import audio files from the Files app or iCloud Drive." : "Songs matching this filter or search will appear here.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                        if items.isEmpty {
                            Button(action: { showFileImporter = true }) {
                                Label("Import Music", systemImage: "plus")
                                    .fontWeight(.semibold)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(Color.accentColor)
                                    .foregroundColor(.white)
                                    .clipShape(Capsule())
                            }
                            .padding(.top, 4)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    switch filter {
                    case .albums:
                        albumsGrid
                    case .artists:
                        artistsList
                    default:
                        songsList
                    }
                }
            }
            .navigationTitle("Music")
            .searchable(text: $searchText, prompt: "Search music")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showFileImporter = true }) {
                        Image(systemName: "plus")
                    }
                }
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [.audio, .mp3, .wav, .aiff],
                allowsMultipleSelection: true
            ) { result in
                handleImport(result: result)
            }
            .onAppear(perform: loadData)
            .onReceive(NotificationCenter.default.publisher(for: .vimivoLibraryDidChange)) { _ in loadData() }
        }
    }

    private var songsList: some View {
        List {
            Section {
                HStack(spacing: 12) {
                    Button(action: { engine.playQueue(items: filteredItems, startIndex: 0) }) {
                        HStack {
                            Image(systemName: "play.fill")
                            Text("Play All (\(filteredItems.count))")
                        }
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color.accentColor)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        var shuffled = filteredItems
                        shuffled.shuffle()
                        engine.playQueue(items: shuffled, startIndex: 0)
                    }) {
                        HStack {
                            Image(systemName: "shuffle")
                            Text("Shuffle")
                        }
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color.secondary.opacity(0.15))
                        .foregroundColor(.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                .listRowSeparator(.hidden)
            }

            Section {
                ForEach(filteredItems) { item in
                    MediaRow(
                        item: item,
                        isPlaying: engine.state.currentItem?.id == item.id,
                        isFavorite: favorites.contains(item.id),
                        onPlay: { engine.play(item: item) },
                        onPlayNext: { engine.queue.insert(item, at: min(engine.currentIndex + 1, engine.queue.count)) },
                        onAddToQueue: { engine.appendToQueue(item: item) },
                        onToggleFavorite: { toggleFavorite(item) },
                        onDelete: { deleteItem(item) }
                    )
                }
            }
        }
        .listStyle(.plain)
    }

    private var albumsGrid: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 16)], spacing: 16) {
                ForEach(Array(albums.keys.sorted()), id: \.self) { albumName in
                    let albumTracks = albums[albumName] ?? []
                    VStack(alignment: .leading, spacing: 6) {
                        ArtworkThumbnailView(uri: albumTracks.first?.artworkUri, kind: .audio, size: 150)

                        Text(albumName)
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .lineLimit(1)

                        Text("\(albumTracks.count) songs")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        engine.playQueue(items: albumTracks, startIndex: 0)
                    }
                }
            }
            .padding(16)
        }
    }

    private var artistsList: some View {
        List {
            ForEach(Array(artists.keys.sorted()), id: \.self) { artistName in
                let artistTracks = artists[artistName] ?? []
                HStack(spacing: 12) {
                    ArtworkThumbnailView(uri: artistTracks.first?.artworkUri, kind: .audio, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(artistName)
                            .font(.body)
                            .fontWeight(.medium)
                        Text("\(artistTracks.count) songs")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }
                .contentShape(Rectangle())
                .onTapGesture {
                    engine.playQueue(items: artistTracks, startIndex: 0)
                }
            }
        }
        .listStyle(.plain)
    }

    private func loadData() {
        items = IOSDatabase.shared.getAllMediaItems()
        favorites = IOSDatabase.shared.getFavorites()
        history = IOSDatabase.shared.getHistory()
    }

    private func toggleFavorite(_ item: MediaItem) {
        let isFav = favorites.contains(item.id)
        IOSDatabase.shared.setFavorite(mediaId: item.id, isFavorite: !isFav)
        loadData()
    }

    private func deleteItem(_ item: MediaItem) {
        IOSDatabase.shared.deleteMediaItem(id: item.id)
        loadData()
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
