import AVFoundation
import CryptoKit
import Darwin
import Foundation
import ImageIO

/**
 * The companion's asset-library boundary.
 *
 * Media itself remains in a user-selected library outside this repository. A
 * manifest records immutable, relative references to that media; composition
 * code must validate the bytes and pin an approved identity before use.
 */
public struct AssetIdentity: Codable, Hashable, Sendable {
    public let assetID: String
    public let version: String

    public init(assetID: String, version: String) {
        self.assetID = assetID
        self.version = version
    }
}

public enum AssetKind: String, Codable, Sendable {
    case reference
    case image
    case layer
    case mask
    case prop
    case animationClip
    case video

    fileprivate var usesVideoProbe: Bool {
        self == .animationClip || self == .video
    }
}

public enum AssetCreationMethod: String, Codable, Sendable {
    case owned
    case licensed
    case userProvided
    case generated
}

public struct GeneratedAssetProvenance: Codable, Hashable, Sendable {
    public let provider: String
    public let model: String
    public let prompt: String
    public let referenceAssets: [AssetIdentity]
    public let seed: String?

    public init(provider: String, model: String, prompt: String, referenceAssets: [AssetIdentity], seed: String? = nil) {
        self.provider = provider
        self.model = model
        self.prompt = prompt
        self.referenceAssets = referenceAssets
        self.seed = seed
    }
}

public struct AssetProvenance: Codable, Hashable, Sendable {
    public let originalSource: String
    public let creator: String
    public let creationMethod: AssetCreationMethod
    public let createdAt: Date
    public let generated: GeneratedAssetProvenance?

    public init(
        originalSource: String,
        creator: String,
        creationMethod: AssetCreationMethod,
        createdAt: Date,
        generated: GeneratedAssetProvenance? = nil
    ) {
        self.originalSource = originalSource
        self.creator = creator
        self.creationMethod = creationMethod
        self.createdAt = createdAt
        self.generated = generated
    }
}

public struct AssetRights: Codable, Hashable, Sendable {
    public let ownershipOrLicense: String
    public let permittedUses: [String]
    public let attribution: String?
    public let restrictions: [String]

    public init(ownershipOrLicense: String, permittedUses: [String], attribution: String? = nil, restrictions: [String] = []) {
        self.ownershipOrLicense = ownershipOrLicense
        self.permittedUses = permittedUses
        self.attribution = attribution
        self.restrictions = restrictions
    }
}

public enum AssetAlpha: String, Codable, Sendable {
    case present
    case absent
    case unknown
}

public struct AssetPoint: Codable, Hashable, Sendable {
    public let x: Double
    public let y: Double

    public init(x: Double, y: Double) {
        self.x = x
        self.y = y
    }
}

public struct AssetGeometry: Codable, Hashable, Sendable {
    public let width: Int
    public let height: Int
    public let frameRate: Double?
    public let durationSeconds: Double?
    public let alpha: AssetAlpha
    public let mask: AssetIdentity?
    public let pivot: AssetPoint?
    public let placementAnchors: [String: AssetPoint]
    public let compatibleSceneVersions: [String]
    public let compatibleIdentityVersions: [String]

    public init(
        width: Int,
        height: Int,
        frameRate: Double? = nil,
        durationSeconds: Double? = nil,
        alpha: AssetAlpha,
        mask: AssetIdentity? = nil,
        pivot: AssetPoint? = nil,
        placementAnchors: [String: AssetPoint] = [:],
        compatibleSceneVersions: [String] = [],
        compatibleIdentityVersions: [String] = []
    ) {
        self.width = width
        self.height = height
        self.frameRate = frameRate
        self.durationSeconds = durationSeconds
        self.alpha = alpha
        self.mask = mask
        self.pivot = pivot
        self.placementAnchors = placementAnchors
        self.compatibleSceneVersions = compatibleSceneVersions
        self.compatibleIdentityVersions = compatibleIdentityVersions
    }
}

public enum AssetApprovalState: String, Codable, Sendable {
    case proposed
    case approved
    case rejected
}

public struct AssetApproval: Codable, Hashable, Sendable {
    public let state: AssetApprovalState
    public let decidedBy: String
    public let decidedAt: Date
    public let reason: String?

    public init(state: AssetApprovalState, decidedBy: String, decidedAt: Date, reason: String? = nil) {
        self.state = state
        self.decidedBy = decidedBy
        self.decidedAt = decidedAt
        self.reason = reason
    }
}

/**
 * A concrete difference observed while comparing a source reference with a
 * candidate TABI asset. These findings deliberately remain review evidence:
 * recording one never approves or rewrites the candidate.
 */
public struct AssetIdentityDifference: Codable, Hashable, Sendable {
    public let field: String
    public let referenceDescription: String
    public let observedDescription: String
    public let resolved: Bool

    public init(field: String, referenceDescription: String, observedDescription: String, resolved: Bool = false) {
        self.field = field
        self.referenceDescription = referenceDescription
        self.observedDescription = observedDescription
        self.resolved = resolved
    }
}

public struct AssetRecord: Codable, Hashable, Sendable {
    public let identity: AssetIdentity
    public let kind: AssetKind
    /// A relative path beneath the selected asset-library root, never a project path.
    public let relativeMediaPath: String
    public let sha256: String
    public let provenance: AssetProvenance
    public let rights: AssetRights
    public let geometry: AssetGeometry
    public let approval: AssetApproval
    /// Reference-versus-candidate observations that still require a human TABI decision.
    public let identityDifferences: [AssetIdentityDifference]

    public init(
        identity: AssetIdentity,
        kind: AssetKind,
        relativeMediaPath: String,
        sha256: String,
        provenance: AssetProvenance,
        rights: AssetRights,
        geometry: AssetGeometry,
        approval: AssetApproval,
        identityDifferences: [AssetIdentityDifference] = []
    ) {
        self.identity = identity
        self.kind = kind
        self.relativeMediaPath = relativeMediaPath
        self.sha256 = sha256
        self.provenance = provenance
        self.rights = rights
        self.geometry = geometry
        self.approval = approval
        self.identityDifferences = identityDifferences
    }

    private enum CodingKeys: String, CodingKey {
        case identity, kind, relativeMediaPath, sha256, provenance, rights, geometry, approval, identityDifferences
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        identity = try container.decode(AssetIdentity.self, forKey: .identity)
        kind = try container.decode(AssetKind.self, forKey: .kind)
        relativeMediaPath = try container.decode(String.self, forKey: .relativeMediaPath)
        sha256 = try container.decode(String.self, forKey: .sha256)
        provenance = try container.decode(AssetProvenance.self, forKey: .provenance)
        rights = try container.decode(AssetRights.self, forKey: .rights)
        geometry = try container.decode(AssetGeometry.self, forKey: .geometry)
        approval = try container.decode(AssetApproval.self, forKey: .approval)
        identityDifferences = try container.decodeIfPresent([AssetIdentityDifference].self, forKey: .identityDifferences) ?? []
    }
}

public struct AssetManifest: Codable, Hashable, Sendable {
    public static let currentSchemaVersion = 1

    public let schemaVersion: Int
    public let libraryID: String
    public let assets: [AssetRecord]

    public init(schemaVersion: Int = AssetManifest.currentSchemaVersion, libraryID: String, assets: [AssetRecord]) {
        self.schemaVersion = schemaVersion
        self.libraryID = libraryID
        self.assets = assets
    }
}

public enum AssetManifestError: Error, Equatable, LocalizedError, Sendable {
    case unreadableManifest(String)
    case unsupportedSchema(Int)
    case invalidManifest(String)
    case missingMedia(AssetIdentity, String)
    case unsafeMediaPath(AssetIdentity, String)
    case nonRegularMedia(AssetIdentity, String)
    case digestMismatch(AssetIdentity, expected: String, actual: String)
    case dimensionMismatch(AssetIdentity, expectedWidth: Int, expectedHeight: Int, actualWidth: Int, actualHeight: Int)
    case metadataMismatch(AssetIdentity, String)
    case alphaMismatch(AssetIdentity, declared: AssetAlpha, actual: AssetAlpha)
    case missingMask(AssetIdentity, AssetIdentity)
    case invalidMask(AssetIdentity, AssetIdentity, String)
    case assetNotFound(AssetIdentity)
    case assetNotApproved(AssetIdentity, AssetApprovalState)

    public var errorDescription: String? {
        switch self {
        case .unreadableManifest(let message): "Cannot read asset manifest: \(message)"
        case .unsupportedSchema(let version): "Asset manifest schema \(version) is unsupported."
        case .invalidManifest(let message): "Invalid asset manifest: \(message)"
        case .missingMedia(let identity, let path): "Missing media for \(identity.assetID)@\(identity.version): \(path)"
        case .unsafeMediaPath(let identity, let path): "Unsafe media path for \(identity.assetID)@\(identity.version): \(path)"
        case .nonRegularMedia(let identity, let path): "Media for \(identity.assetID)@\(identity.version) is not a regular file: \(path)"
        case .digestMismatch(let identity, let expected, let actual): "Digest mismatch for \(identity.assetID)@\(identity.version): expected \(expected), got \(actual)"
        case .dimensionMismatch(let identity, let expectedWidth, let expectedHeight, let actualWidth, let actualHeight): "Dimensions differ for \(identity.assetID)@\(identity.version): expected \(expectedWidth)x\(expectedHeight), got \(actualWidth)x\(actualHeight)"
        case .metadataMismatch(let identity, let message): "Media metadata differs for \(identity.assetID)@\(identity.version): \(message)"
        case .alphaMismatch(let identity, let declared, let actual): "Transparency differs for \(identity.assetID)@\(identity.version): declared \(declared.rawValue), inspected \(actual.rawValue)"
        case .missingMask(let identity, let mask): "Mask \(mask.assetID)@\(mask.version) for \(identity.assetID)@\(identity.version) is missing."
        case .invalidMask(let identity, let mask, let message): "Mask \(mask.assetID)@\(mask.version) for \(identity.assetID)@\(identity.version) is invalid: \(message)"
        case .assetNotFound(let identity): "No asset exists for \(identity.assetID)@\(identity.version)."
        case .assetNotApproved(let identity, let state): "Asset \(identity.assetID)@\(identity.version) is \(state.rawValue), not approved."
        }
    }
}

public struct AssetManifestValidation: Sendable {
    public let issues: [AssetManifestError]

    public init(issues: [AssetManifestError]) {
        self.issues = issues
    }

    public var isValid: Bool { issues.isEmpty }

    public func requireValid() throws {
        if let first = issues.first { throw first }
    }
}

public enum AssetDigest {
    public static func sha256(of url: URL) throws -> String {
        let handle: FileHandle
        do {
            handle = try FileHandle(forReadingFrom: url)
        } catch {
            throw AssetManifestError.unreadableManifest(error.localizedDescription)
        }
        defer { try? handle.close() }

        var hasher = SHA256()
        while true {
            let chunk = try handle.read(upToCount: 1_048_576) ?? Data()
            if chunk.isEmpty { break }
            hasher.update(data: chunk)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }
}

/** Facts measured from the imported bytes, never inferred from a filename. */
public struct AssetMediaFacts: Hashable, Sendable {
    public let width: Int
    public let height: Int
    public let frameRate: Double?
    public let durationSeconds: Double?
    /// `present` means at least one decoded pixel is non-opaque, not merely that a PNG declares an alpha channel.
    public let alpha: AssetAlpha

    public init(width: Int, height: Int, frameRate: Double?, durationSeconds: Double?, alpha: AssetAlpha) {
        self.width = width
        self.height = height
        self.frameRate = frameRate
        self.durationSeconds = durationSeconds
        self.alpha = alpha
    }
}

public enum AssetMediaInspector {
    public static func inspect(kind: AssetKind, url: URL) throws -> AssetMediaFacts {
        if kind.usesVideoProbe {
            let asset = AVURLAsset(url: url)
            guard let videoTrack = asset.tracks(withMediaType: .video).first else {
                throw AssetManifestError.unreadableManifest("no video track")
            }
            let size = videoTrack.naturalSize.applying(videoTrack.preferredTransform)
            // The current AVFoundation probe cannot truthfully establish a video alpha plane.
            return AssetMediaFacts(
                width: Int(abs(size.width.rounded())),
                height: Int(abs(size.height.rounded())),
                frameRate: Double(videoTrack.nominalFrameRate),
                durationSeconds: asset.duration.seconds,
                alpha: .unknown
            )
        }
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            throw AssetManifestError.unreadableManifest("not a readable still image")
        }
        let hasTransparency = try hasTransparentPixel(in: image)
        return AssetMediaFacts(
            width: image.width,
            height: image.height,
            frameRate: nil,
            durationSeconds: nil,
            alpha: hasTransparency ? .present : .absent
        )
    }

    private static func hasTransparentPixel(in image: CGImage) throws -> Bool {
        let alphaInfo = image.alphaInfo
        guard alphaInfo != .none && alphaInfo != .noneSkipFirst && alphaInfo != .noneSkipLast else {
            return false
        }
        let pixelCount = image.width.multipliedReportingOverflow(by: image.height)
        guard !pixelCount.overflow, pixelCount.partialValue <= 100_000_000 else {
            throw AssetManifestError.unreadableManifest("image is too large for bounded alpha inspection")
        }
        let byteCount = pixelCount.partialValue.multipliedReportingOverflow(by: 4)
        guard !byteCount.overflow else {
            throw AssetManifestError.unreadableManifest("image is too large for bounded alpha inspection")
        }
        let bytes = UnsafeMutableRawPointer.allocate(byteCount: byteCount.partialValue, alignment: MemoryLayout<UInt8>.alignment)
        defer { bytes.deallocate() }
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let context = CGContext(
            data: bytes,
            width: image.width,
            height: image.height,
            bitsPerComponent: 8,
            bytesPerRow: image.width * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
        ) else {
            throw AssetManifestError.unreadableManifest("cannot decode alpha pixels")
        }
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        let alphaBytes = bytes.bindMemory(to: UInt8.self, capacity: byteCount.partialValue)
        for index in stride(from: 3, to: byteCount.partialValue, by: 4) where alphaBytes[index] < UInt8.max {
            return true
        }
        return false
    }
}

public enum AssetManifestStore {
    public static func load(from url: URL) throws -> AssetManifest {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        do {
            let manifest = try decoder.decode(AssetManifest.self, from: Data(contentsOf: url))
            guard manifest.schemaVersion == AssetManifest.currentSchemaVersion else {
                throw AssetManifestError.unsupportedSchema(manifest.schemaVersion)
            }
            return manifest
        } catch let error as AssetManifestError {
            throw error
        } catch {
            throw AssetManifestError.unreadableManifest(error.localizedDescription)
        }
    }

    public static func save(_ manifest: AssetManifest, to url: URL) throws {
        guard manifest.schemaVersion == AssetManifest.currentSchemaVersion else {
            throw AssetManifestError.unsupportedSchema(manifest.schemaVersion)
        }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        // Each manifest is an immutable snapshot: publishing a revision requires a new path.
        let staged = url.deletingLastPathComponent().appendingPathComponent(".asset-manifest-\(UUID().uuidString).tmp")
        defer { try? FileManager.default.removeItem(at: staged) }
        try encoder.encode(manifest).write(to: staged, options: .withoutOverwriting)
        guard link(staged.path, url.path) == 0 else {
            throw AssetManifestError.invalidManifest("Cannot publish a new manifest without overwriting: \(String(cString: strerror(errno)))")
        }
    }
}

public enum AssetManifestValidator {
    public static func validate(_ manifest: AssetManifest, libraryRoot: URL) -> AssetManifestValidation {
        var issues: [AssetManifestError] = []
        guard manifest.schemaVersion == AssetManifest.currentSchemaVersion else {
            return AssetManifestValidation(issues: [.unsupportedSchema(manifest.schemaVersion)])
        }
        guard !manifest.libraryID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return AssetManifestValidation(issues: [.invalidManifest("libraryID is required")])
        }

        var identities = Set<AssetIdentity>()
        for asset in manifest.assets {
            if !identities.insert(asset.identity).inserted {
                issues.append(.invalidManifest("duplicate identity \(asset.identity.assetID)@\(asset.identity.version)"))
            }
            issues.append(contentsOf: validateRecord(asset, libraryRoot: libraryRoot))
        }
        issues.append(contentsOf: validateMasks(in: manifest))
        return AssetManifestValidation(issues: issues)
    }

    fileprivate static func approvedAsset(_ identity: AssetIdentity, in manifest: AssetManifest) throws -> AssetRecord {
        let matches = manifest.assets.filter { $0.identity == identity }
        guard matches.count == 1, let asset = matches.first else {
            throw matches.isEmpty ? AssetManifestError.assetNotFound(identity) : AssetManifestError.invalidManifest("duplicate identity \(identity.assetID)@\(identity.version)")
        }
        guard asset.approval.state == .approved else {
            throw AssetManifestError.assetNotApproved(identity, asset.approval.state)
        }
        return asset
    }

    private static func validateRecord(_ asset: AssetRecord, libraryRoot: URL) -> [AssetManifestError] {
        var issues = validateRequiredFields(asset)
        guard let mediaURL = confinedMediaURL(relativePath: asset.relativeMediaPath, libraryRoot: libraryRoot) else {
            issues.append(.unsafeMediaPath(asset.identity, asset.relativeMediaPath))
            return issues
        }
        guard FileManager.default.fileExists(atPath: mediaURL.path) else {
            issues.append(.missingMedia(asset.identity, asset.relativeMediaPath))
            return issues
        }
        let attributes = try? FileManager.default.attributesOfItem(atPath: mediaURL.path)
        guard let mediaType = attributes?[.type] as? FileAttributeType, mediaType == .typeRegular else {
            issues.append(.nonRegularMedia(asset.identity, asset.relativeMediaPath))
            return issues
        }

        do {
            let actualDigest = try AssetDigest.sha256(of: mediaURL)
            if actualDigest != asset.sha256.lowercased() {
                issues.append(.digestMismatch(asset.identity, expected: asset.sha256.lowercased(), actual: actualDigest))
            }
        } catch let error as AssetManifestError {
            issues.append(error)
        } catch {
            issues.append(.unreadableManifest(error.localizedDescription))
        }

        do {
            let facts = try AssetMediaInspector.inspect(kind: asset.kind, url: mediaURL)
            if facts.width != asset.geometry.width || facts.height != asset.geometry.height {
                issues.append(.dimensionMismatch(
                    asset.identity,
                    expectedWidth: asset.geometry.width,
                    expectedHeight: asset.geometry.height,
                    actualWidth: facts.width,
                    actualHeight: facts.height
                ))
            }
            if asset.geometry.alpha != .unknown && facts.alpha != .unknown && asset.geometry.alpha != facts.alpha {
                issues.append(.alphaMismatch(asset.identity, declared: asset.geometry.alpha, actual: facts.alpha))
            }
            if let expectedFrameRate = asset.geometry.frameRate {
                if let actualFrameRate = facts.frameRate {
                    if abs(expectedFrameRate - actualFrameRate) > 0.001 {
                        issues.append(.metadataMismatch(asset.identity, "frame rate expected \(expectedFrameRate), got \(actualFrameRate)"))
                    }
                } else {
                    issues.append(.metadataMismatch(asset.identity, "frame rate expected \(expectedFrameRate), but the media probe reported no frame rate"))
                }
            }
            if let expectedDuration = asset.geometry.durationSeconds {
                if let actualDuration = facts.durationSeconds {
                    if abs(expectedDuration - actualDuration) > 0.001 {
                        issues.append(.metadataMismatch(asset.identity, "duration expected \(expectedDuration), got \(actualDuration)"))
                    }
                } else {
                    issues.append(.metadataMismatch(asset.identity, "duration expected \(expectedDuration), but the media probe reported no duration"))
                }
            }
        } catch {
            issues.append(.metadataMismatch(asset.identity, error.localizedDescription))
        }
        return issues
    }

    private static func validateRequiredFields(_ asset: AssetRecord) -> [AssetManifestError] {
        var issues: [AssetManifestError] = []
        if asset.identity.assetID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || asset.identity.version.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            issues.append(.invalidManifest("asset identity and version are required"))
        }
        if asset.sha256.range(of: "^[0-9a-fA-F]{64}$", options: .regularExpression) == nil {
            issues.append(.invalidManifest("SHA-256 is required for \(asset.identity.assetID)@\(asset.identity.version)"))
        }
        if asset.provenance.originalSource.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || asset.provenance.creator.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            issues.append(.invalidManifest("provenance source and creator are required for \(asset.identity.assetID)@\(asset.identity.version)"))
        }
        if asset.provenance.creationMethod == .generated && asset.provenance.generated == nil {
            issues.append(.invalidManifest("generated provenance is required for \(asset.identity.assetID)@\(asset.identity.version)"))
        }
        if let generated = asset.provenance.generated,
           [generated.provider, generated.model, generated.prompt].contains(where: { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) {
            issues.append(.invalidManifest("generated provider, model and prompt are required"))
        }
        let points = Array(asset.geometry.placementAnchors.values) + [asset.geometry.pivot].compactMap { $0 }
        if points.contains(where: { !$0.x.isFinite || !$0.y.isFinite || $0.x < 0 || $0.x > 1 || $0.y < 0 || $0.y > 1 }) {
            issues.append(.invalidManifest("pivot and anchor coordinates must be finite normalized values"))
        }
        if asset.rights.ownershipOrLicense.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || asset.rights.permittedUses.isEmpty || asset.rights.permittedUses.contains(where: { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) {
            issues.append(.invalidManifest("rights and permitted uses are required for \(asset.identity.assetID)@\(asset.identity.version)"))
        }
        if asset.geometry.width <= 0 || asset.geometry.height <= 0 || asset.geometry.frameRate.map({ !$0.isFinite || $0 <= 0 }) == true || asset.geometry.durationSeconds.map({ !$0.isFinite || $0 <= 0 }) == true {
            issues.append(.invalidManifest("positive geometry is required for \(asset.identity.assetID)@\(asset.identity.version)"))
        }
        if asset.approval.decidedBy.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            issues.append(.invalidManifest("approval provenance is required for \(asset.identity.assetID)@\(asset.identity.version)"))
        }
        return issues
    }

    private static func validateMasks(in manifest: AssetManifest) -> [AssetManifestError] {
        var issues: [AssetManifestError] = []
        for asset in manifest.assets {
            guard let maskIdentity = asset.geometry.mask else { continue }
            let matches = manifest.assets.filter { $0.identity == maskIdentity }
            guard let mask = matches.first, matches.count == 1 else {
                issues.append(.missingMask(asset.identity, maskIdentity))
                continue
            }
            guard mask.kind == .mask else {
                issues.append(.invalidMask(asset.identity, maskIdentity, "referenced asset is not a mask"))
                continue
            }
            if mask.geometry.mask != nil {
                issues.append(.invalidMask(asset.identity, maskIdentity, "a mask cannot itself depend on another mask"))
            }
            if mask.geometry.width != asset.geometry.width || mask.geometry.height != asset.geometry.height {
                issues.append(.invalidMask(asset.identity, maskIdentity, "dimensions must match the masked asset"))
            }
        }
        return issues
    }

    private static func confinedMediaURL(relativePath: String, libraryRoot: URL) -> URL? {
        let components = relativePath.split(separator: "/", omittingEmptySubsequences: false)
        guard !relativePath.isEmpty,
              !relativePath.hasPrefix("/"),
              !components.contains(".."),
              !components.contains("."),
              !components.contains(where: { $0.isEmpty }) else { return nil }
        let root = libraryRoot.standardizedFileURL.resolvingSymlinksInPath()
        let candidate = root.appendingPathComponent(relativePath).standardizedFileURL.resolvingSymlinksInPath()
        let prefix = root.path.hasSuffix("/") ? root.path : root.path + "/"
        return candidate.path.hasPrefix(prefix) ? candidate : nil
    }

}

/** A caller-facing gate: validate the complete library, then resolve exact approved pins. */
public struct AssetLibrary: Sendable {
    public let rootURL: URL
    public let manifest: AssetManifest

    public init(manifestURL: URL, libraryRoot: URL? = nil) throws {
        self.rootURL = (libraryRoot ?? manifestURL.deletingLastPathComponent()).standardizedFileURL
        self.manifest = try AssetManifestStore.load(from: manifestURL)
    }

    public func validatedApprovedAssets(pinned identities: [AssetIdentity]) throws -> [AssetRecord] {
        guard Set(identities).count == identities.count else {
            throw AssetManifestError.invalidManifest("duplicate requested asset pins")
        }
        try AssetManifestValidator.validate(manifest, libraryRoot: rootURL).requireValid()
        return try identities.map { try AssetManifestValidator.approvedAsset($0, in: manifest) }
    }
}
