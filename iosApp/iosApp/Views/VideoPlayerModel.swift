import SwiftUI
import AVKit
import UIKit
import VimivoShared
import Combine

enum VideoAspect: CaseIterable, Identifiable {
    case fit, crop, stretch, ratio16x9, ratio4x3

    var id: Self { self }

    var label: String {
        switch self {
        case .fit: return "Fit to screen"
        case .crop: return "Crop"
        case .stretch: return "Stretch"
        case .ratio16x9: return "16:9"
        case .ratio4x3: return "4:3"
        }
    }

    var shortLabel: String {
        switch self {
        case .fit: return "Fit"
        case .crop: return "Crop"
        case .stretch: return "Stretch"
        case .ratio16x9: return "16:9"
        case .ratio4x3: return "4:3"
        }
    }

    var gravity: AVLayerVideoGravity {
        switch self {
        case .fit: return .resizeAspect
        case .crop: return .resizeAspectFill
        case .stretch, .ratio16x9, .ratio4x3: return .resize
        }
    }

    /// Fixed frame ratio, or nil when the video fills the whole screen.
    var frameRatio: CGFloat? {
        switch self {
        case .ratio16x9: return 16.0 / 9.0
        case .ratio4x3: return 4.0 / 3.0
        default: return nil
        }
    }
}

enum PlayerSheet: String, Identifiable {
    case speed, audio, subtitles, options, queue, tools, decoder
    var id: String { rawValue }
}

struct KidParticle: Identifiable {
    let id = UUID()
    let point: CGPoint
    let color: Color
    let symbol: String
}

enum GestureFeedback: Equatable {
    case seek(target: Int64, delta: Int64, duration: Int64)
    case level(icon: String, label: String, fraction: Double)
    case message(icon: String?, text: String)
}

/// Transient UI state for the full-screen video player. Playback itself stays in the engine.
@MainActor
final class VideoPlayerModel: ObservableObject {
    static let skipSeconds = 10
    static let holdSpeed: Float = 2.0
    static let controlsTimeoutNanos: UInt64 = 3_500_000_000
    private static let feedbackTimeoutNanos: UInt64 = 900_000_000
    private static let doubleTapWindowNanos: UInt64 = 750_000_000

    let engine: AVPlayerPlaybackEngine

    @Published var controlsVisible = true
    @Published var locked = false
    @Published var kidsLocked = false
    @Published var kidParticles: [KidParticle] = []
    @Published var lockHintVisible = false
    @Published var rotationLocked = false
    @Published var aspect: VideoAspect = .fit
    @Published private(set) var zoom: CGFloat = 1
    @Published var showRemaining = false
    @Published var feedback: GestureFeedback?
    @Published private(set) var gestureActive = false
    @Published private(set) var doubleTapSide = 0
    @Published private(set) var doubleTapSeconds = 0
    @Published var sheet: PlayerSheet?
    @Published private(set) var interactionTick = 0
    @Published private(set) var audioOptions: [AVMediaSelectionOption] = []
    @Published private(set) var subtitleOptions: [AVMediaSelectionOption] = []
    @Published private(set) var selectedAudio: AVMediaSelectionOption?
    @Published private(set) var selectedSubtitle: AVMediaSelectionOption?
    /// Current subtitle cue, rendered by the player view so styling matches Android.
    @Published var subtitleText: String?
    @Published var externalTracks: [ExternalSubtitleTrack] = []
    @Published var selectedExternalTrack: ExternalSubtitleTrack?
    @Published var secondarySubtitleOption: AVMediaSelectionOption?
    @Published var selectedSecondaryExternalTrack: ExternalSubtitleTrack?
    @Published var secondarySubtitleText: String?
    @Published var subtitleDelayMillis: Int64 = 0
    @Published var subtitleSpeedMultiplier: Double = 1.0
    @Published var subtitleTextColor: Color = .white
    @Published var subtitleBgOpacity: Double = 0.6
    @Published var subtitleOutline: Bool = true
    @Published var subtitleEncoding: SubtitleEncoding = .utf8
    @Published var nightModeDim: Double = 0.0
    @Published var audioDelayMillis: Int64 = 0
    @Published var decoderMode: MobileDecoderMode = .hw
    @Published var seekThumbnail: UIImage?
    @Published var seekThumbnailTime: Int64?
    @Published var subtitleBottomMargin: CGFloat = 32
    @Published var subtitleFontSize: CGFloat = 18
    @Published var currentTimeString: String = ""
    @Published var batteryPercent: Int = 100

    /// Set by the player layer when picture in picture is supported on this device.
    var pipController: AVPictureInPictureController?

    private var holdBoostFrom: Float?
    private var feedbackTask: Task<Void, Never>?
    private var doubleTapTask: Task<Void, Never>?
    private var originalBrightness: (screen: UIScreen, value: CGFloat)?
    private lazy var legibleRelay = LegibleOutputRelay { [weak self] text in
        // If an external subtitle is chosen, ignore embedded cues
        guard self?.selectedExternalTrack == nil else { return }
        self?.subtitleText = text
    }
    // Options are only valid with the group instance they were loaded from.
    private var audioGroup: AVMediaSelectionGroup?
    private var subtitleGroup: AVMediaSelectionGroup?
    private var cancellables = Set<AnyCancellable>()

    init(engine: AVPlayerPlaybackEngine) {
        self.engine = engine
        engine.$state
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state in
                self?.updateCues(for: state.positionMillis)
            }
            .store(in: &cancellables)
    }

    func updateCues(for positionMillis: Int64) {
        let adjusted = Int64(Double(positionMillis + subtitleDelayMillis) * subtitleSpeedMultiplier)

        if let track = selectedExternalTrack {
            if let cue = track.cues.first(where: { adjusted >= $0.startMillis && adjusted <= $0.endMillis }) {
                self.subtitleText = cue.text
            } else {
                self.subtitleText = nil
            }
        }

        if let secTrack = selectedSecondaryExternalTrack {
            if let cue = secTrack.cues.first(where: { adjusted >= $0.startMillis && adjusted <= $0.endMillis }) {
                self.secondarySubtitleText = cue.text
            } else {
                self.secondarySubtitleText = nil
            }
        } else if secondarySubtitleOption == nil {
            self.secondarySubtitleText = nil
        }
    }

    /// Starts receiving subtitle cues from the engine's legible output.
    func startSubtitleRendering() {
        engine.legibleOutput.setDelegate(legibleRelay, queue: .main)
    }

    var isPlaying: Bool { engine.state.status == .playing }

    /// Restarts the auto-hide countdown after any control interaction.
    func touch() {
        interactionTick &+= 1
    }

    func setDecoderMode(_ mode: MobileDecoderMode) {
        self.decoderMode = mode
        if let uri = engine.state.currentItem?.uri {
            InMemoryDecoderPreferenceStore.companion.shared.setDecoderForMedia(uri: uri, mode: mode)
        }
    }

    func setAudioDelay(_ millis: Int64) {
        self.audioDelayMillis = millis
    }

    func addKidParticle(at point: CGPoint) {
        let colors: [Color] = [.yellow, .pink, .cyan, .green, .orange, .purple]
        let symbols = ["star.fill", "sparkles", "heart.fill", "circle.fill"]
        let particle = KidParticle(point: point, color: colors.randomElement() ?? .yellow, symbol: symbols.randomElement() ?? "star.fill")
        kidParticles.append(particle)
        Task {
            try? await Task.sleep(nanoseconds: 700_000_000)
            kidParticles.removeAll { $0.id == particle.id }
        }
    }

    func loadSeekThumbnail(at millis: Int64) {
        seekThumbnailTime = millis
        guard let item = engine.state.currentItem else { return }
        let urlString = item.uri
        let url: URL? = urlString.hasPrefix("/") ? URL(fileURLWithPath: urlString) : URL(string: urlString)
        guard let targetUrl = url else { return }
        Task.detached(priority: .userInitiated) {
            let asset = AVURLAsset(url: targetUrl)
            let generator = AVAssetImageGenerator(asset: asset)
            generator.appliesPreferredTrackTransform = true
            generator.maximumSize = CGSize(width: 240, height: 135)
            let cmTime = CMTime(seconds: Double(millis) / 1000.0, preferredTimescale: 600)
            if let cgImage = try? await generator.image(at: cmTime).image {
                let uiImage = UIImage(cgImage: cgImage)
                await MainActor.run {
                    if self.seekThumbnailTime == millis {
                        self.seekThumbnail = uiImage
                    }
                }
            }
        }
    }

    func updateSystemStatus() {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        currentTimeString = formatter.string(from: Date())
        UIDevice.current.isBatteryMonitoringEnabled = true
        let level = UIDevice.current.batteryLevel
        batteryPercent = level >= 0 ? Int(level * 100) : 100
    }

    // MARK: Taps

    func handleSingleTap(x: CGFloat, width: CGFloat) {
        if locked {
            lockHintVisible.toggle()
            return
        }
        // Extra taps on the same side keep accumulating an in-progress double-tap seek.
        if doubleTapSide != 0, side(for: x, width: width) == doubleTapSide {
            accumulateSeek(side: doubleTapSide)
            return
        }
        controlsVisible.toggle()
    }

    func handleDoubleTap(x: CGFloat, width: CGFloat) {
        let side = side(for: x, width: width)
        if side == 0 {
            let wasPlaying = isPlaying
            engine.togglePlayPause()
            showMessage(icon: wasPlaying ? "pause.fill" : "play.fill", text: wasPlaying ? "Paused" : "Playing")
        } else {
            accumulateSeek(side: side)
        }
    }

    private func side(for x: CGFloat, width: CGFloat) -> Int {
        if x < width * 0.4 { return -1 }
        if x > width * 0.6 { return 1 }
        return 0
    }

    private func accumulateSeek(side: Int) {
        doubleTapSeconds = doubleTapSide == side ? doubleTapSeconds + Self.skipSeconds : Self.skipSeconds
        doubleTapSide = side
        controlsVisible = false
        skip(seconds: side * Self.skipSeconds)
        doubleTapTask?.cancel()
        doubleTapTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: Self.doubleTapWindowNanos)
            guard !Task.isCancelled else { return }
            self?.doubleTapSide = 0
            self?.doubleTapSeconds = 0
        }
    }

    func skip(seconds: Int) {
        let state = engine.state
        let upper = max(state.durationMillis, 0)
        let target = min(max(state.positionMillis + Int64(seconds) * 1000, 0), upper)
        engine.seek(toMillis: target)
    }

    // MARK: Hold, pinch, drag

    func beginHoldBoost() {
        guard isPlaying, holdBoostFrom == nil else { return }
        holdBoostFrom = engine.state.playbackRate
        engine.setPlaybackRate(Self.holdSpeed)
        gestureActive = true
        feedbackTask?.cancel()
        feedback = .message(icon: "forward.fill", text: "\(MediaFormatting.speed(Self.holdSpeed)) speed")
    }

    func endHoldBoost() {
        guard let previous = holdBoostFrom else { return }
        engine.setPlaybackRate(previous)
        holdBoostFrom = nil
        endGesture()
    }

    func beginGesture() {
        gestureActive = true
        controlsVisible = false
        feedbackTask?.cancel()
    }

    func endGesture() {
        gestureActive = false
        scheduleFeedbackClear()
    }

    func setZoom(_ value: CGFloat) {
        zoom = value
        feedback = .message(icon: "plus.magnifyingglass", text: "Zoom \(Int((value * 100).rounded()))%")
    }

    func setBrightness(_ value: CGFloat, on screen: UIScreen) {
        if originalBrightness == nil {
            originalBrightness = (screen, screen.brightness)
        }
        screen.brightness = value
        feedback = .level(
            icon: value < 0.4 ? "sun.min.fill" : "sun.max.fill",
            label: "Brightness \(Int((value * 100).rounded()))",
            fraction: Double(value)
        )
    }

    // MARK: Controls

    func cycleAspect() {
        let all = VideoAspect.allCases
        let next = all[(all.firstIndex(of: aspect).map { $0 + 1 } ?? 0) % all.count]
        setAspect(next)
    }

    func setAspect(_ value: VideoAspect) {
        aspect = value
        zoom = 1
        showMessage(icon: "aspectratio", text: value.label)
    }

    func lockControls() {
        locked = true
        controlsVisible = false
        lockHintVisible = true
    }

    func unlockControls() {
        locked = false
        lockHintVisible = false
        controlsVisible = true
    }

    func toggleRotationLock(scene: UIWindowScene?) {
        rotationLocked.toggle()
        OrientationController.shared.lock(to: rotationLocked ? scene : nil)
        showMessage(
            icon: rotationLocked ? "lock.rotation" : "rotate.right",
            text: rotationLocked ? "Rotation locked" : "Auto-rotate"
        )
    }

    func togglePictureInPicture() {
        guard let controller = pipController else { return }
        if controller.isPictureInPictureActive {
            controller.stopPictureInPicture()
        } else {
            controller.startPictureInPicture()
        }
    }

    func showMessage(icon: String?, text: String) {
        feedback = .message(icon: icon, text: text)
        scheduleFeedbackClear()
    }

    private func scheduleFeedbackClear() {
        feedbackTask?.cancel()
        feedbackTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: Self.feedbackTimeoutNanos)
            guard !Task.isCancelled, let self = self, !self.gestureActive else { return }
            self.feedback = nil
        }
    }

    // MARK: Audio and subtitle tracks

    func loadMediaOptions() async {
        subtitleText = nil
        secondarySubtitleText = nil
        externalTracks = []
        selectedExternalTrack = nil
        selectedSecondaryExternalTrack = nil

        // Scan for companion subtitle files next to video or in Subs/ folder
        if let current = engine.state.currentItem {
            let candidateUrl = SecurityScopedMediaManager.shared.resolvedUrl(for: current) ?? URL(string: current.uri)
            if let url = candidateUrl {
                let companions = SubtitleLocator.findCompanionSubtitles(for: url)
                await MainActor.run {
                    self.externalTracks = companions
                }
            }
        }

        guard let item = engine.player.currentItem else {
            audioOptions = []
            subtitleOptions = []
            return
        }
        do {
            audioGroup = try await item.asset.loadMediaSelectionGroup(for: .audible)
            subtitleGroup = try await item.asset.loadMediaSelectionGroup(for: .legible)
            if let group = audioGroup {
                audioOptions = group.options
                selectedAudio = item.currentMediaSelection.selectedMediaOption(in: group)
            } else {
                audioOptions = []
            }
            if let group = subtitleGroup {
                subtitleOptions = group.options
                selectedSubtitle = item.currentMediaSelection.selectedMediaOption(in: group)
            } else {
                subtitleOptions = []
            }
        } catch {
            NSLog("Vimivo: could not load media selection groups: %@", error.localizedDescription)
            audioGroup = nil
            subtitleGroup = nil
            audioOptions = []
            subtitleOptions = []
        }
    }

    func selectAudio(_ option: AVMediaSelectionOption) {
        guard let item = engine.player.currentItem, let group = audioGroup else { return }
        item.select(option, in: group)
        selectedAudio = option
        showMessage(icon: "waveform", text: option.displayName)
    }

    func selectSubtitle(_ option: AVMediaSelectionOption?) {
        guard let item = engine.player.currentItem, let group = subtitleGroup else { return }
        item.select(option, in: group)
        selectedSubtitle = option
        selectedExternalTrack = nil
        subtitleText = nil
        engine.reattachLegibleOutput()
        showMessage(icon: "captions.bubble", text: option?.displayName ?? "Subtitles off")
    }

    func selectExternalSubtitle(_ track: ExternalSubtitleTrack?) {
        if let item = engine.player.currentItem, let group = subtitleGroup {
            item.select(nil, in: group)
        }
        selectedSubtitle = nil
        selectedExternalTrack = track
        subtitleText = nil
        showMessage(icon: "captions.bubble", text: track?.name ?? "External subtitles off")
    }

    func selectSecondarySubtitle(_ option: AVMediaSelectionOption?) {
        secondarySubtitleOption = option
        selectedSecondaryExternalTrack = nil
        secondarySubtitleText = nil
        showMessage(icon: "captions.bubble", text: option != nil ? "Secondary: \(option!.displayName)" : "Secondary subtitle off")
    }

    func selectSecondaryExternalSubtitle(_ track: ExternalSubtitleTrack?) {
        secondarySubtitleOption = nil
        selectedSecondaryExternalTrack = track
        secondarySubtitleText = nil
        showMessage(icon: "captions.bubble", text: track != nil ? "Secondary: \(track!.name)" : "Secondary subtitle off")
    }

    func addExternalSubtitleFile(url: URL) {
        guard url.startAccessingSecurityScopedResource() else {
            loadSubtitleFromUrl(url)
            return
        }
        defer { url.stopAccessingSecurityScopedResource() }
        loadSubtitleFromUrl(url)
    }

    private func loadSubtitleFromUrl(_ url: URL) {
        guard let data = try? Data(contentsOf: url) else { return }
        let cues = SubtitleParser.parse(data: data, encoding: subtitleEncoding)
        let track = ExternalSubtitleTrack(name: url.lastPathComponent, url: url, cues: cues)
        externalTracks.append(track)
        selectExternalSubtitle(track)
    }

    // MARK: Teardown

    func tearDown() {
        endHoldBoost()
        if let original = originalBrightness {
            original.screen.brightness = original.value
            originalBrightness = nil
        }
        if rotationLocked {
            rotationLocked = false
            OrientationController.shared.lock(to: nil)
        }
        feedbackTask?.cancel()
        doubleTapTask?.cancel()
        engine.legibleOutput.setDelegate(nil, queue: nil)
    }
}

final class LegibleOutputRelay: NSObject, AVPlayerItemLegibleOutputPushDelegate {
    private let onText: @MainActor (String?) -> Void

    init(onText: @escaping @MainActor (String?) -> Void) {
        self.onText = onText
    }

    func legibleOutput(
        _ output: AVPlayerItemLegibleOutput,
        didOutputAttributedStrings strings: [NSAttributedString],
        nativeSampleBuffers nativeSamples: [Any],
        forItemTime itemTime: CMTime
    ) {
        let text = strings.map(\.string).joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        Task { @MainActor [onText] in onText(text.isEmpty ? nil : text) }
    }
}
