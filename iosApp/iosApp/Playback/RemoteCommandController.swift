import Foundation
import MediaPlayer

public protocol RemoteCommandDelegate: AnyObject {
    func remotePlay()
    func remotePause()
    func remoteTogglePlayPause()
    func remoteNext()
    func remotePrevious()
    func remoteSeek(to positionSeconds: Double)
}

public final class RemoteCommandController {
    public static let shared = RemoteCommandController()
    public weak var delegate: RemoteCommandDelegate?
    private var isConfigured = false

    public func configureCommands() {
        guard !isConfigured else { return }
        let center = MPRemoteCommandCenter.shared()

        center.playCommand.isEnabled = true
        center.playCommand.addTarget { [weak self] _ in
            self?.delegate?.remotePlay()
            return .success
        }

        center.pauseCommand.isEnabled = true
        center.pauseCommand.addTarget { [weak self] _ in
            self?.delegate?.remotePause()
            return .success
        }

        center.togglePlayPauseCommand.isEnabled = true
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.delegate?.remoteTogglePlayPause()
            return .success
        }

        center.nextTrackCommand.isEnabled = true
        center.nextTrackCommand.addTarget { [weak self] _ in
            self?.delegate?.remoteNext()
            return .success
        }

        center.previousTrackCommand.isEnabled = true
        center.previousTrackCommand.addTarget { [weak self] _ in
            self?.delegate?.remotePrevious()
            return .success
        }

        center.changePlaybackPositionCommand.isEnabled = true
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let posEvent = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.delegate?.remoteSeek(to: posEvent.positionTime)
            return .success
        }

        isConfigured = true
    }
}
