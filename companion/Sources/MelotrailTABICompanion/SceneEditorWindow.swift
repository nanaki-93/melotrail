import AppKit
import Foundation

/// Keeps transport shortcuts at the native-window boundary so they work for
/// the compact editor without taking key events away from an active text field.
private final class SceneEditorWindow: NSWindow {
    var allowsOversizedEvidence = false

    override func constrainFrameRect(_ frameRect: NSRect, to screen: NSScreen?) -> NSRect {
        allowsOversizedEvidence ? frameRect : super.constrainFrameRect(frameRect, to: screen)
    }

    var shortcutHandler: ((NSEvent) -> Bool)?

    override func sendEvent(_ event: NSEvent) {
        if event.type == .keyDown, shortcutHandler?(event) == true { return }
        super.sendEvent(event)
    }
}

/// A native, resizable V05b editor surface. It presents plan-backed preview
/// pixels, rather than a mock video player, and leaves composition editing to
/// V05c.
public final class SceneEditorWindowController: NSWindowController, NSWindowDelegate {
    private var session: SceneEditorSession
    private let sessionDocumentURL: URL?
    private let animationLedgerURL: URL?
    private let previewImage = NSImageView()
    private let sceneStrip = NSStackView()
    private let inspector = NSStackView()
    private let inspectorScroll = NSScrollView()
    private let playPauseButton = NSButton(title: "Play", target: nil, action: nil)
    private let stopPreviewButton = NSButton(title: "Stop preview", target: nil, action: nil)
    private let restartPreviewButton = NSButton(title: "Restart preview", target: nil, action: nil)
    private let saveSessionButton = NSButton(title: "Save session", target: nil, action: nil)
    private let openSessionButton = NSButton(title: "Open saved session", target: nil, action: nil)
    private let reloadStateButton = NSButton(title: "Reload asset/job state", target: nil, action: nil)
    private let timeLabel = NSTextField(labelWithString: "00:00.00")
    private let seekSlider = NSSlider(value: 0, minValue: 0, maxValue: 1, target: nil, action: nil)
    private let statusLabel = NSTextField(labelWithString: "Loading scene preview…")
    private let shortcutHint = NSTextField(labelWithString: "Space play/pause · ←/→ frame · Home/End timeline · Esc stop preview")
    private var displayedInspector: SceneEditorInspector?
    private var cropFields: [NSTextField] = []
    private var motionFields: [NSTextField] = []
    private var crossfadeField: NSTextField?
    private var observerToken: Any?
    private var sceneButtons: [NSButton] = []
    private var frameRate = 30
    private var stageRow: NSStackView?
    private var previewBox: NSView?
    private var stripScroll: NSScrollView?
    private var inspectorWidthConstraint: NSLayoutConstraint?
    private var inspectorScrollWidthConstraint: NSLayoutConstraint?
    private var compactPreviewWidthConstraint: NSLayoutConstraint?
    private var compactInspectorDocumentWidthConstraint: NSLayoutConstraint?
    private var compactInspectorWidthConstraint: NSLayoutConstraint?
    private var compactInspectorHeightConstraint: NSLayoutConstraint?
    private var inspectorHeightConstraint: NSLayoutConstraint?

    public init(
        session: SceneEditorSession,
        sessionDocumentURL: URL? = nil,
        animationLedgerURL: URL? = nil
    ) throws {
        self.session = session
        self.sessionDocumentURL = sessionDocumentURL
        self.animationLedgerURL = animationLedgerURL
        let window = SceneEditorWindow(
            contentRect: NSRect(x: 0, y: 0, width: 1_180, height: 760),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = "Melotrail TABI — Scene Preview"
        window.minSize = NSSize(width: 720, height: 660)
        window.isReleasedWhenClosed = false
        super.init(window: window)
        window.delegate = self
        window.shortcutHandler = { [weak self] event in self?.handleShortcut(event) ?? false }
        try buildInterface()
    }

    required init?(coder: NSCoder) { nil }

    public func showEditor() {
        showWindow(nil)
        window?.makeKeyAndOrderFront(nil)
        window?.makeFirstResponder(sceneButtons.first)
        NSApp.activate(ignoringOtherApps: true)
    }

    public func windowWillClose(_ notification: Notification) {
        releasePreview()
    }

    public func windowWillResize(_ sender: NSWindow, to frameSize: NSSize) -> NSSize {
        let contentWidth = sender.contentRect(forFrameRect: NSRect(origin: .zero, size: frameSize)).width
        updateCompactLayout(forWidth: contentWidth)
        return frameSize
    }

    public func windowDidResize(_ notification: Notification) {
        updateCompactLayout()
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
            var layouts: [[String: Any]] = []
            if let window {
                let originalSize = window.contentView?.bounds.size ?? window.frame.size
                let evidenceWindow = window as? SceneEditorWindow
                evidenceWindow?.allowsOversizedEvidence = true
                defer { evidenceWindow?.allowsOversizedEvidence = false }
                let captureSizes: [(String, NSSize)] = [
                    ("1536x1024", NSSize(width: 1_536, height: 1_024)),
                    ("1280x900", NSSize(width: 1_280, height: 900)),
                    ("720x900", NSSize(width: 720, height: 900)),
                ]
                for (name, size) in captureSizes {
                    updateCompactLayout(forWidth: size.width)
                    window.setContentSize(size)
                    window.contentView?.layoutSubtreeIfNeeded()
                    updateCompactLayout()
                    inspector.layoutSubtreeIfNeeded()
                    guard let content = window.contentView,
                          abs(content.bounds.width - size.width) <= 1,
                          abs(content.bounds.height - size.height) <= 1 else {
                        fputs("capture-size-mismatch: requested \(size), actual \(String(describing: window.contentView?.bounds))\n", stderr)
                        throw SceneEditorEvidenceError.captureFailed
                    }
                    let compact = size.width <= 900
                    let expectedPreview = NSRect(x: 20, y: compact ? 414 : 248,
                        width: size.width - (compact ? 40 : 336), height: size.height - (compact ? 470 : 304))
                    let expectedInspector = NSRect(x: compact ? 20 : size.width - 300, y: 248,
                        width: compact ? size.width - 40 : 280, height: compact ? 150 : size.height - 304)
                    for (view, expected) in [(previewImage as NSView, expectedPreview), (inspectorScroll as NSView, expectedInspector)] {
                        let actual = view.convert(view.bounds, to: content)
                        guard abs(actual.minX - expected.minX) <= 8, abs(actual.minY - expected.minY) <= 8,
                              abs(actual.width - expected.width) <= 8, abs(actual.height - expected.height) <= 8 else {
                            fputs("capture-layout-mismatch \(name): actual \(actual), expected \(expected)\n", stderr)
                            throw SceneEditorEvidenceError.captureFailed
                        }
                    }
                    guard abs(inspector.bounds.width - expectedInspector.width) <= 8 else {
                        fputs("capture-inspector-document-width-mismatch: \(inspector.bounds.width), expected \(expectedInspector.width)\n", stderr)
                        throw SceneEditorEvidenceError.captureFailed
                    }
                    let rendered = try session.currentFrame().image
                    guard rendered.width == session.geometry.width, rendered.height == session.geometry.height else {
                        throw SceneEditorEvidenceError.captureFailed
                    }
                    try verifyInspectorReachability()
                    let captureURL = outputDirectory.appendingPathComponent("editor-\(name).png")
                    try captureWindow(to: captureURL)
                    guard let content = window.contentView else { throw SceneEditorEvidenceError.windowNotVisible }
                    layouts.append(layoutMeasurement(name: name, content: content))
                }
                updateCompactLayout(forWidth: originalSize.width)
                window.setContentSize(originalSize)
                window.contentView?.layoutSubtreeIfNeeded()
                updateCompactLayout()
                if let field = crossfadeField {
                    field.scrollToVisible(field.bounds)
                    try captureWindow(to: outputDirectory.appendingPathComponent("editor-controls.png"))
                }
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
                            if try self.session.snapshot().isPlaying { self.playPauseButton.performClick(nil) }
                            let captureURL = outputDirectory.appendingPathComponent("editor-window.png")
                            try self.captureWindow(to: captureURL)
                            self.dispatchShortcut(characters: "", keyCode: 124)
                            self.waitForFrame(boundaryFrame + 1) { [weak self] keyboardResult in
                                guard let self else { return }
                                do {
                                    let keyboardFrame = try keyboardResult.get()
                                    let finalFrame = Int64(self.seekSlider.maxValue)
                                    self.seekSlider.integerValue = Int(finalFrame)
                                    _ = self.seekSlider.sendAction(self.seekSlider.action, to: self.seekSlider.target)
                                    self.waitForFrame(finalFrame) { [weak self] seekResult in
                                        guard let self else { return }
                                        do {
                                            let soughtFrame = try seekResult.get()
                                            let finalPreview = try self.session.currentFrame()
                                            let finalEndFrame = try self.session.snapshot().frameCount
                                            self.window?.close()
                                            let observations: [String: Any] = [
                                                "schemaVersion": 2,
                                                "releaseExecutableLaunched": true,
                                                "executablePath": CommandLine.arguments[0],
                                                "compositionRequestPath": compositionRequestURL.path,
                                                "windowVisible": true,
                                                "windowResizable": true,
                                                "selectedSceneIndex": selected.sceneIndex,
                                                "selectedSceneStartFrame": selectedFrame,
                                                "playedAcrossBoundaryFrame": boundaryFrame,
                                                "keyboardRightFrame": keyboardFrame,
                                                "soughtFrame": soughtFrame,
                                                "finalPreviewFrame": finalPreview.frame,
                                                "finalAudioTailEndFrame": finalEndFrame,
                                                "finalAudioTailSeconds": Double(finalEndFrame) / Double(self.frameRate),
                                                "soundtrackPlayerCountBeforeClose": playerCount,
                                                "soundtrackPlayerCountAfterClose": self.session.soundtrackPlayerCount,
                                                "frameObserverAndPlayerReleasedOnClose": self.session.isClosed,
                                                "capturePath": captureURL.path,
                                                "layoutCaptures": layouts,
                                            ]
                                            let reportURL = outputDirectory.appendingPathComponent("editor-observations.json")
                                            try JSONSerialization.data(withJSONObject: observations, options: [.prettyPrinted, .sortedKeys])
                                                .write(to: reportURL, options: .withoutOverwriting)
                                            completion(.success(reportURL))
                                        } catch { completion(.failure(error)) }
                                    }
                                } catch { completion(.failure(error)) }
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
        statusLabel.setAccessibilityLabel("Editor status")
        shortcutHint.font = .systemFont(ofSize: 11)
        shortcutHint.textColor = NSColor(calibratedWhite: 0.72, alpha: 1)
        shortcutHint.setAccessibilityLabel("Keyboard shortcuts")
        let header = NSStackView(views: [title, NSView(), shortcutHint, statusLabel])
        header.orientation = .horizontal
        header.alignment = .centerY
        header.spacing = 10
        header.setHuggingPriority(.defaultLow, for: .horizontal)
        root.addArrangedSubview(header)
        header.widthAnchor.constraint(equalTo: root.widthAnchor, constant: -40).isActive = true

        previewImage.imageScaling = .scaleProportionallyUpOrDown
        previewImage.imageAlignment = .alignCenter
        previewImage.setAccessibilityLabel("Rendered scene preview")
        previewImage.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        previewImage.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        previewImage.wantsLayer = true
        previewImage.layer?.backgroundColor = NSColor.black.cgColor
        previewImage.translatesAutoresizingMaskIntoConstraints = false
        let previewBox = NSView()
        self.previewBox = previewBox
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
        inspectorScroll.documentView = inspector
        inspectorScroll.hasVerticalScroller = true
        inspectorScroll.drawsBackground = false
        inspectorScroll.translatesAutoresizingMaskIntoConstraints = false
        inspector.setAccessibilityLabel("Selected scene inspector")
        inspectorScroll.setAccessibilityLabel("Selected scene inspector")
        inspectorWidthConstraint = inspector.widthAnchor.constraint(equalToConstant: 280)
        inspectorWidthConstraint?.isActive = true
        inspectorScrollWidthConstraint = inspectorScroll.widthAnchor.constraint(equalToConstant: 280)
        inspectorScrollWidthConstraint?.isActive = true
        let stageRow = NSStackView(views: [previewBox, inspectorScroll])
        self.stageRow = stageRow
        inspectorHeightConstraint = inspectorScroll.heightAnchor.constraint(equalTo: previewBox.heightAnchor)
        inspectorHeightConstraint?.isActive = true
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
        self.stripScroll = stripScroll
        stripScroll.documentView = sceneStrip
        stripScroll.hasHorizontalScroller = true
        stripScroll.autohidesScrollers = true
        stripScroll.drawsBackground = false
        stripScroll.translatesAutoresizingMaskIntoConstraints = false
        stripScroll.heightAnchor.constraint(equalToConstant: 146).isActive = true
        stripScroll.setAccessibilityLabel("Scene timeline")
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
        updateCompactLayout()
    }

    /// Reference-08 keeps its scene preview, inspector and strip as separate
    /// regions. At compact width the same regions stack so controls stay in
    /// the window rather than being clipped or hidden behind the transport.
    private func updateCompactLayout(forWidth proposedWidth: CGFloat? = nil) {
        guard let content = window?.contentView,
              let stageRow,
              let previewBox else { return }
        let compact = (proposedWidth ?? content.bounds.width) <= 900
        stageRow.orientation = compact ? .vertical : .horizontal
        stageRow.alignment = compact ? .leading : .top
        inspectorWidthConstraint?.isActive = !compact
        inspectorScrollWidthConstraint?.isActive = !compact
        inspectorHeightConstraint?.isActive = !compact

        if compact {
            if compactPreviewWidthConstraint == nil {
                compactPreviewWidthConstraint = previewBox.widthAnchor.constraint(equalTo: stageRow.widthAnchor)
                compactInspectorWidthConstraint = inspectorScroll.widthAnchor.constraint(equalTo: stageRow.widthAnchor)
                compactInspectorDocumentWidthConstraint = inspector.widthAnchor.constraint(equalToConstant: 680)
                compactInspectorHeightConstraint = inspectorScroll.heightAnchor.constraint(equalToConstant: 150)
            }
            compactPreviewWidthConstraint?.isActive = true
            compactInspectorWidthConstraint?.isActive = true
            compactInspectorDocumentWidthConstraint?.constant = max(280, (proposedWidth ?? content.bounds.width) - 40)
            compactInspectorDocumentWidthConstraint?.isActive = true
            compactInspectorHeightConstraint?.isActive = true
            shortcutHint.isHidden = true
        } else {
            compactPreviewWidthConstraint?.isActive = false
            compactInspectorWidthConstraint?.isActive = false
            compactInspectorDocumentWidthConstraint?.isActive = false
            compactInspectorHeightConstraint?.isActive = false
            shortcutHint.isHidden = false
        }
        content.layoutSubtreeIfNeeded()
    }

    private func handleShortcut(_ event: NSEvent) -> Bool {
        guard !isEditingText(),
              event.modifierFlags.intersection([.command, .control, .option, .shift]).isEmpty else { return false }
        switch event.keyCode {
        case 49: // Space
            togglePlayback()
        case 123: // Left arrow
            seekRelative(by: -1)
        case 124: // Right arrow
            seekRelative(by: 1)
        case 115: // Home
            do { try seek(toFrame: 0) } catch { show(error) }
        case 119: // End
            do { try seek(toFrame: session.snapshot().frameCount - 1) } catch { show(error) }
        case 53: // Escape
            stopPreview()
        default:
            return false
        }
        return true
    }

    /// Used by the release evidence path and native regression to send the
    /// same AppKit key event the window handles for a keyboard user.
    func dispatchShortcut(characters: String, keyCode: UInt16) {
        guard let event = NSEvent.keyEvent(
            with: .keyDown,
            location: .zero,
            modifierFlags: [],
            timestamp: 0,
            windowNumber: window?.windowNumber ?? 0,
            context: nil,
            characters: characters,
            charactersIgnoringModifiers: characters,
            isARepeat: false,
            keyCode: keyCode
        ) else { return }
        window?.sendEvent(event)
    }

    private func isEditingText() -> Bool {
        guard let responder = window?.firstResponder else { return false }
        if let editor = responder as? NSTextView, editor.isFieldEditor { return true }
        return responder is NSTextField
    }

    private func seekRelative(by delta: Int64) {
        do {
            let snapshot = try session.snapshot()
            try seek(toFrame: min(max(snapshot.currentFrame + delta, 0), snapshot.frameCount - 1))
        } catch { show(error) }
    }

    private func seek(toFrame frame: Int64) throws {
        session.seek(toFrame: frame) { [weak self] completed in
            DispatchQueue.main.async {
                guard let self else { return }
                completed ? self.updateAfterTransportChange() : self.showError("The soundtrack could not seek to that frame.")
            }
        }
    }

    private func configureInspector() {
        inspector.translatesAutoresizingMaskIntoConstraints = false
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
        stopPreviewButton.target = self
        stopPreviewButton.action = #selector(stopPreview)
        stopPreviewButton.setAccessibilityLabel("Stop local preview")
        stopPreviewButton.toolTip = "Stops local soundtrack playback only. It does not cancel a provider job."
        restartPreviewButton.target = self
        restartPreviewButton.action = #selector(restartPreview)
        restartPreviewButton.setAccessibilityLabel("Restart local preview")
        restartPreviewButton.toolTip = "Restarts local soundtrack playback from the selected scene. It does not submit or cancel a provider job."
        saveSessionButton.target = self
        saveSessionButton.action = #selector(saveSession)
        saveSessionButton.setAccessibilityLabel("Save editor session")
        openSessionButton.target = self
        openSessionButton.action = #selector(openSavedSession)
        openSessionButton.setAccessibilityLabel("Open saved editor session")
        reloadStateButton.target = self
        reloadStateButton.action = #selector(reloadState)
        reloadStateButton.setAccessibilityLabel("Reload asset and job state")
        let hasSessionStore = sessionDocumentURL != nil
        saveSessionButton.isEnabled = hasSessionStore
        openSessionButton.isEnabled = hasSessionStore
        seekSlider.target = self
        seekSlider.action = #selector(seekChanged)
        seekSlider.setAccessibilityLabel("Seek soundtrack")
        timeLabel.setAccessibilityLabel("Soundtrack time")
        timeLabel.font = .monospacedDigitSystemFont(ofSize: 12, weight: .regular)
        timeLabel.textColor = .white
        let playback = NSStackView(views: [
            playPauseButton, stopPreviewButton, restartPreviewButton, timeLabel, seekSlider,
        ])
        playback.orientation = .horizontal
        playback.alignment = .centerY
        playback.spacing = 12
        let storage = NSStackView(views: [saveSessionButton, openSessionButton, reloadStateButton])
        storage.orientation = .horizontal
        storage.spacing = 12
        let transport = NSStackView(views: [playback, storage])
        transport.orientation = .vertical
        transport.alignment = .leading
        transport.spacing = 8
        playback.widthAnchor.constraint(equalTo: transport.widthAnchor).isActive = true
        seekSlider.widthAnchor.constraint(greaterThanOrEqualToConstant: 160).isActive = true
        seekSlider.setContentHuggingPriority(.defaultLow, for: .horizontal)
        return transport
    }

    private func populateSceneStrip() throws {
        let thumbnails = try session.sceneStrip()
        let snapshot = try session.snapshot()
        populateSceneStrip(thumbnails: thumbnails, snapshot: snapshot)
    }

    private func populateSceneStrip(thumbnails: [SceneEditorThumbnail], snapshot: SceneEditorSnapshot) {
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
                if observerToken == nil {
                    observerToken = try session.observeFrames { [weak self] frame in self?.apply(frame: frame) }
                }
                try session.play()
            }
            updateAfterTransportChange()
        } catch {
            show(error)
        }
    }

    /// This is intentionally local transport cancellation. The editor has no
    /// provider instance and therefore cannot send a provider cancellation
    /// request or claim a refund for an animation job.
    @objc private func stopPreview() {
        session.pause()
        if let observerToken {
            session.removeFrameObserver(observerToken)
            self.observerToken = nil
        }
        updateAfterTransportChange()
        statusLabel.stringValue = "Preview stopped locally; audio and frame callbacks stopped · provider jobs are untouched"
    }

    @objc private func restartPreview() {
        do {
            let selected = try session.snapshot().selectedSceneIndex
            let start = try session.compositionPlan().scenes[selected].timing.startFrame
            session.seek(toFrame: start) { [weak self] completed in
                DispatchQueue.main.async {
                    guard let self else { return }
                    guard completed else {
                        self.showError("The soundtrack could not restart the selected scene.")
                        return
                    }
                    do {
                        if self.observerToken == nil {
                            self.observerToken = try self.session.observeFrames { [weak self] frame in
                                self?.apply(frame: frame)
                            }
                        }
                        try self.session.play()
                        self.updateAfterTransportChange()
                        self.statusLabel.stringValue = "Preview restarted locally · provider jobs are untouched"
                    } catch {
                        self.show(error)
                    }
                }
            }
        } catch {
            show(error)
        }
    }

    @objc private func saveSession() {
        do {
            guard let sessionDocumentURL else { throw SceneEditorControlError.missingControl("session storage") }
            try SceneEditorDocumentStore.save(session.document(), to: sessionDocumentURL)
            statusLabel.textColor = NSColor(calibratedRed: 0.55, green: 0.84, blue: 0.75, alpha: 1)
            statusLabel.stringValue = "Editor session saved · source media and accepted output were not changed"
        } catch {
            show(error)
        }
    }

    /// Restores edits on the existing stage. A malformed or
    /// stale document leaves the existing playable editor and its saved file
    /// intact so the user can recover after fixing the external input.
    @objc private func openSavedSession() {
        do {
            guard let sessionDocumentURL else { throw SceneEditorControlError.missingControl("session storage") }
            let document = try SceneEditorDocumentStore.load(from: sessionDocumentURL)
            try session.restore(document) {
                // Resolve every throwing input/render operation before changing
                // any visible view; restore can roll back without partial UI.
                let thumbnails = try session.sceneStrip()
                let snapshot = try session.snapshot()
                let details = try session.inspector()
                let assets = try session.assetStates()
                let frame = try session.currentFrame()
                replaceSceneStrip(thumbnails: thumbnails, snapshot: snapshot)
                populateInspector(details: details, assets: assets)
                apply(frame: frame)
            }
            statusLabel.textColor = NSColor(calibratedRed: 0.55, green: 0.84, blue: 0.75, alpha: 1)
            statusLabel.stringValue = "Saved editor session reopened · pinned inputs revalidated"
        } catch {
            showError("Saved session was not opened. The current preview remains available. \(error.localizedDescription)")
        }
    }

    @objc private func reloadState() {
        do {
            try refreshInspector()
            statusLabel.stringValue = "Asset and persisted job state reloaded · no provider request was made"
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
            // Transport completion must not destroy an active field editor.
            // Rebuild only when selecting a different scene changes its facts.
            if try session.inspector() != displayedInspector { try refreshInspector() }
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
        let assets = try session.assetStates()
        populateInspector(details: details, assets: assets)
    }

    private func populateInspector(details: SceneEditorInspector, assets: [SceneEditorAssetState]) {
        displayedInspector = details
        while inspector.arrangedSubviews.count > 1, let last = inspector.arrangedSubviews.last {
            inspector.removeArrangedSubview(last)
            last.removeFromSuperview()
        }
        var facts = [
            ("Scene", details.label),
            ("Range", "\(format(seconds: details.startSeconds)) – \(format(seconds: details.endSeconds))"),
            ("Frames", "\(details.startFrame) – \(details.endFrame - 1)"),
            ("Interior", assetLabel(details.interior)),
            ("Window mask", assetLabel(details.windowMask)),
            ("Scenery layers", "\(details.parallaxLayerCount)"),
            ("Action", details.actionClip.map { assetLabel($0) } ?? "None"),
            ("Crop", "x \(details.crop.x), y \(details.crop.y), \(details.crop.width) × \(details.crop.height)"),
            ("Crossfade", details.crossfadeToNextFrames == 0 ? "None" : "\(details.crossfadeToNextFrames) frames"),
        ]
        facts.append(("Pinned assets", assets.map(assetStateLabel).joined(separator: "\n")))
        let differences = assets.flatMap { state in
            state.unresolvedIdentityDifferences.map { "\(assetLabel(state.identity)): \($0.field)" }
        }
        facts.append(("Identity review", differences.isEmpty ? "No unresolved recorded differences" : differences.joined(separator: "\n")))
        facts.append(("Animation jobs", jobStateLabel()))
        facts.append(("Recovery", "Correct a pinned input, then use Open saved session. Failed provider jobs remain ledger state; this editor cannot retry or cancel them."))
        for (name, value) in facts {
            let label = NSTextField(wrappingLabelWithString: "\(name)\n\(value)")
            label.font = .systemFont(ofSize: 12)
            label.textColor = NSColor(calibratedWhite: 0.82, alpha: 1)
            label.maximumNumberOfLines = 0
            inspector.addArrangedSubview(label)
        }
        addEditingControls(details)
        inspector.layoutSubtreeIfNeeded()
        inspector.setFrameSize(inspector.fittingSize)
    }

    /// These are deliberately the only editable values represented by the
    /// shared composition resolver. The window never offers a transform,
    /// effect, or timing control that preview/output could not consume.
    private func addEditingControls(_ details: SceneEditorInspector) {
        let divider = NSBox()
        divider.boxType = .separator
        inspector.addArrangedSubview(divider)

        let editingHeading = NSTextField(labelWithString: "Composition controls")
        editingHeading.font = .systemFont(ofSize: 13, weight: .semibold)
        editingHeading.textColor = .white
        inspector.addArrangedSubview(editingHeading)

        let cropHeading = NSTextField(labelWithString: "Crop (normalized 0–10000)")
        cropHeading.textColor = NSColor(calibratedWhite: 0.82, alpha: 1)
        inspector.addArrangedSubview(cropHeading)
        let cropValues = [details.crop.x, details.crop.y, details.crop.width, details.crop.height]
        let cropNames = ["Crop X", "Crop Y", "Crop width", "Crop height"]
        cropFields = zip(cropNames, cropValues).map { name, value in
            makeIntegerField(value: Int64(value), accessibilityLabel: name)
        }
        let cropColumns = zip(["X", "Y", "Width", "Height"], cropFields).map { name, field in
            let label = NSTextField(labelWithString: name)
            label.font = .systemFont(ofSize: 10)
            label.textColor = .white
            let column = NSStackView(views: [label, field])
            column.orientation = .vertical
            column.alignment = .leading
            column.spacing = 3
            return column
        }
        let cropRow = NSStackView(views: cropColumns)
        cropRow.orientation = .horizontal
        cropRow.spacing = 4
        inspector.addArrangedSubview(cropRow)
        inspector.addArrangedSubview(makeButton(title: "Apply crop", action: #selector(applyCrop), accessibilityLabel: "Apply crop"))

        let motionHeading = NSTextField(labelWithString: "Layer motion (pixels/frame)")
        motionHeading.textColor = NSColor(calibratedWhite: 0.82, alpha: 1)
        inspector.addArrangedSubview(motionHeading)
        motionFields = details.parallaxLayers.enumerated().map { index, layer in
            let field = makeIntegerField(value: layer.pixelsPerFrame, accessibilityLabel: "Layer \(index + 1) motion")
            let apply = makeButton(title: "Apply", action: #selector(applyMotion(_:)), accessibilityLabel: "Apply layer \(index + 1) motion")
            apply.tag = index
            let row = NSStackView(views: [NSTextField(labelWithString: "Layer \(index + 1)"), field, apply])
            row.orientation = .horizontal
            row.spacing = 5
            inspector.addArrangedSubview(row)
            return field
        }

        let transitionHeading = NSTextField(labelWithString: "Outgoing crossfade (frames)")
        transitionHeading.textColor = NSColor(calibratedWhite: 0.82, alpha: 1)
        inspector.addArrangedSubview(transitionHeading)
        let fade = makeIntegerField(value: details.crossfadeToNextFrames, accessibilityLabel: "Crossfade frames")
        crossfadeField = fade
        let fadeRow = NSStackView(views: [fade, makeButton(title: "Apply", action: #selector(applyCrossfade), accessibilityLabel: "Apply crossfade")])
        fadeRow.orientation = .horizontal
        fadeRow.spacing = 5
        inspector.addArrangedSubview(fadeRow)
    }

    private func makeIntegerField(value: Int64, accessibilityLabel: String) -> NSTextField {
        let field = NSTextField(string: String(value))
        field.alignment = .right
        field.font = .monospacedDigitSystemFont(ofSize: 11, weight: .regular)
        field.setAccessibilityLabel(accessibilityLabel)
        field.widthAnchor.constraint(equalToConstant: 54).isActive = true
        return field
    }

    private func makeButton(title: String, action: Selector, accessibilityLabel: String) -> NSButton {
        let button = NSButton(title: title, target: self, action: action)
        button.bezelStyle = .rounded
        button.controlSize = .small
        button.setAccessibilityLabel(accessibilityLabel)
        return button
    }

    @objc private func applyCrop() {
        do {
            guard cropFields.count == 4 else { throw SceneEditorControlError.missingControl("crop") }
            let values = try cropFields.map { try integerValue(of: $0) }
            guard values.allSatisfy({ $0 >= Int64(Int.min) && $0 <= Int64(Int.max) }) else {
                throw SceneEditorControlError.invalidInteger("crop")
            }
            try session.setCrop(SceneCrop(x: Int(values[0]), y: Int(values[1]), width: Int(values[2]), height: Int(values[3])))
            try refreshAfterEdit("Crop updated")
        } catch {
            show(error)
        }
    }

    @objc private func applyMotion(_ sender: NSButton) {
        do {
            guard motionFields.indices.contains(sender.tag) else { throw SceneEditorControlError.missingControl("motion") }
            try session.setMotion(layer: sender.tag, pixelsPerFrame: try integerValue(of: motionFields[sender.tag]))
            try refreshAfterEdit("Layer \(sender.tag + 1) motion updated")
        } catch {
            show(error)
        }
    }

    @objc private func applyCrossfade() {
        do {
            guard let crossfadeField else { throw SceneEditorControlError.missingControl("crossfade") }
            try session.setCrossfade(frames: try integerValue(of: crossfadeField))
            try refreshAfterEdit("Crossfade updated")
        } catch {
            show(error)
        }
    }

    private func integerValue(of field: NSTextField) throws -> Int64 {
        guard let value = Int64(field.stringValue.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            throw SceneEditorControlError.invalidInteger(field.accessibilityLabel() ?? "value")
        }
        return value
    }

    private func refreshAfterEdit(_ message: String) throws {
        try refreshSceneStrip()
        try refreshInspector()
        try refreshCurrentFrame()
        statusLabel.textColor = NSColor(calibratedRed: 0.55, green: 0.84, blue: 0.75, alpha: 1)
        statusLabel.stringValue = "\(message) · shared scene plan refreshed"
    }

    private func refreshSceneStrip() throws {
        let thumbnails = try session.sceneStrip()
        let snapshot = try session.snapshot()
        replaceSceneStrip(thumbnails: thumbnails, snapshot: snapshot)
    }

    private func replaceSceneStrip(thumbnails: [SceneEditorThumbnail], snapshot: SceneEditorSnapshot) {
        for view in sceneStrip.arrangedSubviews {
            sceneStrip.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        sceneButtons.removeAll()
        populateSceneStrip(thumbnails: thumbnails, snapshot: snapshot)
        sceneStrip.layoutSubtreeIfNeeded()
        sceneStrip.setFrameSize(sceneStrip.fittingSize)
    }

    /// Captures the actual plan-rendered preview image, allowing native
    /// validation to retain before/after evidence without a mock renderer.
    public func capturePreviewImage(to url: URL) throws {
        guard let image = previewImage.image,
              let tiff = image.tiffRepresentation,
              let representation = NSBitmapImageRep(data: tiff),
              let png = representation.representation(using: .png, properties: [:]) else {
            throw SceneEditorEvidenceError.captureFailed
        }
        try png.write(to: url, options: .withoutOverwriting)
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

    private func assetStateLabel(_ state: SceneEditorAssetState) -> String {
        let approval = state.approval
        let date = ISO8601DateFormatter().string(from: approval.decidedAt)
        return "\(assetLabel(state.identity)) · \(approval.state.rawValue) by \(approval.decidedBy) at \(date)"
    }

    private func jobStateLabel() -> String {
        guard let animationLedgerURL else { return "No persisted animation ledger selected" }
        do {
            let jobs = try AnimationJobStore.load(from: animationLedgerURL).jobs
            guard !jobs.isEmpty else { return "No persisted animation jobs" }
            return jobs.map { job in
                guard let attempt = job.latestAttempt else { return "\(job.jobID): no attempts" }
                let cost = attempt.actualCost ?? attempt.estimatedCost
                let costKind = attempt.actualCost == nil ? "estimated cost" : "actual cost"
                let failure = attempt.failure.map { " · failure: \($0)" } ?? ""
                let progress = (attempt.state == .submissionUncertain || attempt.state == .submitted || attempt.state == .submitting || attempt.state == .cancellationRequested) ? " · progress unknown" : ""
                return "\(job.jobID): \(attempt.state.rawValue) · \(costKind): \(cost.currency) \(cost.amountCents) cents\(progress)\(failure)"
            }.joined(separator: "\n")
        } catch {
            return "Unavailable: \(error.localizedDescription)"
        }
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

    private func layoutMeasurement(name: String, content: NSView) -> [String: Any] {
        func rect(_ view: NSView) -> [String: Double] {
            let value = view.convert(view.bounds, to: content)
            return ["x": value.origin.x, "y": value.origin.y, "width": value.width, "height": value.height]
        }
        return [
            "fixture": name,
            "content": rect(content),
            "preview": rect(previewImage),
            "inspector": rect(inspectorScroll),
            "sceneStrip": stripScroll.map { rect($0) } ?? [:],
            "transport": rect(seekSlider),
            "compactStackedLayout": stageRow?.orientation == .vertical,
        ]
    }

    private func verifyInspectorReachability() throws {
        guard let content = window?.contentView else { throw SceneEditorEvidenceError.windowNotVisible }
        content.layoutSubtreeIfNeeded()
        func controls(in view: NSView) -> [NSView] {
            let own = (view is NSButton || (view as? NSTextField)?.isEditable == true) ? [view] : []
            return own + view.subviews.flatMap { controls(in: $0) }
        }
        let items = controls(in: inspector)
        guard !items.isEmpty else { throw SceneEditorEvidenceError.captureFailed }
        for control in items {
            control.scrollToVisible(control.bounds)
            content.layoutSubtreeIfNeeded()
            let visible = control.visibleRect
            guard visible.width >= control.bounds.width - 1,
                  visible.height >= control.bounds.height - 1 else {
                throw SceneEditorEvidenceError.captureFailed
            }
        }
        if let heading = inspector.arrangedSubviews.first { heading.scrollToVisible(heading.bounds) }
    }

    private func captureWindow(to url: URL) throws {
        guard let content = window?.contentView else {
            throw SceneEditorEvidenceError.windowNotVisible
        }
        window?.displayIfNeeded()
        content.layoutSubtreeIfNeeded()
        for view in [inspectorScroll, sceneButtons[0], playPauseButton, seekSlider,
                     stopPreviewButton, restartPreviewButton, saveSessionButton, openSessionButton, reloadStateButton] {
            let rect = view.convert(view.bounds, to: content)
            guard content.bounds.contains(rect), rect.width > 0, rect.height > 0 else {
                throw SceneEditorEvidenceError.captureFailed
            }
        }
        guard seekSlider.bounds.width >= 160 else { throw SceneEditorEvidenceError.captureFailed }
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

private enum SceneEditorControlError: Error, LocalizedError {
    case missingControl(String)
    case invalidInteger(String)

    var errorDescription: String? {
        switch self {
        case .missingControl(let name):
            "The \(name) control is unavailable. Select a scene and try again."
        case .invalidInteger(let name):
            "\(name) must be a whole number. The previous valid scene edit remains active."
        }
    }
}
