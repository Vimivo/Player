import SwiftUI

/// Bar heights for the waveform-style seek bar: a smooth shape that is the same every time for the same track. It is a
/// styled scrubber, not the audio's real amplitude.
func waveformShape(seed: String, count: Int = 72) -> [CGFloat] {
    var state = seed.unicodeScalars.reduce(UInt64(1469598103934665603)) { ($0 ^ UInt64($1.value)) &* 1099511628211 }
    var level: CGFloat = 0.5
    return (0..<count).map { _ in
        state = state &* 6364136223846793005 &+ 1442695040888963407
        let random = CGFloat((state >> 33) % 1000) / 1000
        level = min(1, max(0.18, level * 0.55 + random * 0.45 + 0.1))
        return level
    }
}

func formatClock(millis: Int64) -> String {
    let total = Int(max(0, millis) / 1000)
    let hours = total / 3600, minutes = total % 3600 / 60, seconds = total % 60
    return hours > 0 ? String(format: "%d:%02d:%02d", hours, minutes, seconds) : String(format: "%d:%02d", minutes, seconds)
}

/// A seek bar drawn as bars; tap or drag to seek. Behaves as an adjustable control for VoiceOver.
struct WaveformSeekBar: View {
    let progress: Double
    let seed: String
    let positionMillis: Int64
    let durationMillis: Int64
    let onCommit: (Double) -> Void
    @State private var dragProgress: Double?

    var body: some View {
        let shape = waveformShape(seed: seed)
        let shown = dragProgress ?? progress
        GeometryReader { proxy in
            Canvas { context, size in
                let slot = size.width / CGFloat(shape.count)
                let barWidth = slot * 0.56
                for (index, height) in shape.enumerated() {
                    let barHeight = size.height * height
                    let rect = CGRect(x: CGFloat(index) * slot + (slot - barWidth) / 2, y: (size.height - barHeight) / 2, width: barWidth, height: barHeight)
                    let played = (Double(index) + 0.5) / Double(shape.count) <= shown
                    context.fill(Path(roundedRect: rect, cornerRadius: barWidth / 2), with: .color(played ? .accentColor : Color.primary.opacity(0.22)))
                }
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
        .frame(height: 56)
        .accessibilityElement()
        .accessibilityLabel("Playback progress")
        .accessibilityValue("\(formatClock(millis: positionMillis)) of \(formatClock(millis: durationMillis))")
        .accessibilityAdjustableAction { direction in
            let step = 0.05
            onCommit(min(1, max(0, progress + (direction == .increment ? step : -step))))
        }
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
