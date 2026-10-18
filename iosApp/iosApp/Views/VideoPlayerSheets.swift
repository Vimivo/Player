import SwiftUI
import AVKit

/// Focused bottom sheets for the video player: speed, audio, subtitles, and general options.
struct PlayerSheetView: View {
    let sheet: PlayerSheet
    @ObservedObject var engine: AVPlayerPlaybackEngine
    @ObservedObject var model: VideoPlayerModel
    @Environment(\.dismiss) private var dismiss

    static let speedPresets: [Float] = [0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0]

    var body: some View {
        NavigationStack {
            Group {
                switch sheet {
                case .speed: speedContent
                case .audio: audioContent
                case .subtitles: subtitleContent
                case .options: optionsContent
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private var title: String {
        switch sheet {
        case .speed: return "Playback speed"
        case .audio: return "Audio track"
        case .subtitles: return "Subtitles"
        case .options: return "Video options"
        }
    }

    // MARK: Speed

    private var speedContent: some View {
        let rate = engine.state.playbackRate
        return ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(MediaFormatting.speed(rate))
                    .font(.system(size: 40, weight: .semibold))
                    .monospacedDigit()
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Current speed \(MediaFormatting.speed(rate))")
                Slider(
                    value: Binding(
                        get: { Double(rate) },
                        set: { engine.setPlaybackRate(Float(($0 * 20).rounded() / 20)) }
                    ),
                    in: 0.25...3.0
                ) {
                    Text("Playback speed")
                } minimumValueLabel: {
                    Text("0.25×").font(.caption)
                } maximumValueLabel: {
                    Text("3×").font(.caption)
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 64), spacing: 8)], spacing: 8) {
                    ForEach(Self.speedPresets, id: \.self) { preset in
                        ChoiceChip(text: MediaFormatting.speed(preset), selected: abs(preset - rate) < 0.001) {
                            engine.setPlaybackRate(preset)
                        }
                    }
                }
                Text("Tip: press and hold on the video for \(MediaFormatting.speed(VideoPlayerModel.holdSpeed)) while you hold.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(20)
        }
    }

    // MARK: Audio

    @ViewBuilder
    private var audioContent: some View {
        if model.audioOptions.isEmpty {
            EmptySheetMessage(icon: "waveform", text: "This video reports a single audio track.")
        } else {
            List(model.audioOptions, id: \.self) { option in
                SelectableRow(
                    title: option.displayName,
                    detail: option.extendedLanguageTag,
                    selected: option == model.selectedAudio
                ) {
                    model.selectAudio(option)
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    // MARK: Subtitles

    @ViewBuilder
    private var subtitleContent: some View {
        if model.subtitleOptions.isEmpty {
            EmptySheetMessage(icon: "captions.bubble", text: "No embedded subtitle tracks in this video.")
        } else {
            List {
                SelectableRow(title: "Off", detail: nil, selected: model.selectedSubtitle == nil) {
                    model.selectSubtitle(nil)
                }
                ForEach(model.subtitleOptions, id: \.self) { option in
                    SelectableRow(
                        title: option.displayName,
                        detail: option.extendedLanguageTag,
                        selected: option == model.selectedSubtitle
                    ) {
                        model.selectSubtitle(option)
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    // MARK: Options

    private var optionsContent: some View {
        List {
            Section("Aspect ratio") {
                ForEach(VideoAspect.allCases) { aspect in
                    SelectableRow(title: aspect.label, detail: nil, selected: aspect == model.aspect) {
                        model.setAspect(aspect)
                    }
                }
            }
            Section("Playback") {
                Button {
                    model.sheet = .speed
                } label: {
                    LabeledContent("Speed", value: MediaFormatting.speed(engine.state.playbackRate))
                }
                Button {
                    model.sheet = .audio
                } label: {
                    LabeledContent("Audio track", value: model.selectedAudio?.displayName ?? "Default")
                }
                Button {
                    model.sheet = .subtitles
                } label: {
                    LabeledContent("Subtitles", value: model.selectedSubtitle?.displayName ?? "Off")
                }
            }
            .foregroundColor(.primary)
            Section("Gestures") {
                GestureHint(icon: "hand.tap", text: "Double-tap left or right to skip 10 s; keep tapping to add more")
                GestureHint(icon: "hand.point.up.left", text: "Hold for \(MediaFormatting.speed(VideoPlayerModel.holdSpeed)) speed")
                GestureHint(icon: "arrow.left.and.right", text: "Swipe sideways to seek")
                GestureHint(icon: "sun.max", text: "Swipe up or down on the left for brightness")
                GestureHint(icon: "speaker.wave.2", text: "Swipe up or down on the right for volume")
                GestureHint(icon: "arrow.up.left.and.arrow.down.right", text: "Pinch to zoom")
            }
            if let item = engine.state.currentItem {
                Section("Details") {
                    LabeledContent("Title", value: item.title)
                    if let width = engine.state.videoWidth, let height = engine.state.videoHeight {
                        LabeledContent("Resolution", value: "\(width) × \(height)")
                    }
                    if engine.state.durationMillis > 0 {
                        LabeledContent("Duration", value: MediaFormatting.clock(millis: engine.state.durationMillis))
                    }
                    if let size = item.sizeBytes {
                        LabeledContent("Size", value: MediaFormatting.byteCount(size))
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

private struct ChoiceChip: View {
    let text: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.subheadline.weight(.semibold))
                .monospacedDigit()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(
                    selected ? Color.accentColor : Color(uiColor: .tertiarySystemFill),
                    in: Capsule()
                )
                .foregroundColor(selected ? .white : .primary)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct SelectableRow: View {
    let title: String
    let detail: String?
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundColor(.primary)
                    if let detail = detail, !detail.isEmpty {
                        Text(detail).font(.caption).foregroundColor(.secondary)
                    }
                }
                Spacer()
                if selected {
                    Image(systemName: "checkmark").foregroundColor(.accentColor)
                }
            }
            .contentShape(Rectangle())
        }
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct EmptySheetMessage: View {
    let icon: String
    let text: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 32)).foregroundColor(.secondary)
            Text(text).font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct GestureHint: View {
    let icon: String
    let text: String

    var body: some View {
        Label(text, systemImage: icon).font(.subheadline)
    }
}
