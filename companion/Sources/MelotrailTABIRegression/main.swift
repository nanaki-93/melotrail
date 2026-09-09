import CoreMedia
import CoreGraphics
import Foundation
import ImageIO
import MelotrailTABICompanion
import UniformTypeIdentifiers

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

let fixtureDate = Date(timeIntervalSince1970: 1_700_000_000)

func writeOwnedPNG(to url: URL, width: Int, height: Int) throws {
    try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    guard let context = CGContext(
        data: nil,
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else { throw AssetManifestError.unreadableManifest("cannot create owned PNG fixture") }
    context.setFillColor(red: 0.2, green: 0.3, blue: 0.7, alpha: 0.5)
    context.fill(CGRect(x: 0, y: 0, width: width, height: height))
    guard let image = context.makeImage(),
          let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        throw AssetManifestError.unreadableManifest("cannot write owned PNG fixture")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else {
        throw AssetManifestError.unreadableManifest("cannot finish owned PNG fixture")
    }
}

func fixtureRecord(identity: AssetIdentity, path: String, digest: String, width: Int = 2, height: Int = 3, approval: AssetApprovalState = .approved, frameRate: Double? = nil, duration: Double? = nil, uses: [String] = ["companion regression"]) -> AssetRecord {
    AssetRecord(
        identity: identity,
        kind: .image,
        relativeMediaPath: path,
        sha256: digest,
        provenance: AssetProvenance(
            originalSource: "owned regression fixture",
            creator: "Melotrail regression fixture",
            creationMethod: .owned,
            createdAt: fixtureDate
        ),
        rights: AssetRights(ownershipOrLicense: "owned fixture", permittedUses: uses),
        geometry: AssetGeometry(
            width: width,
            height: height,
            frameRate: frameRate,
            durationSeconds: duration,
            alpha: .present,
            pivot: AssetPoint(x: 0.5, y: 1.0),
            placementAnchors: ["seat": AssetPoint(x: 0.5, y: 1.0)],
            compatibleSceneVersions: ["train-v1"],
            compatibleIdentityVersions: ["tabi-v1"]
        ),
        approval: AssetApproval(state: approval, decidedBy: "fixture-review", decidedAt: fixtureDate)
    )
}

func makeAssetLibrary() throws -> (root: URL, manifestURL: URL, approved: AssetIdentity, proposed: AssetIdentity) {
    let root = FileManager.default.temporaryDirectory.appending(path: "melotrail-tabi-assets-\(UUID().uuidString)")
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
    let proposedURL = root.appending(path: "originals/tabi-proposed.png")
    let approvedURL = root.appending(path: "approved/tabi-approved.png")
    try writeOwnedPNG(to: proposedURL, width: 2, height: 3)
    try writeOwnedPNG(to: approvedURL, width: 2, height: 3)
    let proposed = AssetIdentity(assetID: "tabi-seat", version: "v1")
    let approved = AssetIdentity(assetID: "tabi-seat", version: "v2")
    let manifest = AssetManifest(libraryID: "owned-regression-library", assets: [
        fixtureRecord(identity: proposed, path: "originals/tabi-proposed.png", digest: try AssetDigest.sha256(of: proposedURL), approval: .proposed),
        fixtureRecord(identity: approved, path: "approved/tabi-approved.png", digest: try AssetDigest.sha256(of: approvedURL)),
    ])
    let manifestURL = root.appending(path: "asset-manifest.json")
    try AssetManifestStore.save(manifest, to: manifestURL)
    return (root, manifestURL, approved, proposed)
}

func hasIssue(_ validation: AssetManifestValidation, matching predicate: (AssetManifestError) -> Bool) -> Bool {
    validation.issues.contains(where: predicate)
}

func openAssetLibrary(_ manifest: AssetManifest, root: URL, manifestName: String) throws -> AssetLibrary {
    let manifestURL = root.appending(path: manifestName)
    try AssetManifestStore.save(manifest, to: manifestURL)
    return try AssetLibrary(manifestURL: manifestURL, libraryRoot: root)
}

func requireLibraryRejection(
    _ library: AssetLibrary,
    pinned identities: [AssetIdentity],
    _ message: String,
    matching predicate: (AssetManifestError) -> Bool
) {
    do {
        _ = try library.validatedApprovedAssets(pinned: identities)
        require(false, message)
    } catch let error as AssetManifestError {
        require(predicate(error), "\(message): got \(error.localizedDescription)")
    } catch {
        require(false, "\(message): got \(error.localizedDescription)")
    }
}

do {
    let started = Date()
    let probe = try OwnedMediaSpike.run()
    let elapsed = Date().timeIntervalSince(started)
    let size = try probe.outputURL.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
    require(size > 0 && elapsed > 0, "owned encode measurements must be real and positive")
    print("owned-encode output=\(probe.outputURL.path) bytes=\(size) elapsed-seconds=\(elapsed)")
    directories.append(probe.outputURL.deletingLastPathComponent())
    require(FileManager.default.fileExists(atPath: probe.outputURL.path), "the real caller must publish a MOV")
    require(abs(probe.previewSeekSeconds - 0.5) <= 1.0 / Double(OwnedMediaSpike.frameRate), "a shared half-second preview seek must remain aligned")
    let original = try Data(contentsOf: probe.outputURL)
    let repeatedStarted = Date()
    let repeated = try OwnedMediaSpike.run()
    let repeatedSize = try repeated.outputURL.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
    require(repeatedSize > 0, "retry output must have measured bytes")
    print("owned-encode output=\(repeated.outputURL.path) bytes=\(repeatedSize) elapsed-seconds=\(Date().timeIntervalSince(repeatedStarted))")
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

do {
    let fixture = try makeAssetLibrary()
    directories.append(fixture.root)
    let library = try AssetLibrary(manifestURL: fixture.manifestURL)
    let selected = try library.validatedApprovedAssets(pinned: [fixture.approved])
    require(selected.count == 1 && selected[0].identity == fixture.approved, "a caller must resolve the exact approved asset version after validating the library")
    requireLibraryRejection(library, pinned: [fixture.proposed], "a proposed version must not be selectable") {
        if case .assetNotApproved(let identity, .proposed) = $0 { return identity == fixture.proposed }
        return false
    }

    let approvedMedia = fixture.root.appending(path: "approved/tabi-approved.png")
    let original = try Data(contentsOf: approvedMedia)
    var changed = original
    changed.append(0)
    try changed.write(to: approvedMedia, options: .atomic)
    requireLibraryRejection(library, pinned: [fixture.approved], "changed media bytes must reject the pinned digest") {
        if case .digestMismatch(let identity, _, _) = $0 { return identity == fixture.approved }
        return false
    }

    let missingFixture = try makeAssetLibrary()
    directories.append(missingFixture.root)
    let missingLibrary = try AssetLibrary(manifestURL: missingFixture.manifestURL)
    try FileManager.default.removeItem(at: missingFixture.root.appending(path: "approved/tabi-approved.png"))
    requireLibraryRejection(missingLibrary, pinned: [missingFixture.approved], "missing media must reject the pinned asset") {
        if case .missingMedia(let identity, _) = $0 { return identity == missingFixture.approved }
        return false
    }

    let dimensionsFixture = try makeAssetLibrary()
    directories.append(dimensionsFixture.root)
    let dimensionsManifest = try AssetManifestStore.load(from: dimensionsFixture.manifestURL)
    let wrongDimensions = AssetManifest(
        libraryID: dimensionsManifest.libraryID,
        assets: dimensionsManifest.assets.map { asset in
            guard asset.identity == dimensionsFixture.approved else { return asset }
            return fixtureRecord(
                identity: asset.identity,
                path: asset.relativeMediaPath,
                digest: asset.sha256,
                width: 3,
                height: 2,
                approval: asset.approval.state
            )
        }
    )
    let wrongDimensionsLibrary = try openAssetLibrary(wrongDimensions, root: dimensionsFixture.root, manifestName: "wrong-dimensions.json")
    requireLibraryRejection(wrongDimensionsLibrary, pinned: [dimensionsFixture.approved], "declared dimensions must match the real owned media") {
        if case .dimensionMismatch(let identity, _, _, 2, 3) = $0 { return identity == dimensionsFixture.approved }
        return false
    }
    let cleanLibrary = try AssetLibrary(manifestURL: dimensionsFixture.manifestURL)
    requireLibraryRejection(cleanLibrary, pinned: [dimensionsFixture.approved, dimensionsFixture.approved], "duplicate caller pins must reject") {
        if case .invalidManifest(let message) = $0 { return message == "duplicate requested asset pins" }
        return false
    }
    guard let validAsset = dimensionsManifest.assets.first(where: { $0.identity == dimensionsFixture.approved }) else {
        throw AssetManifestError.assetNotFound(dimensionsFixture.approved)
    }
    for invalidField in ["provider", "model", "prompt"] {
        let generated = GeneratedAssetProvenance(provider: invalidField == "provider" ? " " : "fixture", model: invalidField == "model" ? "" : "v1", prompt: invalidField == "prompt" ? " " : "owned probe", referenceAssets: [])
        let invalidAsset = AssetRecord(identity: validAsset.identity, kind: validAsset.kind, relativeMediaPath: validAsset.relativeMediaPath, sha256: validAsset.sha256,
            provenance: AssetProvenance(originalSource: "fixture", creator: "fixture", creationMethod: .generated, createdAt: fixtureDate, generated: generated),
            rights: validAsset.rights, geometry: validAsset.geometry, approval: validAsset.approval)
        let invalidLibrary = try openAssetLibrary(
            AssetManifest(libraryID: "invalid-generated", assets: [invalidAsset]),
            root: dimensionsFixture.root,
            manifestName: "invalid-generated-\(invalidField).json"
        )
        requireLibraryRejection(invalidLibrary, pinned: [invalidAsset.identity], "generated provenance must be checked: \(invalidField)") {
            if case .invalidManifest = $0 { return true }
            return false
        }
    }
    for invalidField in ["pivot-x", "pivot-y", "anchor-x", "anchor-y"] {
        let geometry = AssetGeometry(width: 2, height: 3, alpha: .present,
            pivot: AssetPoint(x: invalidField == "pivot-x" ? .nan : 0.5, y: invalidField == "pivot-y" ? .infinity : 1),
            placementAnchors: ["seat": AssetPoint(x: invalidField == "anchor-x" ? .infinity : 0.5, y: invalidField == "anchor-y" ? .nan : 1)])
        let invalidAsset = AssetRecord(identity: validAsset.identity, kind: validAsset.kind, relativeMediaPath: validAsset.relativeMediaPath, sha256: validAsset.sha256,
            provenance: validAsset.provenance, rights: validAsset.rights, geometry: geometry, approval: validAsset.approval)
        require(hasIssue(AssetManifestValidator.validate(AssetManifest(libraryID: "invalid-point", assets: [invalidAsset]), libraryRoot: dimensionsFixture.root)) {
            if case .invalidManifest = $0 { return true }
            return false
        }, "pivot and anchor coordinates must be finite: \(invalidField)")
    }
    let manifestBytes = try Data(contentsOf: fixture.manifestURL)
    do {
        try AssetManifestStore.save(AssetManifest(libraryID: "replacement", assets: []), to: fixture.manifestURL)
        require(false, "an existing immutable manifest must not be overwritten")
    } catch AssetManifestError.invalidManifest { }
    let retainedManifest = try Data(contentsOf: fixture.manifestURL)
    require(manifestBytes == retainedManifest, "failed snapshot publication must preserve the prior manifest")
    let unknown = AssetIdentity(assetID: "unknown", version: "v1")
    requireLibraryRejection(cleanLibrary, pinned: [unknown], "missing versions must reject explicitly") {
        if case .assetNotFound(let identity) = $0 { return identity == unknown }
        return false
    }
    let duplicate = AssetManifest(libraryID: "duplicates", assets: [selected[0], selected[0]])
    let duplicateLibrary = try openAssetLibrary(duplicate, root: dimensionsFixture.root, manifestName: "duplicate-identities.json")
    requireLibraryRejection(duplicateLibrary, pinned: [fixture.approved], "ambiguous versions must reject explicitly") {
        if case .invalidManifest(let message) = $0 { return message.contains("duplicate identity") }
        return false
    }
    for unsafePath in ["../outside.png", "/tmp/outside.png", "approved/../outside.png"] {
        let unsafe = AssetManifest(libraryID: "unsafe", assets: [fixtureRecord(identity: fixture.approved, path: unsafePath, digest: selected[0].sha256)])
        require(hasIssue(AssetManifestValidator.validate(unsafe, libraryRoot: fixture.root)) {
            if case .unsafeMediaPath = $0 { return true }; return false
        }, "traversal must reject before reading media")
    }
    let escaping = fixture.root.appending(path: "escape")
    try FileManager.default.createSymbolicLink(at: escaping, withDestinationURL: dimensionsFixture.root)
    let unsafeLink = AssetManifest(libraryID: "symlink", assets: [fixtureRecord(identity: fixture.approved, path: "escape/approved/tabi-approved.png", digest: selected[0].sha256)])
    require(hasIssue(AssetManifestValidator.validate(unsafeLink, libraryRoot: fixture.root)) {
        if case .unsafeMediaPath = $0 { return true }; return false
    }, "symlink escape must reject before reading media")
    for (field, assertedAsset) in [
        ("frame rate", fixtureRecord(identity: validAsset.identity, path: validAsset.relativeMediaPath, digest: validAsset.sha256, frameRate: 30)),
        ("duration", fixtureRecord(identity: validAsset.identity, path: validAsset.relativeMediaPath, digest: validAsset.sha256, duration: 1)),
    ] {
        let assertedLibrary = try openAssetLibrary(
            AssetManifest(libraryID: "asserted-\(field)", assets: [assertedAsset]),
            root: dimensionsFixture.root,
            manifestName: "asserted-\(field.replacingOccurrences(of: " ", with: "-")).json"
        )
        requireLibraryRejection(assertedLibrary, pinned: [assertedAsset.identity], "asserted \(field) must reject when the media probe cannot verify it") {
            if case .metadataMismatch(let identity, let message) = $0 {
                return identity == assertedAsset.identity && message.contains(field)
            }
            return false
        }
    }
    for invalid in [
        fixtureRecord(identity: fixture.approved, path: "approved/tabi-approved.png", digest: selected[0].sha256, frameRate: .nan),
        fixtureRecord(identity: fixture.approved, path: "approved/tabi-approved.png", digest: selected[0].sha256, duration: .infinity),
        fixtureRecord(identity: fixture.approved, path: "approved/tabi-approved.png", digest: selected[0].sha256, uses: [" "]),
    ] {
        require(hasIssue(AssetManifestValidator.validate(AssetManifest(libraryID: "invalid-fields", assets: [invalid]), libraryRoot: fixture.root)) {
            if case .invalidManifest = $0 { return true }; return false
        }, "non-finite geometry and empty permission claims must reject")
    }
    print("asset-manifest-regression=PASS")
} catch {
    fputs("asset-manifest-regression=FAIL: \(error.localizedDescription)\n", stderr)
    exit(1)
}
