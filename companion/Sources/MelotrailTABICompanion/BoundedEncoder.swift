import Darwin
import Foundation

/// A process boundary for companion-only encoders. It deliberately has no MIDI,
/// scene, or audio-production dependency; V06 supplies the actual encoder and
/// media validation around this boundary.
public enum BoundedEncoderError: Error, LocalizedError, Sendable {
    case unsafeInvocation(String)
    case invalidInput(String)
    case insufficientDiskSpace
    case launchFailed(String)
    case timedOut
    case cancelled
    case outputTooLarge
    case missingStagedOutput
    case processFailed(status: Int32, diagnostic: String)
    case publishFailed(String)

    public var errorDescription: String? {
        switch self {
        case .unsafeInvocation(let message), .invalidInput(let message), .launchFailed(let message), .publishFailed(let message):
            return message
        case .insufficientDiskSpace:
            return "The selected companion output volume does not have the configured free-space reserve."
        case .timedOut:
            return "The owned encoder process exceeded its time limit."
        case .cancelled:
            return "The owned encoder process was cancelled."
        case .outputTooLarge:
            return "The encoder exceeded this job's staged-output disk limit."
        case .missingStagedOutput:
            return "The encoder finished without creating its required staged output."
        case .processFailed(let status, let diagnostic):
            return "The encoder exited with status \(status). \(diagnostic)"
        }
    }
}

public struct EncoderLimits: Sendable, Equatable {
    public let maximumInputBytes: Int64
    public let maximumOutputBytes: Int64
    public let minimumAvailableBytes: Int64
    public let timeout: TimeInterval
    public let maximumDiagnosticBytes: Int

    public init(
        maximumInputBytes: Int64 = 4 * 1024 * 1024 * 1024,
        maximumOutputBytes: Int64 = 8 * 1024 * 1024 * 1024,
        minimumAvailableBytes: Int64 = 64 * 1024 * 1024,
        timeout: TimeInterval = 60 * 60,
        maximumDiagnosticBytes: Int = 16 * 1024
    ) {
        self.maximumInputBytes = maximumInputBytes
        self.maximumOutputBytes = maximumOutputBytes
        self.minimumAvailableBytes = minimumAvailableBytes
        self.timeout = timeout
        self.maximumDiagnosticBytes = maximumDiagnosticBytes
    }
}

public enum EncoderArgument: Sendable, Equatable {
    /// A literal is passed directly as one argv element, never interpolated into a shell command.
    case literal(String)
    case input(Int)
    case stagedOutput
}

public struct EncoderInvocation: Sendable, Equatable {
    public let executableURL: URL
    public let arguments: [EncoderArgument]

    public init(executableURL: URL, arguments: [EncoderArgument]) {
        self.executableURL = executableURL
        self.arguments = arguments
    }
}

public struct EncoderProgress: Sendable, Equatable {
    /// A value emitted by the encoder itself (for example, a parsed `progress=0.5` line).
    public let fraction: Double
    public let line: String
}

public final class EncoderCancellation: @unchecked Sendable {
    private let lock = NSLock()
    private var cancelled = false
    public init() {}

    public func cancel() {
        lock.lock()
        cancelled = true
        lock.unlock()
    }

    fileprivate var isCancelled: Bool {
        lock.lock()
        defer { lock.unlock() }
        return cancelled
    }
}

public struct PublishedEncoderOutput: Sendable, Equatable {
    public let outputURL: URL
    public let progress: [EncoderProgress]
}

/// Starts a unique, companion-owned staging directory below the selected output
/// directory. Only this directory is ever removed on failure or cancellation.
public final class OwnedOutputStager {
    public let outputDirectory: URL
    public let stagedOutputURL: URL
    private let jobDirectory: URL
    private let desiredName: String
    private var finished = false

    public init(outputDirectory: URL, outputFileName: String) throws {
        guard Self.isSafeFileName(outputFileName) else {
            throw BoundedEncoderError.unsafeInvocation("Output names must be a single non-empty filename.")
        }
        self.outputDirectory = outputDirectory.standardizedFileURL.resolvingSymlinksInPath()
        self.desiredName = outputFileName

        try FileManager.default.createDirectory(at: self.outputDirectory, withIntermediateDirectories: true)
        try Self.requireRealDirectory(self.outputDirectory, description: "output directory")
        let stagingParent = self.outputDirectory.appendingPathComponent(".melotrail-tabi-staging", isDirectory: true)
        try FileManager.default.createDirectory(at: stagingParent, withIntermediateDirectories: true)
        try Self.requireRealDirectory(stagingParent, description: "staging directory")

        let candidate = stagingParent.appendingPathComponent("job-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: candidate, withIntermediateDirectories: false)
        self.jobDirectory = candidate
        self.stagedOutputURL = candidate.appendingPathComponent("encoded-\(outputFileName)")
    }

    deinit { cleanup() }

    fileprivate func cleanup() {
        guard !finished else { return }
        finished = true
        // This is a generated UUID path below our own staging parent, never a caller path.
        try? FileManager.default.removeItem(at: jobDirectory)
    }

    fileprivate func publish() throws -> URL {
        guard Self.isRegularFile(stagedOutputURL), try stagedSize() > 0 else { throw BoundedEncoderError.missingStagedOutput }
        let extensionPart = (desiredName as NSString).pathExtension
        let stem = (desiredName as NSString).deletingPathExtension
        for collision in 0..<10_000 {
            let suffix = collision == 0 ? "" : " (\(collision + 1))"
            let name = extensionPart.isEmpty ? "\(stem)\(suffix)" : "\(stem)\(suffix).\(extensionPart)"
            let destination = outputDirectory.appendingPathComponent(name)
            if link(stagedOutputURL.path, destination.path) == 0 {
                // The link is the publication point. Cleanup cannot make this completed,
                // collision-safe output become a reported failure.
                try? FileManager.default.removeItem(at: stagedOutputURL)
                try? FileManager.default.removeItem(at: jobDirectory)
                // If cleanup was transiently blocked, deinit retries only this UUID job path.
                finished = !FileManager.default.fileExists(atPath: jobDirectory.path)
                return destination
            }
            if errno == EEXIST { continue }
            throw BoundedEncoderError.publishFailed("Cannot publish a new output: \(String(cString: strerror(errno))).")
        }
        throw BoundedEncoderError.publishFailed("Could not claim a new output name after repeated collisions.")
    }

    fileprivate func stagedSize() throws -> Int64 {
        guard FileManager.default.fileExists(atPath: stagedOutputURL.path) else { return 0 }
        var currentURL = stagedOutputURL
        currentURL.removeAllCachedResourceValues()
        let values = try currentURL.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey, .isSymbolicLinkKey])
        guard values.isRegularFile == true, values.isSymbolicLink != true else {
            throw BoundedEncoderError.invalidInput("Encoder staging did not create a regular file.")
        }
        return Int64(values.fileSize ?? 0)
    }

    fileprivate func snapshot(_ inputs: [URL], maximumBytes: Int64) throws -> [URL] {
        var total: Int64 = 0
        return try inputs.enumerated().map { index, source in
            let target = jobDirectory.appendingPathComponent("input-\(index).\(source.pathExtension)")
            guard FileManager.default.createFile(atPath: target.path, contents: nil, attributes: [.posixPermissions: 0o600]) else {
                throw BoundedEncoderError.invalidInput("Cannot stage an encoder input snapshot.")
            }
            let reader = try FileHandle(forReadingFrom: source)
            defer { try? reader.close() }
            let writer = try FileHandle(forWritingTo: target)
            defer { try? writer.close() }
            while let bytes = try reader.read(upToCount: 1_048_576), !bytes.isEmpty {
                guard Int64(bytes.count) <= maximumBytes - total else {
                    throw BoundedEncoderError.invalidInput("Input snapshot exceeds the byte limit.")
                }
                total += Int64(bytes.count)
                try writer.write(contentsOf: bytes)
            }
            return target
        }
    }

    fileprivate var workingDirectory: URL { jobDirectory }

    private static func isSafeFileName(_ value: String) -> Bool {
        !value.isEmpty && value != "." && value != ".." && !value.contains("/") && !value.contains("\\") && !value.utf8.contains(0)
    }

    private static func requireRealDirectory(_ url: URL, description: String) throws {
        let values = try url.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
        guard values.isDirectory == true, values.isSymbolicLink != true else {
            throw BoundedEncoderError.unsafeInvocation("The \(description) must be a real directory, not a symbolic link.")
        }
    }

    private static func isRegularFile(_ url: URL) -> Bool {
        var currentURL = url
        currentURL.removeAllCachedResourceValues()
        guard let values = try? currentURL.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey]) else { return false }
        return values.isRegularFile == true && values.isSymbolicLink != true
    }
}

public final class BoundedEncoderProcess {
    public init() {}

    public func run(
        invocation: EncoderInvocation,
        inputs: [URL],
        stager: OwnedOutputStager,
        limits: EncoderLimits = EncoderLimits(),
        cancellation: EncoderCancellation = EncoderCancellation(),
        onProgress: @escaping @Sendable (EncoderProgress) -> Void = { _ in }
    ) throws -> PublishedEncoderOutput {
        defer { stager.cleanup() }
        guard !cancellation.isCancelled else { throw BoundedEncoderError.cancelled }
        guard limits.maximumInputBytes > 0, limits.maximumOutputBytes > 0,
              limits.minimumAvailableBytes >= 0, limits.timeout.isFinite, limits.timeout > 0, limits.maximumDiagnosticBytes > 0, limits.maximumDiagnosticBytes <= 1_048_576 else {
            throw BoundedEncoderError.unsafeInvocation("Encoder limits must be positive and finite.")
        }
        try validate(inputs: inputs, maximumBytes: limits.maximumInputBytes)
        try validateAvailableSpace(at: stager.outputDirectory, minimumBytes: limits.minimumAvailableBytes)
        let snapshots = try stager.snapshot(inputs, maximumBytes: limits.maximumInputBytes)
        let arguments = try materialize(invocation: invocation, inputs: snapshots, stagedOutput: stager.stagedOutputURL)

        let stdout = Pipe()
        let stderr = Pipe()
        let collector = EncoderOutputCollector(limit: limits.maximumDiagnosticBytes, progress: onProgress)
        defer { collector.close() }
        for handle in [stdout.fileHandleForReading, stderr.fileHandleForReading] {
            let flags = fcntl(handle.fileDescriptor, F_GETFL)
            guard flags >= 0, fcntl(handle.fileDescriptor, F_SETFL, flags | O_NONBLOCK) == 0 else {
                throw BoundedEncoderError.launchFailed("Cannot configure bounded encoder diagnostics.")
            }
        }
        guard !cancellation.isCancelled else { throw BoundedEncoderError.cancelled }
        let process = try OwnedEncoderGroup(executable: invocation.executableURL.path, arguments: arguments,
            directory: stager.workingDirectory.path, stdout: stdout.fileHandleForWriting.fileDescriptor,
            stderr: stderr.fileHandleForWriting.fileDescriptor)
        try? stdout.fileHandleForWriting.close()
        try? stderr.fileHandleForWriting.close()
        defer {
            process.stop()
            try? stdout.fileHandleForReading.close()
            try? stderr.fileHandleForReading.close()
        }

        let deadline = Date().addingTimeInterval(limits.timeout)
        var timedOut = false
        var outputExceeded = false
        while try process.poll() == nil {
            Thread.sleep(forTimeInterval: 0.02)
            Self.drain(stdout.fileHandleForReading, into: collector, stream: 0)
            Self.drain(stderr.fileHandleForReading, into: collector, stream: 1)
            if cancellation.isCancelled {
                process.stop()
            } else if Date() >= deadline {
                timedOut = true
                process.stop()
            } else if ((try? stager.stagedSize()) ?? 0) > limits.maximumOutputBytes {
                outputExceeded = true
                process.stop()
            }
        }
        // Any surviving member means parent success is not a complete output.
        let hadDescendants = process.finish()
        // Nonblocking drains cannot hang if an encoder descendant retains a pipe.
        Self.drain(stdout.fileHandleForReading, into: collector, stream: 0)
        Self.drain(stderr.fileHandleForReading, into: collector, stream: 1)
        collector.finish()

        if cancellation.isCancelled {
            stager.cleanup()
            throw BoundedEncoderError.cancelled
        }
        if timedOut {
            stager.cleanup()
            throw BoundedEncoderError.timedOut
        }
        if try outputExceeded || stager.stagedSize() > limits.maximumOutputBytes {
            stager.cleanup()
            throw BoundedEncoderError.outputTooLarge
        }
        guard !hadDescendants else {
            throw BoundedEncoderError.processFailed(status: 1, diagnostic: "Encoder left child processes running; output was not published.")
        }
        guard process.status == 0 else {
            let diagnostic = Self.redact(collector.diagnostic)
            stager.cleanup()
            if diagnostic.localizedCaseInsensitiveContains("no space left") || diagnostic.localizedCaseInsensitiveContains("disk full") {
                throw BoundedEncoderError.insufficientDiskSpace
            }
            throw BoundedEncoderError.processFailed(status: process.status ?? 1, diagnostic: diagnostic)
        }
        do {
            let outputURL = try stager.publish()
            return PublishedEncoderOutput(outputURL: outputURL, progress: collector.progress)
        } catch {
            stager.cleanup()
            throw error
        }
    }

    private func materialize(invocation: EncoderInvocation, inputs: [URL], stagedOutput: URL) throws -> [String] {
        guard invocation.executableURL.isFileURL, invocation.executableURL.path.hasPrefix("/") else {
            throw BoundedEncoderError.unsafeInvocation("Encoder executable must be an absolute local file URL.")
        }
        let executableValues = try invocation.executableURL.resourceValues(forKeys: [.isRegularFileKey, .isExecutableKey, .isSymbolicLinkKey])
        guard executableValues.isRegularFile == true, executableValues.isExecutable == true, executableValues.isSymbolicLink != true else {
            throw BoundedEncoderError.unsafeInvocation("Encoder executable must be an executable regular file, not a symbolic link.")
        }
        guard invocation.arguments.filter({ if case .stagedOutput = $0 { return true }; return false }).count == 1 else {
            throw BoundedEncoderError.unsafeInvocation("Encoder argv must contain exactly one staged-output token.")
        }
        let argv = try invocation.arguments.map { argument -> String in
            switch argument {
            case .literal(let value):
                guard !value.utf8.contains(0) else { throw BoundedEncoderError.unsafeInvocation("Encoder argv cannot contain NUL bytes.") }
                return value
            case .input(let index):
                guard inputs.indices.contains(index) else { throw BoundedEncoderError.unsafeInvocation("Encoder argv references a missing input.") }
                return inputs[index].path
            case .stagedOutput:
                return stagedOutput.path
            }
        }
        guard argv.reduce(0, { $0 + $1.utf8.count + 1 }) <= 32 * 1024 else {
            throw BoundedEncoderError.unsafeInvocation("Encoder argv exceeds the companion limit.")
        }
        return argv
    }

    private func validate(inputs: [URL], maximumBytes: Int64) throws {
        var total: Int64 = 0
        for input in inputs {
            guard input.isFileURL else { throw BoundedEncoderError.invalidInput("Encoder inputs must be local files.") }
            let values = try input.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
            guard values.isRegularFile == true, values.isSymbolicLink != true else {
                throw BoundedEncoderError.invalidInput("Encoder input must be a regular file, not a symbolic link.")
            }
            total += Int64(values.fileSize ?? 0)
            guard total <= maximumBytes else { throw BoundedEncoderError.invalidInput("Encoder inputs exceed this job's byte limit.") }
        }
    }

    private func validateAvailableSpace(at outputDirectory: URL, minimumBytes: Int64) throws {
        let values = try outputDirectory.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
        guard let available = values.volumeAvailableCapacityForImportantUsage, available >= minimumBytes else {
            throw BoundedEncoderError.insufficientDiskSpace
        }
    }

    private static func drain(_ handle: FileHandle, into collector: EncoderOutputCollector, stream: Int) {
        var bytes = [UInt8](repeating: 0, count: 4096)
        // Limit work per poll as well as storage so a noisy process cannot starve timeout/cancel.
        for _ in 0..<16 {
            let count = Darwin.read(handle.fileDescriptor, &bytes, bytes.count)
            guard count > 0 else { break }
            collector.consume(Data(bytes.prefix(count)), stream: stream)
        }
    }

    private static func redact(_ value: String) -> String {
        let clipped = String(value.prefix(4_000))
        let pattern = "(?im)(authorization|token|secret|password)\\s*[:=][^\\r\\n]*"
        guard let expression = try? NSRegularExpression(pattern: pattern) else { return clipped }
        return expression.stringByReplacingMatches(in: clipped, range: NSRange(clipped.startIndex..., in: clipped), withTemplate: "$1=<redacted>")
    }
}

private final class EncoderOutputCollector {
    private let limit: Int
    private let delivery: EncoderProgressDelivery
    private var partial = [Data(), Data()]
    private var overflow = [false, false]
    private var captured = Data()
    private(set) var progress: [EncoderProgress] = []

    init(limit: Int, progress: @escaping @Sendable (EncoderProgress) -> Void) {
        self.limit = limit
        self.delivery = EncoderProgressDelivery(handler: progress)
    }

    func close() { delivery.close() }

    func consume(_ data: Data, stream: Int) {
        captured.append(data.suffix(limit))
        if captured.count > limit { captured = Data(captured.suffix(limit)) }
        for byte in data {
            if byte == 10 {
                emit(stream)
                partial[stream].removeAll(keepingCapacity: true)
                overflow[stream] = false
            } else if partial[stream].count < limit {
                partial[stream].append(byte)
            } else { overflow[stream] = true }
        }
    }

    func finish() { emit(0); emit(1) }

    private func emit(_ stream: Int) {
        guard !overflow[stream], let line = String(data: partial[stream], encoding: .utf8),
              line.hasPrefix("progress="),
              let first = line.dropFirst(9).split(whereSeparator: \.isWhitespace).first,
              let value = Double(first), value.isFinite, (0...1).contains(value) else { return }
        // Never forward arbitrary diagnostic text or credentials in progress callbacks.
        let update = EncoderProgress(fraction: value, line: "progress=\(value)")
        if progress.count == 128 { progress.removeFirst() }
        progress.append(update)
        delivery.submit(update)
    }

    var diagnostic: String {
        String(data: captured, encoding: .utf8) ?? "Encoder emitted non-text diagnostics."
    }
}

/// Supervision never waits for a UI consumer. At most one callback runs and one
/// latest update waits. Closing discards pending updates; an in-flight callback
/// may finish after run returns, so consumers should keep callbacks short.
private final class EncoderProgressDelivery: @unchecked Sendable {
    private let lock = NSLock()
    private let queue = DispatchQueue(label: "melotrail.tabi.encoder-progress")
    private let handler: @Sendable (EncoderProgress) -> Void
    private var pending: EncoderProgress?
    private var delivering = false
    private var closed = false

    init(handler: @escaping @Sendable (EncoderProgress) -> Void) { self.handler = handler }

    func submit(_ update: EncoderProgress) {
        lock.lock()
        guard !closed else { lock.unlock(); return }
        pending = update
        guard !delivering else { lock.unlock(); return }
        delivering = true
        lock.unlock()
        queue.async { self.deliver() }
    }

    func close() {
        lock.lock()
        closed = true
        pending = nil
        lock.unlock()
    }

    private func deliver() {
        while true {
            lock.lock()
            guard !closed, let update = pending else {
                delivering = false
                lock.unlock()
                return
            }
            pending = nil
            lock.unlock()
            handler(update)
        }
    }
}

/// A group is created atomically with spawn; no post-launch setpgid race.
private final class OwnedEncoderGroup {
    private var pid: pid_t = 0
    private var stopped = false
    private(set) var status: Int32?

    init(executable: String, arguments: [String], directory: String, stdout: Int32, stderr: Int32) throws {
        var actions: posix_spawn_file_actions_t?
        var attributes: posix_spawnattr_t?
        func checked(_ result: Int32) throws {
            guard result == 0 else { throw BoundedEncoderError.launchFailed(String(cString: strerror(result))) }
        }
        try checked(posix_spawn_file_actions_init(&actions))
        defer { posix_spawn_file_actions_destroy(&actions) }
        try checked(posix_spawnattr_init(&attributes))
        defer { posix_spawnattr_destroy(&attributes) }
        try checked(posix_spawn_file_actions_addopen(&actions, STDIN_FILENO, "/dev/null", O_RDONLY, 0))
        try checked(posix_spawn_file_actions_adddup2(&actions, stdout, STDOUT_FILENO))
        try checked(posix_spawn_file_actions_adddup2(&actions, stderr, STDERR_FILENO))
        try checked(posix_spawn_file_actions_addchdir_np(&actions, directory))
        try checked(posix_spawnattr_setpgroup(&attributes, 0))
        try checked(posix_spawnattr_setflags(&attributes, Int16(POSIX_SPAWN_SETPGROUP | POSIX_SPAWN_CLOEXEC_DEFAULT)))
        let argv = ([executable] + arguments).map { strdup($0) } + [nil]
        let env = ProcessInfo.processInfo.environment.map { strdup("\($0.key)=\($0.value)") } + [nil]
        defer { argv.forEach { free($0) }; env.forEach { free($0) } }
        try argv.withUnsafeBufferPointer { args in
            try env.withUnsafeBufferPointer { environment in
                try checked(posix_spawn(&pid, executable, &actions, &attributes,
                    UnsafeMutablePointer(mutating: args.baseAddress!), UnsafeMutablePointer(mutating: environment.baseAddress!)))
            }
        }
    }

    func poll() throws -> Int32? {
        if let status { return status }
        var raw: Int32 = 0
        let result = waitpid(pid, &raw, WNOHANG)
        if result == pid {
            status = raw & 0x7f == 0 ? (raw >> 8) & 0xff : 128 + (raw & 0x7f)
        } else if result < 0 && errno != EINTR {
            throw BoundedEncoderError.launchFailed("Cannot observe owned encoder exit.")
        }
        return status
    }

    func finish() -> Bool {
        guard !stopped else { return false }
        let descendants = kill(-pid, 0) == 0
        if descendants { stop() } else { stopped = true }
        return descendants
    }

    func stop() {
        guard !stopped, pid > 0 else { return }
        stopped = true
        _ = kill(-pid, SIGTERM)
        let deadline = Date().addingTimeInterval(0.1)
        while Date() < deadline && kill(-pid, 0) == 0 {
            _ = try? poll()
            Thread.sleep(forTimeInterval: 0.01)
        }
        _ = kill(-pid, SIGKILL)
        if status == nil {
            var raw: Int32 = 0
            var result: pid_t
            repeat { result = waitpid(pid, &raw, 0) } while result < 0 && errno == EINTR
            if result == pid { status = raw & 0x7f == 0 ? (raw >> 8) & 0xff : 128 + (raw & 0x7f) }
        }
    }

    deinit { stop() }
}
