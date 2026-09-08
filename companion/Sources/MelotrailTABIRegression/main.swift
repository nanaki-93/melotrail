import CoreMedia
import Foundation
import MelotrailTABICompanion

func require(_ condition: @autoclosure () -> Bool, _ message: String) {
    guard condition() else {
        fputs("regression=FAIL: \(message)\n", stderr)
        exit(1)
    }
}

let synchronizer = PreviewSynchronizer(
    videoDuration: CMTime(seconds: 1.0, preferredTimescale: 600),
    soundtrackDuration: CMTime(seconds: 0.9, preferredTimescale: 600),
    frameRate: 30
)
let beforeStart = synchronizer.position(forSoundtrackTime: CMTime(seconds: -0.1, preferredTimescale: 600))
let afterEnd = synchronizer.position(forSoundtrackTime: CMTime(seconds: 2.0, preferredTimescale: 600))
require(beforeStart == .zero, "preview seeks before zero must clamp to zero")
require(abs(afterEnd.seconds - 0.9) < 0.001, "preview seeks after the soundtrack must clamp to the shared extent")
require(synchronizer.isSynchronized(videoTime: afterEnd, soundtrackTime: CMTime(seconds: 2.0, preferredTimescale: 600)), "clamped preview position must be synchronized")
require(!synchronizer.isSynchronized(videoTime: .zero, soundtrackTime: CMTime(seconds: 0.5, preferredTimescale: 600)), "a half-second offset must not be synchronized")

var directories: [URL] = []
defer { for directory in directories { try? FileManager.default.removeItem(at: directory) } }

do {
    let probe = try OwnedMediaSpike.run()
    directories.append(probe.outputURL.deletingLastPathComponent())
    require(FileManager.default.fileExists(atPath: probe.outputURL.path), "the real caller must publish a MOV")
    require(abs(probe.previewSeekSeconds - 0.5) <= 1.0 / Double(OwnedMediaSpike.frameRate), "a shared half-second preview seek must remain aligned")
    let original = try Data(contentsOf: probe.outputURL)
    let repeated = try OwnedMediaSpike.run()
    directories.append(repeated.outputURL.deletingLastPathComponent())
    require(repeated.outputURL != probe.outputURL, "repeated runs must allocate independent outputs")
    let retained = try Data(contentsOf: probe.outputURL)
    require(original == retained, "retry must preserve the original output")
    require(probe.decodedAudioSamples == 44_100, "the complete owned soundtrack must survive the mux")
    require(abs(probe.lastFrameSeconds - 29.0 / 30.0) < 0.0001, "the actual final frame must decode")
    print("regression=PASS")
} catch {
    fputs("regression=FAIL: \(error.localizedDescription)\n", stderr)
    exit(1)
}
