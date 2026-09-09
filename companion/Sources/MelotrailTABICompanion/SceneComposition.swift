import Foundation

/// Composition is deliberately a frame plan, rather than an encoder setting.
/// The preview and encoder will consume this same immutable description in
/// later slices. It only resolves already-approved, digest-validated assets;
/// it never opens a MIDI project or writes a soundtrack, manifest, or source.
public enum SceneCompositionError: Error, LocalizedError, Sendable {
    case invalidRequest(String)
    case incompleteAssetKit(String)
    case invalidAssetKind(AssetIdentity, expected: String, actual: AssetKind)
    case staleAsset(AssetIdentity, expected: String, actual: String)
    case frameOutOfRange(Int64)
    case overflow

    public var errorDescription: String? {
        switch self {
        case .invalidRequest(let message): "Invalid scene composition: \(message)"
        case .incompleteAssetKit(let message): "Scene composition requires a ready approved kit: \(message)"
        case .invalidAssetKind(let identity, let expected, let actual): "Asset \(identity.assetID)@\(identity.version) must be \(expected), not \(actual.rawValue)."
        case .staleAsset(let identity, _, _): "Asset \(identity.assetID)@\(identity.version) no longer has the digest pinned by this job."
        case .frameOutOfRange: "Requested frame is outside the immutable soundtrack plan."
        case .overflow: "Scene composition exceeds the supported exact frame range."
        }
    }
}

public struct SceneAssetPin: Codable, Equatable, Hashable, Sendable {
    public let identity: AssetIdentity
    public let sha256: String

    public init(identity: AssetIdentity, sha256: String) {
        self.identity = identity
        self.sha256 = sha256
    }
}

/// Integer pixels per frame avoids a separate floating-point motion policy.
/// Positive values move left-to-right in the source tile; consumers translate
/// the tile in the opposite direction to create passing scenery.
public struct ParallaxLayerInput: Codable, Equatable, Hashable, Sendable {
    public let asset: AssetIdentity
    public let pixelsPerFrame: Int64

    public init(asset: AssetIdentity, pixelsPerFrame: Int64) {
        self.asset = asset
        self.pixelsPerFrame = pixelsPerFrame
    }
}

public struct ActionLoopInput: Codable, Equatable, Hashable, Sendable {
    public let clip: AssetIdentity
    /// Number of source frames in the verified clip that may be scheduled.
    public let clipFrames: Int64
    /// Keeps an approved action from becoming an unbounded repeated episode.
    public let maximumRepeats: Int

    public init(clip: AssetIdentity, clipFrames: Int64, maximumRepeats: Int) {
        self.clip = clip
        self.clipFrames = clipFrames
        self.maximumRepeats = maximumRepeats
    }
}

/// A normalized complete-scene rectangle expressed in ten-thousandths. Integer
/// coordinates give preview and a future encoder one crop rounding policy.
public struct SceneCrop: Codable, Equatable, Hashable, Sendable {
    public static let fullFrame = SceneCrop(x: 0, y: 0, width: 10_000, height: 10_000)

    public let x: Int
    public let y: Int
    public let width: Int
    public let height: Int

    public init(x: Int, y: Int, width: Int, height: Int) {
        self.x = x
        self.y = y
        self.width = width
        self.height = height
    }

    fileprivate var isValid: Bool {
        x >= 0 && y >= 0 && width > 0 && height > 0 &&
            x <= 10_000 && y <= 10_000 &&
            width <= 10_000 - x && height <= 10_000 - y
    }
}

/// One requested visual treatment for exactly one V04a timing scene.
public struct SceneCompositionInput: Codable, Equatable, Hashable, Sendable {
    public let timingSceneID: String
    public let interior: AssetIdentity
    public let windowMask: AssetIdentity
    public let parallaxLayers: [ParallaxLayerInput]
    public let actionLoop: ActionLoopInput?
    public let crop: SceneCrop
    /// The outgoing fade is rendered in the ending frames of this timing
    /// scene. The following scene is overlaid there; timing frame ranges do
    /// not move, so soundtrack duration can never be shortened.
    public let crossfadeToNextFrames: Int64

    public init(
        timingSceneID: String,
        interior: AssetIdentity,
        windowMask: AssetIdentity,
        parallaxLayers: [ParallaxLayerInput],
        actionLoop: ActionLoopInput? = nil,
        crop: SceneCrop = .fullFrame,
        crossfadeToNextFrames: Int64 = 0
    ) {
        self.timingSceneID = timingSceneID
        self.interior = interior
        self.windowMask = windowMask
        self.parallaxLayers = parallaxLayers
        self.actionLoop = actionLoop
        self.crop = crop
        self.crossfadeToNextFrames = crossfadeToNextFrames
    }

    private enum CodingKeys: String, CodingKey {
        case timingSceneID, interior, windowMask, parallaxLayers, actionLoop, crop, crossfadeToNextFrames
    }

    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        timingSceneID = try values.decode(String.self, forKey: .timingSceneID)
        interior = try values.decode(AssetIdentity.self, forKey: .interior)
        windowMask = try values.decode(AssetIdentity.self, forKey: .windowMask)
        parallaxLayers = try values.decode([ParallaxLayerInput].self, forKey: .parallaxLayers)
        actionLoop = try values.decodeIfPresent(ActionLoopInput.self, forKey: .actionLoop)
        crop = try values.decodeIfPresent(SceneCrop.self, forKey: .crop) ?? .fullFrame
        crossfadeToNextFrames = try values.decodeIfPresent(Int64.self, forKey: .crossfadeToNextFrames) ?? 0
    }
}

public struct SceneCompositionRequest: Decodable, Sendable {
    public let timing: SoundtrackTimingRequest
    public let assetLibraryPath: String
    public let assetManifestPath: String
    public let sceneVersion: String
    public let identityVersion: String
    public let scenes: [SceneCompositionInput]

    public init(
        timing: SoundtrackTimingRequest,
        assetLibraryPath: String,
        assetManifestPath: String,
        sceneVersion: String,
        identityVersion: String,
        scenes: [SceneCompositionInput]
    ) {
        self.timing = timing
        self.assetLibraryPath = assetLibraryPath
        self.assetManifestPath = assetManifestPath
        self.sceneVersion = sceneVersion
        self.identityVersion = identityVersion
        self.scenes = scenes
    }

    /// The only caller-facing composition boundary. It first resolves V04a's
    /// pinned soundtrack timeline, then validates every requested asset byte.
    public func resolve() throws -> SceneCompositionPlan {
        let timingPlan = try timing.resolve()
        let library = try AssetLibrary(
            manifestURL: URL(fileURLWithPath: assetManifestPath),
            libraryRoot: URL(fileURLWithPath: assetLibraryPath)
        )
        return try SceneComposer.plan(timing: timingPlan, library: library, request: self)
    }
}

public struct PlannedParallaxLayer: Codable, Equatable, Hashable, Sendable {
    public let asset: SceneAssetPin
    public let pixelsPerFrame: Int64
    public let tileWidthPixels: Int64
    public let clippedByWindowMask: AssetIdentity

    public init(asset: SceneAssetPin, pixelsPerFrame: Int64, tileWidthPixels: Int64, clippedByWindowMask: AssetIdentity) {
        self.asset = asset
        self.pixelsPerFrame = pixelsPerFrame
        self.tileWidthPixels = tileWidthPixels
        self.clippedByWindowMask = clippedByWindowMask
    }
}

public struct PlannedActionLoop: Codable, Equatable, Hashable, Sendable {
    public let clip: SceneAssetPin
    public let clipFrames: Int64
    public let repeatCount: Int
    public let scheduledFrames: Int64

    public init(clip: SceneAssetPin, clipFrames: Int64, repeatCount: Int, scheduledFrames: Int64) {
        self.clip = clip
        self.clipFrames = clipFrames
        self.repeatCount = repeatCount
        self.scheduledFrames = scheduledFrames
    }
}

public struct ComposedScene: Codable, Equatable, Hashable, Sendable {
    public let timing: TimedScene
    public let interior: SceneAssetPin
    public let windowMask: SceneAssetPin
    /// These layers are always composited behind `windowMask`.
    public let parallaxLayers: [PlannedParallaxLayer]
    public let actionLoop: PlannedActionLoop?
    public let crop: SceneCrop
    public let crossfadeToNextFrames: Int64

    public init(
        timing: TimedScene,
        interior: SceneAssetPin,
        windowMask: SceneAssetPin,
        parallaxLayers: [PlannedParallaxLayer],
        actionLoop: PlannedActionLoop?,
        crop: SceneCrop,
        crossfadeToNextFrames: Int64
    ) {
        self.timing = timing
        self.interior = interior
        self.windowMask = windowMask
        self.parallaxLayers = parallaxLayers
        self.actionLoop = actionLoop
        self.crop = crop
        self.crossfadeToNextFrames = crossfadeToNextFrames
    }
}

public struct SceneCompositionPlan: Codable, Equatable, Sendable {
    public let timing: SoundtrackScenePlan
    public let sceneVersion: String
    public let identityVersion: String
    /// Captures the actual accepted asset digests used to form every frame.
    public let assetPins: [SceneAssetPin]
    public let scenes: [ComposedScene]

    public init(timing: SoundtrackScenePlan, sceneVersion: String, identityVersion: String, assetPins: [SceneAssetPin], scenes: [ComposedScene]) {
        self.timing = timing
        self.sceneVersion = sceneVersion
        self.identityVersion = identityVersion
        self.assetPins = assetPins
        self.scenes = scenes
    }

    /// Resolve one deterministic renderer-neutral frame. A renderer receives
    /// only exact asset references, integer offsets, clip source-frame indices,
    /// and rational opacity; it cannot invent another timing policy.
    public func frame(at frame: Int64) throws -> SceneCompositionFrame {
        guard frame >= 0, frame < timing.frameCount else { throw SceneCompositionError.frameOutOfRange(frame) }
        guard let sceneIndex = scenes.firstIndex(where: { frame >= $0.timing.startFrame && frame < $0.timing.endFrame }) else {
            throw SceneCompositionError.frameOutOfRange(frame)
        }
        let currentScene = scenes[sceneIndex]
        let current = try render(scene: currentScene, at: frame)
        let fadeFrames = currentScene.crossfadeToNextFrames
        guard fadeFrames > 0,
              sceneIndex + 1 < scenes.count,
              frame >= currentScene.timing.endFrame - fadeFrames else {
            return SceneCompositionFrame(frame: frame, layers: [current])
        }
        let transitionStart = currentScene.timing.endFrame - fadeFrames
        let progress = frame - transitionStart + 1
        let next = try render(scene: scenes[sceneIndex + 1], at: scenes[sceneIndex + 1].timing.startFrame)
        return SceneCompositionFrame(
            frame: frame,
            layers: [
                current.withOpacity(numerator: fadeFrames - progress, denominator: fadeFrames),
                next.withOpacity(numerator: progress, denominator: fadeFrames),
            ]
        )
    }

    private func render(scene: ComposedScene, at frame: Int64) throws -> SceneCompositionFrameLayer {
        let relative = frame >= scene.timing.startFrame ? frame - scene.timing.startFrame : 0
        let parallax = try scene.parallaxLayers.map { layer -> SceneParallaxFrame in
            let product = layer.pixelsPerFrame.multipliedReportingOverflow(by: relative)
            guard !product.overflow else { throw SceneCompositionError.overflow }
            let remainder = product.partialValue % layer.tileWidthPixels
            let phase = remainder >= 0 ? remainder : remainder + layer.tileWidthPixels
            return SceneParallaxFrame(asset: layer.asset, phasePixels: phase, clippedByWindowMask: layer.clippedByWindowMask)
        }
        let action: SceneActionFrame?
        if let schedule = scene.actionLoop, relative < schedule.scheduledFrames {
            action = SceneActionFrame(clip: schedule.clip, sourceFrame: relative % schedule.clipFrames)
        } else {
            action = nil
        }
        return SceneCompositionFrameLayer(
            timingSceneID: scene.timing.id,
            interior: scene.interior,
            windowMask: scene.windowMask,
            parallax: parallax,
            action: action,
            crop: scene.crop,
            opacityNumerator: 1,
            opacityDenominator: 1
        )
    }
}

public struct SceneParallaxFrame: Codable, Equatable, Hashable, Sendable {
    public let asset: SceneAssetPin
    public let phasePixels: Int64
    public let clippedByWindowMask: AssetIdentity
}

public struct SceneActionFrame: Codable, Equatable, Hashable, Sendable {
    public let clip: SceneAssetPin
    public let sourceFrame: Int64
}

public struct SceneCompositionFrameLayer: Codable, Equatable, Hashable, Sendable {
    public let timingSceneID: String
    public let interior: SceneAssetPin
    public let windowMask: SceneAssetPin
    public let parallax: [SceneParallaxFrame]
    public let action: SceneActionFrame?
    public let crop: SceneCrop
    public let opacityNumerator: Int64
    public let opacityDenominator: Int64

    fileprivate func withOpacity(numerator: Int64, denominator: Int64) -> SceneCompositionFrameLayer {
        SceneCompositionFrameLayer(
            timingSceneID: timingSceneID,
            interior: interior,
            windowMask: windowMask,
            parallax: parallax,
            action: action,
            crop: crop,
            opacityNumerator: numerator,
            opacityDenominator: denominator
        )
    }
}

public struct SceneCompositionFrame: Codable, Equatable, Hashable, Sendable {
    public let frame: Int64
    /// One layer normally; two only during an explicit crossfade overlap.
    public let layers: [SceneCompositionFrameLayer]
}

public enum SceneComposer {
    public static func plan(timing: SoundtrackScenePlan, library: AssetLibrary, request: SceneCompositionRequest) throws -> SceneCompositionPlan {
        guard !request.sceneVersion.isEmpty, !request.identityVersion.isEmpty else {
            throw SceneCompositionError.invalidRequest("scene and identity versions are required")
        }
        guard request.scenes.count == timing.scenes.count else {
            throw SceneCompositionError.invalidRequest("provide exactly one composition scene for every timing scene")
        }
        let expectedIDs = timing.scenes.map(\.id)
        guard request.scenes.map(\.timingSceneID) == expectedIDs else {
            throw SceneCompositionError.invalidRequest("composition scenes must follow the immutable timing-scene order")
        }

        let identities = uniquePins(request.scenes.flatMap(assetIdentities))
        let inspection = AssetKitInspector.inspect(library, request: AssetKitSceneRequest(
            sceneVersion: request.sceneVersion,
            identityVersion: request.identityVersion,
            assetPins: identities
        ))
        guard inspection.isCompositionReady else {
            throw SceneCompositionError.incompleteAssetKit(inspection.findings.map(\.message).joined(separator: " "))
        }
        let records = try library.validatedApprovedAssets(pinned: identities)
        let byIdentity = Dictionary(uniqueKeysWithValues: records.map { ($0.identity, $0) })
        let pin: (AssetIdentity) throws -> SceneAssetPin = { identity in
            guard let record = byIdentity[identity] else { throw SceneCompositionError.invalidRequest("asset pin is missing") }
            return SceneAssetPin(identity: identity, sha256: record.sha256.lowercased())
        }

        var planned: [ComposedScene] = []
        for (index, input) in request.scenes.enumerated() {
            let timingScene = timing.scenes[index]
            guard input.crop.isValid else {
                throw SceneCompositionError.invalidRequest("crop must stay within the normalized source bounds")
            }
            guard input.crossfadeToNextFrames >= 0 else {
                throw SceneCompositionError.invalidRequest("crossfade frames cannot be negative")
            }
            if index == request.scenes.indices.last {
                guard input.crossfadeToNextFrames == 0 else {
                    throw SceneCompositionError.invalidRequest("the final scene cannot crossfade beyond the soundtrack")
                }
            } else {
                let followingFrames = timing.scenes[index + 1].endFrame - timing.scenes[index + 1].startFrame
                let currentFrames = timingScene.endFrame - timingScene.startFrame
                guard input.crossfadeToNextFrames <= min(currentFrames, followingFrames) else {
                    throw SceneCompositionError.invalidRequest("crossfade cannot exceed either adjacent timing scene")
                }
            }
            try requireKind(input.interior, record: byIdentity[input.interior], allowed: [.image, .layer, .reference, .prop], description: "a still interior")
            try requireKind(input.windowMask, record: byIdentity[input.windowMask], allowed: [.mask], description: "a window mask")
            guard !input.parallaxLayers.isEmpty else {
                throw SceneCompositionError.invalidRequest("every scene needs at least one masked parallax layer")
            }
            let layers = try input.parallaxLayers.map { layer -> PlannedParallaxLayer in
                try requireKind(layer.asset, record: byIdentity[layer.asset], allowed: [.layer], description: "a scrolling scenery layer")
                guard let record = byIdentity[layer.asset], record.geometry.width > 0 else {
                    throw SceneCompositionError.invalidRequest("scrolling scenery requires a positive measured tile width")
                }
                let largestRelativeFrame = max(timingScene.endFrame - timingScene.startFrame - 1, 0)
                guard !layer.pixelsPerFrame.multipliedReportingOverflow(by: largestRelativeFrame).overflow else {
                    throw SceneCompositionError.overflow
                }
                return PlannedParallaxLayer(
                    asset: try pin(layer.asset),
                    pixelsPerFrame: layer.pixelsPerFrame,
                    tileWidthPixels: Int64(record.geometry.width),
                    clippedByWindowMask: input.windowMask
                )
            }
            let action = try makeActionLoop(input.actionLoop, sceneFrames: timingScene.endFrame - timingScene.startFrame, records: byIdentity, pin: pin)
            if index > 0, let action, let previous = planned.last?.actionLoop, previous.clip.identity == action.clip.identity {
                throw SceneCompositionError.invalidRequest("adjacent scenes cannot reuse the same action episode")
            }
            planned.append(ComposedScene(
                timing: timingScene,
                interior: try pin(input.interior),
                windowMask: try pin(input.windowMask),
                parallaxLayers: layers,
                actionLoop: action,
                crop: input.crop,
                crossfadeToNextFrames: input.crossfadeToNextFrames
            ))
        }
        return SceneCompositionPlan(
            timing: timing,
            sceneVersion: request.sceneVersion,
            identityVersion: request.identityVersion,
            assetPins: records.map { SceneAssetPin(identity: $0.identity, sha256: $0.sha256.lowercased()) }
                .sorted {
                    $0.identity.assetID == $1.identity.assetID
                        ? $0.identity.version < $1.identity.version
                        : $0.identity.assetID < $1.identity.assetID
                },
            scenes: planned
        )
    }

    /// Re-check source bytes before a later consumer draws/encodes this plan.
    public static func validatePinnedAssets(_ plan: SceneCompositionPlan, library: AssetLibrary) throws {
        let records = try library.validatedApprovedAssets(pinned: plan.assetPins.map(\.identity))
        let current = Dictionary(uniqueKeysWithValues: records.map { ($0.identity, $0.sha256.lowercased()) })
        for pin in plan.assetPins where current[pin.identity] != pin.sha256 {
            throw SceneCompositionError.staleAsset(pin.identity, expected: pin.sha256, actual: current[pin.identity] ?? "missing")
        }
    }

    private static func assetIdentities(_ input: SceneCompositionInput) -> [AssetIdentity] {
        [input.interior, input.windowMask] + input.parallaxLayers.map(\.asset) + (input.actionLoop.map { [$0.clip] } ?? [])
    }

    private static func uniquePins(_ identities: [AssetIdentity]) -> [AssetIdentity] {
        var seen = Set<AssetIdentity>()
        return identities.filter { seen.insert($0).inserted }
    }

    private static func requireKind(_ identity: AssetIdentity, record: AssetRecord?, allowed: [AssetKind], description: String) throws {
        guard let record else { throw SceneCompositionError.invalidRequest("asset \(identity.assetID)@\(identity.version) is missing") }
        guard allowed.contains(where: { $0.rawValue == record.kind.rawValue }) else {
            throw SceneCompositionError.invalidAssetKind(identity, expected: description, actual: record.kind)
        }
    }

    private static func makeActionLoop(
        _ input: ActionLoopInput?,
        sceneFrames: Int64,
        records: [AssetIdentity: AssetRecord],
        pin: (AssetIdentity) throws -> SceneAssetPin
    ) throws -> PlannedActionLoop? {
        guard let input else { return nil }
        try requireKind(input.clip, record: records[input.clip], allowed: [.animationClip, .video], description: "an approved animation clip")
        guard input.clipFrames > 0, input.maximumRepeats > 0, input.maximumRepeats <= 3 else {
            throw SceneCompositionError.invalidRequest("action loops require positive clip frames and 1...3 repeats")
        }
        guard let record = records[input.clip],
              let duration = record.geometry.durationSeconds,
              let frameRate = record.geometry.frameRate,
              duration.isFinite, frameRate.isFinite,
              duration > 0, frameRate > 0,
              duration * frameRate >= Double(input.clipFrames) else {
            throw SceneCompositionError.invalidRequest("action loop frames exceed the verified clip duration")
        }
        let possible = input.clipFrames.multipliedReportingOverflow(by: Int64(input.maximumRepeats))
        guard !possible.overflow else { throw SceneCompositionError.overflow }
        let scheduled = min(sceneFrames, possible.partialValue)
        let wholeRepeats = scheduled / input.clipFrames
        let repeats = Int(wholeRepeats + (scheduled % input.clipFrames == 0 ? 0 : 1))
        return PlannedActionLoop(clip: try pin(input.clip), clipFrames: input.clipFrames, repeatCount: repeats, scheduledFrames: scheduled)
    }
}
