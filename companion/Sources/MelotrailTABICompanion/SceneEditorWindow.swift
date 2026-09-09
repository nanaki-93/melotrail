import AppKit
import Foundation

/// A native, resizable V05b editor surface. It presents plan-backed preview
/// pixels, rather than a mock video player, and leaves composition editing to
/// V05c.
public final class SceneEditorWindowController: NSWindowController, NSWindowDelegate {
    private let session: SceneEditorSession
    private let previewImage = NSImageView()
    private let sceneStrip = NSStackView()
    private let inspector = NSStackView()
    private let playPauseButton = NSButton(title: "Play", target: nil, action: nil)
    private let timeLabel = NSTextField(labelWithString: "00:00.00")
    private let seekSlider = NSSlider(value: 0, minValue: 0, maxValue: 1, target: nil, action: nil)
    private let statusLabel = NSTextField(labelWithString: "Loading scene preview…")
    private var observerToken: Any?
    private var sceneButtons: [NSButton] = []
    private var frameRate = 30

    public init(session: SceneEditorSession) throws {
        self.session = session
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 1_180, height: 760),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = "Melotrail TABI — Scene Preview"
        window.minSize = NSSize(width: 820, height: 660)
        window.isReleasedWhenClosed = false
        super.init(window: window)
        window.delegate = self
        try buildInterface()
    }

    required init?(coder: NSCoder) { nil }

    public func showEditor() {
        showWindow(nil)
        window?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    public func windowWillClose(_ notification: Notification) {
        releasePreview()
    }

    /// Drives the same visible controls used by a person and retains a bitmap
    /// of this real AppKit window plus machine-readable transport observations.
    /// The release executable enables this only for the companion validation
    /// script through an environment-owned output directory.
    public func recordValidationEvidence(
        in outputDirectory: URL,
        compositionRequestURL: URL,
        completion: @escaping (Result<URL, Error>) -> Void
    ) {
        do {
            let directoryValues = try outputDirectory.resourceValues(forKeys: [.isDirectoryKey])
            guard directoryValues.isDirectory == true else {
                throw SceneEditorEvidenceError.invalidOutputDirectory
            }
            let scenes = try session.sceneStrip()
            guard scenes.count >= 3 else {
                throw SceneEditorEvidenceError.insufficientScenes
            }
            guard window?.isVisible == true, window?.styleMask.contains(.resizable) == true else {
                throw SceneEditorEvidenceError.windowNotVisible
            }
            if let window {
                let originalFrame = window.frame
                var minimumFrame = originalFrame
                minimumFrame.size = window.minSize
                window.setFrame(minimumFrame, display: true)
                try captureWindow(to: outputDirectory.appendingPathComponent("editor-minimum-window.png"))
                window.setFrame(originalFrame, display: true)
            }
            let selected = scenes[1]
            let next = scenes[2]
            let playerCount = try session.snapshot().soundtrackPlayerCount
            guard playerCount == 1 else {
                throw SceneEditorEvidenceError.playerCount(playerCount)
            }

            sceneButtons[selected.sceneIndex].performClick(nil)
            waitForFrame(selected.startFrame) { [weak self] selectedResult in
                guard let self else { return }
                do {
                    let selectedFrame = try selectedResult.get()
                    self.playPauseButton.performClick(nil)
                    self.waitForFrame(atLeast: next.startFrame) { [weak self] boundaryResult in
                        guard let self else { return }
                        do {
                            let boundaryFrame = try boundaryResult.get()
                            if try self.session.snapshot().isPlaying {
                                self.playPauseButton.performClick(nil)
                            }
                            let captureURL = outputDirectory.appendingPathComponent("editor-window.png")
                            try self.captureWindow(to: captureURL)
                            self.seekSlider.integerValue = 1
                            _ = self.seekSlider.sendAction(self.seekSlider.action, to: self.seekSlider.target)
                            self.waitForFrame(1) { [weak self] seekResult in
                                guard let self else { return }
                                do {
                                    let soughtFrame = try seekResult.get()
                                    self.window?.close()
                                    let observations: [String: Any] = [
                                        "schemaVersion": 1,
                                        "releaseExecutableLaunched": true,
                                        "executablePath": CommandLine.arguments[0],
                                        "compositionRequestPath": compositionRequestURL.path,
                                        "windowVisible": true,
                                        "windowResizable": true,
                                        "selectedSceneIndex": selected.sceneIndex,
                                        "selectedSceneStartFrame": selectedFrame,
                                        "playedAcrossBoundaryFrame": boundaryFrame,
                                        "soughtFrame": soughtFrame,
                                        "soundtrackPlayerCountBeforeClose": playerCount,
                                        "soundtrackPlayerCountAfterClose": self.session.soundtrackPlayerCount,
                                        "frameObserverAndPlayerReleasedOnClose": self.session.isClosed,
                                        "capturePath": captureURL.path,
                                    ]
                                    let reportURL = outputDirectory.appendingPathComponent("editor-observations.json")
                                    try JSONSerialization.data(withJSONObject: observations, options: [.prettyPrinted, .sortedKeys])
                                        .write(to: reportURL, options: .withoutOverwriting)
                                    completion(.success(reportURL))
                                } catch {
                                    completion(.failure(error))
                                }
                            }
                        } catch {
                            completion(.failure(error))
                        }
                    }
                } catch {
                    completion(.failure(error))
                }
            }
        } catch {
            completion(.failure(error))
        }
    }

    private func buildInterface() throws {
        guard let content = window?.contentView else { return }
        content.wantsLayer = true
        content.layer?.backgroundColor = NSColor(calibratedRed: 0.043, green: 0.075, blue: 0.118, alpha: 1).cgColor

        let root = NSStackView()
        root.orientation = .vertical
        root.alignment = .leading
        root.spacing = 14
        root.edgeInsets = NSEdgeInsets(top: 18, left: 20, bottom: 18, right: 20)
        root.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(root)
        NSLayoutConstraint.activate([
            root.leadingAnchor.constraint(equalTo: content.leadingAnchor),
            root.trailingAnchor.constraint(equalTo: content.trailingAnchor),
            root.topAnchor.constraint(equalTo: content.topAnchor),
            root.bottomAnchor.constraint(equalTo: content.bottomAnchor),
        ])

        let title = NSTextField(labelWithString: "TABI companion · scene preview")
        title.font = .systemFont(ofSize: 20, weight: .semibold)
        title.textColor = .white
        statusLabel.textColor = NSColor(calibratedWhite: 0.72, alpha: 1)
        statusLabel.lineBreakMode = .byTruncatingMiddle
        let header = NSStackView(views: [title, NSView(), statusLabel])
        header.orientation = .horizontal
        header.alignment = .centerY
        header.spacing = 10
        header.setHuggingPriority(.defaultLow, for: .horizontal)
        root.addArrangedSubview(header)
        header.widthAnchor.constraint(equalTo: root.widthAnchor, constant: -40).isActive = true

        previewImage.imageScaling = .scaleProportionallyUpOrDown
        previewImage.imageAlignment = .alignCenter
        previewImage.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        previewImage.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        previewImage.wantsLayer = true
        previewImage.layer?.backgroundColor = NSColor.black.cgColor
        previewImage.translatesAutoresizingMaskIntoConstraints = false
        let previewBox = NSView()
        previewBox.wantsLayer = true
        previewBox.layer?.cornerRadius = 8
        previewBox.layer?.masksToBounds = true
        previewBox.addSubview(previewImage)
        NSLayoutConstraint.activate([
            previewImage.leadingAnchor.constraint(equalTo: previewBox.leadingAnchor),
            previewImage.trailingAnchor.constraint(equalTo: previewBox.trailingAnchor),
            previewImage.topAnchor.constraint(equalTo: previewBox.topAnchor),
            previewImage.bottomAnchor.constraint(equalTo: previewBox.bottomAnchor),
            previewBox.heightAnchor.constraint(greaterThanOrEqualToConstant: 240),
        ])

        configureInspector()
        let inspectorScroll = NSScrollView()
        inspectorScroll.documentView = inspector
        inspectorScroll.hasVerticalScroller = true
        inspectorScroll.drawsBackground = false
        inspectorScroll.translatesAutoresizingMaskIntoConstraints = false
        inspector.widthAnchor.constraint(equalToConstant: 280).isActive = true
        inspectorScroll.widthAnchor.constraint(equalToConstant: 280).isActive = true
        let stageRow = NSStackView(views: [previewBox, inspectorScroll])
        inspectorScroll.heightAnchor.constraint(equalTo: previewBox.heightAnchor).isActive = true
        stageRow.orientation = .horizontal
        stageRow.alignment = .top
        stageRow.spacing = 16
        root.addArrangedSubview(stageRow)
        stageRow.widthAnchor.constraint(equalTo: root.widthAnchor, constant: -40).isActive = true
        previewBox.widthAnchor.constraint(greaterThanOrEqualToConstant: 460).isActive = true

        let transport = makeTransport()
        root.addArrangedSubview(transport)
        transport.widthAnchor.constraint(equalTo: root.widthAnchor, constant: -40).isActive = true

        sceneStrip.orientation = .horizontal
        sceneStrip.alignment = .top
        sceneStrip.spacing = 10
        sceneStrip.edgeInsets = NSEdgeInsets(top: 4, left: 4, bottom: 4, right: 4)
        let stripScroll = NSScrollView()
        stripScroll.documentView = sceneStrip
        stripScroll.hasHorizontalScroller = true
        stripScroll.autohidesScrollers = true
        stripScroll.drawsBackground = false
        stripScroll.translatesAutoresizingMaskIntoConstraints = false
        stripScroll.heightAnchor.constraint(equalToConstant: 146).isActive = true
        root.addArrangedSubview(stripScroll)
        stripScroll.widthAnchor.constraint(equalTo: root.widthAnchor, constant: -40).isActive = true

        try populateSceneStrip()
        try refreshInspector()
        sceneStrip.layoutSubtreeIfNeeded()
        sceneStrip.setFrameSize(sceneStrip.fittingSize)
        inspector.layoutSubtreeIfNeeded()
        inspector.setFrameSize(inspector.fittingSize)
        try refreshCurrentFrame()
        observerToken = try session.observeFrames { [weak self] frame in
            self?.apply(frame: frame)
        }
        statusLabel.stringValue = "Ready · real soundtrack transport"
    }

    private func configureInspector() {
        inspector.orientation = .vertical
        inspector.alignment = .leading
        inspector.spacing = 8
        inspector.edgeInsets = NSEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        inspector.wantsLayer = true
        inspector.layer?.backgroundColor = NSColor(calibratedRed: 0.063, green: 0.098, blue: 0.137, alpha: 1).cgColor
        let heading = NSTextField(labelWithString: "Selected scene")
        heading.font = .systemFont(ofSize: 14, weight: .semibold)
        heading.textColor = .white
        inspector.addArrangedSubview(heading)
    }

    private func makeTransport() -> NSStackView {
        playPauseButton.target = self
        playPauseButton.action = #selector(togglePlayback)
        playPauseButton.setAccessibilityLabel("Play soundtrack")
        playPauseButton.bezelStyle = .rounded
        seekSlider.target = self
        seekSlider.action = #selector(seekChanged)
        seekSlider.setAccessibilityLabel("Seek soundtrack")
        timeLabel.font = .monospacedDigitSystemFont(ofSize: 12, weight: .regular)
        timeLabel.textColor = .white
        let transport = NSStackView(views: [playPauseButton, timeLabel, seekSlider])
        transport.orientation = .horizontal
        transport.alignment = .centerY
        transport.spacing = 12
        seekSlider.setContentHuggingPriority(.defaultLow, for: .horizontal)
        return transport
    }

    private func populateSceneStrip() throws {
        let thumbnails = try session.sceneStrip()
        let snapshot = try session.snapshot()
        frameRate = snapshot.frameRate
        seekSlider.maxValue = Double(max(snapshot.frameCount - 1, 1))
        for thumbnail in thumbnails {
            let button = NSButton(title: "", target: self, action: #selector(selectScene(_:)))
            button.bezelStyle = .regularSquare
            button.tag = thumbnail.sceneIndex
            button.image = NSImage(cgImage: thumbnail.image, size: NSSize(width: 132, height: 82))
            button.imagePosition = .imageAbove
            button.imageScaling = .scaleProportionallyUpOrDown
            button.setButtonType(.toggle)
            button.title = "\(thumbnail.label)\n\(format(frame: thumbnail.startFrame))"
            button.setAccessibilityLabel("Select scene \(thumbnail.label)")
            button.font = .systemFont(ofSize: 11)
            button.contentTintColor = .white
            button.toolTip = "\(thumbnail.sceneID): frames \(thumbnail.startFrame)–\(thumbnail.endFrame - 1)"
            button.widthAnchor.constraint(equalToConstant: 142).isActive = true
            button.heightAnchor.constraint(equalToConstant: 122).isActive = true
            sceneButtons.append(button)
            sceneStrip.addArrangedSubview(button)
        }
        applySelection(snapshot.selectedSceneIndex)
    }

    @objc private func selectScene(_ sender: NSButton) {
        do {
            try session.selectScene(sender.tag) { [weak self] completed in
                DispatchQueue.main.async {
                    guard let self else { return }
                    if completed {
                        self.applySelection(sender.tag)
                        self.updateAfterTransportChange()
                    } else {
                        self.showError("The soundtrack could not seek to the selected scene.")
                    }
                }
            }
        } catch {
            show(error)
        }
    }

    @objc private func togglePlayback() {
        do {
            let snapshot = try session.snapshot()
            if snapshot.isPlaying {
                session.pause()
            } else {
                try session.play()
            }
            updateAfterTransportChange()
        } catch {
            show(error)
        }
    }

    @objc private func seekChanged() {
        let frame = Int64(seekSlider.integerValue)
        session.seek(toFrame: frame) { [weak self] completed in
            DispatchQueue.main.async {
                guard let self else { return }
                completed ? self.updateAfterTransportChange() : self.showError("The soundtrack could not seek to that frame.")
            }
        }
    }

    private func updateAfterTransportChange() {
        do {
            try refreshCurrentFrame()
            try refreshInspector()
            let snapshot = try session.snapshot()
            playPauseButton.title = snapshot.isPlaying ? "Pause" : "Play"
        } catch {
            show(error)
        }
    }

    private func refreshCurrentFrame() throws {
        apply(frame: try session.currentFrame())
    }

    private func apply(frame: ScenePreviewFrame) {
        if !Thread.isMainThread {
            DispatchQueue.main.async { [weak self] in self?.apply(frame: frame) }
            return
        }
        previewImage.image = NSImage(cgImage: frame.image, size: NSSize(width: frame.image.width, height: frame.image.height))
        seekSlider.integerValue = Int(frame.frame)
        timeLabel.stringValue = format(seconds: frame.soundtrackTime.seconds)
        statusLabel.stringValue = "Frame \(frame.frame) · \(frame.sceneIDs.joined(separator: " + "))"
    }

    private func refreshInspector() throws {
        let details = try session.inspector()
        while inspector.arrangedSubviews.count > 1, let last = inspector.arrangedSubviews.last {
            inspector.removeArrangedSubview(last)
            last.removeFromSuperview()
        }
        let facts = [
            ("Scene", details.label),
            ("Range", "\(format(seconds: details.startSeconds)) – \(format(seconds: details.endSeconds))"),
            ("Frames", "\(details.startFrame) – \(details.endFrame - 1)"),
            ("Interior", assetLabel(details.interior)),
            ("Window mask", assetLabel(details.windowMask)),
            ("Scenery layers", "\(details.parallaxLayerCount)"),
            ("Action", details.actionClip.map { assetLabel($0) } ?? "None"),
            ("Crossfade", details.crossfadeToNextFrames == 0 ? "None" : "\(details.crossfadeToNextFrames) frames"),
        ]
        for (name, value) in facts {
            let label = NSTextField(wrappingLabelWithString: "\(name)\n\(value)")
            label.font = .systemFont(ofSize: 12)
            label.textColor = NSColor(calibratedWhite: 0.82, alpha: 1)
            label.maximumNumberOfLines = 2
            inspector.addArrangedSubview(label)
        }
    }

    private func applySelection(_ index: Int) {
        for (buttonIndex, button) in sceneButtons.enumerated() {
            button.state = buttonIndex == index ? .on : .off
        }
    }

    private func releasePreview() {
        if let observerToken {
            session.removeFrameObserver(observerToken)
            self.observerToken = nil
        }
        session.close()
    }

    private func waitForFrame(
        _ frame: Int64,
        timeout: Date = Date().addingTimeInterval(3),
        completion: @escaping (Result<Int64, Error>) -> Void
    ) {
        waitForFrame(matching: { $0 == frame }, timeout: timeout, completion: completion)
    }

    private func waitForFrame(
        atLeast frame: Int64,
        timeout: Date = Date().addingTimeInterval(3),
        completion: @escaping (Result<Int64, Error>) -> Void
    ) {
        waitForFrame(matching: { $0 >= frame }, timeout: timeout, completion: completion)
    }

    private func waitForFrame(
        matching predicate: @escaping (Int64) -> Bool,
        timeout: Date,
        completion: @escaping (Result<Int64, Error>) -> Void
    ) {
        do {
            let frame = try session.snapshot().currentFrame
            if predicate(frame) {
                completion(.success(frame))
            } else if Date() >= timeout {
                completion(.failure(SceneEditorEvidenceError.transportTimeout(frame)))
            } else {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.02) { [weak self] in
                    guard let self else { return }
                    self.waitForFrame(matching: predicate, timeout: timeout, completion: completion)
                }
            }
        } catch {
            completion(.failure(error))
        }
    }

    private func captureWindow(to url: URL) throws {
        guard let content = window?.contentView else {
            throw SceneEditorEvidenceError.windowNotVisible
        }
        window?.displayIfNeeded()
        content.layoutSubtreeIfNeeded()
        for view in [inspector, sceneButtons[0], playPauseButton] {
            let rect = view.convert(view.bounds, to: content)
            guard content.bounds.contains(rect), rect.width > 0, rect.height > 0 else {
                throw SceneEditorEvidenceError.captureFailed
            }
        }
        let bounds = content.bounds
        guard bounds.width > 0, bounds.height > 0,
              let representation = content.bitmapImageRepForCachingDisplay(in: bounds) else {
            throw SceneEditorEvidenceError.captureFailed
        }
        content.cacheDisplay(in: bounds, to: representation)
        guard let png = representation.representation(using: .png, properties: [:]) else {
            throw SceneEditorEvidenceError.captureFailed
        }
        try png.write(to: url, options: .withoutOverwriting)
    }

    private func show(_ error: Error) { showError(error.localizedDescription) }

    private func showError(_ message: String) {
        statusLabel.stringValue = "Preview error: \(message)"
        NSAlert(error: NSError(domain: "MelotrailTABIEditor", code: 1, userInfo: [NSLocalizedDescriptionKey: message])).beginSheetModal(for: window!)
    }

    private func assetLabel(_ identity: AssetIdentity) -> String { "\(identity.assetID)@\(identity.version)" }
    private func format(frame: Int64) -> String { format(seconds: Double(frame) / Double(frameRate)) }
    private func format(seconds: Double) -> String {
        let clamped = max(seconds, 0)
        return String(format: "%02d:%05.2f", Int(clamped) / 60, clamped.truncatingRemainder(dividingBy: 60))
    }
}

private enum SceneEditorEvidenceError: Error, LocalizedError {
    case invalidOutputDirectory
    case insufficientScenes
    case windowNotVisible
    case playerCount(Int)
    case transportTimeout(Int64)
    case captureFailed

    var errorDescription: String? {
        switch self {
        case .invalidOutputDirectory:
            "The editor evidence destination must be an existing directory."
        case .insufficientScenes:
            "The editor evidence fixture must contain at least three scenes."
        case .windowNotVisible:
            "The native editor window is not visible and resizable."
        case .playerCount(let count):
            "Expected one soundtrack player before close; observed \(count)."
        case .transportTimeout(let frame):
            "The real soundtrack transport stopped at frame \(frame)."
        case .captureFailed:
            "The native editor window could not be captured."
        }
    }
}
