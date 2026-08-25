package app.melotrail.harmony

import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.ScaleModeId

/** A fixed, versioned catalog. Resolved events are the only data renderers need. */
data class HarmonyTemplate(
    val id: HarmonyTemplateId,
    val label: String,
    val romanNumerals: String,
    val mode: ScaleModeId,
    val chords: List<HarmonyTemplateChord>
)

data class HarmonyTemplateChord(
    val degree: Int,
    val quality: ChordQuality? = null,
    val bassDegree: Int? = null
)

data class HarmonyTemplateOption(
    val id: HarmonyTemplateId,
    val label: String,
    val romanNumerals: String,
    val chordSymbols: List<String>
)

object HarmonyTemplateCatalog {
    private val major = listOf(
        exactTemplate("lofi-major-classic-v1", "Classic loop", "I–V/7–vi7–IVmaj7",
            chord(1, ChordQuality.MAJOR), chord(5, ChordQuality.MAJOR, bassDegree = 7),
            chord(6, ChordQuality.MINOR_7), chord(4, ChordQuality.MAJOR_7)),
        template("lofi-major-turnaround-v1", "Turnaround", "I–vi–ii–V", 1, 6, 2, 5),
        template("lofi-major-jazzy-v1", "Jazzy resolve", "ii–V–I", 2, 5, 1),
        template("lofi-major-cycle-v1", "Soft cycle", "vi–ii–V–I", 6, 2, 5, 1),
        template("lofi-major-lift-v1", "Gentle lift", "I–IV–vi–V", 1, 4, 6, 5),
        template("lofi-major-rising-v1", "Rising warmth", "I–iii–IV–V", 1, 3, 4, 5),
        exactTemplate("lofi-major-warm-intro-v1", "Warm intro", "Imaj7–vi7–IVmaj7–V",
            chord(1, ChordQuality.MAJOR_7), chord(6, ChordQuality.MINOR_7),
            chord(4, ChordQuality.MAJOR_7), chord(5, ChordQuality.MAJOR)),
        exactTemplate("lofi-major-open-chorus-v1", "Open chorus", "IV–V–I–vi7–IV–V–I–I",
            chord(4, ChordQuality.MAJOR), chord(5, ChordQuality.MAJOR), chord(1, ChordQuality.MAJOR),
            chord(6, ChordQuality.MINOR_7), chord(4, ChordQuality.MAJOR), chord(5, ChordQuality.MAJOR),
            chord(1, ChordQuality.MAJOR), chord(1, ChordQuality.MAJOR)),
        exactTemplate("lofi-major-reflective-bridge-v1", "Reflective bridge", "vi7–iii–IVmaj7–V",
            chord(6, ChordQuality.MINOR_7), chord(3, ChordQuality.MINOR),
            chord(4, ChordQuality.MAJOR_7), chord(5, ChordQuality.MAJOR)),
        exactTemplate("lofi-major-soft-outro-v1", "Soft outro", "IVmaj7–V–Imaj7–I6",
            chord(4, ChordQuality.MAJOR_7), chord(5, ChordQuality.MAJOR),
            chord(1, ChordQuality.MAJOR_7), chord(1, ChordQuality.MAJOR_6))
    )
    private val minor = listOf(
        template("lofi-minor-drift-v1", "Minor drift", "i–VII–VI–VII", 1, 7, 6, 7),
        template("lofi-minor-cinematic-v1", "Cinematic", "i–VI–III–VII", 1, 6, 3, 7),
        template("lofi-minor-moody-v1", "Moody", "i–iv–VII–III", 1, 4, 7, 3),
        template("lofi-minor-descending-v1", "Descending", "i–v–VI–VII", 1, 5, 6, 7),
        template("lofi-minor-resolve-v1", "Soft resolve", "VI–VII–i–i", 6, 7, 1, 1),
        template("lofi-minor-warm-v1", "Warm return", "III–VII–i–VI", 3, 7, 1, 6)
    )
    private val all = major + minor

    fun options(key: MusicalKey): List<HarmonyTemplateOption> = templatesFor(key).map { template ->
        HarmonyTemplateOption(template.id, template.label, template.romanNumerals,
            resolve(template, key).map(ChordSymbolFormatter::format))
    }

    fun resolve(id: HarmonyTemplateId, key: MusicalKey, section: SectionTypeId, previous: List<ChordEvent> = emptyList()): ChordProgression {
        val template = requireNotNull(all.firstOrNull { it.id == id && it.mode == key.modeId }) {
            "Harmony template '$id' is not available for ${key.displayName}."
        }
        val generated = resolve(template, key).mapIndexed { index, chord ->
            chord.copy(id = previous.getOrNull(index)?.id ?: ChordEventId("h-${section.value}-${index + 1}"), order = index)
        }
        return ChordProgression(section, generated, template.id)
    }

    fun isAvailable(id: HarmonyTemplateId, key: MusicalKey): Boolean =
        all.any { it.id == id && it.mode == key.modeId }

    private fun templatesFor(key: MusicalKey): List<HarmonyTemplate> = all.filter { it.mode == key.modeId }

    private fun resolve(template: HarmonyTemplate, key: MusicalKey): List<ChordEvent> = template.chords.mapIndexed { index, chord ->
        val root = key.scalePitchClasses()[chord.degree - 1]
        val bass = chord.bassDegree?.let { key.scalePitchClasses()[it - 1] }
        ChordEvent(ChordEventId("template-$index"), root, chord.quality ?: qualityFor(key.modeId, chord.degree), index, bass = bass)
    }

    private fun qualityFor(mode: ScaleModeId, degree: Int): ChordQuality = when (mode) {
        ScaleModeId.MAJOR -> when (degree) {
            1, 4 -> ChordQuality.MAJOR_7
            2, 3, 6 -> ChordQuality.MINOR_7
            5 -> ChordQuality.DOMINANT_7
            else -> error("Unsupported major scale degree $degree")
        }
        ScaleModeId.NATURAL_MINOR -> when (degree) {
            1, 4, 5 -> ChordQuality.MINOR_7
            3, 6 -> ChordQuality.MAJOR_7
            7 -> ChordQuality.DOMINANT_7
            else -> error("Unsupported natural-minor scale degree $degree")
        }
        else -> error("Unsupported scale mode ${mode.value}")
    }

    private fun template(id: String, label: String, numerals: String, vararg degrees: Int) = HarmonyTemplate(
        HarmonyTemplateId(id), label, numerals,
        if (id.contains("-major-")) ScaleModeId.MAJOR else ScaleModeId.NATURAL_MINOR,
        degrees.map(::HarmonyTemplateChord)
    )

    /** Declare an exact major-mode template without applying automatic diatonic chord qualities. */
    private fun exactTemplate(id: String, label: String, numerals: String, vararg chords: HarmonyTemplateChord) = HarmonyTemplate(
        HarmonyTemplateId(id), label, numerals, ScaleModeId.MAJOR, chords.toList()
    )

    /** Define one exact scale-relative chord, optionally with a scale-relative slash bass. */
    private fun chord(degree: Int, quality: ChordQuality, bassDegree: Int? = null) =
        HarmonyTemplateChord(degree, quality, bassDegree)
}
