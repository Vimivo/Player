import SwiftUI

public typealias OpenVideoAction = (_ items: [MediaItem], _ index: Int, _ resumeMillis: Int64) -> Void

enum VideoFilter: String, CaseIterable {
    case all = "All videos"
    case continueWatching = "Continue watching"
    case recentlyWatched = "Recently watched"
    case recentlyAdded = "Recently added"
    case favorites = "Favorites"
}

public struct VideoView: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    private let onOpenVideo: OpenVideoAction
    @State private var items: [MediaItem] = []
    @State private var history: [PlaybackHistory] = []
    @State private var favorites = Set<String>()
    @State private var searchText = ""
    @State private var showFileImporter = false
    @State private var showOpenURL = false
    @State private var filter: VideoFilter = .all
    @AppStorage("vimivo_video_grid") private var gridMode = false

    public init(engine: AVPlayerPlaybackEngine, onOpenVideo: @escaping OpenVideoAction) {
        self.engine = engine
        self.onOpenVideo = onOpenVideo
    }

    private var historyMap: [String: PlaybackHistory] {
        Dictionary(history.map { ($0.mediaId, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private var visibleVideos: [MediaItem] {
        let videos = items.filter { $0.kind == .video }
        let searched = searchText.isEmpty ? videos : videos.filter { $0.title.localizedCaseInsensitiveContains(searchText) }
        let byId = Dictionary(searched.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        switch filter {
        case .all:
            return searched
        case .continueWatching:
            return history.filter { $0.positionMillis > 0 && !$0.completed }.compactMap { byId[$0.mediaId] }
        case .recentlyWatched:
            return history.compactMap { byId[$0.mediaId] }
        case .recentlyAdded:
            return searched.sorted { ($0.dateAddedMillis ?? 0) > ($1.dateAddedMillis ?? 0) }
        case .favorites:
            return searched.filter { favorites.contains($0.id) }
        }
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                FilterChipRow(options: VideoFilter.allCases, selection: $filter) { $0.rawValue }
                    .padding(.vertical, 4)

                let videos = visibleVideos
                if videos.isEmpty {
                    emptyState
                } else if gridMode {
                    grid(videos)
                } else {
                    list(videos)
                }
            }
            .navigationTitle("Videos")
            .searchable(text: $searchText, prompt: "Search videos")
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button(action: { showOpenURL = true }) {
                        Image(systemName: "link")
                    }
                    .accessibilityLabel("Open URL")
                    Button(action: { gridMode.toggle() }) {
                        Image(systemName: gridMode ? "list.bullet" : "square.grid.2x2")
                    }
                    .accessibilityLabel(gridMode ? "List view" : "Grid view")
                    Button(action: { showFileImporter = true }) {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Import videos")
                }
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [.movie, .video, .quickTimeMovie, .mpeg4Movie],
                allowsMultipleSelection: true
            ) { result in
                handleImport(result: result)
            }
            .sheet(isPresented: $showOpenURL) {
                OpenURLSheet { item in
                    showOpenURL = false
                    onOpenVideo([item], 0, 0)
                }
            }
            .onAppear(perform: loadData)
            .onChange(of: engine.state.currentItem?.id) { _ in loadData() }
            .task { await backfillMetadata() }
        }
        .navigationViewStyle(.stack)
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "film")
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            Text(items.contains { $0.kind == .video } ? "Nothing here yet" : "No videos imported")
                .font(.headline)
            Text(items.contains { $0.kind == .video }
                 ? "Videos matching this filter will appear here."
                 : "Import video files from the Files app or iCloud Drive.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            if !items.contains(where: { $0.kind == .video }) {
                Button(action: { showFileImporter = true }) {
                    Label("Import videos", systemImage: "plus")
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
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func list(_ videos: [MediaItem]) -> some View {
        List {
            ForEach(Array(videos.enumerated()), id: \.element.id) { index, video in
                MediaRow(
                    item: video,
                    isPlaying: engine.state.currentItem?.id == video.id,
                    isFavorite: favorites.contains(video.id),
                    onPlay: { play(videos, index) },
                    onPlayNext: { engine.queue.insert(video, at: min(engine.currentIndex + 1, engine.queue.count)) },
                    onAddToQueue: { engine.appendToQueue(item: video) },
                    onToggleFavorite: { toggleFavorite(video) },
                    onDelete: { deleteVideo(video) },
                    progress: resumeProgress(video)
                )
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 12))
            }
        }
        .listStyle(.plain)
    }

    private func grid(_ videos: [MediaItem]) -> some View {
        GeometryReader { geo in
            let columns = max(Int((geo.size.width - 16) / 236), 1)
            let width = (geo.size.width - 32 - CGFloat(columns - 1) * 16) / CGFloat(columns)
            ScrollView {
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(width), spacing: 16), count: columns), spacing: 18) {
                    ForEach(Array(videos.enumerated()), id: \.element.id) { index, video in
                        VideoCard(item: video, width: width, progress: resumeProgress(video)) {
                            play(videos, index)
                        }
                        .contextMenu {
                            Button(action: { toggleFavorite(video) }) {
                                Label(favorites.contains(video.id) ? "Remove from Favorites" : "Favorite", systemImage: "heart")
                            }
                            Button(role: .destructive, action: { deleteVideo(video) }) {
                                Label("Remove", systemImage: "trash")
                            }
                        }
                    }
                }
                .padding(16)
            }
        }
    }

    private func resumeProgress(_ video: MediaItem) -> Double? {
        guard let entry = historyMap[video.id], entry.positionMillis > 0, !entry.completed,
              let duration = video.durationMillis, duration > 0 else { return nil }
        return Double(entry.positionMillis) / Double(duration)
    }

    private func play(_ videos: [MediaItem], _ index: Int) {
        let entry = historyMap[videos[index].id]
        let resume = (entry?.completed == false) ? (entry?.positionMillis ?? 0) : 0
        onOpenVideo(videos, index, resume)
    }

    private func loadData() {
        items = IOSDatabase.shared.getAllMediaItems()
        history = IOSDatabase.shared.getHistory()
        favorites = IOSDatabase.shared.getFavorites()
    }

    /// Fills in resolution and size for videos imported before those columns existed.
    private func backfillMetadata() async {
        let missing = items.filter { $0.kind == .video && ($0.width == nil || $0.sizeBytes == nil) }
        guard !missing.isEmpty else { return }
        for video in missing {
            guard let meta = await SecurityScopedMediaManager.shared.loadVideoMetadata(for: video) else { continue }
            IOSDatabase.shared.updateVideoMetadata(id: video.id, width: meta.width, height: meta.height, sizeBytes: meta.sizeBytes)
        }
        loadData()
    }

    private func toggleFavorite(_ video: MediaItem) {
        IOSDatabase.shared.setFavorite(mediaId: video.id, isFavorite: !favorites.contains(video.id))
        loadData()
    }

    private func deleteVideo(_ video: MediaItem) {
        IOSDatabase.shared.deleteMediaItem(id: video.id)
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

/// Plays an explicitly entered HTTPS stream (for example HLS) without adding it to the library.
private struct OpenURLSheet: View {
    let onOpen: (MediaItem) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var address = ""
    @FocusState private var focused: Bool

    private var streamURL: URL? {
        let trimmed = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed), url.scheme?.lowercased() == "https", url.host != nil else { return nil }
        return url
    }

    var body: some View {
        NavigationView {
            Form {
                Section {
                    TextField("https://example.com/video.m3u8", text: $address)
                        .keyboardType(.URL)
                        .textContentType(.URL)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .submitLabel(.go)
                        .focused($focused)
                        .onSubmit(open)
                } footer: {
                    Text("Play an HTTPS or HLS link you are authorized to stream. Links are not saved or shared.")
                }
            }
            .navigationTitle("Open URL")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Play", action: open).disabled(streamURL == nil)
                }
            }
            .onAppear { focused = true }
        }
        .navigationViewStyle(.stack)
    }

    private func open() {
        guard let url = streamURL else { return }
        let title = url.lastPathComponent.isEmpty || url.lastPathComponent == "/" ? (url.host ?? url.absoluteString) : url.lastPathComponent
        onOpen(MediaItem(id: url.absoluteString, uri: url.absoluteString, title: title, kind: .video))
    }
}
