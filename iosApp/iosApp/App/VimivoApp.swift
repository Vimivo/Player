import SwiftUI

@main
public struct VimivoApp: App {
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
