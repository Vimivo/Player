import SwiftUI
import AVKit

/// Full-screen custom video player with MX-style gestures, matching the Android player.
public struct VideoPlayerView: View {
    @ObservedObject private var engine: AVPlayerPlaybackEngine
    @StateObject private var model: VideoPlayerModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private let onDismiss: () -> Void

    public init(engine: AVPlayerPlaybackEngine, onDismiss: @escaping () -> Void) {
        self.engine = engine
        self.onDismiss = onDismiss
        _model = StateObject(wrappedValue: VideoPlayerModel(engine: engine))
    }

    private struct HideKey: Equatable {
        let visible: Bool
        let playing: Bool
        let locked: Bool
        let sheetOpen: Bool
        let gesture: Bool
        let tick: Int
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            videoLayer
            PlayerGestureSurface(model: model)
                .ignoresSafeArea()
                .accessibilityHidden(true)
            errorCard
            if let cue = model.subtitleText {
                VStack {
                    Spacer()
                    Text(cue)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Color.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                        .padding(.horizontal, 24)
                        .padding(.bottom, model.controlsVisible && !model.locked ? 120 : 32)
                }
                .allowsHitTesting(false)
                .accessibilityLabel("Subtitle: \(cue)")
            }
            if model.doubleTapSide != 0 {
                DoubleTapSeekIndicator(side: model.doubleTapSide, seconds: model.doubleTapSeconds)
                    .allowsHitTesting(false)
            }
            if model.locked {
                lockOverlay
            } else if model.controlsVisible {
                VideoControls(engine: engine, model: model, onClose: close)
                    .transition(.opacity)
            }
            if let feedback = model.feedback {
                VStack {
                    GestureFeedbackHud(feedback: feedback)
                        .padding(.top, 72)
                    Spacer()
                }
                .allowsHitTesting(false)
                .transition(.opacity)
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: model.controlsVisible)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.15), value: model.feedback)
        .preferredColorScheme(.dark)
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .task(id: HideKey(
            visible: model.controlsVisible,
            playing: model.isPlaying,
            locked: model.locked,
            sheetOpen: model.sheet != nil,
            gesture: model.gestureActive,
            tick: model.interactionTick
        )) {
            guard model.controlsVisible, model.isPlaying, !model.locked, model.sheet == nil, !model.gestureActive else { return }
            try? await Task.sleep(nanoseconds: VideoPlayerModel.controlsTimeoutNanos)
            if !Task.isCancelled { model.controlsVisible = false }
        }
        .task(id: model.lockHintVisible) {
            guard model.lockHintVisible else { return }
            try? await Task.sleep(nanoseconds: VideoPlayerModel.controlsTimeoutNanos)
            if !Task.isCancelled { model.lockHintVisible = false }
        }
        .task(id: engine.state.currentItem?.id) {
            await model.loadMediaOptions()
        }
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = model.isPlaying
            model.startSubtitleRendering()
        }
        .onChange(of: engine.state.currentItem?.id) { id in
            // The queue finished (the engine stopped), so leave the player like MX does.
            if id == nil { close() }
        }
        .onChange(of: model.isPlaying) { playing in
            UIApplication.shared.isIdleTimerDisabled = playing
        }
        .onDisappear {
            UIApplication.shared.isIdleTimerDisabled = false
            model.tearDown()
        }
        .sheet(item: $model.sheet) { sheet in
            PlayerSheetView(sheet: sheet, engine: engine, model: model)
                .presentationDetents(sheet == .options ? [.medium, .large] : [.medium])
                .presentationDragIndicator(.visible)
                .preferredColorScheme(.dark)
        }
    }

    @ViewBuilder
    private var videoLayer: some View {
        let layer = PlayerLayerView(player: engine.player, gravity: model.aspect.gravity, model: model)
        Group {
            if let ratio = model.aspect.frameRatio {
                layer.aspectRatio(ratio, contentMode: .fit)
            } else {
                layer
            }
        }
        .scaleEffect(model.zoom)
        .ignoresSafeArea()
        .accessibilityLabel(engine.state.currentItem?.title ?? "Video")
    }

    @ViewBuilder
    private var errorCard: some View {
        if engine.state.status == .error {
            VStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.title2)
                Text("Can't play this video")
                    .font(.headline)
                Text(engine.state.errorMessage ?? "The file could not be opened.")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.72))
                    .multilineTextAlignment(.center)
            }
            .foregroundColor(.white)
            .padding(20)
            .frame(maxWidth: 420)
            .background(Color(white: 0.13).opacity(0.9), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(32)
        }
    }

    private var lockOverlay: some View {
        HStack {
            if model.lockHintVisible {
                Button(action: model.unlockControls) {
                    Label("Tap to unlock", systemImage: "lock.open.fill")
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 18)
                        .padding(.vertical, 12)
                        .background(Color.white.opacity(0.18), in: Capsule())
                        .foregroundColor(.white)
                }
                .padding(24)
                .transition(.opacity)
            }
            Spacer()
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: model.lockHintVisible)
    }

    private func close() {
        model.tearDown()
        onDismiss()
    }
}

// MARK: - Controls

private struct VideoControls: View {
    @ObservedObject var engine: AVPlayerPlaybackEngine
    @ObservedObject var model: VideoPlayerModel
    let onClose: () -> Void

    private var hasPrevious: Bool { engine.queue.count > 1 && engine.currentIndex > 0 }
    private var hasNext: Bool { engine.queue.count > 1 && engine.currentIndex < engine.queue.count - 1 }

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.72), .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 160)
                Spacer()
                LinearGradient(colors: [.clear, .black.opacity(0.78)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 200)
            }
            .ignoresSafeArea()
            .allowsHitTesting(false)

            VStack(spacing: 0) {
                topBar
                Spacer()
                bottomBar
            }

            centerTransport
        }
    }

    private var subtitle: String? {
        var parts: [String] = []
        if engine.queue.count > 1 {
            parts.append("\(engine.currentIndex + 1) of \(engine.queue.count)")
        }
        if let width = engine.state.videoWidth, let height = engine.state.videoHeight, width > 0, height > 0 {
            parts.append(MediaFormatting.resolutionLabel(width: width, height: height))
        }
        return parts.isEmpty ? nil : parts.joined(separator: "  ·  ")
    }

    private var topBar: some View {
        HStack(spacing: 4) {
            PlayerIconButton(systemName: "chevron.backward", label: "Close video", action: onClose)
            VStack(alignment: .leading, spacing: 2) {
                Text(engine.state.currentItem?.title ?? "")
                    .font(.headline)
                    .foregroundColor(.white)
                    .lineLimit(1)
                if let subtitle = subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundColor(.white.opacity(0.7))
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
            PlayerIconButton(systemName: "waveform", label: "Audio track") { open(.audio) }
            PlayerIconButton(systemName: "captions.bubble", label: "Subtitles") { open(.subtitles) }
            if model.pipController != nil {
                PlayerIconButton(systemName: "pip.enter", label: "Picture in picture") {
                    model.togglePictureInPicture()
                }
            }
            PlayerIconButton(systemName: "ellipsis", label: "Video options") { open(.options) }
        }
        .padding(.horizontal, 4)
        .padding(.top, 4)
    }

    private var centerTransport: some View {
        HStack(spacing: 20) {
            PlayerIconButton(systemName: "backward.end.fill", label: "Previous", size: 26, enabled: hasPrevious) {
                model.touch()
                engine.playPrevious()
            }
            PlayerIconButton(systemName: "gobackward.10", label: "Back 10 seconds", size: 28) {
                model.touch()
                model.skip(seconds: -VideoPlayerModel.skipSeconds)
            }
            Button {
                model.touch()
                engine.togglePlayPause()
            } label: {
                Image(systemName: model.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 76, height: 76)
                    .background(Color.black.opacity(0.42), in: Circle())
            }
            .accessibilityLabel(model.isPlaying ? "Pause" : "Play")
            PlayerIconButton(systemName: "goforward.10", label: "Forward 10 seconds", size: 28) {
                model.touch()
                model.skip(seconds: VideoPlayerModel.skipSeconds)
            }
            PlayerIconButton(systemName: "forward.end.fill", label: "Next", size: 26, enabled: hasNext) {
                model.touch()
                engine.playNext()
            }
        }
    }

    private var bottomBar: some View {
        VStack(spacing: 2) {
            VideoSeekBar(engine: engine, model: model)
            HStack(spacing: 4) {
                PlayerIconButton(systemName: "lock.fill", label: "Lock screen controls", action: model.lockControls)
                PlayerIconButton(
                    systemName: model.rotationLocked ? "lock.rotation" : "rotate.right",
                    label: model.rotationLocked ? "Unlock orientation" : "Lock orientation"
                ) {
                    model.touch()
                    model.toggleRotationLock(scene: activeScene)
                }
                Spacer()
                PlayerPill(
                    systemName: "speedometer",
                    text: MediaFormatting.speed(engine.state.playbackRate),
                    label: "Playback speed \(MediaFormatting.speed(engine.state.playbackRate))"
                ) { open(.speed) }
                PlayerPill(systemName: "aspectratio", text: model.aspect.shortLabel, label: "Aspect ratio \(model.aspect.label)") {
                    model.touch()
                    model.cycleAspect()
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
    }

    private var activeScene: UIWindowScene? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
    }

    private func open(_ sheet: PlayerSheet) {
        model.touch()
        model.sheet = sheet
    }
}

private struct VideoSeekBar: View {
    @ObservedObject var engine: AVPlayerPlaybackEngine
    @ObservedObject var model: VideoPlayerModel
    @State private var scrubFraction: Double?

    var body: some View {
        let state = engine.state
        let duration = max(state.durationMillis, 0)
        let position: Int64 = scrubFraction.map { Int64($0 * Double(duration)) } ?? min(state.positionMillis, duration)
        HStack(spacing: 12) {
            Text(MediaFormatting.clock(millis: position))
                .font(.subheadline.weight(.medium))
                .monospacedDigit()
                .foregroundColor(.white)
            GeometryReader { geo in
                let width = max(geo.size.width, 1)
                let played = duration > 0 ? CGFloat(position) / CGFloat(duration) : 0
                let buffered = duration > 0 ? min(CGFloat(state.bufferedMillis) / CGFloat(duration), 1) : 0
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.18)).frame(height: 4)
                    Capsule().fill(Color.white.opacity(0.35)).frame(width: width * buffered, height: 4)
                    Capsule().fill(Color.accentColor).frame(width: width * played, height: 4)
                    Circle()
                        .fill(Color.white)
                        .frame(width: scrubFraction == nil ? 14 : 20, height: scrubFraction == nil ? 14 : 20)
                        .offset(x: width * played - (scrubFraction == nil ? 7 : 10))
                }
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            guard duration > 0 else { return }
                            model.touch()
                            scrubFraction = min(max(Double(value.location.x / width), 0), 1)
                        }
                        .onEnded { _ in
                            if let fraction = scrubFraction {
                                engine.seek(toMillis: Int64(fraction * Double(duration)))
                            }
                            scrubFraction = nil
                        }
                )
            }
            .frame(height: 36)
            .accessibilityElement()
            .accessibilityLabel("Video playback seekbar")
            .accessibilityValue("\(MediaFormatting.clock(millis: position)) of \(MediaFormatting.clock(millis: duration))")
            .accessibilityAdjustableAction { direction in
                switch direction {
                case .increment: model.skip(seconds: VideoPlayerModel.skipSeconds)
                case .decrement: model.skip(seconds: -VideoPlayerModel.skipSeconds)
                @unknown default: break
                }
            }
            Button {
                model.touch()
                model.showRemaining.toggle()
            } label: {
                Text(model.showRemaining
                     ? "−" + MediaFormatting.clock(millis: duration - position)
                     : MediaFormatting.clock(millis: duration))
                    .font(.subheadline.weight(.medium))
                    .monospacedDigit()
                    .foregroundColor(.white)
            }
            .accessibilityHint("Toggles remaining time")
        }
    }
}

private struct PlayerIconButton: View {
    let systemName: String
    let label: String
    var size: CGFloat = 20
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: size, weight: .semibold))
                .foregroundColor(.white.opacity(enabled ? 1 : 0.35))
                .frame(width: size + 24, height: size + 24)
                .contentShape(Rectangle())
        }
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

private struct PlayerPill: View {
    let systemName: String
    let text: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: systemName).font(.system(size: 14, weight: .semibold))
                Text(text).font(.subheadline.weight(.semibold)).monospacedDigit()
            }
            .foregroundColor(.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Color.white.opacity(0.14), in: Capsule())
        }
        .accessibilityLabel(label)
    }
}

// MARK: - Gesture feedback

private struct GestureFeedbackHud: View {
    let feedback: GestureFeedback

    var body: some View {
        content
            .foregroundColor(.white)
            .background(Color.black.opacity(0.64), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var content: some View {
        switch feedback {
        case let .seek(target, delta, duration):
            VStack(spacing: 2) {
                Text(MediaFormatting.clock(millis: target))
                    .font(.system(size: 28, weight: .semibold))
                    .monospacedDigit()
                Text((delta >= 0 ? "+" : "−") + MediaFormatting.clock(millis: abs(delta)) + "  ·  " + MediaFormatting.clock(millis: duration))
                    .font(.subheadline.weight(.medium))
                    .monospacedDigit()
                    .foregroundColor(.white.opacity(0.72))
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 14)
        case let .level(icon, label, fraction):
            VStack(spacing: 10) {
                Image(systemName: icon).font(.system(size: 24, weight: .semibold))
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.white.opacity(0.24))
                        Capsule().fill(Color.white).frame(width: geo.size.width * CGFloat(min(max(fraction, 0), 1)))
                    }
                }
                .frame(height: 4)
                Text(label).font(.subheadline.weight(.medium)).monospacedDigit()
            }
            .frame(width: 160)
            .padding(.horizontal, 22)
            .padding(.vertical, 16)
        case let .message(icon, text):
            HStack(spacing: 8) {
                if let icon = icon {
                    Image(systemName: icon).font(.system(size: 16, weight: .semibold))
                }
                Text(text).font(.subheadline.weight(.semibold))
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
        }
    }
}

private struct DoubleTapSeekIndicator: View {
    let side: Int
    let seconds: Int

    var body: some View {
        let forward = side > 0
        GeometryReader { geo in
            HStack(spacing: 0) {
                if forward { Spacer(minLength: 0) }
                VStack(spacing: 4) {
                    Image(systemName: forward ? "forward.fill" : "backward.fill")
                        .font(.system(size: 30, weight: .semibold))
                    Text("\(forward ? "+" : "−")\(seconds) s")
                        .font(.headline)
                        .monospacedDigit()
                }
                .foregroundColor(.white)
                .frame(width: geo.size.width * 0.36, height: geo.size.height)
                .background(
                    UnevenCapsule(leading: !forward)
                        .fill(Color.white.opacity(0.10))
                )
                if !forward { Spacer(minLength: 0) }
            }
        }
        .ignoresSafeArea()
        .accessibilityLabel("\(forward ? "Forward" : "Back") \(seconds) seconds")
    }
}

/// Half-capsule whose round side faces the screen centre.
private struct UnevenCapsule: Shape {
    /// True when the shape sits on the leading edge (round side on the right).
    let leading: Bool

    func path(in rect: CGRect) -> Path {
        // The quad control sits one curve-width outside, so the bulge peaks exactly at the edge.
        let curve = rect.width * 0.5
        var path = Path()
        if leading {
            path.move(to: CGPoint(x: rect.minX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX - curve, y: rect.minY))
            path.addQuadCurve(to: CGPoint(x: rect.maxX - curve, y: rect.maxY), control: CGPoint(x: rect.maxX + curve, y: rect.midY))
            path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        } else {
            path.move(to: CGPoint(x: rect.maxX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.minX + curve, y: rect.minY))
            path.addQuadCurve(to: CGPoint(x: rect.minX + curve, y: rect.maxY), control: CGPoint(x: rect.minX - curve, y: rect.midY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        }
        path.closeSubpath()
        return path
    }
}
