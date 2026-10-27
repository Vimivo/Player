import SwiftUI
import UniformTypeIdentifiers
import VimivoShared

func formatClock(millis: Int64) -> String {
    let total = Int(max(0, millis) / 1000)
    let hours = total / 3600, minutes = total % 3600 / 60, seconds = total % 60
    return hours > 0 ? String(format: "%d:%02d:%02d", hours, minutes, seconds) : String(format: "%d:%02d", minutes, seconds)
}

/// The track's seek bar: a clean thin timeline with a thumb. Tap or drag to seek; VoiceOver sees an adjustable control either way.
struct WaveformSeekBar: View {
    let progress: Double
    var peaks: [Float]? = nil
    let positionMillis: Int64
    let durationMillis: Int64
    let onCommit: (Double) -> Void
    @State private var dragProgress: Double?

    var body: some View {
        let shown = dragProgress ?? progress
        GeometryReader { proxy in
            Canvas { context, size in
                drawLine(shown: shown, in: &context, size: size)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { dragProgress = min(1, max(0, $0.location.x / proxy.size.width)) }
                    .onEnded {
                        onCommit(min(1, max(0, $0.location.x / proxy.size.width)))
                        dragProgress = nil
                    }
            )
        }
        .frame(height: 36)
        .accessibilityElement()
        .accessibilityLabel("Playback progress")
        .accessibilityValue("\(formatClock(millis: positionMillis)) of \(formatClock(millis: durationMillis))")
        .accessibilityAdjustableAction { direction in
            let step = 0.05
            onCommit(min(1, max(0, progress + (direction == .increment ? step : -step))))
        }
    }

    private func drawLine(shown: Double, in context: inout GraphicsContext, size: CGSize) {
        let thickness: CGFloat = 4
        let y = size.height / 2
        let track = CGRect(x: 0, y: y - thickness / 2, width: size.width, height: thickness)
        context.fill(Path(roundedRect: track, cornerRadius: thickness / 2), with: .color(Color.primary.opacity(0.22)))
        let done = CGRect(x: 0, y: y - thickness / 2, width: size.width * CGFloat(shown), height: thickness)
        context.fill(Path(roundedRect: done, cornerRadius: thickness / 2), with: .color(.accentColor))
        let thumb = CGRect(x: size.width * CGFloat(shown) - 7, y: y - 7, width: 14, height: 14)
        context.fill(Path(ellipseIn: thumb), with: .color(.accentColor))
    }
}

/// A small pill with the file format, such as FLAC or MP3.
struct FormatBadge: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundColor(.accentColor)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(Color.accentColor.opacity(0.2), in: RoundedRectangle(cornerRadius: 6))
    }
}

/// Play/pause inside an accent ring.
struct RingPlayButton: View {
    let isPlaying: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(Color.accentColor.opacity(0.16))
                Circle().stroke(Color.accentColor, lineWidth: 2)
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 30))
                    .foregroundColor(.primary)
            }
            .frame(width: 76, height: 76)
        }
        .accessibilityLabel(isPlaying ? "Pause" : "Play")
    }
}

/// An icon over a label, used for the shortcuts under the player.
struct PlayerShortcut: View {
    let systemImage: String
    let title: String
    var active = false
    var badge: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: systemImage).font(.system(size: 20))
                    if let badge {
                        Text(badge).font(.caption2.weight(.bold)).foregroundColor(.white)
                            .padding(.horizontal, 5).background(Color.accentColor, in: Capsule()).offset(x: 12, y: -8)
                    }
                }
                Text(title).font(.caption2)
            }
            .foregroundColor(active ? .accentColor : .secondary)
            .frame(minWidth: 64, minHeight: 44)
        }
        .accessibilityLabel(title)
    }
}

/// A vertical gain slider for one equalizer band.
struct VerticalGainSlider: View {
    let gain: Double
    let range: ClosedRange<Double>
    let label: String
    let enabled: Bool
    let onChange: (Double) -> Void

    var body: some View {
        GeometryReader { proxy in
            let span = range.upperBound - range.lowerBound
            let thumbY = proxy.size.height * (range.upperBound - gain) / span
            let zeroY = proxy.size.height * range.upperBound / span
            ZStack(alignment: .top) {
                Capsule().fill(Color.primary.opacity(0.18)).frame(width: 4).frame(maxHeight: .infinity)
                Capsule().fill(Color.accentColor.opacity(enabled ? 1 : 0.4)).frame(width: 4, height: abs(thumbY - zeroY)).offset(y: min(thumbY, zeroY))
                Rectangle().fill(Color.primary.opacity(0.4)).frame(width: 18, height: 1).offset(y: zeroY)
                Circle().fill(Color.accentColor.opacity(enabled ? 1 : 0.4)).frame(width: 18, height: 18)
                    .overlay(Circle().fill(Color.white.opacity(0.9)).frame(width: 7, height: 7))
                    .offset(y: thumbY - 9)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0).onChanged { value in
                    guard enabled else { return }
                    let fraction = min(1, max(0, value.location.y / proxy.size.height))
                    onChange(range.upperBound - fraction * span)
                }
            )
        }
        .accessibilityElement()
        .accessibilityLabel("\(label) gain")
        .accessibilityValue(String(format: "%.1f decibels", gain))
        .accessibilityAdjustableAction { direction in
            guard enabled else { return }
            onChange(min(range.upperBound, max(range.lowerBound, gain + (direction == .increment ? 1 : -1))))
        }
    }
}

// MARK: - Subtitle Cue & Parser

public struct SubtitleCue: Identifiable, Hashable {
    public let id: Int
    public let startMillis: Int64
    public let endMillis: Int64
    public let text: String

    public init(id: Int, startMillis: Int64, endMillis: Int64, text: String) {
        self.id = id
        self.startMillis = startMillis
        self.endMillis = endMillis
        self.text = text
    }
}

public struct ExternalSubtitleTrack: Identifiable, Hashable {
    public let id: String
    public let name: String
    public let url: URL
    public let cues: [SubtitleCue]
    public let isCompanion: Bool

    public init(id: String = UUID().uuidString, name: String, url: URL, cues: [SubtitleCue], isCompanion: Bool = false) {
        self.id = id
        self.name = name
        self.url = url
        self.cues = cues
        self.isCompanion = isCompanion
    }
}

public enum SubtitleEncoding: String, CaseIterable, Identifiable {
    case utf8 = "UTF-8"
    case windows1252 = "Windows-1252 (Western)"
    case windows1256 = "Windows-1256 (Arabic)"
    case isoLatin1 = "ISO-8859-1 (Latin-1)"
    case utf16 = "UTF-16"

    public var id: String { rawValue }

    public var stringEncoding: String.Encoding {
        switch self {
        case .utf8: return .utf8
        case .windows1252: return .windowsCP1252
        case .windows1256: return String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.windowsArabic.rawValue)))
        case .isoLatin1: return .isoLatin1
        case .utf16: return .utf16
        }
    }
}

public enum SubtitleParser {
    public static func parse(data: Data, encoding: SubtitleEncoding = .utf8) -> [SubtitleCue] {
        guard let content = String(data: data, encoding: encoding.stringEncoding) ?? String(data: data, encoding: .utf8) ?? String(decoding: data, as: UTF8.self) as String? else {
            return []
        }
        return parse(text: content)
    }

    public static func parse(text: String) -> [SubtitleCue] {
        var cues: [SubtitleCue] = []
        let normalized = text.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
        let blocks = normalized.components(separatedBy: "\n\n")

        var cueIndex = 1
        for block in blocks {
            let lines = block.trimmingCharacters(in: .whitespacesAndNewlines).components(separatedBy: "\n")
            guard !lines.isEmpty else { continue }

            var timeLineIndex = 0
            if lines.count >= 2 && lines[0].range(of: "-->") == nil && lines[1].range(of: "-->") != nil {
                timeLineIndex = 1
            } else if lines[0].range(of: "-->") == nil {
                continue
            }

            let timeLine = lines[timeLineIndex]
            let timeParts = timeLine.components(separatedBy: "-->")
            guard timeParts.count == 2 else { continue }

            let startMillis = parseTimestamp(timeParts[0].trimmingCharacters(in: .whitespaces))
            let endRaw = timeParts[1].trimmingCharacters(in: .whitespaces).components(separatedBy: " ").first ?? ""
            let endMillis = parseTimestamp(endRaw)

            guard startMillis >= 0 && endMillis > startMillis else { continue }

            let textLines = lines.dropFirst(timeLineIndex + 1)
            let cueText = textLines.joined(separator: "\n").replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)

            if !cueText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                cues.append(SubtitleCue(id: cueIndex, startMillis: startMillis, endMillis: endMillis, text: cueText))
                cueIndex += 1
            }
        }
        return cues.sorted { $0.startMillis < $1.startMillis }
    }

    private static func parseTimestamp(_ raw: String) -> Int64 {
        let clean = raw.replacingOccurrences(of: ",", with: ".")
        let parts = clean.components(separatedBy: ":")
        guard parts.count >= 2 else { return -1 }

        var hours: Double = 0
        var minutes: Double = 0
        var seconds: Double = 0

        if parts.count == 3 {
            hours = Double(parts[0]) ?? 0
            minutes = Double(parts[1]) ?? 0
            seconds = Double(parts[2]) ?? 0
        } else if parts.count == 2 {
            minutes = Double(parts[0]) ?? 0
            seconds = Double(parts[1]) ?? 0
        }

        let totalSeconds = hours * 3600 + minutes * 60 + seconds
        return Int64(totalSeconds * 1000)
    }
}

// MARK: - Companion Subtitle Finder

public enum SubtitleLocator {
    public static let supportedExtensions = ["srt", "vtt", "webvtt", "ass", "ssa", "sub"]

    public static func findCompanionSubtitles(for videoUrl: URL) -> [ExternalSubtitleTrack] {
        guard videoUrl.isFileURL else { return [] }
        let parentDir = videoUrl.deletingLastPathComponent()
        let baseName = videoUrl.deletingPathExtension().lastPathComponent

        var results: [ExternalSubtitleTrack] = []
        let fm = FileManager.default

        for ext in supportedExtensions {
            let candidate = parentDir.appendingPathComponent("\(baseName).\(ext)")
            if fm.fileExists(atPath: candidate.path) {
                if let data = try? Data(contentsOf: candidate) {
                    let cues = SubtitleParser.parse(data: data)
                    results.append(ExternalSubtitleTrack(name: "\(baseName).\(ext)", url: candidate, cues: cues, isCompanion: true))
                }
            }
        }

        for subfolder in ["Subs", "Subtitles", "subs", "subtitles"] {
            let subDir = parentDir.appendingPathComponent(subfolder, isDirectory: true)
            if let files = try? fm.contentsOfDirectory(at: subDir, includingPropertiesForKeys: nil) {
                for file in files where supportedExtensions.contains(file.pathExtension.lowercased()) {
                    if let data = try? Data(contentsOf: file) {
                        let cues = SubtitleParser.parse(data: data)
                        results.append(ExternalSubtitleTrack(name: "\(file.lastPathComponent)", url: file, cues: cues, isCompanion: true))
                    }
                }
            }
        }

        return results
    }
}

// MARK: - M3U Parser & Exporter

public enum M3UPlaylistManager {
    public static func exportM3U(name: String, items: [MediaItem]) -> String {
        var lines: [String] = ["#EXTM3U", "#PLAYLIST:\(name)"]
        for item in items {
            let durationSeconds = (item.durationMillis ?? 0) / 1000
            lines.append("#EXTINF:\(durationSeconds),\(item.title)")
            lines.append(item.uri)
        }
        return lines.joined(separator: "\n")
    }

    public static func parseM3U(content: String, defaultName: String = "Imported Playlist") -> (name: String, items: [MediaItem]) {
        let lines = content.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").components(separatedBy: "\n")
        var playlistName = defaultName
        var items: [MediaItem] = []
        var nextTitle: String? = nil
        var nextDuration: Int64? = nil

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty { continue }

            if trimmed.hasPrefix("#PLAYLIST:") {
                playlistName = String(trimmed.dropFirst("#PLAYLIST:".count)).trimmingCharacters(in: .whitespaces)
            } else if trimmed.hasPrefix("#EXTINF:") {
                let info = String(trimmed.dropFirst("#EXTINF:".count))
                if let commaIdx = info.firstIndex(of: ",") {
                    let durStr = String(info[..<commaIdx]).trimmingCharacters(in: .whitespaces)
                    let titleStr = String(info[info.index(after: commaIdx)...]).trimmingCharacters(in: .whitespaces)
                    if let seconds = Double(durStr), seconds > 0 {
                        nextDuration = Int64(seconds * 1000)
                    }
                    nextTitle = titleStr
                }
            } else if !trimmed.hasPrefix("#") {
                let uri = trimmed
                let title = nextTitle ?? (URL(string: uri)?.lastPathComponent ?? "Track \(items.count + 1)")
                items.append(MediaItem(uri: uri, title: title, kind: .video, durationMillis: nextDuration))
                nextTitle = nil
                nextDuration = nil
            }
        }
        return (playlistName, items)
    }
}

// MARK: - Media Properties Sheet

public struct MediaPropertiesSheet: View {
    public let item: MediaItem
    @Environment(\.dismiss) private var dismiss

    public init(item: MediaItem) {
        self.item = item
    }

    public var body: some View {
        NavigationStack {
            List {
                Section("Details") {
                    LabeledContent("Title", value: item.title)
                    if let resolution = item.resolutionLabel {
                        LabeledContent("Resolution", value: resolution)
                    }
                    if let width = item.width, let height = item.height {
                        LabeledContent("Dimensions", value: "\(width) × \(height)")
                    }
                    if let dur = item.durationMillis, dur > 0 {
                        LabeledContent("Duration", value: item.formattedDuration)
                    }
                    if let size = item.sizeBytes {
                        LabeledContent("File Size", value: MediaFormatting.byteCount(size))
                    }
                    LabeledContent("Type", value: item.kind == .video ? "Video File" : "Audio File")
                }

                Section("Location") {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("URI / Path")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(item.uri)
                            .font(.system(.footnote, design: .monospaced))
                            .foregroundColor(.primary)
                            .textSelection(.enabled)
                    }
                    .padding(.vertical, 2)
                }

                if let date = item.dateAddedMillis, date > 0 {
                    Section("Date") {
                        LabeledContent("Added", value: Date(timeIntervalSince1970: TimeInterval(date / 1000)).formatted(date: .long, time: .shortened))
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Media Info")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Private Vault PIN Sheet

public struct PrivateVaultPinSheet: View {
    public let onUnlock: () -> Void
    @Environment(\.dismiss) private var dismiss
    @AppStorage("vimivo_private_vault_pin") private var savedPin: String = ""
    @State private var enteredPin = ""
    @State private var errorText: String?
    @State private var isSettingUp = false

    public init(onUnlock: @escaping () -> Void) {
        self.onUnlock = onUnlock
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()

                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 64))
                    .foregroundColor(.accentColor)

                Text(savedPin.isEmpty ? "Set Up Private Vault PIN" : "Enter Private Vault PIN")
                    .font(.title2.weight(.bold))

                Text(savedPin.isEmpty ? "Choose a 4-digit PIN to secure your private videos." : "Enter your 4-digit PIN to access private media.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                SecureField("4-digit PIN", text: $enteredPin)
                    .keyboardType(.numberPad)
                    .textContentType(.password)
                    .multilineTextAlignment(.center)
                    .font(.system(size: 32, weight: .bold, design: .monospaced))
                    .frame(maxWidth: 200)
                    .padding(12)
                    .background(Color.secondary.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
                    .onChange(of: enteredPin) { newValue in
                        if newValue.count == 4 {
                            submitPin()
                        }
                    }

                if let error = errorText {
                    Text(error)
                        .font(.caption.weight(.medium))
                        .foregroundColor(.red)
                }

                Button(action: submitPin) {
                    Text(savedPin.isEmpty ? "Set PIN" : "Unlock")
                        .fontWeight(.semibold)
                        .frame(maxWidth: 200)
                        .padding(.vertical, 12)
                        .background(enteredPin.count == 4 ? Color.accentColor : Color.secondary.opacity(0.3))
                        .foregroundColor(.white)
                        .clipShape(Capsule())
                }
                .disabled(enteredPin.count != 4)

                Spacer()
            }
            .padding()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .onAppear {
                isSettingUp = savedPin.isEmpty
            }
        }
    }

    private func submitPin() {
        if savedPin.isEmpty {
            savedPin = enteredPin
            onUnlock()
            dismiss()
        } else if enteredPin == savedPin {
            onUnlock()
            dismiss()
        } else {
            errorText = "Incorrect PIN. Try again."
            enteredPin = ""
        }
    }
}

// MARK: - Add To Playlist Sheet

public struct AddToPlaylistSheet: View {
    public let items: [MediaItem]
    @Environment(\.dismiss) private var dismiss
    @State private var playlists: [Playlist] = []
    @State private var newPlaylistName = ""
    @State private var showCreateDialog = false

    public init(items: [MediaItem]) {
        self.items = items
    }

    public var body: some View {
        NavigationStack {
            List {
                Button {
                    showCreateDialog = true
                } label: {
                    Label("Create New Playlist", systemImage: "plus.circle.fill")
                        .foregroundColor(.accentColor)
                        .fontWeight(.medium)
                }

                Section("Existing Playlists") {
                    if playlists.isEmpty {
                        Text("No playlists yet")
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(playlists) { playlist in
                            Button {
                                addToPlaylist(playlist)
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(playlist.name)
                                            .font(.body)
                                            .foregroundColor(.primary)
                                        Text("\(playlist.mediaIds.count) items")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "plus")
                                        .foregroundColor(.accentColor)
                                }
                            }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Add to Playlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .alert("New Playlist", isPresented: $showCreateDialog) {
                TextField("Playlist Name", text: $newPlaylistName)
                Button("Cancel", role: .cancel) { newPlaylistName = "" }
                Button("Create and Add") {
                    let trimmed = newPlaylistName.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty {
                        let newPl = Playlist(name: trimmed, mediaIds: items.map { $0.id })
                        IOSDatabase.shared.savePlaylist(newPl)
                        dismiss()
                    }
                }
            }
            .onAppear {
                playlists = IOSDatabase.shared.getAllPlaylists()
            }
        }
    }

    private func addToPlaylist(_ playlist: Playlist) {
        var existing = Set(playlist.mediaIds)
        var updated = playlist.mediaIds
        for item in items where !existing.contains(item.id) {
            existing.insert(item.id)
            updated.append(item.id)
        }
        let saved = Playlist(id: playlist.id, name: playlist.name, mediaIds: updated, createdAtMillis: playlist.createdAtMillis)
        IOSDatabase.shared.savePlaylist(saved)
        dismiss()
    }
}

// MARK: - Share Sheet Wrapper

public struct ShareSheet: UIViewControllerRepresentable {
    public let activityItems: [Any]

    public init(activityItems: [Any]) {
        self.activityItems = activityItems
    }

    public func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    public func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

// MARK: - Folder Row

public struct MxFolderRow: View {
    public let name: String
    public let count: Int
    public let newCount: Int
    public let isPrivate: Bool
    public let onOpen: () -> Void

    public init(name: String, count: Int, newCount: Int, isPrivate: Bool = false, onOpen: @escaping () -> Void) {
        self.name = name
        self.count = count
        self.newCount = newCount
        self.isPrivate = isPrivate
        self.onOpen = onOpen
    }

    public var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(isPrivate ? Color.orange.opacity(0.15) : Color.accentColor.opacity(0.15))
                        .frame(width: 48, height: 48)
                    Image(systemName: isPrivate ? "lock.shield.fill" : "folder.fill")
                        .foregroundColor(isPrivate ? .orange : .accentColor)
                        .font(.system(size: 22))
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(name)
                        .font(.body)
                        .fontWeight(.medium)
                        .foregroundColor(.primary)

                    Text("\(count) \(count == 1 ? "video" : "videos")")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                if newCount > 0 {
                    HStack(spacing: 2) {
                        Text("NEW")
                            .font(.system(size: 9, weight: .black))
                        if newCount > 1 {
                            Text("+\(newCount)")
                                .font(.system(size: 9, weight: .bold))
                        }
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .clipShape(Capsule())
                }

                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.secondary.opacity(0.6))
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
