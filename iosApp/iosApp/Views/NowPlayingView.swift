import SwiftUI

public struct NowPlayingView: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    @Environment(\.dismiss) private var dismiss
    @State private var isDraggingSlider = false
    @State private var sliderValue: Double = 0.0
    @State private var showQueue = false

    public init(engine: AVPlayerPlaybackEngine) {
        self.engine = engine
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                Spacer()

                if let current = engine.state.currentItem {
                    ArtworkThumbnailView(uri: current.artworkUri, kind: current.kind, size: 280)
                        .padding(.bottom, 12)

                    VStack(spacing: 6) {
                        Text(current.title)
                            .font(.title2)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .padding(.horizontal, 24)

                        if let artist = current.artist {
                            Text(artist)
                                .font(.headline)
                                .foregroundColor(.secondary)
                        }

                        if let album = current.album {
                            Text(album)
                                .font(.subheadline)
                                .foregroundColor(.secondary.opacity(0.8))
                        }
                    }
                }

                Spacer()

                // Progress Bar
                VStack(spacing: 8) {
                    Slider(
                        value: Binding(
                            get: { isDraggingSlider ? sliderValue : engine.state.progress },
                            set: { newValue in
                                isDraggingSlider = true
                                sliderValue = newValue
                            }
                        ),
                        onEditingChanged: { editing in
                            if !editing {
                                let targetMillis = Int64(sliderValue * Double(engine.state.durationMillis))
                                engine.seek(toMillis: targetMillis)
                                isDraggingSlider = false
                            }
                        }
                    )
                    .accentColor(.accentColor)
                    .accessibilityLabel("Playback progress")
                    .accessibilityValue("\(formatTime(millis: engine.state.positionMillis)) of \(formatTime(millis: engine.state.durationMillis))")

                    HStack {
                        Text(formatTime(millis: isDraggingSlider ? Int64(sliderValue * Double(engine.state.durationMillis)) : engine.state.positionMillis))
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(formatTime(millis: engine.state.durationMillis))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding(.horizontal, 28)

                // Playback Controls
                HStack(spacing: 36) {
                    Button(action: engine.toggleShuffle) {
                        Image(systemName: "shuffle")
                            .font(.system(size: 20))
                            .foregroundColor(engine.state.isShuffled ? .accentColor : .secondary)
                    }
                    .accessibilityLabel(engine.state.isShuffled ? "Shuffle on" : "Shuffle off")

                    Button(action: engine.playPrevious) {
                        Image(systemName: "backward.fill")
                            .font(.system(size: 28))
                            .foregroundColor(.primary)
                    }
                    .accessibilityLabel("Previous track")

                    Button(action: engine.togglePlayPause) {
                        ZStack {
                            Circle()
                                .fill(Color.accentColor)
                                .frame(width: 68, height: 68)
                                .shadow(color: Color.accentColor.opacity(0.4), radius: 8, x: 0, y: 4)

                            Image(systemName: engine.state.status == .playing ? "pause.fill" : "play.fill")
                                .font(.system(size: 28))
                                .foregroundColor(.white)
                        }
                    }
                    .accessibilityLabel(engine.state.status == .playing ? "Pause" : "Play")

                    Button(action: engine.playNext) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 28))
                            .foregroundColor(.primary)
                    }
                    .accessibilityLabel("Next track")

                    Button(action: engine.cycleRepeatMode) {
                        Image(systemName: engine.state.repeatMode.iconName)
                            .font(.system(size: 20))
                            .foregroundColor(engine.state.repeatMode != .off ? .accentColor : .secondary)
                    }
                    .accessibilityLabel("Repeat mode: \(engine.state.repeatMode.rawValue)")
                }
                .padding(.vertical, 8)

                // Bottom Controls: Speed & Queue
                HStack {
                    Menu {
                        ForEach(PlaybackPreferences.speedPresets.map(Double.init), id: \.self) { speed in
                            Button(action: { engine.setPlaybackRate(Float(speed)) }) {
                                HStack {
                                    Text("\(String(format: "%.2fx", speed))")
                                    if abs(Double(engine.state.playbackRate) - speed) < 0.01 {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                        }
                    } label: {
                        Text("\(String(format: "%.2fx", engine.state.playbackRate))")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color.secondary.opacity(0.12))
                            .clipShape(Capsule())
                    }
                    .accessibilityLabel("Playback speed \(String(format: "%.2fx", engine.state.playbackRate))")

                    Spacer()

                    Button(action: { showQueue = true }) {
                        Image(systemName: "list.bullet")
                            .font(.system(size: 18))
                            .foregroundColor(.primary)
                            .padding(8)
                    }
                    .accessibilityLabel("Show playback queue")
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 16)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.primary)
                    }
                    .accessibilityLabel("Dismiss now playing")
                }
            }
            .sheet(isPresented: $showQueue) {
                QueueView(engine: engine)
            }
        }
    }

    private func formatTime(millis: Int64) -> String {
        guard millis > 0 else { return "0:00" }
        let totalSeconds = Int(millis / 1000)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        let hours = minutes / 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes % 60, seconds)
        } else {
            return String(format: "%d:%02d", minutes, seconds)
        }
    }
}
