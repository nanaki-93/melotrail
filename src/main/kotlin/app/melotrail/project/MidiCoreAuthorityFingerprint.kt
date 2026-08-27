package app.melotrail.project

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Authority dimensions that can independently make derived MIDI work stale. */
enum class MidiCoreAuthorityDimension {
    SOURCE,
    MELODY,
    TIMING,
    STRUCTURE,
    HARMONY,
    SETTINGS,
}

/** Deterministic, non-production settings supplied to the target arranger. */
data class MidiCoreAuthoritySettings(val values: Map<String, String> = emptyMap()) {
    init {
        require(values.keys.all { SETTING_KEY.matches(it) }) { "Authority setting keys must be stable identifiers" }
        require(values.values.all { it.length <= 1_000 && it.none(Char::isISOControl) }) {
            "Authority setting values must be bounded and printable"
        }
    }

    val canonicalSerialization: String
        get() = canonicalRecord("settings", values.toSortedMap().map { (key, value) -> key to value })
}

/** Identifies one role's derived work in one exact section occurrence. */
data class MidiCoreAuthorityScopeKey(val occurrenceId: String, val role: CandidateRole) {
    init { require(SAFE_ID.matches(occurrenceId)) { "Authority scope occurrence ID is invalid" } }
}

/** Hash material used to prove which occurrence-local authority a candidate consumed. */
data class MidiCoreAuthorityScopeFingerprint(
    val key: MidiCoreAuthorityScopeKey,
    val structureSha256: String,
    val harmonySha256: String,
    val settingsSha256: String,
    val sha256: String,
) {
    init {
        requireHash(structureSha256, "Scope structure hash")
        requireHash(harmonySha256, "Scope harmony hash")
        requireHash(settingsSha256, "Scope settings hash")
        requireHash(sha256, "Scope hash")
    }
}

/** Canonical authority inputs and one scoped hash for every role/occurrence pair. */
data class MidiCoreAuthorityFingerprint(
    val sourceSha256: String,
    val melodySha256: String,
    val timingSha256: String,
    val structureSha256: String,
    val harmonySha256: String,
    val settingsSha256: String,
    val scopes: List<MidiCoreAuthorityScopeFingerprint>,
) {
    init {
        requireHash(sourceSha256, "Source authority hash")
        requireHash(melodySha256, "Melody authority hash")
        requireHash(timingSha256, "Timing authority hash")
        requireHash(structureSha256, "Structure authority hash")
        requireHash(harmonySha256, "Harmony authority hash")
        requireHash(settingsSha256, "Settings authority hash")
        require(scopes.map(MidiCoreAuthorityScopeFingerprint::key).distinct().size == scopes.size) {
            "Authority scope keys must be unique"
        }
        require(scopes == scopes.sortedWith(scopeOrder())) { "Authority scopes must be deterministic" }
    }

    /** Stable text used as the input to [sha256]. */
    val canonicalSerialization: String
        get() = canonicalRecord(
            "authority",
            listOf(
                "source" to sourceSha256,
                "melody" to melodySha256,
                "timing" to timingSha256,
                "structure" to structureSha256,
                "harmony" to harmonySha256,
                "settings" to settingsSha256,
                "scopes" to scopes.joinToString(";") { scope ->
                    canonicalRecord(
                        "scope",
                        listOf(
                            "occurrence" to scope.key.occurrenceId,
                            "role" to scope.key.role.name,
                            "structure" to scope.structureSha256,
                            "harmony" to scope.harmonySha256,
                            "settings" to scope.settingsSha256,
                            "hash" to scope.sha256,
                        ),
                    )
                },
            ),
        )

    /** Hash of the complete authority snapshot, including all scoped hashes. */
    val sha256: String get() = digest(canonicalSerialization)

    fun scopeHash(occurrenceId: String, role: CandidateRole): String =
        scopes.singleOrNull { it.key == MidiCoreAuthorityScopeKey(occurrenceId, role) }?.sha256
            ?: throw IllegalArgumentException("No authority scope exists for $occurrenceId/${role.name}")

    fun scope(occurrenceId: String, role: CandidateRole): MidiCoreAuthorityScopeFingerprint =
        scopes.singleOrNull { it.key == MidiCoreAuthorityScopeKey(occurrenceId, role) }
            ?: throw IllegalArgumentException("No authority scope exists for $occurrenceId/${role.name}")
}

/** Builds canonical authority hashes from one immutable target project. */
object MidiCoreAuthorityHasher {
    fun from(project: MidiCoreProject, settings: MidiCoreAuthoritySettings = MidiCoreAuthoritySettings()): MidiCoreAuthorityFingerprint {
        val authority = project.authority
        val sourceSha256 = project.sourceMidi?.sha256 ?: missingHash("source")
        val melodySha256 = project.selectedMelody?.identitySha256 ?: missingHash("melody")
        val timingSha256 = digest(
            if (authority == null) "authority=absent"
            else canonicalRecord(
                "timing",
                listOf(
                    "tempo" to authority.tempo.microsecondsPerQuarter.toString(),
                    "meter-numerator" to authority.meter.numerator.toString(),
                    "meter-denominator-exponent" to authority.meter.denominatorExponent.toString(),
                    "pickup-ticks" to authority.pickupTicks.toString(),
                ),
            ),
        )
        val structureSerialization = canonicalRecord(
            "structure",
            listOf("present" to (authority != null).toString()) +
                (authority?.sectionDefinitions.orEmpty().mapIndexed { index, definition ->
                    "definition[$index]" to canonicalRecord("definition", listOf("id" to definition.id, "name" to definition.name))
                }) +
                (authority?.occurrences.orEmpty().mapIndexed { index, occurrence ->
                    "occurrence[$index]" to canonicalRecord(
                        "occurrence",
                        listOf(
                            "id" to occurrence.id,
                            "definition" to occurrence.definitionId,
                            "label" to occurrence.label,
                            "start" to occurrence.startTick.toString(),
                            "end" to occurrence.endTick.toString(),
                        ),
                    )
                }),
        )
        val harmonySerialization = canonicalRecord(
            "harmony",
            listOf(
                "present" to (authority != null).toString(),
                "key-tonic" to (authority?.key?.tonic?.toString() ?: "absent"),
                "key-mode" to (authority?.key?.modeId ?: "absent"),
                "key-spelling" to (authority?.key?.spelling?.symbol ?: "absent"),
            ) + (authority?.chordEvents.orEmpty().mapIndexed { index, event ->
                "event[$index]" to canonicalRecord(
                    "chord",
                    listOf(
                        "id" to event.id,
                        "occurrence" to event.occurrenceId,
                        "symbol" to event.symbol,
                        "start" to event.startTick.toString(),
                        "end" to event.endTick.toString(),
                    ),
                )
            }),
        )
        val structureSha256 = digest(structureSerialization)
        val harmonySha256 = digest(harmonySerialization)
        val settingsSha256 = digest(settings.canonicalSerialization)
        val scopes = authority?.let { current ->
            val definitions = current.sectionDefinitions.associateBy(ProjectSectionDefinition::id)
            current.occurrences.flatMap { occurrence ->
                val definition = requireNotNull(definitions[occurrence.definitionId])
                val scopeStructureSha256 = digest(
                    canonicalRecord(
                        "scope-structure",
                        listOf(
                            "definition-id" to definition.id,
                            "definition-name" to definition.name,
                            "occurrence-id" to occurrence.id,
                            "label" to occurrence.label,
                            "start" to occurrence.startTick.toString(),
                            "end" to occurrence.endTick.toString(),
                        ),
                    ),
                )
                val scopeHarmonySha256 = digest(
                    canonicalRecord(
                        "scope-harmony",
                        listOf(
                            "key-tonic" to current.key.tonic.toString(),
                            "key-mode" to current.key.modeId,
                            "key-spelling" to current.key.spelling.symbol,
                        ) + current.chordEvents.filter { it.occurrenceId == occurrence.id }.mapIndexed { index, event ->
                            "event[$index]" to canonicalRecord(
                                "chord",
                                listOf(
                                    "id" to event.id,
                                    "symbol" to event.symbol,
                                    "start" to event.startTick.toString(),
                                    "end" to event.endTick.toString(),
                                ),
                            )
                        },
                    ),
                )
                CandidateRole.entries.map { role ->
                    val key = MidiCoreAuthorityScopeKey(occurrence.id, role)
                    val rolePrefix = "${role.name.lowercase()}."
                    val scopeSettingsSha256 = digest(
                        canonicalRecord(
                            "scope-settings",
                            settings.values.filterKeys { settingKey ->
                                CandidateRole.entries.none { candidateRole ->
                                    settingKey.startsWith("${candidateRole.name.lowercase()}.")
                                } || settingKey.startsWith(rolePrefix)
                            }.toSortedMap().map { (settingKey, value) -> settingKey to value },
                        ),
                    )
                    val scopeSha256 = digest(
                        canonicalRecord(
                            "authority-scope",
                            listOf(
                                "source" to sourceSha256,
                                "melody" to melodySha256,
                                "timing" to timingSha256,
                                "structure" to scopeStructureSha256,
                                "harmony" to scopeHarmonySha256,
                                "settings" to scopeSettingsSha256,
                                "occurrence" to occurrence.id,
                                "role" to role.name,
                            ),
                        ),
                    )
                    MidiCoreAuthorityScopeFingerprint(key, scopeStructureSha256, scopeHarmonySha256, scopeSettingsSha256, scopeSha256)
                }
            }
        }.orEmpty().sortedWith(scopeOrder())
        return MidiCoreAuthorityFingerprint(
            sourceSha256,
            melodySha256,
            timingSha256,
            structureSha256,
            harmonySha256,
            settingsSha256,
            scopes,
        )
    }

    fun generation(
        project: MidiCoreProject,
        occurrenceId: String,
        role: CandidateRole,
        generator: MidiCoreGeneratorInput,
        acceptedDependencies: List<MidiCoreAcceptedDependency> = emptyList(),
        settings: MidiCoreAuthoritySettings = MidiCoreAuthoritySettings(),
    ): MidiCoreGenerationFingerprint = MidiCoreGenerationFingerprint(
        from(project, settings),
        MidiCoreAuthorityScopeKey(occurrenceId, role),
        generator,
        acceptedDependencies,
    )
}

/** Generator identity and explicit seed recorded alongside a deterministic run. */
data class MidiCoreGeneratorInput(
    val generatorId: String,
    val generatorVersion: String,
    val patternId: String,
    val seed: Long,
) {
    init {
        require(TOKEN.matches(generatorId) && TOKEN.matches(generatorVersion) && TOKEN.matches(patternId)) {
            "Generator identity and pattern ID must be stable tokens"
        }
    }

    val canonicalSerialization: String
        get() = canonicalRecord(
            "generator",
            listOf(
                "id" to generatorId,
                "version" to generatorVersion,
                "pattern" to patternId,
                "seed" to seed.toString(),
            ),
        )
}

/** Accepted candidate input that a later role generation consumed. */
data class MidiCoreAcceptedDependency(
    val role: CandidateRole,
    val occurrenceId: String,
    val candidateId: String,
    val authorityHash: String,
) {
    init {
        require(SAFE_ID.matches(occurrenceId) && SAFE_ID.matches(candidateId)) { "Accepted dependency identity is invalid" }
        requireHash(authorityHash, "Accepted dependency authority hash")
    }
}

/** Full deterministic generation identity: scoped authority, generator, and accepted inputs. */
data class MidiCoreGenerationFingerprint(
    val authority: MidiCoreAuthorityFingerprint,
    val scope: MidiCoreAuthorityScopeKey,
    val generator: MidiCoreGeneratorInput,
    val acceptedDependencies: List<MidiCoreAcceptedDependency> = emptyList(),
) {
    init {
        authority.scope(scope.occurrenceId, scope.role)
        require(acceptedDependencies.map { it.role to it.occurrenceId }.distinct().size == acceptedDependencies.size) {
            "Accepted dependencies must have one input per role/occurrence"
        }
    }

    val authorityHash: String get() = authority.scopeHash(scope.occurrenceId, scope.role)

    val canonicalSerialization: String
        get() = canonicalRecord(
            "generation",
            listOf(
                "authority" to authorityHash,
                "scope-occurrence" to scope.occurrenceId,
                "scope-role" to scope.role.name,
                "generator" to generator.canonicalSerialization,
                "accepted" to acceptedDependencies.sortedWith(
                    compareBy<MidiCoreAcceptedDependency> { it.occurrenceId }
                        .thenBy { it.role.ordinal }
                        .thenBy { it.candidateId },
                ).joinToString(";") { dependency ->
                    canonicalRecord(
                        "dependency",
                        listOf(
                            "role" to dependency.role.name,
                            "occurrence" to dependency.occurrenceId,
                            "candidate" to dependency.candidateId,
                            "authority" to dependency.authorityHash,
                        ),
                    )
                },
            ),
        )

    val sha256: String get() = digest(canonicalSerialization)
}

private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
private val SETTING_KEY = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,119}")
private val TOKEN = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}")
private val HASH = Regex("[0-9a-f]{64}")

private fun requireHash(value: String, label: String) {
    require(HASH.matches(value)) { "$label must be a lowercase SHA-256 value" }
}

private fun scopeOrder() = compareBy<MidiCoreAuthorityScopeFingerprint> { it.key.occurrenceId }.thenBy { it.key.role.ordinal }

private fun canonicalRecord(type: String, fields: List<Pair<String, String>>): String = buildString {
    append(type)
    fields.forEach { (name, value) ->
        append('|').append(name).append('=').append(value.length).append(':').append(value)
    }
}

private fun missingHash(dimension: String): String = digest("melotrail-midi-core:missing:$dimension")

private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
