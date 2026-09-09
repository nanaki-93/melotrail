import CryptoKit
import Darwin
import Foundation

/// A provider-neutral, persisted animation-request ledger. It intentionally
/// contains request metadata and costs, never credentials or provider payloads.
public struct AnimationCost: Codable, Equatable, Hashable, Sendable {
    public let currency: String
    public let amountCents: Int64

    public init(currency: String, amountCents: Int64) {
        self.currency = currency.uppercased()
        self.amountCents = amountCents
    }
}

public struct AnimationBudget: Codable, Equatable, Hashable, Sendable {
    public let budgetID: String
    public let maximumCost: AnimationCost
    public let maximumInFlight: Int

    public init(budgetID: String, maximumCost: AnimationCost, maximumInFlight: Int = 1) {
        self.budgetID = budgetID
        self.maximumCost = maximumCost
        self.maximumInFlight = maximumInFlight
    }
}

/// This is the reviewable provider input shape. A V03b adapter translates it
/// to its API; this slice deliberately supplies no live provider implementation.
public struct AnimationRequest: Codable, Equatable, Hashable, Sendable {
    public let providerID: String
    public let model: String
    public let options: [String: String]
    public let prompt: String
    public let referenceAssets: [AssetIdentity]
    public let seed: String?
    /// A missing quote is an admission failure, never a zero-cost request.
    public let estimatedCost: AnimationCost?
    public let maximumAttempts: Int

    public init(
        providerID: String,
        model: String,
        options: [String: String],
        prompt: String,
        referenceAssets: [AssetIdentity],
        seed: String? = nil,
        estimatedCost: AnimationCost?,
        maximumAttempts: Int
    ) {
        self.providerID = providerID
        self.model = model
        self.options = options
        self.prompt = prompt
        self.referenceAssets = referenceAssets
        self.seed = seed
        self.estimatedCost = estimatedCost
        self.maximumAttempts = maximumAttempts
    }
}

/// Stable request identity. An adapter must pass `idempotencyKey` to a provider
/// when that provider supports it; V03b owns the provider-specific mechanics.
public struct AnimationSubmissionIdentity: Codable, Equatable, Hashable, Sendable {
    public let requestFingerprint: String
    public let attempt: Int
    public let idempotencyKey: String

    public init(requestFingerprint: String, attempt: Int) {
        self.requestFingerprint = requestFingerprint
        self.attempt = attempt
        self.idempotencyKey = "melotrail-tabi-\(requestFingerprint)-\(attempt)"
    }
}

public enum AnimationAttemptState: String, Codable, Equatable, Sendable {
    case submitting
    case submitted
    case cancellationRequested
    case succeeded
    case failed
    case cancelled
    /// The provider might have received the paid request, but did not return an ID.
    case submissionUncertain
}

public struct AnimationJobAttempt: Codable, Equatable, Sendable {
    public let identity: AnimationSubmissionIdentity
    public var state: AnimationAttemptState
    public var providerJobID: String?
    public let estimatedCost: AnimationCost
    public var actualCost: AnimationCost?
    public var failure: String?
    public let submittedAt: Date
    public var updatedAt: Date
    public var nextPollAt: Date?

    public init(
        identity: AnimationSubmissionIdentity,
        state: AnimationAttemptState,
        providerJobID: String? = nil,
        estimatedCost: AnimationCost,
        actualCost: AnimationCost? = nil,
        failure: String? = nil,
        submittedAt: Date,
        updatedAt: Date,
        nextPollAt: Date? = nil
    ) {
        self.identity = identity
        self.state = state
        self.providerJobID = providerJobID
        self.estimatedCost = estimatedCost
        self.actualCost = actualCost
        self.failure = failure
        self.submittedAt = submittedAt
        self.updatedAt = updatedAt
        self.nextPollAt = nextPollAt
    }
}

public struct AnimationJob: Codable, Equatable, Sendable {
    public let jobID: String
    public let budgetID: String
    public let request: AnimationRequest
    public let requestFingerprint: String
    public let createdAt: Date
    public var attempts: [AnimationJobAttempt]

    public init(jobID: String, budgetID: String, request: AnimationRequest, requestFingerprint: String, createdAt: Date, attempts: [AnimationJobAttempt]) {
        self.jobID = jobID
        self.budgetID = budgetID
        self.request = request
        self.requestFingerprint = requestFingerprint
        self.createdAt = createdAt
        self.attempts = attempts
    }

    public var latestAttempt: AnimationJobAttempt? { attempts.last }
}

public struct AnimationJobLedger: Codable, Equatable, Sendable {
    public static let currentSchemaVersion = 1

    public let schemaVersion: Int
    public var budgets: [AnimationBudget]
    public var jobs: [AnimationJob]

    public init(schemaVersion: Int = AnimationJobLedger.currentSchemaVersion, budgets: [AnimationBudget] = [], jobs: [AnimationJob] = []) {
        self.schemaVersion = schemaVersion
        self.budgets = budgets
        self.jobs = jobs
    }
}

public enum AnimationJobError: Error, Equatable, LocalizedError, Sendable {
    case unreadableLedger(String)
    case unsupportedSchema(Int)
    case invalidRequest(String)
    case invalidBudget(String)
    case unknownCost
    case budgetExceeded(budgetID: String, maximumCents: Int64, projectedCents: Int64)
    case concurrencyLimit(budgetID: String, maximumInFlight: Int)
    case providerMismatch(expected: String, actual: String)
    case jobNotFound(String)
    case attemptNotRestartable(String)
    case attemptLimitReached(String)
    case missingProviderJobID(String)
    case unsafeLedgerPath(String)

    public var errorDescription: String? {
        switch self {
        case .unreadableLedger(let message): "Cannot read animation job ledger: \(message)"
        case .unsupportedSchema(let version): "Animation job ledger schema \(version) is unsupported."
        case .invalidRequest(let message), .invalidBudget(let message), .unsafeLedgerPath(let message): message
        case .unknownCost: "Animation requests require a known estimated cost before submission."
        case .budgetExceeded(let budgetID, let maximum, let projected): "Budget \(budgetID) caps requests at \(maximum) cents; this request would reserve \(projected) cents."
        case .concurrencyLimit(let budgetID, let maximum): "Budget \(budgetID) permits at most \(maximum) in-flight animation request(s)."
        case .providerMismatch(let expected, let actual): "Job belongs to provider \(expected), not \(actual)."
        case .jobNotFound(let id): "No animation job exists with ID \(id)."
        case .attemptNotRestartable(let id): "Animation job \(id) has no failed or cancelled attempt to restart."
        case .attemptLimitReached(let id): "Animation job \(id) has reached its configured attempt limit."
        case .missingProviderJobID(let id): "Animation job \(id) has no provider job ID to query or cancel."
        }
    }
}

public enum AnimationProviderState: String, Codable, Equatable, Sendable {
    case queued
    case running
    case succeeded
    case failed
    case cancelled
    case rateLimited
}

public struct AnimationProviderPoll: Equatable, Sendable {
    public let state: AnimationProviderState
    public let actualCost: AnimationCost?
    public let failure: String?
    public let retryAfter: TimeInterval?

    public init(state: AnimationProviderState, actualCost: AnimationCost? = nil, failure: String? = nil, retryAfter: TimeInterval? = nil) {
        self.state = state
        self.actualCost = actualCost
        self.failure = failure
        self.retryAfter = retryAfter
    }
}

/// Provider implementations belong to V03b. This protocol permits contract
/// fakes now and prevents a coordinator from depending on network details.
public protocol AnimationJobProvider: Sendable {
    var providerID: String { get }
    func submit(request: AnimationRequest, identity: AnimationSubmissionIdentity) throws -> String
    func poll(providerJobID: String) throws -> AnimationProviderPoll
    func cancel(providerJobID: String) throws -> AnimationProviderPoll
}

public enum AnimationJobStore {
    public static func load(from url: URL) throws -> AnimationJobLedger {
        guard url.isFileURL else { throw AnimationJobError.unsafeLedgerPath("Animation jobs require a local ledger path.") }
        guard FileManager.default.fileExists(atPath: url.path) else { return AnimationJobLedger() }
        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let ledger = try decoder.decode(AnimationJobLedger.self, from: Data(contentsOf: url))
            guard ledger.schemaVersion == AnimationJobLedger.currentSchemaVersion else { throw AnimationJobError.unsupportedSchema(ledger.schemaVersion) }
            try validate(ledger)
            return ledger
        } catch let error as AnimationJobError {
            throw error
        } catch {
            throw AnimationJobError.unreadableLedger(error.localizedDescription)
        }
    }

    fileprivate static func save(_ ledger: AnimationJobLedger, to url: URL) throws {
        guard url.isFileURL, !url.lastPathComponent.isEmpty else { throw AnimationJobError.unsafeLedgerPath("Animation jobs require a local ledger file.") }
        guard ledger.schemaVersion == AnimationJobLedger.currentSchemaVersion else { throw AnimationJobError.unsupportedSchema(ledger.schemaVersion) }
        try validate(ledger)
        let directory = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let staged = directory.appendingPathComponent(".animation-jobs-\(UUID().uuidString).tmp")
        defer { try? FileManager.default.removeItem(at: staged) }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        try encoder.encode(ledger).write(to: staged, options: .withoutOverwriting)
        // Both paths are in the same directory. POSIX rename is the one atomic
        // publication point for this mutable ledger and replaces only this file.
        if rename(staged.path, url.path) != 0 {
            throw AnimationJobError.unreadableLedger("Cannot publish animation job ledger: \(String(cString: strerror(errno))).")
        }
    }

    private static func validate(_ ledger: AnimationJobLedger) throws {
        var budgetIDs = Set<String>()
        for budget in ledger.budgets {
            guard !budget.budgetID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  budget.maximumCost.amountCents >= 0,
                  budget.maximumInFlight > 0,
                  !budget.maximumCost.currency.isEmpty else {
                throw AnimationJobError.invalidBudget("Animation budgets require an ID, currency, and non-negative cap.")
            }
            guard budgetIDs.insert(budget.budgetID).inserted else { throw AnimationJobError.invalidBudget("Duplicate animation budget ID \(budget.budgetID).") }
        }
        var jobIDs = Set<String>()
        var fingerprints = Set<String>()
        for job in ledger.jobs {
            guard jobIDs.insert(job.jobID).inserted, fingerprints.insert(job.requestFingerprint).inserted else {
                throw AnimationJobError.invalidRequest("Animation jobs require unique IDs and request fingerprints.")
            }
            guard let budget = ledger.budgets.first(where: { $0.budgetID == job.budgetID }), !job.attempts.isEmpty else {
                throw AnimationJobError.invalidRequest("Animation job \(job.jobID) has no known budget or attempt.")
            }
            guard !job.request.providerID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  !job.request.model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  !job.request.prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  !job.request.referenceAssets.isEmpty,
                  Set(job.request.referenceAssets).count == job.request.referenceAssets.count,
                  job.request.maximumAttempts > 0,
                  let estimate = job.request.estimatedCost,
                  estimate.amountCents >= 0,
                  estimate.currency == budget.maximumCost.currency,
                  job.attempts.count <= job.request.maximumAttempts else {
                throw AnimationJobError.invalidRequest("Animation job \(job.jobID) has invalid immutable request or attempt data.")
            }
            for (offset, attempt) in job.attempts.enumerated() {
                guard attempt.identity.requestFingerprint == job.requestFingerprint,
                      attempt.identity.attempt == offset + 1,
                      attempt.estimatedCost.currency == budget.maximumCost.currency,
                      attempt.estimatedCost.amountCents >= 0,
                      attempt.actualCost.map({ $0.currency == budget.maximumCost.currency && $0.amountCents >= 0 }) ?? true else {
                    throw AnimationJobError.invalidRequest("Animation job \(job.jobID) has invalid attempt cost or identity data.")
                }
                if attempt.state == .submitted || attempt.state == .cancellationRequested {
                    guard let providerJobID = attempt.providerJobID,
                          !providerJobID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                        throw AnimationJobError.invalidRequest("Animation job \(job.jobID) has a queryable state without a provider job ID.")
                    }
                }
            }
        }
    }
}

/// Coordinates durable admission and provider state. It deliberately never
/// creates a network client: callers must inject a provider (the V03a tests use
/// an owned fake), so production cannot submit a live generation request here.
public final class AnimationJobCoordinator {
    private let ledgerURL: URL
    private let pollBaseDelay: TimeInterval

    public init(ledgerURL: URL, pollBaseDelay: TimeInterval = 5) {
        self.ledgerURL = ledgerURL.isFileURL
            ? ledgerURL.standardizedFileURL.pathComponents.dropFirst().reduce(URL(fileURLWithPath: "/")) {
                $0.appendingPathComponent($1).resolvingSymlinksInPath()
            }
            : ledgerURL
        self.pollBaseDelay = pollBaseDelay
    }

    public func jobs() throws -> [AnimationJob] { try AnimationJobStore.load(from: ledgerURL).jobs }

    /// The normal asset-aware caller validates the exact proposed/approved pins
    /// at admission. Keeping this separate lets owned provider-contract tests
    /// exercise the job layer without manufacturing production media.
    @discardableResult
    public func submitApproved(
        _ request: AnimationRequest,
        to budget: AnimationBudget,
        assetLibrary: AssetLibrary,
        provider: any AnimationJobProvider,
        now: Date = Date()
    ) throws -> AnimationJob {
        _ = try assetLibrary.validatedApprovedAssets(pinned: request.referenceAssets)
        return try submit(request, to: budget, provider: provider, now: now)
    }

    @discardableResult
    public func submit(_ request: AnimationRequest, to budget: AnimationBudget, provider: any AnimationJobProvider, now: Date = Date()) throws -> AnimationJob {
        let lockDescriptor = try acquireLedgerLock()
        defer { close(lockDescriptor) }

        try validateRequest(request, budget: budget, provider: provider)
        var ledger = try AnimationJobStore.load(from: ledgerURL)
        try register(budget, in: &ledger)
        let fingerprint = try requestFingerprint(request)
        if let existing = ledger.jobs.first(where: { $0.requestFingerprint == fingerprint }) {
            guard existing.budgetID == budget.budgetID else { throw AnimationJobError.invalidRequest("The identical request is already recorded under budget \(existing.budgetID).") }
            return existing
        }
        let estimate = try knownCost(request)
        try admit(estimate, budget: budget, ledger: ledger)
        let identity = AnimationSubmissionIdentity(requestFingerprint: fingerprint, attempt: 1)
        let job = AnimationJob(
            jobID: UUID().uuidString.lowercased(), budgetID: budget.budgetID, request: request,
            requestFingerprint: fingerprint, createdAt: now,
            attempts: [AnimationJobAttempt(identity: identity, state: .submitting, estimatedCost: estimate, submittedAt: now, updatedAt: now)]
        )
        ledger.jobs.append(job)
        try AnimationJobStore.save(ledger, to: ledgerURL)

        do {
            let providerJobID = try provider.submit(request: request, identity: identity)
            guard !providerJobID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return try markSubmissionUncertain(job.jobID, reason: "Provider returned an empty job ID.", now: now)
            }
            return try markSubmitted(job.jobID, providerJobID: providerJobID, now: now)
        } catch {
            // Do not rethrow an ambiguous transport error: the durable state is
            // the important result and blocks blind paid retries after restart.
            return try markSubmissionUncertain(job.jobID, reason: "Submission outcome is uncertain; query the provider before any retry.", now: now)
        }
    }

    @discardableResult
    public func poll(jobID: String, provider: any AnimationJobProvider, now: Date = Date()) throws -> AnimationJob {
        let lockDescriptor = try acquireLedgerLock()
        defer { close(lockDescriptor) }

        var ledger = try AnimationJobStore.load(from: ledgerURL)
        let index = try index(of: jobID, in: ledger)
        try requireProvider(provider, for: ledger.jobs[index])
        guard let attempt = ledger.jobs[index].latestAttempt else { throw AnimationJobError.invalidRequest("Animation job \(jobID) has no attempt.") }
        guard attempt.state == .submitted || attempt.state == .cancellationRequested else { return ledger.jobs[index] }
        if let nextPollAt = attempt.nextPollAt, now < nextPollAt { return ledger.jobs[index] }
        guard let providerJobID = attempt.providerJobID else { throw AnimationJobError.missingProviderJobID(jobID) }
        let result: AnimationProviderPoll
        do {
            result = try provider.poll(providerJobID: providerJobID)
        } catch {
            ledger.jobs[index].attempts[ledger.jobs[index].attempts.count - 1].nextPollAt = now.addingTimeInterval(nextDelay(for: attempt))
            ledger.jobs[index].attempts[ledger.jobs[index].attempts.count - 1].updatedAt = now
            try AnimationJobStore.save(ledger, to: ledgerURL)
            return ledger.jobs[index]
        }
        apply(result, to: &ledger.jobs[index].attempts[ledger.jobs[index].attempts.count - 1], now: now)
        try AnimationJobStore.save(ledger, to: ledgerURL)
        return ledger.jobs[index]
    }

    /// Restart recovery only queries an existing provider ID. A submission that
    /// lacks one remains visibly uncertain and is never resubmitted here.
    @discardableResult
    public func recover(provider: any AnimationJobProvider, now: Date = Date()) throws -> [AnimationJob] {
        let current = try jobs().filter { job in
            guard let attempt = job.latestAttempt else { return false }
            return (attempt.state == .submitted || attempt.state == .cancellationRequested) && attempt.providerJobID != nil
        }
        return try current.map { try poll(jobID: $0.jobID, provider: provider, now: now) }
    }

    @discardableResult
    public func cancel(jobID: String, provider: any AnimationJobProvider, now: Date = Date()) throws -> AnimationJob {
        let lockDescriptor = try acquireLedgerLock()
        defer { close(lockDescriptor) }

        var ledger = try AnimationJobStore.load(from: ledgerURL)
        let jobIndex = try index(of: jobID, in: ledger)
        try requireProvider(provider, for: ledger.jobs[jobIndex])
        guard let providerJobID = ledger.jobs[jobIndex].latestAttempt?.providerJobID else { throw AnimationJobError.missingProviderJobID(jobID) }
        guard ledger.jobs[jobIndex].latestAttempt?.state == .submitted || ledger.jobs[jobIndex].latestAttempt?.state == .cancellationRequested else { return ledger.jobs[jobIndex] }
        ledger.jobs[jobIndex].attempts[ledger.jobs[jobIndex].attempts.count - 1].state = .cancellationRequested
        ledger.jobs[jobIndex].attempts[ledger.jobs[jobIndex].attempts.count - 1].updatedAt = now
        try AnimationJobStore.save(ledger, to: ledgerURL)
        do {
            let result = try provider.cancel(providerJobID: providerJobID)
            ledger = try AnimationJobStore.load(from: ledgerURL)
            let refreshed = try index(of: jobID, in: ledger)
            apply(result, to: &ledger.jobs[refreshed].attempts[ledger.jobs[refreshed].attempts.count - 1], now: now)
            try AnimationJobStore.save(ledger, to: ledgerURL)
            return ledger.jobs[refreshed]
        } catch {
            let persisted = try AnimationJobStore.load(from: ledgerURL)
            return persisted.jobs[try index(of: jobID, in: persisted)]
        }
    }

    @discardableResult
    public func restart(jobID: String, provider: any AnimationJobProvider, now: Date = Date()) throws -> AnimationJob {
        let lockDescriptor = try acquireLedgerLock()
        defer { close(lockDescriptor) }

        var ledger = try AnimationJobStore.load(from: ledgerURL)
        let index = try index(of: jobID, in: ledger)
        let current = ledger.jobs[index]
        try requireProvider(provider, for: current)
        guard let previous = current.latestAttempt,
              (previous.state == .failed || previous.state == .cancelled) else { throw AnimationJobError.attemptNotRestartable(jobID) }
        guard current.attempts.count < current.request.maximumAttempts else { throw AnimationJobError.attemptLimitReached(jobID) }
        let estimate = try knownCost(current.request)
        guard let budget = ledger.budgets.first(where: { $0.budgetID == current.budgetID }) else { throw AnimationJobError.invalidBudget("Animation job \(jobID) references a missing budget.") }
        try admit(estimate, budget: budget, ledger: ledger)
        let identity = AnimationSubmissionIdentity(requestFingerprint: current.requestFingerprint, attempt: current.attempts.count + 1)
        ledger.jobs[index].attempts.append(AnimationJobAttempt(identity: identity, state: .submitting, estimatedCost: estimate, submittedAt: now, updatedAt: now))
        try AnimationJobStore.save(ledger, to: ledgerURL)
        do {
            let providerJobID = try provider.submit(request: current.request, identity: identity)
            guard !providerJobID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return try markSubmissionUncertain(jobID, reason: "Provider returned an empty job ID.", now: now)
            }
            return try markSubmitted(jobID, providerJobID: providerJobID, now: now)
        } catch {
            return try markSubmissionUncertain(jobID, reason: "Submission outcome is uncertain; query the provider before any retry.", now: now)
        }
    }

    // Lock a stable sibling, not the atomically replaced ledger inode. Never
    // unlink this file: replacing it would split concurrent lock ownership.
    // flock coordinates independent instances, threads and local processes;
    // the OS releases it when the owner exits, including after a crash.
    private func acquireLedgerLock() throws -> Int32 {
        guard ledgerURL.isFileURL, !ledgerURL.lastPathComponent.isEmpty else {
            throw AnimationJobError.unsafeLedgerPath("Animation jobs require a local ledger file.")
        }
        let directory = ledgerURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let lockURL = directory.appendingPathComponent(".\(ledgerURL.lastPathComponent).lock")
        let descriptor = open(lockURL.path, O_CREAT | O_RDWR | O_CLOEXEC | O_NOFOLLOW, S_IRUSR | S_IWUSR)
        guard descriptor >= 0 else {
            throw AnimationJobError.unreadableLedger("Cannot open animation ledger lock.")
        }
        var acquired = false
        defer { if !acquired { close(descriptor) } }
        let deadline = ProcessInfo.processInfo.systemUptime + 5
        while flock(descriptor, LOCK_EX | LOCK_NB) != 0 {
            guard errno == EWOULDBLOCK || errno == EINTR else {
                throw AnimationJobError.unreadableLedger("Cannot acquire animation ledger lock.")
            }
            guard ProcessInfo.processInfo.systemUptime < deadline else {
                throw AnimationJobError.unreadableLedger("Animation ledger is busy; retry after the active operation finishes.")
            }
            Thread.sleep(forTimeInterval: 0.01)
        }
        acquired = true
        return descriptor
    }

    private func markSubmitted(_ jobID: String, providerJobID: String, now: Date) throws -> AnimationJob {
        var ledger = try AnimationJobStore.load(from: ledgerURL)
        let index = try index(of: jobID, in: ledger)
        ledger.jobs[index].attempts[ledger.jobs[index].attempts.count - 1].state = .submitted
        ledger.jobs[index].attempts[ledger.jobs[index].attempts.count - 1].providerJobID = providerJobID
        ledger.jobs[index].attempts[ledger.jobs[index].attempts.count - 1].updatedAt = now
        ledger.jobs[index].attempts[ledger.jobs[index].attempts.count - 1].nextPollAt = now
        try AnimationJobStore.save(ledger, to: ledgerURL)
        return ledger.jobs[index]
    }

    private func markSubmissionUncertain(_ jobID: String, reason: String, now: Date) throws -> AnimationJob {
        var ledger = try AnimationJobStore.load(from: ledgerURL)
        let index = try index(of: jobID, in: ledger)
        ledger.jobs[index].attempts[ledger.jobs[index].attempts.count - 1].state = .submissionUncertain
        ledger.jobs[index].attempts[ledger.jobs[index].attempts.count - 1].failure = reason
        ledger.jobs[index].attempts[ledger.jobs[index].attempts.count - 1].updatedAt = now
        try AnimationJobStore.save(ledger, to: ledgerURL)
        return ledger.jobs[index]
    }

    private func validateRequest(_ request: AnimationRequest, budget: AnimationBudget, provider: any AnimationJobProvider) throws {
        guard !request.providerID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !request.model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !request.prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !request.referenceAssets.isEmpty,
              Set(request.referenceAssets).count == request.referenceAssets.count,
              request.maximumAttempts > 0 else {
            throw AnimationJobError.invalidRequest("Animation requests require provider, model, prompt, unique approved reference pins, and a positive attempt limit.")
        }
        try requireProvider(provider, for: request)
        let estimate = try knownCost(request)
        guard estimate.currency == budget.maximumCost.currency else { throw AnimationJobError.invalidBudget("Budget and request costs must use the same currency.") }
        guard !budget.budgetID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, budget.maximumCost.amountCents >= 0, budget.maximumInFlight > 0 else { throw AnimationJobError.invalidBudget("Animation budgets require an ID, non-negative cap, and positive concurrency limit.") }
    }

    private func requireProvider(_ provider: any AnimationJobProvider, for request: AnimationRequest) throws {
        guard provider.providerID == request.providerID else { throw AnimationJobError.providerMismatch(expected: request.providerID, actual: provider.providerID) }
    }

    private func requireProvider(_ provider: any AnimationJobProvider, for job: AnimationJob) throws { try requireProvider(provider, for: job.request) }

    private func knownCost(_ request: AnimationRequest) throws -> AnimationCost {
        guard let cost = request.estimatedCost else { throw AnimationJobError.unknownCost }
        guard cost.amountCents >= 0, !cost.currency.isEmpty else { throw AnimationJobError.invalidRequest("Estimated animation cost must be a non-negative amount with currency.") }
        return cost
    }

    private func register(_ budget: AnimationBudget, in ledger: inout AnimationJobLedger) throws {
        if let existing = ledger.budgets.first(where: { $0.budgetID == budget.budgetID }) {
            guard existing == budget else { throw AnimationJobError.invalidBudget("Budget \(budget.budgetID) is immutable once it has jobs.") }
        } else {
            ledger.budgets.append(budget)
        }
    }

    private func admit(_ newCost: AnimationCost, budget: AnimationBudget, ledger: AnimationJobLedger) throws {
        let inFlight = ledger.jobs.filter { job in
            guard job.budgetID == budget.budgetID, let attempt = job.latestAttempt else { return false }
            return attempt.state == .submitting || attempt.state == .submitted || attempt.state == .cancellationRequested || attempt.state == .submissionUncertain
        }.count
        guard inFlight < budget.maximumInFlight else {
            throw AnimationJobError.concurrencyLimit(budgetID: budget.budgetID, maximumInFlight: budget.maximumInFlight)
        }
        let reservation = ledger.jobs.filter { $0.budgetID == budget.budgetID }.flatMap(\.attempts).reduce((amount: Int64(0), overflow: false)) { partial, attempt in
            let (amount, overflow) = partial.amount.addingReportingOverflow(attempt.actualCost?.amountCents ?? attempt.estimatedCost.amountCents)
            return (amount, partial.overflow || overflow)
        }
        let (projected, overflow) = reservation.amount.addingReportingOverflow(newCost.amountCents)
        guard !reservation.overflow, !overflow, projected <= budget.maximumCost.amountCents else {
            throw AnimationJobError.budgetExceeded(budgetID: budget.budgetID, maximumCents: budget.maximumCost.amountCents, projectedCents: reservation.overflow || overflow ? Int64.max : projected)
        }
    }

    private func requestFingerprint(_ request: AnimationRequest) throws -> String {
        struct Canonical: Codable {
            let providerID: String
            let model: String
            let options: [String: String]
            let prompt: String
            let referenceAssets: [AssetIdentity]
            let seed: String?
            let estimatedCost: AnimationCost?
            let maximumAttempts: Int
        }
        let canonical = Canonical(providerID: request.providerID, model: request.model, options: request.options, prompt: request.prompt, referenceAssets: request.referenceAssets, seed: request.seed, estimatedCost: request.estimatedCost, maximumAttempts: request.maximumAttempts)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        return SHA256.hash(data: try encoder.encode(canonical)).map { String(format: "%02x", $0) }.joined()
    }

    private func index(of jobID: String, in ledger: AnimationJobLedger) throws -> Int {
        guard let index = ledger.jobs.firstIndex(where: { $0.jobID == jobID }) else { throw AnimationJobError.jobNotFound(jobID) }
        return index
    }

    private func apply(_ result: AnimationProviderPoll, to attempt: inout AnimationJobAttempt, now: Date) {
        if let actual = result.actualCost, actual.currency == attempt.estimatedCost.currency, actual.amountCents >= 0 { attempt.actualCost = actual }
        attempt.failure = result.failure
        attempt.updatedAt = now
        switch result.state {
        case .queued, .running:
            attempt.state = .submitted
            attempt.nextPollAt = now.addingTimeInterval(nextDelay(for: attempt))
        case .rateLimited:
            attempt.state = .submitted
            attempt.nextPollAt = now.addingTimeInterval(max(0, result.retryAfter ?? nextDelay(for: attempt)))
        case .succeeded:
            attempt.state = .succeeded
            attempt.nextPollAt = nil
        case .failed:
            attempt.state = .failed
            attempt.nextPollAt = nil
        case .cancelled:
            attempt.state = .cancelled
            attempt.nextPollAt = nil
        }
    }

    private func nextDelay(for attempt: AnimationJobAttempt) -> TimeInterval {
        let pollsAlreadyScheduled = attempt.nextPollAt == nil ? 0 : 1
        return min(300, pollBaseDelay * pow(2, Double(pollsAlreadyScheduled)))
    }
}
