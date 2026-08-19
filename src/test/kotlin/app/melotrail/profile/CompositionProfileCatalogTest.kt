package app.melotrail.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompositionProfileCatalogTest {
    private val lofi = CompositionProfileRef("lofi", 1)

    @Test
    fun `bundled catalog has exactly one versioned lofi profile and initial moods`() {
        val catalog = BundledCompositionProfileCatalog.load()

        assertEquals(listOf(lofi), catalog.profiles().map { it.ref })
        assertEquals(setOf("warm", "nostalgic", "melancholic", "dreamy", "relaxed", "dark"), catalog.moods(lofi).map { it.ref.id }.toSet())
        assertEquals(MoodRef("warm", 1), catalog.profiles().single().defaultMood)
    }

    @Test
    fun `resolution applies moods clamps bounds and produces a deterministic hash`() {
        val catalog = BundledCompositionProfileCatalog.load()

        val warm = catalog.resolve(lofi, MoodRef("warm", 1))
        val melancholy = catalog.resolve(lofi, MoodRef("melancholic", 1))

        assertEquals(78, warm.tempoBpm)
        assertEquals(74, melancholy.tempoBpm)
        assertTrue(melancholy.instrumentAffinity.first { it.role == MusicalRole.ATMOSPHERE }.desiredCharacteristics.any {
            it.characteristic == InstrumentCharacteristic.SUSTAINED && it.weight == 0.7
        })
        assertEquals(warm, catalog.resolve(lofi, MoodRef("warm", 1)))
        assertEquals(64, ValidatedCompositionProfileCatalog(resourceWithMoodDelta(swing = 15)).resolve(lofi, MoodRef("warm", 1)).swingPercent)
    }

    @Test
    fun `unknown versions neutral modifiers and invalid catalog data are rejected at the boundary`() {
        val catalog = BundledCompositionProfileCatalog.load()
        assertThrows(IllegalArgumentException::class.java) { catalog.resolve(CompositionProfileRef("lofi", 2)) }
        assertThrows(IllegalArgumentException::class.java) { catalog.resolve(lofi, MoodRef("warm", 2)) }

        val neutral = ValidatedCompositionProfileCatalog(resourceWithMoodDelta()).resolve(lofi, MoodRef("warm", 1))
        assertEquals(80, neutral.tempoBpm)
        assertEquals(58, neutral.swingPercent)
        assertEquals(12, neutral.humanizationMs)
        assertEquals(12, neutral.timingToleranceMs)

        val resource = bundledResource()
        val profile = resource.profiles.single()
        val duplicate = resource.copy(profiles = listOf(profile, profile))
        assertThrows(IllegalArgumentException::class.java) { ValidatedCompositionProfileCatalog(duplicate) }
        val invalidRange = resource.copy(profiles = listOf(profile.copy(groove = profile.groove.copy(tempoBpm = IntParameterBounds(92, 68, 80)))))
        assertThrows(IllegalArgumentException::class.java) { ValidatedCompositionProfileCatalog(invalidRange) }
        val unknownProfile = resource.copy(moods = resource.moods.map { mood ->
            if (mood.ref == MoodRef("warm", 1)) mood.copy(profile = CompositionProfileRef("unknown", 1)) else mood
        })
        assertThrows(IllegalArgumentException::class.java) { ValidatedCompositionProfileCatalog(unknownProfile) }
        val unavailable = resource.copy(profiles = listOf(profile.copy(styleProcessing = StyleProcessingPolicy(ProfileCapability.STYLE_PROCESSING, listOf("warmth")))))
        assertThrows(IllegalArgumentException::class.java) { ValidatedCompositionProfileCatalog(unavailable, emptySet()) }
    }

    @Test
    fun `instrument criteria remain controlled and license preference is a late ranking tie break with user override`() {
        val resolved = BundledCompositionProfileCatalog.load().resolve(lofi, MoodRef("dark", 1))
        assertTrue(resolved.instrumentAffinity.isNotEmpty())
        assertTrue(resolved.instrumentAffinity.all { it.desiredCharacteristics.all { characteristic -> characteristic.weight in 0.0..1.0 } })
        assertFalse(bundledText().contains(".sfz"))
        assertFalse(bundledText().contains(".wav"))
        assertFalse(bundledText().contains("samples/"))

        val ranked = rankEligibleCandidates(
            LicensePreference.PREFER_NO_ATTRIBUTION,
            listOf(
                EligibleInstrumentCandidate("no-credit", true, true, false),
                EligibleInstrumentCandidate("cc-by-choice", true, true, true, explicitlySelectedByUser = true),
                EligibleInstrumentCandidate("not-admitted", false, true, false)
            )
        )
        assertEquals(listOf("cc-by-choice", "no-credit"), ranked.map { it.id })
    }

    private fun resourceWithMoodDelta(swing: Int? = null): CompositionProfileCatalogResource {
        val resource = bundledResource()
        return resource.copy(moods = resource.moods.map { mood ->
            if (mood.ref == MoodRef("warm", 1)) mood.copy(modifier = MoodModifier(swingPercentDelta = swing)) else mood
        })
    }

    private fun bundledResource(): CompositionProfileCatalogResource = json.decodeFromString(bundledText())

    private fun bundledText(): String = checkNotNull(javaClass.classLoader.getResourceAsStream("profiles/lofi-v1.json"))
        .bufferedReader().use { it.readText() }

    private companion object {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }
    }
}
