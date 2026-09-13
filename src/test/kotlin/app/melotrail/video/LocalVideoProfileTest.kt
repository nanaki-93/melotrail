package app.melotrail.video

import app.melotrail.video.adapter.EvidenceRecordPin
import app.melotrail.video.adapter.LocalVideoEvidenceStatus
import app.melotrail.video.adapter.LocalVideoKeyframeProbe
import app.melotrail.video.adapter.LocalVideoModelWorkspace
import app.melotrail.video.adapter.LocalVideoMediaInspectionStatus
import app.melotrail.video.adapter.LocalVideoProbeExecution
import app.melotrail.video.adapter.LocalVideoProbeProfile
import app.melotrail.video.adapter.LocalVideoProbeRequest
import app.melotrail.video.adapter.LocalVideoProbeRunStatus
import app.melotrail.video.adapter.LocalVideoProbeStatus
import app.melotrail.video.adapter.LocalVideoProbeTake
import app.melotrail.video.adapter.LocalVideoProbeVideo
import app.melotrail.video.adapter.LocalVideoProfileBoundary
import app.melotrail.video.adapter.LocalVideoReferenceDisposition
import app.melotrail.video.adapter.LocalVideoReferencePin
import app.melotrail.video.adapter.LocalVideoReferenceRole
import app.melotrail.video.adapter.PinnedLocalFile
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LocalVideoProfileTest {
    @TempDir lateinit var root: Path

    @Test
    fun `bundled profile is explicitly candidate and contains no invented evidence`() {
        val profile = LocalVideoProfileBoundary.loadBundledProfile()

        assertEquals("draw-things-local-candidate-v1", profile.id)
        assertEquals(LocalVideoEvidenceStatus.CANDIDATE_UNVERIFIED, profile.evidence.status)
        assertNull(profile.evidence.measurementRecord)
        assertNull(profile.evidence.selectionRecord)
        assertEquals(listOf("ltx-2.3-distilled-candidate"), profile.videoProfiles.map { it.id })
        assertTrue(profile.videoProfiles.all { it.evidence.status == LocalVideoEvidenceStatus.CANDIDATE_UNVERIFIED })
        assertTrue(profile.videoProfiles.all { it.evidence.measurementRecord == null && it.evidence.selectionRecord == null })
        assertEquals("v1.20260430.0", profile.backend.release)
        assertEquals("7e5fb3af7dd99916d7671354a11fd40182bc6a0d16e7faf06fda7740c24915cd", profile.backend.executableSha256)
        assertEquals(1, profile.backend.maxInputImagesPerInvocation)
        assertFalse(profile.backend.supportsMultipleReferenceRoles)
        assertEquals(8, profile.videoProfiles.single().requiredModels.size)
        assertEquals(46_321_545_216L, profile.videoProfiles.single().requiredModels.sumOf { it.bytes })
    }

    @Test
    fun `real request with multiple reference roles writes unsupported report before launch`() {
        val fixture = fixture()
        val secondReferencePath = Files.writeString(root.resolve("style.png"), "owned-style")
        val request = fixture.request.copy(
            references = fixture.request.references + LocalVideoReferencePin(
                "style-reference",
                pin("style-image", secondReferencePath, "owned-style"),
                LocalVideoReferenceRole.STYLE,
            ),
            profiles = listOf(fixture.request.profiles.single().copy(
                referenceBindings = listOf("subject-reference", "style-reference"),
            )),
            execution = LocalVideoProbeExecution(
                toolRelease = "v1.20260430.0",
                sourceRevision = "a187a319a7c13a97d4100d4a824cf9e2747094b2",
                modelsDirectory = root.toString(),
                timeoutSecondsPerStage = 10,
            ),
        )

        val report = LocalVideoProfileBoundary.run(LocalVideoProfileBoundary.loadBundledProfile(), request)

        assertEquals(LocalVideoProbeRunStatus.FAILED, report.status)
        assertTrue(report.reason.contains("exactly one"))
        assertTrue(report.stages.isEmpty())
        assertTrue(report.referenceUsage.all { it.disposition == LocalVideoReferenceDisposition.NOT_CONSUMED })
        assertTrue(Files.isRegularFile(Path.of(request.reportPath)))
        Files.list(fixture.outputDirectory).use { outputs ->
            assertEquals(listOf(Path.of(request.reportPath)), outputs.toList())
        }
        assertEquals("owned-tool-stub", Files.readString(Path.of(request.tool.path)))
    }

    @Test
    fun `mutating CLI success preserves installed models and records private sidecars`() = mutatingCli("success")

    @Test
    fun `mutating CLI failure preserves keyframe models and derived evidence`() = mutatingCli("failure")

    @Test
    fun `mutating CLI cancellation preserves keyframe models and derived evidence`() = mutatingCli("cancel")

    private fun mutatingCli(mode: String) {
        val tool = Files.writeString(root.resolve("fixture-draw-things-cli"), """#!/bin/sh
            output=''
            image=''
            models=''
            model=''
            offline=0
            no_download=0
            while [ "${'$'}#" -gt 0 ]; do
              case "${'$'}1" in
                generate) shift ;;
                --output) output="${'$'}2"; shift 2 ;;
                --image) image="${'$'}2"; shift 2 ;;
                --models-dir) models="${'$'}2"; shift 2 ;;
                --model) model="${'$'}2"; shift 2 ;;
                --offline) offline=1; shift ;;
                --no-download-missing) no_download=1; shift ;;
                --disable-preview) shift ;;
                *) shift 2 ;;
              esac
            done
            [ "${'$'}offline" = 1 ] && [ "${'$'}no_download" = 1 ] || exit 17
            # Faithful failure mechanism: mutate the opened checkpoint and add adjacent tensor storage.
            /bin/cp "${'$'}models/${'$'}model" "${'$'}models/${'$'}model-tensordata" || exit 20
            /usr/bin/printf 'SQLite metadata fixture' > "${'$'}models/${'$'}model"
            case "${'$'}output" in
              *.mp4)
                if [ '$mode' = 'failure' ]; then exit 19; fi
                if [ '$mode' = 'cancel' ]; then
                  /usr/bin/touch "${'$'}{output%/*}/../cancel-probe"
                  while :; do /bin/sleep 1; done
                fi ;;
            esac
            case "${'$'}output" in
              *.png) /bin/cp "${'$'}image" "${'$'}output" ;;
              *.mp4) /usr/bin/printf 'owned fake video from %s' "${'$'}image" > "${'$'}output" ;;
              *) exit 18 ;;
            esac
        """.trimIndent())
        Files.setPosixFilePermissions(tool, PosixFilePermissions.fromString("rwx------"))
        val modelsDirectory = Files.createDirectory(root.resolve("fixture models"))
        val bundled = LocalVideoProfileBoundary.loadBundledProfile()
        val bundledCandidate = bundled.videoProfiles.single()
        val fakeModels = bundledCandidate.requiredModels.map { required ->
            val path = Files.writeString(modelsDirectory.resolve(required.fileName), "fake-${required.id}")
            PinnedLocalFile(required.id, path.toString(), digest(Files.readAllBytes(path)))
        }
        val sourceBefore = fakeModels.associate { it.id to Files.readAllBytes(Path.of(it.path)) }
        // Neither an unpinned checkpoint nor source custom registry may enter the runtime bundle.
        Files.writeString(modelsDirectory.resolve("unapproved.ckpt"), "must not be read")
        Files.writeString(modelsDirectory.resolve("custom.json"), "unapproved model override")
        val fakeById = fakeModels.associateBy { it.id }
        val fakeCandidate = bundledCandidate.copy(requiredModels = bundledCandidate.requiredModels.map { required ->
            val pin = checkNotNull(fakeById[required.id])
            required.copy(sha256 = pin.sha256, bytes = Files.size(Path.of(pin.path)))
        })
        val fakeProfile = bundled.copy(
            backend = bundled.backend.copy(
                release = "fixture-release",
                executableSha256 = digest(Files.readAllBytes(tool)),
                sourceRevision = "1".repeat(40),
            ),
            videoProfiles = listOf(fakeCandidate),
        )
        val reference = Files.writeString(root.resolve("reference fixture.png"), "owned pixels")
        val output = root.resolve("actual-output")
        val request = LocalVideoProbeRequest(
            schema = LocalVideoProfileBoundary.REQUEST_SCHEMA,
            version = 1,
            profileId = fakeProfile.id,
            backendId = fakeProfile.backend.id,
            tool = PinnedLocalFile("draw-things-cli", tool.toString(), digest(Files.readAllBytes(tool))),
            prompt = "An owned fixture prompt with spaces.",
            references = listOf(LocalVideoReferencePin(
                "complete-scene",
                PinnedLocalFile("reference-image", reference.toString(), digest(Files.readAllBytes(reference))),
                LocalVideoReferenceRole.COMPLETE_SCENE,
            )),
            profiles = listOf(LocalVideoProbeProfile(
                profileId = fakeCandidate.id,
                modelPins = fakeModels,
                referenceBindings = listOf("complete-scene"),
                takes = listOf(LocalVideoProbeTake("take-1", output.resolve("take-1.mp4").toString(), 22)),
                keyframe = LocalVideoKeyframeProbe(
                    fakeCandidate.keyframeModelId,
                    "complete-scene",
                    output.resolve("keyframe.png").toString(),
                    512,
                    320,
                    4,
                    1.0,
                    0.4,
                    11,
                ),
                video = LocalVideoProbeVideo(
                    fakeCandidate.videoModelId,
                    512,
                    320,
                    129,
                    25.0,
                    8,
                    1.0,
                    1.0,
                ),
            )),
            outputDirectory = output.toString(),
            reportPath = output.resolve("probe-report.json").toString(),
            execution = LocalVideoProbeExecution(
                "fixture-release",
                "1".repeat(40),
                modelsDirectory.toString(),
                10,
                cancellationFile = root.resolve("cancel-probe").toString(),
            ),
        )

        val report = LocalVideoProfileBoundary.run(fakeProfile, request)

        assertEquals(
            if (mode == "success") LocalVideoProbeRunStatus.COMPLETED_UNREVIEWED else LocalVideoProbeRunStatus.FAILED,
            report.status,
        )
        assertEquals(2, report.stages.size)
        if (mode != "success") {
            assertEquals(if (mode == "cancel") "CANCELLED" else "NONZERO_EXIT", report.stages.last().failure?.type)
            assertTrue(Files.exists(output.resolve("keyframe.png")))
        }
        val workspace = checkNotNull(report.modelWorkspace)
        assertNull(workspace.failure)
        assertEquals(8, workspace.copies.size)
        assertTrue(workspace.sourceAudit.all { it.matchesPin })
        val staged = Path.of(workspace.directory)
        assertEquals(output.resolve(".models"), staged)
        assertFalse(Files.exists(staged.resolve("unapproved.ckpt")))
        assertEquals("[]\n", Files.readString(staged.resolve("custom.json")))
        for (pin in fakeModels) {
            assertContentEquals(sourceBefore.getValue(pin.id), Files.readAllBytes(Path.of(pin.path)))
            assertFalse(Files.isSameFile(Path.of(pin.path), staged.resolve(pin.id)))
            assertFalse(Files.exists(modelsDirectory.resolve("${pin.id}-tensordata")))
        }
        for (id in listOf(fakeCandidate.keyframeModelId, fakeCandidate.videoModelId)) {
            assertEquals("SQLite metadata fixture", Files.readString(staged.resolve(id)))
            val sidecar = workspace.derivedArtifacts.single { it.path.endsWith("/$id-tensordata") }
            assertEquals(id, sidecar.sourceModelId)
            assertEquals(fakeById.getValue(id).sha256, sidecar.sha256)
        }
        assertTrue(report.stages.all {
            it.arguments[it.arguments.indexOf("--models-dir") + 1] == staged.toString()
        })
        assertTrue(Files.readString(Path.of(request.reportPath)).contains("derivedArtifacts"))
        assertTrue(Files.isRegularFile(output.resolve(".model-staging.json")))
        assertTrue(report.stages.all { it.arguments.contains("--offline") && it.arguments.contains("--no-download-missing") })
        assertTrue(report.stages.all { it.arguments.contains("--negative-prompt") && it.arguments.contains("--config-json") })
        assertTrue(report.stages.all { it.arguments.none { argument -> argument == "--config-file" } })
        assertTrue(report.stages.first().output != null)
        if (mode == "success") assertEquals(5_160L, report.stages.last().output?.requestedDurationMillis)
        assertEquals(
            LocalVideoReferenceDisposition.PIXEL_CONDITIONED,
            report.referenceUsage.single().disposition,
        )
        assertEquals(LocalVideoMediaInspectionStatus.PENDING_EXTERNAL_INSPECTION, report.mediaInspection.status)
        assertNull(report.mediaInspection.actualDurationMillis)
        assertNull(report.mediaInspection.audioStreamPresent)
        assertNull(report.selectedProfileId)
        assertContentEquals("owned pixels".encodeToByteArray(), Files.readAllBytes(reference))
    }

    @Test
    fun `model staging rejects disk shortage and cancellation without touching source`() {
        val source = Files.writeString(root.resolve("model.ckpt"), "owned original")
        val pin = pin("model.ckpt", source, "owned original")
        val lowDisk = Files.createDirectory(root.resolve("low-disk"))
        val workspace = LocalVideoModelWorkspace(lowDisk, listOf(pin), null, usableSpace = { 0L })
        assertTrue(assertFailsWith<IllegalArgumentException> { workspace.stage() }.message!!.contains("free bytes"))
        assertFalse(Files.exists(workspace.directory))
        assertTrue(workspace.inspect().sourceAudit.single().matchesPin)
        val cancel = Files.writeString(root.resolve("cancel"), "cancel")
        val cancelled = LocalVideoModelWorkspace(Files.createDirectory(root.resolve("cancelled")), listOf(pin), cancel)
        assertFailsWith<IllegalStateException> { cancelled.stage() }
        assertFalse(Files.exists(cancelled.directory))
        assertEquals("owned original", Files.readString(source))
    }

    @Test
    fun `staging verifies actual copied bytes and rejects missing model fallback`() {
        val source = Files.writeString(root.resolve("model.ckpt"), "owned original")
        val pin = pin("model.ckpt", source, "owned original")
        val output = Files.createDirectory(root.resolve("staged"))
        val workspace = LocalVideoModelWorkspace(output, listOf(pin), null)
        workspace.stage()
        workspace.requireReady()
        assertNull(workspace.resourceIssue())
        Files.writeString(workspace.directory.resolve("custom.json"), "[unapproved override]")
        assertTrue(assertFailsWith<IllegalArgumentException> { workspace.requireReady() }.message!!.contains("registry"))
        Files.writeString(workspace.directory.resolve("custom.json"), "[]\n")
        Files.delete(workspace.directory.resolve("model.ckpt"))
        assertTrue(assertFailsWith<IllegalArgumentException> { workspace.requireReady() }.message!!.contains("fallback"))
        assertEquals("owned original", Files.readString(source))
        Files.writeString(source, "changed since preparation")
        val changed = LocalVideoModelWorkspace(Files.createDirectory(root.resolve("changed")), listOf(pin), null)
        assertTrue(assertFailsWith<IllegalArgumentException> { changed.stage() }.message!!.contains("pin changed"))
        assertTrue(Files.isRegularFile(changed.directory.resolve("model.ckpt")))
        assertFalse(changed.inspect().sourceAudit.single().matchesPin)
    }

    @Test
    fun `complete owned request validates pins and remains NOT_RUN without touching inputs or outputs`() {
        val fixture = fixture()
        val referenceBefore = Files.readAllBytes(fixture.reference)
        val report = LocalVideoProfileBoundary.prepare(LocalVideoProfileBoundary.loadBundledProfile(), fixture.request)

        assertEquals(LocalVideoProbeStatus.NOT_RUN, report.status)
        assertTrue(report.reason.contains("V11"))
        assertEquals(3, report.validatedInputPins)
        assertEquals(1, report.preparedVideoProfiles)
        assertEquals(2, report.preparedTakes)
        assertNull(report.measurements)
        assertNull(report.selectedProfileId)
        assertContentEquals(referenceBefore, Files.readAllBytes(fixture.reference))
        assertFalse(Files.exists(fixture.outputDirectory))
        assertTrue(report.outputLocations.all { Path.of(it).startsWith(fixture.outputDirectory) })
    }

    @Test
    fun `missing and mismatched tool model and reference pins fail actionably`() {
        val fixture = fixture()
        val missingModel = fixture.request.copy(profiles = listOf(fixture.request.profiles.single().copy(
            modelPins = listOf(pin("model", root.resolve("missing-model.bin"), "model")),
        )))
        assertTrue(failure(missingModel).contains("Model"))
        assertTrue(failure(missingModel).contains("missing"))

        val mismatchedTool = fixture.request.copy(tool = fixture.request.tool.copy(sha256 = "0".repeat(64)))
        assertTrue(failure(mismatchedTool).contains("Tool pin mismatch"))

        val mismatchedReference = fixture.request.copy(references = listOf(
            fixture.request.references.single().copy(pin = fixture.request.references.single().pin.copy(sha256 = "f".repeat(64))),
        ))
        assertTrue(failure(mismatchedReference).contains("Reference"))
        assertContentEquals("owned-reference".encodeToByteArray(), Files.readAllBytes(fixture.reference))
    }

    @Test
    fun `request bounds profiles takes prompt and reference bindings`() {
        val fixture = fixture()
        val run = fixture.request.profiles.single()
        val tooManyProfiles = fixture.request.copy(profiles = listOf(
            run,
            run.copy(profileId = "candidate-two"),
            run.copy(profileId = "candidate-three"),
        ))
        assertTrue(failure(tooManyProfiles).contains("between one and 2"))

        val tooManyTakes = fixture.request.copy(profiles = listOf(run.copy(takes = (1..4).map {
            LocalVideoProbeTake("take-$it", fixture.outputDirectory.resolve("take-$it.mp4").toString())
        })))
        assertTrue(failure(tooManyTakes).contains("between one and 3"))
        assertTrue(failure(fixture.request.copy(prompt = "  ")).contains("Prompt"))
        assertTrue(failure(fixture.request.copy(references = emptyList())).contains("at least one pinned reference"))
        assertTrue(failure(fixture.request.copy(profiles = listOf(run.copy(referenceBindings = listOf("unknown")))))
            .contains("unknown reference binding"))

        val optionalMetadata = fixture.request.copy(
            references = listOf(fixture.request.references.single().copy(role = null)),
            profiles = listOf(run.copy(referenceBindings = emptyList())),
        )
        assertEquals(LocalVideoProbeStatus.NOT_RUN, LocalVideoProfileBoundary.prepare(
            LocalVideoProfileBoundary.loadBundledProfile(),
            optionalMetadata,
        ).status)
    }

    @Test
    fun `existing duplicate and input-containing output locations are rejected without replacement`() {
        val fixture = fixture()
        Files.createDirectories(fixture.outputDirectory)
        val existing = Path.of(fixture.request.profiles.single().takes.first().outputPath)
        Files.writeString(existing, "keep-me")
        assertTrue(failure(fixture.request).contains("Refusing to replace existing output"))
        assertEquals("keep-me", Files.readString(existing))

        Files.delete(existing)
        val firstOutput = fixture.request.profiles.single().takes.first().outputPath
        val duplicate = fixture.request.copy(reportPath = firstOutput)
        assertTrue(failure(duplicate).contains("distinct output path"))

        val inputContainingDirectory = fixture.request.copy(
            outputDirectory = root.toString(),
            reportPath = root.resolve("report.json").toString(),
        )
        assertTrue(failure(inputContainingDirectory).contains("contains a pinned"))
    }

    @Test
    fun `report and take cannot be ancestors of each other even when both are absent`() {
        val fixture = fixture()
        val parent = fixture.outputDirectory.resolve("result")
        val child = parent.resolve("take.mp4")
        listOf(parent to child, child to parent).forEach { (reportPath, takePath) ->
            val request = fixture.request.copy(
                reportPath = reportPath.toString(),
                profiles = listOf(fixture.request.profiles.single().copy(
                    takes = listOf(LocalVideoProbeTake("take-1", takePath.toString())),
                )),
            )
            assertTrue(failure(request).contains("ancestor/descendant overlap"))
            assertFalse(Files.exists(fixture.outputDirectory))
        }
    }

    @Test
    fun `absent case-only output aliases and ancestors are rejected without writes`() {
        val fixture = fixture()
        val collisions = listOf(
            "Result" to "result",
            "Result" to "result/take.mp4",
            "Result/report.json" to "result",
            "Group/Result" to "group/result/take.mp4",
        )
        collisions.forEach { (reportName, takeName) ->
            val request = fixture.request.copy(
                reportPath = fixture.outputDirectory.resolve(reportName).toString(),
                profiles = listOf(fixture.request.profiles.single().copy(
                    takes = listOf(LocalVideoProbeTake("take-1", fixture.outputDirectory.resolve(takeName).toString())),
                )),
            )
            assertTrue(failure(request).contains("distinct output path"), "$reportName and $takeName")
            assertFalse(Files.exists(fixture.outputDirectory))
        }
        assertContentEquals("owned-reference".encodeToByteArray(), Files.readAllBytes(fixture.reference))
        assertEquals("owned-tool-stub", Files.readString(Path.of(fixture.request.tool.path)))
        assertEquals("owned-model-stub", Files.readString(Path.of(fixture.request.profiles.single().modelPins.single().path)))
    }

    @Test
    fun `absent Unicode components fail portable naming policy before any write`() {
        val fixture = fixture()
        // Both spellings are rejected, including when the Unicode name is an absent ancestor.
        val composed = "caf\u00e9"
        val decomposed = "cafe\u0301"
        listOf(composed, decomposed, "$composed/report.json", "$decomposed/report.json").forEach { name ->
            assertTrue(failure(fixture.request.copy(
                reportPath = fixture.outputDirectory.resolve(name).toString(),
            )).contains("portable ASCII"))
            assertFalse(Files.exists(fixture.outputDirectory))
        }
        assertContentEquals("owned-reference".encodeToByteArray(), Files.readAllBytes(fixture.reference))
    }

    @Test
    fun `distinct portable siblings retain spelling under existing Unicode directories`() {
        val fixture = fixture()
        val existing = Files.createDirectory(root.resolve("caf\u00e9"))
        val reference = Files.copy(fixture.reference, root.resolve("r\u00e9f\u00e9rence.png"))
        val outputs = existing.resolve("Probe-Outputs")
        val reportPath = outputs.resolve("Result.JSON").toString()
        val takePath = outputs.resolve("Results.MP4").toString()
        val request = fixture.request.copy(
            outputDirectory = outputs.toString(),
            reportPath = reportPath,
            references = listOf(fixture.request.references.single().let {
                it.copy(pin = it.pin.copy(path = reference.toString()))
            }),
            profiles = listOf(fixture.request.profiles.single().copy(
                takes = listOf(LocalVideoProbeTake("take-1", takePath)),
            )),
        )

        val report = LocalVideoProfileBoundary.prepare(LocalVideoProfileBoundary.loadBundledProfile(), request)

        assertEquals(LocalVideoProbeStatus.NOT_RUN, report.status)
        assertEquals(listOf(reportPath, takePath), report.outputLocations)
        assertFalse(Files.exists(outputs))
        Files.list(existing).use { assertEquals(0L, it.count()) }
        assertContentEquals("owned-reference".encodeToByteArray(), Files.readAllBytes(reference))
    }

    @Test
    fun `Unicode directory aliases use existing file identity or reject an absent Unicode component`() {
        val fixture = fixture()
        val existing = Files.createDirectories(fixture.outputDirectory.resolve("Caf\u00e9"))
        val alias = fixture.outputDirectory.resolve("cafe\u0301")
        val request = fixture.request.copy(
            reportPath = existing.resolve("result").toString(),
            profiles = listOf(fixture.request.profiles.single().copy(
                takes = listOf(LocalVideoProbeTake("take-1", alias.resolve("result/take.mp4").toString())),
            )),
        )

        // Read the native identity of owned fixture data; do not assume a volume's case/Unicode rules.
        val expectedFailure = if (Files.exists(alias) && Files.isSameFile(existing, alias)) {
            "distinct output path"
        } else {
            "portable ASCII"
        }
        assertTrue(failure(request).contains(expectedFailure))
        Files.list(existing).use { assertEquals(0L, it.count()) }
        assertContentEquals("owned-reference".encodeToByteArray(), Files.readAllBytes(fixture.reference))
    }

    @Test
    fun `canonical output aliases and nested paths through symlink ancestors are rejected`() {
        val fixture = fixture()
        val realDirectory = Files.createDirectories(fixture.outputDirectory.resolve("real/child"))
        val alias = Files.createSymbolicLink(fixture.outputDirectory.resolve("alias"), realDirectory.parent)
        val realOutput = realDirectory.resolve("result")
        val aliasOutput = alias.resolve("child/result")
        val collisions = listOf(
            realOutput to aliasOutput,
            realOutput to aliasOutput.resolve("take.mp4"),
            realOutput.resolve("report.json") to aliasOutput,
        )
        collisions.forEach { (reportPath, takePath) ->
            val request = fixture.request.copy(
                reportPath = reportPath.toString(),
                profiles = listOf(fixture.request.profiles.single().copy(
                    takes = listOf(LocalVideoProbeTake("take-1", takePath.toString())),
                )),
            )
            assertTrue(failure(request).contains("distinct output path"))
            assertFalse(Files.exists(realOutput))
            assertTrue(Files.isSymbolicLink(alias))
        }

        val distinctSiblings = fixture.request.copy(
            reportPath = realDirectory.resolve("report.json").toString(),
            profiles = listOf(fixture.request.profiles.single().copy(
                takes = listOf(LocalVideoProbeTake("take-1", alias.resolve("child/take.mp4").toString())),
            )),
        )
        assertEquals(LocalVideoProbeStatus.NOT_RUN, LocalVideoProfileBoundary.prepare(
            LocalVideoProfileBoundary.loadBundledProfile(), distinctSiblings,
        ).status)
        Files.list(realDirectory).use { assertEquals(0L, it.count()) }
    }

    @Test
    fun `outputs cannot escape their directory through an existing symlink ancestor`() {
        val fixture = fixture()
        Files.createDirectories(fixture.outputDirectory)
        val outside = Files.createDirectories(root.resolve("outside/child"))
        val alias = Files.createSymbolicLink(fixture.outputDirectory.resolve("alias"), outside.parent)
        val request = fixture.request.copy(reportPath = alias.resolve("child/report.json").toString())

        assertTrue(failure(request).contains("must resolve to a child of outputDirectory"))
        Files.list(outside).use { assertEquals(0L, it.count()) }
        assertTrue(Files.isSymbolicLink(alias))
        assertContentEquals("owned-reference".encodeToByteArray(), Files.readAllBytes(fixture.reference))
    }

    @Test
    fun `symlink followed by dot-dot cannot redirect report take or output directory`() {
        val fixture = fixture()
        Files.createDirectory(fixture.outputDirectory)
        val outsideChild = Files.createDirectories(root.resolve("outside/child"))
        val alias = Files.createSymbolicLink(fixture.outputDirectory.resolve("link"), outsideChild)
        val redirectedParent = alias.resolve("..")
        // Native resolution leaves outputs, whereas lexical normalization would hide the escape.
        assertEquals(outsideChild.parent.toRealPath(), redirectedParent.toRealPath())
        assertEquals(fixture.outputDirectory, redirectedParent.normalize())

        val run = fixture.request.profiles.single()
        val planned = fixture.outputDirectory.resolve("planned")
        val requests = listOf(
            "reportPath" to fixture.request.copy(reportPath = redirectedParent.resolve("report.json").toString()),
            "takePath" to fixture.request.copy(profiles = listOf(run.copy(
                takes = listOf(LocalVideoProbeTake("take-1", redirectedParent.resolve("take.mp4").toString())),
            ))),
            "outputDirectory" to fixture.request.copy(
                outputDirectory = redirectedParent.resolve("planned").toString(),
                reportPath = planned.resolve("report.json").toString(),
                profiles = listOf(run.copy(
                    takes = listOf(LocalVideoProbeTake("take-1", planned.resolve("take.mp4").toString())),
                )),
            ),
        )
        val before = snapshotOwnedTree()
        requests.forEach { (field, request) ->
            assertTrue(failure(request).contains("without '.' or '..' path components"), field)
            assertEquals(before, snapshotOwnedTree(), "$field must preserve inputs and create no outputs")
        }
    }

    @Test
    fun `symlink followed by dot-dot cannot substitute tool model or reference pins`() {
        val fixture = fixture()
        val outsideChild = Files.createDirectories(root.resolve("outside/child"))
        val alias = Files.createSymbolicLink(root.resolve("link"), outsideChild)
        val pins = listOf(fixture.request.tool, fixture.request.references.single().pin) +
            fixture.request.profiles.single().modelPins
        pins.forEach { pin ->
            val original = Path.of(pin.path)
            val nativeFile = Files.writeString(outsideChild.parent.resolve(original.fileName), "different-${pin.id}")
            val raw = alias.resolve("..").resolve(original.fileName)
            assertEquals(nativeFile.toRealPath(), raw.toRealPath())
            assertEquals(original, raw.normalize())
            assertTrue(digest(Files.readAllBytes(raw)) != pin.sha256)
        }
        val requests = requestsWithRewrittenPaths(fixture.request) { raw ->
            alias.resolve("..").resolve(Path.of(raw).fileName).toString()
        }.filter { (field, _) -> field in setOf("tool", "model", "reference") }
        val before = snapshotOwnedTree()
        requests.forEach { (field, request) ->
            assertTrue(failure(request).contains("without '.' or '..' path components"), field)
            assertEquals(before, snapshotOwnedTree(), "$field must preserve both named and lexical input targets")
            assertFalse(Files.exists(fixture.outputDirectory))
        }
    }

    @Test
    fun `all pin and output fields reject ordinary dot and dot-dot components without writes`() {
        val fixture = fixture()
        val before = snapshotOwnedTree()
        listOf(".", "missing/..").forEach { component ->
            requestsWithRewrittenPaths(fixture.request) { raw ->
                val path = Path.of(raw)
                path.parent.resolve(component).resolve(path.fileName).toString()
            }.forEach { (field, request) ->
                assertTrue(failure(request).contains("without '.' or '..' path components"), "$field: $component")
                assertEquals(before, snapshotOwnedTree(), "$field: $component must preserve inputs and create no outputs")
                assertFalse(Files.exists(fixture.outputDirectory))
            }
        }
    }

    @Test
    fun `candidate evidence cannot masquerade as measured or selected facts`() {
        val fixture = fixture()
        val profile = LocalVideoProfileBoundary.loadBundledProfile().let {
            it.copy(evidence = it.evidence.copy(
                measurementRecord = EvidenceRecordPin("invented", "a".repeat(64)),
            ))
        }
        val error = assertFailsWith<IllegalArgumentException> {
            LocalVideoProfileBoundary.prepare(profile, fixture.request)
        }
        assertTrue(error.message.orEmpty().contains("cannot contain measured or selected facts"))
    }

    @Test
    fun `request JSON rejects unsupported versions unknown fields and relative paths`() {
        val wrongVersion = """{
            "schema":"melotrail-local-video-probe-request","version":2,
            "profileId":"draw-things-local-candidate-v1","backendId":"draw-things-cli",
            "tool":{"id":"tool","path":"/tmp/tool","sha256":"${"a".repeat(64)}"},
            "prompt":"prompt","references":[],"profiles":[],
            "outputDirectory":"/tmp/out","reportPath":"/tmp/out/report.json"
        }"""
        assertTrue(assertFailsWith<IllegalArgumentException> {
            LocalVideoProfileBoundary.decodeRequest(wrongVersion)
        }.message.orEmpty().contains("Unsupported"))
        val unknownField = wrongVersion.replace(
            "\"version\":2,",
            "\"version\":1,\"unexpected\":true,",
        )
        assertFailsWith<IllegalArgumentException> {
            LocalVideoProfileBoundary.decodeRequest(unknownField)
        }

        val fixture = fixture()
        assertTrue(failure(fixture.request.copy(tool = fixture.request.tool.copy(path = "relative/tool")))
            .contains("must be absolute"))
    }

    private fun requestsWithRewrittenPaths(
        request: LocalVideoProbeRequest,
        rewrite: (String) -> String,
    ): List<Pair<String, LocalVideoProbeRequest>> {
        val run = request.profiles.single()
        val model = run.modelPins.single()
        val reference = request.references.single()
        val take = run.takes.first()
        return listOf(
            "tool" to request.copy(tool = request.tool.copy(path = rewrite(request.tool.path))),
            "model" to request.copy(profiles = listOf(run.copy(
                modelPins = listOf(model.copy(path = rewrite(model.path))),
            ))),
            "reference" to request.copy(references = listOf(reference.copy(
                pin = reference.pin.copy(path = rewrite(reference.pin.path)),
            ))),
            "outputDirectory" to request.copy(outputDirectory = rewrite(request.outputDirectory)),
            "reportPath" to request.copy(reportPath = rewrite(request.reportPath)),
            "takePath" to request.copy(profiles = listOf(run.copy(
                takes = listOf(take.copy(outputPath = rewrite(take.outputPath))) + run.takes.drop(1),
            ))),
        )
    }

    private fun snapshotOwnedTree(): Map<Path, String> = Files.walk(root).use { paths ->
        paths.toList().associateWith { path ->
            when {
                Files.isSymbolicLink(path) -> "symlink:${Files.readSymbolicLink(path)}"
                Files.isDirectory(path, NOFOLLOW_LINKS) -> "directory"
                else -> "file:${digest(Files.readAllBytes(path))}"
            }
        }
    }

    private fun fixture(): Fixture {
        val tool = Files.writeString(root.resolve("draw-things-cli"), "owned-tool-stub")
        val model = Files.writeString(root.resolve("model.bin"), "owned-model-stub")
        val reference = Files.writeString(root.resolve("reference.png"), "owned-reference")
        val outputDirectory = root.resolve("outputs")
        val request = LocalVideoProbeRequest(
            schema = LocalVideoProfileBoundary.REQUEST_SCHEMA,
            version = LocalVideoProfileBoundary.SCHEMA_VERSION,
            profileId = "draw-things-local-candidate-v1",
            backendId = LocalVideoProfileBoundary.SUPPORTED_BACKEND_ID,
            tool = pin("draw-things-cli", tool, "owned-tool-stub"),
            prompt = "Animate the supplied subject turning toward a softly moving landscape.",
            references = listOf(LocalVideoReferencePin(
                id = "subject-reference",
                pin = pin("reference-image", reference, "owned-reference"),
                role = LocalVideoReferenceRole.SUBJECT,
            )),
            profiles = listOf(LocalVideoProbeProfile(
                profileId = "ltx-2.3-distilled-candidate",
                modelPins = listOf(pin("model-bundle", model, "owned-model-stub")),
                referenceBindings = listOf("subject-reference"),
                takes = listOf(
                    LocalVideoProbeTake("take-1", outputDirectory.resolve("take-1.mp4").toString()),
                    LocalVideoProbeTake("take-2", outputDirectory.resolve("take-2.mp4").toString()),
                ),
            )),
            outputDirectory = outputDirectory.toString(),
            reportPath = outputDirectory.resolve("preparation-report.json").toString(),
        )
        return Fixture(request, reference, outputDirectory)
    }

    private fun pin(id: String, path: Path, contents: String): PinnedLocalFile =
        PinnedLocalFile(id, path.toAbsolutePath().toString(), digest(contents.encodeToByteArray()))

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun failure(request: LocalVideoProbeRequest): String = assertFailsWith<IllegalArgumentException> {
        LocalVideoProfileBoundary.prepare(LocalVideoProfileBoundary.loadBundledProfile(), request)
    }.message.orEmpty()

    private data class Fixture(
        val request: LocalVideoProbeRequest,
        val reference: Path,
        val outputDirectory: Path,
    )
}
