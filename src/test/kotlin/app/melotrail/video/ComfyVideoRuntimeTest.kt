package app.melotrail.video

import app.melotrail.video.adapter.ComfyHostResources
import app.melotrail.video.adapter.ComfyMemoryPressure
import app.melotrail.video.adapter.ComfyVideoHttpMethod
import app.melotrail.video.adapter.ComfyVideoRuntime
import app.melotrail.video.adapter.ComfyVideoRuntimeException
import app.melotrail.video.adapter.ComfyVideoRuntimeFailure
import app.melotrail.video.adapter.ComfyVideoRuntimeState
import app.melotrail.video.adapter.LocalVideoSetup
import app.melotrail.video.adapter.LocalVideoSetupState
import app.melotrail.video.adapter.PinnedServerProfile
import app.melotrail.video.adapter.ReadyLocalVideoSetup
import app.melotrail.video.adapter.VideoMediaProcessCancellation
import app.melotrail.video.adapter.VideoMediaProcessException
import app.melotrail.video.adapter.VideoMediaProcessFailure
import app.melotrail.video.adapter.VideoMediaProcessOutput
import app.melotrail.video.adapter.VideoMediaProcessRequest
import app.melotrail.video.adapter.VideoMediaProcessResult
import com.sun.net.httpserver.HttpServer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComfyVideoRuntimeTest {
    @Test
    fun `runtime issues an owned HTTP and WebSocket connection only for its exact session`() {
        val fixture = RuntimeFixture.create()
        val runner = OwnedProtocolRunner()
        val runtime = fixture.runtime(runner, healthy = { uri -> ComfyVideoRuntime.defaultHealthProbe(uri) })
        try {
            val session = runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
            val copied = session.copy()
            val copyFailure = assertFailsWith<ComfyVideoRuntimeException> { runtime.connection(copied) }
            assertEquals(ComfyVideoRuntimeFailure.INVALID_REQUEST, copyFailure.failure)

            val connection = runtime.connection(session)
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
            val queueUri = connection.httpUri(ComfyVideoHttpMethod.GET, "/queue?owned=true")
            assertTrue(queueUri.path.matches(Regex("/_melotrail/[0-9a-f-]+/queue")))
            assertFalse(queueUri.toString().contains(assertNotNull(runner.healthIdentity.get())))
            assertTrue(queueUri.toString().contains(assertNotNull(runner.routeToken.get())))
            val queue = client.send(
                HttpRequest.newBuilder(queueUri).timeout(Duration.ofSeconds(1)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, queue.statusCode())
            assertTrue(queue.body().contains("queue_running"))

            val viewPath = "/view?filename=coffee%20cup.png&subfolder=a%26b&type=output"
            val view = client.send(
                HttpRequest.newBuilder(connection.httpUri(ComfyVideoHttpMethod.GET, viewPath))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, view.statusCode())
            val receivedQuery = URI(assertNotNull(runner.lastViewTarget.get())).rawQuery
                .split('&')
                .associate { field ->
                    val (name, value) = field.split('=', limit = 2)
                    name to URLDecoder.decode(value, Charsets.UTF_8)
                }
            assertEquals("coffee cup.png", receivedQuery["filename"])
            assertEquals("a&b", receivedQuery["subfolder"])

            val raw = client.send(
                HttpRequest.newBuilder(session.endpoint.resolve("/queue")).timeout(Duration.ofSeconds(1)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(404, raw.statusCode(), "public session metadata must not expose raw ComfyUI routes")

            val status = awaitStatusEvent(client, connection.webSocketUri("runtime-fixture"))
            assertTrue(status.contains("\"type\":\"status\""))
            assertTrue(runner.privateWebSockets.get() == 1)
        } finally {
            runtime.close()
        }
        assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `connection validates the documented operation namespace`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val runtime = fixture.runtime(runner, { true })
        try {
            val connection = runtime.connection(runtime.start(fixture.setup, fixture.sessions, fixture.freePort()))
            listOf(
                ComfyVideoHttpMethod.GET to "/system_stats",
                ComfyVideoHttpMethod.GET to "/object_info/LTXVConditioning",
                ComfyVideoHttpMethod.GET to "/history/prompt-123",
                ComfyVideoHttpMethod.GET to "/view?filename=result.png&type=output",
                ComfyVideoHttpMethod.POST to "/upload/image",
                ComfyVideoHttpMethod.POST to "/prompt",
                ComfyVideoHttpMethod.POST to "/queue",
                ComfyVideoHttpMethod.POST to "/interrupt",
            ).forEach { (method, path) ->
                assertTrue(connection.httpUri(method, path).path.startsWith("/_melotrail/"))
            }

            listOf(
                ComfyVideoHttpMethod.GET to "http://127.0.0.1:9999/queue",
                ComfyVideoHttpMethod.GET to "/api/queue",
                ComfyVideoHttpMethod.GET to "/history/../prompt",
                ComfyVideoHttpMethod.GET to "/history/..",
                ComfyVideoHttpMethod.GET to "/object_info/.",
                ComfyVideoHttpMethod.GET to "/history/id%2Fother",
                ComfyVideoHttpMethod.GET to "/ws",
                ComfyVideoHttpMethod.POST to "/system_stats",
                ComfyVideoHttpMethod.POST to "/prompt#fragment",
                ComfyVideoHttpMethod.POST to "/unknown",
            ).forEach { (method, path) ->
                assertEquals(
                    ComfyVideoRuntimeFailure.INVALID_REQUEST,
                    assertFailsWith<ComfyVideoRuntimeException> { connection.httpUri(method, path) }.failure,
                )
            }
            assertEquals(
                ComfyVideoRuntimeFailure.INVALID_REQUEST,
                assertFailsWith<ComfyVideoRuntimeException> { connection.webSocketUri("bad?client") }.failure,
            )
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `connections become stale after stop and restart`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val runtime = fixture.runtime(runner, { true })
        val port = fixture.freePort()
        try {
            val firstSession = runtime.start(fixture.setup, fixture.sessions, port)
            val first = runtime.connection(firstSession)
            runtime.stop()
            assertEquals(
                ComfyVideoRuntimeFailure.NOT_RUNNING,
                assertFailsWith<ComfyVideoRuntimeException> {
                    first.httpUri(ComfyVideoHttpMethod.GET, "/queue")
                }.failure,
            )

            val secondSession = runtime.start(fixture.setup, fixture.sessions, port)
            val second = runtime.connection(secondSession)
            assertEquals(
                ComfyVideoRuntimeFailure.NOT_RUNNING,
                assertFailsWith<ComfyVideoRuntimeException> { first.webSocketUri("old-client") }.failure,
            )
            assertTrue(second.httpUri(ComfyVideoHttpMethod.GET, "/queue").path.startsWith("/_melotrail/"))
            assertFalse(
                firstSession.id == secondSession.id,
                "restart must issue a fresh session and route namespace",
            )
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `same-port replacement cannot receive a standard Comfy mutation after URI validation`() {
        val fixture = RuntimeFixture.create()
        val port = fixture.freePort()
        val runner = OwnedProtocolRunner()
        val runtime = fixture.runtime(runner, healthy = { uri -> ComfyVideoRuntime.defaultHealthProbe(uri) })
        val session = runtime.start(fixture.setup, fixture.sessions, port)
        val privatePrompt = runtime.connection(session).httpUri(ComfyVideoHttpMethod.POST, "/prompt")
        runtime.stop()

        val standardMutations = AtomicInteger()
        val receivedPath = AtomicReference<String>()
        val replacement = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        replacement.createContext("/") { exchange ->
            receivedPath.set(exchange.requestURI.path)
            if (exchange.requestURI.path == "/prompt") standardMutations.incrementAndGet()
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        replacement.start()
        try {
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(privatePrompt)
                    .timeout(Duration.ofSeconds(1))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(),
                HttpResponse.BodyHandlers.discarding(),
            )
            assertEquals(404, response.statusCode())
            assertTrue(receivedPath.get().startsWith("/_melotrail/"))
            assertEquals(0, standardMutations.get())
            assertEquals(
                ComfyVideoRuntimeFailure.NOT_RUNNING,
                assertFailsWith<ComfyVideoRuntimeException> {
                    runtime.connection(session)
                }.failure,
            )
        } finally {
            replacement.stop(0)
            runtime.close()
        }
    }

    @Test
    fun `owned launch uses resolved Python with explicit venv imports and private directories`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val runtime = fixture.runtime(runner = runner, healthy = { true })

        val session = runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
        val request = assertNotNull(runner.request.get())

        assertEquals(fixture.setup.pythonExecutable, request.executable)
        assertEquals(fixture.setup.pythonExecutableSha256, request.executableSha256)
        assertEquals(fixture.setup.pythonVirtualEnvironment.toString(), request.environment["VIRTUAL_ENV"])
        assertEquals(fixture.setup.pythonSitePackages.toString(), request.environment["PYTHONPATH"])
        assertEquals("1", request.environment["PYTHONNOUSERSITE"])
        assertEquals("1", request.environment["HF_HUB_OFFLINE"])
        assertEquals("1", request.environment["TRANSFORMERS_OFFLINE"])
        assertEquals(session.directory, request.workingDirectory)
        assertTrue(request.arguments.containsAll(listOf(
            "--listen", "127.0.0.1", "--disable-api-nodes", "--disable-auto-launch",
            "--disable-all-custom-nodes", "--whitelist-custom-nodes", "ComfyUI-GGUF",
            "--input-directory", session.inputDirectory.toString(),
            "--output-directory", session.outputDirectory.toString(),
            "--temp-directory", session.temporaryDirectory.toString(),
            "--user-directory", session.userDirectory.toString(),
        )))
        assertEquals(ComfyVideoRuntimeState.READY, runtime.state)

        val slot = runtime.acquireInferenceSlot()
        val busy = assertFailsWith<ComfyVideoRuntimeException> { runtime.acquireInferenceSlot() }
        assertEquals(ComfyVideoRuntimeFailure.INFERENCE_BUSY, busy.failure)
        slot.close()
        runtime.acquireInferenceSlot().close()

        runtime.stop()
        assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
        assertEquals(ComfyVideoRuntimeState.STOPPED, runtime.state)
        runtime.close()
    }

    @Test
    fun `occupied port is refused before any process is launched or signalled`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val runtime = fixture.runtime(runner = runner, healthy = { true })
        var port = 0
        ServerSocket().use { occupied ->
            occupied.reuseAddress = false
            occupied.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
            port = occupied.localPort

            val error = assertFailsWith<ComfyVideoRuntimeException> {
                runtime.start(fixture.setup, fixture.sessions, port)
            }

            assertEquals(ComfyVideoRuntimeFailure.PORT_OCCUPIED, error.failure)
            assertEquals(0, runner.launches.get())
            assertFalse(runner.cancelObserved.await(20, TimeUnit.MILLISECONDS))
            assertEquals(ComfyVideoRuntimeState.FAILED, runtime.state)
        }
        runtime.start(fixture.setup, fixture.sessions, port)
        assertEquals(ComfyVideoRuntimeState.READY, runtime.state)
        runtime.stop()
        assertEquals(1, runner.launches.get())
        runtime.close()
    }

    @Test
    fun `stop during prelaunch waits for ownership registration then cancels and reaps`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val resourceEntered = CountDownLatch(1)
        val releaseResource = CountDownLatch(1)
        val runtime = fixture.runtime(
            runner = runner,
            healthy = { false },
            resources = {
                resourceEntered.countDown()
                check(releaseResource.await(1, TimeUnit.SECONDS))
                ComfyHostResources(ComfyMemoryPressure.NORMAL, 0)
            },
        )
        val callers = Executors.newFixedThreadPool(2)
        val start = callers.submit<Throwable?> {
            runCatching { runtime.start(fixture.setup, fixture.sessions, fixture.freePort()) }.exceptionOrNull()
        }
        assertTrue(resourceEntered.await(1, TimeUnit.SECONDS))
        val stop = callers.submit { runtime.stop() }
        assertFalse(stop.isDone)

        releaseResource.countDown()
        stop.get(2, TimeUnit.SECONDS)
        assertNotNull(start.get(2, TimeUnit.SECONDS))
        assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
        assertEquals(1, runner.launches.get())
        assertEquals(ComfyVideoRuntimeState.STOPPED, runtime.state)
        callers.shutdownNow()
        runtime.close()
    }

    @Test
    fun `rejected critical-pressure start is recoverable without launching`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val pressure = AtomicReference(ComfyMemoryPressure.CRITICAL)
        val runtime = fixture.runtime(
            runner = runner,
            healthy = { true },
            resources = { ComfyHostResources(pressure.get(), 0) },
        )
        val error = assertFailsWith<ComfyVideoRuntimeException> {
            runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
        }
        assertEquals(ComfyVideoRuntimeFailure.RESOURCE_LIMIT, error.failure)
        assertEquals(0, runner.launches.get())

        pressure.set(ComfyMemoryPressure.NORMAL)
        runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
        runtime.stop()
        assertEquals(1, runner.launches.get())
        runtime.close()
    }

    @Test
    fun `readiness timeout cancels and reaps its process and a clean restart works`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val healthy = AtomicBoolean(false)
        val clock = AtomicLong(0)
        val runtime = fixture.runtime(
            runner = runner,
            healthy = { healthy.get() },
            nanoTime = { clock.addAndGet(Duration.ofMillis(600).toNanos()) },
        )

        val timeout = assertFailsWith<ComfyVideoRuntimeException> {
            runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
        }
        assertEquals(ComfyVideoRuntimeFailure.STARTUP_TIMEOUT, timeout.failure)
        assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
        assertEquals(ComfyVideoRuntimeState.FAILED, runtime.state)

        healthy.set(true)
        val restarted = runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
        assertEquals(ComfyVideoRuntimeState.READY, runtime.state)
        assertTrue(restarted.id.startsWith("comfy-"))
        runtime.stop()
        assertEquals(2, runner.launches.get())
        runtime.close()
    }

    @Test
    fun `critical pressure cancels owned server while warning pressure remains allowed`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val pressure = AtomicReference(ComfyMemoryPressure.WARNING)
        val runtime = fixture.runtime(
            runner = runner,
            healthy = { true },
            resources = { ComfyHostResources(pressure.get(), 0) },
        )

        runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
        assertEquals(ComfyVideoRuntimeState.READY, runtime.state)
        pressure.set(ComfyMemoryPressure.CRITICAL)

        waitUntil { runtime.state == ComfyVideoRuntimeState.FAILED }
        assertEquals(ComfyVideoRuntimeFailure.RESOURCE_LIMIT, runtime.lastFailure?.failure)
        assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
        runtime.stop()
        runtime.close()
    }

    @Test
    fun `bounded inference timeout cancels server and releases only its own slot`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val clock = AtomicLong(0)
        val runtime = fixture.runtime(
            runner = runner,
            healthy = { true },
            nanoTime = { clock.get() },
        )

        runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
        val lease = runtime.acquireInferenceSlot()
        clock.set(Duration.ofSeconds(2).toNanos())

        waitUntil { runtime.state == ComfyVideoRuntimeState.FAILED }
        assertEquals(ComfyVideoRuntimeFailure.RESOURCE_LIMIT, runtime.lastFailure?.failure)
        assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
        lease.close()
        runtime.stop()
        runtime.close()
    }

    @Test
    fun `failed child startup is surfaced and cleaned without accepting unrelated health`() {
        val fixture = RuntimeFixture.create()
        val runner: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult = { _, _ ->
            throw VideoMediaProcessException(VideoMediaProcessFailure.NONZERO_EXIT, "fixture bind failure")
        }
        val runtime = fixture.runtime(runner = runner, healthy = { Thread.sleep(20); true })

        val error = assertFailsWith<ComfyVideoRuntimeException> {
            runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
        }

        assertEquals(ComfyVideoRuntimeFailure.STARTUP_FAILED, error.failure)
        assertEquals(ComfyVideoRuntimeState.FAILED, runtime.state)
        assertTrue(error.suppressed.isEmpty(), "ordinary process failure was already reaped")
        runtime.close()
    }

    @Test
    fun `resource sampler exception after readiness fails cancels and reaps the owner`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val throwSample = AtomicBoolean(false)
        val runtime = fixture.runtime(runner, { true }, resources = {
            if (throwSample.get()) error("sample unavailable")
            ComfyHostResources(ComfyMemoryPressure.NORMAL, 0)
        })
        try {
            val connection = runtime.connection(runtime.start(fixture.setup, fixture.sessions, fixture.freePort()))
            throwSample.set(true)
            waitUntil { runtime.state == ComfyVideoRuntimeState.FAILED }
            assertEquals(ComfyVideoRuntimeFailure.RESOURCE_LIMIT, runtime.lastFailure?.failure)
            assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
            assertEquals(
                ComfyVideoRuntimeFailure.NOT_RUNNING,
                assertFailsWith<ComfyVideoRuntimeException> {
                    connection.httpUri(ComfyVideoHttpMethod.GET, "/queue")
                }.failure,
            )
            assertEquals(ComfyVideoRuntimeFailure.NOT_RUNNING,
                assertFailsWith<ComfyVideoRuntimeException> { runtime.acquireInferenceSlot() }.failure)
            // Restart without stop proves the monitor itself awaited and released its completed child.
            throwSample.set(false)
            waitUntil { runCatching { runtime.start(fixture.setup, fixture.sessions, fixture.freePort()) }.isSuccess }
            assertEquals(2, runner.launches.get())
        } finally { runtime.close() }
    }

    @Test
    fun `throwing health probes after readiness use three consecutive failures then reap`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val throwProbe = AtomicBoolean(false)
        val failures = AtomicInteger()
        val runtime = fixture.runtime(runner, {
            if (throwProbe.get()) { failures.incrementAndGet(); error("health unavailable") }
            true
        })
        try {
            runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
            throwProbe.set(true)
            waitUntil { runtime.state == ComfyVideoRuntimeState.FAILED }
            assertEquals(3, failures.get())
            assertEquals(ComfyVideoRuntimeFailure.HEALTH_LOST, runtime.lastFailure?.failure)
            assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
            throwProbe.set(false)
            waitUntil { runCatching { runtime.start(fixture.setup, fixture.sessions, fixture.freePort()) }.isSuccess }
            assertEquals(2, runner.launches.get())
        } finally { runtime.close() }
    }

    @Test
    fun `delayed normal stop retains ownership and forbids a second server until reaped`() {
        val fixture = RuntimeFixture.create()
        val runner = DelayedRunner()
        val runtime = fixture.runtime(runner, { true })
        try {
            runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
            val error = assertFailsWith<ComfyVideoRuntimeException> { runtime.stop() }
            assertEquals(ComfyVideoRuntimeFailure.SHUTDOWN_FAILED, error.failure)
            assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
            val restart = assertFailsWith<ComfyVideoRuntimeException> {
                runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
            }
            assertEquals(ComfyVideoRuntimeFailure.INVALID_REQUEST, restart.failure)
            assertEquals(1, runner.launches.get())
            assertEquals(ComfyVideoRuntimeFailure.NOT_RUNNING,
                assertFailsWith<ComfyVideoRuntimeException> { runtime.acquireInferenceSlot() }.failure)
            runner.release.countDown()
            runtime.stop()
            assertEquals(ComfyVideoRuntimeState.STOPPED, runtime.state)
        } finally { runner.release.countDown(); runtime.close() }
    }

    @Test
    fun `delayed failed startup cleanup retains ownership until a later close reaps it`() {
        val fixture = RuntimeFixture.create()
        val runner = DelayedRunner()
        val clock = AtomicLong()
        val runtime = fixture.runtime(runner, { false }, nanoTime = { clock.addAndGet(Duration.ofMillis(600).toNanos()) })
        try {
            val error = assertFailsWith<ComfyVideoRuntimeException> {
                runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
            }
            assertEquals(ComfyVideoRuntimeFailure.STARTUP_TIMEOUT, error.failure)
            assertTrue(error.suppressed.any { it is ComfyVideoRuntimeException && it.failure == ComfyVideoRuntimeFailure.SHUTDOWN_FAILED })
            assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
            assertEquals(ComfyVideoRuntimeFailure.INVALID_REQUEST,
                assertFailsWith<ComfyVideoRuntimeException> { runtime.start(fixture.setup, fixture.sessions, fixture.freePort()) }.failure)
            assertEquals(1, runner.launches.get())
            // Closing after another timed-out wait must not interrupt the still-owned supervisor.
            assertFailsWith<ComfyVideoRuntimeException> { runtime.close() }
            assertFalse(runner.interrupted.get())
            runner.release.countDown()
            runtime.close()
            assertEquals(ComfyVideoRuntimeState.STOPPED, runtime.state)
        } finally { runner.release.countDown(); runtime.close() }
    }

    @Test
    fun `completed process with suppressed cleanup failure remains owned and blocks restart`() {
        val fixture = RuntimeFixture.create()
        val executor = Executors.newCachedThreadPool()
        val runtime = ComfyVideoRuntime(
            runProcess = { _, _ ->
                throw VideoMediaProcessException(VideoMediaProcessFailure.NONZERO_EXIT, "child failed").apply {
                    addSuppressed(VideoMediaProcessException(VideoMediaProcessFailure.SUPERVISION_FAILED, "group termination unconfirmed"))
                }
            },
            healthProbe = { Thread.sleep(10); true },
            resources = { ComfyHostResources(ComfyMemoryPressure.NORMAL, 0) },
            executor = executor,
            nanoTime = System::nanoTime,
        )
        try {
            val error = assertFailsWith<ComfyVideoRuntimeException> { runtime.start(fixture.setup, fixture.sessions, fixture.freePort()) }
            assertEquals(ComfyVideoRuntimeFailure.STARTUP_FAILED, error.failure)
            assertTrue(error.suppressed.any { it is ComfyVideoRuntimeException && it.failure == ComfyVideoRuntimeFailure.SHUTDOWN_FAILED })
            assertEquals(ComfyVideoRuntimeFailure.INVALID_REQUEST,
                assertFailsWith<ComfyVideoRuntimeException> { runtime.start(fixture.setup, fixture.sessions, fixture.freePort()) }.failure)
            assertEquals(ComfyVideoRuntimeFailure.SHUTDOWN_FAILED,
                assertFailsWith<ComfyVideoRuntimeException> { runtime.close() }.failure)
        } finally { executor.shutdownNow() } // Fixture has no native child; retain production ownership on uncertainty.
    }

    @Test
    fun `competing compatible HTTP listener winning after precheck is never accepted or signalled`() {
        val fixture = RuntimeFixture.create()
        val port = fixture.freePort()
        val competitor = AtomicReference<HttpServer?>()
        val bound = CountDownLatch(1)
        val runner = BlockingRunner()
        val clock = AtomicLong()
        val runtime = fixture.runtime(
            runner = { request, cancellation ->
                val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
                server.createContext("/system_stats") { exchange ->
                    val body = "{\"system\":{\"comfyui_version\":\"0.35.0\"}}".toByteArray()
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                competitor.set(server)
                server.start()
                bound.countDown()
                runner(request, cancellation)
            },
            healthy = { uri ->
                check(bound.await(1, TimeUnit.SECONDS))
                ComfyVideoRuntime.defaultHealthProbe(uri)
            },
            nanoTime = { clock.addAndGet(Duration.ofMillis(600).toNanos()) },
        )
        try {
            val error = assertFailsWith<ComfyVideoRuntimeException> {
                runtime.start(fixture.setup, fixture.sessions, port)
            }
            assertEquals(ComfyVideoRuntimeFailure.STARTUP_TIMEOUT, error.failure)
            assertEquals(ComfyVideoRuntimeState.FAILED, runtime.state)
            assertTrue(runner.cancelObserved.await(1, TimeUnit.SECONDS))
            val response = java.net.URI("http://127.0.0.1:$port/system_stats").toURL().readText()
            assertTrue(response.contains("comfyui_version"), "unrelated listener must remain alive")
        } finally { runtime.close(); competitor.get()?.stop(0) }
    }

    @Test
    fun `health response requires the exact launch marker without sending it to listener`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val marker = AtomicReference("wrong")
        val receivedPath = AtomicReference<String>()
        server.createContext("/system_stats") { exchange ->
            receivedPath.set(exchange.requestURI.toString())
            exchange.responseHeaders.set("X-Melotrail-Session", marker.get())
            val body = "{\"comfyui_version\":\"fixture\"}".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val uri = java.net.URI("http://127.0.0.1:${server.address.port}/system_stats#expected")
            assertFalse(ComfyVideoRuntime.defaultHealthProbe(uri))
            marker.set("expected")
            assertTrue(ComfyVideoRuntime.defaultHealthProbe(uri))
            assertEquals("/system_stats", receivedPath.get())
        } finally { server.stop(0) }
    }

    @Test
    fun `inference admission and stop serialize so stopped owner cannot acquire a late slot`() {
        val fixture = RuntimeFixture.create()
        val runner = BlockingRunner()
        val arm = AtomicBoolean(false)
        val admissionEntered = CountDownLatch(1)
        val releaseAdmission = CountDownLatch(1)
        val runtime = fixture.runtime(runner, { true }, nanoTime = {
            if (arm.get()) {
                admissionEntered.countDown()
                check(releaseAdmission.await(2, TimeUnit.SECONDS))
            }
            System.nanoTime()
        })
        val callers = Executors.newFixedThreadPool(2)
        try {
            runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
            arm.set(true)
            val acquired = callers.submit<app.melotrail.video.adapter.ComfyVideoInferenceSlot> { runtime.acquireInferenceSlot() }
            assertTrue(admissionEntered.await(1, TimeUnit.SECONDS))
            val stopEntered = CountDownLatch(1)
            val stopped = callers.submit { stopEntered.countDown(); runtime.stop() }
            assertTrue(stopEntered.await(1, TimeUnit.SECONDS))
            // Admission owns the lifecycle lock until the timestamp and token are both published.
            assertFalse(runner.cancelObserved.await(50, TimeUnit.MILLISECONDS))
            assertFalse(stopped.isDone)
            arm.set(false)
            releaseAdmission.countDown()
            val slot = acquired.get(2, TimeUnit.SECONDS)
            stopped.get(2, TimeUnit.SECONDS)
            slot.close()
            assertEquals(ComfyVideoRuntimeState.STOPPED, runtime.state)
            assertEquals(ComfyVideoRuntimeFailure.NOT_RUNNING,
                assertFailsWith<ComfyVideoRuntimeException> { runtime.acquireInferenceSlot() }.failure)
            runtime.start(fixture.setup, fixture.sessions, fixture.freePort())
            runtime.acquireInferenceSlot().close()
        } finally { releaseAdmission.countDown(); callers.shutdownNow(); runtime.close() }
    }

    private class DelayedRunner : (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult {
        val launches = AtomicInteger()
        val cancelObserved = CountDownLatch(1)
        val release = CountDownLatch(1)
        val interrupted = AtomicBoolean()
        override fun invoke(request: VideoMediaProcessRequest, cancellation: VideoMediaProcessCancellation): VideoMediaProcessResult {
            launches.incrementAndGet()
            try {
                while (!cancellation.isCancelled()) Thread.sleep(2)
                cancelObserved.countDown()
                release.await()
            } catch (error: InterruptedException) { interrupted.set(true); throw error }
            throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "fixture eventually reaped")
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue(condition(), "condition did not become true")
    }

    private class BlockingRunner : (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult {
        val request = AtomicReference<VideoMediaProcessRequest?>()
        val launches = AtomicInteger(0)
        val cancelObserved = CountDownLatch(1)

        override fun invoke(request: VideoMediaProcessRequest, cancellation: VideoMediaProcessCancellation): VideoMediaProcessResult {
            this.request.set(request)
            launches.incrementAndGet()
            while (!cancellation.isCancelled()) Thread.sleep(2)
            cancelObserved.countDown()
            throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "fixture cancelled")
        }
    }

    private class OwnedProtocolRunner :
        (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult {
        val healthIdentity = AtomicReference<String?>()
        val routeToken = AtomicReference<String?>()
        val lastViewTarget = AtomicReference<String?>()
        val privateWebSockets = AtomicInteger()
        val cancelObserved = CountDownLatch(1)

        override fun invoke(
            request: VideoMediaProcessRequest,
            cancellation: VideoMediaProcessCancellation,
        ): VideoMediaProcessResult {
            val identity = request.arguments[4]
            val token = request.arguments[5]
            val port = request.arguments[request.arguments.indexOf("--port") + 1].toInt()
            healthIdentity.set(identity)
            routeToken.set(token)
            val server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("127.0.0.1", port))
                soTimeout = 25
            }
            val clients = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "comfy-owned-protocol-fixture").apply { isDaemon = true }
            }
            try {
                while (!cancellation.isCancelled()) {
                    try {
                        val socket = server.accept()
                        clients.submit { handle(socket, identity, token) }
                    } catch (_: SocketTimeoutException) {
                        // Poll the same cancellation object used by the production process supervisor.
                    }
                }
                cancelObserved.countDown()
            } finally {
                server.close()
                clients.shutdownNow()
            }
            throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "owned protocol fixture cancelled")
        }

        private fun handle(socket: Socket, identity: String, token: String) {
            socket.use {
                it.soTimeout = 1_000
                val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.US_ASCII))
                val requestLine = reader.readLine() ?: return
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: return
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
                }
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val path = parts[1].substringBefore('?')
                val namespace = "/_melotrail/$token"
                if (headers["upgrade"].equals("websocket", ignoreCase = true) && path == "$namespace/ws") {
                    privateWebSockets.incrementAndGet()
                    val key = headers["sec-websocket-key"] ?: return
                    val accept = Base64.getEncoder().encodeToString(
                        MessageDigest.getInstance("SHA-1")
                            .digest((key + WEBSOCKET_GUID).toByteArray(Charsets.US_ASCII)),
                    )
                    val output = it.getOutputStream()
                    output.write(
                        ("HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(Charsets.US_ASCII),
                    )
                    val event = "{\"type\":\"status\",\"data\":{\"status\":{\"exec_info\":{\"queue_remaining\":0}},\"sid\":\"fixture\"}}"
                        .toByteArray(Charsets.UTF_8)
                    check(event.size < 126)
                    output.write(byteArrayOf(0x81.toByte(), event.size.toByte()))
                    output.write(event)
                    output.flush()
                    Thread.sleep(20)
                    return
                }

                when (path) {
                    "/system_stats" -> respond(
                        it,
                        200,
                        "{\"system\":{\"comfyui_version\":\"0.35.0\"}}",
                        mapOf("X-Melotrail-Session" to identity),
                    )
                    "$namespace/system_stats" -> respond(
                        it,
                        200,
                        "{\"system\":{\"comfyui_version\":\"0.35.0\"}}",
                        mapOf("X-Melotrail-Session" to identity),
                    )
                    "$namespace/queue" -> respond(it, 200, "{\"queue_running\":[],\"queue_pending\":[]}")
                    "$namespace/view" -> {
                        lastViewTarget.set(parts[1])
                        respond(it, 200, parts[1])
                    }
                    else -> respond(it, 404, "")
                }
            }
        }

        private fun respond(socket: Socket, status: Int, body: String, headers: Map<String, String> = emptyMap()) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val reason = if (status == 200) "OK" else "Not Found"
            val head = buildString {
                append("HTTP/1.1 $status $reason\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Connection: close\r\n")
                headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("\r\n")
            }
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
        }

        companion object {
            private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        }
    }

    private data class RuntimeFixture(
        val root: Path,
        val sessions: Path,
        val setup: ReadyLocalVideoSetup,
    ) {
        fun runtime(
            runner: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult,
            healthy: (java.net.URI) -> Boolean,
            resources: () -> ComfyHostResources = { ComfyHostResources(ComfyMemoryPressure.NORMAL, 0) },
            nanoTime: () -> Long = System::nanoTime,
        ) = ComfyVideoRuntime(
            runProcess = runner,
            healthProbe = healthy,
            resources = resources,
            executor = Executors.newCachedThreadPool { runnable -> Thread(runnable).apply { isDaemon = true } },
            nanoTime = nanoTime,
        )

        fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

        companion object {
            fun create(): RuntimeFixture {
                val root = Files.createTempDirectory("comfy-runtime")
                val sessions = root.resolve("runs").createDirectories()
                val comfy = root.resolve("tools/comfy").createDirectories()
                val workflow = root.resolve("workflows/test").createDirectories()
                val models = root.resolve("models").createDirectories()
                val venv = comfy.resolve(".venv").createDirectories()
                val site = venv.resolve("lib/python3.12/site-packages").createDirectories()
                val executable = root.resolve("python-real").also { it.writeText("fixture"); it.toFile().setExecutable(true) }
                val main = comfy.resolve("main.py").also { it.writeText("fixture") }
                val modelPaths = workflow.resolve("model-paths.yaml").also { it.writeText("fixture") }
                return RuntimeFixture(
                    root,
                    sessions,
                    ReadyLocalVideoSetup(
                        profileId = "test",
                        applicationSupportRoot = root.toRealPath(),
                        comfyUiRoot = comfy.toRealPath(),
                        workflowRoot = workflow.toRealPath(),
                        modelsRoot = models.toRealPath(),
                        pythonExecutable = executable.toRealPath(),
                        pythonExecutableSha256 = "0".repeat(64),
                        pythonVirtualEnvironment = venv.toRealPath(),
                        pythonSitePackages = site.toRealPath(),
                        mainScript = main.toRealPath(),
                        modelPathsConfig = modelPaths.toRealPath(),
                        verifyForLaunch = {},
                        server = PinnedServerProfile(
                            host = "127.0.0.1",
                            defaultPort = 8192,
                            startupTimeoutSeconds = 1,
                            sessionTimeoutSeconds = 3,
                            inferenceTimeoutSeconds = 1,
                            shutdownTimeoutSeconds = 1,
                            additionalSwapLimitBytes = 8L * 1024 * 1024 * 1024,
                            pollIntervalMillis = 5,
                            arguments = listOf(
                                "--fp16-unet", "--bf16-text-enc", "--fp32-vae",
                                "--disable-smart-memory", "--disable-pinned-memory", "--cache-none",
                                "--disable-api-nodes", "--disable-auto-launch", "--disable-all-custom-nodes",
                                "--whitelist-custom-nodes", "ComfyUI-GGUF",
                            ),
                        ),
                    ),
                )
            }
        }
    }
}

private fun awaitStatusEvent(client: HttpClient, uri: URI): String {
    val result = CompletableFuture<String>()
    val text = StringBuilder()
    val socket = client.newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .buildAsync(uri, object : WebSocket.Listener {
            override fun onOpen(webSocket: WebSocket) {
                webSocket.request(1)
            }

            override fun onText(
                webSocket: WebSocket,
                data: CharSequence,
                last: Boolean,
            ): CompletionStage<*>? {
                text.append(data)
                if (last) result.complete(text.toString())
                webSocket.request(1)
                return null
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                result.completeExceptionally(error)
            }
        })
        .get(2, TimeUnit.SECONDS)
    return try {
        result.get(2, TimeUnit.SECONDS)
    } finally {
        socket.abort()
    }
}

/**
 * Explicit host-only V17a probe. The coordinator invokes this through a temporary Gradle init
 * script; ordinary tests never call it and it performs no inference or download.
 */
object ComfyVideoRuntimeHostCheck {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val report = LocalVideoSetup.bundled().inspect()
        check(report.state == LocalVideoSetupState.READY) {
            report.issues.joinToString(prefix = "Pinned setup unavailable: ") { "${it.component}: ${it.detail}" }
        }
        val setup = checkNotNull(report.ready)
        val sessions = setup.applicationSupportRoot.resolve("runs").also { Files.createDirectories(it) }
        val before = sourceSnapshot(setup)
        val runtime = ComfyVideoRuntime()
        val session = try {
            runtime.start(setup, sessions)
        } catch (error: Exception) {
            runtime.close()
            throw error
        }
        check(runtime.state == ComfyVideoRuntimeState.READY)

        val collision = ComfyVideoRuntime()
        try {
            val error = runCatching { collision.start(setup, sessions, session.endpoint.port) }.exceptionOrNull()
            check(error is ComfyVideoRuntimeException && error.failure == ComfyVideoRuntimeFailure.PORT_OCCUPIED) {
                "A second runtime did not refuse the owned port"
            }
        } finally {
            collision.close()
            runtime.stop()
            runtime.close()
        }
        check(before == sourceSnapshot(setup)) { "Pinned source/model identity changed during server-only probe" }
        println("V17a host probe PASS: setup=ready start=ready health=pass busyPort=refused stop=reaped sourcePreserved=true session=${session.directory}")
    }

    private fun sourceSnapshot(setup: ReadyLocalVideoSetup): Map<Path, Pair<Long, FileTime>> {
        val paths = mutableListOf(setup.pythonExecutable, setup.mainScript, setup.modelPathsConfig)
        Files.walk(setup.modelsRoot).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach(paths::add)
        }
        return paths.associateWith { Files.size(it) to Files.getLastModifiedTime(it) }
    }
}

/**
 * Explicit host-only V17c proof. It accesses only read-only ComfyUI routes and the initial
 * WebSocket status event; it never acquires an inference slot or submits a workflow.
 */
object ComfyVideoConnectionHostCheck {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val report = LocalVideoSetup.bundled().inspect()
        check(report.state == LocalVideoSetupState.READY) {
            report.issues.joinToString(prefix = "Pinned setup unavailable: ") { "${it.component}: ${it.detail}" }
        }
        val setup = checkNotNull(report.ready)
        val sessions = setup.applicationSupportRoot.resolve("runs").also { Files.createDirectories(it) }
        val before = sourceSnapshot(setup)
        val runtime = ComfyVideoRuntime()
        var sessionDirectory: Path? = null
        try {
            val session = runtime.start(setup, sessions)
            sessionDirectory = session.directory
            val connection = runtime.connection(session)
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

            val stats = client.send(
                HttpRequest.newBuilder(connection.httpUri(ComfyVideoHttpMethod.GET, "/system_stats"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            check(stats.statusCode() == 200 && stats.body().contains("\"comfyui_version\"")) {
                "Private ComfyUI system stats were unavailable: HTTP ${stats.statusCode()}"
            }
            check(stats.headers().firstValue("X-Melotrail-Session").orElse("").isNotBlank()) {
                "Private system stats did not carry the owned response identity"
            }

            val queue = client.send(
                HttpRequest.newBuilder(connection.httpUri(ComfyVideoHttpMethod.GET, "/queue"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            check(queue.statusCode() == 200 && queue.body().contains("queue_running")) {
                "Private ComfyUI queue read was unavailable: HTTP ${queue.statusCode()}"
            }

            val event = awaitStatusEvent(client, connection.webSocketUri("melotrail-v17c-host-check"))
            check(event.contains("\"type\": \"status\"") || event.contains("\"type\":\"status\"")) {
                "ComfyUI WebSocket did not send its initial status event: $event"
            }
        } finally {
            runCatching { runtime.stop() }
            runtime.close()
            check(before == sourceSnapshot(setup)) {
                "Pinned source/model identity changed during connection-only probe"
            }
        }
        println(
            "V17c host proof PASS: setup=ready privateHttp=system_stats,queue " +
                "webSocket=status stop=reaped inference=none sourcePreserved=true session=$sessionDirectory",
        )
    }

    private fun sourceSnapshot(setup: ReadyLocalVideoSetup): Map<Path, Pair<Long, FileTime>> {
        val paths = mutableListOf(setup.pythonExecutable, setup.mainScript, setup.modelPathsConfig)
        Files.walk(setup.modelsRoot).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach(paths::add)
        }
        return paths.associateWith { Files.size(it) to Files.getLastModifiedTime(it) }
    }
}
