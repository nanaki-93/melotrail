import AppKit
import Foundation
import MelotrailTABICompanion

@MainActor
private final class TABIEditorApplication: NSObject, NSApplicationDelegate {
    private let requestPath: String?
    private var loadingWindow: NSWindow?
    private var editorWindow: SceneEditorWindowController?
    private let evidenceDirectory: URL?

    override init() {
        let arguments = Array(CommandLine.arguments.dropFirst())
        requestPath = arguments.count == 1 ? arguments[0] : nil
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
            guard let requestPath else {
                throw EditorLaunchError.usage
            }
            let url = URL(fileURLWithPath: requestPath)
            guard (try url.resourceValues(forKeys: [.isRegularFileKey])).isRegularFile == true else {
                throw EditorLaunchError.notRegularFile(requestPath)
            }
            let request = try JSONDecoder().decode(SceneCompositionRequest.self, from: Data(contentsOf: url, options: [.mappedIfSafe]))
            let session = try SceneEditorSession(
                request: request,
                geometry: PreviewOutputGeometry(width: 1_280, height: 720)
            )
            let controller = try SceneEditorWindowController(session: session)
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

    private func showInputError(_ message: String) {
        loadingWindow?.title = "Cannot open TABI editor"
        loadingWindow?.setContentSize(NSSize(width: 520, height: 270))
        guard let content = loadingWindow?.contentView else { return }
        content.subviews.forEach { $0.removeFromSuperview() }
        let details = NSTextField(wrappingLabelWithString: "The companion could not open this composition request.\n\n\(message)\n\nUsage: melotrail-tabi-editor <composition-request.json>")
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
            "Provide exactly one composition-request.json path."
        case .notRegularFile(let path):
            "\(path) is not a regular local composition request file."
        case .invalidEvidenceDirectory:
            "The editor evidence destination must be an existing directory."
        case .captureFailed:
            "The input-error window could not be captured."
        }
    }
}
