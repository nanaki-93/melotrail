package app.melotrail.desktop

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A self-contained, local six-page review packet. Rendering never grants human approval. */
class MidiCoreVisualReviewTest {
    @Test
    fun `prepare all six reference actual target and difference comparisons with unscored review form`() {
        val output = U07Evidence.root.resolve("visual-review")
        Files.createDirectories(output)
        val referenceRoot = U07Evidence.repository().resolve("docs/pictures/UI")
        val measurements = Json.parseToJsonElement(Files.readString(referenceRoot.resolve("reference-measurements.json"))).jsonObject
        val references = measurements.getValue("referencePageLandmarks").jsonObject
        val referenceDigests = measurements.getValue("referenceManifest").jsonArray.associate {
            it.jsonObject.getValue("file").jsonPrimitive.content to it.jsonObject.getValue("sha256").jsonPrimitive.content
        }
        val entries = mutableListOf<JsonObject>()
        val pages = mutableListOf<JsonObject>()
        val html = StringBuilder("""<!doctype html><html lang="en"><meta charset="utf-8">
            <title>Melotrail six-page visual review</title>
            <style>body{font:16px system-ui;margin:24px;background:#f4f4f6;color:#171320}a{color:#49346b}
            nav{position:sticky;top:0;background:#fff;padding:12px;display:flex;gap:18px;flex-wrap:wrap}
            figure{margin:0}img{max-width:100%;height:auto}summary{cursor:pointer;padding:10px;font-weight:600}
            .comparison{display:grid;grid-template-columns:repeat(3,minmax(240px,1fr));gap:12px;overflow:auto}
            .comparison img{min-width:240px}section{border-top:2px solid #ccc;margin-top:32px}td,th{padding:8px;text-align:left}
            </style><h1>Melotrail: six-page visual review</h1>
            <p>Human decision: <strong>not recorded</strong>. These are technical captures from the production pages
            using owned presentation fixtures. The original reference shows design intent; the target is the existing
            regression baseline. A pixel match does not approve the design.</p>
            <p>For each page, record hierarchy, reference fidelity and usability (1–5), exact comments,
            reviewer, date and decision in the unscored <a href="review.json">review form</a>.
            Target: at least 4/5 in each category on every page. Review all three sizes and the stress captures.
            Acoustic onset is unmeasured. Listening and Logic results are separate.</p>
            <p><a href="../responsiveness.json">64-bar timing measurements</a> ·
            <a href="../meter-smoke.json">3/4 and 6/8 smoke</a> ·
            <a href="../native/observations.json">Real-window density, geometry and frame replay (not screen pixels)</a>.
            These links require their focused tests to execute in the same candidate tree.</p><nav>
            """.trimIndent())
        midiCoreWorkspaceDestinations.forEach { html.append("<a href=\"#${it.route}\">${it.label}</a>") }
        html.append("</nav>")
        midiCoreWorkspaceDestinations.forEach { destination ->
            val key = if (destination == MidiCoreWorkspaceDestination.STRUCTURE_HARMONY) "structure" else destination.route
            val reference = references.getValue(key).jsonObject.getValue("reference").jsonPrimitive.content
            val referenceBytes = Files.readAllBytes(referenceRoot.resolve(reference))
            assertEquals(referenceDigests.getValue(reference), U07Evidence.sha256(referenceBytes))
            Files.copy(referenceRoot.resolve(reference), output.resolve(reference), StandardCopyOption.REPLACE_EXISTING)
            html.append("<section id=\"${destination.route}\"><h2>${destination.label}</h2>")
            html.append("<details><summary>Original reference (1536 × 1024)</summary><a href=\"$reference\"><img alt=\"Original ${destination.label} reference\" src=\"$reference\"></a></details>")
            pages += buildJsonObject {
                put("page", destination.route); put("reference", reference); put("referenceSha256", U07Evidence.sha256(referenceBytes))
                put("hierarchyScore", JsonNull); put("referenceFidelityScore", JsonNull); put("usabilityScore", JsonNull)
                put("comments", JsonNull); put("reviewer", JsonNull); put("reviewedAt", JsonNull); put("decision", "NOT_RECORDED")
            }
            MidiCoreVisualCapture.viewports.forEach { viewport ->
                val states = buildList {
                    add("ready"); add("empty"); add("populated")
                    if (destination == MidiCoreWorkspaceDestination.ARRANGE) { add("progress"); add("failed") }
                    if (destination in setOf(MidiCoreWorkspaceDestination.REVIEW, MidiCoreWorkspaceDestination.EXPORT)) add("ready-result")
                }
                states.forEach { state ->
                    val name = "${viewport.width}-${destination.route}-$state"
                    val detail = state == "ready-result"
                    val capture = MidiCoreVisualCapture.shell(viewport, destination,
                        populated = state != "empty", ready = state.startsWith("ready"),
                        operation = when (state) {
                            "progress" -> MidiCoreVisualFixture.operation(false)
                            "failed" -> MidiCoreVisualFixture.operation(true)
                            else -> MidiCoreWorkspaceOperation.idle()
                        },
                        scrollToTag = if (!detail) null else if (destination == MidiCoreWorkspaceDestination.REVIEW)
                            MidiCoreReviewPageTags.DRAFT_IDENTITY else MidiCoreExportPageTags.SNAPSHOT_STATUS,
                    )
                    val expected = VisualImageComparator.readExpected("/visual/shell/$name.png")
                    val difference = VisualImageComparator.compare(capture.image, expected, output.resolve(name))
                    // Collect every page even on a difference so the review packet is not truncated at the first failure.
                    entries += buildJsonObject {
                        put("page", destination.route); put("state", state)
                        put("width", viewport.width); put("height", viewport.height); put("density", 1); put("fontScale", 1)
                        put("changedPixels", difference)
                        listOf("actual", "expected", "diff").forEach { kind ->
                            put(kind, "$name/$kind.png")
                            put("${kind}Sha256", U07Evidence.sha256(Files.readAllBytes(output.resolve("$name/$kind.png"))))
                        }
                    }
                    html.append("<details${if (state == "ready") " open" else ""}><summary>${viewport.width} × ${viewport.height} — $state ($difference changed pixels)</summary><div class=\"comparison\">")
                    listOf("actual" to "Current capture", "expected" to "Pinned target", "diff" to "Pixel differences").forEach { (file, label) ->
                        html.append("<figure><figcaption>$label</figcaption><a href=\"$name/$file.png\"><img loading=\"lazy\" alt=\"${destination.label}: $label, $state, ${viewport.width} pixels\" src=\"$name/$file.png\"></a></figure>")
                    }
                    html.append("</div></details>")
                }
            }
            html.append("</section>")
        }
        html.append("</html>")
        Files.writeString(output.resolve("index.html"), html)
        U07Evidence.write(output.resolve("review.json"), buildJsonObject {
            put("identity", U07Evidence.identity()); put("machine", U07Evidence.machine())
            put("humanDecision", "NOT_RECORDED"); put("acousticOnset", "UNMEASURED")
            put("renderer", "U07a pinned ImageComposeScene, software raster, Arial regular/bold, fixed frames, density/font scale 1")
            put("pages", JsonArray(pages)); put("comparisons", JsonArray(entries))
            put("requiresSameTree", JsonArray(listOf("../responsiveness.json", "../meter-smoke.json", "../native/observations.json").map(::JsonPrimitive)))
        })
        assertEquals(6, pages.size)
        assertEquals(66, entries.size)
        assertTrue(entries.all { it.getValue("changedPixels").jsonPrimitive.int == 0 }, "Inspect $output/index.html; targets were not changed")
    }
}
