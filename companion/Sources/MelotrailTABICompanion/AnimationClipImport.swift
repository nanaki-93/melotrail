import Darwin
import Foundation

public enum AnimationOutputError: Error, Equatable, LocalizedError, Sendable {
    case invalidDestination
    case invalidSourceURL
    case downloadRejected(Int)
    case partialDownload(expected: Int, actual: Int)
    case emptyDownload
    case exceedsMaximumBytes(Int)
    case digestMismatch(expected: String, actual: String)
    case invalidClip
    public var errorDescription: String? {
        switch self {
        case .invalidDestination: "Animation output quarantine must be a local directory outside Git repositories."
        case .invalidSourceURL: "Animation output must be downloaded from an HTTPS URL."
        case .downloadRejected(let status): "Animation output download was rejected with HTTP status \(status)."
        case .partialDownload: "Animation output download was partial and has been discarded."
        case .emptyDownload: "Animation output download was empty and has been discarded."
        case .exceedsMaximumBytes: "Animation output download exceeds its configured limit and has been discarded."
        case .digestMismatch: "Animation output digest did not match and has been discarded."
        case .invalidClip: "Animation output is not a readable video clip and has been discarded."
        }
    }
}

public struct AnimationQuarantinedOutput: Sendable { public let url: URL; public let sha256: String; public let facts: AssetMediaFacts }

/// Stores downloaded provider media as immutable, unapproved evidence. A clip
    /// must still be imported/published for human review before composition.
public enum AnimationOutputQuarantine {
    /// The real network caller for a Runway task output. The caller chooses a
    /// URL returned by `RunwayProvider.outputURLs`; credentials are deliberately
    /// not copied onto this separately hosted media request.
    public static func download(
        outputURL: URL,
        transport: any ProviderHTTPTransport,
        timeout: TimeInterval = 60,
        expectedSHA256: String?,
        into root: URL,
        maximumBytes: Int = 250 * 1024 * 1024
    ) throws -> AnimationQuarantinedOutput {
        guard outputURL.scheme == "https", timeout > 0, timeout.isFinite else { throw AnimationOutputError.invalidSourceURL }
        var request = URLRequest(url: outputURL)
        request.httpMethod = "GET"
        request.setValue("video/*", forHTTPHeaderField: "Accept")
        let response: ProviderHTTPResponse
        do { response = try transport.execute(request, timeout: timeout, maximumBytes: maximumBytes) }
        catch { throw AnimationOutputError.downloadRejected(0) }
        guard (200..<300).contains(response.statusCode) else { throw AnimationOutputError.downloadRejected(response.statusCode) }
        return try store(response: response, expectedSHA256: expectedSHA256, into: root, maximumBytes: maximumBytes)
    }

    public static func store(response: ProviderHTTPResponse, expectedSHA256: String?, into root: URL, maximumBytes: Int = 250 * 1024 * 1024) throws -> AnimationQuarantinedOutput {
        let resolvedRoot = root.standardizedFileURL.pathComponents.dropFirst().reduce(URL(fileURLWithPath: "/")) {
            $0.appendingPathComponent($1).resolvingSymlinksInPath()
        }
        guard root.isFileURL, maximumBytes > 0, resolvedRoot.path != "/", enclosingGitRepository(for: resolvedRoot) == nil else { throw AnimationOutputError.invalidDestination }
        guard response.statusCode == 200 else { throw AnimationOutputError.downloadRejected(response.statusCode) }
        guard !response.body.isEmpty else { throw AnimationOutputError.emptyDownload }
        guard response.body.count <= maximumBytes else { throw AnimationOutputError.exceedsMaximumBytes(maximumBytes) }
        if let declared = response.headers.first(where: { $0.key.caseInsensitiveCompare("Content-Length") == .orderedSame }).flatMap({ Int($0.value) }), declared != response.body.count { throw AnimationOutputError.partialDownload(expected: declared, actual: response.body.count) }
        try FileManager.default.createDirectory(at: resolvedRoot, withIntermediateDirectories: true)
        let staged = resolvedRoot.appendingPathComponent(".download-\(UUID().uuidString).tmp")
        defer { try? FileManager.default.removeItem(at: staged) }
        try response.body.write(to: staged, options: .withoutOverwriting)
        let digest = try AssetDigest.sha256(of: staged)
        if let expectedSHA256, expectedSHA256.lowercased() != digest { throw AnimationOutputError.digestMismatch(expected: expectedSHA256.lowercased(), actual: digest) }
        let facts: AssetMediaFacts
        do { facts = try AssetMediaInspector.inspect(kind: .animationClip, url: staged) } catch { throw AnimationOutputError.invalidClip }
        let container = try AssetMediaInspector.videoContainerExtension(staged) ?? "mov"
        let destination = resolvedRoot.appendingPathComponent("\(digest).\(container)")
        var metadata = stat()
        if lstat(destination.path, &metadata) == 0 {
            guard (metadata.st_mode & S_IFMT) == S_IFREG,
                  try AssetDigest.sha256(of: destination) == digest else { throw AnimationOutputError.invalidDestination }
        }
        else { guard link(staged.path, destination.path) == 0 else { throw AnimationOutputError.invalidDestination } }
        return AnimationQuarantinedOutput(url: destination, sha256: digest, facts: facts)
    }

    private static func enclosingGitRepository(for url: URL) -> URL? {
        var components = url.standardizedFileURL.pathComponents
        while !components.isEmpty {
            let candidate = URL(fileURLWithPath: NSString.path(withComponents: components), isDirectory: true)
            let isBareRepository = (try? candidate.appendingPathComponent("HEAD").resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
                && (try? candidate.appendingPathComponent("objects").resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true
                && (try? candidate.appendingPathComponent("refs").resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true
            if FileManager.default.fileExists(atPath: candidate.appendingPathComponent(".git").path) || isBareRepository { return candidate }
            components.removeLast()
        }
        return nil
    }
}

public struct ManualAnimationClipImportRequest: Codable, Sendable { public let assetImport: AssetImportRequest; public init(assetImport: AssetImportRequest) { self.assetImport = assetImport } }

/// Explicitly imports a user-owned local video through the existing immutable
/// asset boundary. It cannot mark the clip approved or write a manifest.
public enum ManualAnimationClipImporter {
    public static func importOwned(_ request: ManualAnimationClipImportRequest, into libraryRoot: URL, now: Date = Date()) throws -> AssetImportResult {
        guard request.assetImport.kind == .animationClip else { throw AssetManifestError.invalidManifest("manual animation clip imports require kind animationClip") }
        guard request.assetImport.provenance.creationMethod == .owned || request.assetImport.provenance.creationMethod == .userProvided else { throw AssetManifestError.invalidManifest("manual animation clips require owned or user-provided provenance") }
        return try AssetKitImporter.importOriginal(request.assetImport, into: libraryRoot, now: now)
    }
}
