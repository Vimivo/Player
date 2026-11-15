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
        .target(
            name: "VimivoCoreRust",
            path: ".",
            sources: ["vimivo_ffi.swift"],
            publicHeadersPath: "."
        ),
    ]
)
