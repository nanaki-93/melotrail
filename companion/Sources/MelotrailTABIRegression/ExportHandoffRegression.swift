import AppKit
import Foundation
import MelotrailTABICompanion

/// Exercises the intake's real controls and the same read-only contract used by the release caller.
func runExportHandoffRegression(manifestURL: URL, soundtrackURL: URL, composition: SceneCompositionRequest) throws {
    let manager = FileManager.default
    let root = manager.temporaryDirectory.appendingPathComponent("tabi-handoff-\(UUID().uuidString)", isDirectory: true)
    try manager.createDirectory(at: root, withIntermediateDirectories: false)
    defer { try? manager.removeItem(at: root) }
    let project = root.appendingPathComponent("MIDI 日本語 project", isDirectory: true)
    let snapshot = project.appendingPathComponent("exports/current", isDirectory: true)
    try manager.createDirectory(at: snapshot, withIntermediateDirectories: true)
    let manifest = snapshot.appendingPathComponent("manifest.json")
    try manager.copyItem(at: manifestURL, to: manifest)
    try Data("protected current MIDI project".utf8).write(to: project.appendingPathComponent("project.json"))
    try Data("protected source MIDI".utf8).write(to: project.appendingPathComponent("source.mid"))
    try Data("accepted candidate".utf8).write(to: project.appendingPathComponent("candidate.mid"))
    let audio = root.appendingPathComponent("finished soundtrack 日本語.mov")
    try manager.copyItem(at: soundtrackURL, to: audio)
    let manifestBytes = try Data(contentsOf: manifest)
    let audioBytes = try Data(contentsOf: audio)
    let digest = try AssetDigest.sha256(of: manifest)
    let verified = try VerifiedMidiTimingManifest.load(url: manifest, expectedSHA256: digest)
    let arguments = ["--midi-export", manifest.path, digest, verified.snapshotID]
    let handoff = try ExportHandoff(arguments: arguments)
    func projectBytes() throws -> [String: Data] {
        let entries = manager.enumerator(at: project, includingPropertiesForKeys: [.isRegularFileKey])!
        var result: [String: Data] = [:]
        for case let url as URL in entries where (try url.resourceValues(forKeys: [.isRegularFileKey])).isRegularFile == true {
            result[url.path] = try Data(contentsOf: url)
        }
        return result
    }
    let before = try projectBytes()
    let selected = try handoff.selectSoundtrack(audio)
    let timing = try handoff.timingRequest(soundtrack: selected, alignment: composition.timing.alignment)
    let plan = try timing.resolve()
    let expectedPlan = try composition.timing.resolve()
    require(plan == expectedPlan, "handoff must reuse the exact soundtrack/section planner")
    let matched = SceneCompositionRequest(timing: timing, assetLibraryPath: composition.assetLibraryPath,
        assetManifestPath: composition.assetManifestPath, sceneVersion: composition.sceneVersion,
        identityVersion: composition.identityVersion, scenes: composition.scenes)
    try handoff.validateComposition(matched, timing: timing)
    do {
        try handoff.validateComposition(composition, timing: timing)
        require(false, "a different manifest/soundtrack path must not replace the selected handoff")
    } catch ExportHandoffError.wrongComposition { }
    do {
        try handoff.validateComposition(composition, timing: composition.timing)
        require(false, "a matching pair of unrelated timing inputs cannot bypass the selected export identity")
    } catch ExportHandoffError.wrongComposition { }
    for invalid in [Array(arguments.dropLast()), arguments + ["extra"], ["--midi-export", "relative.json", digest, "id"]] {
        do { _ = try ExportHandoff(arguments: invalid); require(false, "malformed launch arguments must reject") }
        catch ExportHandoffError.invalidRequest { }
    }
    do {
        _ = try ExportHandoff(arguments: ["--midi-export", manifest.path, digest, "different-snapshot"]).verify()
        require(false, "handoff must check snapshot identity as well as digest")
    } catch ExportHandoffError.wrongSnapshot { }
    let wrongAudio = root.appendingPathComponent("not-a-soundtrack.mid")
    try Data("MIDI is not finished sound".utf8).write(to: wrongAudio)
    do { _ = try handoff.selectSoundtrack(wrongAudio); require(false, "MIDI cannot supply finished sound") }
    catch SoundtrackSceneTimingError.invalidSoundtrack { }
    do {
        _ = try handoff.timingRequest(soundtrack: selected, alignment: BounceAlignment(leadIn: .zero, tail: .zero))
        require(false, "bounce mismatch must require explicit timing correction")
    } catch SoundtrackSceneTimingError.invalidAlignment { }
    let output = root.appendingPathComponent("new timing.json")
    try handoff.saveTiming(timing, to: output)
    let outputBytes = try Data(contentsOf: output)
    let reopened = try JSONDecoder().decode(SoundtrackTimingRequest.self, from: outputBytes)
    require(reopened == timing, "saved timing must reopen without reinterpretation")
    do { try handoff.saveTiming(timing, to: output); require(false, "timing publication must not overwrite") }
    catch {
        let preserved = try Data(contentsOf: output)
        require(preserved == outputBytes, "collision must preserve the existing request")
    }
    let alias = root.appendingPathComponent("project alias", isDirectory: true)
    try manager.createSymbolicLink(at: alias, withDestinationURL: project)
    for destination in [project.appendingPathComponent("new.json"), snapshot.appendingPathComponent("new.json"),
                        alias.appendingPathComponent("new-folder/timing.json"), manifest] {
        do { try handoff.saveTiming(timing, to: destination); require(false, "handoff cannot write any MIDI project descendant") }
        catch ExportHandoffError.unsafeDestination { }
    }
    require(!manager.fileExists(atPath: project.appendingPathComponent("new-folder").path), "a rejected destination must not create MIDI project directories")
    try (manifestBytes + Data(" ".utf8)).write(to: manifest)
    do { _ = try handoff.verify(); require(false, "a stale manifest must reject") }
    catch SoundtrackSceneTimingError.manifestDigestMismatch { }
    try manifestBytes.write(to: manifest)
    try (audioBytes + Data("changed".utf8)).write(to: audio)
    do {
        _ = try handoff.timingRequest(soundtrack: selected, alignment: timing.alignment)
        require(false, "changed soundtrack bytes must reject after selection")
    } catch SoundtrackSceneTimingError.soundtrackDigestMismatch { }
    try audioBytes.write(to: audio)
    let requestURL = root.appendingPathComponent("composition.json")
    try JSONEncoder().encode(matched).write(to: requestURL)
    let nativeTiming = root.appendingPathComponent("native timing.json")
    try MainActor.assumeIsolated {
        let app = NSApplication.shared
        app.setActivationPolicy(.regular)
        var opened = 0
        var selectedURL: URL? = nil
        let previousAppearance = NSApp.appearance
        NSApp.appearance = NSAppearance(named: .aqua)
        defer { NSApp.appearance = previousAppearance }
        let controller = try ExportHandoffWindowController(
            handoff: handoff, chooseSoundtrack: { selectedURL }, chooseComposition: { requestURL },
            chooseDestination: { nativeTiming }
        ) { request in
            let session = try SceneEditorSession(request: request, geometry: PreviewOutputGeometry(width: 20, height: 30))
            defer { session.close() }
            let document = try session.document()
            do {
                try SceneEditorDocumentStore.save(document, to: alias.appendingPathComponent("session.scene-editor.json"))
                require(false, "editor session storage must not enter a MIDI project through an alias")
            } catch SceneEditorDocumentError.unsafeSessionPath { }
            opened += 1
        }
        controller.showHandoff()
        defer { controller.close() }
        require(controller.window?.effectiveAppearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua,
                "dark handoff surface must retain contrasting dynamic labels on an Aqua host")
        guard let content = controller.window?.contentView,
              let choose = findControl(withAccessibilityLabel: "Choose finished soundtrack", in: content) as? NSButton,
              let confirm = findControl(withAccessibilityLabel: "Confirm bounce timing", in: content) as? NSButton,
              let remaining = findControl(withAccessibilityLabel: "Use remaining soundtrack duration as tail", in: content) as? NSButton,
              let save = findControl(withAccessibilityLabel: "Save handoff timing request", in: content) as? NSButton,
              let open = findControl(withAccessibilityLabel: "Open handoff composition", in: content) as? NSButton,
              let lead = findControl(withAccessibilityLabel: "Bounce lead-in seconds", in: content) as? NSTextField,
              let tail = findControl(withAccessibilityLabel: "Bounce tail seconds", in: content) as? NSTextField,
              let status = findControl(withAccessibilityLabel: "Export handoff status", in: content) as? NSTextField else {
            throw ExportHandoffError.invalidRequest
        }
        require(!confirm.isEnabled && !open.isEnabled, "soundtrack is required before confirming or opening")
        choose.performClick(nil)
        require(!confirm.isEnabled && !open.isEnabled, "cancelling soundtrack selection cannot admit a handoff")
        selectedURL = audio
        choose.performClick(nil)
        confirm.performClick(nil)
        require(!open.isEnabled && status.stringValue.contains("do not equal"), "zero alignment cannot silently absorb the tail")
        lead.stringValue = "4410/44100"
        tail.stringValue = "1/0"
        confirm.performClick(nil)
        require(!open.isEnabled, "zero-denominator timing cannot be confirmed")
        remaining.performClick(nil)
        require(tail.stringValue == "1/10" && !open.isEnabled, "the exact remaining tail is only a proposal until confirmation")
        confirm.performClick(nil)
        require(open.isEnabled && save.isEnabled, "confirmed matching alignment must unlock preparation")
        save.performClick(nil)
        // Keep this UI check's FileManager on the main actor; the outer instance
        // still owns nonisolated fixture verification and deferred cleanup.
        let nativeTimingExists = FileManager().fileExists(atPath: nativeTiming.path)
        require(nativeTimingExists, "the real save action must publish a new timing request")
        open.performClick(nil)
        require(opened == 1, "the real open action must reach the existing editor session")
        // A file replacement after confirmation is detected again before opening.
        try (manifestBytes + Data(" ".utf8)).write(to: manifest)
        open.performClick(nil)
        require(opened == 1 && status.stringValue.contains("no longer match"), "stale inputs must keep the intake recoverable")
        try manifestBytes.write(to: manifest)
        confirm.performClick(nil)
        controller.controlTextDidChange(Notification(name: NSControl.textDidChangeNotification, object: lead))
        require(!save.isEnabled && !open.isEnabled && status.stringValue.contains("Timing changed"), "editing alignment must invalidate its confirmation")
        lead.stringValue = "1e-1"
        confirm.performClick(nil)
        require(!open.isEnabled, "alignment fields must reject non-decimal input")
        lead.stringValue = "0.1"
        confirm.performClick(nil)
        content.layoutSubtreeIfNeeded()
        for control in [choose, remaining, confirm, save, open, lead, tail] as [NSView] {
            let rect = control.convert(control.bounds, to: content)
            require(content.bounds.contains(rect) && rect.width > 0 && rect.height > 0, "handoff controls must remain visible")
        }
        if let fixturePath = ProcessInfo.processInfo.environment["MELOTRAIL_TABI_EDITOR_FIXTURE_DIR"] {
            let capture = URL(fileURLWithPath: fixturePath).deletingLastPathComponent().appendingPathComponent("export-handoff.png")
            controller.window?.displayIfNeeded()
            guard let bitmap = content.bitmapImageRepForCachingDisplay(in: content.bounds) else { throw ExportHandoffError.invalidRequest }
            content.cacheDisplay(in: content.bounds, to: bitmap)
            guard let png = bitmap.representation(using: .png, properties: [:]) else { throw ExportHandoffError.invalidRequest }
            try png.write(to: capture, options: .withoutOverwriting)
            require((bitmap.colorAt(x: 2, y: 2)?.alphaComponent ?? 0) > 0.99,
                    "handoff capture must include an opaque window background")
            // Sample inside each enabled button, away from its border: blank native
            // bezels used to pass geometry checks while losing every label in captures.
            let scaleX = CGFloat(bitmap.pixelsWide) / content.bounds.width
            let scaleY = CGFloat(bitmap.pixelsHigh) / content.bounds.height
            for button in [choose, remaining, confirm, save, open] {
                let rect = button.convert(button.bounds, to: content).insetBy(dx: 12, dy: 5)
                var darkest: CGFloat = 1
                var lightest: CGFloat = 0
                for x in Int(rect.minX * scaleX)..<Int(rect.maxX * scaleX) {
                    for y in Int((content.bounds.height - rect.maxY) * scaleY)..<Int((content.bounds.height - rect.minY) * scaleY) {
                        guard let color = bitmap.colorAt(x: x, y: y)?.usingColorSpace(.deviceRGB) else { throw ExportHandoffError.invalidRequest }
                        let luminance = (color.redComponent + color.greenComponent + color.blueComponent) / 3
                        darkest = min(darkest, luminance)
                        lightest = max(lightest, luminance)
                    }
                }
                require(lightest - darkest > 0.3, "handoff button must render a visible label: \(button.title)")
            }
            print("export-handoff-capture=\(capture.path)")
        }
    }
    let after = try projectBytes()
    let finalAudio = try Data(contentsOf: audio)
    require(after == before, "handoff success, failure, recovery and editor opening must preserve every MIDI project byte")
    require(finalAudio == audioBytes, "handoff must preserve separately supplied soundtrack bytes")
    print("export-handoff-regression=PASS")
}
