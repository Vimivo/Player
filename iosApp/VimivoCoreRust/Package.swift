// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "VimivoCoreRust",
    platforms: [
        .iOS(.v16),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "VimivoCoreRust",
            targets: ["VimivoCoreRust"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "VimivoCoreRustFFI",
            path: "../Frameworks/VimivoCoreRustFFI.xcframework"
        ),
        .target(
            name: "VimivoCoreRust",
            dependencies: ["VimivoCoreRustFFI"],
            path: ".",
            sources: ["vimivo_ffi.swift"],
            publicHeadersPath: "."
        ),
    ]
)
