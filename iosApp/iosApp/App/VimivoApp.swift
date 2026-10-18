import SwiftUI
import UIKit

@main
public struct VimivoApp: App {
    @UIApplicationDelegateAdaptor(VimivoAppDelegate.self) private var appDelegate
    @StateObject private var engine = AVPlayerPlaybackEngine.shared
    @AppStorage("vimivo_theme") private var theme: String = "system"

    public init() {}

    public var body: some Scene {
        WindowGroup {
            MainTabView(engine: engine)
                .preferredColorScheme(colorScheme)
                .tint(Color.accentColor)
        }
    }

    private var colorScheme: ColorScheme? {
        switch theme {
        case "dark", "amoled": return .dark
        case "light": return .light
        default: return nil
        }
    }
}

final class VimivoAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        OrientationController.shared.supportedOrientations
    }
}

/// Owns the app-wide orientation mask so the video player can lock rotation.
final class OrientationController {
    static let shared = OrientationController()

    private(set) var supportedOrientations: UIInterfaceOrientationMask = .allButUpsideDown

    /// Locks to the orientation the given scene currently shows, or unlocks when `scene` is nil.
    func lock(to scene: UIWindowScene?) {
        if let scene = scene {
            supportedOrientations = scene.interfaceOrientation.isLandscape ? .landscape : .portrait
        } else {
            supportedOrientations = .allButUpsideDown
        }
        let targetScene = scene ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var controller = targetScene?.keyWindow?.rootViewController
        while let current = controller {
            current.setNeedsUpdateOfSupportedInterfaceOrientations()
            controller = current.presentedViewController
        }
        targetScene?.requestGeometryUpdate(.iOS(interfaceOrientations: supportedOrientations)) { error in
            NSLog("Vimivo: orientation update rejected: %@", error.localizedDescription)
        }
    }
}
