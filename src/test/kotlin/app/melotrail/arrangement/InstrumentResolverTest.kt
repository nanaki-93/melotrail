package app.melotrail.arrangement

import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstrumentResolverTest {
    @TempDir lateinit var root: Path

    @Test
    fun `v2 resolves multiple bass candidates by fit then stable ID and preserves path-free evidence`() {
        writeCatalog()
        val registry = InstrumentRegistryLoader(root).load()
        val intent = intent()

        val decision = VersionedInstrumentResolver(registry).invoke(
            ResolveInstrumentRequest(intent, actor = "test", timestamp = Instant.EPOCH)
        )

        assertEquals("fashion-bass", decision.selectedId)
        assertEquals(listOf("fashion-bass", "sneaky-bass", "unlicensed-bass"), decision.candidates.map { it.id })
        assertTrue(decision.candidates.single { it.id == "fashion-bass" }.reasons.any { it.contains("profile affinity") })
        assertTrue(decision.candidates.single { it.id == "unlicensed-bass" }.rejection.orEmpty().contains("Non-commercial"))
        assertFalse(decision.toString().contains("bass.sfz"))
        assertEquals(2, decision.registryVersion)
        assertEquals(VersionedInstrumentResolver.VERSION, decision.resolverVersion)
    }

    @Test
    fun `compatible user pin wins and incompatible pin cannot bypass capability or license filters`() {
        writeCatalog()
        val registry = InstrumentRegistryLoader(root).load()
        val resolver = VersionedInstrumentResolver(registry)

        assertEquals("sneaky-bass", resolver.invoke(ResolveInstrumentRequest(intent(pinned = "sneaky-bass"), "test")).selectedId)
        assertEquals("fashion-bass", resolver.invoke(ResolveInstrumentRequest(intent(pinned = "unlicensed-bass"), "test")).selectedId)
    }

    @Test
    fun `unknown sonic vocabulary makes only that entry unavailable while future affinity IDs stay neutral`() {
        writeCatalog(extra = ",\"articulationTraits\":[\"future-articulation\"]")
        val registry = InstrumentRegistryLoader(root).load()

        val unavailable = registry.resolve("fashion-bass")
        assertEquals(LicenseAdmission.UNAVAILABLE, unavailable.licenseAdmission.admission)
        assertTrue(unavailable.licenseAdmission.reasons.single().contains("unknown articulation trait"))
        assertEquals("sneaky-bass", VersionedInstrumentResolver(registry).invoke(ResolveInstrumentRequest(intent(), "test")).selectedId)
    }

    private fun intent(pinned: String? = null) = InstrumentIntent(
        role = ArrangementRole.BASS,
        profile = CompositionProfileRef("lofi", 1), mood = MoodRef("nostalgic", 1),
        attackTraits = setOf(SoundTrait.SOFT), toneTraits = setOf(SoundTrait.WARM),
        requiredCapabilities = setOf(PerformanceCapability.PITCHED), pinnedInstrumentId = pinned
    )

    private fun writeCatalog(extra: String = "") {
        copyLibrary()
        val standard = """"engine":{"type":"sfz","path":"bass/bass.sfz"},"library":{"id":"fixture-pack","name":"Fixture pack","version":"1","source":"fixture source"},"capabilities":{"performance":["pitched"]}"""
        val cc0 = """"license":{"id":"CC0-1.0","commercialUse":true,"attributionRequired":false,"sourceName":"Fixture source","licenseText":"CC0 evidence"}"""
        val ccBy = """"license":{"id":"CC-BY-4.0","commercialUse":true,"attributionRequired":true,"attributionText":"Fixture credit","sourceName":"Fixture source","licenseText":"CC BY evidence"}"""
        val nc = """"license":{"id":"CC-BY-NC-4.0","commercialUse":true,"attributionRequired":true,"attributionText":"Credit","sourceName":"Fixture source","licenseText":"NC evidence"}"""
        fun entry(id: String, name: String, profile: Double, license: String, suffix: String = "") = """{"id":"$id","name":"$name","roles":["bass"],"profileAffinities":{"lofi":$profile,"future-profile":0.8},"moodAffinities":{"nostalgic":0.4},"attackTraits":["soft"],"toneTraits":["warm"],$standard,$license$suffix}"""
        Files.writeString(root.resolve("instruments.json"), """{"version":2,"workingSampleRate":44100,"instruments":[${entry("fashion-bass", "Fashion Bass", 0.8, cc0, extra)},${entry("sneaky-bass", "Sneaky Bass", 0.2, ccBy)},${entry("unlicensed-bass", "Unlicensed Bass", 1.0, nc)}]}""")
    }

    private fun copyLibrary() {
        writeSample(root.resolve("bass/samples/bass.wav"))
        Files.createDirectories(root.resolve("bass"))
        Files.writeString(root.resolve("bass/bass.sfz"), "<region> sample=samples/bass.wav key=48")
    }

    private fun writeSample(path: Path) {
        Files.createDirectories(requireNotNull(path.parent))
        val data = byteArrayOf(0, 0)
        val bytes = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVEfmt ".toByteArray()).putInt(16)
        bytes.putShort(1).putShort(1).putInt(44_100).putInt(88_200).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(data.size).put(data)
        Files.write(path, bytes.array())
    }
}
