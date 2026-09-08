import Foundation
import MelotrailTABICompanion

guard CommandLine.arguments.count == 1 else {
    fputs("This owned-media spike accepts no paths or input assets.\n", stderr)
    exit(1)
}

do {
    let probe = try OwnedMediaSpike.run()
    print("owned-media-spike=PASS")
    print("frames=\(probe.firstFrameSeconds)/\(probe.previewSeekSeconds)/\(probe.lastFrameSeconds) audio=\(probe.audioCodecFourCC) samples=\(probe.decodedAudioSamples) byte-preserved=true")
    print("output=\(probe.outputURL.path)")
    print("duration=\(String(format: "%.3f", probe.durationSeconds))s video=\(String(format: "%.3f", probe.videoDurationSeconds))s audio=\(String(format: "%.3f", probe.audioDurationSeconds))s drift=\(String(format: "%.3f", probe.audioVideoDriftSeconds))s")
    print("video=\(probe.width)x\(probe.height) codec=\(probe.videoCodecFourCC) preview=\(probe.firstPreviewWidth)/\(probe.lastPreviewWidth) seek=\(String(format: "%.3f", probe.previewSeekSeconds))s")
} catch {
    fputs("owned-media-spike=FAIL: \(error.localizedDescription)\n", stderr)
    exit(1)
}
