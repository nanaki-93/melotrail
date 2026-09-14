package app.melotrail.video

import app.melotrail.video.adapter.ComfyClientObservation
import app.melotrail.video.adapter.ComfyClientCancellation
import app.melotrail.video.adapter.ComfyClientSubmission
import app.melotrail.video.adapter.ComfyClientSubmissionResult
import app.melotrail.video.adapter.ComfyUploadBinding
import app.melotrail.video.adapter.ComfyVideoClient
import app.melotrail.video.adapter.ComfyVideoFixtureEndpoint
import app.melotrail.video.adapter.ComfyVideoHttpMethod
import app.melotrail.video.domain.VideoComfyInputSlot
import app.melotrail.video.domain.VideoComfyOutputBinding
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ComfyVideoClientTest {
    @Test
    fun `typed workflow validates uploads only consumed images and submits stable prompt identity`() {
        SocketComfyServer().use { server ->
            server.response.set { request -> when (request.path) {
                "/object_info" -> json(OBJECT_INFO)
                "/upload/image" -> json("""{"name":"subject.png","subfolder":"","type":"input"}""")
                "/prompt" -> json("""{"prompt_id":"11111111-2222-3333-4444-555555555555","number":1}""")
                else -> json("{}", 404)
            } }
            val reference = createTempDirectory("comfy-client").resolve("owned.png")
            Files.write(reference, byteArrayOf(1, 2, 3, 4))
            val promptSlot = VideoComfyInputSlot("1", "text")
            val referenceSlot = VideoComfyInputSlot("2", "image")
            val result = server.client().submit(
                ComfyClientSubmission(
                    promptId = PROMPT_ID,
                    clientId = "client-1",
                    workflow = workflow(),
                    scalarInputs = mapOf(promptSlot to JsonPrimitive("../../workflow.json is prose")),
                    uploads = listOf(ComfyUploadBinding(reference, "subject.png", referenceSlot)),
                    output = VideoComfyOutputBinding("3", setOf("mp4")),
                    requestId = "request-1",
                    requestFingerprint = "a".repeat(64),
                    attemptId = "attempt-1",
                    ownershipToken = "owner-1",
                ),
            )

            assertEquals(PROMPT_ID, assertIs<ComfyClientSubmissionResult.Accepted>(result).promptId)
            assertEquals(listOf("/object_info", "/upload/image", "/prompt"), server.requests.map { it.path })
            assertEquals(1, server.requests.count { it.path == "/upload/image" })
            val posted = server.requests.single { it.path == "/prompt" }.body.toString(StandardCharsets.UTF_8)
            assertTrue(posted.contains("../../workflow.json is prose"))
            assertTrue(posted.contains("\"prompt_id\":\"$PROMPT_ID\""))
            assertTrue(posted.contains("\"image\":\"subject.png\""))
        }
    }

    @Test
    fun `unsupported slot fails before upload or inference`() {
        SocketComfyServer().use { server ->
            server.response.set { json(OBJECT_INFO) }
            val result = server.client().submit(
                submission(workflow(), scalar = mapOf(VideoComfyInputSlot("1", "not_declared") to JsonPrimitive("text"))),
            )
            assertTrue(assertIs<ComfyClientSubmissionResult.Rejected>(result).reason.contains("unsupported"))
            assertEquals(listOf("/object_info"), server.requests.map { it.path })
        }
    }

    @Test
    fun `executed websocket event is not success without validated history output`() {
        SocketComfyServer().use { server ->
            server.webSocketEvent.set("""{"type":"executed","data":{"prompt_id":"$PROMPT_ID","node":"3"}}""")
            server.response.set { request -> when (request.path) {
                "/history/$PROMPT_ID" -> json("{}")
                "/queue" -> json("""{"queue_running":[],"queue_pending":[]}""")
                else -> json("{}", 404)
            } }
            val result = server.client().observe(PROMPT_ID, "client-1", VideoComfyOutputBinding("3", setOf("mp4")))
            assertIs<ComfyClientObservation.Unknown>(result)
        }
    }

    @Test
    fun `websocket progress reconciles queue and late history validates exact output node`() {
        SocketComfyServer().use { server ->
            server.webSocketEvent.set("""{"type":"progress","data":{"prompt_id":"$PROMPT_ID","value":2,"max":5}}""")
            server.response.set { request -> when (request.path) {
                "/history/$PROMPT_ID" -> json("{}")
                "/queue" -> json("""{"queue_running":[[1,"$PROMPT_ID",{},{}]],"queue_pending":[]}""")
                else -> json("{}", 404)
            } }
            assertEquals(40, assertIs<ComfyClientObservation.Running>(
                server.client().observe(PROMPT_ID, "client-1", VideoComfyOutputBinding("3", setOf("mp4"))),
            ).progressPercent)

            server.webSocketEvent.set("""{"type":"executed","data":{"prompt_id":"$PROMPT_ID","node":"3"}}""")
            server.response.set { request -> when (request.path) {
                "/history/$PROMPT_ID" -> json("""{"$PROMPT_ID":{"status":{"status_str":"success","completed":true,"messages":[]},"outputs":{"3":{"images":[{"filename":"take_00001_.mp4","subfolder":"clips","type":"output"}],"animated":[true]}}}}""")
                else -> json("{}", 404)
            } }
            val completed = assertIs<ComfyClientObservation.Completed>(
                server.client().observe(PROMPT_ID, "client-1", VideoComfyOutputBinding("3", setOf("mp4"))),
            )
            assertEquals("take_00001_.mp4", completed.output.fileName)
            assertEquals("clips", completed.output.subfolder)
        }
    }

    @Test
    fun `cancellation always targets one prompt and ambiguous absence stays unknown`() {
        SocketComfyServer().use { server ->
            server.response.set { request -> when (request.path) {
                "/history/$PROMPT_ID" -> json("{}")
                "/queue" -> json("""{"queue_running":[[1,"$PROMPT_ID",{},{}]],"queue_pending":[]}""")
                "/interrupt" -> json("{}")
                else -> json("{}", 404)
            } }
            val client = server.client()
            assertEquals(ComfyClientCancellation.Requested, client.cancel(PROMPT_ID, acknowledged = true))
            val body = server.requests.single { it.path == "/interrupt" }.body.toString(StandardCharsets.UTF_8)
            assertEquals("{\"prompt_id\":\"$PROMPT_ID\"}", body)
            assertTrue(server.requests.none { it.path == "/queue" && it.method == "POST" })
            server.response.set { request -> if (request.path == "/queue")
                json("""{"queue_running":[],"queue_pending":[]}""") else json("{}") }
            assertIs<ComfyClientCancellation.Unknown>(client.cancel(PROMPT_ID, acknowledged = true))
            assertIs<ComfyClientObservation.Unknown>(client.observe(PROMPT_ID, "client-1", OUTPUT))
        }
    }

    @Test
    fun `invalid acknowledgement is ambiguous after one prompt post`() {
        SocketComfyServer().use { server ->
            server.response.set { request -> when (request.path) {
                "/object_info" -> json(OBJECT_INFO)
                "/prompt" -> json("{}")
                else -> json("{}", 404)
            } }
            val result = server.client().submit(
                submission(workflow(), mapOf(VideoComfyInputSlot("1", "text") to JsonPrimitive("plain prompt"))),
            )
            assertIs<ComfyClientSubmissionResult.Uncertain>(result)
            assertEquals(1, server.requests.count { it.path == "/prompt" })
        }
    }

    @Test
    fun `history node error is terminal failure even after reconnect`() {
        SocketComfyServer().use { server ->
            server.webSocketEvent.set("""{"type":"execution_error","data":{"prompt_id":"$PROMPT_ID","exception_message":"node exploded"}}""")
            server.response.set { request -> when (request.path) {
                "/history/$PROMPT_ID" -> json("""{"$PROMPT_ID":{"status":{"status_str":"error","completed":true,"messages":[["execution_error",{"exception_message":"node exploded"}]]},"outputs":{}}}""")
                else -> json("{}", 404)
            } }
            val failed = assertIs<ComfyClientObservation.Failed>(
                server.client().observe(PROMPT_ID, "client-1", VideoComfyOutputBinding("3", setOf("mp4"))),
            )
            assertEquals("node exploded", failed.reason)
        }
    }

    @Test
    fun `prompt body stalls after headers or partial bytes return bounded uncertain and close the socket`() {
        listOf(byteArrayOf(), "{".toByteArray()).forEach { partial ->
            SocketComfyServer().use { server ->
                server.response.set { request -> when (request.path) {
                    "/object_info" -> json(OBJECT_INFO)
                    "/prompt" -> Response(200, partial, declaredLength = 100, awaitDisconnect = true)
                    else -> json("{}", 404)
                } }
                val worker = Executors.newSingleThreadExecutor()
                try {
                    val result = worker.submit<ComfyClientSubmissionResult> {
                        server.client(Duration.ofMillis(500)).submit(submission(workflow(), emptyMap()))
                    }.get(3, TimeUnit.SECONDS)
                    assertIs<ComfyClientSubmissionResult.Uncertain>(result)
                    assertTrue(server.bodyStarted.await(1, TimeUnit.SECONDS))
                    assertTrue(server.peerClosed.await(1, TimeUnit.SECONDS), "timed-out body must release its socket")
                    assertEquals(1, server.requests.count { it.path == "/prompt" })
                } finally { worker.shutdownNow() }
            }
        }
    }

    @Test
    fun `oversized response is bounded and interrupted stalled consumption releases the socket`() {
        SocketComfyServer().use { server ->
            server.response.set { Response(200, ByteArray(16 * 1024 * 1024 + 1) { 32 }) }
            val rejected = assertIs<ComfyClientSubmissionResult.Rejected>(server.client().submit(submission(workflow(), emptyMap())))
            assertTrue(rejected.reason.contains("exceeded"))
            assertEquals(listOf("/object_info"), server.requests.map { it.path })
        }
        SocketComfyServer().use { server ->
            server.response.set { Response(200, byteArrayOf(), declaredLength = 100, awaitDisconnect = true) }
            val returned = AtomicReference<Pair<ComfyClientSubmissionResult, Boolean>>()
            val worker = Thread {
                val result = server.client(Duration.ofSeconds(10)).submit(submission(workflow(), emptyMap()))
                returned.set(result to Thread.currentThread().isInterrupted)
            }
            try {
                worker.start()
                assertTrue(server.bodyStarted.await(2, TimeUnit.SECONDS))
                worker.interrupt()
                worker.join(2_000)
                assertTrue(!worker.isAlive, "interruption must end stalled body consumption")
                assertIs<ComfyClientSubmissionResult.Rejected>(returned.get().first)
                assertTrue(returned.get().second)
                assertTrue(server.peerClosed.await(1, TimeUnit.SECONDS))
            } finally { worker.interrupt() }
        }
    }

    @Test
    fun `enqueue followed by server error remains uncertain and reconciles same prompt without repost`() {
        SocketComfyServer().use { server ->
            val enqueued = java.util.concurrent.atomic.AtomicBoolean(false)
            server.response.set { request -> when (request.path) {
                "/object_info" -> json(OBJECT_INFO)
                "/prompt" -> { enqueued.set(true); json("response handling failed", 500) }
                "/history/$PROMPT_ID" -> json("{}")
                "/queue" -> json("""{"queue_running":[[1,"$PROMPT_ID",{},{}]],"queue_pending":[]}""")
                else -> json("{}", 404)
            } }
            assertIs<ComfyClientSubmissionResult.Uncertain>(server.client().submit(submission(workflow(), emptyMap())))
            assertTrue(enqueued.get())
            assertIs<ComfyClientObservation.Running>(server.client().observe(PROMPT_ID, "client-1", OUTPUT))
            assertEquals(1, server.requests.count { it.path == "/prompt" })
        }
    }

    @Test
    fun `interrupted history survives a requested cancellation and client reconstruction`() {
        SocketComfyServer().use { server ->
            val interrupted = java.util.concurrent.atomic.AtomicBoolean(false)
            server.response.set { request -> when (request.path) {
                "/history/$PROMPT_ID" -> if (interrupted.get()) json(interruptedHistory(PROMPT_ID)) else json("{}")
                "/queue" -> json("""{"queue_running":[[1,"$PROMPT_ID",{},{}]],"queue_pending":[]}""")
                "/interrupt" -> json("{}")
                else -> json("{}", 404)
            } }
            assertEquals(ComfyClientCancellation.Requested, server.client().cancel(PROMPT_ID, true))
            interrupted.set(true)
            assertEquals(ComfyClientObservation.Cancelled, server.client().observe(PROMPT_ID, "client-1", OUTPUT))
        }
    }

    @Test
    fun `interrupted websocket event is cancellation and successful history still wins`() {
        SocketComfyServer().use { server ->
            server.webSocketEvent.set("""{"type":"execution_interrupted","data":{"prompt_id":"$PROMPT_ID"}}""")
            server.response.set { json("{}") }
            assertEquals(ComfyClientObservation.Cancelled, server.client().observe(PROMPT_ID, "client-1", OUTPUT))
            server.response.set { json(successHistory(PROMPT_ID)) }
            assertIs<ComfyClientObservation.Completed>(server.client().observe(PROMPT_ID, "client-1", OUTPUT))
        }
    }

    @Test
    fun `success racing interrupt remains recoverable instead of falsely confirmed stopped`() {
        SocketComfyServer().use { server ->
            val completed = java.util.concurrent.atomic.AtomicBoolean(false)
            server.response.set { request -> when (request.path) {
                "/history/$PROMPT_ID" -> if (completed.get()) json(successHistory(PROMPT_ID)) else json("{}")
                "/queue" -> if (completed.get()) json("""{"queue_running":[],"queue_pending":[]}""")
                    else json("""{"queue_running":[[1,"$PROMPT_ID",{},{}]],"queue_pending":[]}""")
                "/interrupt" -> { completed.set(true); json("{}") }
                else -> json("{}", 404)
            } }
            assertIs<ComfyClientCancellation.Unknown>(server.client().cancel(PROMPT_ID, true))
            assertEquals(2, server.requests.count { it.path == "/history/$PROMPT_ID" })
            assertIs<ComfyClientObservation.Completed>(server.client().observe(PROMPT_ID, "client-1", OUTPUT))
        }
    }

    @Test
    fun `acknowledged pending deletion proves cancellation only with subsequent queue and history evidence`() {
        listOf("deleted", "running", "completed", "invalid-queue").forEach { outcome ->
            SocketComfyServer().use { server ->
                val deleted = java.util.concurrent.atomic.AtomicBoolean(false)
                server.response.set { request -> when {
                    request.path == "/history/$PROMPT_ID" -> if (deleted.get() && outcome == "completed") json(successHistory(PROMPT_ID)) else json("{}")
                    request.path == "/queue" && request.method == "POST" -> {
                        deleted.set(true)
                        json("{}")
                    }
                    request.path == "/queue" -> when {
                        !deleted.get() -> json("""{"queue_running":[],"queue_pending":[[1,"$PROMPT_ID",{},{}]]}""")
                        outcome == "running" -> json("""{"queue_running":[[1,"$PROMPT_ID",{},{}]],"queue_pending":[]}""")
                        outcome == "invalid-queue" -> json("{}")
                        else -> json("""{"queue_running":[],"queue_pending":[]}""")
                    }
                    else -> json("{}", 404)
                } }
                val result = server.client().cancel(PROMPT_ID, true)
                when (outcome) {
                    "deleted" -> assertEquals(ComfyClientCancellation.ConfirmedStopped, result)
                    "completed" -> {
                        assertIs<ComfyClientCancellation.Unknown>(result)
                        assertIs<ComfyClientObservation.Completed>(server.client().observe(PROMPT_ID, "client-1", OUTPUT))
                    }
                    else -> assertEquals(ComfyClientCancellation.Requested, result)
                }
                assertEquals("{\"delete\":[\"$PROMPT_ID\"]}", server.requests.single { it.path == "/queue" && it.method == "POST" }.body.toString(Charsets.UTF_8))
                assertTrue(server.requests.none { it.path == "/interrupt" })
            }
        }
    }

    @Test
    fun `unacknowledged pending cancellation preserves queue evidence until acknowledgement`() {
        SocketComfyServer().use { server ->
            val deleted = java.util.concurrent.atomic.AtomicBoolean(false)
            server.response.set { request -> when {
                request.path == "/history/$PROMPT_ID" -> json("{}")
                request.path == "/queue" && request.method == "POST" -> {
                    deleted.set(true)
                    json("{}")
                }
                request.path == "/queue" -> if (deleted.get()) json("""{"queue_running":[],"queue_pending":[]}""")
                    else json("""{"queue_running":[],"queue_pending":[[1,"$PROMPT_ID",{},{}]]}""")
                else -> json("{}", 404)
            } }
            val client = server.client()
            repeat(2) {
                assertEquals(ComfyClientCancellation.Requested, client.cancel(PROMPT_ID, false))
                assertTrue(!deleted.get())
                assertTrue(server.requests.none { it.method == "POST" })
            }
            assertIs<ComfyClientObservation.Running>(client.observe(PROMPT_ID, "client-1", OUTPUT))

            assertEquals(ComfyClientCancellation.ConfirmedStopped, client.cancel(PROMPT_ID, true))
            assertEquals("{\"delete\":[\"$PROMPT_ID\"]}", server.requests.single { it.path == "/queue" && it.method == "POST" }.body.toString(Charsets.UTF_8))
            assertTrue(server.requests.none { it.path == "/interrupt" })
        }
    }

    private fun submission(
        workflow: kotlinx.serialization.json.JsonObject,
        scalar: Map<VideoComfyInputSlot, JsonPrimitive>,
    ) = ComfyClientSubmission(
        PROMPT_ID, "client-1", workflow, scalar, emptyList(), VideoComfyOutputBinding("3", setOf("mp4")),
        "request-1", "a".repeat(64), "attempt-1", "owner-1",
    )

    private fun workflow() = buildJsonObject {
        put("1", buildJsonObject { put("class_type", "TextNode"); put("inputs", buildJsonObject { put("text", "original") }) })
        put("2", buildJsonObject { put("class_type", "ImageNode"); put("inputs", buildJsonObject { put("image", "original.png") }) })
        put("3", buildJsonObject { put("class_type", "SaveVideo"); put("inputs", buildJsonObject { put("frames", kotlinx.serialization.json.buildJsonArray { add(JsonPrimitive("2")); add(JsonPrimitive(0)) }) }) })
    }

    internal class SocketComfyServer : AutoCloseable, ComfyVideoFixtureEndpoint {
        private val socket = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        private val pool = Executors.newCachedThreadPool()
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        val response = AtomicReference<(Request) -> Response>({ json("{}", 404) })
        val webSocketEvent = AtomicReference<String?>(null)
        val bodyStarted = CountDownLatch(1)
        val peerClosed = CountDownLatch(1)
        private val connections = java.util.concurrent.ConcurrentHashMap.newKeySet<java.net.Socket>()

        init {
            pool.submit {
                while (!socket.isClosed) runCatching {
                    val client = socket.accept()
                    connections += client
                    pool.submit { try { client.use { handle(it) } } finally { connections -= client } }
                }
            }
        }

        fun client(timeout: Duration = Duration.ofSeconds(2)) = ComfyVideoClient(this, requestTimeout = timeout, webSocketWait = Duration.ofMillis(500))
        override fun httpUri(method: ComfyVideoHttpMethod, operationPath: String) = URI("http://127.0.0.1:${socket.localPort}$operationPath")
        override fun webSocketUri(clientId: String) = URI("ws://127.0.0.1:${socket.localPort}/ws?clientId=$clientId")

        private fun handle(client: java.net.Socket) {
            val input = BufferedInputStream(client.getInputStream())
            val headerBytes = readHeaders(input)
            val header = headerBytes.toString(StandardCharsets.US_ASCII)
            val lines = header.split("\r\n")
            val first = lines.first().split(' ')
            val method = first[0]
            val path = first[1].substringBefore('?')
            val headers = lines.drop(1).mapNotNull { line ->
                val index = line.indexOf(':').takeIf { it > 0 } ?: return@mapNotNull null
                line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim()
            }.toMap()
            if (headers["upgrade"]?.equals("websocket", true) == true) {
                val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((headers.getValue("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
                client.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
                webSocketEvent.get()?.let { event -> writeWebSocket(client, event) }
                client.getOutputStream().flush()
                Thread.sleep(25)
                return
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            val body = input.readNBytes(length)
            val request = Request(method, path, body)
            requests += request
            val response = response.get().invoke(request)
            client.getOutputStream().write(("HTTP/1.1 ${response.status} OK\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${response.declaredLength ?: response.body.size}\r\nConnection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
            client.getOutputStream().write(response.body)
            client.getOutputStream().flush()
            if (response.awaitDisconnect) {
                bodyStarted.countDown()
                client.soTimeout = 3_000
                try {
                    if (input.read() == -1) peerClosed.countDown()
                } catch (_: java.net.SocketException) {
                    peerClosed.countDown()
                }
            }
        }

        private fun readHeaders(input: BufferedInputStream): ByteArray {
            val bytes = ArrayList<Byte>()
            while (bytes.size < 64 * 1024) {
                val next = input.read()
                if (next < 0) break
                bytes += next.toByte()
                if (bytes.size >= 4 && bytes.takeLast(4).toByteArray().contentEquals("\r\n\r\n".toByteArray())) break
            }
            return bytes.toByteArray()
        }

        private fun writeWebSocket(client: java.net.Socket, text: String) {
            val payload = text.toByteArray()
            require(payload.size < 126)
            client.getOutputStream().write(byteArrayOf(0x81.toByte(), payload.size.toByte()))
            client.getOutputStream().write(payload)
        }

        override fun close() { socket.close(); connections.forEach { it.close() }; pool.shutdownNow() }
    }

    internal data class Request(val method: String, val path: String, val body: ByteArray)
    internal data class Response(val status: Int, val body: ByteArray, val declaredLength: Int? = null, val awaitDisconnect: Boolean = false)

    companion object {
        private val OUTPUT = VideoComfyOutputBinding("3", setOf("mp4"))
        private const val PROMPT_ID = "11111111-2222-3333-4444-555555555555"
        private val OBJECT_INFO = """{
          "TextNode":{"input":{"required":{"text":["STRING",{}]}}},
          "ImageNode":{"input":{"required":{"image":["IMAGE",{}]}}},
          "SaveVideo":{"output_node":true,"input":{"required":{"frames":["IMAGE",{}]}}}
        }""".trimIndent()
        internal fun interruptedHistory(id: String) = """{"$id":{"status":{"status_str":"error","completed":false,"messages":[["execution_interrupted",{"prompt_id":"$id"}]]},"outputs":{}}}"""
        internal fun successHistory(id: String) = """{"$id":{"status":{"status_str":"success","completed":true,"messages":[]},"outputs":{"3":{"images":[{"filename":"take.mp4","subfolder":"","type":"output"}]}}}}"""
        internal fun json(body: String, status: Int = 200) = Response(status, body.toByteArray())
    }
}
