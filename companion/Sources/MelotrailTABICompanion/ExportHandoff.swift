import Foundation

/// Versioned launch contract. Only the immutable manifest is received from the MIDI app.
/// Soundtrack selection and explicit bounce alignment belong to this companion.
public struct ExportHandoff: Sendable {
    public static let capability = "melotrail-tabi-export-handoff-v1-manifest-v2"
    public let manifestURL: URL
    public let manifestSHA256: String
    public let snapshotID: String

    public init(arguments: [String]) throws {
        guard arguments.count == 4, arguments[0] == "--midi-export",
              arguments[1].hasPrefix("/"), arguments[2].count == 64,
              arguments[2].allSatisfy({ $0.isHexDigit }), !arguments[3].isEmpty else {
            throw ExportHandoffError.invalidRequest
        }
        manifestURL = URL(fileURLWithPath: arguments[1])
        manifestSHA256 = arguments[2].lowercased()
        snapshotID = arguments[3]
    }

    public func verify() throws -> VerifiedMidiTimingManifest {
        let manifest = try VerifiedMidiTimingManifest.load(url: manifestURL, expectedSHA256: manifestSHA256)
        guard manifest.snapshotID == snapshotID else { throw ExportHandoffError.wrongSnapshot }
        return manifest
    }

    public func selectSoundtrack(_ url: URL) throws -> FinishedSoundtrack {
        _ = try verify()
        return try FinishedSoundtrack.open(url: url, expectedSHA256: AssetDigest.sha256(of: url))
    }

    public func timingRequest(soundtrack: FinishedSoundtrack, alignment: BounceAlignment) throws -> SoundtrackTimingRequest {
        _ = try verify()
        let request = SoundtrackTimingRequest(
            soundtrackPath: soundtrack.url.path, soundtrackSHA256: soundtrack.sha256,
            manifestPath: manifestURL.path, manifestSHA256: manifestSHA256,
            alignment: alignment, frameRate: 30
        )
        _ = try request.resolve()
        return request
    }

    /// Opening a prepared composition must not silently switch the selected music or snapshot.
    public func validateComposition(_ request: SceneCompositionRequest, timing: SoundtrackTimingRequest) throws {
        _ = try verify()
        guard timing.manifestPath == manifestURL.path, timing.manifestSHA256 == manifestSHA256,
              request.timing == timing else { throw ExportHandoffError.wrongComposition }
        _ = try request.timing.resolve()
    }

    public func saveTiming(_ request: SoundtrackTimingRequest, to destination: URL) throws {
        _ = try verify()
        guard request.manifestPath == manifestURL.path, request.manifestSHA256 == manifestSHA256 else {
            throw ExportHandoffError.wrongComposition
        }
        _ = try request.resolve()
        try Self.requireSeparateDestination(destination, manifestURL: manifestURL)
        guard destination.pathExtension == "json",
              destination.resolvingSymlinksInPath() != URL(fileURLWithPath: request.soundtrackPath).resolvingSymlinksInPath() else {
            throw ExportHandoffError.unsafeDestination
        }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        try encoder.encode(request).write(to: destination, options: .withoutOverwriting)
    }

    /// Reject all MIDI project descendants and snapshot descendants, including aliases and new subfolders.
    static func requireSeparateDestination(_ destination: URL, manifestURL: URL?) throws {
        guard destination.isFileURL else { throw ExportHandoffError.unsafeDestination }
        func canonical(_ url: URL) -> URL {
            url.standardizedFileURL.pathComponents.dropFirst().reduce(URL(fileURLWithPath: "/")) {
                $0.appendingPathComponent($1).resolvingSymlinksInPath()
            }
        }
        let target = canonical(destination)
        if let manifestURL {
            let snapshot = canonical(manifestURL).deletingLastPathComponent().path
            guard target.path != snapshot, !target.path.hasPrefix(snapshot + "/") else {
                throw ExportHandoffError.unsafeDestination
            }
        }
        var ancestor = target.deletingLastPathComponent()
        while true {
            if FileManager.default.fileExists(atPath: ancestor.appendingPathComponent("project.json").path) {
                throw ExportHandoffError.unsafeDestination
            }
            let parent = ancestor.deletingLastPathComponent()
            if parent.path == ancestor.path { break }
            ancestor = parent
        }
    }
}

public enum ExportHandoffError: LocalizedError {
    case invalidRequest, wrongSnapshot, wrongComposition, unsafeDestination
    public var errorDescription: String? {
        switch self {
        case .invalidRequest: "Expected --midi-export <absolute manifest path> <SHA-256> <snapshot ID>."
        case .wrongSnapshot: "The manifest does not identify the selected export snapshot. Reopen Export and retry."
        case .wrongComposition: "Choose a composition prepared with this exact soundtrack, export and confirmed timing."
        case .unsafeDestination: "Choose a new companion JSON file outside MIDI projects, export snapshots and source media."
        }
    }
}
