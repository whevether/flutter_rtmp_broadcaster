// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.
//
//  Generated file. Do not edit.
//

import PackageDescription

let package = Package(
    name: "FlutterGeneratedPluginSwiftPackage",
    platforms: [
        .iOS("12.0")
    ],
    products: [
        .library(name: "FlutterGeneratedPluginSwiftPackage", type: .static, targets: ["FlutterGeneratedPluginSwiftPackage"])
    ],
    dependencies: [
        .package(name: "package_info_plus", path: "/Users/user/.pub-cache/hosted/pub.dev/package_info_plus-8.3.0/ios/package_info_plus"),
        .package(name: "path_provider_foundation", path: "/Users/user/.pub-cache/hosted/pub.dev/path_provider_foundation-2.4.1/darwin/path_provider_foundation"),
        .package(name: "wakelock_plus", path: "/Users/user/.pub-cache/hosted/pub.dev/wakelock_plus-1.3.2/ios/wakelock_plus")
    ],
    targets: [
        .target(
            name: "FlutterGeneratedPluginSwiftPackage",
            dependencies: [
                .product(name: "package-info-plus", package: "package_info_plus"),
                .product(name: "path-provider-foundation", package: "path_provider_foundation"),
                .product(name: "wakelock-plus", package: "wakelock_plus")
            ]
        )
    ]
)
