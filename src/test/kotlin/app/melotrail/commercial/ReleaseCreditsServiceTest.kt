package app.melotrail.commercial

import app.melotrail.arrangement.SourceLibraryProvenance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReleaseCreditsServiceTest {
    private val hash = "a".repeat(64)
    private val service = ReleaseCreditsService()

    @Test fun `CC0 only release has one predictable statement`() {
        val preview = service.preview(manifest(usage("piano", "starter-piano", required = false)), "my song.wav")

        assertEquals("my-song-credits.txt", preview.filename)
        assertEquals("No instrument attribution required.\n", preview.text)
        assertEquals(listOf("starter-piano"), preview.usedInstrumentIds)
        assertEquals(emptyList(), preview.attributionEntryHashes)
    }

    @Test fun `required attributions are deduplicated and stable sorted while unused stems are excluded`() {
        val preview = service.preview(manifest(
            usage("piano", "z-piano", required = true, attribution = "Zulu library"),
            usage("bass", "a-bass", required = true, attribution = "Alpha library"),
            usage("pad", "unused-pad", required = true, attribution = "Must not appear", used = false, absent = true),
            usage("strings", "duplicate", required = true, attribution = "Zulu library")
        ), "my-song.mp3")

        assertEquals("Alpha library\n\nZulu library\n", preview.text)
        assertEquals(listOf("a-bass", "duplicate", "z-piano"), preview.usedInstrumentIds)
        assertEquals(2, preview.attributionEntryHashes.size)
    }

    @Test fun `uncertain absent stem is included conservatively and contradictory attribution is blocked`() {
        val uncertain = service.preview(manifest(usage("piano", "uncertain", required = true, attribution = "Required", used = false, absent = false)), "release.wav")
        assertEquals("Required\n", uncertain.text)

        val contradictory = manifest(usage("piano", "contradictory", required = true, attribution = "Required"))
            .copy(dependencies = listOf(contradictoryDependency("contradictory", "Different")))
        assertFailsWith<IllegalArgumentException> { service.preview(contradictory, "release.wav") }
    }

    private fun manifest(vararg usages: ReleaseInstrumentUsage): CommercialProvenanceManifest {
        val dependencies = usages.map { usage ->
            CommercialDependency(CommercialDependencyKind.SAMPLE, usage.stableInstrumentId, "1", hash, CommercialTerm.PERMITTED, true,
                usage.license.license, usage.license.source, usage.license.attributionText)
        }
        return CommercialProvenanceManifest(
            version = 3, releaseId = "release-" + "b".repeat(32), releaseHash = hash, sources = emptyList(), artifacts = emptyList(), decisions = emptyList(),
            stageRuns = emptyList(), selectedMidi = emptyList(), instrumentUsage = usages.toList(), dependencies = dependencies,
            unresolvedEvidence = emptyList(), commercialReady = true, reasons = emptyList(), attribution = dependencies.mapNotNull { it.attribution },
            reports = ReleaseReportReferences("output/releases/release-${"b".repeat(32)}/provenance.json", "output/releases/release-${"b".repeat(32)}/commercial-report.md", "output/releases/release-${"b".repeat(32)}/youtube-release.json")
        )
    }

    private fun usage(
        role: String,
        id: String,
        required: Boolean,
        attribution: String? = null,
        used: Boolean = true,
        absent: Boolean = !used
    ): ReleaseInstrumentUsage {
        val license = ReleaseLicenseSnapshot(id, "library", "snapshot", if (required) "CC BY 4.0" else "CC0", true, required, attribution, "redistribution")
        // The caller can create contradictory evidence by replacing the snapshot in the manifest dependency.
        return ReleaseInstrumentUsage(role, id, ProvenanceArtifact("stems/$role.wav", hash), used, absent, listOf(hash), 2, hash,
            listOf(ProvenanceArtifact("asset-sample-$hash", hash)), license, SourceLibraryProvenance("starter", "Starter", "1", "local"))
    }

    private fun contradictoryDependency(id: String, attribution: String) = CommercialDependency(
        CommercialDependencyKind.SAMPLE, id, "1", hash, CommercialTerm.PERMITTED, true, "CC BY 4.0", "library", attribution
    )
}
