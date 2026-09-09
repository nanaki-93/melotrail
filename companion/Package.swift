// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "MelotrailTABICompanion",
    platforms: [.macOS(.v14)],
    products: [
        .library(name: "MelotrailTABICompanion", targets: ["MelotrailTABICompanion"]),
        .executable(name: "melotrail-tabi-spike", targets: ["MelotrailTABISpike"]),
        .executable(name: "melotrail-tabi-regression", targets: ["MelotrailTABIRegression"]),
        .executable(name: "melotrail-tabi-assets", targets: ["MelotrailTABIAssets"]),
        .executable(name: "melotrail-tabi-animation", targets: ["MelotrailTABIAnimation"]),
    ],
    targets: [
        .target(name: "MelotrailTABICompanion"),
        .executableTarget(name: "MelotrailTABISpike", dependencies: ["MelotrailTABICompanion"]),
        .executableTarget(name: "MelotrailTABIRegression", dependencies: ["MelotrailTABICompanion"]),
        .executableTarget(name: "MelotrailTABIAssets", dependencies: ["MelotrailTABICompanion"]),
        .executableTarget(name: "MelotrailTABIAnimation", dependencies: ["MelotrailTABICompanion"]),
    ]
)
