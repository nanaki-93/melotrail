import Darwin
import Foundation
import MelotrailTABICompanion

func usage() -> Never {
    fputs("Usage:\n  melotrail-tabi-animation import-manual-clip <library-root> <request.json>\n  melotrail-tabi-animation plan-timing <request.json>\n  melotrail-tabi-animation plan-scenes <request.json>\n", stderr)
    exit(64)
}

let arguments = Array(CommandLine.arguments.dropFirst())
let decoder = JSONDecoder()
decoder.dateDecodingStrategy = .iso8601
let encoder = JSONEncoder()
encoder.dateEncodingStrategy = .iso8601
encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]

do {
    if arguments.count == 2, arguments[0] == "plan-timing" {
        let request = try decoder.decode(SoundtrackTimingRequest.self, from: Data(contentsOf: URL(fileURLWithPath: arguments[1])))
        FileHandle.standardOutput.write(try encoder.encode(request.resolve()))
        FileHandle.standardOutput.write(Data("\n".utf8))
        exit(0)
    }
    if arguments.count == 2, arguments[0] == "plan-scenes" {
        let request = try decoder.decode(SceneCompositionRequest.self, from: Data(contentsOf: URL(fileURLWithPath: arguments[1])))
        FileHandle.standardOutput.write(try encoder.encode(request.resolve()))
        FileHandle.standardOutput.write(Data("\n".utf8))
        exit(0)
    }
    guard arguments.count == 3, arguments[0] == "import-manual-clip" else { usage() }
    let request = try decoder.decode(ManualAnimationClipImportRequest.self, from: Data(contentsOf: URL(fileURLWithPath: arguments[2])))
    let result = try ManualAnimationClipImporter.importOwned(request, into: URL(fileURLWithPath: arguments[1]))
    FileHandle.standardOutput.write(try encoder.encode(result.record))
    FileHandle.standardOutput.write(Data("\n".utf8))
} catch {
    fputs("melotrail-tabi-animation: \(error.localizedDescription)\n", stderr)
    exit(1)
}
