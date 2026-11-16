import Foundation

public struct ThirdPartyComponent: Identifiable {
    public var id: String { name }
    public let name: String
    public let version: String
    public let license: String
    public let sourceUrl: String
    public let isLgpl: Bool

    public init(name: String, version: String, license: String, sourceUrl: String, isLgpl: Bool = false) {
        self.name = name
        self.version = version
        self.license = license
        self.sourceUrl = sourceUrl
        self.isLgpl = isLgpl
    }
}

public enum NoticePlatform {
    case ios
    case macos
    case android
    case windows
    case linux
}

public final class ThirdPartyNotices {
    public static let shared = ThirdPartyNotices()
    public let SOURCE_REPOSITORY = "https://github.com/Vimivo/Player"

    public func lgplNotice(platform: NoticePlatform) -> String? {
        return nil
    }

    public func forPlatform(platform: NoticePlatform) -> [ThirdPartyComponent] {
        return [
            ThirdPartyComponent(name: "Rust standard library", version: "1.82+", license: "MIT / Apache-2.0", sourceUrl: "https://github.com/rust-lang/rust"),
            ThirdPartyComponent(name: "UniFFI", version: "0.28", license: "MPL-2.0", sourceUrl: "https://github.com/mozilla/uniffi-rs"),
            ThirdPartyComponent(name: "rusqlite", version: "0.32", license: "MIT", sourceUrl: "https://github.com/rusqlite/rusqlite"),
            ThirdPartyComponent(name: "SQLite3", version: "3.46", license: "Public Domain", sourceUrl: "https://sqlite.org"),
            ThirdPartyComponent(name: "Lofty", version: "0.21", license: "MIT / Apache-2.0", sourceUrl: "https://github.com/Serial-ATA/lofty-rs"),
        ]
    }
}
