import CoreGraphics
import Foundation

public enum SceneEditorError: Error, LocalizedError, Sendable {
    case invalidSceneIndex(Int)
    case closed

    public var errorDescription: String? {
        switch self {
        case .invalidSceneIndex(let index):
            "Scene \(index + 1) is outside the immutable soundtrack plan."
        case .closed:
            "The scene editor is closed."
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

/// The V05b inspector is deliberately factual and read-only. Crop, motion,
/// and transition mutations remain V05c work.
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
        self.stage = try ScenePreviewStage(
            composition: composition,
            library: library,
            soundtrack: soundtrack,
            geometry: geometry
        )
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
            crossfadeToNextFrames: scene.crossfadeToNextFrames
        )
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
}
