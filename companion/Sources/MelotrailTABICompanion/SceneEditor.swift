import CoreGraphics
import Foundation

public enum SceneEditorError: Error, LocalizedError, Sendable {
    case invalidSceneIndex(Int)
    case invalidLayerIndex(Int)
    case invalidCrop
    case finalSceneTransition
    case closed
    case staleSessionInputs(String)

    public var errorDescription: String? {
        switch self {
        case .invalidSceneIndex(let index):
            "Scene \(index + 1) is outside the immutable soundtrack plan."
        case .invalidLayerIndex(let index):
            "Motion layer \(index + 1) is outside the selected scene."
        case .invalidCrop:
            "Crop must stay inside the normalized source bounds."
        case .finalSceneTransition:
            "The final scene cannot transition beyond the soundtrack."
        case .closed:
            "The scene editor is closed."
        case .staleSessionInputs(let message):
            "Saved session inputs changed: \(message)"
        }
    }
}

/// A real plan frame suitable for the compact scene strip. The UI may scale
/// this image, but may not replace it with decorative or independently cropped
/// artwork.
public struct SceneEditorThumbnail {
    public let sceneIndex: Int
    public let sceneID: String
    public let label: String
    public let startFrame: Int64
    public let endFrame: Int64
    public let image: CGImage
}

public struct SceneEditorInspector: Equatable, Sendable {
    public let sceneID: String
    public let label: String
    public let startFrame: Int64
    public let endFrame: Int64
    public let startSeconds: Double
    public let endSeconds: Double
    public let interior: AssetIdentity
    public let windowMask: AssetIdentity
    public let parallaxLayerCount: Int
    public let actionClip: AssetIdentity?
    public let crop: SceneCrop
    public let parallaxLayers: [PlannedParallaxLayer]
    public let crossfadeToNextFrames: Int64
}

public struct SceneEditorSnapshot: Sendable {
    public let selectedSceneIndex: Int
    public let sceneCount: Int
    public let frameCount: Int64
    public let frameRate: Int
    public let currentFrame: Int64
    public let isPlaying: Bool
    public let soundtrackPlayerCount: Int
}

/// Owns the narrow editor-facing view of V05a. There is exactly one preview
/// stage, and therefore exactly one AVPlayer clock, for the lifetime of a
/// session. It never writes the request, media, MIDI project, or output.
public final class SceneEditorSession {
    public let request: SceneCompositionRequest
    public let geometry: PreviewOutputGeometry

    private let library: AssetLibrary
    /// The editable request-side scene facts. They are re-resolved before
    /// replacing the stage plan so an invalid edit leaves this valid state
    /// untouched.
    private var inputs: [SceneCompositionInput]
    private var selectedIndex = 0
    private var stage: ScenePreviewStage?

    public init(request: SceneCompositionRequest, geometry: PreviewOutputGeometry) throws {
        let library = try AssetLibrary(
            manifestURL: URL(fileURLWithPath: request.assetManifestPath),
            libraryRoot: URL(fileURLWithPath: request.assetLibraryPath)
        )
        let composition = try request.resolve()
        let soundtrack = try FinishedSoundtrack.open(
            url: URL(fileURLWithPath: request.timing.soundtrackPath),
            expectedSHA256: request.timing.soundtrackSHA256
        )
        self.request = request
        self.geometry = geometry
        self.library = library
        self.inputs = request.scenes
        self.stage = try ScenePreviewStage(
            composition: composition,
            library: library,
            soundtrack: soundtrack,
            geometry: geometry
        )
    }

    /// Reopens only a current-schema document after every persisted external
    /// input still matches its saved identity. The document is companion state;
    /// it never becomes authority for a soundtrack, MIDI export, asset library,
    /// or accepted output.
    public convenience init(document: SceneEditorDocument, geometry: PreviewOutputGeometry) throws {
        try document.validatePinnedManifest()
        try self.init(request: document.request, geometry: geometry)
        let pins = try compositionPlan().assetPins
        guard pins == document.assetPins else {
            close()
            throw SceneEditorError.staleSessionInputs("approved asset pins no longer match the saved session")
        }
        guard inputs.indices.contains(document.selectedSceneIndex) else {
            close()
            throw SceneEditorError.staleSessionInputs("the saved selected scene is outside the current scene plan")
        }
        selectedIndex = document.selectedSceneIndex
    }

    /// Restore edits transactionally on the existing soundtrack clock. Opening
    /// another project's inputs requires launching that request separately.
    public func restore(_ document: SceneEditorDocument, refresh: () throws -> Void = {}) throws {
        try document.validatePinnedManifest()
        let incoming = document.request
        guard incoming.assetManifestPath == request.assetManifestPath,
              incoming.assetLibraryPath == request.assetLibraryPath,
              incoming.sceneVersion == request.sceneVersion,
              incoming.identityVersion == request.identityVersion,
              incoming.timing.soundtrackPath == request.timing.soundtrackPath,
              incoming.timing.manifestPath == request.timing.manifestPath,
              incoming.scenes.indices.contains(document.selectedSceneIndex) else {
            throw SceneEditorError.staleSessionInputs("saved session belongs to different inputs")
        }
        let composition = try incoming.resolve()
        guard composition.assetPins == document.assetPins else {
            throw SceneEditorError.staleSessionInputs("approved asset pins no longer match the saved session")
        }
        let stage = try openStage()
        let previousInputs = inputs
        let previousIndex = selectedIndex
        try stage.updateComposition(composition) {
            inputs = incoming.scenes
            selectedIndex = document.selectedSceneIndex
            do { try refresh() } catch {
                inputs = previousInputs
                selectedIndex = previousIndex
                throw error
            }
        }
    }

    deinit { close() }

    public func snapshot() throws -> SceneEditorSnapshot {
        let stage = try openStage()
        return SceneEditorSnapshot(
            selectedSceneIndex: selectedIndex,
            sceneCount: stage.composition.scenes.count,
            frameCount: stage.composition.timing.frameCount,
            frameRate: stage.composition.timing.frameRate,
            currentFrame: stage.currentFrameIndex,
            isPlaying: stage.isPlaying,
            soundtrackPlayerCount: 1
        )
    }

    public var isClosed: Bool { stage == nil }
    public var soundtrackPlayerCount: Int { stage == nil ? 0 : 1 }

    public func sceneStrip() throws -> [SceneEditorThumbnail] {
        let stage = try openStage()
        return try stage.composition.scenes.enumerated().map { index, scene in
            let frame = try stage.render(frame: scene.timing.startFrame)
            return SceneEditorThumbnail(
                sceneIndex: index,
                sceneID: scene.timing.id,
                label: scene.timing.label,
                startFrame: scene.timing.startFrame,
                endFrame: scene.timing.endFrame,
                image: frame.image
            )
        }
    }

    public func inspector() throws -> SceneEditorInspector {
        let stage = try openStage()
        let scene = stage.composition.scenes[selectedIndex]
        let rate = Double(stage.composition.timing.frameRate)
        return SceneEditorInspector(
            sceneID: scene.timing.id,
            label: scene.timing.label,
            startFrame: scene.timing.startFrame,
            endFrame: scene.timing.endFrame,
            startSeconds: Double(scene.timing.startFrame) / rate,
            endSeconds: Double(scene.timing.endFrame) / rate,
            interior: scene.interior.identity,
            windowMask: scene.windowMask.identity,
            parallaxLayerCount: scene.parallaxLayers.count,
            actionClip: scene.actionLoop?.clip.identity,
            crop: scene.crop,
            parallaxLayers: scene.parallaxLayers,
            crossfadeToNextFrames: scene.crossfadeToNextFrames
        )
    }

    /// The currently edited resolver plan. Preview and future output consumers
    /// receive this exact plan, rather than an editor-local crop/motion model.
    public func compositionPlan() throws -> SceneCompositionPlan {
        try openStage().composition
    }

    /// Creates a durable editor-only snapshot. Calling code chooses the
    /// companion-owned destination and can safely retry persistence; no source
    /// media or composition request is rewritten.
    public func document() throws -> SceneEditorDocument {
        // Do not let a long-lived preview session bless bytes that changed
        // after it opened. Persisting remains read-only with respect to every
        // external input, and the later reopen repeats these checks.
        _ = try request.timing.resolve()
        let plan = try compositionPlan()
        try SceneComposer.validatePinnedAssets(plan, library: library)
        return try SceneEditorDocument(
            request: currentRequest(),
            selectedSceneIndex: selectedIndex,
            assetManifestSHA256: AssetDigest.sha256(of: URL(fileURLWithPath: request.assetManifestPath)),
            assetPins: plan.assetPins
        )
    }

    public func assetStates() throws -> [SceneEditorAssetState] {
        let pins = try compositionPlan().assetPins
        return try pins.map { pin in
            guard let record = library.manifest.assets.first(where: { $0.identity == pin.identity }) else {
                throw SceneEditorError.staleSessionInputs("asset \(pin.identity.assetID)@\(pin.identity.version) is missing from the manifest")
            }
            guard record.sha256 == pin.sha256 else {
                throw SceneEditorError.staleSessionInputs("asset \(pin.identity.assetID)@\(pin.identity.version) changed")
            }
            return SceneEditorAssetState(
                identity: pin.identity,
                approval: record.approval,
                unresolvedIdentityDifferences: record.identityDifferences.filter { !$0.resolved }
            )
        }
    }

    public func currentFrame() throws -> ScenePreviewFrame {
        try openStage().currentFrame()
    }

    public func selectScene(_ index: Int, completion: @escaping (Bool) -> Void) throws {
        let stage = try openStage()
        guard stage.composition.scenes.indices.contains(index) else {
            throw SceneEditorError.invalidSceneIndex(index)
        }
        selectedIndex = index
        stage.seek(toFrame: stage.composition.scenes[index].timing.startFrame, completion: completion)
    }

    public func setCrop(_ crop: SceneCrop) throws {
        guard crop.x >= 0, crop.y >= 0, crop.width > 0, crop.height > 0,
              crop.x <= 10_000, crop.y <= 10_000,
              crop.width <= 10_000 - crop.x, crop.height <= 10_000 - crop.y else {
            throw SceneEditorError.invalidCrop
        }
        try replaceSelected { input in
            SceneCompositionInput(
                timingSceneID: input.timingSceneID,
                interior: input.interior,
                windowMask: input.windowMask,
                parallaxLayers: input.parallaxLayers,
                actionLoop: input.actionLoop,
                crop: crop,
                crossfadeToNextFrames: input.crossfadeToNextFrames
            )
        }
    }

    public func setMotion(layer index: Int, pixelsPerFrame: Int64) throws {
        guard inputs[selectedIndex].parallaxLayers.indices.contains(index) else {
            throw SceneEditorError.invalidLayerIndex(index)
        }
        try replaceSelected { input in
            var layers = input.parallaxLayers
            layers[index] = ParallaxLayerInput(asset: layers[index].asset, pixelsPerFrame: pixelsPerFrame)
            return SceneCompositionInput(
                timingSceneID: input.timingSceneID,
                interior: input.interior,
                windowMask: input.windowMask,
                parallaxLayers: layers,
                actionLoop: input.actionLoop,
                crop: input.crop,
                crossfadeToNextFrames: input.crossfadeToNextFrames
            )
        }
    }

    public func setCrossfade(frames: Int64) throws {
        if selectedIndex == inputs.count - 1, frames != 0 {
            throw SceneEditorError.finalSceneTransition
        }
        try replaceSelected { input in
            SceneCompositionInput(
                timingSceneID: input.timingSceneID,
                interior: input.interior,
                windowMask: input.windowMask,
                parallaxLayers: input.parallaxLayers,
                actionLoop: input.actionLoop,
                crop: input.crop,
                crossfadeToNextFrames: frames
            )
        }
    }

    public func play() throws { try openStage().play() }
    public func pause() { stage?.pause() }

    public func seek(toFrame frame: Int64, completion: @escaping (Bool) -> Void) {
        guard let stage else {
            completion(false)
            return
        }
        stage.seek(toFrame: frame, completion: completion)
    }

    @discardableResult
    public func observeFrames(
        queue: DispatchQueue = .main,
        _ observer: @escaping (ScenePreviewFrame) -> Void
    ) throws -> Any {
        try openStage().observeFrames(queue: queue, observer)
    }

    public func removeFrameObserver(_ token: Any) {
        stage?.removeFrameObserver(token)
    }

    /// Releases the observer-capable stage and its sole AVPlayer immediately.
    /// This is idempotent so both `windowWillClose` and deinitialization are
    /// safe cleanup paths.
    public func close() {
        stage?.pause()
        stage = nil
    }

    private func openStage() throws -> ScenePreviewStage {
        guard let stage else { throw SceneEditorError.closed }
        return stage
    }

    /// Resolves first, then swaps the plan into the existing stage. An invalid
    /// edit cannot replace the prior valid inputs, player, or observer set.
    private func replaceSelected(_ transform: (SceneCompositionInput) -> SceneCompositionInput) throws {
        guard let stage else { throw SceneEditorError.closed }
        var edited = inputs
        edited[selectedIndex] = transform(edited[selectedIndex])
        let composition = try SceneComposer.plan(
            timing: request.timing.resolve(),
            library: library,
            request: SceneCompositionRequest(
                timing: request.timing,
                assetLibraryPath: request.assetLibraryPath,
                assetManifestPath: request.assetManifestPath,
                sceneVersion: request.sceneVersion,
                identityVersion: request.identityVersion,
                scenes: edited
            )
        )
        try stage.updateComposition(composition)
        inputs = edited
    }

    private func currentRequest() -> SceneCompositionRequest {
        SceneCompositionRequest(
            timing: request.timing,
            assetLibraryPath: request.assetLibraryPath,
            assetManifestPath: request.assetManifestPath,
            sceneVersion: request.sceneVersion,
            identityVersion: request.identityVersion,
            scenes: inputs
        )
    }
}

public struct SceneEditorAssetState: Equatable, Sendable {
    public let identity: AssetIdentity
    public let approval: AssetApproval
    public let unresolvedIdentityDifferences: [AssetIdentityDifference]

    public init(identity: AssetIdentity, approval: AssetApproval, unresolvedIdentityDifferences: [AssetIdentityDifference]) {
        self.identity = identity
        self.approval = approval
        self.unresolvedIdentityDifferences = unresolvedIdentityDifferences
    }
}
