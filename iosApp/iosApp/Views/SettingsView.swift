import SwiftUI
import VimivoShared

public struct SettingsView: View {
    @AppStorage("vimivo_theme") private var theme: String = "system"
    @AppStorage(PlaybackPreferences.resumeVideoKey) private var resumeVideo: Bool = true
    @AppStorage(PlaybackPreferences.resumeSongsKey) private var resumeSongs: Bool = false
    @State private var cacheSizeFormatted: String = "Calculating..."
    @State private var showCacheAlert = false

    public init() {}

    public var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Appearance")) {
                    Picker("Theme", selection: $theme) {
                        Text("System").tag("system")
                        Text("Dark").tag("dark")
                        Text("Light").tag("light")
                        Text("AMOLED").tag("amoled")
                    }
                }

                Section(
                    header: Text("Playback"),
                    footer: Text("Videos, audiobooks and other long recordings continue where you left off. Songs start from the beginning unless you turn on Resume Songs.")
                ) {
                    Toggle("Resume Videos & Long Audio", isOn: $resumeVideo)
                    Toggle("Resume Songs", isOn: $resumeSongs)
                }

                Section(header: Text("Storage & Cache")) {
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

                Section(header: Text("Design Principles")) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Every Sound. Every Frame.")
                            .font(.subheadline.weight(.semibold))
                        Text("Engineered for speed, clarity, and uncompromised audio-video playback fidelity.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
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
            .onAppear(perform: calculateCacheSize)
        }
    }

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
