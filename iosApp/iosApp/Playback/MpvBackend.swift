import Foundation
import Combine
import UIKit
import QuartzCore

/// File types the system player (AVFoundation) cannot open, and which libmpv plays: Matroska, AVI, WebM, FLV, WMV,
/// transport streams, Ogg and the rest. libmpv can also open anything AVFoundation can, so it is the fallback when
/// AVFoundation reports a failure.
enum MpvFormats {
    static let extensions: Set<String> = [
        "mkv", "mka", "mk3d", "webm", "avi", "divx", "flv", "f4v", "wmv", "asf", "wma", "ogv", "ogg", "oga", "opus",
        "ts", "m2ts", "mts", "mpg", "mpeg", "vob", "rm", "rmvb", "ape", "wv", "tta", "mpc", "dsf", "dff", "amr", "awb",
    ]

    static func prefersMpv(_ url: URL) -> Bool {
        extensions.contains(url.pathExtension.lowercased())
    }
}

/// One audio, video or subtitle track inside the file libmpv is playing.
struct MpvTrack: Identifiable, Equatable {
    enum Kind: String { case video, audio, sub }

    let id: Int
    let kind: Kind
    let title: String?
    let language: String?
    let codec: String?
    let isExternal: Bool
    var isSelected: Bool

    /// "English · SubRip", "Track 2": what the picker shows.
    var label: String {
        let parts = [language.flatMap { Locale.current.localizedString(forLanguageCode: $0) ?? $0 }, title, codec?.uppercased()]
            .compactMap { $0 }.filter { !$0.isEmpty }
        return parts.isEmpty ? "Track \(id)" : parts.joined(separator: " · ")
    }
}

#if canImport(VimivoMedia)
import VimivoMedia
import OpenGLES

/// Plays Matroska, AVI and everything else AVFoundation cannot, through the LGPL libmpv/FFmpeg in `VimivoMedia.framework`.
/// One instance serves the whole app: it is created on first use and keeps its decoder context between files.
/// Pictures are drawn by libmpv's OpenGL ES renderer into an `MpvVideoView`; subtitles (SRT, ASS/SSA with styling,
/// PGS, VobSub, WebVTT, …) are rendered by libass inside libmpv, so every format the file contains is shown.
final class MpvBackend: ObservableObject {
    static let shared = MpvBackend()
    static var isAvailable: Bool { true }

    // MARK: Published state (always changed on the main thread)
    @Published private(set) var positionSeconds: Double = 0
    @Published private(set) var durationSeconds: Double = 0
    @Published private(set) var isPaused = true
    @Published private(set) var isBuffering = false
    @Published private(set) var tracks: [MpvTrack] = []
    @Published private(set) var videoSize = CGSize.zero

    /// Called on the main thread when the file ends (`failed` true means it could not be played).
    var onFinished: ((_ failed: Bool, _ message: String?) -> Void)?
    var onLoaded: (() -> Void)?

    private var handle: OpaquePointer?
    private let eventQueue = DispatchQueue(label: "dev.vimivo.mpv.events")
    fileprivate let renderQueue = DispatchQueue(label: "dev.vimivo.mpv.render")
    fileprivate var renderContext: OpaquePointer?
    private var attachedView: MpvVideoView?
    private var pendingStartSeconds: Double = 0
    private var videoEnabled = true

    private init() {}

    // MARK: - Lifecycle

    /// Creates the player on first use. False means libmpv could not start (the caller falls back to an error).
    @discardableResult
    private func ensureStarted() -> Bool {
        if handle != nil { return true }
        guard let mpv = mpv_create() else { return false }
        let options: [(String, String)] = [
            ("config", "no"), ("terminal", "no"), ("idle", "yes"), ("keep-open", "yes"),
            ("vo", "libmpv"), ("ao", "audiounit"), ("hwdec", "videotoolbox"),
            ("audio-display", "no"), ("force-window", "no"),
            // Subtitles: every embedded format, plus sidecar files next to the video, in the right text encoding.
            ("sub-auto", "fuzzy"), ("sub-codepage", "auto"), ("embeddedfonts", "yes"), ("sub-ass", "yes"),
            ("sub-ass-override", "no"), ("blend-subtitles", "no"), ("sub-fix-timing", "yes"),
            ("slang", "en,eng,bn,ben"), ("alang", "en,eng,bn,ben"),
            // A modest buffer: a phone has little memory to spare.
            ("cache", "yes"), ("demuxer-max-bytes", "50MiB"), ("demuxer-max-back-bytes", "20MiB"),
            ("network-timeout", "15"), ("tls-verify", "yes"), ("ytdl", "no"), ("load-scripts", "no"),
        ]
        for (name, value) in options { mpv_set_option_string(mpv, name, value) }
        guard mpv_initialize(mpv) >= 0 else {
            mpv_terminate_destroy(mpv)
            return false
        }
        handle = mpv
        observe("time-pos", MPV_FORMAT_DOUBLE)
        observe("duration", MPV_FORMAT_DOUBLE)
        observe("pause", MPV_FORMAT_FLAG)
        observe("paused-for-cache", MPV_FORMAT_FLAG)
        observe("track-list", MPV_FORMAT_NONE)
        observe("video-params/w", MPV_FORMAT_INT64)
        observe("video-params/h", MPV_FORMAT_INT64)
        let context = Unmanaged.passUnretained(self).toOpaque()
        mpv_set_wakeup_callback(mpv, { raw in
            guard let raw else { return }
            let backend = Unmanaged<MpvBackend>.fromOpaque(raw).takeUnretainedValue()
            backend.eventQueue.async { backend.drainEvents() }
        }, context)
        return true
    }

    private func observe(_ name: String, _ format: mpv_format) {
        mpv_observe_property(handle, 0, name, format)
    }

    // MARK: - Playback control

    /// Opens [url]. [startSeconds] resumes where the viewer left off; [wantsVideo] is false while listening as audio.
    func load(_ url: URL, startSeconds: Double, wantsVideo: Bool) -> Bool {
        guard ensureStarted(), let mpv = handle else { return false }
        pendingStartSeconds = startSeconds
        videoEnabled = wantsVideo
        positionSeconds = max(0, startSeconds)
        durationSeconds = 0
        tracks = []
        isPaused = false
        // Without a picture surface the video output would wait for one, so the picture is off until a view attaches.
        mpv_set_property_string(mpv, "vid", wantsVideo && attachedView != nil ? "auto" : "no")
        if startSeconds > 0 { mpv_set_option_string(mpv, "start", String(format: "%.3f", startSeconds)) }
        else { mpv_set_option_string(mpv, "start", "none") }
        mpv_set_property_string(mpv, "pause", "no")
        let path = url.isFileURL ? url.path : url.absoluteString
        return command(["loadfile", path, "replace"])
    }

    func play() { setFlag("pause", false) }
    func pause() { setFlag("pause", true) }

    func stop() {
        guard handle != nil else { return }
        command(["stop"])
        isPaused = true
    }

    func seek(toSeconds seconds: Double) {
        command(["seek", String(format: "%.3f", max(0, seconds)), "absolute+exact"])
    }

    func setSpeed(_ speed: Double) { setDouble("speed", speed) }

    /// The player's volume as a percentage (100 is the file's own level); the sleep timer fades it out.
    private(set) var volumePercent: Double = 100
    func setVolume(percent: Double) {
        volumePercent = percent
        setDouble("volume", percent)
    }

    func setVideoEnabled(_ enabled: Bool) {
        videoEnabled = enabled
        guard let mpv = handle else { return }
        mpv_set_property_string(mpv, "vid", enabled && attachedView != nil ? "auto" : "no")
    }

    // MARK: - Tracks and subtitles

    func selectAudio(_ id: Int?) { setString("aid", id.map(String.init) ?? "no") }
    func selectSubtitle(_ id: Int?) { setString("sid", id.map(String.init) ?? "no") }
    func selectSecondarySubtitle(_ id: Int?) { setString("secondary-sid", id.map(String.init) ?? "no") }

    /// Adds a subtitle file (SRT, ASS, SSA, VTT, SUB/IDX, SUP, …) and selects it.
    func addSubtitle(file url: URL) { command(["sub-add", url.path, "select"]) }

    func setSubtitleDelay(seconds: Double) { setDouble("sub-delay", seconds) }
    func setSubtitleSpeed(_ factor: Double) { setDouble("sub-speed", factor) }
    func setAudioDelay(seconds: Double) { setDouble("audio-delay", seconds) }
    /// 1.0 is the file's own size; ASS subtitles keep their own styling unless [overrideStyle] is set.
    func setSubtitleScale(_ scale: Double) { setDouble("sub-scale", scale) }
    func setSubtitlePosition(percentFromTop: Double) { setDouble("sub-pos", percentFromTop) }
    func setSubtitleStyle(color: String, borderSize: Double, shadow: Double, overrideStyle: Bool) {
        setString("sub-color", color)
        setDouble("sub-border-size", borderSize)
        setDouble("sub-shadow-offset", shadow)
        setString("sub-ass-override", overrideStyle ? "force" : "no")
    }

    func setAspect(_ mode: MpvAspectMode) {
        guard let mpv = handle else { return }
        mpv_set_property_string(mpv, "keepaspect", mode.keepAspect ? "yes" : "no")
        mpv_set_property_string(mpv, "panscan", mode.panScan)
        mpv_set_property_string(mpv, "video-aspect-override", mode.aspectOverride)
    }

    func setPicture(brightness: Int, contrast: Int, saturation: Int, gamma: Int, hue: Int) {
        setString("brightness", String(brightness)); setString("contrast", String(contrast))
        setString("saturation", String(saturation)); setString("gamma", String(gamma)); setString("hue", String(hue))
    }

    func setRotation(degrees: Int) { setString("video-rotate", String(degrees)) }

    /// Seconds-from-start positions of the file's chapters, if it has any.
    func chapterTimes() -> [Double] {
        let count = Int(getString("chapter-list/count") ?? "0") ?? 0
        return (0..<count).compactMap { getString("chapter-list/\($0)/time").flatMap(Double.init) }
    }

    func screenshot(to url: URL) { command(["screenshot-to-file", url.path, "video"]) }

    // MARK: - Picture surface

    func attach(_ view: MpvVideoView) {
        attachedView = view
        renderQueue.async { [weak self, weak view] in
            guard let self, let view, let mpv = self.handle else { return }
            view.makeCurrentOnRenderQueue()
            self.createRenderContext(mpv: mpv, view: view)
            DispatchQueue.main.async { if self.videoEnabled { self.setVideoEnabled(true) } }
        }
    }

    func detach(_ view: MpvVideoView) {
        guard attachedView === view else { return }
        attachedView = nil
        if let mpv = handle { mpv_set_property_string(mpv, "vid", "no") }
        renderQueue.async { [weak self] in
            self?.destroyRenderContext()
        }
    }

    private func createRenderContext(mpv: OpaquePointer, view: MpvVideoView) {
        destroyRenderContext()
        var apiType = Array(MPV_RENDER_API_TYPE_OPENGL.utf8CString)
        var initParams = mpv_opengl_init_params(
            get_proc_address: { _, name in
                guard let name else { return nil }
                return dlsym(UnsafeMutableRawPointer(bitPattern: -2), name)  // RTLD_DEFAULT
            },
            get_proc_address_ctx: nil
        )
        var advanced: Int32 = 1
        let created: OpaquePointer? = apiType.withUnsafeMutableBufferPointer { api in
            withUnsafeMutablePointer(to: &initParams) { gl in
                withUnsafeMutablePointer(to: &advanced) { adv in
                    var params = [
                        mpv_render_param(type: MPV_RENDER_PARAM_API_TYPE, data: UnsafeMutableRawPointer(api.baseAddress)),
                        mpv_render_param(type: MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, data: UnsafeMutableRawPointer(gl)),
                        mpv_render_param(type: MPV_RENDER_PARAM_ADVANCED_CONTROL, data: UnsafeMutableRawPointer(adv)),
                        mpv_render_param(type: MPV_RENDER_PARAM_INVALID, data: nil),
                    ]
                    var context: OpaquePointer?
                    return mpv_render_context_create(&context, mpv, &params) >= 0 ? context : nil
                }
            }
        }
        renderContext = created
        guard let created else { return }
        let raw = Unmanaged.passUnretained(self).toOpaque()
        mpv_render_context_set_update_callback(created, { raw in
            guard let raw else { return }
            let backend = Unmanaged<MpvBackend>.fromOpaque(raw).takeUnretainedValue()
            backend.renderQueue.async { backend.drawIfNeeded() }
        }, raw)
    }

    private func destroyRenderContext() {
        if let context = renderContext {
            mpv_render_context_set_update_callback(context, nil, nil)
            mpv_render_context_free(context)
            renderContext = nil
        }
    }

    /// Runs on the render queue: draws a new frame when libmpv has one.
    fileprivate func drawIfNeeded() {
        guard let context = renderContext, let view = attachedView else { return }
        let flags = mpv_render_context_update(context)
        guard flags & UInt64(MPV_RENDER_UPDATE_FRAME.rawValue) != 0 else { return }
        view.draw(with: context)
    }

    /// Called when the view changed size: draws the current frame again at the new size.
    fileprivate func redraw() {
        renderQueue.async { [weak self] in self?.drawIfNeeded() }
    }

    // MARK: - Events

    private func drainEvents() {
        guard let mpv = handle else { return }
        while true {
            guard let event = mpv_wait_event(mpv, 0)?.pointee, event.event_id != MPV_EVENT_NONE else { return }
            switch event.event_id {
            case MPV_EVENT_PROPERTY_CHANGE: handleProperty(event)
            case MPV_EVENT_FILE_LOADED:
                DispatchQueue.main.async { [weak self] in
                    guard let self else { return }
                    if self.pendingStartSeconds > 0 { self.seek(toSeconds: self.pendingStartSeconds); self.pendingStartSeconds = 0 }
                    self.refreshTracks()
                    self.onLoaded?()
                }
            case MPV_EVENT_END_FILE:
                let end = event.data?.assumingMemoryBound(to: mpv_event_end_file.self).pointee
                let reason = end?.reason
                let error = end?.error ?? 0
                if reason == MPV_END_FILE_REASON_EOF || reason == MPV_END_FILE_REASON_ERROR {
                    let failed = reason == MPV_END_FILE_REASON_ERROR
                    let message = failed ? String(cString: mpv_error_string(error)) : nil
                    DispatchQueue.main.async { [weak self] in self?.onFinished?(failed, message) }
                }
            case MPV_EVENT_SHUTDOWN: return
            default: break
            }
        }
    }

    private func handleProperty(_ event: mpv_event) {
        guard let data = event.data?.assumingMemoryBound(to: mpv_event_property.self).pointee else { return }
        let name = String(cString: data.name)
        switch name {
        case "time-pos":
            if data.format == MPV_FORMAT_DOUBLE, let p = data.data?.assumingMemoryBound(to: Double.self).pointee {
                DispatchQueue.main.async { [weak self] in self?.positionSeconds = p }
            }
        case "duration":
            if data.format == MPV_FORMAT_DOUBLE, let d = data.data?.assumingMemoryBound(to: Double.self).pointee {
                DispatchQueue.main.async { [weak self] in self?.durationSeconds = d }
            }
        case "pause":
            if data.format == MPV_FORMAT_FLAG, let v = data.data?.assumingMemoryBound(to: Int32.self).pointee {
                DispatchQueue.main.async { [weak self] in self?.isPaused = v != 0 }
            }
        case "paused-for-cache":
            if data.format == MPV_FORMAT_FLAG, let v = data.data?.assumingMemoryBound(to: Int32.self).pointee {
                DispatchQueue.main.async { [weak self] in self?.isBuffering = v != 0 }
            }
        case "track-list":
            DispatchQueue.main.async { [weak self] in self?.refreshTracks() }
        case "video-params/w", "video-params/h":
            let w = Double(getString("video-params/w") ?? "0") ?? 0
            let h = Double(getString("video-params/h") ?? "0") ?? 0
            DispatchQueue.main.async { [weak self] in self?.videoSize = CGSize(width: w, height: h) }
        default: break
        }
    }

    private func refreshTracks() {
        let count = Int(getString("track-list/count") ?? "0") ?? 0
        var found: [MpvTrack] = []
        for index in 0..<count {
            let base = "track-list/\(index)"
            guard let type = getString("\(base)/type"), let kind = MpvTrack.Kind(rawValue: type),
                  let id = getString("\(base)/id").flatMap(Int.init) else { continue }
            if kind == .video, getString("\(base)/albumart") == "yes" { continue }
            found.append(MpvTrack(
                id: id, kind: kind, title: getString("\(base)/title"), language: getString("\(base)/lang"),
                codec: getString("\(base)/codec"), isExternal: getString("\(base)/external") == "yes",
                isSelected: getString("\(base)/selected") == "yes"
            ))
        }
        tracks = found
    }

    // MARK: - Property helpers

    @discardableResult
    private func command(_ arguments: [String]) -> Bool {
        guard let mpv = handle else { return false }
        var pointers: [UnsafeMutablePointer<CChar>?] = arguments.map { strdup($0) }
        pointers.append(nil)
        defer { pointers.forEach { free($0) } }
        return pointers.withUnsafeMutableBufferPointer { buffer in
            buffer.baseAddress!.withMemoryRebound(to: UnsafePointer<CChar>?.self, capacity: buffer.count) {
                mpv_command(mpv, $0) >= 0
            }
        }
    }

    private func setString(_ name: String, _ value: String) {
        guard let mpv = handle else { return }
        mpv_set_property_string(mpv, name, value)
    }

    private func setDouble(_ name: String, _ value: Double) { setString(name, String(value)) }
    private func setFlag(_ name: String, _ value: Bool) { setString(name, value ? "yes" : "no") }

    private func getString(_ name: String) -> String? {
        guard let mpv = handle, let raw = mpv_get_property_string(mpv, name) else { return nil }
        defer { mpv_free(raw) }
        return String(cString: raw)
    }
}

/// How libmpv should fit the picture into the view.
struct MpvAspectMode {
    let keepAspect: Bool
    let panScan: String
    let aspectOverride: String

    static let fit = MpvAspectMode(keepAspect: true, panScan: "0", aspectOverride: "-1")
    static let stretch = MpvAspectMode(keepAspect: false, panScan: "0", aspectOverride: "-1")
    static let crop = MpvAspectMode(keepAspect: true, panScan: "1", aspectOverride: "-1")
    static let sixteenNine = MpvAspectMode(keepAspect: true, panScan: "0", aspectOverride: "16:9")
    static let fourThree = MpvAspectMode(keepAspect: true, panScan: "0", aspectOverride: "4:3")
}

/// The OpenGL ES surface libmpv draws into. Created by SwiftUI through `MpvVideoSurface`.
final class MpvVideoView: UIView {
    override class var layerClass: AnyClass { CAEAGLLayer.self }

    private var glContext: EAGLContext?
    private var framebuffer: GLuint = 0
    private var renderbuffer: GLuint = 0
    private var bufferSize = CGSize.zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = true
        backgroundColor = .black
        contentScaleFactor = UIScreen.main.scale
        let eagl = layer as! CAEAGLLayer
        eagl.isOpaque = true
        eagl.drawableProperties = [kEAGLDrawablePropertyRetainedBacking: false, kEAGLDrawablePropertyColorFormat: kEAGLColorFormatRGBA8]
        glContext = EAGLContext(api: .openGLES3) ?? EAGLContext(api: .openGLES2)
    }

    @available(*, unavailable) required init?(coder: NSCoder) { fatalError("not used") }

    deinit {
        if let ctx = glContext {
            EAGLContext.setCurrent(ctx)
            if framebuffer != 0 { glDeleteFramebuffers(1, &framebuffer) }
            if renderbuffer != 0 { glDeleteRenderbuffers(1, &renderbuffer) }
            EAGLContext.setCurrent(nil)
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        MpvBackend.shared.renderQueue.async { [weak self] in
            self?.resizeBuffers()
            MpvBackend.shared.redrawFromView()
        }
    }

    func makeCurrentOnRenderQueue() {
        if let ctx = glContext { EAGLContext.setCurrent(ctx) }
        resizeBuffers()
    }

    private func resizeBuffers() {
        guard let ctx = glContext, let eagl = layer as? CAEAGLLayer else { return }
        EAGLContext.setCurrent(ctx)
        if framebuffer == 0 { glGenFramebuffers(1, &framebuffer) }
        if renderbuffer == 0 { glGenRenderbuffers(1, &renderbuffer) }
        glBindFramebuffer(GLenum(GL_FRAMEBUFFER), framebuffer)
        glBindRenderbuffer(GLenum(GL_RENDERBUFFER), renderbuffer)
        ctx.renderbufferStorage(Int(GL_RENDERBUFFER), from: eagl)
        glFramebufferRenderbuffer(GLenum(GL_FRAMEBUFFER), GLenum(GL_COLOR_ATTACHMENT0), GLenum(GL_RENDERBUFFER), renderbuffer)
        var width: GLint = 0, height: GLint = 0
        glGetRenderbufferParameteriv(GLenum(GL_RENDERBUFFER), GLenum(GL_RENDERBUFFER_WIDTH), &width)
        glGetRenderbufferParameteriv(GLenum(GL_RENDERBUFFER), GLenum(GL_RENDERBUFFER_HEIGHT), &height)
        bufferSize = CGSize(width: Int(width), height: Int(height))
    }

    /// Runs on the render queue.
    func draw(with context: OpaquePointer) {
        guard let ctx = glContext, bufferSize.width > 0 else { return }
        EAGLContext.setCurrent(ctx)
        var fbo = mpv_opengl_fbo(fbo: Int32(framebuffer), w: Int32(bufferSize.width), h: Int32(bufferSize.height), internal_format: 0)
        var flip: Int32 = 1
        withUnsafeMutablePointer(to: &fbo) { fboPointer in
            withUnsafeMutablePointer(to: &flip) { flipPointer in
                var params = [
                    mpv_render_param(type: MPV_RENDER_PARAM_OPENGL_FBO, data: UnsafeMutableRawPointer(fboPointer)),
                    mpv_render_param(type: MPV_RENDER_PARAM_FLIP_Y, data: UnsafeMutableRawPointer(flipPointer)),
                    mpv_render_param(type: MPV_RENDER_PARAM_INVALID, data: nil),
                ]
                mpv_render_context_render(context, &params)
            }
        }
        glBindRenderbuffer(GLenum(GL_RENDERBUFFER), renderbuffer)
        ctx.presentRenderbuffer(Int(GL_RENDERBUFFER))
        mpv_render_context_report_swap(context)
    }
}

extension MpvBackend {
    fileprivate func redrawFromView() { redraw() }
}
#else
/// Without `VimivoMedia.framework` (see scripts/build_media) the app still builds and AVFoundation plays what it can.
final class MpvBackend: ObservableObject {
    static let shared = MpvBackend()
    static var isAvailable: Bool { false }
    @Published private(set) var positionSeconds: Double = 0
    @Published private(set) var durationSeconds: Double = 0
    @Published private(set) var isPaused = true
    @Published private(set) var isBuffering = false
    @Published private(set) var tracks: [MpvTrack] = []
    @Published private(set) var videoSize = CGSize.zero
    var onFinished: ((_ failed: Bool, _ message: String?) -> Void)?
    var onLoaded: (() -> Void)?
    func load(_ url: URL, startSeconds: Double, wantsVideo: Bool) -> Bool { false }
    func play() {}
    func pause() {}
    func stop() {}
    func seek(toSeconds seconds: Double) {}
    func setSpeed(_ speed: Double) {}
    private(set) var volumePercent: Double = 100
    func setVolume(percent: Double) { volumePercent = percent }
    func setVideoEnabled(_ enabled: Bool) {}
    func selectAudio(_ id: Int?) {}
    func selectSubtitle(_ id: Int?) {}
    func selectSecondarySubtitle(_ id: Int?) {}
    func addSubtitle(file url: URL) {}
    func setSubtitleDelay(seconds: Double) {}
    func setSubtitleSpeed(_ factor: Double) {}
    func setAudioDelay(seconds: Double) {}
    func setSubtitleScale(_ scale: Double) {}
    func setSubtitlePosition(percentFromTop: Double) {}
    func setSubtitleStyle(color: String, borderSize: Double, shadow: Double, overrideStyle: Bool) {}
    func setAspect(_ mode: MpvAspectMode) {}
    func setPicture(brightness: Int, contrast: Int, saturation: Int, gamma: Int, hue: Int) {}
    func setRotation(degrees: Int) {}
    func chapterTimes() -> [Double] { [] }
    func screenshot(to url: URL) {}
    func attach(_ view: MpvVideoView) {}
    func detach(_ view: MpvVideoView) {}
}

struct MpvAspectMode {
    static let fit = MpvAspectMode()
    static let stretch = MpvAspectMode()
    static let crop = MpvAspectMode()
    static let sixteenNine = MpvAspectMode()
    static let fourThree = MpvAspectMode()
}

final class MpvVideoView: UIView {}
#endif

/// Shows libmpv's picture inside SwiftUI.
import SwiftUI

struct MpvVideoSurface: UIViewRepresentable {
    let backend: MpvBackend

    func makeUIView(context: Context) -> MpvVideoView {
        let view = MpvVideoView(frame: .zero)
        backend.attach(view)
        return view
    }

    func updateUIView(_ uiView: MpvVideoView, context: Context) {}

    static func dismantleUIView(_ uiView: MpvVideoView, coordinator: ()) {
        MpvBackend.shared.detach(uiView)
    }
}
