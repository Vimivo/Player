import SwiftUI
import VimivoShared

public struct SettingsView: View {
    @AppStorage("vimivo_theme") private var theme: String = "system"
    @AppStorage(PlaybackPreferences.resumeVideoKey) private var resumeVideo: Bool = true
    @AppStorage(PlaybackPreferences.resumeSongsKey) private var resumeSongs: Bool = false
    @AppStorage("vimivo_app_language") private var appLanguage: String = "system"
    @AppStorage("vimivo_default_decoder") private var defaultDecoder: String = "hw"
    @AppStorage("vimivo_double_tap_seek") private var doubleTapSeek: Int = 10
    @AppStorage("vimivo_video_grid") private var defaultGridMode: Bool = false
    @AppStorage("vimivo_show_hidden") private var showHiddenFolders: Bool = false
    @AppStorage("vimivo_subtitle_encoding") private var subtitleEncoding: String = "UTF-8"
    @AppStorage("vimivo_subtitle_size") private var subtitleSize: Double = 18
    @AppStorage("vimivo_private_vault_pin") private var privateVaultPin: String = ""
    @ObservedObject private var engine = AVPlayerPlaybackEngine.shared
    @State private var cacheSizeFormatted: String = "Calculating..."
    @State private var showCacheAlert = false
    @State private var showPinSheet = false

    public init() {}

    public var body: some View {
        NavigationView {
            Form {
                // MARK: 1. List / Library
                Section(header: Text("List & Library")) {
                    Toggle("Default to Grid View", isOn: $defaultGridMode)
                    Toggle("Show Hidden Folders & Files", isOn: $showHiddenFolders)

                    HStack {
                        Text("Cached Artwork & Thumbnails")
                        Spacer()
                        Text(cacheSizeFormatted)
                            .foregroundColor(.secondary)
                    }

                    Button("Clear Cache", role: .destructive) {
                        clearCache()
                    }
                }

                // MARK: 2. Player
                Section(
                    header: Text("Player"),
                    footer: Text("Hardware decoder delivers maximum battery efficiency; software decoder ensures maximum format compatibility.")
                ) {
                    Toggle("Resume Videos & Long Audio", isOn: $resumeVideo)
                    Toggle("Resume Songs", isOn: $resumeSongs)

                    Picker("Default Decoder", selection: $defaultDecoder) {
                        Text("Hardware (HW)").tag("hw")
                        Text("Hardware+ (HW+)").tag("hw_plus")
                        Text("Software (SW)").tag("sw")
                    }

                    Picker("Double-Tap Seek Interval", selection: $doubleTapSeek) {
                        Text("5 seconds").tag(5)
                        Text("10 seconds").tag(10)
                        Text("15 seconds").tag(15)
                        Text("30 seconds").tag(30)
                    }
                }

                // MARK: 3. Audio
                soundSection

                // MARK: 4. Subtitles
                Section(header: Text("Subtitles")) {
                    Picker("Default Encoding", selection: $subtitleEncoding) {
                        ForEach(SubtitleEncoding.allCases) { enc in
                            Text(enc.rawValue).tag(enc.rawValue)
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("Default Font Size")
                            Spacer()
                            Text("\(Int(subtitleSize)) pt").foregroundColor(.secondary)
                        }
                        Slider(value: $subtitleSize, in: 12...36, step: 1)
                    }
                }

                // MARK: 5. General & Privacy
                Section(header: Text("General & Privacy")) {
                    Picker("App Language", selection: $appLanguage) {
                        Text("System Default").tag("system")
                        Text("English").tag("en")
                        Text("বাংলা (Bengali)").tag("bn")
                    }

                    Picker("Theme", selection: $theme) {
                        Text("System").tag("system")
                        Text("Dark").tag("dark")
                        Text("Light").tag("light")
                        Text("AMOLED").tag("amoled")
                    }

                    Button {
                        showPinSheet = true
                    } label: {
                        HStack {
                            Text(privateVaultPin.isEmpty ? "Set Private Vault PIN" : "Change Private Vault PIN")
                            Spacer()
                            Image(systemName: "lock.shield")
                                .foregroundColor(.accentColor)
                        }
                    }
                }

                Section(
                    header: Text("Privacy Commitment"),
                    footer: Text("Vimivo is a free, login-free, ad-free media player. Zero analytics, trackers, or account infrastructure.")
                ) {
                    HStack {
                        Image(systemName: "checkmark.shield.fill")
                            .foregroundColor(.green)
                        Text("100% Offline & Private")
                            .font(.subheadline.weight(.medium))
                    }
                }

                Section(header: Text("About")) {
                    HStack {
                        Text("Application")
                        Spacer()
                        Text("Vimivo for iOS")
                            .foregroundColor(.secondary)
                    }
                    HStack {
                        Text("Tagline")
                        Spacer()
                        Text("Every Sound. Every Frame.")
                            .foregroundColor(.secondary)
                    }
                    HStack {
                        Text("Version")
                        Spacer()
                        Text(Self.versionLabel)
                            .foregroundColor(.secondary)
                    }
                    NavigationLink("Open-source licences") { OpenSourceLicensesView() }
                }
            }
            .navigationTitle("Settings")
            .sheet(isPresented: $showPinSheet) {
                PrivateVaultPinSheet {
                    // PIN updated
                }
            }
            .onAppear(perform: calculateCacheSize)
        }
    }

    // MARK: Sound

    private func soundBinding<T>(_ keyPath: WritableKeyPath<AudioEffectSettings, T>) -> Binding<T> {
        Binding(
            get: { engine.audioSettings[keyPath: keyPath] },
            set: { value in
                var settings = engine.audioSettings
                settings[keyPath: keyPath] = value
                engine.applyAudio(settings)
            }
        )
    }

    private func bandBinding(_ index: Int) -> Binding<Double> {
        Binding(
            get: { Double(engine.audioSettings.customGains[index]) },
            set: { value in
                var settings = engine.audioSettings
                settings.customGains[index] = Float(value)
                engine.applyAudio(settings)
            }
        )
    }

    private var soundSection: some View {
        Section(
            header: Text("Sound"),
            footer: Text("The equalizer, volume boost and ReplayGain apply to music and video audio. Boost above 100 % is limited so it does not clip.")
        ) {
            Toggle("Equalizer", isOn: soundBinding(\.equalizerOn))
            if engine.audioSettings.equalizerOn {
                Picker("Preset", selection: soundBinding(\.preset)) {
                    ForEach(EqualizerPreset.entries, id: \.name) { preset in Text(preset.name.capitalized.replacingOccurrences(of: "_", with: " ")).tag(preset) }
                }
                SoundSlider(title: "Bass", value: Binding(get: { Double(engine.audioSettings.bass) }, set: { setBass($0) }), range: 0...1, unit: "")
                SoundSlider(title: "Treble", value: Binding(get: { Double(engine.audioSettings.treble) }, set: { setTreble($0) }), range: -12...12, unit: " dB")
                SoundSlider(title: "Preamp", value: Binding(get: { Double(engine.audioSettings.preampDb) }, set: { setPreamp($0) }), range: -12...0, unit: " dB")
                if engine.audioSettings.preset == EqualizerPreset.custom {
                    ForEach(Array(GraphicEqualizerBands.shared.CENTRES_HZ.enumerated()), id: \.offset) { index, hz in
                        SoundSlider(title: "\(hz) Hz", value: bandBinding(index), range: -12...12, unit: " dB")
                    }
                }
            }
            Picker("ReplayGain", selection: soundBinding(\.replayGain)) {
                ForEach(ReplayGainMode.entries, id: \.name) { mode in Text(mode.name.capitalized).tag(mode) }
            }
            SoundSlider(
                title: "Volume boost",
                value: Binding(get: { Double(engine.audioSettings.boost) * 100 }, set: { setBoost($0 / 100) }),
                range: 100...200,
                unit: " %"
            )
        }
    }

    private func setBass(_ value: Double) { var s = engine.audioSettings; s.bass = Float(value); engine.applyAudio(s) }
    private func setTreble(_ value: Double) { var s = engine.audioSettings; s.treble = Float(value); engine.applyAudio(s) }
    private func setPreamp(_ value: Double) { var s = engine.audioSettings; s.preampDb = Float(value); engine.applyAudio(s) }
    private func setBoost(_ value: Double) { var s = engine.audioSettings; s.boost = Float(value); engine.applyAudio(s) }

    private static var versionLabel: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (\(build))"
    }

    private func calculateCacheSize() {
        guard let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else {
            cacheSizeFormatted = "0 KB"
            return
        }
        let artDir = caches.appendingPathComponent("artwork", isDirectory: true)
        let files = (try? FileManager.default.contentsOfDirectory(at: artDir, includingPropertiesForKeys: [.fileSizeKey])) ?? []
        var totalBytes: Int64 = 0
        for f in files {
            if let vals = try? f.resourceValues(forKeys: [.fileSizeKey]), let size = vals.fileSize {
                totalBytes += Int64(size)
            }
        }
        let mb = Double(totalBytes) / (1024 * 1024)
        if mb >= 1.0 {
            cacheSizeFormatted = String(format: "%.1f MB", mb)
        } else {
            cacheSizeFormatted = String(format: "%.0f KB", Double(totalBytes) / 1024)
        }
    }

    private func clearCache() {
        guard let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return }
        let artDir = caches.appendingPathComponent("artwork", isDirectory: true)
        try? FileManager.default.removeItem(at: artDir)
        calculateCacheSize()
    }
}

/// About ▸ Open-source licences: the generated third-party list shared with the other apps (ADR-004), the LGPL
/// notice when this app contains LGPL code, and links to the exact sources. Links open only on an explicit tap.
struct OpenSourceLicensesView: View {
    private let notices = ThirdPartyNotices.shared
    private var components: [ThirdPartyComponent] { notices.forPlatform(platform: .ios) }

    var body: some View {
        List {
            Section {
                if let notice = notices.lgplNotice(platform: .ios) {
                    Text(notice).font(.footnote)
                } else {
                    Text("Vimivo is MIT-licensed. The iOS app plays media with Apple's system frameworks and contains no LGPL or GPL code.")
                        .font(.footnote)
                }
                if let url = URL(string: notices.SOURCE_REPOSITORY) {
                    Link("Source code", destination: url)
                }
            }
            Section(header: Text("Third-party components")) {
                ForEach(components, id: \.name) { component in
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(component.name) \(component.version)")
                        Text(component.license).font(.caption).foregroundColor(.accentColor)
                        if let url = URL(string: component.sourceUrl) {
                            Link("Source", destination: url).font(.caption)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .navigationTitle("Open-source licences")
    }
}

/// A labelled slider that shows its value, for the sound settings.
private struct SoundSlider: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let unit: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title)
                Spacer()
                Text(String(format: "%.0f", value) + unit).monospacedDigit().foregroundColor(.secondary)
            }
            Slider(value: $value, in: range).accessibilityLabel(title)
        }
    }
}
