import SwiftUI

public struct NowPlayingBar: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    public let onExpand: () -> Void

    public init(engine: AVPlayerPlaybackEngine, onExpand: @escaping () -> Void) {
        self.engine = engine
        self.onExpand = onExpand
    }

    public var body: some View {
        if let current = engine.state.currentItem {
            VStack(spacing: 0) {
                GeometryReader { geo in
                    Rectangle()
                        .fill(Color.accentColor)
                        .frame(width: geo.size.width * CGFloat(engine.state.progress), height: 2.5)
                }
                .frame(height: 2.5)
                .background(Color.secondary.opacity(0.15))

                HStack(spacing: 12) {
                    ArtworkThumbnailView(uri: current.artworkUri, kind: current.kind, size: 42)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(current.title)
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .lineLimit(1)

                        if let subtitle = current.artist ?? current.album {
                            Text(subtitle)
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                    }

                    Spacer()

                    Button(action: engine.togglePlayPause) {
                        Image(systemName: engine.state.status == .playing ? "pause.fill" : "play.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.primary)
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(engine.state.status == .playing ? "Pause" : "Play")

                    Button(action: engine.playNext) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 16))
                            .foregroundColor(.primary)
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Next track")
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color(uiColor: .secondarySystemGroupedBackground))
                        .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 3)
                )
                .padding(.horizontal, 12)
                .contentShape(Rectangle())
                .onTapGesture(perform: onExpand)
            }
        }
    }
}
