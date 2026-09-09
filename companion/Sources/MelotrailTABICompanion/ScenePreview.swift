import AVFoundation
import CoreGraphics
import CoreMedia
import Foundation
import ImageIO

/// The preview output size is deliberately explicit. It is the same pixel
/// geometry an output renderer must later consume; the preview never invents a
/// smaller, differently-cropped scene coordinate system.
public struct PreviewOutputGeometry: Equatable, Sendable {
    public let width: Int
    public let height: Int

    public init(width: Int, height: Int) throws {
        guard width > 0, height > 0 else {
            throw ScenePreviewError.invalidGeometry("output width and height must be positive")
        }
        self.width = width
        self.height = height
    }
}

public enum ScenePreviewError: Error, LocalizedError, Sendable {
    case invalidGeometry(String)
    case invalidSoundtrack(String)
    case invalidFrame(Int64)
    case unreadableAsset(AssetIdentity, String)
    case unreadableAction(AssetIdentity, String)

    public var errorDescription: String? {
        switch self {
        case .invalidGeometry(let message): "Preview geometry is invalid: \(message)"
        case .invalidSoundtrack(let message): "Preview soundtrack is invalid: \(message)"
        case .invalidFrame: "Preview frame is outside the immutable scene plan."
        case .unreadableAsset(let identity, let message): "Cannot draw \(identity.assetID)@\(identity.version): \(message)"
        case .unreadableAction(let identity, let message): "Cannot decode \(identity.assetID)@\(identity.version): \(message)"
        }
    }
}

/// One fully resolved preview frame. `image` is composed from the exact
/// approved stills, masks, and clip frames named by `composition`; it is never
/// a placeholder video frame.
public struct ScenePreviewFrame {
    public let frame: Int64
    public let soundtrackTime: CMTime
    public let sceneIDs: [String]
    public let image: CGImage
}

/**
 * A preview stage for one immutable composition plan.
 *
 * It owns exactly one AVPlayer, and that player opens only the caller-pinned
 * finished soundtrack. Visual frames are drawn on demand from the V04 plan,
 * so playback/seek cannot acquire a second music source or independently
 * round scene boundaries. There is intentionally no MIDI/audio rendering,
 * output encoding, or project write in this type.
 */
public final class ScenePreviewStage {
    public private(set) var composition: SceneCompositionPlan
    public let geometry: PreviewOutputGeometry
    public let soundtrack: FinishedSoundtrack

    private let soundtrackPlayer: AVPlayer
    private let library: AssetLibrary
    private let assets: [AssetIdentity: AssetRecord]
    private let assetURLs: [AssetIdentity: URL]
    private var images: [AssetIdentity: CGImage] = [:]
    private var actionGenerators: [AssetIdentity: AVAssetImageGenerator] = [:]
    private var observerTokens: [Any] = []
    private let lock = NSLock()

    public init(
        composition: SceneCompositionPlan,
        library: AssetLibrary,
        soundtrack: FinishedSoundtrack,
        geometry: PreviewOutputGeometry
    ) throws {
        guard composition.timing.soundtrackSHA256 == soundtrack.sha256 else {
            throw ScenePreviewError.invalidSoundtrack("the composition plan does not pin this soundtrack")
        }
        guard composition.timing.frameRate > 0, composition.timing.frameRate <= Int(Int32.max) else {
            throw ScenePreviewError.invalidGeometry("the plan frame rate cannot be represented by the soundtrack transport")
        }
        // Re-open immediately before use so a retained plan cannot preview
        // replaced soundtrack bytes.
        let verifiedSoundtrack = try FinishedSoundtrack.open(url: soundtrack.url, expectedSHA256: soundtrack.sha256)
        guard verifiedSoundtrack.duration == composition.timing.soundtrackDuration else {
            throw ScenePreviewError.invalidSoundtrack("the soundtrack duration no longer matches the composition plan")
        }
        try SceneComposer.validatePinnedAssets(composition, library: library)
        let records = try library.validatedApprovedAssets(pinned: composition.assetPins.map(\.identity))
        let byIdentity = Dictionary(uniqueKeysWithValues: records.map { ($0.identity, $0) })
        let root = library.rootURL.standardizedFileURL.resolvingSymlinksInPath()
        let prefix = root.path.hasSuffix("/") ? root.path : root.path + "/"
        var urls: [AssetIdentity: URL] = [:]
        for record in records {
            let url = root.appendingPathComponent(record.relativeMediaPath).standardizedFileURL.resolvingSymlinksInPath()
            guard url.path.hasPrefix(prefix), try AssetDigest.sha256(of: url) == record.sha256.lowercased() else {
                throw ScenePreviewError.unreadableAsset(record.identity, "the approved pinned bytes changed before preview")
            }
            urls[record.identity] = url
        }

        self.composition = composition
        self.geometry = geometry
        self.soundtrack = verifiedSoundtrack
        self.library = library
        self.assets = byIdentity
        self.assetURLs = urls
        self.soundtrackPlayer = AVPlayer(url: verifiedSoundtrack.url)
    }

    deinit {
        soundtrackPlayer.pause()
        for token in observerTokens { soundtrackPlayer.removeTimeObserver(token) }
    }

    /// Starts the one finished-soundtrack player. The caller renders the
    /// corresponding `currentFrame()` from the same plan on every observation.
    public func play() throws {
        try validateInputs()
        soundtrackPlayer.play()
    }

    public func pause() {
        soundtrackPlayer.pause()
    }

    /// Applies an edited plan without replacing the stage's one soundtrack
    /// player or its registered observers. Editor controls may change only
    /// resolver-backed scene facts; timing and pinned assets stay immutable.
    public func updateComposition(_ updated: SceneCompositionPlan) throws {
        guard updated.timing == composition.timing else {
            throw ScenePreviewError.invalidGeometry("an editor edit cannot change soundtrack timing or frame extent")
        }
        guard updated.assetPins == composition.assetPins else {
            throw ScenePreviewError.invalidGeometry("an editor edit cannot change approved asset pins")
        }
        try SceneComposer.validatePinnedAssets(updated, library: library)
        composition = updated
    }

    public var isPlaying: Bool { soundtrackPlayer.timeControlStatus == .playing }

    public var currentSoundtrackTime: CMTime { soundtrackPlayer.currentTime() }

    public var currentFrameIndex: Int64 {
        frameIndex(for: soundtrackPlayer.currentTime()) ?? 0
    }

    /// Seeks the transport to a plan-owned output frame. Public preview-frame
    /// snapshots derive from this player's time, so playback never depends on
    /// an optional observer to advance the visuals.
    /// The exact zero-tolerance seek prevents a visual boundary from drifting
    /// ahead of the real soundtrack transport.
    public func seek(toFrame frame: Int64, completion: @escaping (Bool) -> Void) {
        guard frame >= 0, frame < composition.timing.frameCount else {
            completion(false)
            return
        }
        do {
            try validateInputs()
        } catch {
            completion(false)
            return
        }
        let time = CMTime(value: frame, timescale: CMTimeScale(composition.timing.frameRate))
        soundtrackPlayer.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero) { finished in
            completion(finished)
        }
    }

    /// Registers a playback observer at the plan's exact output frame cadence.
    /// It returns the token so a UI caller can remove it on navigation; the
    /// stage also releases retained observers at deinitialization.
    @discardableResult
    public func observeFrames(queue: DispatchQueue = .main, _ observer: @escaping (ScenePreviewFrame) -> Void) -> Any {
        let interval = CMTime(value: 1, timescale: CMTimeScale(composition.timing.frameRate))
        let token = soundtrackPlayer.addPeriodicTimeObserver(forInterval: interval, queue: queue) { [weak self] time in
            guard let self else { return }
            guard let frame = self.frameIndex(for: time), let preview = try? self.render(frame: frame) else {
                self.pause()
                return
            }
            observer(preview)
        }
        lock.lock()
        observerTokens.append(token)
        lock.unlock()
        return token
    }

    public func removeFrameObserver(_ token: Any) {
        soundtrackPlayer.removeTimeObserver(token)
        lock.lock()
        let target = ObjectIdentifier(token as AnyObject)
        observerTokens.removeAll { ObjectIdentifier($0 as AnyObject) == target }
        lock.unlock()
    }

    public func currentFrame() throws -> ScenePreviewFrame {
        try render(frame: currentFrameIndex)
    }

    /// Draws a real preview image from the immutable V04 scene facts. The
    /// compositor crops the completed scene once, before crossfade opacity; no
    /// source is stretched, substituted, or generated.
    public func render(frame: Int64) throws -> ScenePreviewFrame {
        guard frame >= 0, frame < composition.timing.frameCount else {
            throw ScenePreviewError.invalidFrame(frame)
        }
        try validateInputs()
        let planFrame = try composition.frame(at: frame)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let context = CGContext(
            data: nil,
            width: geometry.width,
            height: geometry.height,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            throw ScenePreviewError.invalidGeometry("cannot allocate the requested output pixels")
        }
        let canvas = CGRect(x: 0, y: 0, width: geometry.width, height: geometry.height)
        context.clear(canvas)
        for layer in planFrame.layers {
            try render(layer: layer, into: context, canvas: canvas)
        }
        guard let image = context.makeImage() else {
            throw ScenePreviewError.invalidGeometry("cannot finalize preview pixels")
        }
        return ScenePreviewFrame(
            frame: frame,
            soundtrackTime: CMTime(value: frame, timescale: CMTimeScale(composition.timing.frameRate)),
            sceneIDs: planFrame.layers.map(\.timingSceneID),
            image: image
        )
    }

    private func render(layer: SceneCompositionFrameLayer, into destination: CGContext, canvas: CGRect) throws {
        guard layer.opacityDenominator > 0,
              layer.opacityNumerator >= 0,
              layer.opacityNumerator <= layer.opacityDenominator else {
            throw ScenePreviewError.invalidGeometry("frame opacity must be within its positive rational range")
        }
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let source = CGContext(
            data: nil,
            width: geometry.width,
            height: geometry.height,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            throw ScenePreviewError.invalidGeometry("cannot allocate a scene layer")
        }
        source.clear(canvas)
        let mask = try image(for: layer.windowMask.identity)
        source.saveGState()
        source.clip(to: canvas, mask: mask)
        for parallax in layer.parallax {
            let scenery = try image(for: parallax.asset.identity)
            drawTiled(scenery, phase: parallax.phasePixels, into: source, canvas: canvas)
        }
        source.restoreGState()
        try drawAspectFill(image(for: layer.interior.identity), crop: .fullFrame, into: source, canvas: canvas)
        if let action = layer.action {
            try drawAspectFill(actionImage(for: action), crop: .fullFrame, into: source, canvas: canvas)
        }
        guard let composed = source.makeImage() else {
            throw ScenePreviewError.invalidGeometry("cannot finalize a scene layer")
        }
        destination.saveGState()
        destination.setAlpha(CGFloat(layer.opacityNumerator) / CGFloat(layer.opacityDenominator))
        // Crop the complete scene so the window mask and foreground stay aligned.
        try drawAspectFill(composed, crop: layer.crop, into: destination, canvas: canvas)
        destination.restoreGState()
    }

    private func image(for identity: AssetIdentity) throws -> CGImage {
        if let cached = images[identity] { return cached }
        guard let url = assetURLs[identity], let record = assets[identity] else {
            throw ScenePreviewError.unreadableAsset(identity, "asset is not pinned by this plan")
        }
        guard record.kind != .animationClip, record.kind != .video,
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            throw ScenePreviewError.unreadableAsset(identity, "the approved still cannot be decoded")
        }
        images[identity] = image
        return image
    }

    private func actionImage(for action: SceneActionFrame) throws -> CGImage {
        guard let record = assets[action.clip.identity], let url = assetURLs[action.clip.identity],
              let frameRate = record.geometry.frameRate, frameRate > 0 else {
            throw ScenePreviewError.unreadableAction(action.clip.identity, "the approved clip has no measured frame rate")
        }
        let generator: AVAssetImageGenerator
        if let cached = actionGenerators[action.clip.identity] {
            generator = cached
        } else {
            let created = AVAssetImageGenerator(asset: AVURLAsset(url: url))
            created.appliesPreferredTrackTransform = true
            created.requestedTimeToleranceBefore = .zero
            created.requestedTimeToleranceAfter = .zero
            actionGenerators[action.clip.identity] = created
            generator = created
        }
        var actual = CMTime.zero
        let requested = CMTime(seconds: Double(action.sourceFrame) / frameRate, preferredTimescale: 600_000)
        do {
            let image = try generator.copyCGImage(at: requested, actualTime: &actual)
            let maximumDelta = 0.5 / frameRate
            guard abs(actual.seconds - requested.seconds) <= maximumDelta else {
                throw ScenePreviewError.unreadableAction(action.clip.identity, "the decoded clip frame is outside the approved source-frame cadence")
            }
            return image
        } catch let error as ScenePreviewError {
            throw error
        } catch {
            throw ScenePreviewError.unreadableAction(action.clip.identity, error.localizedDescription)
        }
    }

    private func drawTiled(_ image: CGImage, phase: Int64, into context: CGContext, canvas: CGRect) {
        let scale = canvas.height / CGFloat(image.height)
        let tileWidth = CGFloat(image.width) * scale
        guard tileWidth > 0 else { return }
        let offset = CGFloat(phase) * scale
        var x = -offset
        while x > -tileWidth { x -= tileWidth }
        while x < canvas.maxX {
            context.draw(image, in: CGRect(x: x, y: canvas.minY, width: tileWidth, height: canvas.height))
            x += tileWidth
        }
    }

    private func drawAspectFill(_ image: CGImage, crop: SceneCrop, into context: CGContext, canvas: CGRect) throws {
        guard image.width > 0, image.height > 0 else {
            throw ScenePreviewError.invalidGeometry("an approved source image has zero dimensions")
        }
        guard crop.x >= 0, crop.y >= 0, crop.width > 0, crop.height > 0,
              crop.x <= 10_000, crop.y <= 10_000,
              crop.width <= 10_000 - crop.x, crop.height <= 10_000 - crop.y else {
            throw ScenePreviewError.invalidGeometry("the scene crop is outside normalized source bounds")
        }
        let source = CGRect(
            x: CGFloat(crop.x) * CGFloat(image.width) / 10_000,
            y: CGFloat(crop.y) * CGFloat(image.height) / 10_000,
            width: CGFloat(crop.width) * CGFloat(image.width) / 10_000,
            height: CGFloat(crop.height) * CGFloat(image.height) / 10_000
        ).integral
        guard source.width > 0, source.height > 0,
              let cropped = image.cropping(to: source) else {
            throw ScenePreviewError.invalidGeometry("the scene crop selects no source pixels")
        }
        let scale = max(canvas.width / CGFloat(cropped.width), canvas.height / CGFloat(cropped.height))
        let width = CGFloat(cropped.width) * scale
        let height = CGFloat(cropped.height) * scale
        let rect = CGRect(x: canvas.midX - width / 2, y: canvas.midY - height / 2, width: width, height: height)
        context.draw(cropped, in: rect)
    }

    private func frameIndex(for time: CMTime) -> Int64? {
        guard time.isNumeric, CMTimeCompare(time, .zero) >= 0 else { return 0 }
        let scaled = CMTimeConvertScale(time, timescale: CMTimeScale(composition.timing.frameRate), method: .roundTowardZero)
        let frame = min(max(scaled.value, 0), composition.timing.frameCount - 1)
        return frame
    }

    /// Retained plans are not permission to keep using replaced media. Every
    /// rendered frame and every transport start re-checks the caller-pinned
    /// soundtrack and approved asset bytes before either can be presented.
    private func validateInputs() throws {
        let current = try FinishedSoundtrack.open(url: soundtrack.url, expectedSHA256: soundtrack.sha256)
        guard current.duration == composition.timing.soundtrackDuration else {
            throw ScenePreviewError.invalidSoundtrack("the soundtrack duration no longer matches the composition plan")
        }
        try SceneComposer.validatePinnedAssets(composition, library: library)
    }
}
