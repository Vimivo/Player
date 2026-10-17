import SwiftUI

public struct SettingsView: View {
    @AppStorage("vimivo_theme") private var theme: String = "system"
    @AppStorage("vimivo_gapless") private var gapless: Bool = true
    @AppStorage("vimivo_hardware_decode") private var hardwareDecode: Bool = true
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

                Section(header: Text("Playback")) {
                    Toggle("Gapless Audio Playback", isOn: $gapless)
                    Toggle("Hardware Video Decoding", isOn: $hardwareDecode)
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
                        Text("1.0.0 (1)")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("Settings")
            .onAppear(perform: calculateCacheSize)
        }
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
