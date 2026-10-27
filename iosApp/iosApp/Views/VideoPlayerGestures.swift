import SwiftUI
import AVKit
import MediaPlayer
import UIKit

/// Renders the engine's AVPlayer through an AVPlayerLayer and owns the picture-in-picture controller.
struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer
    let gravity: AVLayerVideoGravity
    let model: VideoPlayerModel

    func makeUIView(context: Context) -> PlayerLayerHostView {
        let view = PlayerLayerHostView()
        view.backgroundColor = .black
        view.playerLayer.player = player
        view.playerLayer.videoGravity = gravity
        if AVPictureInPictureController.isPictureInPictureSupported() {
            model.pipController = AVPictureInPictureController(playerLayer: view.playerLayer)
        }
        return view
    }

    func updateUIView(_ uiView: PlayerLayerHostView, context: Context) {
        if uiView.playerLayer.player !== player {
            uiView.playerLayer.player = player
        }
        if uiView.playerLayer.videoGravity != gravity {
            uiView.playerLayer.videoGravity = gravity
        }
    }

    static func dismantleUIView(_ uiView: PlayerLayerHostView, coordinator: ()) {
        uiView.playerLayer.player = nil
    }
}

final class PlayerLayerHostView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    // swiftlint:disable:next force_cast
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}

/// Transparent UIKit surface that recognises the MX-style gestures: tap, double tap, hold,
/// one-finger pan (seek / brightness / volume) and pinch to zoom.
struct PlayerGestureSurface: UIViewRepresentable {
    let model: VideoPlayerModel

    func makeCoordinator() -> Coordinator { Coordinator(model: model) }

    func makeUIView(context: Context) -> GestureHostView {
        let view = GestureHostView()
        view.backgroundColor = .clear
        let coordinator = context.coordinator
        coordinator.hostView = view

        let doubleTap = UITapGestureRecognizer(target: coordinator, action: #selector(Coordinator.handleDoubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2
        let singleTap = UITapGestureRecognizer(target: coordinator, action: #selector(Coordinator.handleSingleTap(_:)))
        singleTap.require(toFail: doubleTap)
        let hold = UILongPressGestureRecognizer(target: coordinator, action: #selector(Coordinator.handleHold(_:)))
        hold.minimumPressDuration = 0.5
        let pan = UIPanGestureRecognizer(target: coordinator, action: #selector(Coordinator.handlePan(_:)))
        pan.maximumNumberOfTouches = 1
        let pinch = UIPinchGestureRecognizer(target: coordinator, action: #selector(Coordinator.handlePinch(_:)))

        for recognizer in [singleTap, doubleTap, hold, pan, pinch] as [UIGestureRecognizer] {
            recognizer.delegate = coordinator
            view.addGestureRecognizer(recognizer)
        }
        coordinator.singleTap = singleTap
        return view
    }

    func updateUIView(_ uiView: GestureHostView, context: Context) {
        context.coordinator.model = model
    }

    @MainActor
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var model: VideoPlayerModel
        weak var hostView: GestureHostView?
        weak var singleTap: UITapGestureRecognizer?

        private enum DragMode { case none, seek, brightness, volume }
        private var mode = DragMode.none
        private var seekStart: Int64 = 0
        private var seekTarget: Int64 = 0
        private var level: CGFloat = 0
        private var zoomStart: CGFloat = 1
        private var startsAtEdge = false

        init(model: VideoPlayerModel) {
            self.model = model
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            if model.kidsLocked {
                return gestureRecognizer === singleTap
            }
            // While locked only the single tap (which reveals the unlock button) is active.
            return !model.locked || gestureRecognizer === singleTap
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool {
            gestureRecognizer is UIPinchGestureRecognizer || other is UIPinchGestureRecognizer
        }

        @objc func handleSingleTap(_ recognizer: UITapGestureRecognizer) {
            guard let view = recognizer.view else { return }
            let point = recognizer.location(in: view)
            if model.kidsLocked {
                model.addKidParticle(at: point)
                return
            }
            model.handleSingleTap(x: point.x, width: view.bounds.width)
        }

        @objc func handleDoubleTap(_ recognizer: UITapGestureRecognizer) {
            guard let view = recognizer.view else { return }
            let point = recognizer.location(in: view)
            model.handleDoubleTap(x: point.x, width: view.bounds.width)
        }

        @objc func handleHold(_ recognizer: UILongPressGestureRecognizer) {
            switch recognizer.state {
            case .began: model.beginHoldBoost()
            case .ended, .cancelled, .failed: model.endHoldBoost()
            default: break
            }
        }

        @objc func handlePinch(_ recognizer: UIPinchGestureRecognizer) {
            switch recognizer.state {
            case .began:
                zoomStart = model.zoom
                model.beginGesture()
            case .changed:
                let zoom = min(max(zoomStart * recognizer.scale, 1), 4)
                model.setZoom(zoom)
            default:
                model.endGesture()
            }
        }

        @objc func handlePan(_ recognizer: UIPanGestureRecognizer) {
            guard let view = recognizer.view else { return }
            let size = view.bounds.size
            switch recognizer.state {
            case .began:
                let start = recognizer.location(in: view)
                // Leave the top and bottom edges to the system's own swipe gestures.
                startsAtEdge = start.y < 24 || start.y > size.height - 24
                mode = .none
                guard !startsAtEdge else { return }
                let velocity = recognizer.velocity(in: view)
                let duration = model.engine.state.durationMillis
                if abs(velocity.x) > abs(velocity.y) {
                    mode = duration > 0 ? .seek : .none
                    seekStart = model.engine.state.positionMillis
                    seekTarget = seekStart
                } else if start.x < size.width / 2 {
                    mode = .brightness
                    level = view.window?.windowScene?.screen.brightness ?? 0.5
                } else {
                    mode = .volume
                    level = CGFloat(AVAudioSession.sharedInstance().outputVolume)
                }
                if mode != .none {
                    model.beginGesture()
                }
                recognizer.setTranslation(.zero, in: view)
            case .changed:
                let delta = recognizer.translation(in: view)
                recognizer.setTranslation(.zero, in: view)
                applyDrag(delta: delta, in: view)
            case .ended, .cancelled, .failed:
                if mode == .seek {
                    model.engine.seek(toMillis: seekTarget)
                }
                if mode != .none {
                    model.endGesture()
                }
                mode = .none
            default:
                break
            }
        }

        private func applyDrag(delta: CGPoint, in view: UIView) {
            let size = view.bounds.size
            let sensitivity: CGFloat = 1.4
            switch mode {
            case .seek:
                let duration = model.engine.state.durationMillis
                // A full-width swipe covers 90 seconds or a third of the video, whichever is longer.
                let span = Double(max(90_000, duration / 3))
                let step = Int64(Double(delta.x / max(size.width, 1)) * span)
                seekTarget = min(max(seekTarget + step, 0), duration)
                model.loadSeekThumbnail(at: seekTarget)
                model.feedback = .seek(target: seekTarget, delta: seekTarget - seekStart, duration: duration)
            case .brightness:
                level = min(max(level - delta.y / max(size.height, 1) * sensitivity, 0.01), 1)
                if let screen = view.window?.windowScene?.screen {
                    model.setBrightness(level, on: screen)
                }
            case .volume:
                level = min(max(level - delta.y / max(size.height, 1) * sensitivity, 0), 1)
                hostView?.setSystemVolume(Float(level))
                model.feedback = .level(
                    icon: level == 0 ? "speaker.slash.fill" : "speaker.wave.2.fill",
                    label: "Volume \(Int((level * 100).rounded()))",
                    fraction: Double(level)
                )
            case .none:
                break
            }
        }
    }
}

/// Hosts the gesture recognisers and an invisible MPVolumeView, which both provides the
/// documented route to the system volume and suppresses the system volume HUD while open.
final class GestureHostView: UIView {
    private let volumeView: MPVolumeView = {
        let view = MPVolumeView(frame: CGRect(x: -1000, y: -1000, width: 1, height: 1))
        view.alpha = 0.01
        view.isUserInteractionEnabled = false
        view.accessibilityElementsHidden = true
        return view
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(volumeView)
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        addSubview(volumeView)
    }

    func setSystemVolume(_ value: Float) {
        guard let slider = volumeView.subviews.compactMap({ $0 as? UISlider }).first else { return }
        slider.value = value
        slider.sendActions(for: .valueChanged)
    }
}
