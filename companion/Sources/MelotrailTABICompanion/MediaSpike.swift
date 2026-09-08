import AVFoundation
import CoreMedia
import CoreVideo
import Foundation
import ImageIO
import UniformTypeIdentifiers

public enum MediaSpikeError: Error, LocalizedError {
    case writer(String)
    case export(String)
    case invalidOutput(String)

    public var errorDescription: String? {
        switch self {
        case .writer(let message), .export(let message), .invalidOutput(let message): message
        }
    }
}

public struct MediaSpikeProbe: Sendable {
    public let outputURL: URL
    public let durationSeconds: Double
    public let videoDurationSeconds: Double
    public let audioDurationSeconds: Double
    public let width: Int
    public let height: Int
    public let videoCodecFourCC: String
    public let firstPreviewWidth: Int
    public let lastPreviewWidth: Int
    public let previewSeekSeconds: Double
    public let firstFrameSeconds: Double
    public let lastFrameSeconds: Double
    public let decodedAudioSamples: Int
    public let audioCodecFourCC: String

    public var audioVideoDriftSeconds: Double { abs(videoDurationSeconds - audioDurationSeconds) }
}

/**
 * Bounded, owned-media proof for the separately built companion.
 *
 * The fixture is synthesized in memory, is not MIDI, and is never written into
 * the Melotrail project tree. AVFoundation encodes a short ProRes 422 video,
 * muxes a generated PCM soundtrack into QuickTime MOV, then decodes first/last preview frames.
 */
public enum OwnedMediaSpike {
    public static let width = 320
    public static let height = 180
    public static let frameRate: Int32 = 30
    public static let frameCount = 30
    public static let expectedDurationSeconds = Double(frameCount) / Double(frameRate)
    private static let localCodec = AVVideoCodecType(rawValue: "apcn")
    private static let expectedCodecFourCC = "apcn"

    public static func run() throws -> MediaSpikeProbe {
        let outputDirectory = FileManager.default.temporaryDirectory
            .appending(path: "melotrail-tabi-spike-\(UUID().uuidString)")
        // Claim a fresh destination atomically; never overwrite or follow an existing directory.
        try FileManager.default.createDirectory(at: outputDirectory, withIntermediateDirectories: false)
        let videoURL = outputDirectory.appending(path: "owned-spike-video.mov")
        let audioURL = outputDirectory.appending(path: "owned-spike-soundtrack.wav")
        let outputURL = outputDirectory.appending(path: "owned-spike-output.mov")


        try writeVideo(to: videoURL)
        try writeWave(to: audioURL)
        try mux(videoURL: videoURL, audioURL: audioURL, outputURL: outputURL)
        let probe = try inspect(outputURL: outputURL)
        guard probe.width == width, probe.height == height, probe.videoCodecFourCC == expectedCodecFourCC,
              probe.durationSeconds > 0.90, probe.durationSeconds < 1.10,
              probe.audioVideoDriftSeconds <= 1.0 / Double(frameRate),
              probe.firstPreviewWidth == width, probe.lastPreviewWidth == width else {
            throw MediaSpikeError.invalidOutput("The local MOV did not satisfy the bounded encode/decode/preview contract.")
        }
        return probe
    }

    private static func writeVideo(to url: URL) throws {
        let writer: AVAssetWriter
        do {
            writer = try AVAssetWriter(outputURL: url, fileType: .mov)
        } catch {
            throw MediaSpikeError.writer("Cannot create the local video writer: \(error.localizedDescription)")
        }
        let input = AVAssetWriterInput(
            mediaType: .video,
            outputSettings: [
                AVVideoCodecKey: localCodec,
                AVVideoWidthKey: width,
                AVVideoHeightKey: height,
            ]
        )
        input.expectsMediaDataInRealTime = false
        guard writer.canAdd(input) else { throw MediaSpikeError.writer("The local ProRes writer rejected its video input.") }
        writer.add(input)
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: input,
            sourcePixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                kCVPixelBufferWidthKey as String: width,
                kCVPixelBufferHeightKey as String: height,
            ]
        )

        guard writer.startWriting() else {
            throw MediaSpikeError.writer("The local ProRes writer did not start: \(writer.error?.localizedDescription ?? "unknown error")")
        }
        writer.startSession(atSourceTime: .zero)
        for frame in 0..<frameCount {
            let deadline = Date().addingTimeInterval(30)
            while !input.isReadyForMoreMediaData {
                guard writer.status == .writing, Date() < deadline else {
                    writer.cancelWriting()
                    throw MediaSpikeError.writer("Timed out or failed waiting for frame input.")
                }
                RunLoop.current.run(until: Date().addingTimeInterval(0.001))
            }
            guard let buffer = makeFrame(frame: frame) else { throw MediaSpikeError.writer("Cannot allocate a local preview frame.") }
            let time = CMTime(value: Int64(frame), timescale: frameRate)
            guard adaptor.append(buffer, withPresentationTime: time) else {
                throw MediaSpikeError.writer("The local ProRes writer rejected frame \(frame): \(writer.error?.localizedDescription ?? "unknown error")")
            }
        }
        writer.endSession(atSourceTime: CMTime(value: Int64(frameCount), timescale: frameRate))
        input.markAsFinished()
        try finishWriting(writer)
        guard writer.status == .completed else {
            throw MediaSpikeError.writer("The local ProRes writer did not finish: \(writer.error?.localizedDescription ?? "unknown error")")
        }
    }

    private static func makeFrame(frame: Int) -> CVPixelBuffer? {
        var result: CVPixelBuffer?
        guard CVPixelBufferCreate(
            nil, width, height, kCVPixelFormatType_32BGRA,
            [kCVPixelBufferCGImageCompatibilityKey: true, kCVPixelBufferCGBitmapContextCompatibilityKey: true] as CFDictionary,
            &result
        ) == kCVReturnSuccess, let result else { return nil }
        CVPixelBufferLockBaseAddress(result, [])
        defer { CVPixelBufferUnlockBaseAddress(result, []) }
        let stride = CVPixelBufferGetBytesPerRow(result)
        let pointer = CVPixelBufferGetBaseAddress(result)!.assumingMemoryBound(to: UInt8.self)
        for y in 0..<height {
            for x in 0..<width {
                let offset = y * stride + x * 4
                pointer[offset] = UInt8((x + frame * 3) % 255) // blue
                pointer[offset + 1] = UInt8((y * 2 + frame * 5) % 255) // green
                pointer[offset + 2] = UInt8((frame * 17) % 255) // red
                pointer[offset + 3] = 255
            }
        }
        return result
    }

    private static func writeWave(to url: URL) throws {
        let sampleRate = 44_100
        let sampleCount = sampleRate
        var pcm = Data(capacity: sampleCount * MemoryLayout<Int16>.size)
        for sample in 0..<sampleCount {
            let phase = Double(sample) * 2.0 * .pi * 440.0 / Double(sampleRate)
            pcm.appendLittleEndian(Int16((sin(phase) * 0.18 * Double(Int16.max)).rounded()))
        }
        var wave = Data()
        wave.append("RIFF".data(using: .ascii)!)
        wave.appendLittleEndian(UInt32(36 + pcm.count))
        wave.append("WAVEfmt ".data(using: .ascii)!)
        wave.appendLittleEndian(UInt32(16))
        wave.appendLittleEndian(UInt16(1))
        wave.appendLittleEndian(UInt16(1))
        wave.appendLittleEndian(UInt32(sampleRate))
        wave.appendLittleEndian(UInt32(sampleRate * 2))
        wave.appendLittleEndian(UInt16(2))
        wave.appendLittleEndian(UInt16(16))
        wave.append("data".data(using: .ascii)!)
        wave.appendLittleEndian(UInt32(pcm.count))
        wave.append(pcm)
        try wave.write(to: url, options: .atomic)
    }

    private static func mux(videoURL: URL, audioURL: URL, outputURL: URL) throws {
        let videoAsset = AVURLAsset(url: videoURL)
        let audioAsset = AVURLAsset(url: audioURL)
        try waitForAsset(videoAsset, keys: ["duration", "tracks"])
        try waitForAsset(audioAsset, keys: ["duration", "tracks"])
        guard let sourceVideo = videoAsset.tracks(withMediaType: .video).first,
              let sourceAudio = audioAsset.tracks(withMediaType: .audio).first else {
            throw MediaSpikeError.export("The local fixture did not expose one video and one audio track.")
        }
        let composition = AVMutableComposition()
        guard let composedVideo = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid),
              let composedAudio = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw MediaSpikeError.export("Cannot allocate local composition tracks.")
        }
        guard abs(videoAsset.duration.seconds - expectedDurationSeconds) < 0.0001,
              abs(audioAsset.duration.seconds - expectedDurationSeconds) < 0.0001 else {
            throw MediaSpikeError.export("Refusing to trim mismatched fixture timelines.")
        }
        let duration = audioAsset.duration
        do {
            try composedVideo.insertTimeRange(CMTimeRange(start: .zero, duration: duration), of: sourceVideo, at: .zero)
            try composedAudio.insertTimeRange(CMTimeRange(start: .zero, duration: duration), of: sourceAudio, at: .zero)
        } catch {
            throw MediaSpikeError.export("Cannot mux the owned fixture tracks: \(error.localizedDescription)")
        }
        guard let export = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetPassthrough) else {
            throw MediaSpikeError.export("The local AVFoundation passthrough export preset is unavailable.")
        }
        export.outputURL = outputURL
        export.outputFileType = .mov
        export.shouldOptimizeForNetworkUse = false
        try finishExport(export)
        guard export.status == .completed else {
            throw MediaSpikeError.export("The local MOV export did not finish: \(export.error?.localizedDescription ?? "unknown error")")
        }
    }

    private static func inspect(outputURL: URL) throws -> MediaSpikeProbe {
        let asset = AVURLAsset(url: outputURL)
        try waitForAsset(asset, keys: ["duration", "tracks"])
        guard FileManager.default.fileExists(atPath: outputURL.path), asset.duration.isValid,
              let video = asset.tracks(withMediaType: .video).first,
              let audio = asset.tracks(withMediaType: .audio).first else {
            throw MediaSpikeError.invalidOutput("The local MOV output has no readable audio/video streams.")
        }
        let size = video.naturalSize.applying(video.preferredTransform)
        let codec = video.formatDescriptions.first
            .map { fourCC(CMFormatDescriptionGetMediaSubType($0 as! CMFormatDescription)) } ?? "unknown"
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero
        var firstTime = CMTime.invalid
        var lastTime = CMTime.invalid
        var middleTime = CMTime.invalid
        let first = try generator.copyCGImage(at: .zero, actualTime: &firstTime)
        let finalTime = CMTime(value: Int64(frameCount - 1), timescale: frameRate)
        let last = try generator.copyCGImage(at: finalTime, actualTime: &lastTime)
        let middle = try generator.copyCGImage(at: CMTime(seconds: 0.5, preferredTimescale: 600), actualTime: &middleTime)
        guard abs(firstTime.seconds) < 0.0001,
              abs(lastTime.seconds - finalTime.seconds) < 0.0001,
              abs(middleTime.seconds - 0.5) < 0.0001,
              abs(Double(video.nominalFrameRate) - Double(frameRate)) < 0.001 else {
            throw MediaSpikeError.invalidOutput("Decoded preview timestamps or frame rate differ from the fixture.")
        }
        // Read the actual muxed PCM stream and compare every byte with the owned WAV.
        let reader = try AVAssetReader(asset: asset)
        let audioOutput = AVAssetReaderTrackOutput(track: audio, outputSettings: nil)
        guard reader.canAdd(audioOutput) else { throw MediaSpikeError.invalidOutput("Cannot read the soundtrack.") }
        reader.add(audioOutput)
        guard reader.startReading() else { throw MediaSpikeError.invalidOutput("Cannot start soundtrack decoding.") }
        var decoded = Data()
        var samples = 0
        var expectedAudioTime = 0.0
        var audioCoversMiddle = false
        let readDeadline = Date().addingTimeInterval(30)
        while let buffer = audioOutput.copyNextSampleBuffer() {
            guard Date() < readDeadline else {
                reader.cancelReading()
                throw MediaSpikeError.invalidOutput("Soundtrack decode timed out.")
            }
            let timestamp = CMSampleBufferGetPresentationTimeStamp(buffer).seconds
            let count = CMSampleBufferGetNumSamples(buffer)
            // AVAssetReader may emit an empty end-of-stream marker with an invalid PTS.
            if count == 0 { continue }
            guard abs(timestamp - expectedAudioTime) < 1.0 / 44_100 else {
                throw MediaSpikeError.invalidOutput("Soundtrack timestamps are discontinuous: actual=\(timestamp), expected=\(expectedAudioTime), samples=\(count), duration=\(CMSampleBufferGetDuration(buffer).seconds).")
            }
            expectedAudioTime = timestamp + Double(count) / 44_100
            audioCoversMiddle = audioCoversMiddle || (timestamp <= middleTime.seconds && expectedAudioTime > middleTime.seconds)
            samples += count
            guard let block = CMSampleBufferGetDataBuffer(buffer) else { throw MediaSpikeError.invalidOutput("Missing PCM data.") }
            var bytes = [UInt8](repeating: 0, count: CMBlockBufferGetDataLength(block))
            guard CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: bytes.count, destination: &bytes) == kCMBlockBufferNoErr else {
                throw MediaSpikeError.invalidOutput("Unreadable PCM data.")
            }
            decoded.append(contentsOf: bytes)
        }
        let original = try Data(contentsOf: outputURL.deletingLastPathComponent().appending(path: "owned-spike-soundtrack.wav"))
        let audioCodec = audio.formatDescriptions.first
            .map { fourCC(CMFormatDescriptionGetMediaSubType($0 as! CMFormatDescription)) } ?? "unknown"
        guard reader.status == .completed, samples == 44_100, audioCoversMiddle,
              audio.timeRange.start == .zero, video.timeRange.start == .zero,
              decoded == original.dropFirst(44) else {
            throw MediaSpikeError.invalidOutput("Mux changed soundtrack bytes or preview/audio alignment.")
        }
        for (name, frame) in [("first", first), ("middle", middle), ("last", last)] {
            let url = outputURL.deletingLastPathComponent().appending(path: "preview-\(name).png")
            guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
                throw MediaSpikeError.invalidOutput("Cannot create preview PNG.")
            }
            CGImageDestinationAddImage(destination, frame, nil)
            guard CGImageDestinationFinalize(destination) else { throw MediaSpikeError.invalidOutput("Cannot finish preview PNG.") }
        }
        return MediaSpikeProbe(
            outputURL: outputURL,
            durationSeconds: asset.duration.seconds,
            videoDurationSeconds: video.timeRange.duration.seconds,
            audioDurationSeconds: audio.timeRange.duration.seconds,
            width: Int(abs(size.width.rounded())),
            height: Int(abs(size.height.rounded())),
            videoCodecFourCC: codec,
            firstPreviewWidth: first.width,
            lastPreviewWidth: last.width,
            previewSeekSeconds: middleTime.seconds,
            firstFrameSeconds: firstTime.seconds,
            lastFrameSeconds: lastTime.seconds,
            decodedAudioSamples: samples,
            audioCodecFourCC: audioCodec
        )
    }

    private static func finishWriting(_ writer: AVAssetWriter) throws {
        let done = DispatchSemaphore(value: 0)
        writer.finishWriting { done.signal() }
        guard done.wait(timeout: .now() + 30) == .success else {
            writer.cancelWriting()
            throw MediaSpikeError.writer("Writer completion timed out.")
        }
    }

    private static func finishExport(_ export: AVAssetExportSession) throws {
        let done = DispatchSemaphore(value: 0)
        export.exportAsynchronously { done.signal() }
        guard done.wait(timeout: .now() + 30) == .success else {
            export.cancelExport()
            throw MediaSpikeError.export("Export completion timed out.")
        }
    }

    private static func waitForAsset(_ asset: AVAsset, keys: [String]) throws {
        let done = DispatchSemaphore(value: 0)
        asset.loadValuesAsynchronously(forKeys: keys) { done.signal() }
        guard done.wait(timeout: .now() + 30) == .success else {
            asset.cancelLoading()
            throw MediaSpikeError.invalidOutput("Asset loading timed out.")
        }
        for key in keys {
            var error: NSError?
            guard asset.statusOfValue(forKey: key, error: &error) == .loaded else {
                throw MediaSpikeError.invalidOutput("Cannot load asset \(key): \(error?.localizedDescription ?? "unknown error")")
            }
        }
    }

    private static func fourCC(_ value: FourCharCode) -> String {
        let bytes = [
            UInt8((value >> 24) & 0xff), UInt8((value >> 16) & 0xff),
            UInt8((value >> 8) & 0xff), UInt8(value & 0xff),
        ]
        return String(bytes: bytes, encoding: .ascii) ?? "unknown"
    }
}

public struct PreviewSynchronizer: Sendable {
    public let videoDuration: CMTime
    public let soundtrackDuration: CMTime
    public let frameRate: Int32

    public init(videoDuration: CMTime, soundtrackDuration: CMTime, frameRate: Int32) {
        self.videoDuration = videoDuration
        self.soundtrackDuration = soundtrackDuration
        self.frameRate = frameRate
    }

    public func position(forSoundtrackTime soundtrackTime: CMTime) -> CMTime {
        CMTimeMinimum(CMTimeMaximum(.zero, soundtrackTime), CMTimeMinimum(videoDuration, soundtrackDuration))
    }

    public func isSynchronized(videoTime: CMTime, soundtrackTime: CMTime) -> Bool {
        abs(CMTimeSubtract(videoTime, position(forSoundtrackTime: soundtrackTime)).seconds) <= (1.0 / Double(frameRate))
    }
}

private extension Data {
    mutating func appendLittleEndian<T: FixedWidthInteger>(_ value: T) {
        var littleEndian = value.littleEndian
        Swift.withUnsafeBytes(of: &littleEndian) { append(contentsOf: $0) }
    }
}
