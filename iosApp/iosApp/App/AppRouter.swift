import Foundation

/// Routes URLs the system hands to Vimivo: `vimivo://now-playing` from widgets, `vimivo://play?id=` deep
/// links, and media files opened from other apps ("Open in Vimivo", the share sheet, Files).
@MainActor
final class AppRouter: ObservableObject {
    static let shared = AppRouter()

    /// Incremented whenever the player should be brought to the front.
    @Published private(set) var playerRequest = 0

    func handle(_ url: URL, engine: AVPlayerPlaybackEngine) async {
        if url.isFileURL {
            await openFile(url, engine: engine)
            return
        }
        guard url.scheme?.lowercased() == "vimivo" else { return }
        switch url.host?.lowercased() {
        case "now-playing":
            if engine.state.currentItem != nil { playerRequest += 1 }
        case "play":
            let id = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "id" }?.value
            guard let id, let item = IOSDatabase.shared.getAllMediaItems().first(where: { $0.id == id }) else {
                NSLog("Vimivo: deep link references unknown media")
                return
            }
            play(item, engine: engine)
        default:
            NSLog("Vimivo: unsupported deep link host %@", url.host ?? "")
        }
    }

    private func openFile(_ url: URL, engine: AVPlayerPlaybackEngine) async {
        // Files opened in place stay where they are; copies land in Documents/Inbox and are app-owned.
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
        let insideDocuments = documents.map { url.standardizedFileURL.path.hasPrefix($0.standardizedFileURL.path) } ?? false
        let id = insideDocuments
            ? DocumentsLibraryImporter.id(for: url, in: documents!)
            : "opened:" + url.standardizedFileURL.path
        let existing = IOSDatabase.shared.getAllMediaItems().first { $0.id == id }
        let item: MediaItem
        if let existing {
            item = existing
        } else {
            item = await SecurityScopedMediaManager.shared.extractMediaItem(from: url, id: id, createsBookmark: !insideDocuments)
            IOSDatabase.shared.saveMediaItem(item)
            NotificationCenter.default.post(name: .vimivoLibraryDidChange, object: nil)
        }
        play(item, engine: engine)
    }

    private func play(_ item: MediaItem, engine: AVPlayerPlaybackEngine) {
        engine.play(item: item)
        playerRequest += 1
    }
}
