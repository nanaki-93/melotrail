package app.melotrail.video

import app.melotrail.video.adapter.ComfyVideoRuntime
import app.melotrail.video.adapter.ComfyVideoRuntimeException
import app.melotrail.video.adapter.ComfyVideoRuntimeFailure
import app.melotrail.video.adapter.ComfyHostResources
import app.melotrail.video.adapter.ComfyMemoryPressure
import java.util.concurrent.Executors
import kotlin.test.assertFailsWith
import app.melotrail.video.adapter.LocalVideoHost
import app.melotrail.video.adapter.LocalVideoSetup
import app.melotrail.video.adapter.LocalVideoSetupIssueKind
import app.melotrail.video.adapter.LocalVideoSetupState
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalVideoSetupTest {
    @Test
    fun `verified installed files produce ready setup without executing a choice`() {
        val fixture = SetupFixture.create()
        val report = fixture.setup.inspect(fixture.root)

        assertEquals(LocalVideoSetupState.READY, report.state)
        assertNotNull(report.ready)
        assertEquals(fixture.pythonTarget.toRealPath(), report.ready.pythonExecutable)
        assertEquals(fixture.root.toRealPath(), report.ready.applicationSupportRoot)
        assertTrue(report.capability.contains("short LTX-2.3 image-to-video"))
        assertTrue(report.capability.contains("Multi-reference conditioning"))
        assertTrue(report.terms.any { it.component == "Gemma 3" })
        assertTrue(report.choices.any { it.id == "reuse-verified" && !it.requiresDownload && !it.automatic })
        assertTrue(report.choices.any { it.id == "install-or-repair-pinned" && it.requiresDownload && !it.automatic })
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `missing setup reports every absent dependency and keeps MIDI-only choice`() {
        val parent = Files.createTempDirectory("missing-comfy-setup")
        val root = parent.resolve("MelotrailVideo")
        val setup = LocalVideoSetup.fromJson(SetupFixture.profileJson(emptyMap()), MAC_ARM)

        val report = setup.inspect(root)

        assertEquals(LocalVideoSetupState.MISSING, report.state)
        assertNull(report.ready)
        assertTrue(report.issues.size >= 4)
        assertTrue(report.issues.all { it.kind == LocalVideoSetupIssueKind.MISSING })
        assertTrue(report.choices.any { it.id == "leave-unavailable" && !it.requiresDownload })
    }

    @Test
    fun `same-size corruption and source commit mismatch are both reported`() {
        val fixture = SetupFixture.create()
        fixture.files.getValue("models/model.bin").writeText("evil-model")
        fixture.files.getValue("tools/comfy/.git/HEAD").writeText("f".repeat(40))

        val report = fixture.setup.inspect(fixture.root)

        assertEquals(LocalVideoSetupState.CORRUPT, report.state)
        assertNull(report.ready)
        assertTrue(report.issues.any { it.component == "model test model" && it.detail.contains("SHA-256 mismatch") })
        assertTrue(report.issues.any { it.component == "ComfyUI 0.test" && it.detail.contains("commit mismatch") })
    }

    @Test
    fun `resolved Python is pinned while virtual environment imports stay explicit`() {
        val fixture = SetupFixture.create()
        val report = fixture.setup.inspect(fixture.root)
        val ready = assertNotNull(report.ready)

        assertTrue(Files.isSymbolicLink(fixture.pythonLink))
        assertFalse(Files.isSymbolicLink(ready.pythonExecutable))
        assertTrue(ready.pythonSitePackages.startsWith(ready.pythonVirtualEnvironment))

        Files.delete(fixture.pythonTarget)
        fixture.pythonTarget.createFile()
        val corrupt = fixture.setup.inspect(fixture.root)
        assertEquals(LocalVideoSetupState.CORRUPT, corrupt.state)
        assertTrue(corrupt.issues.any { it.component.contains("Python 3.12.0 executable") })
    }

    @Test
    fun `unsupported host is unavailable without inspecting or creating paths`() {
        val parent = Files.createTempDirectory("unsupported-comfy-setup")
        val root = parent.resolve("absent")
        val setup = LocalVideoSetup.fromJson(SetupFixture.profileJson(emptyMap()), LocalVideoHost("Linux", "amd64"))

        val report = setup.inspect(root)

        assertEquals(LocalVideoSetupState.UNSUPPORTED_HOST, report.state)
        assertEquals(LocalVideoSetupIssueKind.UNSUPPORTED_HOST, report.issues.single().kind)
        assertFalse(Files.exists(root))
    }

    @Test
    fun `bundled profile pins Gemma and owned-server restrictions without T5 or cloud`() {
        val text = assertNotNull(javaClass.getResourceAsStream("/video/comfyui/runtime-profile.json"))
            .bufferedReader().use { it.readText() }

        assertTrue(text.contains("40c4fcdf513a4523e39d54a9d391908af8df8171"))
        assertTrue(text.contains("6ea2651e7df66d7585f6ffee804b20e92fb38b8a"))
        assertTrue(text.contains("Gemma 3 12B"))
        assertFalse(text.contains("T5", ignoreCase = true))
        assertTrue(text.contains("--disable-api-nodes"))
        assertTrue(text.contains("--disable-all-custom-nodes"))
        assertTrue(text.contains("--whitelist-custom-nodes"))
        assertFalse(text.contains("cloud", ignoreCase = true))
    }

    @Test
    fun `dirty Comfy and GGUF executable sources are corrupt with unchanged HEAD`() {
        listOf("execution.py", "server.py", "custom_nodes/TestNode/nodes.py").forEach { relative ->
            val fixture = SetupFixture.create()
            assertEquals(LocalVideoSetupState.READY, fixture.setup.inspect(fixture.root).state)
            val head = Files.readString(fixture.files.getValue("tools/comfy/.git/HEAD"))
            fixture.files.getValue("tools/comfy/$relative").writeText("dirty--code")

            val report = fixture.setup.inspect(fixture.root)

            assertEquals(LocalVideoSetupState.CORRUPT, report.state, relative)
            assertNull(report.ready)
            assertTrue(report.issues.any { it.component.startsWith("executable source") && it.detail.contains("SHA-256 mismatch") })
            assertEquals(head, Files.readString(fixture.files.getValue("tools/comfy/.git/HEAD")))
        }
    }

    @Test
    fun `new import source cannot silently join the pinned executable set`() {
        val fixture = SetupFixture.create()
        fixture.root.resolve("tools/comfy/shadow.py").writeText("unexpected")
        val report = fixture.setup.inspect(fixture.root)
        assertEquals(LocalVideoSetupState.CORRUPT, report.state)
        assertTrue(report.issues.any { it.component.startsWith("executable source") })
    }

    @Test
    fun `launch rejects source changed after a ready inspection before starting any child`() {
        val fixture = SetupFixture.create()
        val ready = assertNotNull(fixture.setup.inspect(fixture.root).ready)
        fixture.files.getValue("tools/comfy/server.py").writeText("dirty--code")
        val runtime = ComfyVideoRuntime(
            runProcess = { _, _ -> error("must not launch stale setup") },
            healthProbe = { error("must not probe stale setup") },
            resources = { ComfyHostResources(ComfyMemoryPressure.NORMAL, 0) },
            executor = Executors.newCachedThreadPool(),
            nanoTime = System::nanoTime,
        )
        try {
            val error = assertFailsWith<ComfyVideoRuntimeException> {
                runtime.start(ready, fixture.root.resolve("runs").createDirectories())
            }
            assertEquals(ComfyVideoRuntimeFailure.INVALID_SETUP, error.failure)
        } finally { runtime.close() }
    }

    private data class SetupFixture(
        val root: Path,
        val setup: LocalVideoSetup,
        val files: Map<String, Path>,
        val pythonLink: Path,
        val pythonTarget: Path,
    ) {
        companion object {
            fun create(): SetupFixture {
                val root = Files.createTempDirectory("comfy-setup")
                val text = linkedMapOf(
                    "tools/comfy/main.py" to "pinned-main",
                    "tools/comfy/execution.py" to "pinned-code",
                    "tools/comfy/server.py" to "pinned-code",
                    "tools/comfy/custom_nodes/TestNode/__init__.py" to "pinned-code",
                    "tools/comfy/custom_nodes/TestNode/nodes.py" to "pinned-code",
                    "tools/comfy/.venv/lib/python3.12/site-packages/pkg-1.dist-info/METADATA" to "Name: pkg\nVersion: 1\n",
                    "workflows/test/model-paths.yaml" to "models: test\n",
                    "workflows/test/provenance.json" to "{\"tested\":true}",
                    "models/model.bin" to "test-model",
                )
                val files = text.mapValues { (relative, contents) ->
                    root.resolve(relative).also { it.parent.createDirectories(); it.writeText(contents) }
                }.toMutableMap()
                val comfyHead = root.resolve("tools/comfy/.git/HEAD").also {
                    it.parent.createDirectories(); it.writeText("a".repeat(40))
                }
                files["tools/comfy/.git/HEAD"] = comfyHead
                root.resolve("tools/comfy/custom_nodes/TestNode/.git/HEAD").also {
                    it.parent.createDirectories(); it.writeText("b".repeat(40))
                }
                val pythonTarget = root.resolve("python-real").also {
                    it.writeText("fake-python")
                    it.toFile().setExecutable(true)
                }
                val pythonLink = root.resolve("tools/comfy/.venv/bin/python").also {
                    it.parent.createDirectories()
                    Files.createSymbolicLink(it, pythonTarget)
                }
                val setup = LocalVideoSetup.fromJson(profileJson(files.mapValues { sha(it.value) } + mapOf(
                    "pythonSize" to Files.size(pythonTarget).toString(),
                    "pythonSha" to sha(pythonTarget),
                )), MAC_ARM)
                return SetupFixture(root, setup, files, pythonLink, pythonTarget)
            }

            fun profileJson(values: Map<String, String>): String {
                fun file(relative: String) = """{
                    "relativePath":"$relative",
                    "size":${values[relative]?.let { key -> valuesSize(values, relative) } ?: 0},
                    "sha256":"${values[relative] ?: "0".repeat(64)}"
                }"""
                return """
                    {
                      "schemaVersion":1,
                      "profileId":"test",
                      "applicationSupportRelativePath":"unused",
                      "testedHost":"test mac",
                      "capability":"Pinned short LTX-2.3 image-to-video. Multi-reference conditioning is unproven.",
                      "comfyUi":{
                        "relativePath":"tools/comfy","version":"0.test","commit":"${"a".repeat(40)}",
                        "main":${file("tools/comfy/main.py").replace("tools/comfy/", "")},
                        "files":[],
                        "sourceTrees":[${sourceTree(values, "", listOf("main.py", "execution.py", "server.py"), "\".git\",\".venv\",\"custom_nodes\"")},${sourceTree(values, "custom_nodes/TestNode", listOf("__init__.py", "nodes.py"), "\".git\"")}],
                        "python":{"linkRelativePath":".venv/bin/python","version":"3.12.0","resolvedSize":${values["pythonSize"] ?: 0},"resolvedSha256":"${values["pythonSha"] ?: "0".repeat(64)}","sitePackagesRelativePath":".venv/lib/python3.12/site-packages"},
                        "packages":[{"name":"pkg","version":"1","metadataRelativePath":".venv/lib/python3.12/site-packages/pkg-1.dist-info/METADATA","size":${valuesSize(values, "tools/comfy/.venv/lib/python3.12/site-packages/pkg-1.dist-info/METADATA")},"sha256":"${values["tools/comfy/.venv/lib/python3.12/site-packages/pkg-1.dist-info/METADATA"] ?: "0".repeat(64)}"}],
                        "customNodes":[{"directory":"TestNode","commit":"${"b".repeat(40)}"}]
                      },
                      "workflow":{"relativePath":"workflows/test","modelPaths":${file("workflows/test/model-paths.yaml").replace("workflows/test/", "")},"provenance":${file("workflows/test/provenance.json").replace("workflows/test/", "")}},
                      "modelsRelativePath":"models",
                      "models":[{"name":"test model","relativePath":"model.bin","size":${valuesSize(values, "models/model.bin")},"sha256":"${values["models/model.bin"] ?: "0".repeat(64)}","source":"fixture"}],
                      "server":{"host":"127.0.0.1","defaultPort":8192,"startupTimeoutSeconds":1,"sessionTimeoutSeconds":3,"inferenceTimeoutSeconds":1,"shutdownTimeoutSeconds":1,"additionalSwapLimitBytes":8589934592,"pollIntervalMillis":5,"arguments":["--disable-api-nodes"]},
                      "terms":[{"component":"Gemma 3","terms":"test terms","source":"https://example.test/terms"}]
                    }
                """.trimIndent()
            }

            private fun sourceTree(values: Map<String, String>, relative: String, paths: List<String>, excluded: String): String {
                val prefix = "tools/comfy/" + if (relative.isEmpty()) "" else "$relative/"
                val digest = MessageDigest.getInstance("SHA-256")
                paths.sorted().forEach { path ->
                    digest.update("$path\u0000${valuesSize(values, prefix + path)}\u0000${values[prefix + path] ?: "0".repeat(64)}\n".toByteArray())
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                return """{"relativePath":"$relative","excludedDirectories":[$excluded],"fileCount":${paths.size},"totalBytes":${paths.sumOf { valuesSize(values, prefix + it) }},"sha256":"$sha"}"""
            }

            private fun valuesSize(values: Map<String, String>, relative: String): Long = when (relative) {
                "tools/comfy/execution.py", "tools/comfy/server.py", "tools/comfy/custom_nodes/TestNode/__init__.py", "tools/comfy/custom_nodes/TestNode/nodes.py" -> 11L
                "tools/comfy/main.py" -> "pinned-main".encodeToByteArray().size.toLong()
                "tools/comfy/.venv/lib/python3.12/site-packages/pkg-1.dist-info/METADATA" -> "Name: pkg\nVersion: 1\n".encodeToByteArray().size.toLong()
                "workflows/test/model-paths.yaml" -> "models: test\n".encodeToByteArray().size.toLong()
                "workflows/test/provenance.json" -> "{\"tested\":true}".encodeToByteArray().size.toLong()
                "models/model.bin" -> "test-model".encodeToByteArray().size.toLong()
                else -> if (values.isEmpty()) 0 else error("Unknown fixture $relative")
            }

            private fun sha(path: Path): String = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
        }
    }

    companion object {
        private val MAC_ARM = LocalVideoHost("Mac OS X", "arm64")
    }
}
