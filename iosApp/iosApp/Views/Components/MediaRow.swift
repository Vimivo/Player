import SwiftUI

public struct MediaRow: View {
    public let item: MediaItem
    public let isPlaying: Bool
    public let isFavorite: Bool
    public let onPlay: () -> Void
    public let onPlayNext: () -> Void
    public let onAddToQueue: () -> Void
    public let onToggleFavorite: () -> Void
    public let onDelete: () -> Void

    public init(
        item: MediaItem,
        isPlaying: Bool = false,
        isFavorite: Bool = false,
        onPlay: @escaping () -> Void,
        onPlayNext: @escaping () -> Void = {},
        onAddToQueue: @escaping () -> Void = {},
        onToggleFavorite: @escaping () -> Void = {},
        onDelete: @escaping () -> Void = {}
    ) {
        self.item = item
        self.isPlaying = isPlaying
        self.isFavorite = isFavorite
        self.onPlay = onPlay
        self.onPlayNext = onPlayNext
        self.onAddToQueue = onAddToQueue
        self.onToggleFavorite = onToggleFavorite
        self.onDelete = onDelete
    }

    public var body: some View {
        HStack(spacing: 14) {
            ArtworkThumbnailView(uri: item.artworkUri, kind: item.kind, size: 50)

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.body)
                    .fontWeight(.medium)
                    .lineLimit(1)
                    .foregroundColor(isPlaying ? .accentColor : .primary)

                HStack(spacing: 6) {
                    if let subtitle = item.artist ?? item.album {
                        Text(subtitle)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                    if item.durationMillis != nil {
                        Text("•")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        Text(item.formattedDuration)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                }
            }

            Spacer()

            Button(action: onToggleFavorite) {
                Image(systemName: isFavorite ? "heart.fill" : "heart")
                    .foregroundColor(isFavorite ? .red : .secondary.opacity(0.6))
                    .font(.system(size: 16))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isFavorite ? "Remove \(item.title) from favorites" : "Add \(item.title) to favorites")
            .padding(.trailing, 4)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onPlay)
        .contextMenu {
            Button(action: onPlay) {
                Label("Play Now", systemImage: "play.fill")
            }
            Button(action: onPlayNext) {
                Label("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward")
            }
            Button(action: onAddToQueue) {
                Label("Add to Queue", systemImage: "text.badge.plus")
            }
            Button(action: onToggleFavorite) {
                Label(isFavorite ? "Remove from Favorites" : "Favorite", systemImage: isFavorite ? "heart.slash" : "heart")
            }
            Divider()
            Button(role: .destructive, action: onDelete) {
                Label("Remove", systemImage: "trash")
            }
        }
    }
}
