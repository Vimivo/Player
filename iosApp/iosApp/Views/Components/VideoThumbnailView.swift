import SwiftUI
import AVFoundation
import UIKit

/// Generates and caches a representative frame for local videos. Frames are written to the
/// per-user Caches directory and never leave the device.
actor VideoThumbnailStore {
    static let shared = VideoThumbnailStore()

    private let memory = NSCache<NSString, UIImage>()
    private var inFlight: [String: Task<UIImage?, Never>] = [:]

    func thumbnail(for item: MediaItem) async -> UIImage? {
        let key = item.id as NSString
        if let cached = memory.object(forKey: key) { return cached }
        if let task = inFlight[item.id] { return await task.value }
        let task = Task<UIImage?, Never> { await Self.loadOrGenerate(item) }
        inFlight[item.id] = task
        let image = await task.value
        inFlight[item.id] = nil
        if let image = image { memory.setObject(image, forKey: key) }
        return image
    }

    private static func cacheFile(for id: String) -> URL? {
        guard let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return nil }
        let dir = caches.appendingPathComponent("video-thumbnails", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        } catch {
            NSLog("Vimivo: cannot create thumbnail cache: %@", error.localizedDescription)
            return nil
        }
        let safe = id.replacingOccurrences(of: "/", with: "_")
        return dir.appendingPathComponent("\(safe).jpg")
    }

    private static func loadOrGenerate(_ item: MediaItem) async -> UIImage? {
        let file = cacheFile(for: item.id)
        if let file = file, let image = UIImage(contentsOfFile: file.path) {
            return image
        }
        guard let url = SecurityScopedMediaManager.shared.resolvedUrl(for: item) else { return nil }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 480, height: 480)
        let seconds = item.durationMillis.map { min(max(Double($0) / 1000 * 0.1, 1), 30) } ?? 1
        do {
            let (cgImage, _) = try await generator.image(at: CMTime(seconds: seconds, preferredTimescale: 600))
            let image = UIImage(cgImage: cgImage)
            if let file = file, let data = image.jpegData(compressionQuality: 0.8) {
                try? data.write(to: file, options: .atomic)
            }
            return image
        } catch {
            NSLog("Vimivo: thumbnail generation failed for %@: %@", item.title, error.localizedDescription)
            return nil
        }
    }
}

/// 16:9 video thumbnail with duration badge, NEW badge, resolution badge, and optional resume progress.
struct VideoThumbnailView: View {
    let item: MediaItem
    var width: CGFloat = 96
    var cornerRadius: CGFloat = 10
    var progress: Double?
    var showsDuration = true

    @State private var image: UIImage?

    private var resolutionBadge: String? {
        guard let width = item.width, let height = item.height else { return nil }
        let minDim = min(width, height)
        switch minDim {
        case 2000...: return "4K"
        case 1400..<2000: return "1440p"
        case 1000..<1400: return "1080p"
        case 700..<1000: return "720p"
        case 400..<700: return "480p"
        default: return "\(minDim)p"
        }
    }

    private var isNew: Bool {
        guard let added = item.dateAddedMillis else { return false }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let isRecent = (now - added) < 7 * 86400 * 1000
        let notDone = (progress ?? 0) < 0.95
        return isRecent && notDone
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Group {
                if let image = image {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } else {
                    ZStack {
                        Color(uiColor: .tertiarySystemFill)
                        Image(systemName: "film")
                            .font(.system(size: width * 0.22, weight: .semibold))
                            .foregroundColor(.secondary)
                    }
                }
            }
            .frame(width: width, height: width * 9 / 16)
            .clipped()

            if isNew {
                VStack {
                    HStack {
                        Text("NEW")
                            .font(.system(size: 9, weight: .bold))
                            .padding(.horizontal, 4)
                            .padding(.vertical, 2)
                            .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 3))
                            .foregroundColor(.white)
                            .padding(4)
                        Spacer()
                    }
                    Spacer()
                }
            }

            if let res = resolutionBadge {
                VStack {
                    HStack {
                        Spacer()
                        Text(res)
                            .font(.system(size: 9, weight: .bold))
                            .padding(.horizontal, 4)
                            .padding(.vertical, 2)
                            .background(Color.black.opacity(0.7), in: RoundedRectangle(cornerRadius: 3))
                            .foregroundColor(.white)
                            .padding(4)
                    }
                    Spacer()
                }
            }

            if showsDuration, let ms = item.durationMillis, ms > 0 {
                Text(item.formattedDuration)
                    .font(.caption2.weight(.bold))
                    .monospacedDigit()
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(Color.black.opacity(0.72), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                    .foregroundColor(.white)
                    .padding(5)
            }

            if let progress = progress, progress > 0 {
                VStack {
                    Spacer()
                    ZStack(alignment: .leading) {
                        Rectangle().fill(Color.black.opacity(0.4))
                        Rectangle().fill(Color.accentColor).frame(width: width * CGFloat(min(progress, 1)))
                    }
                    .frame(height: 3)
                }
            }
        }
        .frame(width: width, height: width * 9 / 16)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .accessibilityHidden(true)
        .task(id: item.id) {
            if let uri = item.artworkUri, let url = URL(string: uri), let art = UIImage(contentsOfFile: url.path) {
                image = art
            } else {
                image = await VideoThumbnailStore.shared.thumbnail(for: item)
            }
        }
    }
}

/// Large 16:9 card for "Continue watching" and the video grid.
struct VideoCard: View {
    let item: MediaItem
    var width: CGFloat = 220
    var progress: Double?
    let onPlay: () -> Void

    var body: some View {
        Button(action: onPlay) {
            VStack(alignment: .leading, spacing: 6) {
                VideoThumbnailView(item: item, width: width, cornerRadius: 14, progress: progress, showsDuration: true)
                Text(item.title)
                    .font(.subheadline.weight(.medium))
                    .foregroundColor(.primary)
                    .lineLimit(1)
                let detail = item.detailLine
                if !detail.isEmpty {
                    Text(detail)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
            .frame(width: width, alignment: .leading)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Play \(item.title)")
    }
}

/// Horizontally scrolling single-select chips, matching the Android FilterChipRow.
struct FilterChipRow<Option: Hashable>: View {
    let options: [Option]
    @Binding var selection: Option
    let label: (Option) -> String

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(options, id: \.self) { option in
                    let selected = option == selection
                    Button {
                        selection = option
                    } label: {
                        HStack(spacing: 4) {
                            if selected {
                                Image(systemName: "checkmark").font(.caption.weight(.bold))
                            }
                            Text(label(option)).font(.subheadline.weight(.medium))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            Capsule().fill(selected ? Color.accentColor.opacity(0.18) : Color.clear)
                        )
                        .overlay(
                            Capsule().strokeBorder(selected ? Color.clear : Color.secondary.opacity(0.35), lineWidth: 1)
                        )
                        .foregroundColor(selected ? .accentColor : .primary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
        }
    }
}
