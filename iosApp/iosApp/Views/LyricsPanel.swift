import SwiftUI
import VimivoShared

/// Synced lyrics as a centred, fading list around the current line; or a prompt to load a file when there are none.
struct LyricsPanel: View {
    @ObservedObject var lyrics: LyricsModel
    let positionMillis: Int64
    let onSeek: (Int64) -> Void
    let onLoad: () -> Void

    var body: some View {
        if let document = lyrics.document, !document.lines.isEmpty {
            let active = lyrics.activeIndex(positionMillis: positionMillis)
            ScrollViewReader { proxy in
                ScrollView(showsIndicators: false) {
                    LazyVStack(spacing: 16) {
                        ForEach(Array(document.lines.enumerated()), id: \.offset) { index, line in
                            let distance = active < 0 ? 1 : abs(index - active)
                            Text(line.text.isEmpty ? "\u{266A}" : line.text)
                                .font(index == active ? .title2.weight(.bold) : .title3.weight(.medium))
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: .infinity)
                                .opacity(index == active ? 1 : max(0.22, 0.62 - 0.12 * Double(distance)))
                                .id(index)
                                .onTapGesture { if let time = line.timeMillis { onSeek(time.int64Value) } }
                                .accessibilityAddTraits(index == active ? .isSelected : [])
                        }
                    }
                    .padding(.vertical, 24)
                }
                .onChange(of: active) { value in
                    guard value >= 0 else { return }
                    withAnimation { proxy.scrollTo(max(0, value - 2), anchor: .top) }
                }
            }
        } else {
            VStack(spacing: 12) {
                Image(systemName: "music.note").font(.system(size: 44)).foregroundColor(.secondary)
                Text("No lyrics for this track").font(.headline)
                Text("Load a local .lrc or text file.").font(.subheadline).foregroundColor(.secondary)
                if let message = lyrics.errorMessage { Text(message).font(.footnote).foregroundColor(.red) }
                Button("Load lyrics", action: onLoad).buttonStyle(.borderedProminent)
            }
        }
    }
}
