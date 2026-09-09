import Darwin
import Foundation

/**
 * Metadata supplied alongside a local import. Pixel dimensions, timing and
 * transparency are measured from the copied bytes; callers cannot assert them
 * into existence through this request.
 */
public struct AssetImportRequest: Codable, Sendable {
    public let identity: AssetIdentity
    public let kind: AssetKind
    public let sourceURL: URL
    public let provenance: AssetProvenance
    public let rights: AssetRights
    public let mask: AssetIdentity?
    public let pivot: AssetPoint?
    public let placementAnchors: [String: AssetPoint]
    public let compatibleSceneVersions: [String]
    public let compatibleIdentityVersions: [String]
    public let identityDifferences: [AssetIdentityDifference]
    public let importedBy: String

    public init(
        identity: AssetIdentity,
        kind: AssetKind,
        sourceURL: URL,
        provenance: AssetProvenance,
        rights: AssetRights,
        mask: AssetIdentity? = nil,
        pivot: AssetPoint? = nil,
        placementAnchors: [String: AssetPoint] = [:],
        compatibleSceneVersions: [String] = [],
        compatibleIdentityVersions: [String] = [],
        identityDifferences: [AssetIdentityDifference] = [],
        importedBy: String
    ) {
        self.identity = identity
        self.kind = kind
        self.sourceURL = sourceURL
        self.provenance = provenance
        self.rights = rights
        self.mask = mask
        self.pivot = pivot
        self.placementAnchors = placementAnchors
        self.compatibleSceneVersions = compatibleSceneVersions
        self.compatibleIdentityVersions = compatibleIdentityVersions
        self.identityDifferences = identityDifferences
        self.importedBy = importedBy
    }
}

public struct AssetImportResult: Sendable {
    public let importedURL: URL
    public let record: AssetRecord

    public init(importedURL: URL, record: AssetRecord) {
        self.importedURL = importedURL
        self.record = record
    }
}

/**
 * Copies one selected local source into the library's immutable `originals/`
 * area. It never edits the selected source, existing media, or a manifest.
 * The caller must publish the returned record in a separate new manifest
 * snapshot after review.
 */
public enum AssetKitImporter {
    public static func importOriginal(_ request: AssetImportRequest, into libraryRoot: URL, now: Date = Date()) throws -> AssetImportResult {
        let source = request.sourceURL.standardizedFileURL.resolvingSymlinksInPath()
        guard FileManager.default.fileExists(atPath: source.path),
              (try? source.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true else {
            throw AssetManifestError.unreadableManifest("import source is not a regular file")
        }
        guard !request.importedBy.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw AssetManifestError.invalidManifest("import provenance is required")
        }
        try validateImportMetadata(request)
        let safeAssetID = safeComponent(request.identity.assetID)
        let safeVersion = safeComponent(request.identity.version)
        guard !safeAssetID.isEmpty, !safeVersion.isEmpty else {
            throw AssetManifestError.invalidManifest("import asset identity and version require path-safe content")
        }

        guard libraryRoot.isFileURL else {
            throw AssetManifestError.invalidManifest("asset library must be a local directory")
        }
        // Resolve existing ancestors before appending a not-yet-created library.
        let root = libraryRoot.standardizedFileURL.pathComponents.dropFirst().reduce(URL(fileURLWithPath: "/")) {
            $0.appendingPathComponent($1).resolvingSymlinksInPath()
        }
        guard root.path != "/" else {
            throw AssetManifestError.invalidManifest("asset library root cannot be the filesystem root")
        }
        guard enclosingGitRepository(for: root) == nil else {
            throw AssetManifestError.invalidManifest("asset library root must be outside Git repositories and worktrees")
        }
        let destination = root
            .appending(path: "originals", directoryHint: .isDirectory)
            .appending(path: safeAssetID, directoryHint: .isDirectory)
            .appending(path: safeVersion, directoryHint: .isDirectory)
            .appending(path: safeFileName(source.lastPathComponent), directoryHint: .notDirectory)
        guard destination.path.hasPrefix(root.path.hasSuffix("/") ? root.path : root.path + "/") else {
            throw AssetManifestError.unsafeMediaPath(request.identity, destination.path)
        }
        var ancestor = root
        for component in ["originals", safeAssetID, safeVersion] {
            ancestor.appendPathComponent(component)
            if (try? ancestor.resourceValues(forKeys: [.isSymbolicLinkKey]).isSymbolicLink) == true {
                throw AssetManifestError.unsafeMediaPath(request.identity, ancestor.path)
            }
        }
        let versionDirectory = destination.deletingLastPathComponent()
        guard !FileManager.default.fileExists(atPath: versionDirectory.path) else {
            throw AssetManifestError.invalidManifest("import asset identity/version already exists; use a new asset version")
        }

        try FileManager.default.createDirectory(at: versionDirectory, withIntermediateDirectories: true)
        defer {
            if !FileManager.default.fileExists(atPath: destination.path) {
                try? FileManager.default.removeItem(at: versionDirectory)
            }
        }
        let staged = destination.deletingLastPathComponent().appendingPathComponent(".asset-import-\(UUID().uuidString).tmp")
        defer { try? FileManager.default.removeItem(at: staged) }
        try FileManager.default.copyItem(at: source, to: staged)
        // Probe the staged copy before publishing, so an unreadable file never
        // becomes a library original merely because it was copied successfully.
        let facts = try AssetMediaInspector.inspect(kind: request.kind, url: staged)
        let record = AssetRecord(
            identity: request.identity,
            kind: request.kind,
            relativeMediaPath: relativePath(of: destination, beneath: root),
            sha256: try AssetDigest.sha256(of: staged),
            provenance: request.provenance,
            rights: request.rights,
            geometry: AssetGeometry(
                width: facts.width,
                height: facts.height,
                frameRate: facts.frameRate,
                durationSeconds: facts.durationSeconds,
                alpha: facts.alpha,
                mask: request.mask,
                pivot: request.pivot,
                placementAnchors: request.placementAnchors,
                compatibleSceneVersions: request.compatibleSceneVersions,
                compatibleIdentityVersions: request.compatibleIdentityVersions
            ),
            // Import is evidence collection, not an approval action.
            approval: AssetApproval(state: .proposed, decidedBy: request.importedBy, decidedAt: now),
            identityDifferences: request.identityDifferences
        )
        guard link(staged.path, destination.path) == 0 else {
            throw AssetManifestError.invalidManifest("cannot publish imported original without overwriting: \(String(cString: strerror(errno)))")
        }
        return AssetImportResult(importedURL: destination, record: record)
    }

    private static func safeComponent(_ value: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_."))
        let result = value.unicodeScalars.map { allowed.contains($0) ? Character(String($0)) : "-" }
        return String(result).trimmingCharacters(in: CharacterSet(charactersIn: ".-"))
    }

    private static func safeFileName(_ value: String) -> String {
        let name = safeComponent(value)
        return name.isEmpty ? "asset.bin" : name
    }

    private static func relativePath(of url: URL, beneath root: URL) -> String {
        String(url.path.dropFirst(root.path.count + (root.path.hasSuffix("/") ? 0 : 1)))
    }

    private static func enclosingGitRepository(for url: URL) -> URL? {
        // Bridged Foundation URLs can append /.. when deleting the root.
        // Walk a finite component list rather than relying on URL equality.
        var components = url.standardizedFileURL.pathComponents
        while !components.isEmpty {
            let candidate = URL(fileURLWithPath: NSString.path(withComponents: components), isDirectory: true)
            let bareHead = candidate.appendingPathComponent("HEAD")
            let bareObjects = candidate.appendingPathComponent("objects")
            let bareRefs = candidate.appendingPathComponent("refs")
            let isBareRepository = (try? bareHead.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
                && (try? bareObjects.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true
                && (try? bareRefs.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true
            if FileManager.default.fileExists(atPath: candidate.appending(path: ".git").path) || isBareRepository {
                return candidate
            }
            components.removeLast()
        }
        return nil
    }

    private static func validateImportMetadata(_ request: AssetImportRequest) throws {
        guard !request.identity.assetID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !request.identity.version.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw AssetManifestError.invalidManifest("asset identity and version are required")
        }
        guard !request.provenance.originalSource.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !request.provenance.creator.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw AssetManifestError.invalidManifest("provenance source and creator are required")
        }
        if request.provenance.creationMethod == .generated && request.provenance.generated == nil {
            throw AssetManifestError.invalidManifest("generated provenance is required")
        }
        if let generated = request.provenance.generated,
           [generated.provider, generated.model, generated.prompt].contains(where: { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) {
            throw AssetManifestError.invalidManifest("generated provider, model and prompt are required")
        }
        guard !request.rights.ownershipOrLicense.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !request.rights.permittedUses.isEmpty,
              !request.rights.permittedUses.contains(where: { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) else {
            throw AssetManifestError.invalidManifest("rights and permitted uses are required")
        }
        let points = Array(request.placementAnchors.values) + [request.pivot].compactMap { $0 }
        guard !points.contains(where: { !$0.x.isFinite || !$0.y.isFinite || $0.x < 0 || $0.x > 1 || $0.y < 0 || $0.y > 1 }) else {
            throw AssetManifestError.invalidManifest("pivot and anchor coordinates must be finite normalized values")
        }
        guard !request.identityDifferences.contains(where: {
            $0.field.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            $0.referenceDescription.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            $0.observedDescription.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }) else {
            throw AssetManifestError.invalidManifest("identity difference evidence requires field, reference and observed values")
        }
    }
}

public struct AssetKitSceneRequest: Codable, Sendable {
    public let sceneVersion: String
    public let identityVersion: String
    public let assetPins: [AssetIdentity]

    public init(sceneVersion: String, identityVersion: String, assetPins: [AssetIdentity]) {
        self.sceneVersion = sceneVersion
        self.identityVersion = identityVersion
        self.assetPins = assetPins
    }
}

public enum AssetKitInspectionCode: String, Codable, Sendable {
    case manifestInvalid
    case missingPin
    case duplicatePin
    case notApproved
    case sceneIncompatible
    case identityIncompatible
    case unresolvedIdentityDifference
}

public struct AssetKitInspectionFinding: Codable, Sendable {
    public let code: AssetKitInspectionCode
    public let identity: AssetIdentity?
    public let message: String

    public init(code: AssetKitInspectionCode, identity: AssetIdentity? = nil, message: String) {
        self.code = code
        self.identity = identity
        self.message = message
    }
}

public struct AssetKitInspectionReport: Codable, Sendable {
    public let request: AssetKitSceneRequest
    public let inspectedAssets: [AssetRecord]
    public let findings: [AssetKitInspectionFinding]

    public var requiresHumanIdentityReview: Bool {
        findings.contains { $0.code == .unresolvedIdentityDifference }
    }

    public var isCompositionReady: Bool {
        findings.isEmpty
    }
}

/** Reports all scene/identity compatibility findings before composition can pin a kit. */
public enum AssetKitInspector {
    public static func inspect(_ library: AssetLibrary, request: AssetKitSceneRequest) -> AssetKitInspectionReport {
        var findings = AssetManifestValidator.validate(library.manifest, libraryRoot: library.rootURL).issues.map {
            AssetKitInspectionFinding(code: .manifestInvalid, message: $0.localizedDescription)
        }
        var inspected: [AssetRecord] = []
        var pins = Set<AssetIdentity>()
        for pin in request.assetPins {
            guard pins.insert(pin).inserted else {
                findings.append(AssetKitInspectionFinding(code: .duplicatePin, identity: pin, message: "Asset pin is duplicated."))
                continue
            }
            let matches = library.manifest.assets.filter { $0.identity == pin }
            guard matches.count == 1, let asset = matches.first else {
                findings.append(AssetKitInspectionFinding(code: .missingPin, identity: pin, message: "Asset pin is missing or ambiguous."))
                continue
            }
            inspected.append(asset)
            if asset.approval.state != .approved {
                findings.append(AssetKitInspectionFinding(code: .notApproved, identity: pin, message: "Asset is \(asset.approval.state.rawValue), not approved."))
            }
            if !asset.geometry.compatibleSceneVersions.contains(request.sceneVersion) {
                findings.append(AssetKitInspectionFinding(code: .sceneIncompatible, identity: pin, message: "Asset is not compatible with scene \(request.sceneVersion)."))
            }
            if !asset.geometry.compatibleIdentityVersions.contains(request.identityVersion) {
                findings.append(AssetKitInspectionFinding(code: .identityIncompatible, identity: pin, message: "Asset is not compatible with identity \(request.identityVersion)."))
            }
            for difference in asset.identityDifferences where !difference.resolved {
                findings.append(AssetKitInspectionFinding(
                    code: .unresolvedIdentityDifference,
                    identity: pin,
                    message: "Unresolved \(difference.field): reference \(difference.referenceDescription); observed \(difference.observedDescription)."
                ))
            }
        }
        return AssetKitInspectionReport(request: request, inspectedAssets: inspected, findings: findings)
    }
}
