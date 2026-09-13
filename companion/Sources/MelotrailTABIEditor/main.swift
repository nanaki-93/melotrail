import AppKit
import Foundation
import MelotrailTABICompanion

@MainActor
private final class TABIEditorApplication: NSObject, NSApplicationDelegate {
    private let requestPath: String?
    private let animationLedgerPath: String?
    private let handoffArguments: [String]?
    private var loadingWindow: NSWindow?
    private var editorWindow: SceneEditorWindowController?
    private var handoffWindow: ExportHandoffWindowController?
    private let evidenceDirectory: URL?

    override init() {
        let arguments = Array(CommandLine.arguments.dropFirst())
        requestPath = (arguments.count == 1 || arguments.count == 2) ? arguments[0] : nil
        animationLedgerPath = arguments.count == 2 ? arguments[1] : nil
        handoffArguments = arguments.first == "--midi-export" ? arguments : nil
        evidenceDirectory = ProcessInfo.processInfo.environment["MELOTRAIL_TABI_EDITOR_EVIDENCE_DIR"]
            .map { URL(fileURLWithPath: $0, isDirectory: true) }
        super.init()
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        showLoadingWindow()
        DispatchQueue.main.async { [weak self] in self?.openRequest() }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }

    private func showLoadingWindow() {
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 460, height: 150),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )
        // Swift owns this window; AppKit must not release it again on close.
        window.isReleasedWhenClosed = false
        window.title = "Melotrail TABI"
        let label = NSTextField(wrappingLabelWithString: "Loading composition request…")
        label.font = .systemFont(ofSize: 14)
        label.translatesAutoresizingMaskIntoConstraints = false
        window.contentView?.addSubview(label)
        if let content = window.contentView {
            NSLayoutConstraint.activate([
                label.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 24),
                label.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -24),
                label.centerYAnchor.constraint(equalTo: content.centerYAnchor),
            ])
        }
        loadingWindow = window
        window.center()
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    private func openRequest() {
        do {
            if let handoffArguments {
                let handoff = try ExportHandoff(arguments: handoffArguments)
                let controller = try ExportHandoffWindowController(handoff: handoff) { [weak self] request in
                    guard let self else { return }
                    self.editorWindow = try self.makeEditor(request)
                    self.editorWindow?.showEditor()
                    self.handoffWindow?.close()
                    self.handoffWindow = nil
                }
                handoffWindow = controller
                controller.showHandoff()
                loadingWindow?.close()
                loadingWindow = nil
                if let evidenceDirectory {
                    DispatchQueue.main.async { [weak self] in
                        self?.recordHandoffEvidence(handoff, in: evidenceDirectory)
                    }
                }
                return
            }
            guard let requestPath else {
                throw EditorLaunchError.usage
            }
            let url = URL(fileURLWithPath: requestPath)
            guard (try url.resourceValues(forKeys: [.isRegularFileKey])).isRegularFile == true else {
                throw EditorLaunchError.notRegularFile(requestPath)
            }
            let request = try JSONDecoder().decode(SceneCompositionRequest.self, from: Data(contentsOf: url, options: [.mappedIfSafe]))
            let controller = try makeEditor(request)
            editorWindow = controller
            controller.showEditor()
            loadingWindow?.close()
            loadingWindow = nil
            if let evidenceDirectory {
                controller.recordValidationEvidence(in: evidenceDirectory, compositionRequestURL: url) { result in
                    switch result {
                    case .success(let reportURL):
                        print("release-editor-observations=\(reportURL.path)")
                        NSApp.terminate(nil)
                    case .failure(let error):
                        writeStandardError("release-editor-evidence=FAIL: \(error.localizedDescription)")
                        exit(EXIT_FAILURE)
                    }
                }
            }
        } catch {
            showInputError(error.localizedDescription)
        }
    }

    private func makeEditor(_ request: SceneCompositionRequest) throws -> SceneEditorWindowController {
        try SceneEditorWindowController(
            session: SceneEditorSession(request: request, geometry: PreviewOutputGeometry(width: 1_280, height: 720)),
            sessionDocumentURL: SceneEditorDocumentStore.defaultURL(for: request),
            animationLedgerURL: animationLedgerPath.map { URL(fileURLWithPath: $0) }
        )
    }

    private func showInputError(_ message: String) {
        loadingWindow?.title = "Cannot open TABI editor"
        loadingWindow?.setContentSize(NSSize(width: 520, height: 270))
        guard let content = loadingWindow?.contentView else { return }
        content.subviews.forEach { $0.removeFromSuperview() }
        let subject = handoffArguments == nil ? "composition request" : "MIDI export snapshot"
        let usage = handoffArguments == nil ? "<composition-request.json> [animation-jobs.json]" : "--midi-export <manifest.json> <SHA-256> <snapshot ID>"
        let details = NSTextField(wrappingLabelWithString: "The companion could not open this \(subject).\n\n\(message)\n\nUsage: melotrail-tabi-editor \(usage)")
        details.font = .systemFont(ofSize: 13)
        details.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(details)
        NSLayoutConstraint.activate([
            details.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 24),
            details.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -24),
            details.centerYAnchor.constraint(equalTo: content.centerYAnchor),
        ])
        if let evidenceDirectory {
            DispatchQueue.main.async { [weak self] in
                self?.recordInputErrorEvidence(message, in: evidenceDirectory)
            }
        }
    }

    private func recordHandoffEvidence(_ handoff: ExportHandoff, in outputDirectory: URL) {
        do {
            _ = try handoff.verify()
            guard (try outputDirectory.resourceValues(forKeys: [.isDirectoryKey])).isDirectory == true,
                  let content = handoffWindow?.window?.contentView else { throw EditorLaunchError.invalidEvidenceDirectory }
            handoffWindow?.window?.displayIfNeeded()
            content.layoutSubtreeIfNeeded()
            func control(_ label: String, in view: NSView) -> NSControl? {
                if let control = view as? NSControl, control.accessibilityLabel() == label { return control }
                for child in view.subviews {
                    if let match = control(label, in: child) { return match }
                }
                return nil
            }
            guard let choose = control("Choose finished soundtrack", in: content),
                  let confirm = control("Confirm bounce timing", in: content),
                  let open = control("Open handoff composition", in: content),
                  choose.isEnabled && !confirm.isEnabled && !open.isEnabled else { throw EditorLaunchError.captureFailed }
            guard let bitmap = content.bitmapImageRepForCachingDisplay(in: content.bounds) else { throw EditorLaunchError.captureFailed }
            content.cacheDisplay(in: content.bounds, to: bitmap)
            guard let png = bitmap.representation(using: .png, properties: [:]) else { throw EditorLaunchError.captureFailed }
            let capture = outputDirectory.appendingPathComponent("handoff-window.png")
            try png.write(to: capture, options: .withoutOverwriting)
            let report: [String: Any] = [
                "schemaVersion": 1, "releaseExecutableLaunched": true,
                "executablePath": CommandLine.arguments[0], "snapshotId": handoff.snapshotID,
                "manifestSHA256": handoff.manifestSHA256,
                "finishedSoundtrackSelectionRequired": choose.isEnabled && !confirm.isEnabled && !open.isEnabled,
                "capturePath": capture.path,
            ]
            try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
                .write(to: outputDirectory.appendingPathComponent("handoff-observations.json"), options: .withoutOverwriting)
            NSApp.terminate(nil)
        } catch {
            writeStandardError("release-handoff-evidence=FAIL: \(error.localizedDescription)")
            exit(EXIT_FAILURE)
        }
    }

    private func recordInputErrorEvidence(_ message: String, in outputDirectory: URL) {
        do {
            guard (try outputDirectory.resourceValues(forKeys: [.isDirectoryKey])).isDirectory == true,
                  let content = loadingWindow?.contentView else {
                throw EditorLaunchError.invalidEvidenceDirectory
            }
            loadingWindow?.displayIfNeeded()
            guard let representation = content.bitmapImageRepForCachingDisplay(in: content.bounds) else {
                throw EditorLaunchError.captureFailed
            }
            content.cacheDisplay(in: content.bounds, to: representation)
            guard let png = representation.representation(using: .png, properties: [:]) else {
                throw EditorLaunchError.captureFailed
            }
            let captureURL = outputDirectory.appendingPathComponent("input-error-window.png")
            try png.write(to: captureURL, options: .withoutOverwriting)
            let report: [String: Any] = [
                "schemaVersion": 1,
                "releaseExecutableLaunched": true,
                "executablePath": CommandLine.arguments[0],
                "compositionRequestPath": requestPath ?? "",
                "inputErrorVisible": true,
                "message": message,
                "capturePath": captureURL.path,
            ]
            let reportURL = outputDirectory.appendingPathComponent("input-error-observations.json")
            try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
                .write(to: reportURL, options: .withoutOverwriting)
            print("release-editor-input-error=\(reportURL.path)")
            NSApp.terminate(nil)
        } catch {
            writeStandardError("release-editor-input-evidence=FAIL: \(error.localizedDescription)")
            exit(EXIT_FAILURE)
        }
    }
}

@main
@MainActor
private enum TABIEditorMain {
    static func main() {
        if Array(CommandLine.arguments.dropFirst()) == ["--capabilities"] {
            print(ExportHandoff.capability)
            return
        }
        let application = NSApplication.shared
        let delegate = TABIEditorApplication()
        application.delegate = delegate
        withExtendedLifetime(delegate) { application.run() }
    }
}

private func writeStandardError(_ message: String) {
    FileHandle.standardError.write(Data((message + "\n").utf8))
}

private enum EditorLaunchError: LocalizedError {
    case usage
    case notRegularFile(String)
    case invalidEvidenceDirectory
    case captureFailed

    var errorDescription: String? {
        switch self {
        case .usage:
            "Provide a composition-request.json path and, optionally, a persisted animation-jobs.json ledger."
        case .notRegularFile(let path):
            "\(path) is not a regular local composition request file."
        case .invalidEvidenceDirectory:
            "The editor evidence destination must be an existing directory."
        case .captureFailed:
            "The input-error window could not be captured."
        }
    }
}
