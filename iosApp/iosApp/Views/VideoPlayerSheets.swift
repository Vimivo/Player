import SwiftUI
import AVKit
import VimivoShared
import UniformTypeIdentifiers

/// Focused bottom sheets for the video player: speed, audio, subtitles, and general options.
struct PlayerSheetView: View {
    let sheet: PlayerSheet
    @ObservedObject var engine: AVPlayerPlaybackEngine
    @ObservedObject var model: VideoPlayerModel
    @Environment(\.dismiss) private var dismiss
    @State private var bookmarks: [Bookmark] = []
    @State private var pendingA: Int64?
    @State private var snapshotMessage: String?
    @State private var showSubtitleFilePicker = false

    static let speedPresets = PlaybackPreferences.speedPresets

    var body: some View {
        NavigationStack {
            Group {
                switch sheet {
                case .speed: speedContent
                case .audio: audioContent
                case .subtitles: subtitleContent
                case .options: optionsContent
                case .queue: nextUpContent
                case .tools: toolsContent
                case .decoder: decoderContent
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .fileImporter(
                isPresented: $showSubtitleFilePicker,
                allowedContentTypes: [.plainText, UTType(filenameExtension: "srt") ?? .plainText, UTType(filenameExtension: "vtt") ?? .plainText, UTType(filenameExtension: "ass") ?? .plainText, UTType(filenameExtension: "ssa") ?? .plainText],
                allowsMultipleSelection: false
            ) { result in
                if case .success(let urls) = result, let url = urls.first {
                    model.addExternalSubtitleFile(url: url)
                }
            }
        }
    }

    private var title: String {
        switch sheet {
        case .speed: return "Playback speed"
        case .audio: return "Audio track"
        case .subtitles: return "Subtitles"
        case .options: return "Playback settings"
        case .queue: return "Next up"
        case .tools: return "Tools"
        case .decoder: return "Decoder"
        }
    }

    // MARK: Picture

    /// A slider value on the -100…100 scale that applies the change to the picture as it moves.
    private func pictureBinding(_ keyPath: WritableKeyPath<PictureSettings, Int>) -> Binding<Double> {
        Binding(
            get: { Double(engine.pictureSettings[keyPath: keyPath]) },
            set: { value in
                var settings = engine.pictureSettings
                settings[keyPath: keyPath] = Int(value.rounded())
                Task { await engine.applyPicture(settings) }
            }
        )
    }

    // MARK: A–B repeat

    private var abRepeatTitle: String {
        if let region = engine.abRepeat {
            return "Repeating \(MediaFormatting.clock(millis: region.lowerBound)) – \(MediaFormatting.clock(millis: region.upperBound))"
        }
        if let pendingA { return "Set B (A is \(MediaFormatting.clock(millis: pendingA)))" }
        return "Set A at \(MediaFormatting.clock(millis: engine.state.positionMillis))"
    }

    /// A, then B, then clear: the same steps as on the other platforms (the shared `PlaybackControlPolicy`).
    private func advanceABRepeat() {
        let current = ABRepeatRange(
            pointA: (engine.abRepeat?.lowerBound ?? pendingA).map { KotlinLong(longLong: $0) },
            pointB: engine.abRepeat.map { KotlinLong(longLong: $0.upperBound) },
            enabled: engine.abRepeat != nil
        )
        let next = PlaybackControlPolicy.shared.nextABRepeat(current: current, positionMillis: engine.state.positionMillis)
        if next.enabled, let a = next.pointA?.int64Value, let b = next.pointB?.int64Value {
            engine.setABRepeat(a...b)
            pendingA = nil
        } else {
            engine.setABRepeat(nil)
            pendingA = next.pointA?.int64Value
        }
    }

    private func clearABRepeat() {
        engine.setABRepeat(nil)
        pendingA = nil
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
                    in: PlaybackPreferences.speedRange
                ) {
                    Text("Playback speed")
                } minimumValueLabel: {
                    Text("0.25×").font(.caption)
                } maximumValueLabel: {
                    Text(MediaFormatting.speed(PlaybackPreferences.speedPresets.last ?? 4)).font(.caption)
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
        List {
            Section(header: Text("Tracks")) {
                if model.audioOptions.isEmpty {
                    Text("Single embedded audio track").foregroundColor(.secondary)
                } else {
                    ForEach(model.audioOptions, id: \.self) { option in
                        SelectableRow(
                            title: option.displayName,
                            detail: option.extendedLanguageTag,
                            selected: option == model.selectedAudio
                        ) {
                            model.selectAudio(option)
                        }
                    }
                }
            }

            Section(header: Text("Audio Synchronization")) {
                HStack {
                    Text("Audio Delay")
                    Spacer()
                    Text("\(model.audioDelayMillis) ms")
                        .foregroundColor(.secondary)
                        .monospacedDigit()
                    Stepper("", value: $model.audioDelayMillis, in: -5000...5000, step: 50)
                        .labelsHidden()
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: Subtitles

    private var subtitleContent: some View {
        List {
            Section("Primary Subtitles") {
                SelectableRow(
                    title: "Off",
                    detail: nil,
                    selected: model.selectedSubtitle == nil && model.selectedExternalTrack == nil
                ) {
                    model.selectSubtitle(nil)
                    model.selectExternalSubtitle(nil)
                }

                if !model.subtitleOptions.isEmpty {
                    ForEach(model.subtitleOptions, id: \.self) { option in
                        SelectableRow(
                            title: option.displayName,
                            detail: "Embedded · " + (option.extendedLanguageTag ?? "und"),
                            selected: option == model.selectedSubtitle && model.selectedExternalTrack == nil
                        ) {
                            model.selectSubtitle(option)
                        }
                    }
                }

                if !model.externalTracks.isEmpty {
                    ForEach(model.externalTracks) { track in
                        SelectableRow(
                            title: track.name,
                            detail: track.isCompanion ? "Auto-loaded companion" : "External file",
                            selected: track.id == model.selectedExternalTrack?.id
                        ) {
                            model.selectExternalSubtitle(track)
                        }
                    }
                }

                Button {
                    showSubtitleFilePicker = true
                } label: {
                    Label("Load subtitle file...", systemImage: "doc.badge.plus")
                        .foregroundColor(.accentColor)
                }
            }

            Section("Secondary Subtitle (Dual Display)") {
                SelectableRow(
                    title: "Off",
                    detail: nil,
                    selected: model.secondarySubtitleOption == nil && model.selectedSecondaryExternalTrack == nil
                ) {
                    model.selectSecondarySubtitle(nil)
                }

                ForEach(model.subtitleOptions, id: \.self) { option in
                    SelectableRow(
                        title: option.displayName,
                        detail: "Top display · " + (option.extendedLanguageTag ?? ""),
                        selected: option == model.secondarySubtitleOption
                    ) {
                        model.selectSecondarySubtitle(option)
                    }
                }

                ForEach(model.externalTracks) { track in
                    SelectableRow(
                        title: track.name,
                        detail: "Top display · External",
                        selected: track.id == model.selectedSecondaryExternalTrack?.id
                    ) {
                        model.selectSecondaryExternalSubtitle(track)
                    }
                }
            }

            Section("Timing & Synchronization") {
                HStack {
                    Text("Delay")
                    Spacer()
                    Text("\(model.subtitleDelayMillis) ms")
                        .foregroundColor(.secondary)
                        .monospacedDigit()
                    Stepper("", value: $model.subtitleDelayMillis, in: -10000...10000, step: 50)
                        .labelsHidden()
                }

                Picker("Speed Multiplier", selection: $model.subtitleSpeedMultiplier) {
                    Text("0.5x").tag(0.5)
                    Text("0.75x").tag(0.75)
                    Text("1.0x (Normal)").tag(1.0)
                    Text("1.25x").tag(1.25)
                    Text("1.5x").tag(1.5)
                }
            }

            Section("Appearance") {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Font Size")
                        Spacer()
                        Text("\(Int(model.subtitleFontSize)) pt")
                            .foregroundColor(.secondary)
                    }
                    Slider(value: $model.subtitleFontSize, in: 12...36, step: 1)
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Bottom Margin")
                        Spacer()
                        Text("\(Int(model.subtitleBottomMargin)) pt")
                            .foregroundColor(.secondary)
                    }
                    Slider(value: $model.subtitleBottomMargin, in: 16...120, step: 2)
                }

                Picker("Text Color", selection: $model.subtitleTextColor) {
                    Text("White").tag(Color.white)
                    Text("Yellow").tag(Color.yellow)
                    Text("Cyan").tag(Color.cyan)
                    Text("Green").tag(Color.green)
                }

                Picker("Background", selection: $model.subtitleBgOpacity) {
                    Text("None").tag(0.0)
                    Text("Subtle (30%)").tag(0.3)
                    Text("Medium (60%)").tag(0.6)
                    Text("Solid (100%)").tag(1.0)
                }

                Toggle("Text Outline & Shadow", isOn: $model.subtitleOutline)
            }

            Section("Text Encoding") {
                Picker("Encoding", selection: $model.subtitleEncoding) {
                    ForEach(SubtitleEncoding.allCases) { enc in
                        Text(enc.rawValue).tag(enc)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: Options

    private var nextUpContent: some View {
        List {
            ForEach(Array(engine.queue.enumerated()), id: \.element.id) { index, item in
                Button {
                    engine.playQueue(items: engine.queue, startIndex: index)
                    dismiss()
                } label: {
                    HStack(spacing: 12) {
                        ArtworkThumbnailView(uri: item.artworkUri, kind: item.kind, size: 54)
                            .frame(width: 96, height: 54)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .lineLimit(1)
                                .fontWeight(index == engine.currentIndex ? .bold : .regular)
                                .foregroundColor(index == engine.currentIndex ? .accentColor : .primary)
                            if let duration = item.durationMillis, duration > 0 {
                                Text(MediaFormatting.clock(millis: duration)).font(.caption).foregroundColor(.secondary)
                            }
                        }
                        Spacer()
                        if index == engine.currentIndex { Text("Playing").font(.caption).foregroundColor(.accentColor) }
                    }
                }
                .listRowBackground(index == engine.currentIndex ? Color.accentColor.opacity(0.12) : nil)
            }
        }
        .listStyle(.plain)
    }

    private var optionsContent: some View {
        List {
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
                Toggle("Loop", isOn: Binding(
                    get: { engine.state.repeatMode == .one },
                    set: { engine.state.repeatMode = $0 ? .one : .off }
                ))
                if engine.queue.count > 1 {
                    Button {
                        model.sheet = .queue
                    } label: {
                        LabeledContent("Next up", value: "\(engine.queue.count) videos")
                    }
                }
            }
            .foregroundColor(.primary)
            Section("Aspect ratio") {
                ForEach(VideoAspect.allCases) { aspect in
                    SelectableRow(title: aspect.label, detail: nil, selected: aspect == model.aspect) {
                        model.setAspect(aspect)
                    }
                }
            }
            Section("Picture") {
                Picker("Crop", selection: Binding(
                    get: { engine.pictureSettings.cropPreset },
                    set: { preset in Task { await engine.chooseCrop(preset) } }
                )) {
                    ForEach(CropPreset.entries, id: \.name) { preset in Text(preset.label).tag(preset) }
                }
                PictureSlider(title: "Brightness", value: pictureBinding(\.brightness))
                PictureSlider(title: "Contrast", value: pictureBinding(\.contrast))
                PictureSlider(title: "Saturation", value: pictureBinding(\.saturation))
                PictureSlider(title: "Hue", value: pictureBinding(\.hue))
                PictureSlider(title: "Gamma", value: pictureBinding(\.gamma))
                Button("Reset picture") { Task { await engine.applyPicture(PictureSettings()) } }
                    .disabled(engine.pictureSettings.isNeutral)
            }
            Section("Frames") {
                Button { engine.stepFrame(forward: false) } label: { Label("Previous frame", systemImage: "backward.frame") }
                Button { engine.stepFrame(forward: true) } label: { Label("Next frame", systemImage: "forward.frame") }
                Button {
                    Task {
                        do {
                            let url = try await engine.saveSnapshot()
                            snapshotMessage = "Saved to Files › Vimivo › Snapshots (\(url.lastPathComponent))"
                        } catch {
                            snapshotMessage = "The snapshot could not be saved"
                        }
                    }
                } label: {
                    Label("Save snapshot", systemImage: "camera")
                }
                if let snapshotMessage {
                    Text(snapshotMessage).font(.footnote).foregroundColor(.secondary)
                }
            }
            .foregroundColor(.primary)
            Section("A–B repeat") {
                Button { advanceABRepeat() } label: {
                    Label(abRepeatTitle, systemImage: "repeat")
                }
                if engine.abRepeat != nil || pendingA != nil {
                    Button(role: .destructive) { clearABRepeat() } label: { Label("Clear A–B repeat", systemImage: "xmark") }
                }
            }
            .foregroundColor(.primary)
            Section("Bookmarks") {
                Button {
                    if engine.addBookmarkAtCurrentPosition() != nil {
                        bookmarks = engine.bookmarksForCurrentItem()
                    }
                } label: {
                    Label("Add bookmark at \(MediaFormatting.clock(millis: engine.state.positionMillis))", systemImage: "bookmark")
                }
                ForEach(bookmarks) { bookmark in
                    Button {
                        engine.seek(toMillis: bookmark.positionMillis)
                        dismiss()
                    } label: {
                        LabeledContent(bookmark.name, value: MediaFormatting.clock(millis: bookmark.positionMillis))
                    }
                    .accessibilityHint("Jumps to this position")
                }
                .onDelete { offsets in
                    offsets.map { bookmarks[$0] }.forEach(engine.deleteBookmark)
                    bookmarks.remove(atOffsets: offsets)
                }
            }
            .foregroundColor(.primary)
            .onAppear { bookmarks = engine.bookmarksForCurrentItem() }
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

    // MARK: Tools Grid

    private var toolsContent: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 14)], spacing: 14) {
                ToolButton(icon: "cpu", title: "Decoder", subtitle: model.decoderMode.chipLabel) {
                    model.sheet = .decoder
                }
                ToolButton(icon: "waveform", title: "Audio", subtitle: model.selectedAudio?.displayName ?? "Default") {
                    model.sheet = .audio
                }
                ToolButton(icon: "captions.bubble", title: "Subtitles", subtitle: model.selectedSubtitle?.displayName ?? "Off") {
                    model.sheet = .subtitles
                }
                ToolButton(icon: "speedometer", title: "Speed", subtitle: MediaFormatting.speed(engine.state.playbackRate)) {
                    model.sheet = .speed
                }
                ToolButton(icon: "moon.fill", title: "Night Mode", subtitle: model.nightModeDim > 0 ? "\(Int(model.nightModeDim * 100))%" : "Off") {
                    model.nightModeDim = model.nightModeDim > 0 ? 0 : 0.4
                }
                ToolButton(icon: "aspectratio", title: "Aspect", subtitle: model.aspect.shortLabel) {
                    let all = VideoAspect.allCases
                    let next = all[(all.firstIndex(of: model.aspect)! + 1) % all.count]
                    model.aspect = next
                }
                ToolButton(icon: "repeat.1", title: "A–B Loop", subtitle: engine.abRepeat != nil ? "Active" : "Off") {
                    advanceABRepeat()
                }
                ToolButton(icon: "lock.shield", title: "Kids Lock", subtitle: model.kidsLocked ? "On" : "Off") {
                    model.kidsLocked = true
                    dismiss()
                }
                ToolButton(icon: "lock.fill", title: "Lock", subtitle: "Screen") {
                    model.locked = true
                    model.controlsVisible = false
                    model.lockHintVisible = true
                    dismiss()
                }
            }
            .padding(20)

            if model.nightModeDim > 0 {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Night Mode Dimming: \(Int(model.nightModeDim * 100))%")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Slider(value: $model.nightModeDim, in: 0.1...0.8, step: 0.05)
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
            }
        }
    }

    // MARK: Decoder Selection

    private var decoderContent: some View {
        List {
            Section(footer: Text("Vimivo selects the optimal decoding path for each media file, automatically falling back when needed.")) {
                Button {
                    model.setDecoderMode(.hw)
                    dismiss()
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Hardware Decoder (HW)")
                                .foregroundColor(.primary)
                            Text("Uses Apple VideoToolbox hardware acceleration.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        if model.decoderMode == .hw {
                            Image(systemName: "checkmark").foregroundColor(.accentColor)
                        }
                    }
                }

                Button {
                    model.setDecoderMode(.hwPlus)
                    dismiss()
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Hardware+ Decoder (HW+)")
                                .foregroundColor(.primary)
                            Text("Advanced hardware acceleration pipeline.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        if model.decoderMode == .hwPlus {
                            Image(systemName: "checkmark").foregroundColor(.accentColor)
                        }
                    }
                }

                Button {
                    model.setDecoderMode(.sw)
                    dismiss()
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Software Decoder (SW)")
                                .foregroundColor(.primary)
                            Text("Pure CPU decoding for maximum compatibility.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        if model.decoderMode == .sw {
                            Image(systemName: "checkmark").foregroundColor(.accentColor)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

private struct ToolButton: View {
    let icon: String
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 24))
                    .foregroundColor(.accentColor)
                    .frame(height: 32)
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.primary)
                Text(subtitle)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 92)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
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

/// One colour control: a title, the value, and a slider from -100 to 100 that returns to 0 on double tap.
private struct PictureSlider: View {
    let title: String
    @Binding var value: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title)
                Spacer()
                Text("\(Int(value))").monospacedDigit().foregroundColor(.secondary)
            }
            Slider(value: $value, in: -100...100, step: 1)
                .accessibilityLabel(title)
                .onTapGesture(count: 2) { value = 0 }
        }
    }
}
