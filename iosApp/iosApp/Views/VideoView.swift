import SwiftUI
import AVKit

public struct VideoView: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    @State private var items: [MediaItem] = []
    @State private var history: [PlaybackHistory] = []
    @State private var favorites = Set<String>()
    @State private var searchText = ""
    @State private var showFileImporter = false
    @State private var activeVideoItem: MediaItem?

    public init(engine: AVPlayerPlaybackEngine) {
        self.engine = engine
    }

    private var filteredVideos: [MediaItem] {
        let videos = items.filter { $0.kind == .video }
        if searchText.isEmpty {
            return videos
        }
        return videos.filter { $0.title.localizedCaseInsensitiveContains(searchText) }
    }

    private var historyMap: [String: PlaybackHistory] {
        Dictionary(uniqueKeysWithValues: history.map { ($0.mediaId, $0) })
    }

    public var body: some View {
        NavigationView {
            Group {
                if filteredVideos.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "film")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text(items.isEmpty ? "No Videos Imported" : "No Results")
                            .font(.headline)
                        Text(items.isEmpty ? "Import video files from the Files app or iCloud Drive." : "Try searching for a different video title.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                        if items.isEmpty {
                            Button(action: { showFileImporter = true }) {
                                Label("Import Video", systemImage: "plus")
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
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 160, maximum: 240), spacing: 16)], spacing: 16) {
                            ForEach(filteredVideos) { video in
                                let hist = historyMap[video.id]
                                VStack(alignment: .leading, spacing: 6) {
                                    ZStack(alignment: .bottomTrailing) {
                                        ArtworkThumbnailView(uri: video.artworkUri, kind: .video, size: 160)
                                            .aspectRatio(16/9, contentMode: .fit)

                                        if video.durationMillis != nil {
                                            Text(video.formattedDuration)
                                                .font(.caption2)
                                                .fontWeight(.bold)
                                                .padding(.horizontal, 6)
                                                .padding(.vertical, 3)
                                                .background(Color.black.opacity(0.75))
                                                .foregroundColor(.white)
                                                .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                                                .padding(6)
                                        }

                                        if let h = hist, h.positionMillis > 0, !h.completed {
                                            GeometryReader { geo in
                                                VStack {
                                                    Spacer()
                                                    Rectangle()
                                                        .fill(Color.accentColor)
                                                        .frame(
                                                            width: geo.size.width * CGFloat(Double(h.positionMillis) / Double(video.durationMillis ?? 1)),
                                                            height: 3
                                                        )
                                                }
                                            }
                                        }
                                    }
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                                    Text(video.title)
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                        .lineLimit(2)

                                    if let h = hist, h.positionMillis > 0, !h.completed {
                                        Text("Resume from \(formatMillis(h.positionMillis))")
                                            .font(.caption2)
                                            .foregroundColor(.accentColor)
                                    }
                                }
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    playVideo(video, resumeMillis: hist?.positionMillis ?? 0)
                                }
                                .contextMenu {
                                    Button(action: { playVideo(video, resumeMillis: 0) }) {
                                        Label("Play from Beginning", systemImage: "play.fill")
                                    }
                                    if let pos = hist?.positionMillis, pos > 0 {
                                        Button(action: { playVideo(video, resumeMillis: pos) }) {
                                            Label("Resume Playback", systemImage: "arrow.clockwise")
                                        }
                                    }
                                    Divider()
                                    Button(role: .destructive, action: { deleteVideo(video) }) {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                            }
                        }
                        .padding(16)
                    }
                }
            }
            .navigationTitle("Videos")
            .searchable(text: $searchText, prompt: "Search videos")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showFileImporter = true }) {
                        Image(systemName: "plus")
                    }
                }
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [.movie, .video, .quickTimeMovie, .mpeg4Movie],
                allowsMultipleSelection: true
            ) { result in
                handleImport(result: result)
            }
            .fullScreenCover(item: $activeVideoItem) { item in
                ZStack {
                    Color.black.ignoresSafeArea()
                    VideoPlayerView(player: engine.player)
                        .ignoresSafeArea()
                    VStack {
                        HStack {
                            Button(action: {
                                activeVideoItem = nil
                                engine.pause()
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 28))
                                    .foregroundColor(.white.opacity(0.85))
                                    .padding(16)
                            }
                            Spacer()
                        }
                        Spacer()
                    }
                }
            }
            .onAppear(perform: loadData)
        }
    }

    private func playVideo(_ video: MediaItem, resumeMillis: Int64) {
        engine.play(item: video)
        if resumeMillis > 0 {
            engine.seek(toMillis: resumeMillis)
        }
        activeVideoItem = video
    }

    private func loadData() {
        items = IOSDatabase.shared.getAllMediaItems()
        history = IOSDatabase.shared.getHistory()
        favorites = IOSDatabase.shared.getFavorites()
    }

    private func deleteVideo(_ video: MediaItem) {
        IOSDatabase.shared.deleteMediaItem(id: video.id)
        loadData()
    }

    private func formatMillis(_ ms: Int64) -> String {
        let total = Int(ms / 1000)
        let m = total / 60
        let s = total % 60
        return String(format: "%d:%02d", m, s)
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
