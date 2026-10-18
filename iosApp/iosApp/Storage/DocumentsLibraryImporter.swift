import Foundation
import UniformTypeIdentifiers

extension Notification.Name {
    /// Posted on the main queue whenever media is added to the library outside a view's own import flow.
    static let vimivoLibraryDidChange = Notification.Name("dev.vimivo.libraryDidChange")
}

/// Indexes the app's Documents folder, which the Files app shows as "On My iPhone › Vimivo" and Finder
/// exposes over USB, so media copied there appears in the library without using the document picker.
public actor DocumentsLibraryImporter {
    public static let shared = DocumentsLibraryImporter()

    static let idPrefix = "documents:"

    private let fileManager = FileManager.default

    /// Adds new media and refreshes stored paths; returns how many items were added.
    @discardableResult
    public func importDocuments() async -> Int {
        guard let documents = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else { return 0 }
        let known = Dictionary(uniqueKeysWithValues: IOSDatabase.shared.getAllMediaItems().map { ($0.id, $0.uri) })
        var added = 0
        for url in mediaFiles(in: documents) {
            let id = Self.id(for: url, in: documents)
            if let storedUri = known[id] {
                if storedUri != url.absoluteString {
                    IOSDatabase.shared.updateMediaUri(id: id, uri: url.absoluteString)
                }
                continue
            }
            let item = await SecurityScopedMediaManager.shared.extractMediaItem(from: url, id: id, createsBookmark: false)
            IOSDatabase.shared.saveMediaItem(item)
            added += 1
        }
        if added > 0 {
            await MainActor.run { NotificationCenter.default.post(name: .vimivoLibraryDidChange, object: nil) }
        }
        return added
    }

    static func id(for url: URL, in documents: URL) -> String {
        let root = documents.standardizedFileURL.path
        let path = url.standardizedFileURL.path
        let relative = path.hasPrefix(root) ? String(path.dropFirst(root.count)).trimmingCharacters(in: CharacterSet(charactersIn: "/")) : path
        return idPrefix + relative
    }

    private func mediaFiles(in documents: URL) -> [URL] {
        let keys: [URLResourceKey] = [.isRegularFileKey, .contentTypeKey]
        guard let enumerator = fileManager.enumerator(at: documents, includingPropertiesForKeys: keys, options: [.skipsHiddenFiles]) else {
            return []
        }
        return enumerator.compactMap { $0 as? URL }.filter { url in
            guard let values = try? url.resourceValues(forKeys: Set(keys)), values.isRegularFile == true,
                  let type = values.contentType else { return false }
            return type.conforms(to: .audiovisualContent)
        }
    }
}
