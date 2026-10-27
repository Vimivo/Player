import SwiftUI
import VimivoShared

/// The equalizer panel: on/off, presets, ten vertical band sliders, preamp, bass and treble. Dragging a band turns the
/// current curve into a custom one, so a preset is never edited in place.
struct EqualizerSheet: View {
    @ObservedObject var engine: AVPlayerPlaybackEngine
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ScrollView { EqualizerContent(engine: engine).padding(.vertical) }
                .navigationTitle("Equalizer")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("Done") { dismiss() } } }
        }
    }
}

/// The equalizer controls without the navigation chrome, so they can also be rendered on their own.
struct EqualizerContent: View {
    @ObservedObject var engine: AVPlayerPlaybackEngine

    private var settings: AudioEffectSettings { engine.audioSettings }
    private var centres: [Int] { GraphicEqualizerBands.shared.CENTRES_HZ.map { Int(truncating: $0) } }

    private func update(_ change: (inout AudioEffectSettings) -> Void) {
        var next = engine.audioSettings
        change(&next)
        engine.applyAudio(next)
    }

    /// The band gains as shown: the preset's own, or the custom curve.
    private var gains: [Double] {
        if settings.preset == EqualizerPreset.custom { return settings.customGains.map(Double.init) }
        return EqualizerPresets.shared.gains(preset: settings.preset).map { $0.doubleValue }
    }

    private func bandLabel(_ hz: Int) -> String { hz >= 1000 ? "\(hz / 1000)k" : "\(hz)" }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Toggle("Equalizer", isOn: Binding(get: { settings.equalizerOn }, set: { value in update { $0.equalizerOn = value } }))
                .padding(.horizontal)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(EqualizerPreset.entries, id: \.name) { preset in
                        Button(preset.name.capitalized.replacingOccurrences(of: "_", with: " ")) {
                            update {
                                $0.preset = preset
                                $0.equalizerOn = true
                                if preset == EqualizerPreset.custom { $0.customGains = gains.map(Float.init) }
                            }
                        }
                        .font(.subheadline)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(settings.preset == preset ? Color.accentColor.opacity(0.25) : Color.secondary.opacity(0.12), in: Capsule())
                        .foregroundColor(.primary)
                    }
                }
                .padding(.horizontal)
            }
            HStack(alignment: .bottom, spacing: 0) {
                ForEach(Array(centres.enumerated()), id: \.offset) { index, hz in
                    VStack(spacing: 6) {
                        VerticalGainSlider(
                            gain: gains.indices.contains(index) ? gains[index] : 0,
                            range: -12...12,
                            label: "\(bandLabel(hz)) hertz",
                            enabled: settings.equalizerOn
                        ) { value in
                            update {
                                let base = gains
                                $0.preset = EqualizerPreset.custom
                                $0.customGains = base.map(Float.init)
                                $0.customGains[index] = Float(value)
                            }
                        }
                        .frame(height: 200)
                        Text(bandLabel(hz)).font(.caption2).foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 8)
            slider("Preamp", value: Double(settings.preampDb), range: -12...0, unit: " dB") { value in update { $0.preampDb = Float(value) } }
            slider("Bass", value: Double(settings.bass), range: 0...1, unit: "") { value in update { $0.bass = Float(value) } }
            slider("Treble", value: Double(settings.treble), range: -12...12, unit: " dB") { value in update { $0.treble = Float(value) } }
            Button("Reset") {
                update {
                    $0.preset = EqualizerPreset.flat
                    $0.customGains = Array(repeating: 0, count: 10)
                    $0.bass = 0; $0.treble = 0; $0.preampDb = 0
                }
            }
            .padding(.horizontal)
        }
    }

    private func slider(_ title: String, value: Double, range: ClosedRange<Double>, unit: String, onChange: @escaping (Double) -> Void) -> some View {
        VStack(alignment: .leading) {
            HStack { Text(title).font(.subheadline.weight(.medium)); Spacer(); Text(String(format: "%.1f", value) + unit).font(.subheadline).foregroundColor(.secondary) }
            Slider(value: Binding(get: { value }, set: onChange), in: range)
        }
        .padding(.horizontal)
    }
}
