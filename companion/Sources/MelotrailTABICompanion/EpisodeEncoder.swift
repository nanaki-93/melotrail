import AVFoundation
import AudioToolbox
import CoreGraphics
import CoreMedia
import CoreVideo
import CryptoKit
import Darwin
import Foundation

/// The reviewed first-delivery preset. It is intentionally narrow: callers
/// cannot silently choose a different codec, frame cadence, or audio policy.
public enum TABIVideoPreset: String, Codable, Sendable {
    case proRes422PcmMOV

    public var fileExtension: String { "mov" }
    public var videoCodec: String { "apcn" }
    public var audioCodec: String { "lpcm" }
}

public enum EpisodeEncodingError: Error, LocalizedError, Sendable {
    case invalidRequest(String)
    case cancelled
    case timedOut
    case writer(String)
    case probe(String)

    public var errorDescription: String? {
        switch self {
        case .invalidRequest(let message), .writer(let message), .probe(let message): message
        case .cancelled: "The local video encode was cancelled before publication."
        case .timedOut: "The local video encode exceeded its configured time limit."
        }
    }
}

public struct EpisodeEncodingLimits: Sendable, Equatable {
    public let maximumInputBytes: Int64
    public let timeout: TimeInterval
    public let maximumOutputBytes: Int64
    public let minimumAvailableBytes: Int64

    public init(maximumInputBytes: Int64 = 4 * 1024 * 1024 * 1024, timeout: TimeInterval = 60 * 60, maximumOutputBytes: Int64 = 8 * 1024 * 1024 * 1024, minimumAvailableBytes: Int64 = 64 * 1024 * 1024) {
        self.maximumInputBytes = maximumInputBytes
        self.timeout = timeout
        self.maximumOutputBytes = maximumOutputBytes
        self.minimumAvailableBytes = minimumAvailableBytes
    }
}

public struct EpisodeTechnicalReport: Codable, Sendable, Equatable {
    public let schema: String
    public let preset: TABIVideoPreset
    public let outputSHA256: String
    public let soundtrackSHA256: String
    public let audioSamplesSHA256: String
    public let audioChannels: Int
    public let audioSampleRate: Double
    public let compositionSHA256: String
    public let assetPins: [SceneAssetPin]
    public let sceneVersion: String
    public let identityVersion: String
    public let frameRate: Int
    public let frameCount: Int64
    public let width: Int
    public let height: Int
    public let videoCodec: String
    public let audioCodec: String
    public let videoDurationSeconds: Double
    public let audioDurationSeconds: Double
    public let audioVideoDriftSeconds: Double
    public let firstFrameMeanAbsoluteError: Double
    public let finalFrameMeanAbsoluteError: Double
}

public enum EpisodeEncodingPhase: String, Sendable {
    case encoding, finalizing, validating, publishing
}

public struct PublishedEpisode: Sendable, Equatable {
    public let outputURL: URL
    public let reportURL: URL
    public let report: EpisodeTechnicalReport
}

/// Encodes the exact same resolver frames used by `ScenePreviewStage`, then
/// verifies the staged MOV before no-overwrite publication. The finished
/// soundtrack is decoded/re-encoded explicitly as PCM; it is never trimmed,
/// stretched, normalized, or otherwise produced from MIDI.
public enum TABIEpisodeEncoder {
    public static func encode(
        composition: SceneCompositionPlan,
        library: AssetLibrary,
        soundtrack: FinishedSoundtrack,
        geometry: PreviewOutputGeometry,
        outputDirectory: URL,
        outputFileName: String,
        preset: TABIVideoPreset = .proRes422PcmMOV,
        limits: EpisodeEncodingLimits = EpisodeEncodingLimits(),
        cancellation: EncoderCancellation = EncoderCancellation(),
        onProgress: (EpisodeEncodingPhase) -> Void = { _ in }
    ) throws -> PublishedEpisode {
        guard outputFileName.lowercased().hasSuffix(".\(preset.fileExtension)"),
              limits.maximumInputBytes > 0, limits.timeout.isFinite, limits.timeout > 0,
              limits.maximumOutputBytes > 0, limits.minimumAvailableBytes >= 0 else {
            throw EpisodeEncodingError.invalidRequest("Use a .mov output name and finite positive encoding limits.")
        }
        guard geometry.width <= 3840, geometry.height <= 3840,
              geometry.width * geometry.height <= 3840 * 2160,
              geometry.width.isMultiple(of: 2), geometry.height.isMultiple(of: 2) else {
            throw EpisodeEncodingError.invalidRequest("ProRes 422 delivery requires even dimensions within a 4K pixel budget.")
        }
        guard !cancellation.isCancelled else { throw EpisodeEncodingError.cancelled }
        guard composition.timing.frameRate > 0, composition.timing.frameCount > 0,
              composition.timing.soundtrackDuration == soundtrack.duration else {
            throw EpisodeEncodingError.invalidRequest("The composition must retain the digest-pinned soundtrack timing plan.")
        }
        try SceneComposer.validatePinnedAssets(composition, library: library)
        guard try inputBytes(composition: composition, library: library, soundtrack: soundtrack) <= limits.maximumInputBytes else {
            throw EpisodeEncodingError.invalidRequest("Pinned soundtrack and scene inputs exceed this encode's byte limit.")
        }
        let stage = try ScenePreviewStage(composition: composition, library: library, soundtrack: soundtrack, geometry: geometry)
        let stager = try OwnedOutputStager(outputDirectory: outputDirectory, outputFileName: outputFileName)
        let available = try stager.outputDirectory.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]).volumeAvailableCapacityForImportantUsage
        guard let available, available >= limits.minimumAvailableBytes else { throw BoundedEncoderError.insufficientDiskSpace }
        let deadline = Date().addingTimeInterval(limits.timeout)
        do {
            onProgress(.encoding)
            try writeMOV(stage: stage, stagedURL: stager.stagedOutputURL, limits: limits, deadline: deadline, cancellation: cancellation, onProgress: onProgress)
            onProgress(.validating)
            let report = try probe(
                stagedURL: stager.stagedOutputURL, stage: stage, composition: composition,
                soundtrack: soundtrack, geometry: geometry, preset: preset, deadline: deadline, cancellation: cancellation
            )
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
            let reportData = try encoder.encode(report)
            try check(deadline: deadline, cancellation: cancellation)
            onProgress(.publishing)
            let outputURL = try stager.validateAndPublish(provenance: reportData) { staged in
                try check(deadline: deadline, cancellation: cancellation)
                guard try fileSize(staged) <= limits.maximumOutputBytes else { throw BoundedEncoderError.outputTooLarge }
            }
            let reportURL = outputURL.deletingPathExtension().appendingPathExtension("provenance.json")
            return PublishedEpisode(outputURL: outputURL, reportURL: reportURL, report: report)
        } catch {
            // `OwnedOutputStager` owns only its UUID directory. It is released
            // here on every non-publication path and never touches caller media.
            throw error
        }
    }

    private static func writeMOV(stage: ScenePreviewStage, stagedURL: URL, limits: EpisodeEncodingLimits, deadline: Date, cancellation: EncoderCancellation, onProgress: (EpisodeEncodingPhase) -> Void) throws {
        let composition = stage.composition
        let writer: AVAssetWriter
        do { writer = try AVAssetWriter(outputURL: stagedURL, fileType: .mov) }
        catch { throw EpisodeEncodingError.writer("Cannot create MOV writer: \(error.localizedDescription)") }
        let video = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.proRes422,
            AVVideoWidthKey: stage.geometry.width,
            AVVideoHeightKey: stage.geometry.height,
        ])
        video.expectsMediaDataInRealTime = false
        guard writer.canAdd(video) else { throw EpisodeEncodingError.writer("The ProRes 422 writer rejected the preview geometry.") }
        writer.add(video)
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: video, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: stage.geometry.width,
            kCVPixelBufferHeightKey as String: stage.geometry.height,
        ])
        let source = AVURLAsset(url: stage.soundtrack.url)
        guard let sourceAudio = source.tracks(withMediaType: .audio).first else { throw EpisodeEncodingError.writer("Finished soundtrack has no readable audio stream.") }
        let audio = AVAssetWriterInput(mediaType: .audio, outputSettings: pcmSettings(for: sourceAudio))
        audio.expectsMediaDataInRealTime = false
        guard writer.canAdd(audio) else { throw EpisodeEncodingError.writer("The PCM writer rejected the finished soundtrack.") }
        writer.add(audio)
        guard writer.startWriting() else { throw EpisodeEncodingError.writer("MOV writer did not start: \(writer.error?.localizedDescription ?? "unknown error")") }
        writer.startSession(atSourceTime: .zero)
        do {
            let audioReader = try AVAssetReader(asset: source)
            let audioOutput = AVAssetReaderTrackOutput(track: sourceAudio, outputSettings: pcmDecodeSettings)
            guard audioReader.canAdd(audioOutput) else { throw EpisodeEncodingError.writer("Cannot decode soundtrack.") }
            audioReader.add(audioOutput)
            guard audioReader.startReading() else { throw EpisodeEncodingError.writer("Cannot start soundtrack decode.") }
            defer { audioReader.cancelReading() }
            var sample = audioOutput.copyNextSampleBuffer()
            var frame: Int64 = 0
            var audioFinished = false
            while frame < composition.timing.frameCount || !audioFinished {
                try check(deadline: deadline, cancellation: cancellation)
                try enforceOutputLimits(stagedURL, limits: limits)
                guard writer.status == .writing else {
                    throw EpisodeEncodingError.writer("MOV writer stopped accepting media: \(writer.error?.localizedDescription ?? "unknown error")")
                }
                if sample == nil && !audioFinished {
                    guard audioReader.status == .completed else { throw EpisodeEncodingError.writer("Soundtrack decoding failed.") }
                    audio.markAsFinished(); audioFinished = true
                }
                let videoTime = CMTime(value: frame, timescale: CMTimeScale(composition.timing.frameRate))
                let videoReady = frame < composition.timing.frameCount && video.isReadyForMoreMediaData
                // Prefer the earlier timestamp, but never wait on one input while
                // its sibling can advance: AVAssetWriter backpressure couples them.
                if let current = sample, audio.isReadyForMoreMediaData,
                   !videoReady || CMTimeCompare(CMSampleBufferGetPresentationTimeStamp(current), videoTime) <= 0 {
                    guard audio.append(current) else { throw EpisodeEncodingError.writer("PCM writer rejected soundtrack samples.") }
                    sample = audioOutput.copyNextSampleBuffer()
                } else if videoReady {
                    let preview = try stage.render(frame: frame)
                    guard let buffer = pixelBuffer(from: preview.image, width: stage.geometry.width, height: stage.geometry.height) else {
                        throw EpisodeEncodingError.writer("Cannot allocate output pixels for frame \(frame).")
                    }
                    guard adaptor.append(buffer, withPresentationTime: videoTime) else {
                        throw EpisodeEncodingError.writer("ProRes 422 writer rejected frame \(frame): \(writer.error?.localizedDescription ?? "unknown error")")
                    }
                    frame += 1
                    if frame == composition.timing.frameCount { video.markAsFinished() }
                } else if !audioFinished || frame < composition.timing.frameCount {
                    RunLoop.current.run(until: Date().addingTimeInterval(0.002))
                }
            }
            let recheckedSoundtrack = try FinishedSoundtrack.open(url: stage.soundtrack.url, expectedSHA256: stage.soundtrack.sha256)
            guard recheckedSoundtrack.duration == composition.timing.soundtrackDuration else {
                throw EpisodeEncodingError.writer("Finished soundtrack timing changed before PCM delivery.")
            }
            writer.endSession(atSourceTime: CMTime(value: composition.timing.frameCount, timescale: CMTimeScale(composition.timing.frameRate)))
            onProgress(.finalizing)
            try check(deadline: deadline, cancellation: cancellation)
            try finish(writer, stagedURL: stagedURL, limits: limits, deadline: deadline, cancellation: cancellation)
            guard writer.status == .completed else { throw EpisodeEncodingError.writer("MOV writer did not finish: \(writer.error?.localizedDescription ?? "unknown error")") }
        } catch {
            writer.cancelWriting()
            throw error
        }
    }

    private static func probe(stagedURL: URL, stage: ScenePreviewStage, composition: SceneCompositionPlan, soundtrack: FinishedSoundtrack, geometry: PreviewOutputGeometry, preset: TABIVideoPreset, deadline: Date, cancellation: EncoderCancellation) throws -> EpisodeTechnicalReport {
        try check(deadline: deadline, cancellation: cancellation)
        let recheckedSoundtrack = try FinishedSoundtrack.open(url: soundtrack.url, expectedSHA256: soundtrack.sha256)
        guard recheckedSoundtrack.duration == composition.timing.soundtrackDuration else {
            throw EpisodeEncodingError.probe("Finished soundtrack timing changed before publication.")
        }
        let asset = AVURLAsset(url: stagedURL, options: [AVURLAssetOverrideMIMETypeKey: "video/quicktime"])
        let videos = asset.tracks(withMediaType: .video), audios = asset.tracks(withMediaType: .audio)
        guard videos.count == 1, audios.count == 1, let video = videos.first, let audio = audios.first else {
            throw EpisodeEncodingError.probe("Staged MOV must contain exactly one video and one audio stream.")
        }
        let size = video.naturalSize.applying(video.preferredTransform)
        let videoCodec = codec(video), audioCodec = codec(audio)
        guard Int(abs(size.width.rounded())) == geometry.width, Int(abs(size.height.rounded())) == geometry.height,
              abs(Double(video.nominalFrameRate) - Double(composition.timing.frameRate)) < 0.01,
              videoCodec == preset.videoCodec, audioCodec == preset.audioCodec else {
            throw EpisodeEncodingError.probe("Staged MOV stream differs: size \(size), fps \(video.nominalFrameRate), codecs \(videoCodec)/\(audioCodec).")
        }
        let expectedVideoDuration = Double(composition.timing.frameCount) / Double(composition.timing.frameRate)
        let videoDuration = video.timeRange.duration.seconds, audioDuration = audio.timeRange.duration.seconds
        let frameTolerance = 1.0 / Double(composition.timing.frameRate)
        guard abs(videoDuration - expectedVideoDuration) <= frameTolerance,
              abs(audioDuration - soundtrack.duration.doubleValue) <= frameTolerance,
              abs(videoDuration - audioDuration) <= frameTolerance else {
            throw EpisodeEncodingError.probe("Staged MOV duration or A/V timeline differs by more than one output frame.")
        }
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.requestedTimeToleranceBefore = .zero; generator.requestedTimeToleranceAfter = .zero
        var firstActual = CMTime.invalid, finalActual = CMTime.invalid
        let first = try generator.copyCGImage(at: .zero, actualTime: &firstActual)
        let finalFrame = composition.timing.frameCount - 1
        let last = try generator.copyCGImage(at: CMTime(value: finalFrame, timescale: CMTimeScale(composition.timing.frameRate)), actualTime: &finalActual)
        guard abs(firstActual.seconds) <= frameTolerance,
              abs(finalActual.seconds - Double(finalFrame) / Double(composition.timing.frameRate)) <= frameTolerance else {
            throw EpisodeEncodingError.probe("First or final output frame is not decodable at the shared plan timestamp.")
        }
        let firstError = try imageError(first, try stage.render(frame: 0).image)
        let finalError = try imageError(last, try stage.render(frame: finalFrame).image)
        guard firstError <= 32, finalError <= 32 else {
            throw EpisodeEncodingError.probe("Decoded output frame errors \(firstError)/\(finalError) exceed the ProRes 422 fidelity bound.")
        }
        let deliveredSamples = try validateAudioTimeline(asset: asset, track: audio, expectedDuration: soundtrack.duration.doubleValue, deadline: deadline, cancellation: cancellation)
        let sourceAsset = AVURLAsset(url: soundtrack.url)
        guard let sourceAudio = sourceAsset.tracks(withMediaType: .audio).first,
              let format = audio.formatDescriptions.first else { throw EpisodeEncodingError.probe("Cannot read PCM source/output format.") }
        let sourceSamples = try validateAudioTimeline(asset: sourceAsset, track: sourceAudio, expectedDuration: soundtrack.duration.doubleValue, deadline: deadline, cancellation: cancellation)
        guard sourceSamples == deliveredSamples else { throw EpisodeEncodingError.probe("Delivered PCM samples differ from the finished soundtrack.") }
        guard let audioDescription = CMAudioFormatDescriptionGetStreamBasicDescription(format as! CMAudioFormatDescription)?.pointee else {
            throw EpisodeEncodingError.probe("Cannot read delivered PCM stream description.")
        }
        let planEncoder = JSONEncoder(); planEncoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        let planDigest = SHA256.hash(data: try planEncoder.encode(composition)).map { String(format: "%02x", $0) }.joined()
        try check(deadline: deadline, cancellation: cancellation)
        return EpisodeTechnicalReport(schema: "melotrail-tabi-episode-report-v1", preset: preset,
            outputSHA256: try AssetDigest.sha256(of: stagedURL), soundtrackSHA256: soundtrack.sha256, audioSamplesSHA256: deliveredSamples, audioChannels: Int(audioDescription.mChannelsPerFrame), audioSampleRate: audioDescription.mSampleRate, compositionSHA256: planDigest, assetPins: composition.assetPins,
            sceneVersion: composition.sceneVersion, identityVersion: composition.identityVersion,
            frameRate: composition.timing.frameRate, frameCount: composition.timing.frameCount,
            width: geometry.width, height: geometry.height, videoCodec: videoCodec, audioCodec: audioCodec,
            videoDurationSeconds: videoDuration, audioDurationSeconds: audioDuration,
            audioVideoDriftSeconds: abs(videoDuration - audioDuration),
            firstFrameMeanAbsoluteError: firstError, finalFrameMeanAbsoluteError: finalError)
    }

    private static func validateAudioTimeline(asset: AVAsset, track: AVAssetTrack, expectedDuration: Double, deadline: Date, cancellation: EncoderCancellation) throws -> String {
        let reader = try AVAssetReader(asset: asset)
        let output = AVAssetReaderTrackOutput(track: track, outputSettings: pcmDecodeSettings)
        guard reader.canAdd(output) else { throw EpisodeEncodingError.probe("Cannot decode staged PCM audio.") }
        reader.add(output); guard reader.startReading() else { throw EpisodeEncodingError.probe("Cannot start staged PCM decode.") }
        defer { reader.cancelReading() }
        var digest = SHA256()
        var previousEnd = 0.0
        var sawAudio = false
        while let sample = output.copyNextSampleBuffer() {
            try check(deadline: deadline, cancellation: cancellation)
            let count = CMSampleBufferGetNumSamples(sample)
            if count == 0 { continue }
            let start = CMSampleBufferGetPresentationTimeStamp(sample).seconds
            let duration = CMSampleBufferGetDuration(sample).seconds
            guard start.isFinite, duration.isFinite, duration > 0, abs(start - previousEnd) <= 0.01 else {
                throw EpisodeEncodingError.probe("Staged PCM timestamps are discontinuous.")
            }
            guard let block = CMSampleBufferGetDataBuffer(sample) else { throw EpisodeEncodingError.probe("PCM sample data is missing.") }
            var bytes = Data(count: CMBlockBufferGetDataLength(block))
            let status = bytes.withUnsafeMutableBytes { storage in
                CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: storage.count, destination: storage.baseAddress!)
            }
            guard status == kCMBlockBufferNoErr else { throw EpisodeEncodingError.probe("Cannot inspect decoded PCM samples.") }
            digest.update(data: bytes)
            previousEnd = start + duration; sawAudio = true
        }
        guard reader.status == .completed, sawAudio, abs(previousEnd - expectedDuration) <= 0.02 else {
            throw EpisodeEncodingError.probe("Staged PCM timeline does not cover the exact finished soundtrack duration.")
        }
        return digest.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private static var pcmDecodeSettings: [String: Any] { [AVFormatIDKey: kAudioFormatLinearPCM,
        AVLinearPCMBitDepthKey: 32, AVLinearPCMIsFloatKey: true,
        AVLinearPCMIsBigEndianKey: false, AVLinearPCMIsNonInterleaved: false] }

    private static func pcmSettings(for track: AVAssetTrack) -> [String: Any] {
        let description = track.formatDescriptions.first.flatMap {
            CMAudioFormatDescriptionGetStreamBasicDescription($0 as! CMAudioFormatDescription)?.pointee
        }
        return [AVFormatIDKey: kAudioFormatLinearPCM,
                AVSampleRateKey: description?.mSampleRate ?? 44_100,
                AVNumberOfChannelsKey: Int(description?.mChannelsPerFrame ?? 2),
                AVLinearPCMBitDepthKey: 32, AVLinearPCMIsFloatKey: true,
                AVLinearPCMIsBigEndianKey: false, AVLinearPCMIsNonInterleaved: false]
    }

    private static func pixelBuffer(from image: CGImage, width: Int, height: Int) -> CVPixelBuffer? {
        var result: CVPixelBuffer?
        guard CVPixelBufferCreate(nil, width, height, kCVPixelFormatType_32BGRA,
              [kCVPixelBufferCGImageCompatibilityKey: true, kCVPixelBufferCGBitmapContextCompatibilityKey: true] as CFDictionary, &result) == kCVReturnSuccess,
              let result else { return nil }
        CVPixelBufferLockBaseAddress(result, [])
        defer { CVPixelBufferUnlockBaseAddress(result, []) }
        guard let context = CGContext(data: CVPixelBufferGetBaseAddress(result), width: width, height: height, bitsPerComponent: 8,
              bytesPerRow: CVPixelBufferGetBytesPerRow(result), space: CGColorSpace(name: CGColorSpace.sRGB)!,
              bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else { return nil }
        context.setFillColor(CGColor(gray: 0, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return result
    }

    private static func imageError(_ left: CGImage, _ right: CGImage) throws -> Double {
        guard left.width == right.width, left.height == right.height else {
            throw EpisodeEncodingError.probe("Preview/output frame dimensions differ.")
        }
        func pixels(_ image: CGImage) throws -> [UInt8] {
            var bytes = [UInt8](repeating: 0, count: image.width * image.height * 4)
            try bytes.withUnsafeMutableBytes { storage in
                guard let context = CGContext(data: storage.baseAddress, width: image.width, height: image.height,
                    bitsPerComponent: 8, bytesPerRow: image.width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue) else {
                    throw EpisodeEncodingError.probe("Cannot normalize preview/output pixels.")
                }
                context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
            }
            return bytes
        }
        let a = try pixels(left), b = try pixels(right)
        var sum: Int64 = 0
        for offset in a.indices where offset % 4 != 3 { sum += Int64(abs(Int(a[offset]) - Int(b[offset]))) }
        return Double(sum) / Double(left.width * left.height * 3)
    }

    private static func codec(_ track: AVAssetTrack) -> String {
        guard let description = track.formatDescriptions.first else { return "unknown" }
        let code = CMFormatDescriptionGetMediaSubType(description as! CMFormatDescription)
        if code == kAudioFormatLinearPCM { return "lpcm" }
        let bytes = [UInt8((code >> 24) & 0xff), UInt8((code >> 16) & 0xff), UInt8((code >> 8) & 0xff), UInt8(code & 0xff)]
        return String(bytes: bytes, encoding: .ascii) ?? "unknown"
    }

    private static func finish(_ writer: AVAssetWriter, stagedURL: URL, limits: EpisodeEncodingLimits, deadline: Date, cancellation: EncoderCancellation) throws {
        let complete = DispatchSemaphore(value: 0); writer.finishWriting { complete.signal() }
        while complete.wait(timeout: .now() + 0.02) == .timedOut {
            try check(deadline: deadline, cancellation: cancellation)
            try enforceOutputLimits(stagedURL, limits: limits)
        }
        try check(deadline: deadline, cancellation: cancellation)
        try enforceOutputLimits(stagedURL, limits: limits)
    }

    private static func enforceOutputLimits(_ url: URL, limits: EpisodeEncodingLimits) throws {
        guard try fileSize(url) <= limits.maximumOutputBytes else { throw BoundedEncoderError.outputTooLarge }
        let capacity = try url.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]).volumeAvailableCapacityForImportantUsage
        guard let capacity, capacity >= limits.minimumAvailableBytes else { throw BoundedEncoderError.insufficientDiskSpace }
    }

    private static func check(deadline: Date, cancellation: EncoderCancellation) throws {
        if cancellation.isCancelled { throw EpisodeEncodingError.cancelled }
        if Date() >= deadline { throw EpisodeEncodingError.timedOut }
    }

    private static func fileSize(_ url: URL) throws -> Int64 {
        Int64(try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0)
    }

    private static func inputBytes(composition: SceneCompositionPlan, library: AssetLibrary, soundtrack: FinishedSoundtrack) throws -> Int64 {
        let assets = try library.validatedApprovedAssets(pinned: composition.assetPins.map(\.identity))
        let root = library.rootURL.standardizedFileURL.resolvingSymlinksInPath()
        var total = try fileSize(soundtrack.url)
        for asset in assets {
            let bytes = try fileSize(root.appendingPathComponent(asset.relativeMediaPath))
            let next = total.addingReportingOverflow(bytes)
            guard !next.overflow else { throw EpisodeEncodingError.invalidRequest("Pinned input byte total overflowed.") }
            total = next.partialValue
        }
        return total
    }
}
