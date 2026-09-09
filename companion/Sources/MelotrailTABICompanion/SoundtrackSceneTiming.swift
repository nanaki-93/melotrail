import AVFoundation
import CoreMedia
import CryptoKit
import Foundation

/// An exact, non-negative duration. The companion keeps timing as a rational
/// number until the one documented conversion to output frame boundaries.
public struct RationalTime: Codable, Equatable, Hashable, Comparable, Sendable {
    public let numerator: Int64
    public let denominator: Int64

    public init(_ numerator: Int64, _ denominator: Int64 = 1) throws {
        guard numerator >= 0, denominator > 0 else {
            throw SoundtrackSceneTimingError.invalidTime("durations must be non-negative rationals")
        }
        let divisor = Self.gcd(numerator, denominator)
        self.numerator = numerator / divisor
        self.denominator = denominator / divisor
    }

    private enum CodingKeys: String, CodingKey { case numerator, denominator }

    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        try self.init(values.decode(Int64.self, forKey: .numerator), values.decode(Int64.self, forKey: .denominator))
    }

    public static let zero = try! RationalTime(0)

    public static func < (lhs: RationalTime, rhs: RationalTime) -> Bool {
        let product = lhs.numerator.multipliedFullWidth(by: rhs.denominator)
        let reverse = rhs.numerator.multipliedFullWidth(by: lhs.denominator)
        return product.high == reverse.high ? product.low < reverse.low : product.high < reverse.high
    }

    public var doubleValue: Double { Double(numerator) / Double(denominator) }

    public func adding(_ other: RationalTime) throws -> RationalTime {
        let left = numerator.multipliedReportingOverflow(by: other.denominator)
        let right = other.numerator.multipliedReportingOverflow(by: denominator)
        guard !left.overflow, !right.overflow else { throw SoundtrackSceneTimingError.overflow }
        let sum = left.partialValue.addingReportingOverflow(right.partialValue)
        let denominator = self.denominator.multipliedReportingOverflow(by: other.denominator)
        guard !sum.overflow, !denominator.overflow else { throw SoundtrackSceneTimingError.overflow }
        return try RationalTime(sum.partialValue, denominator.partialValue)
    }

    public func subtracting(_ other: RationalTime) throws -> RationalTime {
        guard self >= other else { throw SoundtrackSceneTimingError.invalidTime("duration cannot become negative") }
        let left = numerator.multipliedReportingOverflow(by: other.denominator)
        let right = other.numerator.multipliedReportingOverflow(by: denominator)
        let denominator = self.denominator.multipliedReportingOverflow(by: other.denominator)
        guard !left.overflow, !right.overflow, !denominator.overflow else { throw SoundtrackSceneTimingError.overflow }
        return try RationalTime(left.partialValue - right.partialValue, denominator.partialValue)
    }

    public func multiplied(by value: Int64) throws -> RationalTime {
        let result = numerator.multipliedReportingOverflow(by: value)
        guard value >= 0, !result.overflow else { throw SoundtrackSceneTimingError.overflow }
        return try RationalTime(result.partialValue, denominator)
    }

    fileprivate func ceilFrames(at frameRate: Int) throws -> Int64 {
        let product = numerator.multipliedReportingOverflow(by: Int64(frameRate))
        guard !product.overflow else { throw SoundtrackSceneTimingError.overflow }
        let quotient = product.partialValue / denominator
        let remainder = product.partialValue % denominator
        let result = quotient.addingReportingOverflow(remainder == 0 ? 0 : 1)
        guard !result.overflow else { throw SoundtrackSceneTimingError.overflow }
        return result.partialValue
    }

    private static func gcd(_ left: Int64, _ right: Int64) -> Int64 {
        var a = left
        var b = right
        while b != 0 { (a, b) = (b, a % b) }
        return max(a, 1)
    }
}

public enum SoundtrackSceneTimingError: Error, Equatable, LocalizedError, Sendable {
    case invalidSoundtrack(String)
    case soundtrackDigestMismatch(expected: String, actual: String)
    case invalidManifest(String)
    case manifestDigestMismatch(expected: String, actual: String)
    case invalidTime(String)
    case invalidAlignment(expected: RationalTime, actual: RationalTime)
    case invalidFrameRate(Int)
    case overflow

    public var errorDescription: String? {
        switch self {
        case .invalidSoundtrack(let message): "Finished soundtrack is invalid: \(message)"
        case .soundtrackDigestMismatch: "Finished soundtrack bytes no longer match the selected immutable digest."
        case .invalidManifest(let message): "MIDI export manifest is not a verified fixed-tempo timing manifest: \(message)"
        case .manifestDigestMismatch: "MIDI export manifest bytes no longer match the selected immutable digest."
        case .invalidTime(let message): "Invalid scene timing: \(message)"
        case .invalidAlignment: "Bounce lead-in, MIDI duration, and tail do not equal the finished soundtrack duration."
        case .invalidFrameRate: "Output frame rate must be a positive integer."
        case .overflow: "Scene timing exceeds the supported exact rational range."
        }
    }
}

/// A digest-pinned, read-only view of the finished soundtrack. No audio is
/// decoded, rendered, remastered, trimmed, or written by this boundary.
public struct FinishedSoundtrack: Equatable, Sendable {
    public let url: URL
    public let sha256: String
    public let duration: RationalTime

    public static func open(url: URL, expectedSHA256: String) throws -> FinishedSoundtrack {
        guard url.isFileURL,
              (try? url.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true else {
            throw SoundtrackSceneTimingError.invalidSoundtrack("select one regular local file")
        }
        let actualDigest = try digestFile(at: url)
        guard actualDigest == expectedSHA256.lowercased() else {
            throw SoundtrackSceneTimingError.soundtrackDigestMismatch(expected: expectedSHA256.lowercased(), actual: actualDigest)
        }
        let asset = AVURLAsset(url: url)
        let tracks = asset.tracks(withMediaType: .audio)
        guard tracks.count == 1, let track = tracks.first,
              track.timeRange.start == .zero,
              track.timeRange.duration.isValid, track.timeRange.duration.value > 0,
              track.timeRange.duration.timescale > 0 else {
            throw SoundtrackSceneTimingError.invalidSoundtrack("file must contain a readable, non-empty audio stream")
        }
        guard try digestFile(at: url) == actualDigest else {
            throw SoundtrackSceneTimingError.invalidSoundtrack("file changed while reading timing")
        }
        return try FinishedSoundtrack(
            url: url.standardizedFileURL,
            sha256: actualDigest,
            duration: RationalTime(track.timeRange.duration.value, Int64(track.timeRange.duration.timescale))
        )
    }
}

public struct MidiTimingSection: Codable, Equatable, Hashable, Sendable {
    public let occurrenceID: String
    public let label: String
    public let startTick: Int64
    public let endTick: Int64
}

/// The fixed-tempo subset of the immutable MIDI export manifest consumed by a
/// video plan. It intentionally has no MIDI-reader or project-store dependency.
public struct VerifiedMidiTimingManifest: Equatable, Sendable {
    public let sha256: String
    public let snapshotID: String
    public let ppq: Int64
    public let microsecondsPerQuarter: Int64
    public let sections: [MidiTimingSection]

    public static func load(url: URL, expectedSHA256: String) throws -> VerifiedMidiTimingManifest {
        guard url.isFileURL,
              (try? url.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true else {
            throw SoundtrackSceneTimingError.invalidManifest("select one regular local manifest file")
        }
        let data = try Data(contentsOf: url, options: [.mappedIfSafe])
        let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        guard digest == expectedSHA256.lowercased() else {
            throw SoundtrackSceneTimingError.manifestDigestMismatch(expected: expectedSHA256.lowercased(), actual: digest)
        }
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              object["schema"] as? String == "melotrail-midi-export",
              object["manifestSchemaVersion"] as? Int == 1,
              let snapshotID = object["snapshotId"] as? String,
              let authority = object["authority"] as? [String: Any],
              let validation = object["validation"] as? [String: Any],
              validation["status"] as? String == "passed",
              validation["allMIDIFilesPassed"] as? Bool == true,
              let ppq = integer(authority["ppq"]), ppq > 0,
              let tempo = integer(authority["tempoMicrosecondsPerQuarter"]), tempo > 0,
              let rawSections = authority["sections"] as? [[String: Any]] else {
            throw SoundtrackSceneTimingError.invalidManifest("schema, validation, and fixed-tempo authority facts are required")
        }
        let sections = try rawSections.map { raw -> MidiTimingSection in
            guard let id = raw["occurrenceId"] as? String, !id.isEmpty,
                  let label = raw["label"] as? String,
                  let start = integer(raw["startTick"]),
                  let end = integer(raw["endTick"]) else {
                throw SoundtrackSceneTimingError.invalidManifest("every section needs occurrence ID, label, and integer ticks")
            }
            return MidiTimingSection(occurrenceID: id, label: label, startTick: start, endTick: end)
        }
        let result = VerifiedMidiTimingManifest(
            sha256: digest,
            snapshotID: snapshotID,
            ppq: ppq,
            microsecondsPerQuarter: tempo,
            sections: sections
        )
        try result.validate()
        return result
    }

    private static func integer(_ value: Any?) -> Int64? {
        guard let number = value as? NSNumber else { return nil }
        guard CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        let integer = number.int64Value
        return number.doubleValue == Double(integer) ? integer : nil
    }

    public func time(forTick tick: Int64) throws -> RationalTime {
        guard tick >= 0 else { throw SoundtrackSceneTimingError.invalidManifest("ticks cannot be negative") }
        let numerator = tick.multipliedReportingOverflow(by: microsecondsPerQuarter)
        guard !numerator.overflow else { throw SoundtrackSceneTimingError.overflow }
        let denominator = ppq.multipliedReportingOverflow(by: 1_000_000)
        guard !denominator.overflow else { throw SoundtrackSceneTimingError.overflow }
        return try RationalTime(numerator.partialValue, denominator.partialValue)
    }

    public var endTick: Int64 { sections.last?.endTick ?? 0 }

    private func validate() throws {
        guard ppq > 0, microsecondsPerQuarter > 0, !snapshotID.isEmpty, !sections.isEmpty else {
            throw SoundtrackSceneTimingError.invalidManifest("positive PPQ, tempo, snapshot ID, and at least one section are required")
        }
        var previousEnd: Int64 = 0
        var ids = Set<String>()
        for section in sections {
            guard ids.insert(section.occurrenceID).inserted,
                  section.startTick == previousEnd,
                  section.endTick > section.startTick else {
                throw SoundtrackSceneTimingError.invalidManifest("sections must be unique, ordered, positive, and gap-free from tick zero")
            }
            previousEnd = section.endTick
        }
    }
}

public struct BounceAlignment: Codable, Equatable, Hashable, Sendable {
    /// Finished-audio time before MIDI tick zero. This is explicit; it never
    /// causes an audio trim or a silent shift of MIDI timing.
    public let leadIn: RationalTime
    /// Finished-audio time after the final MIDI section. This is explicit and
    /// preserves a bounce's intentional decay/ending.
    public let tail: RationalTime

    public init(leadIn: RationalTime, tail: RationalTime) {
        self.leadIn = leadIn
        self.tail = tail
    }
}

public enum SceneTimingKind: String, Codable, Equatable, Hashable, Sendable { case leadIn, section, tail }

public struct TimedScene: Codable, Equatable, Hashable, Sendable {
    public let id: String
    public let label: String
    public let kind: SceneTimingKind
    public let start: RationalTime
    public let end: RationalTime
    /// Half-open output-frame range. Boundaries use ceil(time × fps), keeping
    /// every frame contiguous and making the final correction deterministic.
    public let startFrame: Int64
    public let endFrame: Int64
}

public struct SoundtrackScenePlan: Codable, Equatable, Sendable {
    public let soundtrackSHA256: String
    public let soundtrackDuration: RationalTime
    public let midiManifestSHA256: String?
    public let alignment: BounceAlignment
    public let frameRate: Int
    public let frameCount: Int64
    public let scenes: [TimedScene]
}

/// The V04a caller-facing resolution boundary. Later composition, preview, and
/// encoding consume this immutable plan; none may independently re-round time.
public enum SoundtrackScenePlanner {
    public static func plan(
        soundtrack: FinishedSoundtrack,
        midiManifest: VerifiedMidiTimingManifest?,
        alignment: BounceAlignment,
        frameRate: Int = 30
    ) throws -> SoundtrackScenePlan {
        let currentDigest = try digestFile(at: soundtrack.url)
        guard currentDigest == soundtrack.sha256 else {
            throw SoundtrackSceneTimingError.soundtrackDigestMismatch(expected: soundtrack.sha256, actual: currentDigest)
        }
        guard frameRate > 0 else { throw SoundtrackSceneTimingError.invalidFrameRate(frameRate) }
        var boundaries: [(id: String, label: String, kind: SceneTimingKind, start: RationalTime, end: RationalTime)] = []
        if let midiManifest {
            let midiDuration = try midiManifest.time(forTick: midiManifest.endTick)
            let expected = try alignment.leadIn.adding(midiDuration).adding(alignment.tail)
            guard expected == soundtrack.duration else {
                throw SoundtrackSceneTimingError.invalidAlignment(expected: expected, actual: soundtrack.duration)
            }
            if alignment.leadIn > .zero {
                boundaries.append(("bounce-lead-in", "Bounce lead-in", .leadIn, .zero, alignment.leadIn))
            }
            for section in midiManifest.sections {
                let start = try alignment.leadIn.adding(midiManifest.time(forTick: section.startTick))
                let end = try alignment.leadIn.adding(midiManifest.time(forTick: section.endTick))
                boundaries.append(("section-\(section.occurrenceID)", section.label, .section, start, end))
            }
            if alignment.tail > .zero {
                boundaries.append(("bounce-tail", "Bounce tail", .tail, try soundtrack.duration.subtracting(alignment.tail), soundtrack.duration))
            }
        } else {
            // Without a MIDI suggestion, the entire immutable soundtrack is one
            // scene. A caller cannot invent an offset or trim a tail here.
            guard alignment.leadIn == .zero, alignment.tail == .zero else {
                throw SoundtrackSceneTimingError.invalidTime("lead-in and tail require a verified MIDI timing manifest")
            }
            boundaries.append(("soundtrack", "Finished soundtrack", .section, .zero, soundtrack.duration))
        }

        var priorEndFrame: Int64 = 0
        let scenes = try boundaries.map { boundary -> TimedScene in
            let endFrame = try boundary.end.ceilFrames(at: frameRate)
            guard endFrame > priorEndFrame else {
                throw SoundtrackSceneTimingError.invalidTime("\(boundary.label) rounds to no output frame")
            }
            let scene = TimedScene(
                id: boundary.id,
                label: boundary.label,
                kind: boundary.kind,
                start: boundary.start,
                end: boundary.end,
                startFrame: priorEndFrame,
                endFrame: endFrame
            )
            priorEndFrame = endFrame
            return scene
        }
        return SoundtrackScenePlan(
            soundtrackSHA256: soundtrack.sha256,
            soundtrackDuration: soundtrack.duration,
            midiManifestSHA256: midiManifest?.sha256,
            alignment: alignment,
            frameRate: frameRate,
            frameCount: priorEndFrame,
            scenes: scenes
        )
    }
}

private func digestFile(at url: URL) throws -> String {
    let handle = try FileHandle(forReadingFrom: url)
    defer { try? handle.close() }
    var digest = SHA256()
    while true {
        let bytes = try handle.read(upToCount: 1_048_576) ?? Data()
        if bytes.isEmpty { break }
        digest.update(data: bytes)
    }
    return digest.finalize().map { String(format: "%02x", $0) }.joined()
}

/// Read-only CLI request: paths and expected digests are chosen explicitly by the caller.
public struct SoundtrackTimingRequest: Decodable, Sendable {
    public let soundtrackPath: String
    public let soundtrackSHA256: String
    public let manifestPath: String?
    public let manifestSHA256: String?
    public let alignment: BounceAlignment
    public let frameRate: Int

    public func resolve() throws -> SoundtrackScenePlan {
        guard (manifestPath == nil) == (manifestSHA256 == nil) else {
            throw SoundtrackSceneTimingError.invalidManifest("manifest path and digest must be supplied together")
        }
        let soundtrack = try FinishedSoundtrack.open(url: URL(fileURLWithPath: soundtrackPath), expectedSHA256: soundtrackSHA256)
        let manifest: VerifiedMidiTimingManifest?
        if let manifestPath, let manifestSHA256 {
            manifest = try VerifiedMidiTimingManifest.load(url: URL(fileURLWithPath: manifestPath), expectedSHA256: manifestSHA256)
        } else { manifest = nil }
        return try SoundtrackScenePlanner.plan(soundtrack: soundtrack, midiManifest: manifest, alignment: alignment, frameRate: frameRate)
    }
}
