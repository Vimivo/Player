import SwiftUI

public struct QueueView: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    @Environment(\.dismiss) private var dismiss

    public init(engine: AVPlayerPlaybackEngine) {
        self.engine = engine
    }

    public var body: some View {
        NavigationView {
            List {
                ForEach(Array(engine.queue.enumerated()), id: \.element.id) { index, item in
                    HStack(spacing: 12) {
                        if index == engine.currentIndex {
                            Image(systemName: "speaker.wave.2.fill")
                                .foregroundColor(.accentColor)
                                .font(.system(size: 14))
                        } else {
                            Text("\(index + 1)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .frame(width: 20)
                        }

                        ArtworkThumbnailView(uri: item.artworkUri, kind: item.kind, size: 40)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .font(.body)
                                .lineLimit(1)
                                .foregroundColor(index == engine.currentIndex ? .accentColor : .primary)

                            if let artist = item.artist {
                                Text(artist)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                            }
                        }

                        Spacer()
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        engine.play(item: item)
                    }
                    .deleteDisabled(index == engine.currentIndex)
                }
                .onMove { indices, newOffset in
                    engine.moveInQueue(fromOffsets: indices, toOffset: newOffset)
                }
                .onDelete { indices in
                    engine.removeFromQueue(atOffsets: indices)
                }
            }
            .listStyle(.plain)
            .navigationTitle("Playback Queue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    EditButton()
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
