import CryptoKit
import Darwin
import Foundation

/// The one current, companion-owned editor-session schema. It stores only
/// request facts and edits; source media, MIDI exports, asset manifests, and
/// accepted output remain external read-only inputs.
public struct SceneEditorDocument: Codable, Sendable {
    public static let currentSchemaVersion = 1

    public let schemaVersion: Int
    public let request: SceneCompositionRequest
    public let selectedSceneIndex: Int
    /// The exact manifest bytes selected when the session was saved. Pins below
    /// also prevent a rewritten manifest from silently selecting different
    /// approved media under the same identity.
    public let assetManifestSHA256: String
    public let assetPins: [SceneAssetPin]

    public init(
        schemaVersion: Int = SceneEditorDocument.currentSchemaVersion,
        request: SceneCompositionRequest,
        selectedSceneIndex: Int,
        assetManifestSHA256: String,
        assetPins: [SceneAssetPin]
    ) throws {
        guard schemaVersion == Self.currentSchemaVersion else {
            throw SceneEditorDocumentError.unsupportedSchema(schemaVersion)
        }
        guard selectedSceneIndex >= 0, request.scenes.indices.contains(selectedSceneIndex) else {
            throw SceneEditorDocumentError.invalidDocument("selected scene is outside the saved request")
        }
        guard assetManifestSHA256.count == 64,
              assetManifestSHA256.allSatisfy({ $0.isHexDigit }) else {
            throw SceneEditorDocumentError.invalidDocument("asset manifest SHA-256 is required")
        }
        guard !assetPins.isEmpty else {
            throw SceneEditorDocumentError.invalidDocument("saved session has no resolved asset pins")
        }
        self.schemaVersion = schemaVersion
        self.request = request
        self.selectedSceneIndex = selectedSceneIndex
        self.assetManifestSHA256 = assetManifestSHA256.lowercased()
        self.assetPins = assetPins
    }

    public func validatePinnedManifest() throws {
        guard schemaVersion == Self.currentSchemaVersion else {
            throw SceneEditorDocumentError.unsupportedSchema(schemaVersion)
        }
        let manifestURL = URL(fileURLWithPath: request.assetManifestPath)
        guard (try? manifestURL.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true else {
            throw SceneEditorError.staleSessionInputs("asset manifest is missing")
        }
        let actual = try AssetDigest.sha256(of: manifestURL)
        guard actual == assetManifestSHA256 else {
            throw SceneEditorError.staleSessionInputs("asset manifest bytes no longer match the saved digest")
        }
    }
}

public enum SceneEditorDocumentError: Error, Equatable, LocalizedError, Sendable {
    case unsupportedSchema(Int)
    case invalidDocument(String)
    case unreadableDocument(String)
    case unsafeSessionPath(String)

    public var errorDescription: String? {
        switch self {
        case .unsupportedSchema(let schema): "Scene editor session schema \(schema) is unsupported."
        case .invalidDocument(let message), .unreadableDocument(let message), .unsafeSessionPath(let message): message
        }
    }
}

public enum SceneEditorDocumentStore {
    public static func load(from url: URL) throws -> SceneEditorDocument {
        try requireSessionFile(url)
        do {
            let document = try JSONDecoder().decode(SceneEditorDocument.self, from: Data(contentsOf: url, options: [.mappedIfSafe]))
            guard document.schemaVersion == SceneEditorDocument.currentSchemaVersion else {
                throw SceneEditorDocumentError.unsupportedSchema(document.schemaVersion)
            }
            return try SceneEditorDocument(
                schemaVersion: document.schemaVersion,
                request: document.request,
                selectedSceneIndex: document.selectedSceneIndex,
                assetManifestSHA256: document.assetManifestSHA256,
                assetPins: document.assetPins
            )
        } catch let error as SceneEditorDocumentError {
            throw error
        } catch {
            throw SceneEditorDocumentError.unreadableDocument("Cannot read saved editor session: \(error.localizedDescription)")
        }
    }

    /// Replaces only the caller's companion-owned session document, staged in
    /// the same directory. It cannot publish into a soundtrack, asset library,
    /// MIDI project, or video-output destination.
    public static func save(_ document: SceneEditorDocument, to url: URL) throws {
        try requireSessionDestination(url, document: document)
        let directory = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let staged = directory.appendingPathComponent(".scene-editor-\(UUID().uuidString).tmp")
        defer { try? FileManager.default.removeItem(at: staged) }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        try encoder.encode(document).write(to: staged, options: .withoutOverwriting)
        if rename(staged.path, url.path) != 0 {
            throw SceneEditorDocumentError.unreadableDocument("Cannot save editor session: \(String(cString: strerror(errno)))")
        }
    }

    public static func defaultURL(for request: SceneCompositionRequest) throws -> URL {
        let base = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        ).appendingPathComponent("MelotrailTABI", isDirectory: true)
            .appendingPathComponent("sessions", isDirectory: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        let key = SHA256.hash(data: try encoder.encode(request)).map { String(format: "%02x", $0) }.joined()
        return base.appendingPathComponent("\(key).scene-editor.json")
    }

    private static func requireSessionFile(_ url: URL) throws {
        guard url.isFileURL,
              (try? url.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true else {
            throw SceneEditorDocumentError.unsafeSessionPath("Saved editor session must be one regular local file.")
        }
    }

    private static func requireSessionDestination(_ url: URL, document: SceneEditorDocument) throws {
        guard url.isFileURL, url.lastPathComponent.hasSuffix(".scene-editor.json") else {
            throw SceneEditorDocumentError.unsafeSessionPath("Choose a companion .scene-editor.json file.")
        }
        func canonical(_ url: URL) -> String {
            url.standardizedFileURL.pathComponents.dropFirst().reduce(URL(fileURLWithPath: "/")) {
                $0.appendingPathComponent($1).resolvingSymlinksInPath()
            }.path
        }
        let destination = canonical(url)
        let library = canonical(URL(fileURLWithPath: document.request.assetLibraryPath))
        let protectedPaths = [document.request.timing.soundtrackPath, document.request.assetManifestPath,
                              document.request.timing.manifestPath].compactMap { $0 }.map { canonical(URL(fileURLWithPath: $0)) }
        guard !protectedPaths.contains(destination), destination != library,
              !destination.hasPrefix(library + "/") else {
            throw SceneEditorDocumentError.unsafeSessionPath("Session storage cannot replace or enter protected inputs.")
        }
        var info = stat()
        if lstat(url.path, &info) == 0 {
            guard (info.st_mode & S_IFMT) == S_IFREG else {
                throw SceneEditorDocumentError.unsafeSessionPath("Session destination must not be a link or directory.")
            }
            _ = try load(from: url) // Never replace an unrelated or malformed file.
        } else if errno != ENOENT {
            throw SceneEditorDocumentError.unsafeSessionPath("Cannot inspect session destination.")
        }
    }
}
