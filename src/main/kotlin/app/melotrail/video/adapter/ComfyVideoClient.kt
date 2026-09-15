package app.melotrail.video.adapter

import app.melotrail.video.domain.VideoComfyInputSlot
import app.melotrail.video.domain.VideoComfyOutputBinding
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ComfyUploadBinding(
    val source: Path,
    val uploadFileName: String,
    val slot: VideoComfyInputSlot,
)

data class ComfyClientSubmission(
    val promptId: String,
    val clientId: String,
    val workflow: JsonObject,
    val scalarInputs: Map<VideoComfyInputSlot, JsonPrimitive>,
    val uploads: List<ComfyUploadBinding>,
    val output: VideoComfyOutputBinding,
    val requestId: String,
    val requestFingerprint: String,
    val attemptId: String,
    val ownershipToken: String,
)

sealed interface ComfyClientSubmissionResult {
    data class Accepted(val promptId: String) : ComfyClientSubmissionResult
    data class Rejected(val reason: String) : ComfyClientSubmissionResult
    data class Uncertain(val reason: String) : ComfyClientSubmissionResult
}

data class ComfyApiOutput(
    val nodeId: String,
    val fileName: String,
    val subfolder: String,
    val type: String,
)

sealed interface ComfyClientObservation {
    data class Running(val progressPercent: Int?) : ComfyClientObservation
    data class Completed(val output: ComfyApiOutput) : ComfyClientObservation
    data object Cancelled : ComfyClientObservation
    data class Failed(val reason: String) : ComfyClientObservation
    data class Unknown(val detail: String) : ComfyClientObservation
}

sealed interface ComfyClientCancellation {
    data object Requested : ComfyClientCancellation
    data object ConfirmedStopped : ComfyClientCancellation
    data class Unknown(val detail: String) : ComfyClientCancellation
}

/**
 * Bounded ComfyUI HTTP/WebSocket protocol client. Production construction accepts only V17c's
 * runtime-issued connection; the internal resolver constructor exists for real-socket fixtures.
 */
class ComfyVideoClient private constructor(
    private val httpUri: (ComfyVideoHttpMethod, String) -> URI,
    private val webSocketUri: (String) -> URI,
    private val http: HttpClient,
    private val requestTimeout: Duration,
    private val webSocketWait: Duration,
) : ComfyVideoApi {
    constructor(
        connection: ComfyVideoConnection,
        http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
        requestTimeout: Duration = Duration.ofSeconds(10),
        webSocketWait: Duration = Duration.ofMillis(200),
    ) : this(connection::httpUri, connection::webSocketUri, http, requestTimeout, webSocketWait)

    internal constructor(
        endpoint: ComfyVideoFixtureEndpoint,
        http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
        requestTimeout: Duration = Duration.ofSeconds(10),
        webSocketWait: Duration = Duration.ofMillis(200),
    ) : this(endpoint::httpUri, endpoint::webSocketUri, http, requestTimeout, webSocketWait)

    override fun submit(request: ComfyClientSubmission): ComfyClientSubmissionResult {
        val validation = try {
            validateWorkflow(request.workflow, objectInfo(), request)
        } catch (error: ComfyProtocolRejected) {
            return ComfyClientSubmissionResult.Rejected(error.message ?: "ComfyUI rejected the workflow binding.")
        } catch (error: Exception) {
            // No upload or prompt mutation has occurred.
            return ComfyClientSubmissionResult.Rejected("Could not validate ComfyUI object information: ${useful(error)}")
        }
        if (validation != null) return ComfyClientSubmissionResult.Rejected(validation)

        val uploaded = linkedMapOf<VideoComfyInputSlot, String>()
        try {
            request.uploads.forEach { binding ->
                uploaded[binding.slot] = upload(binding)
            }
        } catch (error: ComfyProtocolRejected) {
            // Uploads are inert files. A prompt has conclusively not been queued yet.
            return ComfyClientSubmissionResult.Rejected(error.message ?: "ComfyUI rejected an input upload.")
        } catch (error: Exception) {
            // No /prompt call has occurred, so this is a conclusive pre-submit rejection.
            return ComfyClientSubmissionResult.Rejected("Could not upload a consumed reference: ${useful(error)}")
        }

        val workflow = try {
            bindWorkflow(request.workflow, request.scalarInputs, uploaded)
        } catch (error: Exception) {
            return ComfyClientSubmissionResult.Rejected("Could not bind the validated ComfyUI workflow: ${useful(error)}")
        }
        val body = buildJsonObject {
            put("prompt", workflow)
            put("client_id", request.clientId)
            put("prompt_id", request.promptId)
            put("extra_data", buildJsonObject {
                put("melotrail_request_id", request.requestId)
                put("melotrail_request_fingerprint", request.requestFingerprint)
                put("melotrail_attempt_id", request.attemptId)
                put("melotrail_ownership_token", request.ownershipToken)
            })
        }
        return try {
            val response = postJson("/prompt", body)
            if (response.statusCode() >= 500) {
                // ComfyUI may enqueue before response handling fails. The UUID correlates work;
                // it is not a server-side deduplication key and must never justify another POST.
                ComfyClientSubmissionResult.Uncertain(protocolError("ComfyUI submission acknowledgement failed", response))
            } else if (response.statusCode() !in 200..299) {
                ComfyClientSubmissionResult.Rejected(protocolError("ComfyUI rejected workflow submission", response))
            } else {
                val returned = parseObject(response.body())["prompt_id"]?.jsonPrimitive?.contentOrNull
                if (returned != request.promptId) {
                    ComfyClientSubmissionResult.Uncertain("ComfyUI did not acknowledge the stable prompt identity.")
                } else ComfyClientSubmissionResult.Accepted(request.promptId)
            }
        } catch (error: Exception) {
            // The request may have reached PromptQueue.put. Never post it again.
            ComfyClientSubmissionResult.Uncertain("ComfyUI submission acknowledgement is ambiguous: ${useful(error)}")
        }
    }

    override fun observe(promptId: String, clientId: String, output: VideoComfyOutputBinding): ComfyClientObservation {
        val event = receiveEvent(clientId, promptId)
        val history = try {
            getHistory(promptId)
        } catch (error: Exception) {
            return ComfyClientObservation.Unknown("ComfyUI history is unavailable: ${useful(error)}")
        }
        parseHistory(history, promptId, output)?.let { return it }
        if (event?.cancelled == true) return ComfyClientObservation.Cancelled
        if (event?.failure != null) return ComfyClientObservation.Failed(event.failure)

        val queue = try {
            getQueue()
        } catch (error: Exception) {
            return ComfyClientObservation.Unknown("ComfyUI queue is unavailable: ${useful(error)}")
        }
        return if (queueContains(queue, promptId)) {
            ComfyClientObservation.Running(event?.progressPercent)
        } else {
            // Absence cannot prove that a prior ambiguous POST did not enqueue or finish elsewhere.
            ComfyClientObservation.Unknown("The stable prompt identity is absent from current history and queue.")
        }
    }

    override fun cancel(promptId: String, acknowledged: Boolean): ComfyClientCancellation {
        val history = runCatching { getHistory(promptId) }.getOrNull()
        if (history != null && historyContains(history, promptId)) {
            if (historyInterrupted(history[promptId] as JsonObject)) return ComfyClientCancellation.ConfirmedStopped
            return ComfyClientCancellation.Unknown("The prompt already has history and must be reconciled before cancellation.")
        }
        val queue = try { getQueue() } catch (error: Exception) {
            return ComfyClientCancellation.Unknown("Could not inspect the owned queue: ${useful(error)}")
        }
        val running = queueContains(queue, promptId, "queue_running")
        val pending = queueContains(queue, promptId, "queue_pending")
        if (!running && !pending) {
            return ComfyClientCancellation.Unknown("The prompt is absent; submission history is inconclusive.")
        }
        if (pending && !running && !acknowledged) {
            // Deleting pending work creates no history. Keep it observable until V16
            // durably acknowledges its identity, then retries the saved cancellation.
            return ComfyClientCancellation.Requested
        }
        return try {
            val response = if (running) {
                postJson("/interrupt", buildJsonObject { put("prompt_id", promptId) })
            } else {
                postJson("/queue", buildJsonObject { put("delete", buildJsonArray { add(JsonPrimitive(promptId)) }) })
            }
            if (response.statusCode() !in 200..299) {
                ComfyClientCancellation.Unknown(protocolError("ComfyUI cancellation failed", response))
            } else {
                val after = runCatching { getQueue() }.getOrNull()
                if (after != null && !queueContains(after, promptId)) {
                    // The prompt may have completed while the interrupt/delete was processed.
                    // Queue absence alone cannot prove cancellation, even after a 200 response.
                    val terminal = getHistory(promptId)[promptId] as? JsonObject
                    when {
                        // Pinned PromptQueue moves pending -> running -> history under one
                        // mutex. After this acknowledged pending item's exact delete succeeds,
                        // queue absence followed by history absence proves that deletion won.
                        terminal == null && pending && !running && acknowledged -> ComfyClientCancellation.ConfirmedStopped
                        terminal == null -> ComfyClientCancellation.Requested
                        historyInterrupted(terminal) -> ComfyClientCancellation.ConfirmedStopped
                        else -> ComfyClientCancellation.Unknown("The prompt has history and must be reconciled after cancellation.")
                    }
                } else ComfyClientCancellation.Requested
            }
        } catch (error: Exception) {
            ComfyClientCancellation.Unknown("ComfyUI cancellation is ambiguous: ${useful(error)}")
        }
    }

    private fun getHistory(promptId: String): JsonObject = getJson("/history/$promptId").also { history ->
        if (promptId in history && history[promptId] !is JsonObject) {
            throw ComfyProtocolRejected("ComfyUI history contains an invalid prompt record.")
        }
    }

    private fun getQueue(): JsonObject = getJson("/queue").also { queue ->
        for (key in listOf("queue_running", "queue_pending")) {
            val entries = queue[key] as? JsonArray
                ?: throw ComfyProtocolRejected("ComfyUI queue response lacks the running or pending list.")
            if (entries.any { ((it as? JsonArray)?.getOrNull(1) as? JsonPrimitive)?.contentOrNull.isNullOrBlank() }) {
                throw ComfyProtocolRejected("ComfyUI queue response contains an invalid prompt identity.")
            }
        }
    }

    private fun objectInfo(): JsonObject = getJson("/object_info")

    private fun validateWorkflow(
        workflow: JsonObject,
        objectInfo: JsonObject,
        request: ComfyClientSubmission,
    ): String? {
        if (workflow.isEmpty() || workflow.size > MAX_NODES) return "ComfyUI API workflow must contain 1..$MAX_NODES nodes."
        val boundSlots = request.scalarInputs.keys + request.uploads.map(ComfyUploadBinding::slot)
        if (boundSlots.distinct().size != boundSlots.size) return "ComfyUI workflow slots must be unique."
        if (request.output.nodeId !in workflow) return "Configured ComfyUI output node '${request.output.nodeId}' is missing."
        boundSlots.firstOrNull { it.nodeId !in workflow }?.let {
            return "Bound ComfyUI node '${it.nodeId}' is missing."
        }
        workflow.forEach { (nodeId, value) ->
            val node = value as? JsonObject ?: return "ComfyUI node '$nodeId' must be an object."
            val classType = node["class_type"]?.jsonPrimitive?.contentOrNull
                ?: return "ComfyUI node '$nodeId' has no class_type."
            val inputs = node["inputs"] as? JsonObject ?: return "ComfyUI node '$nodeId' has no inputs object."
            val definition = objectInfo[classType] as? JsonObject
                ?: return "ComfyUI does not provide required node type '$classType'."
            if (nodeId == request.output.nodeId && definition["output_node"]?.jsonPrimitive?.booleanOrNull != true) {
                return "Configured ComfyUI output node '$nodeId' is not an output node."
            }
            val declared = definition["input"] as? JsonObject ?: JsonObject(emptyMap())
            // Select dynamic branches using the values that will actually be posted, before any upload.
            val effectiveInputs = inputs.toMutableMap()
            request.scalarInputs.filterKeys { it.nodeId == nodeId }.forEach { (slot, value) ->
                effectiveInputs[slot.inputName] = value
            }
            request.uploads.filter { it.slot.nodeId == nodeId }.forEach {
                effectiveInputs[it.slot.inputName] = JsonPrimitive(it.uploadFileName)
            }
            val (allowed, required) = declaredInputNames(nodeId, declared, effectiveInputs)
            val unknown = effectiveInputs.keys - allowed
            if (unknown.isNotEmpty()) return "ComfyUI node '$nodeId' has unsupported inputs: ${unknown.sorted().joinToString()}."
            val missing = required - effectiveInputs.keys
            if (missing.isNotEmpty()) return "ComfyUI node '$nodeId' lacks required inputs: ${missing.sorted().joinToString()}."
        }
        return null
    }

    /** Mirrors pinned ComfyUI _io.py's DynamicCombo/parse_class_inputs wire-name expansion only. */
    private fun declaredInputNames(
        nodeId: String,
        declared: JsonObject,
        inputs: Map<String, JsonElement>,
    ): Pair<Set<String>, Set<String>> {
        val allowed = linkedSetOf<String>()
        val required = linkedSetOf<String>()
        fun malformed(name: String): Nothing =
            throw ComfyProtocolRejected("ComfyUI node '$nodeId' has malformed dynamic input schema at '$name'.")

        fun collect(groups: JsonObject, prefix: String, depth: Int) {
            if (depth > 16 || groups.keys.any { it !in setOf("required", "optional", "hidden") }) malformed(prefix)
            groups.forEach { (category, entries) ->
                val definitions = entries as? JsonObject ?: malformed(prefix)
                definitions.forEach input@{ (name, definition) ->
                    val path = if (prefix.isEmpty()) name else "$prefix.$name"
                    if (!allowed.add(path) || allowed.size > 1024) malformed(path)
                    if (category == "required") required += path
                    val tuple = definition as? JsonArray ?: return@input
                    if ((tuple.firstOrNull() as? JsonPrimitive)?.contentOrNull != "COMFY_DYNAMICCOMBO_V3") return@input
                    if (tuple.size != 2) malformed(path)
                    val options = (tuple[1] as? JsonObject)?.get("options") as? JsonArray ?: malformed(path)
                    if (options.isEmpty()) malformed(path)
                    val branches = linkedMapOf<String, JsonObject>()
                    options.forEach { option ->
                        val branch = option as? JsonObject ?: malformed(path)
                        val key = (branch["key"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: malformed(path)
                        val nested = branch["inputs"] as? JsonObject ?: malformed(path)
                        if (branches.put(key, nested) != null) malformed(path)
                    }
                    val selection = inputs[path] ?: return@input
                    val key = (selection as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                    val selected = branches[key] ?: throw ComfyProtocolRejected(
                        "ComfyUI node '$nodeId' has unsupported dynamic selection at '$path': $selection.",
                    )
                    // _io.py selects a scalar key, then prefixes only that option's nested inputs.
                    // It reconstructs the nested dictionary later, immediately before node execution.
                    collect(selected, path, depth + 1)
                }
            }
        }
        collect(declared, "", 0)
        return allowed to required
    }

    private fun bindWorkflow(
        original: JsonObject,
        scalarInputs: Map<VideoComfyInputSlot, JsonPrimitive>,
        uploads: Map<VideoComfyInputSlot, String>,
    ): JsonObject {
        val replacements: Map<VideoComfyInputSlot, JsonElement> = scalarInputs + uploads.mapValues { JsonPrimitive(it.value) }
        return JsonObject(original.mapValues { (nodeId, value) ->
            val node = value.jsonObject
            val inputs = node["inputs"]!!.jsonObject.toMutableMap()
            replacements.filterKeys { it.nodeId == nodeId }.forEach { (slot, replacement) -> inputs[slot.inputName] = replacement }
            JsonObject(node + ("inputs" to JsonObject(inputs)))
        })
    }

    private fun upload(binding: ComfyUploadBinding): String {
        val boundary = "Melotrail${System.nanoTime().toString(16)}"
        val prefix = ("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"${binding.uploadFileName}\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n").toByteArray()
        val suffix = ("\r\n--$boundary\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\ninput\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\nfalse\r\n--$boundary--\r\n").toByteArray()
        val body = HttpRequest.BodyPublishers.concat(
            HttpRequest.BodyPublishers.ofByteArray(prefix),
            HttpRequest.BodyPublishers.ofFile(binding.source),
            HttpRequest.BodyPublishers.ofByteArray(suffix),
        )
        val response = send(
            HttpRequest.newBuilder(httpUri(ComfyVideoHttpMethod.POST, "/upload/image"))
                .timeout(requestTimeout)
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(body)
                .build(),
        )
        if (response.statusCode() !in 200..299) throw ComfyProtocolRejected(protocolError("ComfyUI rejected image upload", response))
        val uploaded = parseObject(response.body())
        val name = uploaded["name"]?.jsonPrimitive?.contentOrNull ?: throw ComfyProtocolRejected("ComfyUI upload response has no name.")
        val subfolder = uploaded["subfolder"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val type = uploaded["type"]?.jsonPrimitive?.contentOrNull
        if (type != "input" || !SAFE_FILE.matches(name) || subfolder.length > MAX_SUBFOLDER_LENGTH ||
            !SAFE_SUBFOLDER.matches(subfolder) || subfolder.split('/').any { it in setOf(".", "..") }
        ) {
            throw ComfyProtocolRejected("ComfyUI returned an unsafe upload identity.")
        }
        return if (subfolder.isEmpty()) name else "$subfolder/$name"
    }

    private fun parseHistory(history: JsonObject, promptId: String, output: VideoComfyOutputBinding): ComfyClientObservation? {
        val record = history[promptId] as? JsonObject ?: return null
        val status = record["status"] as? JsonObject
        val statusText = status?.get("status_str")?.jsonPrimitive?.contentOrNull
        val completed = status?.get("completed")?.jsonPrimitive?.booleanOrNull
        if (historyInterrupted(record)) return ComfyClientObservation.Cancelled
        if (statusText == "error") return ComfyClientObservation.Failed(historyFailure(record))
        if (completed != true && statusText != "success") return ComfyClientObservation.Running(null)
        val outputs = record["outputs"] as? JsonObject
            ?: return ComfyClientObservation.Failed("ComfyUI history completed without outputs.")
        val node = outputs[output.nodeId] as? JsonObject
            ?: return ComfyClientObservation.Failed("ComfyUI history lacks configured output node '${output.nodeId}'.")
        val entries = node["images"] as? JsonArray
            ?: return ComfyClientObservation.Failed("Configured ComfyUI output node has no image/video files.")
        val candidates = entries.mapNotNull { it as? JsonObject }.mapNotNull { item ->
            val name = item["filename"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val folder = item["subfolder"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val type = item["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ComfyApiOutput(output.nodeId, name, folder, type)
        }.filter { candidate -> candidate.fileName.substringAfterLast('.', "") in output.allowedExtensions }
        if (candidates.size != 1) return ComfyClientObservation.Failed("Configured ComfyUI output must resolve to exactly one allowed file.")
        val candidate = candidates.single()
        if (candidate.type != "output" || !SAFE_FILE.matches(candidate.fileName) ||
            candidate.subfolder.length > MAX_SUBFOLDER_LENGTH || !SAFE_SUBFOLDER.matches(candidate.subfolder) ||
            candidate.subfolder.split('/').any { it in setOf(".", "..") }
        ) return ComfyClientObservation.Failed("ComfyUI history returned an unsafe output identity.")
        return ComfyClientObservation.Completed(candidate)
    }

    private fun historyInterrupted(record: JsonObject): Boolean {
        val status = record["status"] as? JsonObject ?: return false
        if ((status["status_str"] as? JsonPrimitive)?.contentOrNull != "error") return false
        return (status["messages"] as? JsonArray).orEmpty().any { message ->
            ((message as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull == "execution_interrupted"
        }
    }

    private fun historyFailure(record: JsonObject): String {
        val messages = record["status"]?.jsonObject?.get("messages") as? JsonArray ?: return "ComfyUI workflow execution failed."
        messages.forEach { message ->
            val pair = message as? JsonArray ?: return@forEach
            if (pair.firstOrNull()?.jsonPrimitive?.contentOrNull == "execution_error") {
                val detail = (pair.getOrNull(1) as? JsonObject)?.get("exception_message")?.jsonPrimitive?.contentOrNull
                if (!detail.isNullOrBlank()) return detail.take(MAX_ERROR)
            }
        }
        return "ComfyUI workflow execution failed."
    }

    private fun receiveEvent(clientId: String, promptId: String): SocketEvent? {
        val listener = EventListener(promptId)
        val socket = try {
            http.newWebSocketBuilder().connectTimeout(requestTimeout).buildAsync(webSocketUri(clientId), listener)
                .get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            return null
        }
        return try {
            listener.result.get(webSocketWait.toMillis().coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        } finally {
            socket.abort()
        }
    }

    private fun getJson(path: String): JsonObject {
        val response = send(HttpRequest.newBuilder(httpUri(ComfyVideoHttpMethod.GET, path)).timeout(requestTimeout).GET().build())
        if (response.statusCode() !in 200..299) throw ComfyProtocolRejected(protocolError("ComfyUI read failed", response))
        return parseObject(response.body())
    }

    private fun postJson(path: String, body: JsonObject): ApiResponse = send(
        HttpRequest.newBuilder(httpUri(ComfyVideoHttpMethod.POST, path)).timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build(),
    )

    private fun send(request: HttpRequest): ApiResponse {
        val body = BoundedBodySubscriber()
        val response = http.sendAsync(request, HttpResponse.BodyHandler { body })
        return try {
            // Unlike ofInputStream, this future completes only after the whole capped body.
            val completed = response.get(requestTimeout.toNanos(), TimeUnit.NANOSECONDS)
            ApiResponse(completed.statusCode(), completed.body())
        } catch (error: Exception) {
            body.cancel(error)
            response.cancel(true)
            if (error is InterruptedException) Thread.currentThread().interrupt()
            throw error
        }
    }

    private class BoundedBodySubscriber : HttpResponse.BodySubscriber<ByteArray> {
        private val result = CompletableFuture<ByteArray>()
        private val bytes = ByteArrayOutputStream()
        private var subscription: Flow.Subscription? = null
        override fun getBody(): CompletionStage<ByteArray> = result

        @Synchronized
        override fun onSubscribe(value: Flow.Subscription) {
            if (result.isDone || subscription != null) value.cancel()
            else { subscription = value; value.request(1) }
        }

        @Synchronized
        override fun onNext(items: List<ByteBuffer>) {
            if (result.isDone) return
            for (item in items) {
                if (item.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    cancel(ComfyProtocolRejected("ComfyUI response exceeded $MAX_RESPONSE_BYTES bytes."))
                    return
                }
                val part = ByteArray(item.remaining())
                item.get(part)
                bytes.write(part)
            }
            subscription?.request(1)
        }

        @Synchronized
        override fun onComplete() {
            if (!result.isDone) result.complete(bytes.toByteArray())
            bytes.reset()
        }

        override fun onError(error: Throwable) = cancel(error)

        @Synchronized
        fun cancel(error: Throwable) {
            result.completeExceptionally(error)
            bytes.reset()
            subscription?.cancel()
        }
    }

    private fun parseObject(bytes: ByteArray): JsonObject = try {
        JSON.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
    } catch (error: Exception) {
        throw ComfyProtocolRejected("ComfyUI returned invalid JSON.", error)
    }

    private fun protocolError(prefix: String, response: ApiResponse): String {
        val detail = response.body().toString(Charsets.UTF_8).replace(Regex("\\s+"), " ").take(500)
        return "$prefix (${response.statusCode()}): $detail"
    }

    private fun queueContains(queue: JsonObject, promptId: String): Boolean =
        queueContains(queue, promptId, "queue_running") || queueContains(queue, promptId, "queue_pending")

    private fun queueContains(queue: JsonObject, promptId: String, key: String): Boolean =
        (queue[key] as? JsonArray).orEmpty().any { entry ->
            ((entry as? JsonArray)?.getOrNull(1) as? JsonPrimitive)?.contentOrNull == promptId
        }

    private fun historyContains(history: JsonObject, promptId: String): Boolean = history[promptId] is JsonObject

    private data class SocketEvent(val progressPercent: Int? = null, val failure: String? = null, val cancelled: Boolean = false)
    private data class ApiResponse(val status: Int, val bytes: ByteArray) {
        fun statusCode(): Int = status
        fun body(): ByteArray = bytes
    }

    private class EventListener(private val promptId: String) : WebSocket.Listener {
        val result = CompletableFuture<SocketEvent>()
        private val text = StringBuilder()

        override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (text.length > MAX_SOCKET_EVENT_BYTES) {
                result.complete(SocketEvent(failure = "ComfyUI WebSocket event exceeded the bound."))
                return null
            }
            if (last) {
                val event = runCatching { JSON.parseToJsonElement(text.toString()).jsonObject }.getOrNull()
                text.setLength(0)
                val dataObject = event?.get("data") as? JsonObject
                if (dataObject?.get("prompt_id")?.jsonPrimitive?.contentOrNull == promptId) {
                    when (event["type"]?.jsonPrimitive?.contentOrNull) {
                        "progress" -> {
                            val value = dataObject["value"]?.jsonPrimitive?.intOrNull
                            val max = dataObject["max"]?.jsonPrimitive?.intOrNull
                            result.complete(SocketEvent(if (value != null && max != null && max > 0) (value * 100 / max).coerceIn(0, 100) else null))
                        }
                        "execution_interrupted" -> result.complete(SocketEvent(cancelled = true))
                        "execution_error" -> result.complete(SocketEvent(failure =
                            dataObject["exception_message"]?.jsonPrimitive?.contentOrNull?.take(MAX_ERROR)
                                ?: "ComfyUI reported a node execution error."))
                        "executing", "executed", "execution_success" -> result.complete(SocketEvent())
                    }
                }
            }
            webSocket.request(1)
            return null
        }

        override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*>? {
            webSocket.request(1)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) { result.completeExceptionally(error) }
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        private const val MAX_NODES = 512
        private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        private const val MAX_SOCKET_EVENT_BYTES = 256 * 1024
        private const val MAX_ERROR = 2_000
        private const val MAX_SUBFOLDER_LENGTH = 160
        private val SAFE_FILE = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,191}")
        private val SAFE_SUBFOLDER = Regex("(?:[A-Za-z0-9][A-Za-z0-9._-]{0,127})(?:/[A-Za-z0-9][A-Za-z0-9._-]{0,127}){0,7}|^$")
    }
}

/** Socket fixture seam; production code cannot create a client from raw endpoint URIs. */
internal interface ComfyVideoFixtureEndpoint {
    fun httpUri(method: ComfyVideoHttpMethod, operationPath: String): URI
    fun webSocketUri(clientId: String): URI
}

interface ComfyVideoApi {
    fun submit(request: ComfyClientSubmission): ComfyClientSubmissionResult
    fun observe(promptId: String, clientId: String, output: VideoComfyOutputBinding): ComfyClientObservation
    fun cancel(promptId: String, acknowledged: Boolean): ComfyClientCancellation
}

private class ComfyProtocolRejected(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

private fun useful(error: Throwable): String = error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName
