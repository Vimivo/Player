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
                HStack(spacing: 12) {
                    ArtworkThumbnailView(uri: current.artworkUri, kind: current.kind, size: 48)
                        .clipShape(Circle())

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
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(engine.state.status == .playing ? "Pause" : "Play")

                    Button(action: engine.playNext) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 16))
                            .foregroundColor(.primary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Next track")
                }
                .padding(.leading, 8)
                .padding(.trailing, 6)
                .padding(.top, 8)
                .padding(.bottom, 6)

                GeometryReader { geo in
                    Rectangle()
                        .fill(Color.accentColor)
                        .frame(width: geo.size.width * CGFloat(engine.state.progress), height: 2)
                }
                .frame(height: 2)
                .background(Color.secondary.opacity(0.15))
            }
            .vimivoFloatingChrome()
            .contentShape(Rectangle())
            .onTapGesture(perform: onExpand)
            .padding(.horizontal, VimivoChrome.margin)
            .padding(.bottom, VimivoChrome.gap)
        }
    }
}
