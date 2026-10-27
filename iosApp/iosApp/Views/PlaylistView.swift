import SwiftUI
import UniformTypeIdentifiers

public struct PlaylistView: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    @State private var playlists: [Playlist] = []
    @State private var showCreateDialog = false
    @State private var newPlaylistName = ""
    @State private var selectedPlaylist: Playlist?
    @State private var showM3UImporter = false

    public init(engine: AVPlayerPlaybackEngine) {
        self.engine = engine
    }

    public var body: some View {
        NavigationView {
            List {
                if playlists.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "music.note.list")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text("No Playlists")
                            .font(.headline)
                        Text("Create your first playlist or import an M3U file to organize your tracks.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                        HStack(spacing: 12) {
                            Button(action: { showCreateDialog = true }) {
                                Text("Create Playlist")
                                    .fontWeight(.semibold)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(Color.accentColor)
                                    .foregroundColor(.white)
                                    .clipShape(Capsule())
                            }

                            Button(action: { showM3UImporter = true }) {
                                Text("Import M3U")
                                    .fontWeight(.semibold)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(Color.secondary.opacity(0.15))
                                    .foregroundColor(.primary)
                                    .clipShape(Capsule())
                            }
                        }
                        .padding(.top, 4)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
                    .listRowBackground(Color.clear)
                } else {
                    ForEach(playlists) { playlist in
                        NavigationLink(destination: PlaylistDetailView(playlist: playlist, engine: engine, onUpdate: loadPlaylists)) {
                            HStack(spacing: 14) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(Color.accentColor.opacity(0.15))
                                        .frame(width: 48, height: 48)
                                    Image(systemName: "music.note.list")
                                        .foregroundColor(.accentColor)
                                        .font(.system(size: 20))
                                }

                                VStack(alignment: .leading, spacing: 3) {
                                    Text(playlist.name)
                                        .font(.body)
                                        .fontWeight(.medium)
                                    Text("\(playlist.mediaIds.count) items")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                    .onDelete(perform: deletePlaylists)
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Playlists")
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button(action: { showM3UImporter = true }) {
                        Image(systemName: "square.and.arrow.down")
                    }
                    .accessibilityLabel("Import M3U")

                    Button(action: { showCreateDialog = true }) {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Create Playlist")
                }
            }
            .fileImporter(
                isPresented: $showM3UImporter,
                allowedContentTypes: [.plainText, UTType(filenameExtension: "m3u") ?? .plainText, UTType(filenameExtension: "m3u8") ?? .plainText],
                allowsMultipleSelection: false
            ) { result in
                handleM3UImport(result: result)
            }
            .alert("New Playlist", isPresented: $showCreateDialog) {
                TextField("Playlist Name", text: $newPlaylistName)
                Button("Cancel", role: .cancel) { newPlaylistName = "" }
                Button("Create") {
                    let trimmed = newPlaylistName.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty {
                        let newPl = Playlist(name: trimmed)
                        IOSDatabase.shared.savePlaylist(newPl)
                        loadPlaylists()
                        newPlaylistName = ""
                    }
                }
            }
            .onAppear(perform: loadPlaylists)
        }
    }

    private func loadPlaylists() {
        playlists = IOSDatabase.shared.getAllPlaylists()
    }

    private func handleM3UImport(result: Result<[URL], Error>) {
        guard case .success(let urls) = result, let url = urls.first else { return }
        guard url.startAccessingSecurityScopedResource() else {
            readAndImportM3U(url: url)
            return
        }
        defer { url.stopAccessingSecurityScopedResource() }
        readAndImportM3U(url: url)
    }

    private func readAndImportM3U(url: URL) {
        guard let text = try? String(contentsOf: url, encoding: .utf8) ?? String(contentsOf: url, encoding: .isoLatin1) else { return }
        let defaultName = url.deletingPathExtension().lastPathComponent
        let parsed = M3UPlaylistManager.parseM3U(content: text, defaultName: defaultName)
        for item in parsed.items {
            IOSDatabase.shared.saveMediaItem(item)
        }
        let playlist = Playlist(name: parsed.name, mediaIds: parsed.items.map { $0.id })
        IOSDatabase.shared.savePlaylist(playlist)
        loadPlaylists()
    }

    private func deletePlaylists(at offsets: IndexSet) {
        for index in offsets {
            let id = playlists[index].id
            IOSDatabase.shared.deletePlaylist(id: id)
        }
        loadPlaylists()
    }
}

public struct PlaylistDetailView: View {
    public var playlist: Playlist
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    public let onUpdate: () -> Void
    @State private var items: [MediaItem] = []
    @State private var showExportShare = false
    @State private var exportText = ""

    public var body: some View {
        List {
            if items.isEmpty {
                VStack(spacing: 12) {
                    Text("Empty Playlist")
                        .font(.headline)
                    Text("Add songs from the Music library using the item menu.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 32)
                .listRowBackground(Color.clear)
            } else {
                Section {
                    Button(action: { engine.playQueue(items: items, startIndex: 0) }) {
                        HStack {
                            Image(systemName: "play.fill")
                            Text("Play All (\(items.count))")
                                .fontWeight(.semibold)
                        }
                        .foregroundColor(.accentColor)
                    }
                }

                Section {
                    ForEach(items) { item in
                        MediaRow(
                            item: item,
                            isPlaying: engine.state.currentItem?.id == item.id,
                            onPlay: { engine.playQueue(items: items, startIndex: items.firstIndex(of: item) ?? 0) },
                            onDelete: { removeItem(item) }
                        )
                    }
                }
            }
        }
        .navigationTitle(playlist.name)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    exportText = M3UPlaylistManager.exportM3U(name: playlist.name, items: items)
                    showExportShare = true
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
                .accessibilityLabel("Export M3U")
                .disabled(items.isEmpty)
            }
        }
        .sheet(isPresented: $showExportShare) {
            ShareSheet(activityItems: [exportText])
        }
        .onAppear(perform: loadItems)
    }

    private func loadItems() {
        let all = IOSDatabase.shared.getAllMediaItems()
        let dict = Dictionary(uniqueKeysWithValues: all.map { ($0.id, $0) })
        items = playlist.mediaIds.compactMap { dict[$0] }
    }

    private func removeItem(_ item: MediaItem) {
        var updated = playlist
        updated.mediaIds.removeAll { $0 == item.id }
        IOSDatabase.shared.savePlaylist(updated)
        loadItems()
        onUpdate()
    }
}
