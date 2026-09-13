import AppKit
import Foundation
import UniformTypeIdentifiers

/// Read-only intake before the existing asset-backed editor. No media or project is created on launch.
@MainActor
public final class ExportHandoffWindowController: NSWindowController, NSTextFieldDelegate {
    private let handoff: ExportHandoff
    private let openComposition: (SceneCompositionRequest) throws -> Void
    private let chooseSoundtrack: @MainActor () -> URL?
    private let chooseComposition: @MainActor () -> URL?
    private let chooseDestination: @MainActor () -> URL?
    private var soundtrack: FinishedSoundtrack?
    private var timing: SoundtrackTimingRequest?
    private let soundtrackLabel = NSTextField(wrappingLabelWithString: "Select the finished soundtrack bounced from Logic Pro.")
    private let status = NSTextField(wrappingLabelWithString: "The export supplies section timing. Your finished soundtrack supplies all sound.")
    private let leadIn = NSTextField(string: "0")
    private let tail = NSTextField(string: "0")
    private let confirm = NSButton(title: "Confirm timing", target: nil, action: nil)
    private let remainingTail = NSButton(title: "Use remaining duration as tail", target: nil, action: nil)
    private let save = NSButton(title: "Save timing request…", target: nil, action: nil)
    private let open = NSButton(title: "Open prepared composition…", target: nil, action: nil)

    public init(
        handoff: ExportHandoff,
        chooseSoundtrack: @escaping @MainActor () -> URL? = { ExportHandoffWindowController.chooseFile(soundtrack: true) },
        chooseComposition: @escaping @MainActor () -> URL? = { ExportHandoffWindowController.chooseFile(soundtrack: false) },
        chooseDestination: @escaping @MainActor () -> URL? = { ExportHandoffWindowController.chooseTimingDestination() },
        openComposition: @escaping (SceneCompositionRequest) throws -> Void
    ) throws {
        let manifest = try handoff.verify()
        self.handoff = handoff
        self.chooseSoundtrack = chooseSoundtrack
        self.chooseComposition = chooseComposition
        self.chooseDestination = chooseDestination
        self.openComposition = openComposition
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 640, height: 680),
                              styleMask: [.titled, .closable], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.title = "TABI · Finished soundtrack"
        window.appearance = NSAppearance(named: .darkAqua)
        super.init(window: window)
        window.contentView?.wantsLayer = true
        window.contentView?.layer?.backgroundColor = NSColor(calibratedRed: 0.043, green: 0.075, blue: 0.118, alpha: 1).cgColor
        let heading = NSTextField(wrappingLabelWithString: "Create a TABI video from your finished music")
        heading.font = .systemFont(ofSize: 20, weight: .semibold)
        let facts = NSTextField(wrappingLabelWithString: "\(manifest.sections.count) sections · \(String(format: "%.3f", try manifest.time(forTick: manifest.endTick).doubleValue)) s from your MIDI export")
        facts.toolTip = "Snapshot: \(manifest.snapshotID)\nManifest: \(handoff.manifestURL.path)"
        let select = NSButton(title: "Choose finished soundtrack…", target: self, action: #selector(selectSoundtrack))
        select.bezelStyle = .rounded
        select.setAccessibilityLabel("Choose finished soundtrack")
        soundtrackLabel.setAccessibilityLabel("Selected finished soundtrack")
        soundtrackLabel.maximumNumberOfLines = 2
        leadIn.setAccessibilityLabel("Bounce lead-in seconds")
        tail.setAccessibilityLabel("Bounce tail seconds")
        leadIn.delegate = self
        tail.delegate = self
        let alignment = NSStackView(views: [NSTextField(labelWithString: "Lead-in (s)"), leadIn,
                                           NSTextField(labelWithString: "Tail (s)"), tail])
        alignment.orientation = .horizontal
        alignment.spacing = 12
        leadIn.widthAnchor.constraint(equalToConstant: 90).isActive = true
        tail.widthAnchor.constraint(equalToConstant: 90).isActive = true
        configure(confirm, "Confirm bounce timing", #selector(confirmTiming))
        configure(remainingTail, "Use remaining soundtrack duration as tail", #selector(proposeRemainingTail))
        configure(save, "Save handoff timing request", #selector(saveTiming))
        configure(open, "Open handoff composition", #selector(openPreparedComposition))
        confirm.isEnabled = false
        remainingTail.isEnabled = false
        invalidateTiming()
        status.setAccessibilityLabel("Export handoff status")
        status.maximumNumberOfLines = 3
        let guidance = NSTextField(wrappingLabelWithString: "Enter seconds or an exact fraction (for example, 441/44100). Confirm the bounce’s lead-in and tail; music is never trimmed or stretched. Save timing for scene preparation, then open a composition using your approved assets.")
        let stack = NSStackView(views: [heading, facts, soundtrackLabel, select, alignment, remainingTail, confirm, status, guidance, save, open])
        stack.orientation = .vertical
        stack.alignment = .leading
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        window.contentView?.addSubview(stack)
        if let content = window.contentView {
            NSLayoutConstraint.activate([
                stack.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 24),
                stack.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -24),
                stack.topAnchor.constraint(equalTo: content.topAnchor, constant: 24),
                stack.bottomAnchor.constraint(lessThanOrEqualTo: content.bottomAnchor, constant: -24),
            ])
            for label in [heading, facts, soundtrackLabel, status, guidance] {
                label.widthAnchor.constraint(equalTo: stack.widthAnchor).isActive = true
            }
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }

    public func showHandoff() {
        window?.center()
        showWindow(nil)
        window?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    public func controlTextDidChange(_ notification: Notification) {
        invalidateTiming()
        status.stringValue = "Timing changed. Confirm the soundtrack alignment again."
    }

    private func configure(_ button: NSButton, _ label: String, _ action: Selector) {
        button.bezelStyle = .rounded
        button.target = self
        button.action = action
        button.setAccessibilityLabel(label)
    }

    private func invalidateTiming() {
        timing = nil
        save.isEnabled = false
        open.isEnabled = false
    }

    @objc private func selectSoundtrack() {
        guard let url = chooseSoundtrack() else { return }
        invalidateTiming()
        soundtrack = nil
        soundtrackLabel.stringValue = "No finished soundtrack selected."
        confirm.isEnabled = false
        remainingTail.isEnabled = false
        do {
            let selected = try handoff.selectSoundtrack(url)
            soundtrack = selected
            soundtrackLabel.stringValue = "\(url.lastPathComponent) · \(String(format: "%.6f", selected.duration.doubleValue)) s"
            soundtrackLabel.toolTip = url.path
            confirm.isEnabled = true
            remainingTail.isEnabled = true
            status.stringValue = "Set lead-in and tail, then confirm the exact soundtrack alignment."
        } catch { showError(error) }
    }

    @objc private func proposeRemainingTail() {
        invalidateTiming()
        do {
            guard let soundtrack else { return }
            let manifest = try handoff.verify()
            let verifiedSoundtrack = try FinishedSoundtrack.open(url: soundtrack.url, expectedSHA256: soundtrack.sha256)
            let used = try Self.seconds(leadIn.stringValue).adding(manifest.time(forTick: manifest.endTick))
            let remainder = try verifiedSoundtrack.duration.subtracting(used)
            tail.stringValue = "\(remainder.numerator)/\(remainder.denominator)"
            status.stringValue = "Remainder proposed as tail. Confirm only if it matches the ending of your bounce."
        } catch { showError(error) }
    }

    @objc private func confirmTiming() {
        invalidateTiming()
        do {
            guard let soundtrack else { return }
            let alignment = BounceAlignment(leadIn: try Self.seconds(leadIn.stringValue), tail: try Self.seconds(tail.stringValue))
            timing = try handoff.timingRequest(soundtrack: soundtrack, alignment: alignment)
            save.isEnabled = true
            open.isEnabled = true
            status.stringValue = "Timing confirmed · 30 fps · the complete soundtrack and section boundaries are preserved."
        } catch { showError(error) }
    }

    @objc private func saveTiming() {
        guard let timing, let url = chooseDestination() else { return }
        do {
            try handoff.saveTiming(timing, to: url)
            status.stringValue = "Timing request saved: \(url.lastPathComponent)"
        } catch { showError(error) }
    }

    @objc private func openPreparedComposition() {
        guard let timing, let url = chooseComposition() else { return }
        do {
            let request = try JSONDecoder().decode(SceneCompositionRequest.self, from: Data(contentsOf: url))
            try handoff.validateComposition(request, timing: timing)
            try openComposition(request)
        } catch { showError(error) }
    }

    private func showError(_ error: Error) {
        status.stringValue = error.localizedDescription
        status.toolTip = error.localizedDescription
    }

    private static func seconds(_ text: String) throws -> RationalTime {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        if trimmed.contains("/") {
            let parts = trimmed.split(separator: "/", omittingEmptySubsequences: false)
            guard parts.count == 2, let numerator = Int64(parts[0]), let denominator = Int64(parts[1]) else {
                throw SoundtrackSceneTimingError.invalidTime("enter a fraction such as 441/44100 seconds")
            }
            return try boundedSeconds(RationalTime(numerator, denominator))
        }
        let parts = trimmed.split(separator: ".", omittingEmptySubsequences: false)
        guard (1...2).contains(parts.count), !parts[0].isEmpty,
              parts.allSatisfy({ $0.allSatisfy({ $0 >= "0" && $0 <= "9" }) }),
              parts.count == 1 || (1...6).contains(parts[1].count),
              let whole = Int64(parts[0]), whole <= 86_400 else {
            throw SoundtrackSceneTimingError.invalidTime("enter seconds from 0 to 86400, with at most six decimal places")
        }
        let fraction = parts.count == 2 ? String(parts[1]) : ""
        let denominator = (0..<fraction.count).reduce(Int64(1)) { value, _ in value * 10 }
        return try boundedSeconds(RationalTime(whole * denominator + (Int64(fraction) ?? 0), denominator))
    }

    private static func boundedSeconds(_ value: RationalTime) throws -> RationalTime {
        guard value <= (try RationalTime(86_400)) else {
            throw SoundtrackSceneTimingError.invalidTime("lead-in and tail must be at most 86400 seconds")
        }
        return value
    }

    public static func chooseFile(soundtrack: Bool) -> URL? {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = soundtrack ? [.audio, .movie] : [.json]
        panel.message = soundtrack ? "Choose the finished Logic Pro soundtrack." : "Choose a prepared composition using this export and soundtrack."
        return panel.runModal() == .OK ? panel.url : nil
    }

    public static func chooseTimingDestination() -> URL? {
        let panel = NSSavePanel()
        panel.allowedContentTypes = [.json]
        panel.nameFieldStringValue = "soundtrack-timing.json"
        panel.message = "Save a new timing request in companion storage, outside your MIDI project."
        return panel.runModal() == .OK ? panel.url : nil
    }
}
