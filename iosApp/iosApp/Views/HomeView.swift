import SwiftUI

public struct HomeView: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    public let onSelectTab: (Int) -> Void
    @State private var recentItems: [MediaItem] = []
    @State private var favoriteItems: [MediaItem] = []
    @State private var totalSongs = 0
    @State private var totalVideos = 0
    @State private var showFileImporter = false

    public init(engine: AVPlayerPlaybackEngine, onSelectTab: @escaping (Int) -> Void) {
        self.engine = engine
        self.onSelectTab = onSelectTab
    }

    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Quick Stats & Hero
                    HStack(spacing: 16) {
                        Button(action: { onSelectTab(1) }) {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Image(systemName: "music.note")
                                        .font(.title2)
                                        .foregroundColor(.accentColor)
                                    Spacer()
                                    Text("\(totalSongs)")
                                        .font(.title3)
                                        .fontWeight(.bold)
                                }
                                Text("Music Tracks")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            .padding(16)
                            .frame(maxWidth: .infinity)
                            .background(Color(uiColor: .secondarySystemGroupedBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        .buttonStyle(.plain)

                        Button(action: { onSelectTab(2) }) {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Image(systemName: "film")
                                        .font(.title2)
                                        .foregroundColor(.accentColor)
                                    Spacer()
                                    Text("\(totalVideos)")
                                        .font(.title3)
                                        .fontWeight(.bold)
                                }
                                Text("Videos")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            .padding(16)
                            .frame(maxWidth: .infinity)
                            .background(Color(uiColor: .secondarySystemGroupedBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 16)

                    // Import Quick Action
                    Button(action: { showFileImporter = true }) {
                        HStack(spacing: 14) {
                            ZStack {
                                Circle()
                                    .fill(Color.accentColor.opacity(0.15))
                                    .frame(width: 44, height: 44)
                                Image(systemName: "plus")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(.accentColor)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                Text("Import Media")
                                    .font(.headline)
                                Text("Add audio and video from Files or iCloud")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }

                            Spacer()

                            Image(systemName: "chevron.right")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.secondary)
                        }
                        .padding(14)
                        .background(Color(uiColor: .secondarySystemGroupedBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 16)

                    // Recently Played
                    if !recentItems.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Recently Played")
                                .font(.title3)
                                .fontWeight(.bold)
                                .padding(.horizontal, 16)

                            ScrollView(.horizontal, showsIndicators: false) {
                                LazyHStack(spacing: 14) {
                                    ForEach(recentItems) { item in
                                        VStack(alignment: .leading, spacing: 6) {
                                            ArtworkThumbnailView(uri: item.artworkUri, kind: item.kind, size: 120)

                                            Text(item.title)
                                                .font(.caption)
                                                .fontWeight(.medium)
                                                .lineLimit(1)
                                                .frame(width: 120, alignment: .leading)

                                            if let artist = item.artist {
                                                Text(artist)
                                                    .font(.caption2)
                                                    .foregroundColor(.secondary)
                                                    .lineLimit(1)
                                                    .frame(width: 120, alignment: .leading)
                                            }
                                        }
                                        .contentShape(Rectangle())
                                        .onTapGesture {
                                            engine.play(item: item)
                                        }
                                    }
                                }
                                .padding(.horizontal, 16)
                            }
                        }
                    }

                    // Favorites Section
                    if !favoriteItems.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Favorites")
                                .font(.title3)
                                .fontWeight(.bold)
                                .padding(.horizontal, 16)

                            VStack(spacing: 0) {
                                ForEach(favoriteItems.prefix(5)) { item in
                                    MediaRow(
                                        item: item,
                                        isPlaying: engine.state.currentItem?.id == item.id,
                                        isFavorite: true,
                                        onPlay: { engine.play(item: item) }
                                    )
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)

                                    if item.id != favoriteItems.prefix(5).last?.id {
                                        Divider().padding(.leading, 78)
                                    }
                                }
                            }
                            .background(Color(uiColor: .secondarySystemGroupedBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .padding(.horizontal, 16)
                        }
                    }
                }
                .padding(.vertical, 16)
            }
            .navigationTitle("Vimivo")
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [.audio, .movie, .video],
                allowsMultipleSelection: true
            ) { result in
                handleImport(result: result)
            }
            .onAppear(perform: loadData)
        }
    }

    private func loadData() {
        let allItems = IOSDatabase.shared.getAllMediaItems()
        let itemMap = Dictionary(uniqueKeysWithValues: allItems.map { ($0.id, $0) })
        totalSongs = allItems.filter { $0.kind == .audio }.count
        totalVideos = allItems.filter { $0.kind == .video }.count

        let history = IOSDatabase.shared.getHistory()
        recentItems = history.compactMap { itemMap[$0.mediaId] }

        let favorites = IOSDatabase.shared.getFavorites()
        favoriteItems = favorites.compactMap { itemMap[$0] }
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
