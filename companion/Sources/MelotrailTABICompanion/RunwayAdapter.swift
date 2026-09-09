import CryptoKit
import ImageIO
import UniformTypeIdentifiers
import Foundation

/// The fixed, reviewed Runway image-to-video request. Keeping this constructor
/// narrow prevents a caller from silently selecting a different model, output
/// size, duration, or generated-audio option.
public enum RunwayPreset {
    public static let providerID = "runway-dev"
    public static let apiVersion = "2024-11-06"
    public static let model = "gen4.5"
    public static let ratio = "1280:720"
    public static let durationSeconds = "5"

    public static func request(prompt: String, referenceAsset: AssetIdentity, estimatedCost: AnimationCost?, maximumAttempts: Int = 2) -> AnimationRequest {
        AnimationRequest(providerID: providerID, model: model, options: ["duration": durationSeconds, "ratio": ratio], prompt: prompt, referenceAssets: [referenceAsset], estimatedCost: estimatedCost, maximumAttempts: maximumAttempts)
    }
}

/// Credentials are loaded from process configuration at the boundary and are
/// never Codable or retained in the job ledger.
public struct RunwayCredentials: Sendable {
    private let token: String
    public init(environment: [String: String] = ProcessInfo.processInfo.environment) throws {
        guard let value = environment["RUNWAYML_API_SECRET"], !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw RunwayProviderError.missingCredentials }
        token = value
    }
    fileprivate var authorizationValue: String { "Bearer \(token)" }
}

public struct ProviderHTTPResponse: Sendable {
    public let statusCode: Int
    public let headers: [String: String]
    public let body: Data
    public init(statusCode: Int, headers: [String: String] = [:], body: Data = Data()) { self.statusCode = statusCode; self.headers = headers; self.body = body }
}

/// A testable I/O port. The MIDI app never supplies this implementation.
public protocol ProviderHTTPTransport: Sendable {
    func execute(_ request: URLRequest, timeout: TimeInterval, maximumBytes: Int) throws -> ProviderHTTPResponse
}

public extension ProviderHTTPTransport {
    func execute(_ request: URLRequest, timeout: TimeInterval) throws -> ProviderHTTPResponse {
        try execute(request, timeout: timeout, maximumBytes: 4 * 1024 * 1024)
    }
}

/// Each request owns an ephemeral delegate and bounded buffer. No credentials,
/// cookies or cache are shared with a separate output-host request.
public final class URLSessionProviderHTTPTransport: ProviderHTTPTransport, @unchecked Sendable {
    private let configuration: URLSessionConfiguration
    public init(session: URLSession = .shared) { self.configuration = session.configuration }

    public func execute(_ request: URLRequest, timeout: TimeInterval, maximumBytes: Int) throws -> ProviderHTTPResponse {
        guard request.url?.scheme == "https", timeout.isFinite, timeout > 0, maximumBytes > 0 else { throw URLError(.badURL) }
        let collector = BoundedHTTPCollector(maximumBytes: maximumBytes)
        let config = configuration.copy() as! URLSessionConfiguration
        config.httpCookieStorage = nil
        config.httpShouldSetCookies = false
        config.urlCredentialStorage = nil
        config.urlCache = nil
        config.httpAdditionalHeaders = nil
        let session = URLSession(configuration: config, delegate: collector, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var bounded = request
        bounded.timeoutInterval = timeout
        let task = session.dataTask(with: bounded)
        task.resume()
        guard collector.completed.wait(timeout: .now() + timeout) == .success else {
            task.cancel()
            throw URLError(.timedOut)
        }
        return try collector.result()
    }
}

private final class BoundedHTTPCollector: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    let completed = DispatchSemaphore(value: 0)
    private let lock = NSLock()
    private let maximumBytes: Int
    private var body = Data()
    private var response: HTTPURLResponse?
    private var failure: Error?

    init(maximumBytes: Int) { self.maximumBytes = maximumBytes }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        guard request.url?.scheme == "https" else {
            lock.lock(); failure = URLError(.secureConnectionFailed); lock.unlock()
            completionHandler(nil)
            task.cancel()
            return
        }
        let original = task.originalRequest
        let originChanged = request.url?.host != original?.url?.host
            || (request.url?.port ?? 443) != (original?.url?.port ?? 443)
        if originChanged && original?.value(forHTTPHeaderField: "Authorization") != nil {
            lock.lock(); failure = URLError(.secureConnectionFailed); lock.unlock()
            completionHandler(nil)
            task.cancel()
            return
        }
        completionHandler(request)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        lock.lock()
        if response.url?.scheme != "https" { failure = URLError(.secureConnectionFailed) }
        if response.expectedContentLength > Int64(maximumBytes) { failure = URLError(.dataLengthExceedsMaximum) }
        self.response = response as? HTTPURLResponse
        let allowed = failure == nil && self.response != nil
        lock.unlock()
        completionHandler(allowed ? .allow : .cancel)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        lock.lock()
        let exceedsLimit = data.count > maximumBytes - body.count
        if exceedsLimit { failure = URLError(.dataLengthExceedsMaximum) }
        else if failure == nil { body.append(data) }
        let cancel = failure != nil
        lock.unlock()
        if cancel { dataTask.cancel() }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        if failure == nil { failure = error }
        lock.unlock()
        completed.signal()
    }

    func result() throws -> ProviderHTTPResponse {
        lock.lock(); defer { lock.unlock() }
        if let failure { throw failure }
        guard let response else { throw URLError(.badServerResponse) }
        let headers = response.allHeaderFields.reduce(into: [String: String]()) { values, entry in
            guard let key = entry.key as? String else { return }
            values[key] = String(describing: entry.value)
        }
        return ProviderHTTPResponse(statusCode: response.statusCode, headers: headers, body: body)
    }
}

public enum RunwayProviderError: Error, Equatable, LocalizedError, Sendable {
    case missingCredentials
    case invalidRequest(String)
    case timedOut
    case rateLimited(retryAfter: TimeInterval?)
    case rejected(statusCode: Int)
    case invalidResponse
    public var errorDescription: String? {
        switch self {
        case .missingCredentials: "Runway credentials are unavailable; set RUNWAYML_API_SECRET in secure process configuration."
        case .invalidRequest(let message): message
        case .timedOut: "Runway request timed out; the submission outcome may be uncertain."
        case .rateLimited: "Runway rate limited this request."
        case .rejected(let status): "Runway rejected the request with HTTP status \(status)."
        case .invalidResponse: "Runway returned an invalid response."
        }
    }
}

/// The reviewed Runway REST contract translated to the V03a provider port.
/// It sends one approved still as a data URI and never sends a soundtrack.
public final class RunwayProvider: AnimationJobProvider, @unchecked Sendable {
    public let providerID = RunwayPreset.providerID
    private let credentials: RunwayCredentials
    private let assetLibrary: AssetLibrary
    private let transport: any ProviderHTTPTransport
    private let baseURL: URL
    private let timeout: TimeInterval

    public init(credentials: RunwayCredentials, assetLibrary: AssetLibrary, transport: any ProviderHTTPTransport = URLSessionProviderHTTPTransport(), baseURL: URL = URL(string: "https://api.dev.runwayml.com")!, timeout: TimeInterval = 30) throws {
        guard baseURL.scheme == "https", baseURL.host == "api.dev.runwayml.com",
              baseURL.user == nil, baseURL.password == nil,
              baseURL.port == nil || baseURL.port == 443,
              baseURL.path.isEmpty || baseURL.path == "/",
              baseURL.query == nil, baseURL.fragment == nil,
              timeout > 0, timeout.isFinite else {
            throw RunwayProviderError.invalidRequest("Runway requires the canonical api.dev.runwayml.com HTTPS endpoint and a positive finite timeout.")
        }
        self.credentials = credentials; self.assetLibrary = assetLibrary; self.transport = transport; self.baseURL = baseURL; self.timeout = timeout
    }

    public func submit(request: AnimationRequest, identity: AnimationSubmissionIdentity) throws -> String {
        try prepareSubmission(request: request)(identity)
    }

    public func prepareSubmission(request: AnimationRequest) throws -> @Sendable (AnimationSubmissionIdentity) throws -> String {
        try validate(request: request)
        let approved = try assetLibrary.validatedApprovedAssets(pinned: request.referenceAssets)
        guard let still = approved.first, (still.kind == .image || still.kind == .reference) else { throw RunwayProviderError.invalidRequest("Runway requires exactly one approved still-image reference.") }
        let source = assetLibrary.rootURL.appendingPathComponent(still.relativeMediaPath)
        let handle = try FileHandle(forReadingFrom: source)
        defer { try? handle.close() }
        let image = try handle.read(upToCount: Self.maximumImageBytes + 1) ?? Data()
        guard image.count <= Self.maximumImageBytes else { throw RunwayProviderError.invalidRequest("Runway image data URI exceeds 5 MB.") }
        let digest = SHA256.hash(data: image).map { String(format: "%02x", $0) }.joined()
        guard digest == still.sha256 else { throw RunwayProviderError.invalidRequest("Runway reference bytes changed after approval.") }
        let body = RunwaySubmission(model: request.model, promptImage: try dataURI(for: image, source: source), promptText: request.prompt, ratio: RunwayPreset.ratio, duration: Int(RunwayPreset.durationSeconds)!)
        var http = try authenticatedRequest(path: "v1/image_to_video", method: "POST")
        http.setValue("application/json", forHTTPHeaderField: "Content-Type")
        // Runway does not document an idempotency header. V03a's durable
        // identity remains authoritative rather than inventing an API field.
        http.httpBody = try JSONEncoder().encode(body)
        // Freeze the exact verified payload before the coordinator reserves a
        // paid attempt. No source file is reopened after durable admission.
        let preparedHTTP = http
        return { [self] _ in
            let response = try execute(preparedHTTP)
            guard (200..<300).contains(response.statusCode) else { throw failure(for: response) }
            guard let taskID = try decodedTask(from: response.body).id, !taskID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw RunwayProviderError.invalidResponse }
            return taskID
        }
    }

    public func poll(providerJobID: String) throws -> AnimationProviderPoll {
        let response = try execute(try authenticatedRequest(path: "v1/tasks/\(safePathComponent(providerJobID))", method: "GET"))
        if response.statusCode == 429 { return AnimationProviderPoll(state: .rateLimited, retryAfter: retryAfter(response.headers)) }
        guard (200..<300).contains(response.statusCode) else { throw failure(for: response) }
        let task = try decodedTask(from: response.body)
        switch task.status.uppercased() {
        case "PENDING", "QUEUED": return AnimationProviderPoll(state: .queued)
        case "RUNNING", "THROTTLED": return AnimationProviderPoll(state: .running)
        case "SUCCEEDED", "SUCCESS": return AnimationProviderPoll(state: .succeeded)
        case "CANCELLED", "CANCELED": return AnimationProviderPoll(state: .cancelled)
        case "FAILED", "ERROR": return AnimationProviderPoll(state: .failed, failure: "Runway reported a failed task.")
        default: throw RunwayProviderError.invalidResponse
        }
    }

    public func cancel(providerJobID: String) throws -> AnimationProviderPoll {
        let response = try execute(try authenticatedRequest(path: "v1/tasks/\(safePathComponent(providerJobID))", method: "DELETE"))
        if response.statusCode == 429 { return AnimationProviderPoll(state: .rateLimited, retryAfter: retryAfter(response.headers)) }
        guard (200..<300).contains(response.statusCode) else { throw failure(for: response) }
        return AnimationProviderPoll(state: .cancelled)
    }

    /// Only HTTPS task outputs are exposed. The caller must quarantine selected
    /// bytes before importing them; a task response is never an asset record.
    public func outputURLs(providerJobID: String) throws -> [URL] {
        let response = try execute(try authenticatedRequest(path: "v1/tasks/\(safePathComponent(providerJobID))", method: "GET"))
        guard (200..<300).contains(response.statusCode) else { throw failure(for: response) }
        let task = try decodedTask(from: response.body)
        guard ["SUCCEEDED", "SUCCESS"].contains(task.status.uppercased()) else { throw RunwayProviderError.invalidResponse }
        let urls = (task.output ?? []).compactMap(URL.init(string:)).filter { $0.scheme == "https" }
        guard !urls.isEmpty else { throw RunwayProviderError.invalidResponse }
        return urls
    }

    private func authenticatedRequest(path: String, method: String) throws -> URLRequest {
        guard let url = URL(string: path, relativeTo: baseURL)?.absoluteURL else { throw RunwayProviderError.invalidRequest("Invalid Runway endpoint.") }
        var request = URLRequest(url: url); request.httpMethod = method
        request.setValue(credentials.authorizationValue, forHTTPHeaderField: "Authorization")
        request.setValue(RunwayPreset.apiVersion, forHTTPHeaderField: "X-Runway-Version")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        return request
    }

    private func execute(_ request: URLRequest) throws -> ProviderHTTPResponse {
        do { return try transport.execute(request, timeout: timeout) }
        catch let error as RunwayProviderError { throw error }
        catch { throw RunwayProviderError.timedOut } // Never interpolate possibly secret-bearing transport text.
    }

    private static let maximumImageBytes = ((5_000_000 - 32) / 4) * 3

    public func validate(request: AnimationRequest) throws {
        guard request.providerID == providerID, request.model == RunwayPreset.model, request.options == ["duration": RunwayPreset.durationSeconds, "ratio": RunwayPreset.ratio], request.referenceAssets.count == 1, !request.prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw RunwayProviderError.invalidRequest("Runway requests must use the reviewed gen4.5, 1280:720, five-second image-to-video preset.") }
        let assets = try assetLibrary.validatedApprovedAssets(pinned: request.referenceAssets)
        guard let still = assets.first, still.kind == .image || still.kind == .reference else {
            throw RunwayProviderError.invalidRequest("Runway requires an approved still image.")
        }
        let ratio = Double(still.geometry.width) / Double(still.geometry.height)
        guard ratio >= 0.5, ratio <= 2 else { throw RunwayProviderError.invalidRequest("Runway still-image aspect ratio must be between 0.5 and 2.0.") }
        let source = assetLibrary.rootURL.appendingPathComponent(still.relativeMediaPath)
        let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? Int.max
        guard size <= Self.maximumImageBytes else { throw RunwayProviderError.invalidRequest("Runway image data URI exceeds 5 MB.") }
        guard ["png", "jpg", "jpeg", "webp"].contains(source.pathExtension.lowercased()) else { throw RunwayProviderError.invalidRequest("Runway requires PNG, JPEG, or WebP.") }
    }

    private func dataURI(for data: Data, source: URL) throws -> String {
        let mime: String
        switch source.pathExtension.lowercased() { case "png": mime = "image/png"; case "jpg", "jpeg": mime = "image/jpeg"; case "webp": mime = "image/webp"; default: throw RunwayProviderError.invalidRequest("Runway reference still must be PNG, JPEG, or WebP.") }
        guard let decoded = CGImageSourceCreateWithData(data as CFData, nil), let type = CGImageSourceGetType(decoded) else {
            throw RunwayProviderError.invalidRequest("Runway reference bytes are not a readable image.")
        }
        let actualType = type as String
        let actualMIME = actualType == UTType.png.identifier ? "image/png"
            : actualType == UTType.jpeg.identifier ? "image/jpeg"
            : actualType == UTType.webP.identifier ? "image/webp" : nil
        guard actualMIME == mime else { throw RunwayProviderError.invalidRequest("Runway reference file extension does not match its encoded image type.") }
        return "data:\(mime);base64,\(data.base64EncodedString())"
    }

    private func safePathComponent(_ value: String) -> String {
        var allowed = CharacterSet.urlPathAllowed
        allowed.remove(charactersIn: "/")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? ""
    }
    private func failure(for response: ProviderHTTPResponse) -> RunwayProviderError { response.statusCode == 429 ? .rateLimited(retryAfter: retryAfter(response.headers)) : .rejected(statusCode: response.statusCode) }
    private func retryAfter(_ headers: [String: String]) -> TimeInterval? { headers.first(where: { $0.key.caseInsensitiveCompare("Retry-After") == .orderedSame }).flatMap { TimeInterval($0.value) }.flatMap { $0 >= 0 && $0.isFinite ? $0 : nil } }
    private func decodedTask(from data: Data) throws -> RunwayTaskResponse { do { return try JSONDecoder().decode(RunwayTaskResponse.self, from: data) } catch { throw RunwayProviderError.invalidResponse } }
}

private struct RunwaySubmission: Encodable { let model: String; let promptImage: String; let promptText: String; let ratio: String; let duration: Int }
private struct RunwayTaskResponse: Decodable {
    let id: String?
    let status: String
    let output: [String]?

    private enum CodingKeys: String, CodingKey { case id, status, output }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decodeIfPresent(String.self, forKey: .id)
        status = try values.decodeIfPresent(String.self, forKey: .status) ?? ""
        output = try values.decodeIfPresent([String].self, forKey: .output)
    }
}
