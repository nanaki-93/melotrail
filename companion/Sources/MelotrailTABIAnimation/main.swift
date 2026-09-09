import Darwin
import Foundation
import MelotrailTABICompanion

func usage() -> Never {
    fputs("Usage:\n  melotrail-tabi-animation import-manual-clip <library-root> <request.json>\n  melotrail-tabi-animation plan-timing <request.json>\n  melotrail-tabi-animation plan-scenes <request.json>\n  melotrail-tabi-animation preview-frame <request.json> <frame>\n", stderr)
    exit(64)
}

private struct PreviewFrameRequest: Decodable {
    let composition: SceneCompositionRequest
    let outputWidth: Int
    let outputHeight: Int
}

private struct PreviewFrameResult: Encodable {
    let frame: Int64
    let soundtrackTimeSeconds: Double
    let sceneIDs: [String]
    let width: Int
    let height: Int
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
    if arguments.count == 3, arguments[0] == "preview-frame", let frame = Int64(arguments[2]) {
        let request = try decoder.decode(PreviewFrameRequest.self, from: Data(contentsOf: URL(fileURLWithPath: arguments[1])))
        let composition = try request.composition.resolve()
        let library = try AssetLibrary(
            manifestURL: URL(fileURLWithPath: request.composition.assetManifestPath),
            libraryRoot: URL(fileURLWithPath: request.composition.assetLibraryPath)
        )
        let soundtrack = try FinishedSoundtrack.open(
            url: URL(fileURLWithPath: request.composition.timing.soundtrackPath),
            expectedSHA256: request.composition.timing.soundtrackSHA256
        )
        let stage = try ScenePreviewStage(
            composition: composition,
            library: library,
            soundtrack: soundtrack,
            geometry: try PreviewOutputGeometry(width: request.outputWidth, height: request.outputHeight)
        )
        let seek = DispatchSemaphore(value: 0)
        var seekCompleted = false
        stage.seek(toFrame: frame) { completed in
            seekCompleted = completed
            seek.signal()
        }
        guard seek.wait(timeout: .now() + 2) == .success, seekCompleted else {
            throw ScenePreviewError.invalidSoundtrack("the finished-soundtrack player could not seek to preview frame \(frame)")
        }
        let preview = try stage.currentFrame()
        FileHandle.standardOutput.write(try encoder.encode(PreviewFrameResult(
            frame: preview.frame,
            soundtrackTimeSeconds: preview.soundtrackTime.seconds,
            sceneIDs: preview.sceneIDs,
            width: preview.image.width,
            height: preview.image.height
        )))
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
