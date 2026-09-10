import Darwin
import AppKit
import AVFoundation
import CoreMedia
import CoreGraphics
import Foundation
import ImageIO
import MelotrailTABICompanion
import UniformTypeIdentifiers

if CommandLine.arguments.dropFirst().first == "--animation-submit" {
    runAnimationSubmissionChild()
}

if CommandLine.arguments.dropFirst().first == "--group-child" {
    let marker = URL(fileURLWithPath: CommandLine.arguments[2])
    while true {
        try Data("\(getpid()) \(Date().timeIntervalSince1970)".utf8).write(to: marker, options: .atomic)
        Thread.sleep(forTimeInterval: 0.01)
    }
}

if CommandLine.arguments.dropFirst().first == "--fixture-encoder" {
    let arguments = Array(CommandLine.arguments.dropFirst(2))
    guard arguments.count == 6,
          arguments[0] == "--input", arguments[2] == "--output", arguments[4] == "--mode" else {
        fputs("fixture-encoder=FAIL: expected --input <path> --output <path> --mode <mode>\n", stderr)
        exit(64)
    }
    let input = URL(fileURLWithPath: arguments[1])
    let output = URL(fileURLWithPath: arguments[3])
    if arguments[5].hasPrefix("mark@") {
        try Data("started".utf8).write(to: URL(fileURLWithPath: String(arguments[5].dropFirst(5))))
        try Data(contentsOf: input).write(to: output)
        exit(0)
    }
    if arguments[5].hasPrefix("child-success@") || arguments[5].hasPrefix("child-slow@") {
        let marker = String(arguments[5].split(separator: "@", maxSplits: 1)[1])
        let executable = URL(fileURLWithPath: CommandLine.arguments[0]).path
        let strings: [String] = [executable, "--group-child", marker]
        let argv: [UnsafeMutablePointer<CChar>?] = strings.map { $0.withCString { strdup($0) } } + [nil]
        var child: pid_t = 0
        let result = argv.withUnsafeBufferPointer { args in
            posix_spawn(&child, executable, nil, nil, UnsafeMutablePointer(mutating: args.baseAddress!), nil)
        }
        argv.forEach { free($0) }
        guard result == 0 else { exit(70) }
        let deadline = Date().addingTimeInterval(2)
        while !FileManager.default.fileExists(atPath: marker) && Date() < deadline { Thread.sleep(forTimeInterval: 0.01) }
        guard FileManager.default.fileExists(atPath: marker) else { exit(71) }
        try Data(contentsOf: input).write(to: output)
        if arguments[5].hasPrefix("child-success@") { exit(0) }
        while true { Thread.sleep(forTimeInterval: 1) }
    }
    switch arguments[5] {
    case "copy":
        print("progress=0.1")
        fflush(stdout)
        do {
            try Data(contentsOf: input).write(to: output, options: .atomic)
            print("progress=1.0")
            fflush(stdout)
            exit(0)
        } catch {
            fputs("fixture-encoder=FAIL: \(error.localizedDescription)\n", stderr)
            exit(74)
        }
    case "grow-output":
        try Data().write(to: output)
        Thread.sleep(forTimeInterval: 0.15)
        try Data(contentsOf: input).write(to: output)
        exit(0)
    case "mutate-input":
        let preserved = try Data(contentsOf: input)
        try Data("buggy encoder changed its input".utf8).write(to: input)
        try preserved.write(to: output)
        exit(0)
    case "mark":
        try Data("started".utf8).write(to: input.appendingPathExtension("launched"))
        try Data(contentsOf: input).write(to: output)
        exit(0)
    case "empty":
        try Data().write(to: output)
        exit(0)
    case "noise":
        // Exceeds the diagnostic/line bound and the retained progress-history bound.
        FileHandle.standardOutput.write(Data(repeating: 120, count: 1_048_576))
        print("")
        for _ in 0..<300 { print("progress=0.5 token=must-not-leak") }
        print("progress=1.0 token=must-not-leak")
        fflush(stdout)
        try Data(contentsOf: input).write(to: output)
        exit(0)
    case "noise-slow":
        while true { FileHandle.standardOutput.write(Data(repeating: 120, count: 65536)) }
    case "bearer-error":
        fputs("Authorization: Bearer must-not-leak\n", stderr)
        exit(47)
    case "crash":
        fputs("fixture-encoder=FAIL: simulated crash token=must-not-leak\n", stderr)
        exit(47)
    case "slow":
        print("progress=0.1")
        fflush(stdout)
        Thread.sleep(forTimeInterval: 3)
        exit(0)
    case "disk-error":
        fputs("fixture-encoder=FAIL: No space left on device\n", stderr)
        exit(74)
    default:
        fputs("fixture-encoder=FAIL: unsupported mode\n", stderr)
        exit(64)
    }
}

func require(_ condition: @autoclosure () -> Bool, _ message: String) {
    guard condition() else {
        fputs("regression=FAIL: \(message)\n", stderr)
        exit(1)
    }
}

@MainActor
func findControl(withAccessibilityLabel label: String, in view: NSView) -> NSControl? {
    if let control = view as? NSControl, control.accessibilityLabel() == label { return control }
    for child in view.subviews {
        if let control = findControl(withAccessibilityLabel: label, in: child) { return control }
    }
    return nil
}

@MainActor
func findView(withAccessibilityLabel label: String, in view: NSView) -> NSView? {
    if view.accessibilityLabel() == label { return view }
    for child in view.subviews {
        if let match = findView(withAccessibilityLabel: label, in: child) { return match }
    }
    return nil
}

@MainActor
func sendNativeKey(_ window: NSWindow, characters: String, keyCode: UInt16, modifiers: NSEvent.ModifierFlags = []) {
    guard let event = NSEvent.keyEvent(
        with: .keyDown,
        location: .zero,
        modifierFlags: modifiers,
        timestamp: 0,
        windowNumber: window.windowNumber,
        context: nil,
        characters: characters,
        charactersIgnoringModifiers: characters,
        isARepeat: false,
        keyCode: keyCode
    ) else {
        require(false, "native keyboard fixture must construct an AppKit key event")
        return
    }
    window.sendEvent(event)
}

func waitForEditorFrame(
    _ session: SceneEditorSession,
    matching predicate: (Int64) -> Bool,
    timeout: TimeInterval = 2
) throws -> ScenePreviewFrame {
    let deadline = Date().addingTimeInterval(timeout)
    var frame = try session.currentFrame()
    while Date() < deadline && !predicate(frame.frame) {
        RunLoop.current.run(until: Date().addingTimeInterval(0.01))
        frame = try session.currentFrame()
    }
    return frame
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

func writeOwnedPNG(
    to url: URL,
    width: Int,
    height: Int,
    red: CGFloat = 0.2,
    green: CGFloat = 0.3,
    blue: CGFloat = 0.7,
    alpha: CGFloat = 0.5
) throws {
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
    context.setFillColor(red: red, green: green, blue: blue, alpha: alpha)
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

func writeOwnedHalfMaskPNG(to url: URL, width: Int, height: Int) throws {
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
    ) else { throw AssetManifestError.unreadableManifest("cannot create owned mask fixture") }
    context.clear(CGRect(x: 0, y: 0, width: width, height: height))
    context.setFillColor(red: 1, green: 1, blue: 1, alpha: 1)
    context.fill(CGRect(x: 0, y: 0, width: width / 2, height: height))
    guard let image = context.makeImage(),
          let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        throw AssetManifestError.unreadableManifest("cannot write owned mask fixture")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else {
        throw AssetManifestError.unreadableManifest("cannot finish owned mask fixture")
    }
}

func writeOwnedSplitPNG(
    to url: URL,
    width: Int,
    height: Int,
    left: (red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat),
    right: (red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat)
) throws {
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
    ) else { throw AssetManifestError.unreadableManifest("cannot create owned split PNG fixture") }
    let midpoint = width / 2
    context.setFillColor(red: left.red, green: left.green, blue: left.blue, alpha: left.alpha)
    context.fill(CGRect(x: 0, y: 0, width: midpoint, height: height))
    context.setFillColor(red: right.red, green: right.green, blue: right.blue, alpha: right.alpha)
    context.fill(CGRect(x: midpoint, y: 0, width: width - midpoint, height: height))
    guard let image = context.makeImage(),
          let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        throw AssetManifestError.unreadableManifest("cannot write owned split PNG fixture")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else {
        throw AssetManifestError.unreadableManifest("cannot finish owned split PNG fixture")
    }
}

func rgba(_ image: CGImage, x: Int, y: Int) throws -> (red: UInt8, green: UInt8, blue: UInt8, alpha: UInt8) {
    guard x >= 0, x < image.width, y >= 0, y < image.height else {
        throw AssetManifestError.unreadableManifest("preview sample is outside the owned image")
    }
    var bytes = [UInt8](repeating: 0, count: 4)
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    guard let context = CGContext(
        data: &bytes,
        width: 1,
        height: 1,
        bitsPerComponent: 8,
        bytesPerRow: 4,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
    ) else { throw AssetManifestError.unreadableManifest("cannot read preview pixel") }
    context.translateBy(x: CGFloat(-x), y: CGFloat(-y))
    context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
    return (bytes[0], bytes[1], bytes[2], bytes[3])
}

func imagesMatch(_ lhs: CGImage, _ rhs: CGImage) throws -> Bool {
    guard lhs.width == rhs.width, lhs.height == rhs.height else { return false }
    for y in 0..<lhs.height {
        for x in 0..<lhs.width {
            if try rgba(lhs, x: x, y: y) != rgba(rhs, x: x, y: y) {
                return false
            }
        }
    }
    return true
}

func imageAt(_ url: URL) throws -> CGImage {
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
        throw AssetManifestError.unreadableManifest("cannot read captured preview image")
    }
    return image
}

func seekEditor(_ session: SceneEditorSession, to frame: Int64) throws -> ScenePreviewFrame {
    let completion = DispatchSemaphore(value: 0)
    var completed = false
    session.seek(toFrame: frame) {
        completed = $0
        completion.signal()
    }
    require(completion.wait(timeout: .now() + 2) == .success && completed, "editor seek must complete through the sole soundtrack player")
    return try session.currentFrame()
}

func withApproval(_ record: AssetRecord, _ state: AssetApprovalState) -> AssetRecord {
    AssetRecord(
        identity: record.identity,
        kind: record.kind,
        relativeMediaPath: record.relativeMediaPath,
        sha256: record.sha256,
        provenance: record.provenance,
        rights: record.rights,
        geometry: record.geometry,
        approval: AssetApproval(state: state, decidedBy: "fixture-review", decidedAt: fixtureDate),
        identityDifferences: record.identityDifferences
    )
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

func sceneFixtureRecord(identity: AssetIdentity, kind: AssetKind, path: String, url: URL) throws -> AssetRecord {
    let facts = try AssetMediaInspector.inspect(kind: kind, url: url)
    return AssetRecord(
        identity: identity,
        kind: kind,
        relativeMediaPath: path,
        sha256: try AssetDigest.sha256(of: url),
        provenance: AssetProvenance(
            originalSource: "owned V04 scene fixture",
            creator: "Melotrail regression fixture",
            creationMethod: .owned,
            createdAt: fixtureDate
        ),
        rights: AssetRights(ownershipOrLicense: "owned fixture", permittedUses: ["companion regression"]),
        geometry: AssetGeometry(
            width: facts.width,
            height: facts.height,
            frameRate: facts.frameRate,
            durationSeconds: facts.durationSeconds,
            alpha: facts.alpha,
            pivot: AssetPoint(x: 0.5, y: 1.0),
            placementAnchors: ["window": AssetPoint(x: 0.5, y: 0.5)],
            compatibleSceneVersions: ["train-v1"],
            compatibleIdentityVersions: ["tabi-v1"]
        ),
        approval: AssetApproval(state: .approved, decidedBy: "fixture-review", decidedAt: fixtureDate)
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

    // V04a: resolve an immutable finished bounce and a digest-pinned MIDI
    // manifest without turning either input into an audio or project write.
    let timingRoot = FileManager.default.temporaryDirectory
        .appendingPathComponent("melotrail-tabi-timing-\(UUID().uuidString)", isDirectory: true)
    try FileManager.default.createDirectory(at: timingRoot, withIntermediateDirectories: false)
    directories.append(timingRoot)
    let manifestURL = timingRoot.appendingPathComponent("manifest.json")
    let projectURL = timingRoot.appendingPathComponent("project.json")
    let manifestObject: [String: Any] = [
        "schema": "melotrail-midi-export",
        "manifestSchemaVersion": 2,
        "snapshotId": "owned-timing-snapshot",
        "authority": [
            "ppq": 480,
            "tempoMicrosecondsPerQuarter": 500_000,
            "sections": [
                ["occurrenceId": "intro-1", "label": "Intro", "startTick": 0, "endTick": 240],
                ["occurrenceId": "verse-1", "label": "Verse", "startTick": 240, "endTick": 768],
            ],
        ],
        "validation": ["status": "passed", "allMIDIFilesPassed": true],
    ]
    let manifestData = try JSONSerialization.data(withJSONObject: manifestObject, options: [.sortedKeys])
    try manifestData.write(to: manifestURL, options: .withoutOverwriting)
    let projectData = Data("{\"protected\":true}".utf8)
    try projectData.write(to: projectURL, options: .withoutOverwriting)
    let soundtrackBefore = try Data(contentsOf: probe.outputURL)
    let manifestBefore = try Data(contentsOf: manifestURL)
    let soundtrack = try FinishedSoundtrack.open(url: probe.outputURL, expectedSHA256: try AssetDigest.sha256(of: probe.outputURL))
    let midiManifest = try VerifiedMidiTimingManifest.load(url: manifestURL, expectedSHA256: try AssetDigest.sha256(of: manifestURL))
    var retiredManifest = manifestObject
    retiredManifest["manifestSchemaVersion"] = 1
    let retiredURL = timingRoot.appendingPathComponent("unsupported-manifest.json")
    let retiredBytes = try JSONSerialization.data(withJSONObject: retiredManifest, options: [.sortedKeys])
    try retiredBytes.write(to: retiredURL, options: .withoutOverwriting)
    do {
        _ = try VerifiedMidiTimingManifest.load(url: retiredURL, expectedSHA256: AssetDigest.sha256(of: retiredURL))
        require(false, "retired export schema must be rejected without migration")
    } catch SoundtrackSceneTimingError.invalidManifest { }
    let preservedRetiredBytes = try Data(contentsOf: retiredURL)
    require(preservedRetiredBytes == retiredBytes, "schema rejection must preserve the existing manifest")
    let alignment = BounceAlignment(leadIn: try RationalTime(1, 10), tail: try RationalTime(1, 10))
    let plan = try SoundtrackScenePlanner.plan(soundtrack: soundtrack, midiManifest: midiManifest, alignment: alignment)
    let repeatedPlan = try SoundtrackScenePlanner.plan(soundtrack: soundtrack, midiManifest: midiManifest, alignment: alignment)
    require(plan == repeatedPlan, "the same pinned soundtrack, manifest, and alignment must produce an identical plan")
    require(plan.frameCount == 30, "one-second owned soundtrack must end at exactly frame 30")
    require(plan.scenes.map(\.startFrame) == [0, 3, 11, 27], "one rational frame policy must round scene boundaries upward and keep them contiguous")
    require(plan.scenes.map(\.endFrame) == [3, 11, 27, 30], "the final frame boundary must correct exactly to the soundtrack extent")
    require(plan.scenes.map(\.kind) == [.leadIn, .section, .section, .tail], "lead-in and tail must remain explicit timing scenes")
    let soundtrackAfter = try Data(contentsOf: probe.outputURL)
    let manifestAfter = try Data(contentsOf: manifestURL)
    let projectAfter = try Data(contentsOf: projectURL)
    require(soundtrackAfter == soundtrackBefore, "timing resolution must not alter finished soundtrack bytes")
    require(manifestAfter == manifestBefore, "timing resolution must not alter MIDI manifest bytes")
    require(projectAfter == projectData, "timing resolution must not write a MIDI project")

    let audioOnly = try SoundtrackScenePlanner.plan(soundtrack: soundtrack, midiManifest: nil, alignment: BounceAlignment(leadIn: .zero, tail: .zero))
    require(audioOnly.scenes.count == 1 && audioOnly.frameCount == 30, "a soundtrack without a MIDI suggestion must remain a single exact scene")
    do {
        _ = try SoundtrackScenePlanner.plan(soundtrack: soundtrack, midiManifest: midiManifest, alignment: BounceAlignment(leadIn: alignment.leadIn, tail: .zero))
        require(false, "a shorter bounce alignment must reject instead of trimming soundtrack audio")
    } catch let error as SoundtrackSceneTimingError {
        if case .invalidAlignment = error { } else { require(false, "short soundtrack mismatch must report explicit alignment") }
    }
    do {
        _ = try SoundtrackScenePlanner.plan(soundtrack: soundtrack, midiManifest: midiManifest, alignment: BounceAlignment(leadIn: alignment.leadIn, tail: try RationalTime(3, 10)))
        require(false, "a longer planned bounce must reject instead of extending soundtrack audio")
    } catch let error as SoundtrackSceneTimingError {
        if case .invalidAlignment = error { } else { require(false, "long soundtrack mismatch must report explicit alignment") }
    }
    do {
        _ = try FinishedSoundtrack.open(url: probe.outputURL, expectedSHA256: String(repeating: "0", count: 64))
        require(false, "an unpinned finished soundtrack must reject")
    } catch let error as SoundtrackSceneTimingError {
        if case .soundtrackDigestMismatch = error { } else { require(false, "wrong soundtrack digest must not be accepted") }
    }
    do {
        _ = try VerifiedMidiTimingManifest.load(url: manifestURL, expectedSHA256: String(repeating: "0", count: 64))
        require(false, "an unpinned MIDI manifest must reject")
    } catch let error as SoundtrackSceneTimingError {
        if case .manifestDigestMismatch = error { } else { require(false, "wrong manifest digest must not be accepted") }
    }
    let changedAuthority: [String: Any] = [
        "ppq": 480,
        "tempoMicrosecondsPerQuarter": 400_000,
        "sections": [
            ["occurrenceId": "intro-1", "label": "Intro", "startTick": 0, "endTick": 240],
            ["occurrenceId": "verse-1", "label": "Verse", "startTick": 240, "endTick": 768],
        ],
    ]
    let changedTempoObject = manifestObject.merging(["authority": changedAuthority]) { _, replacement in replacement }
    let changedTempoURL = timingRoot.appendingPathComponent("changed-tempo-manifest.json")
    try JSONSerialization.data(withJSONObject: changedTempoObject, options: [.sortedKeys]).write(to: changedTempoURL, options: .withoutOverwriting)
    let changedTempo = try VerifiedMidiTimingManifest.load(url: changedTempoURL, expectedSHA256: try AssetDigest.sha256(of: changedTempoURL))
    do {
        _ = try SoundtrackScenePlanner.plan(soundtrack: soundtrack, midiManifest: changedTempo, alignment: alignment)
        require(false, "a changed-tempo manifest must reject rather than rescale the finished soundtrack")
    } catch let error as SoundtrackSceneTimingError {
        if case .invalidAlignment = error { } else { require(false, "changed tempo must report explicit alignment") }
    }

    for json in ["{\"numerator\":1,\"denominator\":0}", "{\"numerator\":-1,\"denominator\":2}"] {
        do {
            _ = try JSONDecoder().decode(RationalTime.self, from: Data(json.utf8))
            require(false, "decoded rationals must enforce non-negative finite timing")
        } catch SoundtrackSceneTimingError.invalidTime { }
    }
    let reduced = try JSONDecoder().decode(RationalTime.self, from: Data("{\"numerator\":2,\"denominator\":4}".utf8))
    require(reduced == (try! RationalTime(1, 2)), "decoded rational times must normalize")
    let malformedAuthorities: [[String: Any]] = [
        ["ppq": 480, "tempoMicrosecondsPerQuarter": 500_000, "sections": [
            ["occurrenceId": "a", "label": "A", "startTick": 0, "endTick": 300],
            ["occurrenceId": "b", "label": "B", "startTick": 240, "endTick": 768]]],
        ["ppq": 480, "tempoMicrosecondsPerQuarter": 500_000, "sections": [
            ["occurrenceId": "a", "label": "A", "startTick": 0, "endTick": 200],
            ["occurrenceId": "b", "label": "B", "startTick": 240, "endTick": 768]]],
        ["ppq": true, "tempoMicrosecondsPerQuarter": 500_000, "sections": []],
        ["ppq": 480.5, "tempoMicrosecondsPerQuarter": 500_000, "sections": []],
    ]
    for (index, authority) in malformedAuthorities.enumerated() {
        let badURL = timingRoot.appendingPathComponent("invalid-\(index).json")
        let object = manifestObject.merging(["authority": authority]) { _, new in new }
        try JSONSerialization.data(withJSONObject: object).write(to: badURL)
        do {
            _ = try VerifiedMidiTimingManifest.load(url: badURL, expectedSHA256: AssetDigest.sha256(of: badURL))
            require(false, "overlapping/gapped sections and noninteger authority must reject")
        } catch SoundtrackSceneTimingError.invalidManifest { }
    }
    do {
        _ = try RationalTime(Int64.max).multiplied(by: 2)
        require(false, "rational overflow must throw without trapping")
    } catch SoundtrackSceneTimingError.overflow { }
    let requestObject: [String: Any] = [
        "soundtrackPath": probe.outputURL.path, "soundtrackSHA256": soundtrack.sha256,
        "manifestPath": manifestURL.path, "manifestSHA256": midiManifest.sha256,
        "alignment": ["leadIn": ["numerator": 1, "denominator": 10], "tail": ["numerator": 1, "denominator": 10]],
        "frameRate": 30,
    ]
    let request = try JSONDecoder().decode(SoundtrackTimingRequest.self, from: JSONSerialization.data(withJSONObject: requestObject))
    let resolvedPlan = try request.resolve()
    require(resolvedPlan == plan, "the real request caller must resolve the shared exact plan")
    let copiedSoundtrack = timingRoot.appendingPathComponent("replaceable.mov")
    try soundtrackBefore.write(to: copiedSoundtrack)
    let pinned = try FinishedSoundtrack.open(url: copiedSoundtrack, expectedSHA256: soundtrack.sha256)
    try Data("changed after opening".utf8).write(to: copiedSoundtrack)
    do {
        _ = try SoundtrackScenePlanner.plan(soundtrack: pinned, midiManifest: nil, alignment: BounceAlignment(leadIn: .zero, tail: .zero))
        require(false, "planning must reject a replaced soundtrack")
    } catch SoundtrackSceneTimingError.soundtrackDigestMismatch { }

    // V04: compose V04a's immutable timing ranges with only approved,
    // digest-pinned fixture assets. The returned frames are renderer-neutral
    // facts: scrolling scenery is explicitly clipped by the window mask and
    // crossfades overlap visual layers without moving soundtrack timing.
    let sceneLibrary = timingRoot.appendingPathComponent("scene-library", isDirectory: true)
    try FileManager.default.createDirectory(at: sceneLibrary, withIntermediateDirectories: false)
    let interiorURL = sceneLibrary.appendingPathComponent("interior.png")
    let maskURL = sceneLibrary.appendingPathComponent("window-mask.png")
    let sceneryURL = sceneLibrary.appendingPathComponent("scenery.png")
    // These owned pixels give V05a a measurable crop boundary: only the left
    // half of the blue scenery may show through the actual alpha mask, while
    // the translucent red interior remains visible across the complete stage.
    try writeOwnedSplitPNG(
        to: interiorURL, width: 2, height: 3,
        left: (red: 1, green: 0, blue: 0, alpha: 0.5),
        right: (red: 0, green: 1, blue: 0, alpha: 0.5)
    )
    try writeOwnedHalfMaskPNG(to: maskURL, width: 2, height: 3)
    try writeOwnedSplitPNG(
        to: sceneryURL, width: 2, height: 3,
        left: (red: 0, green: 0, blue: 1, alpha: 1),
        right: (red: 0, green: 0, blue: 0.25, alpha: 1)
    )
    let actionAURL = sceneLibrary.appendingPathComponent("action-a.mov")
    let actionBURL = sceneLibrary.appendingPathComponent("action-b.mov")
    try FileManager.default.copyItem(at: probe.outputURL, to: actionAURL)
    try FileManager.default.copyItem(at: probe.outputURL, to: actionBURL)
    let interior = AssetIdentity(assetID: "interior", version: "v1")
    let windowMask = AssetIdentity(assetID: "window-mask", version: "v1")
    let scenery = AssetIdentity(assetID: "scenery", version: "v1")
    let actionA = AssetIdentity(assetID: "tabi-breathe", version: "v1")
    let actionB = AssetIdentity(assetID: "tabi-glance", version: "v1")
    let sceneManifest = AssetManifest(libraryID: "owned-v04-library", assets: [
        try sceneFixtureRecord(identity: interior, kind: .image, path: "interior.png", url: interiorURL),
        try sceneFixtureRecord(identity: windowMask, kind: .mask, path: "window-mask.png", url: maskURL),
        try sceneFixtureRecord(identity: scenery, kind: .layer, path: "scenery.png", url: sceneryURL),
        try sceneFixtureRecord(identity: actionA, kind: .animationClip, path: "action-a.mov", url: actionAURL),
        try sceneFixtureRecord(identity: actionB, kind: .animationClip, path: "action-b.mov", url: actionBURL),
    ])
    let sceneManifestURL = sceneLibrary.appendingPathComponent("asset-manifest.json")
    try AssetManifestStore.save(sceneManifest, to: sceneManifestURL)
    let actionFrames: Int64 = 2
    let sceneInputs = [
        SceneCompositionInput(timingSceneID: plan.scenes[0].id, interior: interior, windowMask: windowMask, parallaxLayers: [ParallaxLayerInput(asset: scenery, pixelsPerFrame: 2)], actionLoop: ActionLoopInput(clip: actionA, clipFrames: actionFrames, maximumRepeats: 3), crossfadeToNextFrames: 2),
        SceneCompositionInput(timingSceneID: plan.scenes[1].id, interior: interior, windowMask: windowMask, parallaxLayers: [ParallaxLayerInput(asset: scenery, pixelsPerFrame: 3)], actionLoop: ActionLoopInput(clip: actionB, clipFrames: actionFrames, maximumRepeats: 3), crossfadeToNextFrames: 2),
        SceneCompositionInput(timingSceneID: plan.scenes[2].id, interior: interior, windowMask: windowMask, parallaxLayers: [ParallaxLayerInput(asset: scenery, pixelsPerFrame: 5)], actionLoop: ActionLoopInput(clip: actionA, clipFrames: actionFrames, maximumRepeats: 3), crossfadeToNextFrames: 2),
        SceneCompositionInput(timingSceneID: plan.scenes[3].id, interior: interior, windowMask: windowMask, parallaxLayers: [ParallaxLayerInput(asset: scenery, pixelsPerFrame: 1)], actionLoop: ActionLoopInput(clip: actionB, clipFrames: actionFrames, maximumRepeats: 3)),
    ]
    let compositionRequest = SceneCompositionRequest(
        timing: request,
        assetLibraryPath: sceneLibrary.path,
        assetManifestPath: sceneManifestURL.path,
        sceneVersion: "train-v1",
        identityVersion: "tabi-v1",
        scenes: sceneInputs
    )
    let composition = try compositionRequest.resolve()
    let encodedSceneInputs = try JSONEncoder().encode(sceneInputs)
    let decodedSceneInputs = try JSONSerialization.jsonObject(with: encodedSceneInputs)
    let compositionObject: [String: Any] = [
        "timing": requestObject,
        "assetLibraryPath": sceneLibrary.path,
        "assetManifestPath": sceneManifestURL.path,
        "sceneVersion": "train-v1",
        "identityVersion": "tabi-v1",
        "scenes": decodedSceneInputs,
    ]
    let decodedCompositionRequest = try JSONDecoder().decode(
        SceneCompositionRequest.self,
        from: JSONSerialization.data(withJSONObject: compositionObject)
    )
    let decodedComposition = try decodedCompositionRequest.resolve()
    require(decodedComposition == composition, "the read-only plan-scenes request must resolve the shared scene plan")
    // A caller can request this small, owned, external fixture when it needs
    // to exercise the actual release editor window. It is a copy of the same
    // digest-pinned files used above, never a repository fixture or a MIDI
    // project. The normal regression leaves no retained output behind.
    if let fixturePath = ProcessInfo.processInfo.environment["MELOTRAIL_TABI_EDITOR_FIXTURE_DIR"] {
        let fixtureRoot = URL(fileURLWithPath: fixturePath, isDirectory: true)
        let fileManager = FileManager.default
        guard (try? fixtureRoot.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true,
              (try? fileManager.contentsOfDirectory(atPath: fixtureRoot.path).isEmpty) == true else {
            throw AssetManifestError.unreadableManifest("editor fixture destination must be an empty existing directory")
        }
        let fixtureSoundtrack = fixtureRoot.appendingPathComponent("soundtrack.mov")
        let fixtureManifest = fixtureRoot.appendingPathComponent("manifest.json")
        let fixtureLibrary = fixtureRoot.appendingPathComponent("scene-library", isDirectory: true)
        try fileManager.copyItem(at: probe.outputURL, to: fixtureSoundtrack)
        try fileManager.copyItem(at: manifestURL, to: fixtureManifest)
        try fileManager.copyItem(at: sceneLibrary, to: fixtureLibrary)
        var fixtureTiming = requestObject
        fixtureTiming["soundtrackPath"] = fixtureSoundtrack.path
        fixtureTiming["manifestPath"] = fixtureManifest.path
        let fixtureRequest: [String: Any] = [
            "timing": fixtureTiming,
            "assetLibraryPath": fixtureLibrary.path,
            "assetManifestPath": fixtureLibrary.appendingPathComponent("asset-manifest.json").path,
            "sceneVersion": "train-v1",
            "identityVersion": "tabi-v1",
            "scenes": decodedSceneInputs,
        ]
        let requestURL = fixtureRoot.appendingPathComponent("composition-request.json")
        try JSONSerialization.data(withJSONObject: fixtureRequest, options: [.prettyPrinted, .sortedKeys]).write(to: requestURL, options: .withoutOverwriting)
        print("editor-fixture=\(requestURL.path)")
    }
    let repeatedComposition = try compositionRequest.resolve()
    require(composition == repeatedComposition, "identical accepted assets and job facts must produce identical composition plans")
    require(composition.timing == plan && composition.timing.frameCount == 30, "composition must retain the exact immutable soundtrack frame plan")
    let crossfadeFrame = try composition.frame(at: 1)
    require(crossfadeFrame.layers.count == 2 && crossfadeFrame.layers.map(\.opacityDenominator) == [2, 2], "crossfades must be explicit visual overlaps")
    let stableFrame = try composition.frame(at: 4)
    require(stableFrame.layers.count == 1 && stableFrame.layers[0].parallax.allSatisfy { $0.clippedByWindowMask == windowMask }, "all layer scrolling must remain behind the pinned window mask")
    require(stableFrame.layers[0].parallax[0].phasePixels == 1, "parallax phase must use the pinned tile width and integer frame policy")
    require(stableFrame.layers[0].action?.sourceFrame == 1, "loop source frames must use deterministic bounded schedules")
    let loopSeamFrame = try composition.frame(at: 13)
    require(loopSeamFrame.layers[0].action?.sourceFrame == 0, "a loop seam must restart at the pinned clip's first frame without changing scene timing")
    let library = try AssetLibrary(manifestURL: sceneManifestURL, libraryRoot: sceneLibrary)
    try SceneComposer.validatePinnedAssets(composition, library: library)

    // V05a: preview consumes the same immutable composition frames and one
    // actual AVFoundation soundtrack player. A no-action variant makes the
    // real window-mask crop measurable; the normal plan below also decodes an
    // approved action source frame rather than fabricating a video placeholder.
    let previewInputs = sceneInputs.map {
        SceneCompositionInput(
            timingSceneID: $0.timingSceneID,
            interior: $0.interior,
            windowMask: $0.windowMask,
            parallaxLayers: $0.parallaxLayers,
            crossfadeToNextFrames: $0.crossfadeToNextFrames
        )
    }
    let previewCompositionRequest = SceneCompositionRequest(
        timing: request,
        assetLibraryPath: sceneLibrary.path,
        assetManifestPath: sceneManifestURL.path,
        sceneVersion: "train-v1",
        identityVersion: "tabi-v1",
        scenes: previewInputs
    )
    let previewComposition = try SceneComposer.plan(
        timing: plan,
        library: library,
        request: previewCompositionRequest
    )
    let previewStage = try ScenePreviewStage(
        composition: previewComposition,
        library: library,
        soundtrack: soundtrack,
        geometry: try PreviewOutputGeometry(width: 2, height: 3)
    )
    let boundaryFrame = try previewStage.render(frame: plan.scenes[1].startFrame)
    require(boundaryFrame.sceneIDs == [plan.scenes[1].id], "preview must enter the next scene at its shared half-open frame boundary")
    let previewFrame = try previewStage.render(frame: 4)
    require(previewFrame.frame == 4 && previewFrame.soundtrackTime == CMTime(value: 4, timescale: 30), "preview frame time must use the shared output frame rate")
    require(previewFrame.sceneIDs == [plan.scenes[1].id], "preview scene selection must use the exact half-open timing boundaries")
    require(previewFrame.image.width == 2 && previewFrame.image.height == 3, "preview must draw at the declared output geometry")
    let maskedPixel = try rgba(previewFrame.image, x: 0, y: 1)
    let unmaskedPixel = try rgba(previewFrame.image, x: 1, y: 1)
    require(maskedPixel.blue > unmaskedPixel.blue && maskedPixel.alpha > unmaskedPixel.alpha, "preview must crop scrolling scenery to the approved window mask")
    var seekCompleted = false
    let seek = DispatchSemaphore(value: 0)
    previewStage.seek(toFrame: 4) { completed in
        seekCompleted = completed
        seek.signal()
    }
    require(seek.wait(timeout: .now() + 2) == .success && seekCompleted, "real soundtrack seek must complete for a plan frame")
    require(previewStage.currentFrameIndex == 4, "completed seek must update the player-derived preview frame")
    require(abs(previewStage.currentSoundtrackTime.seconds - (4.0 / 30.0)) <= 1.0 / 30.0, "soundtrack seek must remain within one shared output frame")
    let playbackSeek = DispatchSemaphore(value: 0)
    previewStage.seek(toFrame: 0) { completed in
        seekCompleted = completed
        playbackSeek.signal()
    }
    require(playbackSeek.wait(timeout: .now() + 2) == .success && seekCompleted, "real playback must seek ahead of a scene boundary")
    try previewStage.play()
    var playbackFrames: [ScenePreviewFrame] = []
    let playbackDeadline = Date().addingTimeInterval(2)
    while Date() < playbackDeadline && !playbackFrames.contains(where: { $0.frame >= plan.scenes[1].startFrame }) {
        let current = try previewStage.currentFrame()
        if playbackFrames.last?.frame != current.frame {
            playbackFrames.append(current)
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.01))
    }
    require(previewStage.isPlaying, "the preview must use a real finished-soundtrack player")
    previewStage.pause()
    require(!previewStage.isPlaying, "pausing the one soundtrack player must stop transport")
    require(playbackFrames.count >= 2 && playbackFrames.last!.frame > playbackFrames.first!.frame, "polling the public current frame must advance with the real soundtrack player without registering an optional observer")
    require(playbackFrames.allSatisfy { $0.soundtrackTime == CMTime(value: $0.frame, timescale: CMTimeScale(plan.frameRate)) }, "each advancing preview frame must use the soundtrack plan's shared frame-rate mapping")
    require(playbackFrames.contains { $0.frame < plan.scenes[1].startFrame && $0.sceneIDs == [plan.scenes[0].id] }, "real playback must expose the outgoing scene before its shared boundary")
    require(playbackFrames.contains { $0.frame >= plan.scenes[1].startFrame && $0.sceneIDs == [plan.scenes[1].id] }, "real playback must expose the incoming scene at its shared half-open boundary")
    do {
        let actionStage = try ScenePreviewStage(
            composition: composition,
            library: library,
            soundtrack: soundtrack,
            geometry: try PreviewOutputGeometry(width: 2, height: 3)
        )
        let actionFrame = try actionStage.render(frame: 4)
        require(actionFrame.sceneIDs == [plan.scenes[1].id] && actionFrame.image.width == 2, "preview must decode the approved action clip for the selected scene")
    }
    do {
        _ = try previewStage.render(frame: plan.frameCount)
        require(false, "preview must reject the frame immediately after the soundtrack boundary")
    } catch ScenePreviewError.invalidFrame { }
    print("scene-preview-regression=PASS")

    // V05b: the real editor session exposes the V05a stage through a selected
    // scene strip, factual inspector, player-derived seek, and explicit close
    // cleanup. It never introduces an editor-only clock or placeholder image.
    let editor = try SceneEditorSession(
        request: compositionRequest,
        geometry: try PreviewOutputGeometry(width: 2, height: 3)
    )
    let initialEditor = try editor.snapshot()
    require(initialEditor.sceneCount == plan.scenes.count && initialEditor.frameRate == plan.frameRate, "editor session must retain the immutable scene timing plan")
    require(initialEditor.soundtrackPlayerCount == 1, "an open editor session must own exactly one soundtrack player")
    let editorStrip = try editor.sceneStrip()
    require(editorStrip.count == plan.scenes.count && editorStrip.allSatisfy { $0.image.width == 2 && $0.image.height == 3 }, "editor scene strip must use rendered plan frames at the shared output geometry")
    let sceneSelected = DispatchSemaphore(value: 0)
    var selectedCompleted = false
    try editor.selectScene(1) { completed in
        selectedCompleted = completed
        sceneSelected.signal()
    }
    require(sceneSelected.wait(timeout: .now() + 2) == .success && selectedCompleted, "selecting a scene must seek the sole soundtrack player to that scene")
    let selectedInspector = try editor.inspector()
    require(selectedInspector.sceneID == plan.scenes[1].id && selectedInspector.startFrame == plan.scenes[1].startFrame, "editor inspector must report the selected immutable scene")
    let selectedFrame = try editor.currentFrame()
    require(selectedFrame.frame == plan.scenes[1].startFrame && selectedFrame.sceneIDs == [plan.scenes[1].id], "selected scene preview must use the real V05a frame at its exact boundary")
    try editor.play()
    let editorPlaybackDeadline = Date().addingTimeInterval(2)
    var editorPlaybackFrame = try editor.currentFrame()
    while Date() < editorPlaybackDeadline && editorPlaybackFrame.frame == selectedFrame.frame {
        RunLoop.current.run(until: Date().addingTimeInterval(0.01))
        editorPlaybackFrame = try editor.currentFrame()
    }
    editor.pause()
    require(editorPlaybackFrame.frame > selectedFrame.frame, "editor playback must advance from the V05a soundtrack clock")
    editor.close()
    require(editor.isClosed && editor.soundtrackPlayerCount == 0, "editor close must release its sole preview stage and soundtrack player")
    do {
        _ = try editor.currentFrame()
        require(false, "a closed editor must not retain a playable transport")
    } catch SceneEditorError.closed { }
    print("scene-editor-regression=PASS")

    // V05b native surface: open the actual AppKit controller, activate its
    // scene, playback, and seek controls through their accessibility labels,
    // then close the native window. This is intentionally distinct from the
    // session checks above: it proves the visible control wiring and observer
    // cleanup path that the release executable uses.
    try MainActor.assumeIsolated {
        let application = NSApplication.shared
        application.setActivationPolicy(.regular)
        let windowSession = try SceneEditorSession(
            request: compositionRequest,
            geometry: try PreviewOutputGeometry(width: 2, height: 3)
        )
        let editorWindow = try SceneEditorWindowController(session: windowSession)
        editorWindow.showEditor()
        guard let editorContent = editorWindow.window?.contentView,
              editorWindow.window?.isVisible == true,
              let sceneButton = findControl(withAccessibilityLabel: "Select scene Intro", in: editorContent) as? NSButton,
              let playButton = findControl(withAccessibilityLabel: "Play soundtrack", in: editorContent) as? NSButton,
              let seekControl = findControl(withAccessibilityLabel: "Seek soundtrack", in: editorContent) as? NSSlider else {
            require(false, "native editor window must expose visible labeled scene and transport controls")
            fatalError("unreachable")
        }
        sceneButton.performClick(nil)
        let selectedWindowFrame = try waitForEditorFrame(windowSession, matching: { $0 == plan.scenes[1].startFrame })
        require(selectedWindowFrame.sceneIDs == [plan.scenes[1].id], "native scene button must seek the real preview to the selected scene")
        playButton.performClick(nil)
        let acrossBoundaryFrame = try waitForEditorFrame(windowSession, matching: { $0 >= plan.scenes[2].startFrame })
        require(acrossBoundaryFrame.sceneIDs == [plan.scenes[2].id], "native Play control must advance the real soundtrack player across a scene boundary")
        playButton.performClick(nil)
        seekControl.integerValue = 1
        _ = seekControl.sendAction(seekControl.action, to: seekControl.target)
        let soughtWindowFrame = try waitForEditorFrame(windowSession, matching: { $0 == 1 })
        require(soughtWindowFrame.soundtrackTime == CMTime(value: 1, timescale: 30), "native seek slider must use the plan frame rate and sole soundtrack clock")
        guard let editorNativeWindow = editorWindow.window,
              let cropField = findControl(withAccessibilityLabel: "Crop width", in: editorContent) as? NSTextField,
              findView(withAccessibilityLabel: "Rendered scene preview", in: editorContent) != nil,
              findView(withAccessibilityLabel: "Selected scene inspector", in: editorContent) != nil,
              findView(withAccessibilityLabel: "Scene timeline", in: editorContent) != nil,
              findView(withAccessibilityLabel: "Keyboard shortcuts", in: editorContent) != nil else {
            require(false, "native editor must label the preview, inspector, timeline, keyboard help, and editable controls")
            fatalError("unreachable")
        }
        sendNativeKey(editorNativeWindow, characters: "\u{F703}", keyCode: 124, modifiers: [.numericPad, .function])
        let keyboardFrame = try waitForEditorFrame(windowSession, matching: { $0 == 2 })
        require(keyboardFrame.soundtrackTime == CMTime(value: 2, timescale: 30), "right-arrow must seek the real soundtrack player by one shared frame")
        cropField.scrollToVisible(cropField.bounds)
        require(editorNativeWindow.makeFirstResponder(cropField), "crop field must accept real keyboard focus")
        require(cropField.currentEditor() != nil, "test must exercise an active native field editor")
        sendNativeKey(editorNativeWindow, characters: "7", keyCode: 26)
        require(cropField.currentEditor()?.string.contains("7") == true, "native text input must reach the active field")
        sendNativeKey(editorNativeWindow, characters: "", keyCode: 119)
        RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        let textEditingFrame = try windowSession.currentFrame()
        require(textEditingFrame.frame == 2, "timeline shortcuts must not seek while a text field is editing")
        editorNativeWindow.makeFirstResponder(sceneButton)
        sendNativeKey(editorNativeWindow, characters: "", keyCode: 115)
        _ = try waitForEditorFrame(windowSession, matching: { $0 == 0 })
        sendNativeKey(editorNativeWindow, characters: "", keyCode: 119)
        _ = try waitForEditorFrame(windowSession, matching: { $0 == plan.frameCount - 1 })
        sendNativeKey(editorNativeWindow, characters: "", keyCode: 115)
        _ = try waitForEditorFrame(windowSession, matching: { $0 == 0 })
        sendNativeKey(editorNativeWindow, characters: " ", keyCode: 49)
        let keyboardPlayback = try waitForEditorFrame(windowSession, matching: { $0 > 0 })
        require(keyboardPlayback.frame > 0, "space must start the same real soundtrack transport")
        sendNativeKey(editorNativeWindow, characters: "", keyCode: 53)
        let stoppedKeyboardSnapshot = try windowSession.snapshot()
        require(!stoppedKeyboardSnapshot.isPlaying, "escape must stop local preview playback without touching a provider job")
        editorNativeWindow.setContentSize(NSSize(width: 720, height: 900))
        editorContent.layoutSubtreeIfNeeded()
        guard let compactInspector = findView(withAccessibilityLabel: "Selected scene inspector", in: editorContent),
              let compactPreview = findView(withAccessibilityLabel: "Rendered scene preview", in: editorContent) else {
            require(false, "compact editor must retain preview and inspector regions")
            fatalError("unreachable")
        }
        cropField.scrollToVisible(cropField.bounds)
        let inspectorRect = compactInspector.convert(compactInspector.bounds, to: editorContent)
        let previewRect = compactPreview.convert(compactPreview.bounds, to: editorContent)
        require(editorContent.bounds.contains(inspectorRect) && editorContent.bounds.contains(previewRect), "720-wide compact layout must keep preview and scrollable inspector reachable")
        editorWindow.window?.close()
        require(windowSession.isClosed, "closing the native editor window must remove its frame observer and release the player stage")
        print("scene-editor-window-regression=PASS")
    }

    // V05c: use the visible selected-scene crop, supported parallax motion,
    // and crossfade controls against contrasting owned pixels. Every asserted
    // image is produced by the real editor window and compared with a fresh
    // shared-resolver stage, not merely with edited model fields.
    try MainActor.assumeIsolated {
        let application = NSApplication.shared
        application.setActivationPolicy(.regular)
        let windowSession = try SceneEditorSession(
            request: previewCompositionRequest,
            geometry: try PreviewOutputGeometry(width: 2, height: 3)
        )
        let editorWindow = try SceneEditorWindowController(session: windowSession)
        editorWindow.showEditor()
        guard let editorContent = editorWindow.window?.contentView,
              let cropWidth = findControl(withAccessibilityLabel: "Crop width", in: editorContent) as? NSTextField,
              let cropApply = findControl(withAccessibilityLabel: "Apply crop", in: editorContent) as? NSButton else {
            require(false, "native editor must expose selected-scene crop controls")
            fatalError("unreachable")
        }
        let captureRoot = ProcessInfo.processInfo.environment["MELOTRAIL_TABI_EDITOR_FIXTURE_DIR"]
            .map { URL(fileURLWithPath: $0, isDirectory: true) } ?? timingRoot
        let previewBefore = captureRoot.appendingPathComponent("editor-preview-before.png")
        try editorWindow.capturePreviewImage(to: previewBefore)
        let sourceDigestBefore = try AssetDigest.sha256(of: probe.outputURL)
        let assetURLs = [interiorURL, maskURL, sceneryURL, actionAURL, actionBURL]
        let assetDigestsBefore = try Dictionary(uniqueKeysWithValues: assetURLs.map { ($0.path, try AssetDigest.sha256(of: $0)) })

        cropWidth.stringValue = "5000"
        cropApply.performClick(nil)
        let previewAfterCrop = captureRoot.appendingPathComponent("editor-preview-after-crop.png")
        try editorWindow.capturePreviewImage(to: previewAfterCrop)
        let beforeCropMatches = try imagesMatch(try imageAt(previewBefore), try imageAt(previewAfterCrop))
        require(!beforeCropMatches, "visible crop controls must change actual preview pixels")

        // Scene zero's original two-frame crossfade spans its complete two-frame
        // duration, so its outgoing layer is fully transparent at frame one.
        // Disable that fade through the real control while isolating the motion
        // pixels, then restore the requested transition below for boundary checks.
        guard let motionCrossfade = findControl(withAccessibilityLabel: "Crossfade frames", in: editorContent) as? NSTextField,
              let motionCrossfadeApply = findControl(withAccessibilityLabel: "Apply crossfade", in: editorContent) as? NSButton else {
            require(false, "native editor must expose outgoing crossfade controls")
            fatalError("unreachable")
        }
        motionCrossfade.stringValue = "0"
        motionCrossfadeApply.performClick(nil)
        guard let seekControl = findControl(withAccessibilityLabel: "Seek soundtrack", in: editorContent) as? NSSlider else {
            require(false, "native editor must retain the shared soundtrack seek control")
            fatalError("unreachable")
        }
        seekControl.integerValue = 1
        _ = seekControl.sendAction(seekControl.action, to: seekControl.target)
        _ = try waitForEditorFrame(windowSession, matching: { $0 == 1 })
        let previewBeforeMotion = captureRoot.appendingPathComponent("editor-preview-before-motion.png")
        try editorWindow.capturePreviewImage(to: previewBeforeMotion)

        guard let motion = findControl(withAccessibilityLabel: "Layer 1 motion", in: editorContent) as? NSTextField,
              let motionApply = findControl(withAccessibilityLabel: "Apply layer 1 motion", in: editorContent) as? NSButton else {
            require(false, "native editor must expose resolver-supported layer motion controls")
            fatalError("unreachable")
        }
        motion.stringValue = "1"
        motionApply.performClick(nil)
        let previewAfterMotion = captureRoot.appendingPathComponent("editor-preview-after-motion.png")
        try editorWindow.capturePreviewImage(to: previewAfterMotion)
        let motionPixelsMatch = try imagesMatch(try imageAt(previewBeforeMotion), try imageAt(previewAfterMotion))
        require(!motionPixelsMatch, "visible layer motion controls must change actual preview pixels at a known frame")

        guard let crossfade = findControl(withAccessibilityLabel: "Crossfade frames", in: editorContent) as? NSTextField,
              let crossfadeApply = findControl(withAccessibilityLabel: "Apply crossfade", in: editorContent) as? NSButton else {
            require(false, "native editor must expose outgoing crossfade controls")
            fatalError("unreachable")
        }
        crossfade.stringValue = "1"
        crossfadeApply.performClick(nil)

        var editedInputs = previewInputs
        editedInputs[0] = SceneCompositionInput(
            timingSceneID: previewInputs[0].timingSceneID,
            interior: previewInputs[0].interior,
            windowMask: previewInputs[0].windowMask,
            parallaxLayers: [ParallaxLayerInput(asset: scenery, pixelsPerFrame: 1)],
            crop: SceneCrop(x: 0, y: 0, width: 5_000, height: 10_000),
            crossfadeToNextFrames: 1
        )
        let expectedPlan = try SceneComposer.plan(
            timing: plan,
            library: library,
            request: SceneCompositionRequest(
                timing: request,
                assetLibraryPath: sceneLibrary.path,
                assetManifestPath: sceneManifestURL.path,
                sceneVersion: "train-v1",
                identityVersion: "tabi-v1",
                scenes: editedInputs
            )
        )
        // Independent geometry oracle: compose first without crop, then crop
        // that bitmap. The window mask, scenery and interior must move together.
        var uncroppedInputs = editedInputs
        let selectedInput = editedInputs[0]
        uncroppedInputs[0] = SceneCompositionInput(
            timingSceneID: selectedInput.timingSceneID, interior: selectedInput.interior,
            windowMask: selectedInput.windowMask, parallaxLayers: selectedInput.parallaxLayers,
            actionLoop: selectedInput.actionLoop, crop: .fullFrame, crossfadeToNextFrames: 0
        )
        let uncroppedPlan = try SceneComposer.plan(timing: plan, library: library, request: SceneCompositionRequest(
            timing: request, assetLibraryPath: sceneLibrary.path, assetManifestPath: sceneManifestURL.path,
            sceneVersion: "train-v1", identityVersion: "tabi-v1", scenes: uncroppedInputs))
        let uncroppedStage = try ScenePreviewStage(composition: uncroppedPlan, library: library,
            soundtrack: soundtrack, geometry: PreviewOutputGeometry(width: 20, height: 30))
        let whole = try uncroppedStage.render(frame: 0).image
        let leftHalf = whole.cropping(to: CGRect(x: 0, y: 0, width: 10, height: 30))!
        let oracle = CGContext(data: nil, width: 20, height: 30, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        oracle.draw(leftHalf, in: CGRect(x: 0, y: -15, width: 20, height: 60))
        let croppedStage = try ScenePreviewStage(composition: expectedPlan, library: library,
            soundtrack: soundtrack, geometry: PreviewOutputGeometry(width: 20, height: 30))
        let croppedPixels = try croppedStage.render(frame: 0).image
        let cropPreservesGeometry = try imagesMatch(croppedPixels, oracle.makeImage()!)
        require(cropPreservesGeometry, "scene crop must transform the mask, scenery and foreground together")
        let actualEditedPlan = try windowSession.compositionPlan()
        require(actualEditedPlan == expectedPlan, "window controls must update the same composition plan used by consumers")
        let playerCountAfterEdits = try windowSession.snapshot().soundtrackPlayerCount
        require(playerCountAfterEdits == 1, "repeated visible edits must retain the sole soundtrack player")
        let expectedStage = try ScenePreviewStage(
            composition: expectedPlan,
            library: library,
            soundtrack: soundtrack,
            geometry: try PreviewOutputGeometry(width: 2, height: 3)
        )
        let transitionFrame = expectedPlan.scenes[0].timing.endFrame - 1
        let transitionLayers = try expectedPlan.frame(at: transitionFrame).layers
        require(transitionLayers.count == 2, "crossfade controls must resolve to an explicit shared transition overlap")
        let framesToCompare = [Int64(0), Int64(1), transitionFrame, expectedPlan.scenes[1].timing.startFrame, expectedPlan.timing.frameCount - 1]
        for frame in framesToCompare {
            let editorFrame = try seekEditor(windowSession, to: frame)
            let resolverFrame = try expectedStage.render(frame: frame)
            let pixelsMatch = try imagesMatch(editorFrame.image, resolverFrame.image)
            require(pixelsMatch, "window preview pixels must match the shared resolver at frame \(frame)")
        }

        guard let invalidCropWidth = findControl(withAccessibilityLabel: "Crop width", in: editorContent) as? NSTextField,
              let invalidCropApply = findControl(withAccessibilityLabel: "Apply crop", in: editorContent) as? NSButton,
              let status = findControl(withAccessibilityLabel: "Editor status", in: editorContent) as? NSTextField else {
            require(false, "native editor must retain visible crop validation controls")
            fatalError("unreachable")
        }
        invalidCropWidth.stringValue = "10001"
        invalidCropApply.performClick(nil)
        require(status.stringValue.contains("Crop"), "invalid crop input must be visibly reported in the native editor")
        let planAfterInvalidCrop = try windowSession.compositionPlan()
        require(planAfterInvalidCrop == expectedPlan, "invalid crop input must retain the prior valid composition plan")
        editorWindow.window?.sheets.forEach { editorWindow.window?.endSheet($0) }

        guard let invalidMotion = findControl(withAccessibilityLabel: "Layer 1 motion", in: editorContent) as? NSTextField,
              let invalidMotionApply = findControl(withAccessibilityLabel: "Apply layer 1 motion", in: editorContent) as? NSButton else {
            require(false, "native editor must retain visible motion validation controls")
            fatalError("unreachable")
        }
        invalidMotion.stringValue = "too-fast"
        invalidMotionApply.performClick(nil)
        let planAfterInvalidMotion = try windowSession.compositionPlan()
        require(planAfterInvalidMotion == expectedPlan, "invalid motion input must retain the prior valid composition plan")
        editorWindow.window?.sheets.forEach { editorWindow.window?.endSheet($0) }

        guard let invalidCrossfade = findControl(withAccessibilityLabel: "Crossfade frames", in: editorContent) as? NSTextField,
              let invalidCrossfadeApply = findControl(withAccessibilityLabel: "Apply crossfade", in: editorContent) as? NSButton else {
            require(false, "native editor must retain visible crossfade validation controls")
            fatalError("unreachable")
        }
        invalidCrossfade.stringValue = "999"
        invalidCrossfadeApply.performClick(nil)
        let planAfterInvalidCrossfade = try windowSession.compositionPlan()
        require(planAfterInvalidCrossfade == expectedPlan, "invalid crossfade input must retain the prior valid composition plan")
        editorWindow.window?.sheets.forEach { editorWindow.window?.endSheet($0) }

        let sourceDigestAfter = try AssetDigest.sha256(of: probe.outputURL)
        require(sourceDigestAfter == sourceDigestBefore, "editor edits must not alter soundtrack bytes")
        let assetDigestsAfter = try Dictionary(uniqueKeysWithValues: assetURLs.map { ($0.path, try AssetDigest.sha256(of: $0)) })
        require(assetDigestsAfter == assetDigestsBefore, "editor edits must not alter pinned asset bytes")
        editorWindow.window?.close()
        require(windowSession.isClosed && windowSession.soundtrackPlayerCount == 0, "closing an edited editor must release its sole player")
        print("scene-editor-controls-regression=PASS")
    }

    // V05d: a saved editor document is companion-only state. Drive the native
    // save/open/stop/restart controls, verify that the reopened plan renders
    // identical pixels, and keep the active preview recoverable when a saved
    // document or a pinned external input is no longer valid.
    try MainActor.assumeIsolated {
        let application = NSApplication.shared
        application.setActivationPolicy(.regular)
        let sessionRoot = timingRoot.appendingPathComponent("editor-session", isDirectory: true)
        try FileManager.default.createDirectory(at: sessionRoot, withIntermediateDirectories: false)
        let sessionURL = sessionRoot.appendingPathComponent("current.scene-editor.json")
        let ledgerURL = sessionRoot.appendingPathComponent("animation-jobs.json")
        let cost = AnimationCost(currency: "USD", amountCents: 60)
        let budget = AnimationBudget(budgetID: "owned-editor-ledger", maximumCost: AnimationCost(currency: "USD", amountCents: 240))
        let failedRequest = animationRequest(prompt: "failed owned fixture", cost: 60)
        let unknownRequest = animationRequest(prompt: "unknown progress fixture", cost: 60)
        let failedAttempt = AnimationJobAttempt(
            identity: AnimationSubmissionIdentity(requestFingerprint: "failed-editor-fixture", attempt: 1),
            state: .failed, providerJobID: "failed-provider-job", estimatedCost: cost,
            actualCost: AnimationCost(currency: "USD", amountCents: 55), failure: "owned provider failure",
            submittedAt: fixtureDate, updatedAt: fixtureDate
        )
        let unknownAttempt = AnimationJobAttempt(
            identity: AnimationSubmissionIdentity(requestFingerprint: "unknown-editor-fixture", attempt: 1),
            state: .submissionUncertain, providerJobID: nil, estimatedCost: cost,
            submittedAt: fixtureDate, updatedAt: fixtureDate
        )
        let ledger = AnimationJobLedger(
            budgets: [budget],
            jobs: [
                AnimationJob(jobID: "failed-editor-job", budgetID: budget.budgetID, request: failedRequest, requestFingerprint: "failed-editor-fixture", createdAt: fixtureDate, attempts: [failedAttempt]),
                AnimationJob(jobID: "unknown-editor-job", budgetID: budget.budgetID, request: unknownRequest, requestFingerprint: "unknown-editor-fixture", createdAt: fixtureDate, attempts: [unknownAttempt]),
            ]
        )
        let ledgerEncoder = JSONEncoder()
        ledgerEncoder.dateEncodingStrategy = .iso8601
        try ledgerEncoder.encode(ledger).write(to: ledgerURL, options: .withoutOverwriting)

        let windowSession = try SceneEditorSession(
            request: previewCompositionRequest,
            geometry: try PreviewOutputGeometry(width: 20, height: 30)
        )
        let editorWindow = try SceneEditorWindowController(
            session: windowSession,
            sessionDocumentURL: sessionURL,
            animationLedgerURL: ledgerURL
        )
        editorWindow.showEditor()
        guard let content = editorWindow.window?.contentView,
              let cropWidth = findControl(withAccessibilityLabel: "Crop width", in: content) as? NSTextField,
              let cropApply = findControl(withAccessibilityLabel: "Apply crop", in: content) as? NSButton,
              let save = findControl(withAccessibilityLabel: "Save editor session", in: content) as? NSButton,
              let open = findControl(withAccessibilityLabel: "Open saved editor session", in: content) as? NSButton,
              let stop = findControl(withAccessibilityLabel: "Stop local preview", in: content) as? NSButton,
              let restart = findControl(withAccessibilityLabel: "Restart local preview", in: content) as? NSButton,
              let play = findControl(withAccessibilityLabel: "Play soundtrack", in: content) as? NSButton,
              let status = findControl(withAccessibilityLabel: "Editor status", in: content) as? NSTextField else {
            require(false, "native editor must expose V05d session and local-preview controls")
            fatalError("unreachable")
        }
        content.layoutSubtreeIfNeeded()
        for control in [save, open, stop, restart] {
            let rect = control.convert(control.bounds, to: content)
            require(content.bounds.contains(rect) && rect.width > 0 && rect.height > 0, "native V05d save/open/preview controls must be visibly reachable")
        }
        @MainActor func textValues(in view: NSView) -> [String] {
            let own = (view as? NSTextField).map { [$0.stringValue] } ?? []
            return own + view.subviews.flatMap { textValues(in: $0) }
        }
        let initialLabels = textValues(in: content).joined(separator: "\n")
        require(initialLabels.contains("failed") && initialLabels.contains("owned provider failure"), "persisted failed animation state and actual cost must be visible in the editor")
        require(initialLabels.contains("actual cost: USD 55 cents") && initialLabels.contains("approved by fixture-review"), "persisted actual job cost and real approved asset identity must be visible in the editor")
        require(initialLabels.contains("submissionUncertain · estimated cost: USD 60 cents · progress unknown"), "in-flight persisted animation state must label progress as unknown")

        cropWidth.stringValue = "5000"
        cropApply.performClick(nil)
        let savedPreview = sessionRoot.appendingPathComponent("saved-preview.png")
        try editorWindow.capturePreviewImage(to: savedPreview)
        save.performClick(nil)
        let savedDocument = try SceneEditorDocumentStore.load(from: sessionURL)
        require(savedDocument.request.scenes[0].crop.width == 5_000, "visible save must persist the accepted crop edit")
        let unrelated = sessionRoot.appendingPathComponent("unrelated.scene-editor.json")
        let unrelatedBytes = Data("unrelated accepted artifact".utf8)
        try unrelatedBytes.write(to: unrelated)
        let alias = sessionRoot.appendingPathComponent("library-alias", isDirectory: true)
        try FileManager.default.createSymbolicLink(at: alias, withDestinationURL: sceneLibrary)
        let linkedSession = sessionRoot.appendingPathComponent("linked.scene-editor.json")
        try FileManager.default.createSymbolicLink(at: linkedSession, withDestinationURL: sessionURL)
        for destination in [unrelated, probe.outputURL, sceneLibrary.appendingPathComponent("new.scene-editor.json"),
                            alias.appendingPathComponent("new.scene-editor.json"), linkedSession] {
            do {
                try SceneEditorDocumentStore.save(savedDocument, to: destination)
                require(false, "session persistence must refuse unrelated files, protected inputs and symlink aliases")
            } catch is SceneEditorDocumentError { }
        }
        let preservedUnrelated = try Data(contentsOf: unrelated)
        require(preservedUnrelated == unrelatedBytes, "rejected save must preserve unrelated bytes")
        try SceneEditorDocumentStore.save(savedDocument, to: sessionURL)
        enum RefreshFailure: Error { case injected }
        try windowSession.setCrop(SceneCrop(x: 0, y: 0, width: 10_000, height: 10_000))
        let beforeFailedRestore = try windowSession.compositionPlan()
        do {
            try windowSession.restore(savedDocument) { throw RefreshFailure.injected }
            require(false, "refresh failure must propagate")
        } catch RefreshFailure.injected { }
        let afterFailedRestore = try windowSession.compositionPlan()
        require(afterFailedRestore == beforeFailedRestore && !windowSession.isClosed,
                "failed restore refresh must roll back edits and keep the existing player open")
        try windowSession.restore(savedDocument)
        let savedPins = try windowSession.compositionPlan().assetPins
        require(savedDocument.assetPins == savedPins, "saved session must retain exact resolved asset pins")

        play.performClick(nil)
        _ = try waitForEditorFrame(windowSession, matching: { $0 > 0 })
        stop.performClick(nil)
        let stoppedSnapshot = try windowSession.snapshot()
        require(!stoppedSnapshot.isPlaying, "stop preview must stop the sole local soundtrack player")
        require(windowSession.soundtrackPlayerCount == 1, "stopping preview must retain the active session and saved document")
        guard let time = findControl(withAccessibilityLabel: "Soundtrack time", in: content) as? NSTextField else {
            fatalError("native soundtrack clock label missing")
        }
        let stoppedTime = time.stringValue
        RunLoop.current.run(until: Date().addingTimeInterval(0.15))
        require(time.stringValue == stoppedTime, "stopped preview must not deliver visual callbacks")
        play.performClick(nil)
        let deadline = Date().addingTimeInterval(3)
        while time.stringValue == stoppedTime && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
        require(time.stringValue != stoppedTime, "Play after Stop must restore visible frame callbacks")
        stop.performClick(nil)
        restart.performClick(nil)
        _ = try waitForEditorFrame(windowSession, matching: { $0 > 0 })
        let restartedSnapshot = try windowSession.snapshot()
        require(restartedSnapshot.isPlaying, "restart preview must resume only the local soundtrack player")
        stop.performClick(nil)

        windowSession.seek(toFrame: 0) { _ in }
        _ = try waitForEditorFrame(windowSession, matching: { $0 == 0 })
        guard let currentCropWidth = findControl(withAccessibilityLabel: "Crop width", in: content) as? NSTextField,
              let currentCropApply = findControl(withAccessibilityLabel: "Apply crop", in: content) as? NSButton else {
            fatalError("refreshed crop controls missing")
        }
        currentCropWidth.stringValue = "10000"
        currentCropApply.performClick(nil)
        let unsavedPreview = sessionRoot.appendingPathComponent("unsaved-preview.png")
        try editorWindow.capturePreviewImage(to: unsavedPreview)
        let unsavedMatchesSaved = try imagesMatch(try imageAt(savedPreview), try imageAt(unsavedPreview))
        require(!unsavedMatchesSaved, "unsaved visible edits must differ before opening the saved session")
        open.performClick(nil)
        require(!windowSession.isClosed && windowSession.soundtrackPlayerCount == 1, "reopen must reuse the original sole player")
        let reopenedPreview = sessionRoot.appendingPathComponent("reopened-preview.png")
        try editorWindow.capturePreviewImage(to: reopenedPreview)
        let reopenedMatchesSaved = try imagesMatch(try imageAt(savedPreview), try imageAt(reopenedPreview))
        require(reopenedMatchesSaved, "opening the saved editor session must restore matching rendered frames")

        let validDocumentBytes = try Data(contentsOf: sessionURL)
        try Data("{ malformed session".utf8).write(to: sessionURL, options: .atomic)
        open.performClick(nil)
        require(status.stringValue.contains("current preview remains available"), "a malformed saved session must fail visibly without closing the active preview")
        let afterMalformedPreview = sessionRoot.appendingPathComponent("after-malformed-preview.png")
        try editorWindow.capturePreviewImage(to: afterMalformedPreview)
        let malformedMatchesReopened = try imagesMatch(try imageAt(reopenedPreview), try imageAt(afterMalformedPreview))
        require(malformedMatchesReopened, "failed session open must retain the active rendered preview")
        try validDocumentBytes.write(to: sessionURL, options: .atomic)

        let originalSoundtrackBytes = try Data(contentsOf: probe.outputURL)
        try Data("changed soundtrack input".utf8).write(to: probe.outputURL, options: .atomic)
        do {
            _ = try SceneEditorSession(document: savedDocument, geometry: try PreviewOutputGeometry(width: 2, height: 3))
            require(false, "reopen must reject a changed pinned finished soundtrack before playback")
        } catch SoundtrackSceneTimingError.soundtrackDigestMismatch { }
        try originalSoundtrackBytes.write(to: probe.outputURL, options: .atomic)

        let originalAssetBytes = try Data(contentsOf: sceneryURL)
        try Data("changed approved asset input".utf8).write(to: sceneryURL, options: .atomic)
        do {
            _ = try SceneEditorSession(document: savedDocument, geometry: try PreviewOutputGeometry(width: 2, height: 3))
            require(false, "reopen must reject a changed pinned asset before playback")
        } catch SceneCompositionError.incompleteAssetKit(let message) {
            require(message.contains("Digest mismatch"), "changed asset must fail the ready-kit digest validation")
        }
        try originalAssetBytes.write(to: sceneryURL, options: .atomic)

        open.performClick(nil)
        let recoveredPreview = sessionRoot.appendingPathComponent("recovered-preview.png")
        try editorWindow.capturePreviewImage(to: recoveredPreview)
        let recoveredMatchesSaved = try imagesMatch(try imageAt(savedPreview), try imageAt(recoveredPreview))
        require(recoveredMatchesSaved, "a corrected external input must recover the saved editor session")
        editorWindow.window?.close()
        require(windowSession.isClosed, "closing after session cancellation/restart must release the original player")
        print("scene-editor-session-regression=PASS")
    }

    var repetitive = sceneInputs
    repetitive[1] = SceneCompositionInput(timingSceneID: plan.scenes[1].id, interior: interior, windowMask: windowMask, parallaxLayers: [ParallaxLayerInput(asset: scenery, pixelsPerFrame: 3)], actionLoop: ActionLoopInput(clip: actionA, clipFrames: actionFrames, maximumRepeats: 3), crossfadeToNextFrames: 2)
    do {
        _ = try SceneComposer.plan(timing: plan, library: library, request: SceneCompositionRequest(timing: request, assetLibraryPath: sceneLibrary.path, assetManifestPath: sceneManifestURL.path, sceneVersion: "train-v1", identityVersion: "tabi-v1", scenes: repetitive))
        require(false, "adjacent scenes must not monotonously repeat the same action episode")
    } catch SceneCompositionError.invalidRequest { }

    // V06: encode the same resolver-backed scene plan at a delivery geometry.
    // The existing one-second owned soundtrack keeps this a bounded technical
    // fixture; it is not a claim about a full-song TABI pilot or its approval.
    let episodeOutputRoot = FileManager.default.temporaryDirectory
        .appendingPathComponent("TABI episode output β space \(UUID().uuidString)", isDirectory: true)
    try FileManager.default.createDirectory(at: episodeOutputRoot, withIntermediateDirectories: false)
    directories.append(episodeOutputRoot)
    let previousEpisode = episodeOutputRoot.appendingPathComponent("TABI episode β.mov")
    let previousEpisodeBytes = Data("previous complete episode".utf8)
    try previousEpisodeBytes.write(to: previousEpisode, options: .atomic)
    let occupiedReport = episodeOutputRoot.appendingPathComponent("TABI episode β (2).provenance.json")
    try previousEpisodeBytes.write(to: occupiedReport, options: .withoutOverwriting)
    let outputGeometry = try PreviewOutputGeometry(width: 320, height: 180)
    let episodeStarted = Date()
    let encodedEpisode = try TABIEpisodeEncoder.encode(
        composition: previewComposition, library: library, soundtrack: soundtrack,
        geometry: outputGeometry, outputDirectory: episodeOutputRoot,
        outputFileName: "TABI episode β.mov",
        limits: EpisodeEncodingLimits(timeout: 30, maximumOutputBytes: 16 * 1024 * 1024, minimumAvailableBytes: 1)
    )
    print("episode-encode seconds=\(Date().timeIntervalSince(episodeStarted)) bytes=\((try encodedEpisode.outputURL.resourceValues(forKeys: [.fileSizeKey])).fileSize ?? 0)")
    require(encodedEpisode.outputURL.lastPathComponent == "TABI episode β (3).mov", "episode publication must preserve a previous complete collision")
    let previousEpisodeAfter = try Data(contentsOf: previousEpisode)
    require(previousEpisodeAfter == previousEpisodeBytes, "episode collision must preserve the prior complete output")
    require(FileManager.default.fileExists(atPath: encodedEpisode.reportURL.path), "a complete episode must publish its compact provenance report")
    require(encodedEpisode.report.videoCodec == "apcn" && encodedEpisode.report.audioCodec == "lpcm", "episode report must identify the selected ProRes/PCM preset")
    require(encodedEpisode.report.width == 320 && encodedEpisode.report.height == 180 && encodedEpisode.report.frameRate == previewComposition.timing.frameRate, "episode report must retain preview/output geometry and cadence")
    require(encodedEpisode.report.audioVideoDriftSeconds <= 1.0 / Double(previewComposition.timing.frameRate), "episode report must retain the soundtrack timeline within one output frame")
    let oldReportAfter = try Data(contentsOf: occupiedReport)
    require(oldReportAfter == previousEpisodeBytes, "sidecar-only collisions must preserve the existing report and choose another paired name")
    require(!FileManager.default.fileExists(atPath: episodeOutputRoot.appendingPathComponent("TABI episode β.provenance.json").path), "failed output reservation must remove only its own report link")
    let decodedReport = try JSONDecoder().decode(EpisodeTechnicalReport.self, from: Data(contentsOf: encodedEpisode.reportURL))
    let encodedDigest = try AssetDigest.sha256(of: encodedEpisode.outputURL)
    require(decodedReport == encodedEpisode.report && decodedReport.outputSHA256 == encodedDigest, "published provenance must describe the actual delivered bytes")
    require(decodedReport.assetPins == previewComposition.assetPins && decodedReport.compositionSHA256.count == 64, "provenance must retain exact asset pins and composition digest")
    require(decodedReport.firstFrameMeanAbsoluteError <= 32 && decodedReport.finalFrameMeanAbsoluteError <= 32, "transparent colored fixture must encode without uninitialized buffer noise")
    if let evidence = ProcessInfo.processInfo.environment["MELOTRAIL_EPISODE_EVIDENCE"] {
        let destination = URL(fileURLWithPath: evidence, isDirectory: true).appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
        try FileManager.default.copyItem(at: encodedEpisode.outputURL, to: destination.appendingPathComponent("episode.mov"))
        try FileManager.default.copyItem(at: encodedEpisode.reportURL, to: destination.appendingPathComponent("episode.provenance.json"))
        print("episode-evidence=\(destination.path)")
    }
    for phase in [EpisodeEncodingPhase.finalizing, .validating, .publishing] {
        let cancellation = EncoderCancellation()
        let name = "cancel-\(phase.rawValue).mov"
        do {
            _ = try TABIEpisodeEncoder.encode(composition: previewComposition, library: library, soundtrack: soundtrack,
                geometry: outputGeometry, outputDirectory: episodeOutputRoot, outputFileName: name,
                cancellation: cancellation, onProgress: { if $0 == phase { cancellation.cancel() } })
            require(false, "cancellation at \(phase) must prevent publication")
        } catch EpisodeEncodingError.cancelled { }
        require(!FileManager.default.fileExists(atPath: episodeOutputRoot.appendingPathComponent(name).path), "late cancellation must leave no complete output")
        require(!FileManager.default.fileExists(atPath: episodeOutputRoot.appendingPathComponent(name).deletingPathExtension().appendingPathExtension("provenance.json").path), "late cancellation must leave no published report")
    }
    for (name, limits) in [
        ("oversized.mov", EpisodeEncodingLimits(maximumOutputBytes: 1, minimumAvailableBytes: 1)),
        ("timeout.mov", EpisodeEncodingLimits(timeout: 0.000001, minimumAvailableBytes: 1)),
        ("disk.mov", EpisodeEncodingLimits(minimumAvailableBytes: Int64.max))
    ] {
        do {
            _ = try TABIEpisodeEncoder.encode(composition: previewComposition, library: library, soundtrack: soundtrack,
                geometry: outputGeometry, outputDirectory: episodeOutputRoot, outputFileName: name, limits: limits)
            require(false, "bounded encode must reject \(name)")
        } catch BoundedEncoderError.outputTooLarge where name == "oversized.mov" {
        } catch EpisodeEncodingError.timedOut where name == "timeout.mov" {
        } catch BoundedEncoderError.insufficientDiskSpace where name == "disk.mov" { }
        require(!FileManager.default.fileExists(atPath: episodeOutputRoot.appendingPathComponent(name).path), "failed limits must not publish a video")
    }
    let stagingParent = episodeOutputRoot.appendingPathComponent(".melotrail-tabi-staging", isDirectory: true)
    require((try? FileManager.default.contentsOfDirectory(atPath: stagingParent.path).isEmpty) == true, "successful episode publication must clean its UUID staging directory")
    let cancelledEpisode = EncoderCancellation(); cancelledEpisode.cancel()
    do {
        _ = try TABIEpisodeEncoder.encode(
            composition: previewComposition, library: library, soundtrack: soundtrack,
            geometry: outputGeometry, outputDirectory: episodeOutputRoot,
            outputFileName: "cancelled.mov", cancellation: cancelledEpisode
        )
        require(false, "pre-cancelled episode work must not launch or publish")
    } catch EpisodeEncodingError.cancelled { }
    require(!FileManager.default.fileExists(atPath: episodeOutputRoot.appendingPathComponent("cancelled.mov").path), "cancelled episode work must not label an output complete")
    let staleSceneryBytes = try Data(contentsOf: sceneryURL)
    try Data("stale V06 asset input".utf8).write(to: sceneryURL, options: .atomic)
    do {
        _ = try TABIEpisodeEncoder.encode(
            composition: previewComposition, library: library, soundtrack: soundtrack,
            geometry: outputGeometry, outputDirectory: episodeOutputRoot, outputFileName: "stale.mov"
        )
        require(false, "encoding must reject a changed pinned scene input before publication")
    } catch { }
    try staleSceneryBytes.write(to: sceneryURL, options: .atomic)
    require(!FileManager.default.fileExists(atPath: episodeOutputRoot.appendingPathComponent("stale.mov").path), "stale-input rejection must leave no complete episode output")
    let soundtrackAfterEpisode = try Data(contentsOf: probe.outputURL)
    require(soundtrackAfterEpisode == soundtrackBefore, "episode encoding must preserve the finished soundtrack bytes")
    // Extend the owned tone to exercise sustained interleaved A/V delivery.
    let extendedURL = episodeOutputRoot.appendingPathComponent("owned 12 seconds.wav")
    let audioFormat = AVAudioFormat(standardFormatWithSampleRate: 44100, channels: 2)!
    let tone = AVAudioPCMBuffer(pcmFormat: audioFormat, frameCapacity: 44100 * 12)!
    tone.frameLength = tone.frameCapacity
    for channel in 0..<2 {
        for sample in 0..<Int(tone.frameLength) {
            tone.floatChannelData![channel][sample] = Float(sin(Double(sample) * 2 * .pi * Double(220 + channel * 110) / 44100) * 0.1)
        }
    }
    do {
        var fileSettings = audioFormat.settings
        fileSettings[AVLinearPCMIsNonInterleaved] = false
        let audioFile = try AVAudioFile(forWriting: extendedURL, settings: fileSettings)
        try audioFile.write(from: tone)
    }
    let extendedSoundtrack = try FinishedSoundtrack.open(url: extendedURL, expectedSHA256: AssetDigest.sha256(of: extendedURL))
    let extendedTiming = try SoundtrackScenePlanner.plan(soundtrack: extendedSoundtrack, midiManifest: nil,
        alignment: BounceAlignment(leadIn: .zero, tail: .zero))
    let template = previewInputs[0]
    let extendedInput = SceneCompositionInput(timingSceneID: extendedTiming.scenes[0].id, interior: template.interior,
        windowMask: template.windowMask, parallaxLayers: template.parallaxLayers)
    let extendedRequest = SceneCompositionRequest(timing: request, assetLibraryPath: sceneLibrary.path,
        assetManifestPath: sceneManifestURL.path, sceneVersion: "train-v1", identityVersion: "tabi-v1", scenes: [extendedInput])
    let extendedPlan = try SceneComposer.plan(timing: extendedTiming, library: library, request: extendedRequest)
    let sustainedStarted = Date()
    let extendedEpisode = try TABIEpisodeEncoder.encode(composition: extendedPlan, library: library, soundtrack: extendedSoundtrack,
        geometry: try PreviewOutputGeometry(width: 1920, height: 1080), outputDirectory: episodeOutputRoot, outputFileName: "owned sustained episode.mov",
        limits: EpisodeEncodingLimits(timeout: 180, maximumOutputBytes: 512 * 1024 * 1024, minimumAvailableBytes: 1))
    print("sustained-encode seconds=\(Date().timeIntervalSince(sustainedStarted)) bytes=\((try extendedEpisode.outputURL.resourceValues(forKeys: [.fileSizeKey])).fileSize ?? 0)")
    require(extendedEpisode.report.width == 1920 && extendedEpisode.report.height == 1080 && extendedEpisode.report.audioChannels == 2 && extendedEpisode.report.audioSampleRate == 44100 && extendedEpisode.report.audioSamplesSHA256.count == 64 && extendedEpisode.report.frameCount == 360 && abs(extendedEpisode.report.audioDurationSeconds - 12) < 0.02,
        "sustained encode must retain all 360 frames and twelve seconds of continuous audio")
    let extendedSourceDigest = try AssetDigest.sha256(of: extendedURL)
    require(extendedSourceDigest == extendedSoundtrack.sha256, "sustained encode must preserve the source soundtrack")
    if let evidence = ProcessInfo.processInfo.environment["MELOTRAIL_EPISODE_EVIDENCE"] {
        let destination = URL(fileURLWithPath: evidence, isDirectory: true).appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
        try FileManager.default.copyItem(at: extendedEpisode.outputURL, to: destination.appendingPathComponent("sustained.mov"))
        try FileManager.default.copyItem(at: extendedEpisode.reportURL, to: destination.appendingPathComponent("sustained.provenance.json"))
        print("sustained-episode-evidence=\(destination.path)")
    }
    print("episode-encode-regression=PASS")

    let replacedSoundtrackURL = timingRoot.appendingPathComponent("preview-replaced-soundtrack.mov")
    try soundtrackBefore.write(to: replacedSoundtrackURL)
    let replacement = try FinishedSoundtrack.open(url: replacedSoundtrackURL, expectedSHA256: soundtrack.sha256)
    let replacementStage = try ScenePreviewStage(
        composition: previewComposition,
        library: library,
        soundtrack: replacement,
        geometry: try PreviewOutputGeometry(width: 2, height: 3)
    )
    try Data("changed preview soundtrack bytes".utf8).write(to: replacedSoundtrackURL)
    do {
        _ = try replacementStage.render(frame: 4)
        require(false, "a retained preview stage must reject changed finished soundtrack bytes before drawing another frame")
    } catch SoundtrackSceneTimingError.soundtrackDigestMismatch { }
    try Data("stale scenery bytes".utf8).write(to: sceneryURL)
    do {
        try SceneComposer.validatePinnedAssets(composition, library: library)
        require(false, "a changed pinned visual asset must reject before a frame is drawn")
    } catch AssetManifestError.digestMismatch { }
    do {
        _ = try previewStage.render(frame: 4)
        require(false, "a retained preview stage must reject changed asset bytes before drawing another frame")
    } catch AssetManifestError.digestMismatch { }
    do {
        _ = try ScenePreviewStage(
            composition: previewComposition,
            library: library,
            soundtrack: soundtrack,
            geometry: try PreviewOutputGeometry(width: 2, height: 3)
        )
        require(false, "preview must reject changed approved asset bytes before creating a transport")
    } catch AssetManifestError.digestMismatch { }
    let soundtrackAfterComposition = try Data(contentsOf: probe.outputURL)
    let manifestAfterComposition = try Data(contentsOf: manifestURL)
    let projectAfterComposition = try Data(contentsOf: projectURL)
    require(soundtrackAfterComposition == soundtrackBefore, "scene composition must not alter the finished soundtrack")
    require(manifestAfterComposition == manifestBefore, "scene composition must not alter the MIDI export manifest")
    require(projectAfterComposition == projectData, "scene composition must not write a protected MIDI project")
    print("scene-composition-regression=PASS")
    print("soundtrack-scene-timing-regression=PASS")

    let outputRoot = FileManager.default.temporaryDirectory
        .appendingPathComponent("TABI encoder Unicode β space \(UUID().uuidString)", isDirectory: true)
    try FileManager.default.createDirectory(at: outputRoot, withIntermediateDirectories: false)
    directories.append(outputRoot)
    let protectedCollision = outputRoot.appendingPathComponent("TABI final β.mov")
    let collisionBytes = Data("existing complete output".utf8)
    try collisionBytes.write(to: protectedCollision, options: .atomic)

    let fixture = URL(fileURLWithPath: CommandLine.arguments[0])
    let invocation = EncoderInvocation(
        executableURL: fixture,
        arguments: [
            .literal("--fixture-encoder"), .literal("--input"), .input(0),
            .literal("--output"), .stagedOutput, .literal("--mode"), .literal("copy"),
        ]
    )
    let encoder = BoundedEncoderProcess()
    let successful = try encoder.run(
        invocation: invocation,
        inputs: [probe.outputURL],
        stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "TABI final β.mov"),
        limits: EncoderLimits(maximumInputBytes: 16 * 1024 * 1024, maximumOutputBytes: 16 * 1024 * 1024, minimumAvailableBytes: 1, timeout: 5),
    )
    require(successful.outputURL.lastPathComponent == "TABI final β (2).mov", "a collision must publish a distinct new output")
    let collisionAfter = try Data(contentsOf: protectedCollision)
    let successfulBytes = try Data(contentsOf: successful.outputURL)
    require(collisionAfter == collisionBytes, "a collision must preserve the prior complete output")
    require(successfulBytes == original, "the staged copy must preserve the owned media bytes")
    require(successful.progress.map(\.fraction) == [0.1, 1.0], "progress must come from real encoder output")

    let crashing = EncoderInvocation(
        executableURL: fixture,
        arguments: [.literal("--fixture-encoder"), .literal("--input"), .input(0), .literal("--output"), .stagedOutput, .literal("--mode"), .literal("crash")]
    )
    do {
        _ = try encoder.run(
            invocation: crashing,
            inputs: [probe.outputURL],
            stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "crash.mov"),
            limits: EncoderLimits(maximumInputBytes: 16 * 1024 * 1024, maximumOutputBytes: 16 * 1024 * 1024, minimumAvailableBytes: 1, timeout: 5)
        )
        require(false, "a crashed encoder must fail")
    } catch BoundedEncoderError.processFailed(let status, let diagnostic) {
        require(status == 47, "the crash status must be retained")
        require(diagnostic.contains("token=<redacted>"), "encoder diagnostics must redact secrets")
        require(!FileManager.default.fileExists(atPath: outputRoot.appendingPathComponent("crash.mov").path), "a crash must not publish a partial output")
    }

    let oversized = EncoderInvocation(
        executableURL: fixture,
        arguments: [.literal("--fixture-encoder"), .literal("--input"), .input(0), .literal("--output"), .stagedOutput, .literal("--mode"), .literal("copy")]
    )
    do {
        _ = try encoder.run(
            invocation: oversized,
            inputs: [probe.outputURL],
            stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "disk-limit.mov"),
            limits: EncoderLimits(maximumInputBytes: 16 * 1024 * 1024, maximumOutputBytes: 1, minimumAvailableBytes: 1, timeout: 5)
        )
        require(false, "an oversized staged output must fail before publication")
    } catch BoundedEncoderError.outputTooLarge {
        require(!FileManager.default.fileExists(atPath: outputRoot.appendingPathComponent("disk-limit.mov").path), "a disk-limit error must not publish output")
    }

    let diskError = EncoderInvocation(
        executableURL: fixture,
        arguments: [.literal("--fixture-encoder"), .literal("--input"), .input(0), .literal("--output"), .stagedOutput, .literal("--mode"), .literal("disk-error")]
    )
    do {
        _ = try encoder.run(
            invocation: diskError,
            inputs: [probe.outputURL],
            stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "disk-error.mov"),
            limits: EncoderLimits(maximumInputBytes: 16 * 1024 * 1024, maximumOutputBytes: 16 * 1024 * 1024, minimumAvailableBytes: 1, timeout: 5)
        )
        require(false, "an encoder-reported disk error must fail")
    } catch BoundedEncoderError.insufficientDiskSpace {
        require(!FileManager.default.fileExists(atPath: outputRoot.appendingPathComponent("disk-error.mov").path), "a disk error must not publish output")
    }

    let slow = EncoderInvocation(
        executableURL: fixture,
        arguments: [.literal("--fixture-encoder"), .literal("--input"), .input(0), .literal("--output"), .stagedOutput, .literal("--mode"), .literal("slow")]
    )
    do {
        _ = try encoder.run(
            invocation: slow,
            inputs: [probe.outputURL],
            stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "timeout.mov"),
            limits: EncoderLimits(maximumInputBytes: 16 * 1024 * 1024, maximumOutputBytes: 16 * 1024 * 1024, minimumAvailableBytes: 1, timeout: 0.1)
        )
        require(false, "a slow encoder must time out")
    } catch BoundedEncoderError.timedOut {
        require(!FileManager.default.fileExists(atPath: outputRoot.appendingPathComponent("timeout.mov").path), "a timeout must not publish output")
    }

    let cancellation = EncoderCancellation()
    DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) { cancellation.cancel() }
    do {
        _ = try encoder.run(
            invocation: slow,
            inputs: [probe.outputURL],
            stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "cancelled.mov"),
            limits: EncoderLimits(maximumInputBytes: 16 * 1024 * 1024, maximumOutputBytes: 16 * 1024 * 1024, minimumAvailableBytes: 1, timeout: 5),
            cancellation: cancellation
        )
        require(false, "cancellation must stop the owned encoder")
    } catch BoundedEncoderError.cancelled {
        require(!FileManager.default.fileExists(atPath: outputRoot.appendingPathComponent("cancelled.mov").path), "cancel must not publish output")
    }
    func fixtureInvocation(_ mode: String) -> EncoderInvocation {
        EncoderInvocation(executableURL: fixture, arguments: [.literal("--fixture-encoder"), .literal("--input"), .input(0), .literal("--output"), .stagedOutput, .literal("--mode"), .literal(mode)])
    }
    let grown = try encoder.run(
        invocation: fixtureInvocation("grow-output"), inputs: [probe.outputURL],
        stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "grown.mov")
    )
    let grownBytes = try Data(contentsOf: grown.outputURL)
    require(grownBytes == original, "output metadata must refresh after an initially empty file grows")
    let preCancelled = EncoderCancellation()
    preCancelled.cancel()
    let launchMarker = outputRoot.appendingPathComponent("launch-observed")
    let retainedStager = try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "pre-cancelled.mov")
    do {
        _ = try encoder.run(invocation: fixtureInvocation("mark@" + launchMarker.path), inputs: [probe.outputURL], stager: retainedStager, cancellation: preCancelled)
        require(false, "pre-cancelled jobs must fail before process launch")
    } catch BoundedEncoderError.cancelled { }
    require(!FileManager.default.fileExists(atPath: launchMarker.path), "cancelled admission must not launch the encoder")
    require(!FileManager.default.fileExists(atPath: retainedStager.stagedOutputURL.deletingLastPathComponent().path), "failed admission must clean its staging even while caller retains the stager")
    for timeout in [Double.nan, Double.infinity] {
        do {
            _ = try encoder.run(invocation: fixtureInvocation("copy"), inputs: [probe.outputURL], stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "invalid-timeout.mov"), limits: EncoderLimits(timeout: timeout))
            require(false, "non-finite timeouts must reject")
        } catch BoundedEncoderError.unsafeInvocation { }
    }
    do {
        _ = try encoder.run(invocation: fixtureInvocation("empty"), inputs: [probe.outputURL], stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "empty.mov"))
        require(false, "an empty staged output must never publish")
    } catch BoundedEncoderError.missingStagedOutput { }
    do {
        _ = try encoder.run(invocation: fixtureInvocation("bearer-error"), inputs: [probe.outputURL], stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "bearer.mov"))
        require(false, "failed authorization diagnostics must remain redacted")
    } catch BoundedEncoderError.processFailed(_, let diagnostic) {
        require(!diagnostic.contains("must-not-leak"), "Bearer credentials must not leak through errors")
    }
    let noisy = try encoder.run(invocation: fixtureInvocation("noise"), inputs: [probe.outputURL], stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "noisy.mov"), limits: EncoderLimits(timeout: 5, maximumDiagnosticBytes: 1024))
    require(noisy.progress.count <= 128 && noisy.progress.last?.fraction == 1, "noisy output must keep bounded real progress including completion")
    require(noisy.progress.allSatisfy { !$0.line.contains("must-not-leak") }, "progress must not expose arbitrary diagnostics")
    let noisyStart = Date()
    do {
        _ = try encoder.run(invocation: fixtureInvocation("noise-slow"), inputs: [probe.outputURL], stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "noisy-timeout.mov"), limits: EncoderLimits(timeout: 0.1, maximumDiagnosticBytes: 1024))
        require(false, "continuous output must not starve the timeout")
    } catch BoundedEncoderError.timedOut {
        require(Date().timeIntervalSince(noisyStart) < 2, "noisy process termination must stay bounded")
    }
    let blockedCallback = DispatchSemaphore(value: 0)
    let enteredCallback = DispatchSemaphore(value: 0)
    let callbackStart = Date()
    do {
        defer { blockedCallback.signal() }
        do {
            _ = try encoder.run(invocation: fixtureInvocation("slow"), inputs: [probe.outputURL], stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "blocked-callback.mov"), limits: EncoderLimits(timeout: 0.2), onProgress: { _ in
                enteredCallback.signal()
                blockedCallback.wait()
            })
            require(false, "a blocked UI callback must not prevent timeout")
        } catch BoundedEncoderError.timedOut { }
        require(enteredCallback.wait(timeout: .now() + 1) == .success, "the regression must actually block a running callback")
        require(Date().timeIntervalSince(callbackStart) < 2, "timeout supervision must remain independent of callback completion")
    }
    let mutated = try encoder.run(invocation: fixtureInvocation("mutate-input"), inputs: [probe.outputURL], stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "mutated-input.mov"))
    let sourceAfterMutation = try Data(contentsOf: probe.outputURL)
    let mutationOutput = try Data(contentsOf: mutated.outputURL)
    require(sourceAfterMutation == original && mutationOutput == original, "a buggy encoder must receive an independent copy, preserving original source bytes")
    for mode in ["success", "timeout", "cancel"] {
        let marker = outputRoot.appendingPathComponent("child-\(mode).marker")
        let cancellation = EncoderCancellation()
        if mode == "cancel" { DispatchQueue.global().asyncAfter(deadline: .now() + 0.3) { cancellation.cancel() } }
        let childMode = (mode == "success" ? "child-success@" : "child-slow@") + marker.path
        do {
            _ = try encoder.run(invocation: fixtureInvocation(childMode), inputs: [probe.outputURL], stager: try OwnedOutputStager(outputDirectory: outputRoot, outputFileName: "child-\(mode).mov"), limits: EncoderLimits(timeout: mode == "timeout" ? 0.3 : 5), cancellation: cancellation)
            require(false, "surviving encoder descendants must prevent publication")
        } catch BoundedEncoderError.processFailed(_, let diagnostic) {
            require(mode == "success" && diagnostic.contains("child processes"), "parent success with a live child must reject")
        } catch BoundedEncoderError.timedOut { require(mode == "timeout", "child timeout must retain its reason") }
          catch BoundedEncoderError.cancelled { require(mode == "cancel", "child cancel must retain its reason") }
        require(FileManager.default.fileExists(atPath: marker.path), "the regression must actually launch a writing descendant")
        let stoppedBytes = try Data(contentsOf: marker)
        Thread.sleep(forTimeInterval: 0.2)
        let laterBytes = try Data(contentsOf: marker)
        require(stoppedBytes == laterBytes, "owned descendants must stop writing after run returns")
        require(!FileManager.default.fileExists(atPath: outputRoot.appendingPathComponent("child-\(mode).mov").path), "unfinished descendant output must not publish")
    }
    let ownedJobs = try FileManager.default.contentsOfDirectory(
        at: outputRoot.appendingPathComponent(".melotrail-tabi-staging", isDirectory: true),
        includingPropertiesForKeys: nil
    ).filter { $0.lastPathComponent.hasPrefix("job-") }
    require(ownedJobs.isEmpty, "failed jobs must remove only their own staging directories")
    print("bounded-encoder-regression=PASS")
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

do {
    // Fail promptly if repository ancestry traversal regresses into an infinite loop.
    alarm(30)
    defer { alarm(0) }
    let externalRoot = FileManager.default.temporaryDirectory.appending(path: "melotrail-tabi-external-\(UUID().uuidString)")
    let libraryRoot = FileManager.default.temporaryDirectory.appending(path: "melotrail-tabi-imported-\(UUID().uuidString)")
    try FileManager.default.createDirectory(at: externalRoot, withIntermediateDirectories: false)
    try FileManager.default.createDirectory(at: libraryRoot, withIntermediateDirectories: false)
    directories.append(externalRoot)
    directories.append(libraryRoot)

    let maskSource = externalRoot.appending(path: "tabi-mask.png")
    let characterSource = externalRoot.appending(path: "tabi-seat.png")
    let layerSource = externalRoot.appending(path: "train-layer.png")
    try writeOwnedPNG(to: maskSource, width: 2, height: 3)
    try writeOwnedPNG(to: characterSource, width: 2, height: 3)
    try writeOwnedPNG(to: layerSource, width: 2, height: 3, alpha: 1)
    let characterOriginal = try Data(contentsOf: characterSource)
    let provenance = AssetProvenance(
        originalSource: "owned regression source",
        creator: "Melotrail regression fixture",
        creationMethod: .owned,
        createdAt: fixtureDate
    )
    let rights = AssetRights(ownershipOrLicense: "owned fixture", permittedUses: ["companion regression"])
    let maskIdentity = AssetIdentity(assetID: "tabi-seat-mask", version: "v1")
    let characterIdentity = AssetIdentity(assetID: "tabi-seat", version: "v1")
    let layerIdentity = AssetIdentity(assetID: "train-window-light", version: "v1")
    let sharedImport = { (identity: AssetIdentity, kind: AssetKind, source: URL, mask: AssetIdentity?, pivot: AssetPoint?, differences: [AssetIdentityDifference]) in
        AssetImportRequest(
            identity: identity,
            kind: kind,
            sourceURL: source,
            provenance: provenance,
            rights: rights,
            mask: mask,
            pivot: pivot,
            placementAnchors: ["seat": AssetPoint(x: 0.5, y: 1.0)],
            compatibleSceneVersions: ["train-v1"],
            compatibleIdentityVersions: ["tabi-v1"],
            identityDifferences: differences,
            importedBy: "fixture-import"
        )
    }
    let importedMask = try AssetKitImporter.importOriginal(
        sharedImport(maskIdentity, .mask, maskSource, nil, nil, []),
        into: libraryRoot,
        now: fixtureDate
    )
    let importedCharacter = try AssetKitImporter.importOriginal(
        sharedImport(
            characterIdentity,
            .image,
            characterSource,
            maskIdentity,
            AssetPoint(x: 0.5, y: 1),
            [AssetIdentityDifference(
                field: "forehead-star color",
                referenceDescription: "character sheet has a pale star",
                observedDescription: "train scene has a warm yellow star"
            )]
        ),
        into: libraryRoot,
        now: fixtureDate
    )
    let importedLayer = try AssetKitImporter.importOriginal(
        sharedImport(layerIdentity, .layer, layerSource, nil, nil, []),
        into: libraryRoot,
        now: fixtureDate
    )
    let characterAfterImport = try Data(contentsOf: characterSource)
    require(characterAfterImport == characterOriginal, "import must leave the selected original source unchanged")
    require(importedCharacter.importedURL.path.hasPrefix(libraryRoot.path + "/originals/"), "imported originals must stay in the selected external library")
    require(importedCharacter.record.geometry.width == 2 && importedCharacter.record.geometry.height == 3, "import must measure real dimensions")
    require(importedCharacter.record.geometry.alpha == .present, "import must detect non-opaque image pixels")
    require(importedLayer.record.geometry.alpha == .absent, "an opaque RGBA image must not be mislabeled as transparent")
    require(importedCharacter.record.approval.state == .proposed, "import must not manufacture a human approval")

    let importedManifest = AssetManifest(libraryID: "import-regression", assets: [
        withApproval(importedMask.record, .approved),
        withApproval(importedCharacter.record, .approved),
        withApproval(importedLayer.record, .approved),
    ])
    let manifestURL = libraryRoot.appending(path: "pilot-kit.json")
    try AssetManifestStore.save(importedManifest, to: manifestURL)
    let importedLibrary = try AssetLibrary(manifestURL: manifestURL, libraryRoot: libraryRoot)
    let report = AssetKitInspector.inspect(
        importedLibrary,
        request: AssetKitSceneRequest(sceneVersion: "train-v1", identityVersion: "tabi-v1", assetPins: [characterIdentity, maskIdentity, layerIdentity])
    )
    require(report.requiresHumanIdentityReview, "unresolved TABI identity differences must be exposed for review")
    require(!report.isCompositionReady, "unresolved identity differences must prevent automatic composition readiness")
    require(report.findings.contains(where: { $0.code == .unresolvedIdentityDifference && $0.identity == characterIdentity }), "identity finding must identify the affected asset")

    let incompatibleReport = AssetKitInspector.inspect(
        importedLibrary,
        request: AssetKitSceneRequest(sceneVersion: "train-v2", identityVersion: "tabi-v1", assetPins: [characterIdentity, maskIdentity])
    )
    require(incompatibleReport.findings.contains(where: { $0.code == .sceneIncompatible }), "scene-incompatible layers must be reported before composition")

    let opaqueAsTransparent = AssetRecord(
        identity: importedLayer.record.identity,
        kind: importedLayer.record.kind,
        relativeMediaPath: importedLayer.record.relativeMediaPath,
        sha256: importedLayer.record.sha256,
        provenance: importedLayer.record.provenance,
        rights: importedLayer.record.rights,
        geometry: AssetGeometry(
            width: 2,
            height: 3,
            alpha: .present,
            compatibleSceneVersions: ["train-v1"],
            compatibleIdentityVersions: ["tabi-v1"]
        ),
        approval: importedLayer.record.approval
    )
    require(hasIssue(AssetManifestValidator.validate(AssetManifest(libraryID: "opaque-alpha", assets: [opaqueAsTransparent]), libraryRoot: libraryRoot)) {
        if case .alphaMismatch(let identity, .present, .absent) = $0 { return identity == layerIdentity }
        return false
    }, "declared alpha must match inspected pixels, not only an image channel")

    let mismatchedMask = AssetRecord(
        identity: importedMask.record.identity,
        kind: importedMask.record.kind,
        relativeMediaPath: importedMask.record.relativeMediaPath,
        sha256: importedMask.record.sha256,
        provenance: importedMask.record.provenance,
        rights: importedMask.record.rights,
        geometry: AssetGeometry(width: 3, height: 3, alpha: .present, compatibleSceneVersions: ["train-v1"], compatibleIdentityVersions: ["tabi-v1"]),
        approval: importedMask.record.approval
    )
    require(hasIssue(AssetManifestValidator.validate(AssetManifest(libraryID: "bad-mask", assets: [mismatchedMask, importedCharacter.record]), libraryRoot: libraryRoot)) {
        if case .invalidMask(let identity, let mask, let message) = $0 { return identity == characterIdentity && mask == maskIdentity && message.contains("dimensions") }
        return false
    }, "mask and masked asset dimensions must agree")

    let nonMaskReference = AssetRecord(
        identity: importedLayer.record.identity,
        kind: .layer,
        relativeMediaPath: importedLayer.record.relativeMediaPath,
        sha256: importedLayer.record.sha256,
        provenance: importedLayer.record.provenance,
        rights: importedLayer.record.rights,
        geometry: importedLayer.record.geometry,
        approval: importedLayer.record.approval
    )
    let characterWithLayerMask = AssetRecord(
        identity: importedCharacter.record.identity,
        kind: importedCharacter.record.kind,
        relativeMediaPath: importedCharacter.record.relativeMediaPath,
        sha256: importedCharacter.record.sha256,
        provenance: importedCharacter.record.provenance,
        rights: importedCharacter.record.rights,
        geometry: AssetGeometry(width: 2, height: 3, alpha: .present, mask: layerIdentity, compatibleSceneVersions: ["train-v1"], compatibleIdentityVersions: ["tabi-v1"]),
        approval: importedCharacter.record.approval
    )
    require(hasIssue(AssetManifestValidator.validate(AssetManifest(libraryID: "wrong-mask-type", assets: [nonMaskReference, characterWithLayerMask]), libraryRoot: libraryRoot)) {
        if case .invalidMask(let identity, let mask, let message) = $0 { return identity == characterIdentity && mask == layerIdentity && message.contains("not a mask") }
        return false
    }, "a layer cannot be passed off as a mask")

    do {
        _ = try AssetKitImporter.importOriginal(
            sharedImport(AssetIdentity(assetID: "invalid-pivot", version: "v1"), .image, characterSource, nil, AssetPoint(x: 1.2, y: 1), []),
            into: libraryRoot,
            now: fixtureDate
        )
        require(false, "out-of-bounds pivot must reject before publication")
    } catch AssetManifestError.invalidManifest { }
    require(!FileManager.default.fileExists(atPath: libraryRoot.appending(path: "originals/invalid-pivot/v1/tabi-seat.png").path), "rejected import must not leave a published original")
    do {
        _ = try AssetKitImporter.importOriginal(
            sharedImport(AssetIdentity(assetID: "unsafe-library", version: "v1"), .image, characterSource, nil, nil, []),
            into: URL(fileURLWithPath: "/"),
            now: fixtureDate
        )
        require(false, "filesystem root must not be accepted as an asset library")
    } catch AssetManifestError.invalidManifest { }
    let repositoryRoot = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
    let repositoryLibrary = repositoryRoot.appending(path: "companion/.build/rejected-asset-library-\(UUID().uuidString)")
    do {
        let unexpected = try AssetKitImporter.importOriginal(
            sharedImport(AssetIdentity(assetID: "repository-library", version: "v1"), .image, characterSource, nil, nil, []),
            into: repositoryLibrary,
            now: fixtureDate
        )
        try? FileManager.default.removeItem(at: repositoryLibrary)
        require(false, "a path inside the Git worktree must not be accepted as an asset library: \(unexpected.importedURL.path)")
    } catch AssetManifestError.invalidManifest(let message) {
        require(message.contains("outside Git"), "repository-contained import must explain the external-library boundary")
    }
    require(!FileManager.default.fileExists(atPath: repositoryLibrary.path), "rejected repository-contained import must not create a library")
    // Both .git directories and linked-worktree .git files are boundaries,
    // including an external-looking symlink followed by nonexistent children.
    for markerIsDirectory in [false, true] {
        let repository = externalRoot.appending(path: "owned-repository-\(markerIsDirectory)")
        try FileManager.default.createDirectory(at: repository, withIntermediateDirectories: false)
        let marker = repository.appending(path: ".git")
        if markerIsDirectory {
            try FileManager.default.createDirectory(at: marker, withIntermediateDirectories: false)
        } else {
            try Data("gitdir: owned-fixture".utf8).write(to: marker)
        }
        let alias = externalRoot.appending(path: "repository-alias-\(markerIsDirectory)")
        try FileManager.default.createSymbolicLink(at: alias, withDestinationURL: repository)
        for base in [repository, alias] {
            let rejectedRoot = base.appending(path: "missing/library")
            do {
                _ = try AssetKitImporter.importOriginal(
                    sharedImport(AssetIdentity(assetID: "repository-alias", version: "v1"), .image, characterSource, nil, nil, []),
                    into: rejectedRoot
                )
                require(false, "repository aliases and new descendants must reject")
            } catch AssetManifestError.invalidManifest(let message) {
                require(message.contains("outside Git"), "repository alias rejection must explain the boundary")
            }
            require(!FileManager.default.fileExists(atPath: rejectedRoot.path), "repository alias rejection must precede writes")
        }
    }
    let bareRepository = externalRoot.appending(path: "owned-bare-repository")
    try FileManager.default.createDirectory(at: bareRepository.appending(path: "objects"), withIntermediateDirectories: true)
    try FileManager.default.createDirectory(at: bareRepository.appending(path: "refs"), withIntermediateDirectories: false)
    try Data("ref: refs/heads/main\n".utf8).write(to: bareRepository.appending(path: "HEAD"))
    for rejectedRoot in [bareRepository, bareRepository.appending(path: "new/library")] {
        do {
            _ = try AssetKitImporter.importOriginal(
                sharedImport(AssetIdentity(assetID: "bare-repository", version: "v1"), .image, characterSource, nil, nil, []),
                into: rejectedRoot
            )
            require(false, "bare Git repositories must reject asset imports")
        } catch AssetManifestError.invalidManifest(let message) {
            require(message.contains("outside Git"), "bare repository rejection must explain the boundary")
        }
        require(!FileManager.default.fileExists(atPath: rejectedRoot.appending(path: "originals").path), "bare repository rejection must precede writes")
    }
    let linkedLibrary = externalRoot.appending(path: "linked-library")
    try FileManager.default.createDirectory(at: linkedLibrary, withIntermediateDirectories: false)
    try FileManager.default.createSymbolicLink(at: linkedLibrary.appending(path: "originals"), withDestinationURL: externalRoot)
    do {
        _ = try AssetKitImporter.importOriginal(
            sharedImport(AssetIdentity(assetID: "escape", version: "v1"), .image, characterSource, nil, nil, []),
            into: linkedLibrary
        )
        require(false, "import must reject a symlinked originals directory")
    } catch AssetManifestError.unsafeMediaPath { }
    require(!FileManager.default.fileExists(atPath: externalRoot.appending(path: "escape").path), "symlink rejection must precede writes")
    print("asset-kit-import-regression=PASS")
} catch {
    fputs("asset-kit-import-regression=FAIL: \(error.localizedDescription)\n", stderr)
    exit(1)
}

final class FakeAnimationProvider: AnimationJobProvider, @unchecked Sendable {
    let providerID = "owned-fake"
    var submissions: [AnimationSubmissionIdentity] = []
    var polls: [String] = []
    var cancellations: [String] = []
    var shouldLoseSubmissionResponse = false
    var nextPoll = AnimationProviderPoll(state: .queued)
    var cancelResult = AnimationProviderPoll(state: .cancelled)
    var onSubmit: ((AnimationSubmissionIdentity) -> Void)?

    func submit(request: AnimationRequest, identity: AnimationSubmissionIdentity) throws -> String {
        submissions.append(identity)
        onSubmit?(identity)
        if shouldLoseSubmissionResponse { throw AssetManifestError.unreadableManifest("owned fake disconnected") }
        return "fake-provider-job-\(identity.attempt)"
    }

    func poll(providerJobID: String) throws -> AnimationProviderPoll {
        polls.append(providerJobID)
        return nextPoll
    }

    func cancel(providerJobID: String) throws -> AnimationProviderPoll {
        cancellations.append(providerJobID)
        return cancelResult
    }
}

func animationRequest(prompt: String = "TABI breathes calmly", cost: Int64? = 60, maximumAttempts: Int = 2, references: [AssetIdentity] = [AssetIdentity(assetID: "owned-tabi-still", version: "v1")]) -> AnimationRequest {
    AnimationRequest(
        providerID: "owned-fake",
        model: "owned-model-v1",
        options: ["duration": "5", "ratio": "1280:720"],
        prompt: prompt,
        referenceAssets: references,
        seed: "fixture-seed",
        estimatedCost: cost.map { AnimationCost(currency: "usd", amountCents: $0) },
        maximumAttempts: maximumAttempts
    )
}

do {
    let root = FileManager.default.temporaryDirectory.appending(path: "melotrail-tabi-animation-jobs-\(UUID().uuidString)")
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
    directories.append(root)
    let ledgerURL = root.appending(path: "jobs.json")
    let coordinator = AnimationJobCoordinator(ledgerURL: ledgerURL, pollBaseDelay: 2)
    let provider = FakeAnimationProvider()
    let budget = AnimationBudget(budgetID: "owned-pilot", maximumCost: AnimationCost(currency: "USD", amountCents: 200))
    let now = Date(timeIntervalSince1970: 1_700_000_100)

    do {
        _ = try coordinator.submit(animationRequest(cost: nil), to: budget, provider: provider, now: now)
        require(false, "unknown-cost animation requests must reject before provider submission")
    } catch AnimationJobError.unknownCost { }
    require(provider.submissions.isEmpty, "unknown-cost rejection must issue no provider request")
    require(!FileManager.default.fileExists(atPath: ledgerURL.path), "unknown-cost rejection must not create a job ledger")

    let assetLibraryFixture = try makeAssetLibrary()
    directories.append(assetLibraryFixture.root)
    let approvedLedger = root.appending(path: "approved-jobs.json")
    let approvedCoordinator = AnimationJobCoordinator(ledgerURL: approvedLedger)
    do {
        _ = try approvedCoordinator.submitApproved(
            animationRequest(prompt: "proposed asset must not submit", references: [assetLibraryFixture.proposed]),
            to: budget,
            assetLibrary: try AssetLibrary(manifestURL: assetLibraryFixture.manifestURL, libraryRoot: assetLibraryFixture.root),
            provider: provider,
            now: now
        )
        require(false, "provider admission must reject a proposed reference pin")
    } catch AssetManifestError.assetNotApproved { }
    require(provider.submissions.isEmpty, "unapproved asset rejection must issue no provider request")
    let approvedSubmission = try approvedCoordinator.submitApproved(
        animationRequest(prompt: "approved asset contract", references: [assetLibraryFixture.approved]),
        to: budget,
        assetLibrary: try AssetLibrary(manifestURL: assetLibraryFixture.manifestURL, libraryRoot: assetLibraryFixture.root),
        provider: provider,
        now: now
    )
    require(approvedSubmission.latestAttempt?.state == .submitted && provider.submissions.count == 1, "asset-aware admission must bind exact approved reference pins")
    provider.onSubmit = { identity in
        guard let duringSubmit = try? AnimationJobStore.load(from: ledgerURL),
              let persisted = duringSubmit.jobs.first(where: { $0.requestFingerprint == identity.requestFingerprint }) else {
            require(false, "submission must be recorded before the provider is called")
            return
        }
        require(persisted.latestAttempt?.state == .submitting, "provider submission must observe the durable pre-poll submitting state")
    }

    let submitted = try coordinator.submit(animationRequest(), to: budget, provider: provider, now: now)
    require(submitted.latestAttempt?.state == .submitted, "admitted request must persist its returned provider job ID")
    require(provider.submissions.count == 2, "first admitted request must submit exactly once")
    let reloaded = try AnimationJobStore.load(from: ledgerURL)
    require(reloaded.jobs == [submitted], "submitted job must survive ledger reopen exactly")
    require(reloaded.jobs[0].latestAttempt?.identity.idempotencyKey.contains(reloaded.jobs[0].requestFingerprint) == true, "submission identity must bind the persisted fingerprint")
    do {
        _ = try coordinator.submit(animationRequest(prompt: "parallel paid take", cost: 1), to: budget, provider: provider, now: now)
        require(false, "budget admission must bound concurrent provider requests")
    } catch AnimationJobError.concurrencyLimit { }
    require(provider.submissions.count == 2, "concurrency rejection must issue no provider request")

    let duplicate = try coordinator.submit(animationRequest(), to: budget, provider: provider, now: now.addingTimeInterval(1))
    require(duplicate.jobID == submitted.jobID && provider.submissions.count == 2, "identical request must reuse its durable submission instead of spending again")

    provider.nextPoll = AnimationProviderPoll(state: .rateLimited, retryAfter: 9)
    let rateLimited = try coordinator.poll(jobID: submitted.jobID, provider: provider, now: now.addingTimeInterval(2))
    require(rateLimited.latestAttempt?.state == .submitted && rateLimited.latestAttempt?.nextPollAt == now.addingTimeInterval(11), "rate limits must remain queryable and persist provider backoff")
    _ = try coordinator.poll(jobID: submitted.jobID, provider: provider, now: now.addingTimeInterval(3))
    require(provider.polls.count == 1, "persisted rate-limit backoff must prevent eager repeat polling")
    provider.nextPoll = AnimationProviderPoll(state: .failed, actualCost: AnimationCost(currency: "USD", amountCents: 55), failure: "owned fixture failure")
    let failed = try coordinator.recover(provider: provider, now: now.addingTimeInterval(12))
    require(failed.count == 1 && failed[0].latestAttempt?.state == .failed, "restart recovery must query the existing provider job rather than submit anew")
    require(provider.submissions.count == 2 && provider.polls == ["fake-provider-job-1", "fake-provider-job-1"], "polling/recovery must never resubmit an existing request")
    require(failed[0].latestAttempt?.actualCost?.amountCents == 55, "actual provider cost must replace the reservation for future admission")

    provider.nextPoll = AnimationProviderPoll(state: .queued)
    let retried = try coordinator.restart(jobID: submitted.jobID, provider: provider, now: now.addingTimeInterval(13))
    require(retried.attempts.count == 2 && retried.latestAttempt?.identity.attempt == 2 && provider.submissions.count == 3, "failed jobs must restart with a bounded new attempt identity")
    do {
        _ = try coordinator.restart(jobID: submitted.jobID, provider: provider, now: now.addingTimeInterval(14))
        require(false, "in-flight jobs must not be blindly restarted")
    } catch AnimationJobError.attemptNotRestartable { }

    let cancelled = try coordinator.cancel(jobID: submitted.jobID, provider: provider, now: now.addingTimeInterval(15))
    require(cancelled.latestAttempt?.state == .cancelled && provider.cancellations == ["fake-provider-job-2"], "cancel must target only the persisted provider job and record its terminal state")
    do {
        _ = try coordinator.restart(jobID: submitted.jobID, provider: provider, now: now.addingTimeInterval(16))
        require(false, "retry limit must prevent a third paid attempt")
    } catch AnimationJobError.attemptLimitReached { }

    do {
        _ = try coordinator.submit(animationRequest(prompt: "another paid take", cost: 146), to: budget, provider: provider, now: now)
        require(false, "budget admission must include actual and reserved attempt costs")
    } catch AnimationJobError.budgetExceeded { }
    require(provider.submissions.count == 3, "budget rejection must issue no provider request")

    let uncertainRoot = FileManager.default.temporaryDirectory.appending(path: "melotrail-tabi-animation-uncertain-\(UUID().uuidString)")
    try FileManager.default.createDirectory(at: uncertainRoot, withIntermediateDirectories: false)
    directories.append(uncertainRoot)
    let uncertainCoordinator = AnimationJobCoordinator(ledgerURL: uncertainRoot.appending(path: "jobs.json"))
    let uncertainProvider = FakeAnimationProvider()
    uncertainProvider.shouldLoseSubmissionResponse = true
    let uncertain = try uncertainCoordinator.submit(animationRequest(prompt: "connection-loss take"), to: budget, provider: uncertainProvider, now: now)
    require(uncertain.latestAttempt?.state == .submissionUncertain, "ambiguous submission must remain durable and visibly uncertain")
    let uncertainRecovery = try uncertainCoordinator.recover(provider: uncertainProvider, now: now)
    require(uncertainRecovery.isEmpty, "restart must not poll or resubmit an uncertain request without a provider ID")
    let uncertainDuplicate = try uncertainCoordinator.submit(animationRequest(prompt: "connection-loss take"), to: budget, provider: uncertainProvider, now: now)
    require(uncertainDuplicate.jobID == uncertain.jobID && uncertainProvider.submissions.count == 1, "uncertain paid submissions must never be blindly retried")
    do {
        _ = try uncertainCoordinator.restart(jobID: uncertain.jobID, provider: uncertainProvider, now: now)
        require(false, "uncertain submissions must require provider reconciliation, not restart")
    } catch AnimationJobError.attemptNotRestartable { }
    print("animation-job-regression=PASS")
} catch {
    fputs("animation-job-regression=FAIL: \(error.localizedDescription)\n", stderr)
    exit(1)
}

final class FixtureRunwayTransport: ProviderHTTPTransport, @unchecked Sendable {
    enum Result {
        case response(ProviderHTTPResponse)
        case timeout
    }

    var results: [Result]
    var requests: [URLRequest] = []

    init(_ results: [Result]) { self.results = results }

    func execute(_ request: URLRequest, timeout: TimeInterval, maximumBytes: Int) throws -> ProviderHTTPResponse {
        requests.append(request)
        require(timeout == 7, "Runway adapter must pass its bounded timeout to transport")
        guard !results.isEmpty else { throw AssetManifestError.unreadableManifest("fixture transport exhausted") }
        switch results.removeFirst() {
        case .response(let response): return response
        case .timeout: throw AssetManifestError.unreadableManifest("Authorization: Bearer must-not-leak")
        }
    }
}

final class SwappingRunwayProvider: AnimationJobProvider, Sendable {
    let providerID = RunwayPreset.providerID
    let wrapped: RunwayProvider
    let source: URL
    let beforePreparation: Bool
    init(wrapped: RunwayProvider, source: URL, beforePreparation: Bool) {
        self.wrapped = wrapped; self.source = source; self.beforePreparation = beforePreparation
    }
    func validate(request: AnimationRequest) throws { try wrapped.validate(request: request) }
    func prepareSubmission(request: AnimationRequest) throws -> @Sendable (AnimationSubmissionIdentity) throws -> String {
        if beforePreparation { try Data("replaced source".utf8).write(to: source, options: .atomic) }
        let prepared = try wrapped.prepareSubmission(request: request)
        if !beforePreparation { try Data("replaced source".utf8).write(to: source, options: .atomic) }
        return prepared
    }
    func submit(request: AnimationRequest, identity: AnimationSubmissionIdentity) throws -> String { try wrapped.submit(request: request, identity: identity) }
    func poll(providerJobID: String) throws -> AnimationProviderPoll { try wrapped.poll(providerJobID: providerJobID) }
    func cancel(providerJobID: String) throws -> AnimationProviderPoll { try wrapped.cancel(providerJobID: providerJobID) }
}

do {
    let libraryFixture = try makeAssetLibrary()
    directories.append(libraryFixture.root)
    let library = try AssetLibrary(manifestURL: libraryFixture.manifestURL, libraryRoot: libraryFixture.root)
    do {
        _ = try RunwayCredentials(environment: [:])
        require(false, "Runway construction must reject missing secure credentials")
    } catch RunwayProviderError.missingCredentials { }
    let credentials = try RunwayCredentials(environment: ["RUNWAYML_API_SECRET": "must-not-leak"])
    for endpoint in ["https://attacker.example", "https://api.dev.runwayml.com:444", "https://user@api.dev.runwayml.com", "https://api.dev.runwayml.com/other"] {
        do {
            _ = try RunwayProvider(credentials: credentials, assetLibrary: library, transport: FixtureRunwayTransport([]), baseURL: URL(string: endpoint)!)
            require(false, "Runway credentials must be confined to the canonical endpoint")
        } catch RunwayProviderError.invalidRequest { }
    }
    for beforePreparation in [false, true] {
        let fixture = try makeAssetLibrary()
        directories.append(fixture.root)
        let library = try AssetLibrary(manifestURL: fixture.manifestURL, libraryRoot: fixture.root)
        let approved = try library.validatedApprovedAssets(pinned: [fixture.approved])[0]
        let source = fixture.root.appendingPathComponent(approved.relativeMediaPath)
        let originalBytes = try Data(contentsOf: source)
        let transport = FixtureRunwayTransport([.response(ProviderHTTPResponse(statusCode: 200, body: Data("{\"id\":\"frozen-task\"}".utf8)))])
        let wrapped = try RunwayProvider(credentials: credentials, assetLibrary: library, transport: transport, timeout: 7)
        let provider = SwappingRunwayProvider(wrapped: wrapped, source: source, beforePreparation: beforePreparation)
        let ledger = fixture.root.appendingPathComponent("snapshot-jobs.json")
        do {
            _ = try AnimationJobCoordinator(ledgerURL: ledger).submit(RunwayPreset.request(prompt: "snapshot fixture", referenceAsset: fixture.approved, estimatedCost: AnimationCost(currency: "USD", amountCents: 60)), to: AnimationBudget(budgetID: "snapshot", maximumCost: AnimationCost(currency: "USD", amountCents: 60)), provider: provider)
            require(!beforePreparation, "changed source before snapshot must reject")
            let payload = try JSONSerialization.jsonObject(with: transport.requests[0].httpBody!) as! [String: Any]
            require(payload["promptImage"] as? String == "data:image/png;base64," + originalBytes.base64EncodedString(), "source replacement after preparation must not change submitted bytes")
        } catch {
            require(beforePreparation, "verified snapshot submission must succeed")
            require(transport.requests.isEmpty && !FileManager.default.fileExists(atPath: ledger.path), "source replacement before preparation must not reserve budget or call HTTP")
        }
    }
    do {
        let root = libraryFixture.root.appendingPathComponent("mislabeled")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        let image = root.appendingPathComponent("png-bytes.jpg")
        try writeOwnedPNG(to: image, width: 2, height: 3)
        let identity = AssetIdentity(assetID: "mislabeled", version: "v1")
        let record = fixtureRecord(identity: identity, path: image.lastPathComponent, digest: try AssetDigest.sha256(of: image))
        let manifest = root.appendingPathComponent("manifest.json")
        try AssetManifestStore.save(AssetManifest(libraryID: "mislabeled", assets: [record]), to: manifest)
        let transport = FixtureRunwayTransport([])
        let provider = try RunwayProvider(credentials: credentials, assetLibrary: AssetLibrary(manifestURL: manifest, libraryRoot: root), transport: transport, timeout: 7)
        let ledger = root.appendingPathComponent("jobs.json")
        do {
            _ = try AnimationJobCoordinator(ledgerURL: ledger).submit(RunwayPreset.request(prompt: "mislabeled fixture", referenceAsset: identity, estimatedCost: AnimationCost(currency: "USD", amountCents: 60)), to: AnimationBudget(budgetID: "test", maximumCost: AnimationCost(currency: "USD", amountCents: 60)), provider: provider)
            require(false, "mislabeled image must reject before admission")
        } catch RunwayProviderError.invalidRequest { }
        require(transport.requests.isEmpty && !FileManager.default.fileExists(atPath: ledger.path), "mislabeled bytes must not reserve budget or call HTTP")
    }
    for oversized in [false, true] {
        let rejectedRoot = libraryFixture.root.appendingPathComponent("invalid-input-\(oversized)")
        try FileManager.default.createDirectory(at: rejectedRoot, withIntermediateDirectories: false)
        let source = rejectedRoot.appendingPathComponent("input.png")
        try writeOwnedPNG(to: source, width: oversized ? 2 : 1, height: 3)
        if oversized {
            let handle = try FileHandle(forWritingTo: source)
            try handle.seekToEnd(); try handle.write(contentsOf: Data(repeating: 0, count: 4_000_000)); try handle.close()
        }
        let identity = AssetIdentity(assetID: "invalid-input", version: "v1")
        let record = fixtureRecord(identity: identity, path: "input.png", digest: try AssetDigest.sha256(of: source), width: oversized ? 2 : 1)
        let manifest = rejectedRoot.appendingPathComponent("manifest.json")
        try AssetManifestStore.save(AssetManifest(libraryID: "rejected", assets: [record]), to: manifest)
        let transport = FixtureRunwayTransport([])
        let rejectedProvider = try RunwayProvider(credentials: credentials, assetLibrary: AssetLibrary(manifestURL: manifest, libraryRoot: rejectedRoot), transport: transport, timeout: 7)
        let ledger = rejectedRoot.appendingPathComponent("jobs.json")
        do {
            _ = try AnimationJobCoordinator(ledgerURL: ledger).submit(RunwayPreset.request(prompt: "owned invalid fixture", referenceAsset: identity, estimatedCost: AnimationCost(currency: "USD", amountCents: 60)), to: AnimationBudget(budgetID: "test", maximumCost: AnimationCost(currency: "USD", amountCents: 60)), provider: rejectedProvider)
            require(false, "invalid provider image must reject before durable admission")
        } catch RunwayProviderError.invalidRequest { }
        require(transport.requests.isEmpty && !FileManager.default.fileExists(atPath: ledger.path), "invalid image must not reserve budget or call HTTP")
    }
    let request = RunwayPreset.request(
        prompt: "TABI takes one calm breath",
        referenceAsset: libraryFixture.approved,
        estimatedCost: AnimationCost(currency: "USD", amountCents: 60)
    )
    let transport = FixtureRunwayTransport([
        .response(ProviderHTTPResponse(statusCode: 201, body: Data("{\"id\":\"runway-task-1\",\"status\":\"PENDING\"}".utf8))),
        .response(ProviderHTTPResponse(statusCode: 429, headers: ["Retry-After": "12"])),
        .response(ProviderHTTPResponse(statusCode: 200, body: Data("{\"status\":\"SUCCEEDED\",\"output\":[\"https://owned.example/clip.mov\",\"http://reject.example/clip.mov\"]}".utf8))),
    ])
    let runway = try RunwayProvider(credentials: credentials, assetLibrary: library, transport: transport, timeout: 7)
    let providerJobID = try runway.submit(request: request, identity: AnimationSubmissionIdentity(requestFingerprint: "fixture", attempt: 1))
    require(providerJobID == "runway-task-1", "Runway adapter must return its provider task ID")
    require(transport.requests.count == 1, "Runway adapter must make one submission request")
    let submittedRequest = transport.requests[0]
    require(submittedRequest.url?.path == "/v1/image_to_video" && submittedRequest.httpMethod == "POST", "Runway adapter must use the reviewed image-to-video endpoint")
    require(submittedRequest.value(forHTTPHeaderField: "X-Runway-Version") == RunwayPreset.apiVersion, "Runway adapter must pin the reviewed API version")
    require(submittedRequest.value(forHTTPHeaderField: "Authorization") == "Bearer must-not-leak", "Runway adapter must send credentials only as an authorization header")
    let submittedJSON = try JSONSerialization.jsonObject(with: submittedRequest.httpBody ?? Data()) as? [String: Any]
    require(submittedJSON?["model"] as? String == "gen4.5" && submittedJSON?["ratio"] as? String == "1280:720" && submittedJSON?["duration"] as? Int == 5, "Runway adapter must submit explicit reviewed model and options")
    require((submittedJSON?["promptImage"] as? String)?.hasPrefix("data:image/png;base64,") == true, "Runway adapter must resolve the exact approved still into a data URI")
    let rateLimited = try runway.poll(providerJobID: providerJobID)
    require(rateLimited.state == .rateLimited && rateLimited.retryAfter == 12, "Runway rate limits must become persisted-coordinator backoff inputs")
    let outputs = try runway.outputURLs(providerJobID: providerJobID)
    require(outputs.map(\.absoluteString) == ["https://owned.example/clip.mov"], "Runway outputs must retain only HTTPS media URLs")

    let uncertainRoot = FileManager.default.temporaryDirectory.appending(path: "melotrail-runway-timeout-\(UUID().uuidString)")
    try FileManager.default.createDirectory(at: uncertainRoot, withIntermediateDirectories: false)
    directories.append(uncertainRoot)
    let timeoutProvider = try RunwayProvider(credentials: credentials, assetLibrary: library, transport: FixtureRunwayTransport([.timeout]), timeout: 7)
    let uncertain = try AnimationJobCoordinator(ledgerURL: uncertainRoot.appending(path: "jobs.json")).submit(
        request,
        to: AnimationBudget(budgetID: "timeout", maximumCost: AnimationCost(currency: "USD", amountCents: 60)),
        provider: timeoutProvider,
        now: fixtureDate
    )
    require(uncertain.latestAttempt?.state == .submissionUncertain, "Runway transport timeout must preserve uncertain submission instead of retrying")
    require(!(uncertain.latestAttempt?.failure?.contains("must-not-leak") ?? true), "Runway timeout diagnostics must redact credentials")

    let quarantineRoot = FileManager.default.temporaryDirectory.appending(path: "melotrail-runway-quarantine-\(UUID().uuidString)")
    directories.append(quarantineRoot)
    do {
        _ = try AnimationOutputQuarantine.store(response: ProviderHTTPResponse(statusCode: 200, headers: ["Content-Length": "5"], body: Data("abc".utf8)), expectedSHA256: nil, into: quarantineRoot)
        require(false, "partial downloads must not enter output quarantine")
    } catch AnimationOutputError.partialDownload { }
    require(!FileManager.default.fileExists(atPath: quarantineRoot.path), "partial download rejection must happen before quarantine writes")
    do {
        _ = try AnimationOutputQuarantine.store(response: ProviderHTTPResponse(statusCode: 200, body: Data("digest-mismatch".utf8)), expectedSHA256: String(repeating: "0", count: 64), into: quarantineRoot)
        require(false, "digest-mismatched output must not enter quarantine")
    } catch AnimationOutputError.digestMismatch { }
    let quarantineContentsAfterDigestMismatch = try FileManager.default.contentsOfDirectory(atPath: quarantineRoot.path)
    require(quarantineContentsAfterDigestMismatch.isEmpty, "digest mismatch must leave no staged output behind")

    let ownedClip = try OwnedMediaSpike.run()
    directories.append(ownedClip.outputURL.deletingLastPathComponent())
    let clipData = try Data(contentsOf: ownedClip.outputURL)
    let clipDigest = try AssetDigest.sha256(of: ownedClip.outputURL)
    let extensionlessStage = ownedClip.outputURL.deletingLastPathComponent().appendingPathComponent("clip.tmp")
    try clipData.write(to: extensionlessStage)
    let stagedFacts = try AssetMediaInspector.inspect(kind: .animationClip, url: extensionlessStage)
    require(stagedFacts.width == 320 && stagedFacts.height == 180 && stagedFacts.durationSeconds == 1, "temporary suffix must not hide valid MOV content")
    for response in [
        ProviderHTTPResponse(statusCode: 206, headers: ["Content-Length": "\(clipData.count)"], body: clipData),
        ProviderHTTPResponse(statusCode: 500, body: clipData)
    ] {
        do {
            _ = try AnimationOutputQuarantine.store(response: response, expectedSHA256: nil, into: quarantineRoot)
            require(false, "non-complete HTTP responses must never publish clips")
        } catch AnimationOutputError.downloadRejected { }
    }
    do {
        _ = try AnimationOutputQuarantine.store(response: ProviderHTTPResponse(statusCode: 200, body: Data("not a movie".utf8)), expectedSHA256: nil, into: quarantineRoot)
        require(false, "unreadable clip bytes must remain rejected")
    } catch AnimationOutputError.invalidClip { }
    let leftovers = try FileManager.default.contentsOfDirectory(atPath: quarantineRoot.path)
    require(leftovers.isEmpty, "failed video validation must remove staged bytes")
    let repoAlias = ownedClip.outputURL.deletingLastPathComponent().appendingPathComponent("repo-alias")
    let repository = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    try FileManager.default.createSymbolicLink(at: repoAlias, withDestinationURL: repository)
    do {
        _ = try AnimationOutputQuarantine.store(response: ProviderHTTPResponse(statusCode: 200, body: clipData), expectedSHA256: nil, into: repoAlias.appendingPathComponent("missing/quarantine"))
        require(false, "quarantine must reject repository aliases before writes")
    } catch AnimationOutputError.invalidDestination { }
    let downloadTransport = FixtureRunwayTransport([.response(ProviderHTTPResponse(statusCode: 200, headers: ["Content-Length": "\(clipData.count)"], body: clipData))])
    let quarantined = try AnimationOutputQuarantine.download(outputURL: URL(string: "https://owned.example/clip.mov")!, transport: downloadTransport, timeout: 7, expectedSHA256: clipDigest, into: quarantineRoot)
    require(quarantined.sha256 == clipDigest && quarantined.url.lastPathComponent == "\(clipDigest).mov", "digest-valid provider output must be immutable quarantine evidence")
    require(downloadTransport.requests.first?.value(forHTTPHeaderField: "Authorization") == nil, "provider credentials must not be sent to an output host")
    let duplicate = try AnimationOutputQuarantine.store(response: ProviderHTTPResponse(statusCode: 200, body: clipData), expectedSHA256: clipDigest, into: quarantineRoot)
    require(duplicate.url == quarantined.url, "identical output digest must reuse quarantined evidence without overwrite")

    // A separate owned MP4 fixture covers the provider's usual output container.
    let mp4URL = ownedClip.outputURL.deletingLastPathComponent().appendingPathComponent("owned.mp4")
    guard let exporter = AVAssetExportSession(asset: AVURLAsset(url: ownedClip.outputURL), presetName: AVAssetExportPresetMediumQuality) else {
        throw AnimationOutputError.invalidClip
    }
    exporter.outputURL = mp4URL
    exporter.outputFileType = .mp4
    let exportDone = DispatchSemaphore(value: 0)
    exporter.exportAsynchronously { exportDone.signal() }
    guard exportDone.wait(timeout: .now() + 30) == .success else { exporter.cancelExport(); throw AnimationOutputError.invalidClip }
    guard exporter.status == .completed else { throw AnimationOutputError.invalidClip }
    let mp4Data = try Data(contentsOf: mp4URL)
    let mp4 = try AnimationOutputQuarantine.store(response: ProviderHTTPResponse(statusCode: 200, body: mp4Data), expectedSHA256: nil, into: quarantineRoot)
    require(mp4.url.pathExtension == "mp4" && mp4.facts.width > 0, "MP4 bytes must retain their container through temporary staging")
    let mp4Digest = try AssetDigest.sha256(of: mp4URL)
    require(mp4.sha256 == mp4Digest, "quarantine must never transcode provider bytes")

    let symlinkQuarantine = quarantineRoot.appendingPathComponent("symlink-case")
    try FileManager.default.createDirectory(at: symlinkQuarantine, withIntermediateDirectories: false)
    try FileManager.default.createSymbolicLink(at: symlinkQuarantine.appendingPathComponent("\(clipDigest).mov"), withDestinationURL: ownedClip.outputURL)
    do {
        _ = try AnimationOutputQuarantine.store(response: ProviderHTTPResponse(statusCode: 200, body: clipData), expectedSHA256: clipDigest, into: symlinkQuarantine)
        require(false, "matching-digest symlink must never be accepted as immutable evidence")
    } catch AnimationOutputError.invalidDestination { }

    let manualLibrary = FileManager.default.temporaryDirectory.appending(path: "melotrail-manual-clip-library-\(UUID().uuidString)")
    directories.append(manualLibrary)
    let manual = try ManualAnimationClipImporter.importOwned(
        ManualAnimationClipImportRequest(assetImport: AssetImportRequest(
            identity: AssetIdentity(assetID: "owned-loop", version: "v1"),
            kind: .animationClip,
            sourceURL: quarantined.url,
            provenance: AssetProvenance(originalSource: "owned local clip", creator: "fixture", creationMethod: .owned, createdAt: fixtureDate),
            rights: AssetRights(ownershipOrLicense: "owned fixture", permittedUses: ["local review"]),
            importedBy: "fixture-user"
        )),
        into: manualLibrary,
        now: fixtureDate
    )
    require(manual.record.kind == .animationClip && manual.record.approval.state == .proposed, "manual clip import must retain proposed review status")
    let selectedClipDigestAfterImport = try AssetDigest.sha256(of: quarantined.url)
    require(selectedClipDigestAfterImport == clipDigest, "manual clip import must preserve the selected owned source")
    print("runway-adapter-and-manual-clip-regression=PASS")
} catch {
    fputs("runway-adapter-and-manual-clip-regression=FAIL: \(error.localizedDescription)\n", stderr)
    exit(1)
}


// Records every fake provider invocation separately so lost ledger updates or
// duplicate submissions cannot hide behind an overwritten event file.
final class ConcurrentAnimationProvider: AnimationJobProvider, Sendable {
    let providerID = "owned-fake"
    let events: URL
    init(events: URL) { self.events = events }
    func submit(request: AnimationRequest, identity: AnimationSubmissionIdentity) throws -> String {
        try Data(identity.idempotencyKey.utf8).write(to: events.appendingPathComponent(UUID().uuidString), options: .withoutOverwriting)
        Thread.sleep(forTimeInterval: 0.15)
        return identity.idempotencyKey
    }
    func poll(providerJobID: String) throws -> AnimationProviderPoll { AnimationProviderPoll(state: .failed) }
    func cancel(providerJobID: String) throws -> AnimationProviderPoll { AnimationProviderPoll(state: .cancelled) }
}

final class ConcurrentAnimationResults: @unchecked Sendable {
    private let lock = NSLock()
    private var values: [Int] = []
    func append(_ value: Int) { lock.lock(); defer { lock.unlock() }; values.append(value) }
    var statuses: [Int] { lock.lock(); defer { lock.unlock() }; return values }
}

func runAnimationSubmissionChild() -> Never {
    let args = CommandLine.arguments
    guard args.count == 9 else { exit(64) }
    let ledger = URL(fileURLWithPath: args[2]), events = URL(fileURLWithPath: args[3])
    let gate = URL(fileURLWithPath: args[4])
    do {
        try Data().write(to: gate.deletingLastPathComponent().appendingPathComponent("ready-" + args[5]))
        let deadline = ProcessInfo.processInfo.systemUptime + 10
        while !FileManager.default.fileExists(atPath: gate.path) {
            guard ProcessInfo.processInfo.systemUptime < deadline else { exit(70) }
            Thread.sleep(forTimeInterval: 0.01)
        }
        let budget = AnimationBudget(budgetID: "concurrent", maximumCost: AnimationCost(currency: "USD", amountCents: Int64(args[7])!), maximumInFlight: Int(args[8])!)
        _ = try AnimationJobCoordinator(ledgerURL: ledger).submit(animationRequest(prompt: args[6]), to: budget, provider: ConcurrentAnimationProvider(events: events))
        exit(0)
    } catch AnimationJobError.concurrencyLimit { exit(3) }
      catch AnimationJobError.budgetExceeded { exit(3) }
      catch { fputs("concurrent-child=FAIL: \(error)\n", stderr); exit(1) }
}

do {
    alarm(60)
    defer { alarm(0) }
    let root = FileManager.default.temporaryDirectory.appendingPathComponent("melotrail-animation-concurrent-\(UUID().uuidString)")
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
    defer { try? FileManager.default.removeItem(at: root) }
    // Identical requests, distinct requests exceeding in-flight/cost limits,
    // and two permitted jobs test both over-admission and lost updates.
    for (name, prompts, cap, inFlight, expectedJobs) in [
        ("identical", ["same", "same"], 120, 2, 1),
        ("in-flight", ["one", "two"], 120, 1, 1),
        ("cost", ["one", "two"], 60, 2, 1),
        ("both", ["one", "two"], 120, 2, 2)
    ] {
        let folder = root.appendingPathComponent(name)
        let events = folder.appendingPathComponent("events")
        try FileManager.default.createDirectory(at: events, withIntermediateDirectories: true)
        let ledger = folder.appendingPathComponent("jobs.json"), gate = folder.appendingPathComponent("go")
        let alias = folder.appendingPathComponent("alias")
        try FileManager.default.createSymbolicLink(at: alias, withDestinationURL: folder)
        var children: [Process] = []
        defer { for child in children where child.isRunning { child.terminate(); child.waitUntilExit() } }
        for (index, prompt) in prompts.enumerated() {
            let child = Process()
            child.executableURL = URL(fileURLWithPath: CommandLine.arguments[0]).standardizedFileURL
            // A directory alias must share the same lock as the canonical path.
            let childLedger = index == 0 ? ledger : alias.appendingPathComponent("jobs.json")
            child.arguments = ["--animation-submit", childLedger.path, events.path, gate.path, String(index), prompt, String(cap), String(inFlight)]
            child.standardInput = FileHandle.nullDevice
            try child.run(); children.append(child)
        }
        let deadline = ProcessInfo.processInfo.systemUptime + 10
        while !(0..<2).allSatisfy({ FileManager.default.fileExists(atPath: folder.appendingPathComponent("ready-\($0)").path) }) {
            require(ProcessInfo.processInfo.systemUptime < deadline, "concurrent children must reach the start barrier")
            Thread.sleep(forTimeInterval: 0.01)
        }
        try Data().write(to: gate)
        for child in children { child.waitUntilExit(); require([Int32(0), 3].contains(child.terminationStatus), "concurrent submission must complete without unexpected errors") }
        let jobs = try AnimationJobStore.load(from: ledger).jobs
        let calls = try FileManager.default.contentsOfDirectory(atPath: events.path)
        require(jobs.count == expectedJobs && calls.count == expectedJobs, "\(name): durable jobs and provider calls must match admission exactly")
        require(jobs.allSatisfy { $0.latestAttempt?.state == .submitted }, "concurrent completions must preserve every provider ID")
        if name == "identical" { require(children.allSatisfy { $0.terminationStatus == 0 }, "duplicate caller must reuse the durable job") }

        let threadEvents = folder.appendingPathComponent("thread-events")
        try FileManager.default.createDirectory(at: threadEvents, withIntermediateDirectories: false)
        let threadLedger = folder.appendingPathComponent("thread-jobs.json")
        let results = ConcurrentAnimationResults()
        DispatchQueue.concurrentPerform(iterations: 2) { index in
            do {
                _ = try AnimationJobCoordinator(ledgerURL: threadLedger).submit(
                    animationRequest(prompt: prompts[index]),
                    to: AnimationBudget(budgetID: "threads", maximumCost: AnimationCost(currency: "USD", amountCents: Int64(cap)), maximumInFlight: inFlight),
                    provider: ConcurrentAnimationProvider(events: threadEvents)
                )
                results.append(0)
            } catch AnimationJobError.concurrencyLimit { results.append(3) }
              catch AnimationJobError.budgetExceeded { results.append(3) }
              catch { results.append(1) }
        }
        let threadJobs = try AnimationJobStore.load(from: threadLedger).jobs
        let threadCalls = try FileManager.default.contentsOfDirectory(atPath: threadEvents.path)
        require(results.statuses.count == 2 && !results.statuses.contains(1), "threaded callers must finish without unexpected errors")
        require(threadJobs.count == expectedJobs && threadCalls.count == expectedJobs, "\(name): threads must share the ledger lock")
        require(threadJobs.allSatisfy { $0.latestAttempt?.state == .submitted }, "threaded completions must preserve every provider ID")
    }
    print("animation-concurrency-regression=PASS")
} catch {
    fputs("animation-concurrency-regression=FAIL: \(error)\n", stderr)
    exit(1)
}


final class OwnedHTTPProtocol: URLProtocol, @unchecked Sendable {
    static let insecureLoads = ConcurrentAnimationResults()
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "owned-http.example" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let url = request.url!
        if url.scheme != "https" { Self.insecureLoads.append(1) }
        if url.path == "/redirect" || url.path == "/auth-redirect" {
            let destination = url.path == "/redirect" ? "http://owned-http.example/target" : "https://owned-http.example:444/target"
            let redirected = URLRequest(url: URL(string: destination)!)
            let response = HTTPURLResponse(url: url, statusCode: 302, httpVersion: nil, headerFields: ["Location": redirected.url!.absoluteString])!
            client?.urlProtocol(self, wasRedirectedTo: redirected, redirectResponse: response)
            return
        }
        let responseURL = url.path == "/bad-final" ? URL(string: "http://owned-http.example/target")! : url
        let headers = url.path == "/declared-size" ? ["Content-Length": "100000"] : [:]
        client?.urlProtocol(self, didReceive: HTTPURLResponse(url: responseURL, statusCode: 200, httpVersion: nil, headerFields: headers)!, cacheStoragePolicy: .notAllowed)
        if url.path == "/stream" {
            for _ in 0..<3 { client?.urlProtocol(self, didLoad: Data(repeating: 1, count: 4)) }
        } else { client?.urlProtocol(self, didLoad: Data("abc".utf8)) }
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() { }
}

do {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [OwnedHTTPProtocol.self]
    let session = URLSession(configuration: configuration)
    defer { session.invalidateAndCancel() }
    let transport = URLSessionProviderHTTPTransport(session: session)
    let valid = try transport.execute(URLRequest(url: URL(string: "https://owned-http.example/valid")!), timeout: 10, maximumBytes: 8)
    require(valid.body == Data("abc".utf8), "concrete bounded transport must return valid response bytes")
    for endpoint in ["redirect", "auth-redirect", "bad-final", "stream", "declared-size"] {
        do {
            var request = URLRequest(url: URL(string: "https://owned-http.example/\(endpoint)")!)
            if endpoint == "auth-redirect" { request.setValue("Bearer owned-fixture", forHTTPHeaderField: "Authorization") }
            _ = try transport.execute(request, timeout: 10, maximumBytes: 8)
            require(false, "unsafe or oversized transport response must reject")
        } catch let error as URLError {
            require(error.code == .secureConnectionFailed || error.code == .dataLengthExceedsMaximum, "\(endpoint): transport must reject at the HTTPS/byte boundary, got \(error.code)")
        }
    }
    require(OwnedHTTPProtocol.insecureLoads.statuses.isEmpty, "HTTPS redirect must reject before issuing any HTTP request")
    print("bounded-https-transport-regression=PASS")
} catch {
    fputs("bounded-https-transport-regression=FAIL: \(error)\n", stderr)
    exit(1)
}
