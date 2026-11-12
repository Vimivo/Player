import SwiftUI
import UniformTypeIdentifiers

/// Audio tracks and delay for a file libmpv is playing (Matroska, AVI, ...).
struct MpvAudioSheetContent: View {
    @ObservedObject var engine: AVPlayerPlaybackEngine
    @ObservedObject var backend: MpvBackend
    @State private var delayMillis = 0

    var body: some View {
        List {
            Section(header: Text("Tracks")) {
                let audio = backend.tracks.filter { $0.kind == .audio }
                if audio.isEmpty {
                    Text("Single embedded audio track").foregroundColor(.secondary)
                } else {
                    ForEach(audio) { track in
                        SelectableRow(title: track.label, detail: nil, selected: track.isSelected) {
                            backend.selectAudio(track.id)
                        }
                    }
                }
            }
            Section(header: Text("Audio Synchronization")) {
                HStack {
                    Text("Audio Delay")
                    Spacer()
                    Text("\(delayMillis) ms").foregroundColor(.secondary).monospacedDigit()
                    Stepper("", value: $delayMillis, in: -10_000...10_000, step: 50)
                        .labelsHidden()
                        .onChange(of: delayMillis) { backend.setAudioDelay(seconds: Double($0) / 1000.0) }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

/// Subtitle tracks, sidecar files, timing and styling for a file libmpv is playing. Every subtitle format the file holds
/// is shown: SRT, ASS/SSA (with its own styling), WebVTT, PGS, VobSub and the rest.
struct MpvSubtitleSheetContent: View {
    @ObservedObject var backend: MpvBackend
    @State private var showFilePicker = false
    @State private var delayMillis = 0
    @State private var scale = 1.0
    @State private var position = 100.0
    @State private var colorName = "White"
    @State private var overrideStyle = false

    private static let colors: [(String, String)] = [("White", "#FFFFFF"), ("Yellow", "#FFE600"), ("Green", "#4CD964"), ("Cyan", "#4DD2FF")]
    private static let subtitleTypes: [UTType] = ["srt", "ass", "ssa", "vtt", "sub", "idx", "sup", "smi", "txt", "lrc"]
        .compactMap { UTType(filenameExtension: $0) } + [.plainText]

    var body: some View {
        List {
            Section("Primary Subtitles") {
                let subs = backend.tracks.filter { $0.kind == .sub }
                SelectableRow(title: "Off", detail: nil, selected: !subs.contains { $0.isSelected }) {
                    backend.selectSubtitle(nil)
                }
                ForEach(subs) { track in
                    SelectableRow(title: track.label, detail: track.isExternal ? "External file" : nil, selected: track.isSelected) {
                        backend.selectSubtitle(track.id)
                    }
                }
                Button("Load subtitle file...") { showFilePicker = true }
            }
            Section("Secondary Subtitle (Dual Display)") {
                SelectableRow(title: "None", detail: nil, selected: true) { backend.selectSecondarySubtitle(nil) }
                ForEach(backend.tracks.filter { $0.kind == .sub }) { track in
                    SelectableRow(title: track.label, detail: nil, selected: false) { backend.selectSecondarySubtitle(track.id) }
                }
            }
            Section("Timing & Synchronization") {
                HStack {
                    Text("Delay")
                    Spacer()
                    Text("\(delayMillis) ms").foregroundColor(.secondary).monospacedDigit()
                    Stepper("", value: $delayMillis, in: -30_000...30_000, step: 100)
                        .labelsHidden()
                        .onChange(of: delayMillis) { backend.setSubtitleDelay(seconds: Double($0) / 1000.0) }
                }
            }
            Section("Appearance") {
                HStack {
                    Text("Font Size")
                    Slider(value: $scale, in: 0.5...3.0, step: 0.1)
                        .onChange(of: scale) { backend.setSubtitleScale($0) }
                }
                HStack {
                    Text("Bottom Margin")
                    Slider(value: $position, in: 0...100, step: 1)
                        .onChange(of: position) { backend.setSubtitlePosition(percentFromTop: $0) }
                }
                Picker("Text Color", selection: $colorName) {
                    ForEach(Self.colors, id: \.0) { Text($0.0).tag($0.0) }
                }
                .onChange(of: colorName) { applyStyle(colorName: $0) }
                Toggle("Use my style instead of the file's own", isOn: $overrideStyle)
                    .onChange(of: overrideStyle) { _ in applyStyle(colorName: colorName) }
            }
        }
        .listStyle(.insetGrouped)
        .fileImporter(isPresented: $showFilePicker, allowedContentTypes: Self.subtitleTypes, allowsMultipleSelection: false) { result in
            guard case .success(let urls) = result, let url = urls.first else { return }
            let scoped = url.startAccessingSecurityScopedResource()
            // mpv reads the file while it is selected, so it is copied somewhere that stays readable.
            let copy = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
            try? FileManager.default.removeItem(at: copy)
            if (try? FileManager.default.copyItem(at: url, to: copy)) != nil { backend.addSubtitle(file: copy) }
            if scoped { url.stopAccessingSecurityScopedResource() }
        }
    }

    private func applyStyle(colorName: String) {
        let hex = Self.colors.first { $0.0 == colorName }?.1 ?? "#FFFFFF"
        backend.setSubtitleStyle(color: hex, borderSize: 3, shadow: 1, overrideStyle: overrideStyle)
    }
}
