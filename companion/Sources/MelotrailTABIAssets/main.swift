import Darwin
import Foundation
import MelotrailTABICompanion

func usage() -> Never {
    fputs("Usage:\n  melotrail-tabi-assets import <library-root> <request.json>\n  melotrail-tabi-assets inspect <library-root> <manifest.json> <scene-version> <identity-version> <asset-id@version>...\n", stderr)
    exit(64)
}

func pin(_ value: String) -> AssetIdentity? {
    guard let separator = value.lastIndex(of: "@") else { return nil }
    let id = String(value[..<separator])
    let version = String(value[value.index(after: separator)...])
    guard !id.isEmpty, !version.isEmpty else { return nil }
    return AssetIdentity(assetID: id, version: version)
}

let arguments = Array(CommandLine.arguments.dropFirst())
let decoder = JSONDecoder()
decoder.dateDecodingStrategy = .iso8601
let encoder = JSONEncoder()
encoder.dateEncodingStrategy = .iso8601
encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]

do {
    guard let command = arguments.first else { usage() }
    switch command {
    case "import":
        guard arguments.count == 3 else { usage() }
        let root = URL(fileURLWithPath: arguments[1])
        let requestURL = URL(fileURLWithPath: arguments[2])
        let request = try decoder.decode(AssetImportRequest.self, from: Data(contentsOf: requestURL))
        let imported = try AssetKitImporter.importOriginal(request, into: root)
        FileHandle.standardOutput.write(try encoder.encode(imported.record))
        FileHandle.standardOutput.write(Data("\n".utf8))
    case "inspect":
        guard arguments.count >= 6 else { usage() }
        let root = URL(fileURLWithPath: arguments[1])
        let manifest = URL(fileURLWithPath: arguments[2])
        let pins = arguments.dropFirst(5).compactMap(pin)
        guard pins.count == arguments.count - 5 else { usage() }
        let library = try AssetLibrary(manifestURL: manifest, libraryRoot: root)
        let report = AssetKitInspector.inspect(
            library,
            request: AssetKitSceneRequest(sceneVersion: arguments[3], identityVersion: arguments[4], assetPins: pins)
        )
        FileHandle.standardOutput.write(try encoder.encode(report))
        FileHandle.standardOutput.write(Data("\n".utf8))
        if !report.isCompositionReady { exit(2) }
    default:
        usage()
    }
} catch {
    fputs("melotrail-tabi-assets: \(error.localizedDescription)\n", stderr)
    exit(1)
}
