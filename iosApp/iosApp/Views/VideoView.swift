import SwiftUI
import UniformTypeIdentifiers
import VimivoShared

/// Opens the full-screen player; the engine decides the start position using the shared resume rules.
public typealias OpenVideoAction = (_ items: [MediaItem], _ index: Int) -> Void

public enum VideoFilter: String, CaseIterable {
    case all = "All videos"
    case continueWatching = "Continue watching"
    case recentlyWatched = "Recently watched"
    case recentlyAdded = "Recently added"
    case favorites = "Favorites"
}

public enum VideoSortBy: String, CaseIterable, Identifiable {
    case title = "Title"
    case date = "Date Added"
    case size = "File Size"
    case duration = "Duration"

    public var id: String { rawValue }
}

public enum VideoLibraryMode: String, CaseIterable {
    case folders = "Folders"
    case videos = "All Videos"
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
    @State private var libraryMode: VideoLibraryMode = .folders
    @State private var currentFolder: String? = nil

    // Sorting
    @AppStorage("vimivo_video_sort_by") private var sortBy: VideoSortBy = .title
    @AppStorage("vimivo_video_sort_asc") private var sortAscending: Bool = true

    // Multi-select
    @State private var isSelecting = false
    @State private var selectedIds = Set<String>()
    @State private var propertiesItem: MediaItem? = nil
    @State private var showAddToPlaylist = false
    @State private var showDeleteConfirmation = false
    @State private var shareUrls: [URL] = []
    @State private var showShareSheet = false
    @State private var showWifiTransfer = false

    // Private Vault & Hidden Folders
    @AppStorage("vimivo_private_vault_ids") private var privateMediaIdsData: String = ""
    @State private var privateVaultUnlocked = false
    @State private var showPinSheet = false
    @AppStorage("vimivo_hidden_folders") private var hiddenFoldersData: String = ""
    @AppStorage("vimivo_show_hidden") private var showHiddenFolders: Bool = false

    public init(engine: AVPlayerPlaybackEngine, onOpenVideo: @escaping OpenVideoAction) {
        self.engine = engine
        self.onOpenVideo = onOpenVideo
    }

    private var privateMediaIds: Set<String> {
        get { Set(privateMediaIdsData.components(separatedBy: ",").filter { !$0.isEmpty }) }
        nonmutating set { privateMediaIdsData = newValue.joined(separator: ",") }
    }

    private var hiddenFolders: Set<String> {
        get { Set(hiddenFoldersData.components(separatedBy: ",").filter { !$0.isEmpty }) }
        nonmutating set { hiddenFoldersData = newValue.joined(separator: ",") }
    }

    private var historyMap: [String: PlaybackHistory] {
        Dictionary(history.map { ($0.mediaId, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private func isNew(_ item: MediaItem) -> Bool {
        guard let added = item.dateAddedMillis else { return false }
        let sevenDaysAgo = Int64(Date().timeIntervalSince1970 * 1000) - (7 * 24 * 3600 * 1000)
        let isPlayed = historyMap[item.id]?.completed == true
        return added >= sevenDaysAgo && !isPlayed
    }

    private func folderName(for item: MediaItem) -> String {
        if privateMediaIds.contains(item.id) {
            return "Private Vault"
        }
        guard let url = URL(string: item.uri) else { return "Videos" }
        let dirName = url.deletingLastPathComponent().lastPathComponent
        return dirName.isEmpty || dirName == "/" || dirName == "file:" ? "Videos" : dirName
    }

    private var folderGroups: [(name: String, items: [MediaItem], newCount: Int, isPrivate: Bool)] {
        let videos = items.filter { $0.kind == .video }
        var groups: [String: [MediaItem]] = [:]

        for video in videos {
            let fName = folderName(for: video)
            if !showHiddenFolders && hiddenFolders.contains(fName) {
                continue
            }
            groups[fName, default: []].append(video)
        }

        return groups.map { (name, groupItems) in
            let newCount = groupItems.filter { isNew($0) }.count
            return (name: name, items: groupItems, newCount: newCount, isPrivate: name == "Private Vault")
        }
        .sorted { a, b in
            if a.isPrivate { return true }
            if b.isPrivate { return false }
            return a.name.localizedCaseInsensitiveCompare(b.name) == .orderedAscending
        }
    }

    private var sortedVisibleVideos: [MediaItem] {
        var videos = items.filter { $0.kind == .video }

        // Filter out private vault unless explicitly inside it or unlocked
        if currentFolder != "Private Vault" {
            videos = videos.filter { !privateMediaIds.contains($0.id) }
        }

        // Filter out hidden folders if not showing hidden
        if !showHiddenFolders {
            videos = videos.filter { !hiddenFolders.contains(folderName(for: $0)) }
        }

        // Folder drilldown
        if let folder = currentFolder {
            videos = videos.filter { folderName(for: $0) == folder }
        }

        // Search text
        if !searchText.isEmpty {
            videos = videos.filter { $0.title.localizedCaseInsensitiveContains(searchText) }
        }

        // Video filter tabs
        let byId = Dictionary(videos.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        switch filter {
        case .all:
            break
        case .continueWatching:
            videos = history.filter { $0.positionMillis > 0 && !$0.completed }.compactMap { byId[$0.mediaId] }
        case .recentlyWatched:
            videos = history.compactMap { byId[$0.mediaId] }
        case .recentlyAdded:
            videos.sort { ($0.dateAddedMillis ?? 0) > ($1.dateAddedMillis ?? 0) }
        case .favorites:
            videos = videos.filter { favorites.contains($0.id) }
        }

        // Primary sorting
        videos.sort { a, b in
            let asc = sortAscending
            switch sortBy {
            case .title:
                let comp = a.title.localizedCaseInsensitiveCompare(b.title)
                return asc ? (comp == .orderedAscending) : (comp == .orderedDescending)
            case .date:
                let d1 = a.dateAddedMillis ?? 0
                let d2 = b.dateAddedMillis ?? 0
                return asc ? (d1 < d2) : (d1 > d2)
            case .size:
                let s1 = a.sizeBytes ?? 0
                let s2 = b.sizeBytes ?? 0
                return asc ? (s1 < s2) : (s1 > s2)
            case .duration:
                let dur1 = a.durationMillis ?? 0
                let dur2 = b.durationMillis ?? 0
                return asc ? (dur1 < dur2) : (dur1 > dur2)
            }
        }

        return videos
    }

    private var lastPlayedItem: MediaItem? {
        guard let recent = history.first else { return nil }
        return items.first { $0.id == recent.mediaId }
    }

    public var body: some View {
        NavigationView {
            ZStack {
                VStack(spacing: 0) {
                    if currentFolder == nil {
                        Picker("View Mode", selection: $libraryMode) {
                            ForEach(VideoLibraryMode.allCases, id: \.self) { mode in
                                Text(mode.rawValue).tag(mode)
                            }
                        }
                        .pickerStyle(.segmented)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                    }

                    if libraryMode == .videos || currentFolder != nil {
                        FilterChipRow(options: VideoFilter.allCases, selection: $filter) { $0.rawValue }
                            .padding(.vertical, 4)
                    }

                    if libraryMode == .folders && currentFolder == nil {
                        foldersList
                    } else {
                        let videos = sortedVisibleVideos
                        if videos.isEmpty {
                            emptyState
                        } else if gridMode {
                            videoGrid(videos)
                        } else {
                            videoList(videos)
                        }
                    }
                }

                // Multi-select bottom bar
                if isSelecting {
                    VStack {
                        Spacer()
                        selectionActionBar
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }

                // "Play Last" Floating Action Button
                if !isSelecting && currentFolder == nil && lastPlayedItem != nil {
                    VStack {
                        Spacer()
                        HStack {
                            Spacer()
                            playLastFab
                        }
                    }
                    .padding(.bottom, 24)
                    .padding(.trailing, 20)
                }
            }
            .navigationTitle(currentFolder ?? "Videos")
            .searchable(text: $searchText, prompt: "Search videos")
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarLeading) {
                    if let folder = currentFolder {
                        Button {
                            currentFolder = nil
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "chevron.left")
                                Text("Folders")
                            }
                        }
                    } else if isSelecting {
                        Button(selectedIds.count == sortedVisibleVideos.count ? "Deselect All" : "Select All") {
                            if selectedIds.count == sortedVisibleVideos.count {
                                selectedIds.removeAll()
                            } else {
                                selectedIds = Set(sortedVisibleVideos.map { $0.id })
                            }
                        }
                    }
                }

                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    if isSelecting {
                        Button("Done") {
                            isSelecting = false
                            selectedIds.removeAll()
                        }
                        .fontWeight(.semibold)
                    } else {
                        Menu {
                            Section("Sort By") {
                                ForEach(VideoSortBy.allCases) { sort in
                                    Button {
                                        sortBy = sort
                                    } label: {
                                        HStack {
                                            Text(sort.rawValue)
                                            if sortBy == sort {
                                                Image(systemName: "checkmark")
                                            }
                                        }
                                    }
                                }
                            }
                            Section("Order") {
                                Button {
                                    sortAscending = true
                                } label: {
                                    HStack {
                                        Text("Ascending")
                                        if sortAscending { Image(systemName: "checkmark") }
                                    }
                                }
                                Button {
                                    sortAscending = false
                                } label: {
                                    HStack {
                                        Text("Descending")
                                        if !sortAscending { Image(systemName: "checkmark") }
                                    }
                                }
                            }
                            Section("Folders") {
                                Button {
                                    showHiddenFolders.toggle()
                                } label: {
                                    Label(showHiddenFolders ? "Hide Hidden Folders" : "Show Hidden Folders", systemImage: showHiddenFolders ? "eye.slash" : "eye")
                                }
                            }
                        } label: {
                            Image(systemName: "arrow.up.arrow.down")
                        }
                        .accessibilityLabel("Sort and filter options")

                        Button(action: { isSelecting = true }) {
                            Image(systemName: "checkmark.circle")
                        }
                        .accessibilityLabel("Select videos")

                        Button(action: { showWifiTransfer = true }) {
                            Image(systemName: "wifi")
                        }
                        .accessibilityLabel("Wi-Fi File Sharing")

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
                    onOpenVideo([item], 0)
                }
            }
            .sheet(item: $propertiesItem) { item in
                MediaPropertiesSheet(item: item)
            }
            .sheet(isPresented: $showAddToPlaylist) {
                let selectedItems = items.filter { selectedIds.contains($0.id) }
                AddToPlaylistSheet(items: selectedItems)
            }
            .sheet(isPresented: $showPinSheet) {
                PrivateVaultPinSheet {
                    privateVaultUnlocked = true
                    currentFolder = "Private Vault"
                }
            }
            .sheet(isPresented: $showShareSheet) {
                ShareSheet(activityItems: shareUrls)
            }
            .sheet(isPresented: $showWifiTransfer) {
                WifiTransferSheet()
            }
            .alert("Delete Videos", isPresented: $showDeleteConfirmation) {
                Button("Delete", role: .destructive) {
                    deleteSelected()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Are you sure you want to delete \(selectedIds.count) selected video(s) from your library?")
            }
            .onAppear(perform: loadData)
            .onReceive(NotificationCenter.default.publisher(for: .vimivoLibraryDidChange)) { _ in loadData() }
            .onChange(of: engine.state.currentItem?.id) { _ in loadData() }
            .task { await backfillMetadata() }
        }
        .navigationViewStyle(.stack)
    }

    // MARK: - Folders List

    private var foldersList: some View {
        List {
            let groups = folderGroups
            if groups.isEmpty {
                emptyState
            } else {
                ForEach(groups, id: \.name) { group in
                    MxFolderRow(
                        name: group.name,
                        count: group.items.count,
                        newCount: group.newCount,
                        isPrivate: group.isPrivate
                    ) {
                        openFolder(group.name, isPrivate: group.isPrivate)
                    }
                    .contextMenu {
                        if !group.isPrivate {
                            Button {
                                hideFolder(group.name)
                            } label: {
                                Label("Hide Folder", systemImage: "eye.slash")
                            }
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16))
                }
            }
        }
        .listStyle(.plain)
    }

    private func openFolder(_ name: String, isPrivate: Bool) {
        if isPrivate && !privateVaultUnlocked {
            showPinSheet = true
        } else {
            currentFolder = name
        }
    }

    private func hideFolder(_ name: String) {
        var set = hiddenFolders
        set.insert(name)
        hiddenFolders = set
    }

    // MARK: - Videos List

    private func videoList(_ videos: [MediaItem]) -> some View {
        List {
            ForEach(Array(videos.enumerated()), id: \.element.id) { index, video in
                HStack(spacing: 12) {
                    if isSelecting {
                        Button {
                            toggleSelect(video.id)
                        } label: {
                            Image(systemName: selectedIds.contains(video.id) ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 22))
                                .foregroundColor(selectedIds.contains(video.id) ? .accentColor : .secondary)
                        }
                        .buttonStyle(.plain)
                    }

                    MediaRow(
                        item: video,
                        isPlaying: engine.state.currentItem?.id == video.id,
                        isFavorite: favorites.contains(video.id),
                        onPlay: {
                            if isSelecting {
                                toggleSelect(video.id)
                            } else {
                                play(videos, index)
                            }
                        },
                        onPlayNext: { engine.playNextInQueue(video) },
                        onAddToQueue: { engine.appendToQueue(item: video) },
                        onToggleFavorite: { toggleFavorite(video) },
                        onDelete: { deleteVideo(video) },
                        progress: resumeProgress(video)
                    )
                }
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 12))
                .contextMenu {
                    Button(action: { play(videos, index) }) {
                        Label("Play Now", systemImage: "play.fill")
                    }
                    Button(action: { propertiesItem = video }) {
                        Label("Properties", systemImage: "info.circle")
                    }
                    Button(action: { togglePrivateVault(video.id) }) {
                        Label(privateMediaIds.contains(video.id) ? "Remove from Private Vault" : "Move to Private Vault", systemImage: "lock.shield")
                    }
                    Button(action: { toggleFavorite(video) }) {
                        Label(favorites.contains(video.id) ? "Remove from Favorites" : "Favorite", systemImage: "heart")
                    }
                    Button(action: { convertVideoToAudio(video) }) {
                        Label("Extract Audio (M4A)", systemImage: "music.note")
                    }
                    Divider()
                    Button(role: .destructive, action: { deleteVideo(video) }) {
                        Label("Remove", systemImage: "trash")
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    // MARK: - Videos Grid

    private func videoGrid(_ videos: [MediaItem]) -> some View {
        GeometryReader { geo in
            let columns = max(Int((geo.size.width - 16) / 236), 1)
            let width = (geo.size.width - 32 - CGFloat(columns - 1) * 16) / CGFloat(columns)
            ScrollView {
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(width), spacing: 16), count: columns), spacing: 18) {
                    ForEach(Array(videos.enumerated()), id: \.element.id) { index, video in
                        ZStack(alignment: .topTrailing) {
                            VideoCard(item: video, width: width, progress: resumeProgress(video)) {
                                if isSelecting {
                                    toggleSelect(video.id)
                                } else {
                                    play(videos, index)
                                }
                            }

                            if isSelecting {
                                Image(systemName: selectedIds.contains(video.id) ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 24))
                                    .foregroundColor(selectedIds.contains(video.id) ? .accentColor : .white)
                                    .background(Color.black.opacity(0.4), in: Circle())
                                    .padding(8)
                            }
                        }
                        .contextMenu {
                            Button(action: { propertiesItem = video }) {
                                Label("Properties", systemImage: "info.circle")
                            }
                            Button(action: { togglePrivateVault(video.id) }) {
                                Label(privateMediaIds.contains(video.id) ? "Remove from Private Vault" : "Move to Private Vault", systemImage: "lock.shield")
                            }
                            Button(action: { toggleFavorite(video) }) {
                                Label(favorites.contains(video.id) ? "Remove from Favorites" : "Favorite", systemImage: "heart")
                            }
                            Button(action: { convertVideoToAudio(video) }) {
                                Label("Extract Audio (M4A)", systemImage: "music.note")
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

    // MARK: - Multi-Select Action Bar

    private var selectionActionBar: some View {
        HStack(spacing: 24) {
            Button {
                shareSelected()
            } label: {
                VStack(spacing: 4) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 20))
                    Text("Share").font(.caption2)
                }
            }

            Button {
                showAddToPlaylist = true
            } label: {
                VStack(spacing: 4) {
                    Image(systemName: "music.note.list")
                        .font(.system(size: 20))
                    Text("Playlist").font(.caption2)
                }
            }

            Button {
                for id in selectedIds { togglePrivateVault(id) }
                isSelecting = false
                selectedIds.removeAll()
            } label: {
                VStack(spacing: 4) {
                    Image(systemName: "lock.shield")
                        .font(.system(size: 20))
                    Text("Vault").font(.caption2)
                }
            }

            if selectedIds.count == 1, let singleId = selectedIds.first, let item = items.first(where: { $0.id == singleId }) {
                Button {
                    propertiesItem = item
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: "info.circle")
                            .font(.system(size: 20))
                        Text("Info").font(.caption2)
                    }
                }
            }

            Button(role: .destructive) {
                showDeleteConfirmation = true
            } label: {
                VStack(spacing: 4) {
                    Image(systemName: "trash")
                        .font(.system(size: 20))
                    Text("Delete").font(.caption2)
                }
                .foregroundColor(.red)
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)
        .background(
            Capsule()
                .fill(.ultraThinMaterial)
                .shadow(color: .black.opacity(0.2), radius: 10, y: 5)
        )
        .padding(.bottom, 16)
    }

    // MARK: - "Play Last" FAB

    private var playLastFab: some View {
        Button {
            if let last = lastPlayedItem {
                onOpenVideo([last], 0)
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "play.fill")
                    .font(.system(size: 16, weight: .bold))
                Text("Play Last")
                    .font(.subheadline.weight(.semibold))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.accentColor)
            .foregroundColor(.white)
            .clipShape(Capsule())
            .shadow(color: Color.accentColor.opacity(0.4), radius: 8, y: 4)
        }
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

    private func toggleSelect(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
        } else {
            selectedIds.insert(id)
        }
    }

    private func togglePrivateVault(_ id: String) {
        var set = privateMediaIds
        if set.contains(id) {
            set.remove(id)
        } else {
            set.insert(id)
        }
        privateMediaIds = set
        loadData()
    }

    private func convertVideoToAudio(_ video: MediaItem) {
        guard let url = SecurityScopedMediaManager.shared.resolvedUrl(for: video) ?? URL(string: video.uri) else { return }
        Task {
            if let audioUrl = try? await MediaConverter.extractAudio(from: url, outputTitle: video.title) {
                let newItem = MediaItem(uri: audioUrl.absoluteString, title: video.title, kind: .audio)
                await MainActor.run {
                    IOSDatabase.shared.saveMediaItem(newItem)
                }
            }
        }
    }

    private func shareSelected() {
        let selected = items.filter { selectedIds.contains($0.id) }
        let urls = selected.compactMap { item -> URL? in
            SecurityScopedMediaManager.shared.resolvedUrl(for: item) ?? URL(string: item.uri)
        }
        if !urls.isEmpty {
            shareUrls = urls
            showShareSheet = true
        }
    }

    private func deleteSelected() {
        for id in selectedIds {
            IOSDatabase.shared.deleteMediaItem(id: id)
        }
        selectedIds.removeAll()
        isSelecting = false
        loadData()
    }

    private func resumeProgress(_ video: MediaItem) -> Double? {
        guard let entry = historyMap[video.id], entry.positionMillis > 0, !entry.completed,
              let duration = video.durationMillis, duration > 0 else { return nil }
        return Double(entry.positionMillis) / Double(duration)
    }

    private func play(_ videos: [MediaItem], _ index: Int) {
        onOpenVideo(videos, index)
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
