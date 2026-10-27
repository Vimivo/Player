import Foundation
import VimivoShared

/// The lyrics of the track that is playing: what a person loaded earlier, else a "Song.lrc" beside the file. Parsing,
/// finding the current line and choosing the sidecar are the shared module's; this reads files and remembers the choice.
@MainActor
final class LyricsModel: ObservableObject {
    @Published private(set) var document: LyricsDocument?
    @Published var errorMessage: String?
    private var loadedFor: String?
    private let database: IOSDatabase

    init(database: IOSDatabase = .shared) {
        self.database = database
    }

    /// Looks up the lyrics for [item] once per track.
    func refresh(for item: MediaItem?) {
        guard item?.id != loadedFor else { return }
        loadedFor = item?.id
        document = nil
        errorMessage = nil
        guard let item else { return }
        if let stored = database.lyrics(mediaId: item.id) {
            document = LrcLyricsParser.shared.parse(text: stored.text, sourceUri: stored.source)
            return
        }
        document = sidecar(for: item)
    }

    /// Reads a file the person picked, shows it and keeps it for this track.
    func load(from url: URL, for item: MediaItem) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
            let text = try String(contentsOf: url, encoding: .utf8)
            let parsed = LrcLyricsParser.shared.parse(text: text, sourceUri: url.absoluteString)
            guard !parsed.lines.isEmpty else {
                errorMessage = "No readable lyrics were found in that file."
                return
            }
            database.saveLyrics(mediaId: item.id, source: url.absoluteString, text: text)
            loadedFor = item.id
            document = parsed
            errorMessage = nil
        } catch {
            errorMessage = "That lyrics file could not be read."
        }
    }

    /// The index of the line to highlight at [positionMillis], or -1.
    func activeIndex(positionMillis: Int64) -> Int {
        guard let document else { return -1 }
        return Int(document.findActiveLineIndex(positionMillis: positionMillis))
    }

    private func sidecar(for item: MediaItem) -> LyricsDocument? {
        guard let media = URL(string: item.uri), media.isFileURL else { return nil }
        let folder = media.deletingLastPathComponent()
        let names = (try? FileManager.default.contentsOfDirectory(atPath: folder.path)) ?? []
        guard let chosen = LyricsSidecar.shared.best(mediaFileName: media.lastPathComponent, candidateNames: names) else { return nil }
        let file = folder.appendingPathComponent(chosen)
        guard let text = try? String(contentsOf: file, encoding: .utf8) else { return nil }
        let parsed = LrcLyricsParser.shared.parse(text: text, sourceUri: file.absoluteString)
        return parsed.lines.isEmpty ? nil : parsed
    }
}
