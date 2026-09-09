import Darwin
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

func writeOwnedPNG(to url: URL, width: Int, height: Int, alpha: CGFloat = 0.5) throws {
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
    context.setFillColor(red: 0.2, green: 0.3, blue: 0.7, alpha: alpha)
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
