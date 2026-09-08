package app.melotrail.arrangement.core

import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiMelodySelection
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiProtectedMelodySelector
import app.melotrail.midi.domain.MidiProtectedMelodyView
import app.melotrail.midi.domain.MidiSemanticEventKind
import app.melotrail.midi.domain.MidiSourceEventIdentity
import app.melotrail.midi.domain.MidiSourceIdentity
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.midi.domain.SemanticMidiSequence
import app.melotrail.midi.domain.SemanticMidiTrack
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptedDependency
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.ProjectRelativePath
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.SelectedMelodyTrack
import app.melotrail.project.SourceMidiRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MidiCoreChordGeneratorTest {
    @Test
    fun `unsupported comping meters never fall back to four-four`() {
        listOf(ProjectMeter(5, 2), ProjectMeter(7, 3), ProjectMeter(2, 2)).forEach { meter ->
            MidiCorePatternCatalog.chordRhythms.forEach { pattern ->
                val error = assertFailsWith<IllegalArgumentException> {
                    MidiCoreChordGenerator.generate(context(
                        project = project(meter = meter), patternId = pattern.id.id,
                    ))
                }
                assertTrue(error.message.orEmpty().contains("Piano comping does not support ${meter.numerator}/${meter.denominator}"))
            }
        }
    }

    @Test
    fun `generates every authoritative chord extension inside the bounded register`() {
        val result = MidiCoreChordGenerator.generate(
            context(chordSymbol = "Cmaj9", density = 1.0),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, "Expected a valid chord candidate, got ${result.validation.report.findings}")
        assertEquals(setOf(0, 2, 4, 7, 11), notes.map { it.pitch % 12 }.toSet())
        assertEquals(5, notes.size)
        assertTrue(notes.all { it.pitch in 48..84 && it.startTick == 0L && it.endTick == 1_920L })
    }

    @Test
    fun `expands complete curated chord rhythms without changing harmony`() {
        val patterns = listOf(
            MidiCoreChordRhythmPatternId.SUSTAINED,
            MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS,
            MidiCoreChordRhythmPatternId.LATE_ENTRY,
            MidiCoreChordRhythmPatternId.DUSTY_OFFBEATS,
            MidiCoreChordRhythmPatternId.BROKEN_SYNCOPATION,
            MidiCoreChordRhythmPatternId.BRIDGE_HALF_TIME,
        )
        val generated = patterns.associateWith { pattern ->
            MidiCoreChordGenerator.generate(
                context(patternId = pattern.id, profileId = "chords.pulsed", density = 1.0),
            )
        }

        assertTrue(generated.values.all { it.accepted }, generated.values.flatMap { it.validation.report.findings }.toString())
        assertEquals(listOf(0L), starts(generated.getValue(MidiCoreChordRhythmPatternId.SUSTAINED)))
        assertEquals(listOf(0L, 480L, 960L, 1_440L), starts(generated.getValue(MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS)))
        assertEquals(listOf(480L, 960L, 1_440L), starts(generated.getValue(MidiCoreChordRhythmPatternId.LATE_ENTRY)))
        assertEquals(listOf(240L, 720L, 1_200L, 1_680L), starts(generated.getValue(MidiCoreChordRhythmPatternId.DUSTY_OFFBEATS)))
        assertEquals(listOf(0L, 720L, 1_200L, 1_680L), starts(generated.getValue(MidiCoreChordRhythmPatternId.BROKEN_SYNCOPATION)))
        assertEquals(listOf(0L, 960L), starts(generated.getValue(MidiCoreChordRhythmPatternId.BRIDGE_HALF_TIME)))
        generated.values.forEach { result ->
            assertEquals(setOf(0, 4, 7), result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map { it.pitch % 12 }.toSet())
        }
        val golden = goldenPatterns()
        generated.forEach { (pattern, result) ->
            assertEquals(golden.getValue(pattern.id), result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map(::goldenNote))
        }
    }

    @Test
    fun `sustained support clips offbeat harmony while retaining the next metrical bar`() {
        val result = MidiCoreChordGenerator.generate(context(
            project = project(chordEvents = listOf(
                AuthoritativeChordEvent("c", "verse-1", "C", 0, 720),
                AuthoritativeChordEvent("f", "verse-1", "F", 720, 3_840),
            )),
            patternId = MidiCoreChordRhythmPatternId.SUSTAINED.id,
            density = 1.0,
        ))
        assertTrue(result.accepted, result.validation.report.findings.toString())
        val spans = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .map { it.startTick to it.endTick }.distinct()
        assertEquals(listOf(0L to 720L, 720L to 1_920L, 1_920L to 3_840L), spans)
        assertEquals(result, MidiCoreChordGenerator.generate(result.context))
    }

    @Test
    fun `keeps four-four rhythm phase across an offbeat harmonic change and clips the outgoing chord`() {
        val result = MidiCoreChordGenerator.generate(
            context(
                project = project(
                    chordEvents = listOf(
                        AuthoritativeChordEvent("c", "verse-1", "C", 0, 720),
                        AuthoritativeChordEvent("f", "verse-1", "F", 720, 1_920),
                    ),
                ),
                patternId = MidiCoreChordRhythmPatternId.BRIDGE_HALF_TIME.id,
                profileId = "chords.sustained",
                density = 1.0,
            ),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertEquals(listOf(0L, 960L), starts(result), "The F chord must wait for the next metrical attack")
        assertFalse(notes.any { it.startTick == 720L }, "An offbeat chord change must not restart the pattern")
        assertTrue(notes.filter { it.startTick == 0L }.all { it.endTick == 720L }, "The outgoing chord must clip exactly at its harmonic boundary")
        assertTrue(notes.filter { it.startTick == 960L }.all { it.endTick <= 1_920L })
    }

    @Test
    fun `uses authored three-four attacks without carrying a fourth four-four beat into the next bar`() {
        val result = MidiCoreChordGenerator.generate(
            context(
                project = project(
                    chordEvents = listOf(AuthoritativeChordEvent("c", "verse-1", "C", 0, 2_880)),
                    meter = ProjectMeter(3, 2),
                ),
                patternId = MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS.id,
                profileId = "chords.pulsed",
                density = 1.0,
            ),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertEquals(listOf(0L, 480L, 960L, 1_440L, 1_920L, 2_400L), starts(result))
        assertEquals(6, notes.groupBy(MidiCoreCandidateEvent.Note::startTick).size)
        assertTrue(notes.all { it.endTick <= 2_880L })
    }

    @Test
    fun `uses compound six-eight grouping at a nonstandard PPQ with bounded short sections`() {
        val result = MidiCoreChordGenerator.generate(
            context(
                project = project(
                    chordEvents = listOf(AuthoritativeChordEvent("c", "verse-1", "C", 0, 1_500)),
                    ppq = 500,
                    meter = ProjectMeter(6, 3),
                ),
                patternId = MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS.id,
                profileId = "chords.pulsed",
                density = 1.0,
            ),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
        val velocityAt = notes.groupBy(MidiCoreCandidateEvent.Note::startTick)
            .mapValues { (_, grouped) -> grouped.first().velocity }

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertEquals(listOf(0L, 750L), starts(result))
        assertTrue(velocityAt.getValue(0L) > velocityAt.getValue(750L), "6/8 must retain its dotted-quarter group accent")
        assertTrue(notes.all { it.endTick <= 1_500L })
    }

    @Test
    fun `phrase breath selects a complete silent bar across its own harmony window`() {
        val chords = listOf(
            AuthoritativeChordEvent("c", "verse-1", "C", 0, 1_920),
            AuthoritativeChordEvent("f", "verse-1", "F", 1_920, 3_840),
            AuthoritativeChordEvent("g", "verse-1", "G", 3_840, 5_760),
        )
        val melody = listOf(protectedNote(84, 0, 1_920), protectedNote(86, 3_840, 5_760))
        val input = context(project = project(chordEvents = chords), protectedMelodyNotes = melody)
        val result = MidiCoreChordGenerator.generate(input)
        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertEquals(listOf(0L, 3_840L), starts(result))
        assertTrue(result.candidate.events.all { it.endTick <= 1_920L || it.startTick >= 3_840L })
        assertEquals(result, MidiCoreChordGenerator.generate(input))
        assertEquals(melody, result.context.protectedMelodyNotes)
        assertTrue(requireNotNull(result.outgoingPianoVoicingBoundary).pitches.isNotEmpty())
        val withoutPhrases = MidiCoreChordGenerator.generate(context(project = project(chordEvents = chords)))
        assertTrue(withoutPhrases.accepted)
        assertTrue(1_920L in starts(withoutPhrases), "Silence must come from phrase selection, not an empty rhythm pattern")
    }

    @Test
    fun `triple and compound meters leave a full inter-phrase bar without shifting the next attack`() {
        listOf(ProjectMeter(3, 2), ProjectMeter(6, 3)).forEach { meter ->
            val result = MidiCoreChordGenerator.generate(context(
                project = project(meter = meter, chordEvents = listOf(
                    AuthoritativeChordEvent("c", "verse-1", "C", 0, 4_320),
                )),
                protectedMelodyNotes = listOf(protectedNote(84, 0, 480), protectedNote(86, 2_880, 4_320)),
            ))
            assertTrue(result.accepted, result.validation.report.findings.toString())
            assertEquals(listOf(0L, 2_880L), starts(result))
            assertTrue(result.candidate.events.all { it.endTick <= 1_440L || it.startTick >= 2_880L })
        }
    }

    @Test
    fun `dense protected melody selects one held support shape instead of pulsing through it`() {
        val melody = listOf(
            protectedNote(72, 0, 480), protectedNote(74, 480, 960),
            protectedNote(76, 960, 1_440), protectedNote(77, 1_440, 1_920),
        )
        val before = melody.toList()
        val result = MidiCoreChordGenerator.generate(context(
            patternId = MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS.id,
            profileId = "chords.pulsed",
            protectedMelodyNotes = melody,
        ))
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertEquals(listOf(0L), starts(result))
        assertTrue(notes.all { it.endTick == 1_920L }, "Dense melody needs held support, not a shortened pulse: $notes")
        assertEquals(before, melody, "Comping must never mutate protected source timing")
    }

    @Test
    fun `dense support has explicit three-four and compound six-eight bar realizations`() {
        listOf(
            ProjectMeter(3, 2) to (480 to 1_440L),
            ProjectMeter(6, 3) to (500 to 1_500L),
        ).forEach { (meter, timing) ->
            val (ppq, endTick) = timing
            val result = MidiCoreChordGenerator.generate(context(
                project = project(
                    chordEvents = listOf(AuthoritativeChordEvent("c", "verse-1", "C", 0, endTick)),
                    ppq = ppq,
                    meter = meter,
                ),
                patternId = MidiCoreChordRhythmPatternId.DUSTY_OFFBEATS.id,
                profileId = "chords.pulsed",
                protectedMelodyNotes = listOf(protectedNote(72, 0, endTick)),
            ))
            val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

            assertTrue(result.accepted, "$meter: ${result.validation.report.findings}")
            assertEquals(listOf(0L), starts(result), "$meter")
            assertTrue(notes.all { it.endTick == endTick }, "$meter: $notes")
        }
    }

    @Test
    fun `dense bar keeps support use across an offbeat chord change`() {
        val result = MidiCoreChordGenerator.generate(context(
            project = project(chordEvents = listOf(
                AuthoritativeChordEvent("c", "verse-1", "C", 0, 720),
                AuthoritativeChordEvent("f", "verse-1", "F", 720, 1_920),
            )),
            patternId = MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS.id,
            profileId = "chords.pulsed",
            protectedMelodyNotes = listOf(protectedNote(72, 0, 1_440)),
        ))
        val spans = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .map { it.startTick to it.endTick }
            .distinct()

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertEquals(listOf(0L to 720L, 720L to 1_920L), spans)
        assertEquals(result, MidiCoreChordGenerator.generate(result.context))
    }

    @Test
    fun `sustain-aware activity keeps answer attacks out of a pedaled melody span`() {
        val melody = protectedNote(72, 0, 240)
        val base = context(
            patternId = MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS.id,
            profileId = "chords.pulsed",
            protectedMelodyNotes = listOf(melody),
        )
        val keyHeldEvidence = melodyEvidence(
            base,
            melody,
            MidiCoreHarmonyTensionCause.NON_CHORD_TONE,
            accented = false,
        )
        val sounding = keyHeldEvidence.notes.single().copy(soundingEndTick = 1_920)
        val evidence = keyHeldEvidence.copy(
            notes = listOf(sounding),
            windows = keyHeldEvidence.windows.map { window ->
                window.copy(activity = window.activity.copy(soundingNotes = listOf(sounding)))
            },
            findings = keyHeldEvidence.findings.map { finding ->
                finding.copy(endTick = 1_920, overlapTicks = 1_920)
            },
        )
        val result = MidiCoreChordGenerator.generate(base.copy(melodyHarmonyAnalysis = evidence))
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertEquals(listOf(0L), starts(result))
        assertTrue(notes.all { it.endTick == 1_920L }, "Pedaled melody should select held support: $notes")
        assertEquals(240L, melody.endTick, "Comping analysis must not rewrite the protected key release")
    }

    @Test
    fun `answer attacks wait for melody space and a phrase ending keeps its final beat clear`() {
        val melody = listOf(
            protectedNote(72, 0, 960),
            protectedNote(74, 1_320, 1_440),
        )
        val result = MidiCoreChordGenerator.generate(context(
            project = project(chordEvents = listOf(AuthoritativeChordEvent("c", "verse-1", "C", 0, 3_840))),
            patternId = MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS.id,
            profileId = "chords.pulsed",
            protectedMelodyNotes = melody,
        ))
        val starts = starts(result)

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertFalse(960L in starts, "An otherwise empty attack in the phrase's final beat must remain a rest: $starts")
        assertTrue(1_440L in starts, "The following melody rest should receive the selected answer pattern: $starts")
        assertTrue(result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().none { note ->
            melody.any { source -> source.overlaps(note.startTick, note.endTick) && note.startTick in 0L..959L }
        })
    }

    @Test
    fun `six-eight phrase ending rests the final compound pulse`() {
        val result = MidiCoreChordGenerator.generate(context(
            project = project(
                chordEvents = listOf(AuthoritativeChordEvent("c", "verse-1", "C", 0, 1_500)),
                ppq = 500,
                meter = ProjectMeter(6, 3),
            ),
            patternId = MidiCoreChordRhythmPatternId.BROKEN_SYNCOPATION.id,
            profileId = "chords.pulsed",
            protectedMelodyNotes = listOf(
                protectedNote(72, 250, 500),
                protectedNote(74, 1_375, 1_500),
            ),
        ))

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertEquals(listOf(625L), starts(result))
        assertFalse(1_125L in starts(result), "The final dotted-quarter pulse must remain clear")
        assertTrue(result.candidate.events.all { it.endTick <= 750L }, "The terminal compound pulse must be an actual rest")
    }

    @Test
    fun `honors slash-bass inversion and carries nearby voice leading across sub-bar changes`() {
        val result = MidiCoreChordGenerator.generate(
            context(
                project = project(
                    chordEvents = listOf(
                        AuthoritativeChordEvent("first", "verse-1", "G/B", 0, 960),
                        AuthoritativeChordEvent("second", "verse-1", "Cmaj7", 960, 1_920),
                    ),
                ),
                density = 1.0,
            ),
        )
        val voicings = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .groupBy(MidiCoreCandidateEvent.Note::startTick)
            .toSortedMap()
            .values
            .map { notes -> notes.sortedBy(MidiCoreCandidateEvent.Note::pitch).map(MidiCoreCandidateEvent.Note::pitch) }

        assertTrue(result.accepted, "Expected a valid sub-bar candidate, got ${result.validation.report.findings}")
        assertEquals(11, voicings[0].first() % 12)
        assertEquals(setOf(2, 7, 11), voicings[0].map { it % 12 }.toSet())
        assertEquals(setOf(0, 4, 7, 11), voicings[1].map { it % 12 }.toSet(), "voicings=$voicings")
        assertTrue(voicings[0].zip(voicings[1]).all { (before, after) -> abs(after - before) <= 12 })
        assertTrue(voicings.flatten().all { it in 48..84 })
        assertTrue(result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().all { note ->
            if (note.startTick < 960) note.endTick <= 960 else note.endTick <= 1_920
        })
    }

    @Test
    fun `uses protected melody and accepted bass space when selecting a voicing`() {
        val result = MidiCoreChordGenerator.generate(
            context(
                protectedMelodyNotes = listOf(protectedNote(60, true)),
                acceptedDependencies = listOf(
                    MidiCoreAcceptedDependencyContext(
                        MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "d".repeat(64)),
                        listOf(MidiCoreGenerationNote(0, 1_920, 55, 80)),
                    ),
                ),
                density = 1.0,
            ),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, "Expected space-aware candidate, got ${result.validation.report.findings}")
        assertFalse(notes.any { it.pitch == 60 })
        assertTrue(notes.all { note -> abs(note.pitch - 55) > 5 })
    }

    @Test
    fun `ranks sustained accented close melody clashes below passing tension without blocking either`() {
        val melody = protectedNote(65, anchor = false)
        val base = context(protectedMelodyNotes = listOf(melody))
        val sustainedContext = base.copy(
            melodyHarmonyAnalysis = melodyEvidence(
                base,
                melody,
                MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION,
                accented = true,
            ),
        )
        val sustained = MidiCoreChordGenerator.generate(sustainedContext)
        val passingMelody = melody.copy(endTick = 120)
        val passingBase = context(protectedMelodyNotes = listOf(passingMelody))
        val passing = MidiCoreChordGenerator.generate(passingBase.copy(
            melodyHarmonyAnalysis = melodyEvidence(
                passingBase,
                passingMelody,
                MidiCoreHarmonyTensionCause.PASSING_OR_NEIGHBOR_TONE,
                accented = false,
            ),
        ))

        assertTrue(sustained.accepted, sustained.validation.report.findings.toString())
        assertTrue(passing.accepted, passing.validation.report.findings.toString())
        assertFalse(base.contextSha256 == sustainedContext.contextSha256)
        assertTrue(closestDistance(passing, melody.pitch) < closestDistance(sustained, melody.pitch))
        assertEquals(sustained, MidiCoreChordGenerator.generate(sustained.context))
        assertEquals(passing, MidiCoreChordGenerator.generate(passing.context))
    }

    @Test
    fun `weights a real M02 suspension held across a chord boundary by its downbeat prominence`() {
        val protectedMelody = protectedMelodyView(startTick = 1_440, endTick = 2_400, pitch = 65)
        val chordEvents = listOf(
            AuthoritativeChordEvent("before", "verse-1", "Bb", 0, 1_920),
            AuthoritativeChordEvent("after", "verse-1", "C", 1_920, 3_840),
        )
        val project = project(
            chordEvents = chordEvents,
            sourceSha256 = protectedMelody.sourceSha256,
            melodyIdentitySha256 = protectedMelody.identitySha256,
        )
        val context = MidiCoreGenerationContext.from(
            project = project,
            role = CandidateRole.CHORDS,
            occurrenceId = "verse-1",
            performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.CHORDS, "chords.sustained"),
            patternId = MidiCoreChordRhythmPatternId.SUSTAINED.id,
            generator = MidiCoreGeneratorInput("test-generator", "test-v1", MidiCoreChordRhythmPatternId.SUSTAINED.id, 17),
            protectedMelody = protectedMelody,
        )

        val evidence = requireNotNull(context.melodyHarmonyAnalysis)
        val afterWindow = evidence.windows.single { it.chordEventId == "after" }
        val suspension = afterWindow.findings.single()
        assertTrue(afterWindow.accentedNoteIds.isEmpty(), "Held notes are intentionally absent from the onset-only accent projection")
        assertEquals(MidiCoreHarmonyTensionCause.HELD_SUSPENSION, suspension.cause)
        assertEquals(1.0, suspension.beatProminence)
        assertEquals(3L, MidiCoreChordGenerator.findingProminenceMultiplier(afterWindow.findings))

        val result = MidiCoreChordGenerator.generate(context)
        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertEquals(result, MidiCoreChordGenerator.generate(context))
    }

    @Test
    fun `moves piano above low protected melody register pressure`() {
        val melody = protectedNote(54, anchor = false)
        val base = context(protectedMelodyNotes = listOf(melody))
        val result = MidiCoreChordGenerator.generate(base.copy(
            melodyHarmonyAnalysis = melodyEvidence(base, melody, MidiCoreHarmonyTensionCause.NON_CHORD_TONE, accented = false),
        ))
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertTrue(notes.all { it.pitch > melody.pitch + 12 }, "Expected piano above low melody register: $notes")
        assertEquals(result, MidiCoreChordGenerator.generate(result.context))
    }

    @Test
    fun `uses a safe reduced voicing when every complete extension doubles protected melody anchors`() {
        val melodyAnchors = listOf(48, 60, 72, 84).mapIndexed { index, pitch ->
            MidiCoreProtectedMelodyNote(
                id = "pmn-" + index.toString(16).repeat(64),
                startTick = 0,
                endTick = 1_920,
                pitch = pitch,
                velocity = 90,
                anchor = true,
            )
        }
        val generationContext = context(
            chordSymbol = "Cmaj9",
            profileId = "chords.pulsed",
            patternId = MidiCoreChordRhythmPatternId.BRIDGE_HALF_TIME.id,
            density = 0.5,
            protectedMelodyNotes = melodyAnchors,
            acceptedDependencies = listOf(
                MidiCoreAcceptedDependencyContext(
                    MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-root", "e".repeat(64)),
                    listOf(MidiCoreGenerationNote(0, 1_920, 36, 80)),
                ),
            ),
        )
        val result = MidiCoreChordGenerator.generate(generationContext)
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, "Expected an anchor-safe partial voicing, got ${result.validation.report.findings}")
        assertTrue(notes.map { it.pitch % 12 }.toSet().let { it.size >= 2 && it.all { pitchClass -> pitchClass in setOf(2, 4, 7, 11) } })
        assertEquals(1, notes.map { it.startTick }.distinct().size, "Dense melody should receive one held support attack")
        assertFalse(notes.any { it.pitch in melodyAnchors.map(MidiCoreProtectedMelodyNote::pitch) })
        assertFalse(result.validation.report.findings.any { it.code == MidiCoreRoleFindingCode.DENSITY_EXCEEDED })
        assertEquals(result, MidiCoreChordGenerator.generate(generationContext), "Rootless selection must remain deterministic")
    }

    @Test
    fun `rejects rootless selection when bass does not supply the root`() {
        val melodyAnchors = listOf(48, 60, 72, 84).mapIndexed { index, pitch ->
            MidiCoreProtectedMelodyNote("pmn-" + index.toString(16).repeat(64), 0, 1_920, pitch, 90, anchor = true)
        }
        val generationContext = context(chordSymbol = "Cmaj9", protectedMelodyNotes = melodyAnchors)
        val result = MidiCoreChordGenerator.generate(generationContext)
        assertFalse(result.accepted)
        assertTrue(result.candidate.events.isEmpty())
        assertEquals(result, MidiCoreChordGenerator.generate(generationContext))
    }

    @Test
    fun `root omission requires continuous bass coverage in the pool and actual generation`() {
        val anchors = listOf(48, 60, 72, 84).mapIndexed { index, pitch ->
            MidiCoreProtectedMelodyNote("pmn-" + index.toString(16).repeat(64), 0, 1_920, pitch, 90, anchor = true)
        }
        val cases = listOf(
            false to listOf(MidiCoreGenerationNote(0, 480, 36, 80)),
            false to listOf(MidiCoreGenerationNote(0, 480, 36, 80), MidiCoreGenerationNote(960, 1_920, 36, 80)),
            false to listOf(MidiCoreGenerationNote(0, 480, 36, 80), MidiCoreGenerationNote(480, 1_920, 43, 80)),
            true to listOf(
                MidiCoreGenerationNote(960, 1_920, 36, 80),
                MidiCoreGenerationNote(0, 720, 36, 80),
                MidiCoreGenerationNote(480, 960, 36, 80),
            ),
        )
        cases.forEach { (supported, bassNotes) ->
            val generationContext = context(
                chordSymbol = "Cmaj9",
                protectedMelodyNotes = anchors,
                acceptedDependencies = listOf(MidiCoreAcceptedDependencyContext(
                    MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-root", "e".repeat(64)),
                    bassNotes,
                )),
            )
            val pool = MidiCoreChordGenerator.legalVoicingCandidates(generationContext, generationContext.chordWindows.single())
            assertEquals(supported, pool.any { candidate -> candidate.pitches.none { it % 12 == 0 } }, "$bassNotes")
            val result = MidiCoreChordGenerator.generate(generationContext)
            assertEquals(supported, result.accepted, "$bassNotes: ${result.validation.report.findings}")
            if (!supported) assertTrue(result.candidate.events.isEmpty())
            assertEquals(result, MidiCoreChordGenerator.generate(generationContext))
        }
    }

    @Test
    fun `retains the declared slash bass when reduced voicing is required`() {
        val melodyAnchors = listOf(48, 60, 72, 84).mapIndexed { index, pitch ->
            MidiCoreProtectedMelodyNote("pmn-" + index.toString(16).repeat(64), 0, 1_920, pitch, 90, anchor = true)
        }
        val result = MidiCoreChordGenerator.generate(
            context(
                chordSymbol = "C/E",
                profileId = "chords.pulsed",
                patternId = MidiCoreChordRhythmPatternId.BRIDGE_HALF_TIME.id,
                density = 0.5,
                protectedMelodyNotes = melodyAnchors,
                acceptedDependencies = listOf(
                    MidiCoreAcceptedDependencyContext(
                        MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-root", "e".repeat(64)),
                        listOf(MidiCoreGenerationNote(0, 1_920, 36, 80)),
                    ),
                ),
            ),
        )

        assertTrue(result.accepted, "Expected a safe slash-bass guide, got ${result.validation.report.findings}")
        result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .groupBy(MidiCoreCandidateEvent.Note::startTick)
            .values
            .forEach { voicing -> assertEquals(4, voicing.minOf(MidiCoreCandidateEvent.Note::pitch) % 12) }
    }

    @Test
    fun `builds a bounded legal pool with open guide tone and reduced piano choices`() {
        val generationContext = context(
            chordSymbol = "Cmaj9",
            density = 1.0,
            acceptedDependencies = listOf(
                MidiCoreAcceptedDependencyContext(
                    MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-root", "e".repeat(64)),
                    listOf(MidiCoreGenerationNote(0, 1_920, 36, 80)),
                ),
            ),
        )
        val candidates = MidiCoreChordGenerator.legalVoicingCandidates(
            generationContext,
            generationContext.chordWindows.single(),
        )

        assertTrue(candidates.size <= MidiCoreChordGenerator.MAX_PIANO_VOICING_CANDIDATES)
        assertTrue(
            MidiCoreChordGenerator.MidiCorePianoVoicingKind.entries.all { kind -> candidates.any { it.kind == kind } },
            "Expected compact, open, guide-tone, and reduced choices: $candidates",
        )
        assertTrue(candidates.all { candidate ->
            candidate.pitches.all { it in 48..84 } && candidate.pitches.zipWithNext().all { (low, high) -> low < high }
        })
        assertTrue(candidates.filter { it.kind in setOf(
            MidiCoreChordGenerator.MidiCorePianoVoicingKind.CLOSED,
            MidiCoreChordGenerator.MidiCorePianoVoicingKind.OPEN,
        ) }.all { it.pitches.map { pitch -> pitch % 12 }.toSet() == setOf(0, 2, 4, 7, 11) })
        assertTrue(candidates.filter { it.kind == MidiCoreChordGenerator.MidiCorePianoVoicingKind.GUIDE_TONE }.all {
            it.pitches.map { pitch -> pitch % 12 }.toSet() == setOf(2, 4, 11)
        })
        assertTrue(candidates.filter { it.kind == MidiCoreChordGenerator.MidiCorePianoVoicingKind.REDUCED }.all {
            it.pitches.map { pitch -> pitch % 12 }.toSet().containsAll(setOf(2, 4, 11))
        })
        val noBassContext = context(chordSymbol = "Cmaj9", density = 1.0)
        val noBassCandidates = MidiCoreChordGenerator.legalVoicingCandidates(noBassContext, noBassContext.chordWindows.single())
        assertTrue(noBassCandidates.all { candidate -> candidate.pitches.any { pitch -> pitch % 12 == 0 } })
    }

    @Test
    fun `keeps slash identity and bass space with the bounded candidate pool`() {
        val generationContext = context(
            chordSymbol = "Cmaj9/E",
            acceptedDependencies = listOf(
                MidiCoreAcceptedDependencyContext(
                    MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "d".repeat(64)),
                    listOf(MidiCoreGenerationNote(0, 1_920, 36, 80)),
                ),
            ),
        )
        val pool = MidiCoreChordGenerator.legalVoicingCandidates(generationContext, generationContext.chordWindows.single())
        val result = MidiCoreChordGenerator.generate(generationContext)
        val selected = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(pool.isNotEmpty())
        assertTrue(pool.all { candidate -> candidate.pitches.first() % 12 == 4 })
        assertTrue(result.accepted, "Expected bounded slash-bass candidate, got ${result.validation.report.findings}")
        assertTrue(selected.isNotEmpty())
        assertEquals(4, selected.minOf(MidiCoreCandidateEvent.Note::pitch) % 12)
        assertTrue(selected.all { note -> abs(note.pitch - 36) > 5 })
        assertEquals(result, MidiCoreChordGenerator.generate(generationContext))
    }

    @Test
    fun `carries an explicit adjacent phrase boundary through the bounded lookahead search`() {
        val boundaryProject = project(
            chordEvents = listOf(
                AuthoritativeChordEvent("verse-c", "verse-1", "C", 0, 1_920),
                AuthoritativeChordEvent("chorus-g", "chorus-1", "G", 1_920, 3_840),
                AuthoritativeChordEvent("chorus-c", "chorus-1", "C", 3_840, 5_760),
            ),
            occurrences = listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 1_920),
                ProjectSectionOccurrence("chorus-1", "chorus", "Chorus", 1_920, 5_760),
            ),
        )
        val first = MidiCoreChordGenerator.generate(context(project = boundaryProject, occurrenceId = "verse-1"))
        val boundary = requireNotNull(first.outgoingPianoVoicingBoundary)
        val continuationContext = context(
            project = boundaryProject,
            occurrenceId = "chorus-1",
            pianoVoicingBoundary = boundary,
        )
        val firstPlan = requireNotNull(MidiCoreChordGenerator.pianoVoicingPlan(continuationContext))
        val secondPlan = requireNotNull(MidiCoreChordGenerator.pianoVoicingPlan(continuationContext))
        val continued = MidiCoreChordGenerator.generate(continuationContext)

        assertTrue(first.accepted)
        assertTrue(continued.accepted, continued.validation.report.findings.toString())
        assertEquals(firstPlan, secondPlan)
        assertEquals(2, firstPlan.voicings.size)
        assertEquals(2, firstPlan.beamWidths.size)
        assertTrue(firstPlan.beamWidths.all { it in 1..MidiCoreChordGenerator.MAX_PIANO_VOICING_BEAM_WIDTH })
        assertTrue(boundary.pitches.zip(firstPlan.voicings.first()).all { (before, after) -> abs(after - before) <= 12 })
        assertEquals(continued, MidiCoreChordGenerator.generate(continuationContext))
        assertEquals("chorus-1", requireNotNull(continued.outgoingPianoVoicingBoundary).sourceOccurrenceId)
    }

    @Test
    fun `rejects a piano boundary that is not the adjacent current authority input`() {
        val boundaryProject = project(
            chordEvents = listOf(
                AuthoritativeChordEvent("verse-c", "verse-1", "C", 0, 1_920),
                AuthoritativeChordEvent("chorus-g", "chorus-1", "G", 1_920, 3_840),
            ),
            occurrences = listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 1_920),
                ProjectSectionOccurrence("chorus-1", "chorus", "Chorus", 1_920, 3_840),
            ),
        )
        val wrongAuthority = MidiCorePianoVoicingBoundarySummary("verse-1", 1_920, listOf(48, 52, 55), "f".repeat(64))

        assertFailsWith<IllegalArgumentException> {
            context(project = boundaryProject, occurrenceId = "chorus-1", pianoVoicingBoundary = wrongAuthority)
        }
    }

    @Test
    fun `seed and curated pattern identity produce repeatable distinct alternatives`() {
        val generationContext = context(density = 1.0)
        val alternatives = MidiCoreChordGenerator.generateAlternatives(generationContext, count = 2)
        val repeated = MidiCoreChordGenerator.generateAlternatives(generationContext, count = 2)

        assertEquals(2, alternatives.size)
        assertTrue(alternatives.all(MidiCoreChordGenerationResult::accepted))
        assertTrue(alternatives[0].candidate.events != alternatives[1].candidate.events)
        assertEquals(alternatives, repeated)
        assertEquals(
            listOf(MidiCoreChordRhythmPatternId.SUSTAINED.id, MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS.id),
            alternatives.map { it.context.patternId },
        )
        assertTrue(alternatives.map { it.context.seed }.distinct().size == 2)
    }

    @Test
    fun `returns a scoped rejection when authoritative boundaries are off the generation grid`() {
        val result = MidiCoreChordGenerator.generate(
            context(
                project = project(
                    chordEvents = listOf(
                        AuthoritativeChordEvent("first", "verse-1", "C", 0, 1),
                        AuthoritativeChordEvent("second", "verse-1", "F", 1, 1_920),
                    ),
                ),
                density = 1.0,
            ),
        )

        assertFalse(result.accepted)
        assertTrue(result.validation is MidiCoreRoleValidationResult.Rejected)
        assertTrue(result.validation.report.blockers.any { it.code == MidiCoreRoleFindingCode.UNREPRESENTABLE_TICK })
        assertEquals(result.context.contextSha256, result.validation.report.contextSha256)
    }

    private fun starts(result: MidiCoreChordGenerationResult): List<Long> = result.candidate.events
        .filterIsInstance<MidiCoreCandidateEvent.Note>()
        .map(MidiCoreCandidateEvent.Note::startTick)
        .distinct()

    private fun goldenPatterns(): Map<String, List<String>> = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/fixtures/midi-core/chords-golden.json")) { "Missing Chords golden fixture" }.readText(),
    ).jsonObject.getValue("patterns").jsonObject.mapValues { (_, values) ->
        values.jsonArray.map { it.jsonPrimitive.content }
    }

    private fun goldenNote(note: MidiCoreCandidateEvent.Note): String =
        "${note.startTick}|${note.endTick}|${note.pitch}|${note.velocity}"

    private fun context(
        patternId: String = MidiCoreChordRhythmPatternId.SUSTAINED.id,
        profileId: String = "chords.sustained",
        seed: Long = 17,
        density: Double = 1.0,
        chordSymbol: String = "C",
        project: MidiCoreProject = project(chordSymbol = chordSymbol),
        occurrenceId: String = "verse-1",
        protectedMelodyNotes: List<MidiCoreProtectedMelodyNote> = emptyList(),
        acceptedDependencies: List<MidiCoreAcceptedDependencyContext> = emptyList(),
        pianoVoicingBoundary: MidiCorePianoVoicingBoundarySummary? = null,
    ): MidiCoreGenerationContext = MidiCoreGenerationContext.forOccurrence(
        authority = MidiCoreAuthoritySnapshot.from(project),
        role = CandidateRole.CHORDS,
        occurrenceId = occurrenceId,
        performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.CHORDS, profileId),
        patternId = patternId,
        generator = MidiCoreGeneratorInput("test-generator", "test-v1", patternId, seed),
        protectedMelodyNotes = protectedMelodyNotes,
        acceptedDependencies = acceptedDependencies,
        sectionPolicy = MidiCoreSectionPolicy(density = density),
        pianoVoicingBoundary = pianoVoicingBoundary,
    )

    private fun protectedNote(pitch: Int, anchor: Boolean): MidiCoreProtectedMelodyNote = protectedNote(pitch, 0, 1_920, anchor)

    private fun protectedNote(
        pitch: Int,
        startTick: Long,
        endTick: Long,
        anchor: Boolean = false,
    ): MidiCoreProtectedMelodyNote = MidiCoreProtectedMelodyNote(
        id = "pmn-" + "${pitch.toString(16)}${startTick.toString(16)}${endTick.toString(16)}".padStart(64, if (anchor) 'a' else 'b'),
        startTick = startTick,
        endTick = endTick,
        pitch = pitch,
        velocity = 90,
        anchor = anchor,
    )

    private fun protectedMelodyView(startTick: Long, endTick: Long, pitch: Int): MidiProtectedMelodyView =
        MidiProtectedMelodySelector().select(
            SemanticMidiSequence(
                MidiSourceIdentity("a".repeat(64), "source.mid", 1, MidiPpq(480)),
                listOf(SemanticMidiTrack(0, listOf(MidiNoteEvent(
                    orderingKey = MidiEventOrderingKey(
                        startTick,
                        MidiSemanticEventKind.NOTE,
                        sourceEvent = MidiSourceEventIdentity(0, 0),
                    ),
                    endTick = endTick,
                    channel = 0,
                    pitch = pitch,
                    velocity = 90,
                )))),
            ),
            MidiMelodySelection(0, 0),
        )

    private fun closestDistance(result: MidiCoreChordGenerationResult, melodyPitch: Int): Int = result.candidate.events
        .filterIsInstance<MidiCoreCandidateEvent.Note>()
        .minOf { note -> abs(note.pitch - melodyPitch) }

    private fun melodyEvidence(
        context: MidiCoreGenerationContext,
        melody: MidiCoreProtectedMelodyNote,
        cause: MidiCoreHarmonyTensionCause,
        accented: Boolean,
    ): MidiCoreMelodyHarmonyAnalysis {
        val note = MidiCoreMelodyNoteContext(
            id = melody.id,
            startTick = melody.startTick,
            keyReleaseTick = melody.endTick,
            soundingEndTick = melody.endTick,
            pitch = melody.pitch,
            velocity = melody.velocity,
            anchor = melody.anchor,
        )
        val window = context.chordWindows.single()
        val location = MidiCoreMelodyLocation(barNumber = 1, beatNumber = 1, tickIntoBeat = 0, pickup = false)
        val finding = MidiCoreHarmonyTensionFinding(
            cause = cause,
            confidence = if (cause == MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION) {
                MidiCoreHarmonyTensionConfidence.HIGH
            } else {
                MidiCoreHarmonyTensionConfidence.MEDIUM
            },
            occurrenceId = context.occurrence.id,
            chordEventId = window.event.id,
            noteId = note.id,
            startTick = note.startTick,
            endTick = note.soundingEndTick,
            location = location,
            overlapTicks = note.soundingEndTick - note.startTick,
            beatProminence = if (accented) 1.0 else 0.5,
            intervalAboveRootSemitones = 5,
            nearestChordToneDistanceSemitones = 1,
            message = "Test-only read-only melody evidence.",
        )
        return MidiCoreMelodyHarmonyAnalysis(
            version = MidiCoreMelodyHarmonyAnalyzer.ANALYSIS_VERSION,
            authorityHash = context.authority.authorityHash,
            melodySha256 = context.authority.melodySha256,
            notes = listOf(note),
            beats = emptyList(),
            bars = emptyList(),
            phrases = emptyList(),
            windows = listOf(MidiCoreMelodyHarmonyWindowContext(
                chordEventId = window.event.id,
                occurrenceId = context.occurrence.id,
                chordSymbol = window.chord.canonicalSymbol,
                startTick = window.startTick,
                endTick = window.endTick,
                location = location,
                activity = MidiCoreMelodyActivity(
                    activeNotes = listOf(note),
                    soundingNotes = listOf(note),
                    rests = emptyList(),
                    register = MidiCoreMelodyRegister(note.pitch, note.pitch),
                ),
                accentedNoteIds = if (accented) listOf(note.id) else emptyList(),
                phraseHints = emptyList(),
                findings = listOf(finding),
            )),
            findings = listOf(finding),
        )
    }

    private fun project(
        chordSymbol: String = "C",
        chordEvents: List<AuthoritativeChordEvent> = listOf(
            AuthoritativeChordEvent("verse-chord", "verse-1", chordSymbol, 0, 1_920),
        ),
        occurrences: List<ProjectSectionOccurrence> = listOf(
            ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, chordEvents.maxOf(AuthoritativeChordEvent::endTick)),
        ),
        sourceSha256: String = "a".repeat(64),
        melodyIdentitySha256: String = "c".repeat(64),
        ppq: Int = 480,
        meter: ProjectMeter = ProjectMeter(4, 2),
    ): MidiCoreProject = MidiCoreProject(
        id = ProjectId("chord-generator-project"),
        metadata = ProjectMetadata("Chord generator", "2026-08-27T00:00:00Z"),
        sourceMidi = SourceMidiRecord(
            originalFilename = "source.mid",
            sha256 = sourceSha256,
            format = 1,
            ppq = ppq,
            original = ProjectArtifact(ProjectRelativePath("source/original.mid"), sourceSha256),
            importReport = ProjectArtifact(ProjectRelativePath("reports/import.json"), "b".repeat(64)),
            trackSummaries = listOf(MidiTrackSummary(0, "Melody", emptyList())),
            sourceEndTick = chordEvents.maxOf(AuthoritativeChordEvent::endTick),
        ),
        selectedMelody = SelectedMelodyTrack(0, 0, melodyIdentitySha256),
        authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000),
            meter = meter,
            sectionDefinitions = occurrences.map { ProjectSectionDefinition(it.definitionId, it.label) }.distinctBy(ProjectSectionDefinition::id),
            occurrences = occurrences,
            chordEvents = chordEvents,
        ),
    )
}
