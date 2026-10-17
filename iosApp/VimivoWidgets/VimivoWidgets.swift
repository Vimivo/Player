import SwiftUI
import WidgetKit
import UIKit

private struct Snapshot: Codable {
    let title: String
    let artist: String
    let artworkPath: String?
    let isPlaying: Bool
    let position: Double
    let duration: Double
    let updatedAt: Date
}

private struct Entry: TimelineEntry { let date: Date; let snapshot: Snapshot }

private struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry { Entry(date: Date(), snapshot: Snapshot(title: "Nothing Playing", artist: "Open Vimivo", artworkPath: nil, isPlaying: false, position: 0, duration: 0, updatedAt: .distantPast)) }
    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) { completion(Entry(date: Date(), snapshot: load())) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) { completion(Timeline(entries: [Entry(date: Date(), snapshot: load())], policy: .never)) }
    private func load() -> Snapshot {
        guard let data = UserDefaults(suiteName: "group.dev.vimivo.shared")?.data(forKey: "vimivo.widget.playback.v1"),
              let value = try? JSONDecoder().decode(WidgetSnapshot.self, from: data) else {
            return Snapshot(title: "Nothing Playing", artist: "Open Vimivo", artworkPath: nil, isPlaying: false, position: 0, duration: 0, updatedAt: .distantPast)
        }
        return Snapshot(title: value.title, artist: value.artist, artworkPath: value.artworkPath, isPlaying: value.isPlaying, position: value.positionSeconds, duration: value.durationSeconds, updatedAt: value.updatedAt)
    }
}

private struct WidgetSnapshot: Codable { let title: String; let artist: String; let artworkPath: String?; let isPlaying: Bool; let positionSeconds: Double; let durationSeconds: Double; let updatedAt: Date }

private struct WidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: Entry
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                artwork
                VStack(alignment: .leading) { Text(entry.snapshot.title).font(.headline).lineLimit(1); Text(entry.snapshot.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
            }
            if family != .systemSmall { ProgressView(value: progress).tint(.purple) }
            if #available(iOS 17.0, *) {
                HStack { Button(intent: PreviousWidgetTrackIntent()) { Image(systemName: "backward.fill") }; Spacer(); Button(intent: ToggleWidgetPlaybackIntent()) { Image(systemName: entry.snapshot.isPlaying ? "pause.fill" : "play.fill") }; Spacer(); Button(intent: NextWidgetTrackIntent()) { Image(systemName: "forward.fill") } }.buttonStyle(.plain)
            }
        }.containerBackground(for: .widget) { Color.indigo.opacity(0.14) }.widgetURL(URL(string: "vimivo://now-playing"))
    }
    private var progress: Double { guard entry.snapshot.duration > 0 else { return 0 }; return min(max(entry.snapshot.position / entry.snapshot.duration, 0), 1) }

    @ViewBuilder private var artwork: some View {
        if let path = entry.snapshot.artworkPath,
           let image = UIImage(contentsOfFile: URL(string: path)?.path ?? path) {
            Image(uiImage: image).resizable().scaledToFill().frame(width: 42, height: 42).clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        } else {
            Image(systemName: "music.note.list").font(.title2).foregroundStyle(.purple).frame(width: 42, height: 42)
        }
    }
}

struct VimivoNowPlayingWidget: Widget {
    var body: some WidgetConfiguration { StaticConfiguration(kind: "dev.vimivo.widget.now-playing", provider: Provider()) { WidgetView(entry: $0) }.configurationDisplayName("Now Playing").description("Control Vimivo local playback.").supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular]) }
}

#if canImport(ActivityKit)
import ActivityKit

@available(iOS 16.1, *)
struct VimivoPlaybackLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: VimivoPlaybackActivityAttributes.self) { context in
            LiveActivityLockScreenView(state: context.state)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 8) {
                        Image(systemName: "music.note")
                            .font(.title3)
                            .foregroundStyle(.purple)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(context.state.title)
                                .font(.subheadline)
                                .fontWeight(.semibold)
                                .lineLimit(1)
                            Text(context.state.artist)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Image(systemName: context.state.isPlaying ? "waveform" : "pause.fill")
                        .font(.title3)
                        .foregroundStyle(.purple)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 8) {
                        if context.state.durationSeconds > 0 {
                            ProgressView(value: min(max(context.state.positionSeconds / context.state.durationSeconds, 0), 1))
                                .tint(.purple)
                        }
                        if #available(iOS 17.0, *) {
                            HStack {
                                Button(intent: PreviousWidgetTrackIntent()) {
                                    Image(systemName: "backward.fill")
                                }
                                Spacer()
                                Button(intent: ToggleWidgetPlaybackIntent()) {
                                    Image(systemName: context.state.isPlaying ? "pause.fill" : "play.fill")
                                }
                                Spacer()
                                Button(intent: NextWidgetTrackIntent()) {
                                    Image(systemName: "forward.fill")
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            } compactLeading: {
                Image(systemName: "music.note")
                    .foregroundStyle(.purple)
            } compactTrailing: {
                Image(systemName: context.state.isPlaying ? "waveform" : "pause.fill")
                    .foregroundStyle(.purple)
            } minimal: {
                Image(systemName: context.state.isPlaying ? "waveform" : "music.note")
                    .foregroundStyle(.purple)
            }
            .widgetURL(URL(string: "vimivo://now-playing"))
        }
    }
}

@available(iOS 16.1, *)
private struct LiveActivityLockScreenView: View {
    let state: VimivoPlaybackActivityAttributes.ContentState

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "music.note.list")
                .font(.title2)
                .foregroundStyle(.purple)
                .frame(width: 44, height: 44)
                .background(Color.purple.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(state.title)
                    .font(.headline)
                    .lineLimit(1)
                Text(state.artist)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                if state.durationSeconds > 0 {
                    ProgressView(value: min(max(state.positionSeconds / state.durationSeconds, 0), 1))
                        .tint(.purple)
                }
            }

            Spacer()

            if #available(iOS 17.0, *) {
                HStack(spacing: 12) {
                    Button(intent: PreviousWidgetTrackIntent()) {
                        Image(systemName: "backward.fill")
                    }
                    Button(intent: ToggleWidgetPlaybackIntent()) {
                        Image(systemName: state.isPlaying ? "pause.fill" : "play.fill")
                    }
                    Button(intent: NextWidgetTrackIntent()) {
                        Image(systemName: "forward.fill")
                    }
                }
                .buttonStyle(.plain)
                .foregroundStyle(.primary)
            }
        }
        .padding(14)
        .widgetURL(URL(string: "vimivo://now-playing"))
    }
}
#endif

@main
struct VimivoWidgetBundle: WidgetBundle {
    var body: some Widget {
        VimivoNowPlayingWidget()
#if canImport(ActivityKit)
        VimivoPlaybackLiveActivity()
#endif
    }
}

