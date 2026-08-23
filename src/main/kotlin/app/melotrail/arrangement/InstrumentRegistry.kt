package app.melotrail.arrangement

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.ArrayDeque
import app.melotrail.commercial.CommercialDependency
import app.melotrail.commercial.CommercialDependencyKind
import app.melotrail.commercial.CommercialTerm

/** The only logical names planners may use. Filesystem paths never leave this boundary. */
enum class LogicalInstrument(val wireName: String) {
    PIANO("piano"), BASS("bass"), DRUMS("drums"), PAD("pad"), STRINGS("strings");

    companion object {
        fun parse(value: String): LogicalInstrument = entries.firstOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("Unsupported instrument: $value. Allowed instruments: ${entries.joinToString { it.wireName }}")
    }
}

@Serializable
data class InstrumentRegistryFile(
    val version: Int,
    val workingSampleRate: Int,
    /** Registry values are human-readable 1..16; rendering receives channel - 1. */
    val midiChannelConvention: String,
    val instruments: Map<String, InstrumentRegistryEntry>
)

@Serializable
data class InstrumentRegistryEntry(
    val engine: String,
    val path: String,
    val licenseId: String,
    val midiProgram: Int? = null,
    val midiChannel: Int? = null,
    val noteMap: Map<String, Int>? = null
)

/** Registry-v2 is a catalog: stable IDs are independent from engines and legacy aliases. */
@Serializable
data class InstrumentRegistryV2File(
    val version: Int,
    val workingSampleRate: Int,
    val midiChannelConvention: String = "one-based",
    val instruments: List<InstrumentDefinition>
)

/** Registry-v3 keeps native sample rates so a production catalog can combine 44.1 and 48 kHz packs. */
@Serializable
data class InstrumentRegistryV3File(
    val version: Int,
    val supportedSampleRates: Set<Int>,
    val midiChannelConvention: String = "one-based",
    val instruments: List<InstrumentDefinition>
)

@Serializable enum class InstrumentSelectionMode { @SerialName("automatic") AUTOMATIC, @SerialName("manual-only") MANUAL_ONLY }
@Serializable enum class InstrumentQualityTier { @SerialName("draft") DRAFT, @SerialName("production") PRODUCTION }
@Serializable enum class InstrumentEngineType {
    @SerialName("sfz") SFZ,
    @SerialName("sf2") SF2,
    @SerialName("vst3") VST3,
    @SerialName("audio-unit") AUDIO_UNIT;

    val extension: String get() = when (this) {
        SFZ -> ".sfz"; SF2 -> ".sf2"; VST3 -> ".vst3"; AUDIO_UNIT -> ".component"
    }
}

@Serializable
data class InstrumentDefinition(
    val id: String,
    val name: String,
    val category: String = "instrument",
    val selectionMode: InstrumentSelectionMode = InstrumentSelectionMode.AUTOMATIC,
    /** Explicit audition gate for automatic production selection. */
    val productionApproved: Boolean = false,
    val qualityTier: InstrumentQualityTier = InstrumentQualityTier.DRAFT,
    /** Stable style labels, intentionally separate from profile score weights. */
    val styleAffinity: Set<String> = emptySet(),
    val roles: Set<ArrangementRole>,
    /** Roles for which this preset is preferred when it supports several. */
    val preferredRoles: Set<ArrangementRole> = emptySet(),
    val profileAffinities: Map<String, Double> = emptyMap(),
    val moodAffinities: Map<String, Double> = emptyMap(),
    val sectionAffinities: Map<String, Double> = emptyMap(),
    val attackTraits: Set<String> = emptySet(),
    val toneTraits: Set<String> = emptySet(),
    val articulationTraits: Set<String> = emptySet(),
    val engine: InstrumentEngineDescriptor,
    val license: InstrumentLicenseMetadata,
    val library: SourceLibraryProvenance,
    val capabilities: DeclaredInstrumentCapabilities = DeclaredInstrumentCapabilities(),
    val midiProgram: Int? = null,
    val midiChannel: Int? = null
)

@Serializable data class InstrumentEngineDescriptor(val type: InstrumentEngineType, val path: String)
@Serializable data class SourceLibraryProvenance(val id: String, val name: String, val version: String, val source: String)
@Serializable
data class InstrumentLicenseMetadata(
    val id: String,
    val commercialUse: Boolean,
    val attributionRequired: Boolean,
    val attributionText: String? = null,
    val sourceName: String,
    val sourceUrl: String? = null,
    val licenseUrl: String? = null,
    val licenseText: String? = null,
    val policyReview: String? = null,
    val policyVersion: String? = null
)
@Serializable
data class DeclaredInstrumentCapabilities(
    val playableRange: MidiPlayableRange? = null,
    val velocityLayers: Int? = null,
    val roundRobin: Boolean? = null,
    val releaseSamples: Boolean? = null,
    val articulations: Set<String> = emptySet(),
    val polyphony: Int? = null,
    val noteMap: Map<String, Int> = emptyMap(),
    val performance: Set<PerformanceCapability> = emptySet()
)
@Serializable data class MidiPlayableRange(val low: Int, val high: Int)

enum class LicenseAdmission { ADMITTED, UNAVAILABLE }
data class LicenseAdmissionResult(val admission: LicenseAdmission, val reasons: List<String>)
data class VerifiedInstrumentCapabilities(
    val playableRange: MidiPlayableRange,
    val velocityLayers: Int,
    val roundRobin: Boolean,
    val releaseSamples: Boolean,
    val performance: Set<PerformanceCapability>,
    val declaredOnly: Set<String>
)

@Serializable
data class LicenseRegistryFile(val version: Int, val libraries: Map<String, SoundLibraryLicense>)

@Serializable
data class SoundLibraryLicense(
    val displayName: String,
    val source: String,
    val provenance: String,
    val license: String,
    val licenseTextPath: String? = null,
    val commercialUse: Boolean,
    val attributionRequired: Boolean,
    val attributionText: String? = null,
    val redistribution: String,
    val date: String? = null,
    val notes: String? = null
)

/** Commercial export uses this snapshot, not mutable library paths. */
fun SoundLibraryLicense.commercialDependency(identity: String, contentHash: String?): CommercialDependency = CommercialDependency(
    kind = CommercialDependencyKind.SOUND_LIBRARY,
    identity = identity,
    version = "registry-v1",
    contentHash = contentHash,
    commercialTerm = if (commercialUse) CommercialTerm.PERMITTED else CommercialTerm.BLOCKED,
    reviewed = date != null,
    license = license,
    source = source,
    attribution = attributionText?.takeIf { attributionRequired }
)

data class ValidatedInstrumentDescriptor(
    /** The only instrument identifier that leaves the registry boundary. */
    val id: String,
    val name: String,
    val category: String = "instrument",
    val selectionMode: InstrumentSelectionMode = InstrumentSelectionMode.AUTOMATIC,
    val productionApproved: Boolean = false,
    val qualityTier: InstrumentQualityTier = InstrumentQualityTier.DRAFT,
    val styleAffinity: Set<String> = emptySet(),
    val roles: Set<ArrangementRole>,
    val preferredRoles: Set<ArrangementRole> = emptySet(),
    val engine: InstrumentEngineDescriptor,
    val enginePath: Path,
    val samplePaths: List<Path>,
    val license: SoundLibraryLicense,
    val licenseAdmission: LicenseAdmissionResult,
    val verifiedCapabilities: VerifiedInstrumentCapabilities,
    val profileAffinities: Map<String, Double> = emptyMap(),
    val moodAffinities: Map<String, Double> = emptyMap(),
    val sectionAffinities: Map<String, Double> = emptyMap(),
    val attackTraits: Set<SoundTrait> = emptySet(),
    val toneTraits: Set<SoundTrait> = emptySet(),
    val articulationTraits: Set<SoundTrait> = emptySet(),
    val midiProgram: Int?,
    /** MIDI API channel (zero based); the JSON value is explicitly one based. */
    val midiChannelZeroBased: Int?,
    val noteMap: Map<String, Int>,
    /** Snapshot-safe source-library identity; never exposes a local library path. */
    val sourceLibrary: SourceLibraryProvenance = SourceLibraryProvenance(
        id = "legacy-library",
        name = "Legacy sound library",
        version = "v1",
        source = "legacy registry compatibility"
    )
) {
    /** Compatibility accessor for the existing SFZ renderer; never use for non-SFZ engines. */
    val sfzPath: Path
        get() = require(engine.type == InstrumentEngineType.SFZ) { "Instrument '$id' is not an SFZ instrument" }.let { enginePath }
}

class ValidatedInstrumentRegistry internal constructor(
    private val descriptors: Map<String, ValidatedInstrumentDescriptor>,
    val version: Int,
    val registrySha256: String
) {
    fun logicalNames(): Set<String> = descriptors.keys
    fun plannerNames(): List<String> = descriptors.keys.sorted()
    fun resolve(name: String): ValidatedInstrumentDescriptor = descriptors[name]
        ?: throw IllegalArgumentException("Instrument is not validated: $name")

    /**
     * Resolves the concrete sound selected for a logical arrangement role.
     *
     * Registry v1 deliberately uses the logical names as its stable IDs. Newer
     * catalogs do not: their IDs identify a particular installed instrument,
     * and the approved arrangement assignments bind those IDs to a role.
     */
    fun resolveApprovedRole(project: Project, logical: LogicalInstrument): ValidatedInstrumentDescriptor {
        if (version == 1) return resolve(logical.wireName)

        val candidates = project.envelope.arrangementAssignments
            .filter { assignment -> assignment.logicalInstrument.isEmpty() || assignment.logicalInstrument == logical.wireName }
            .map { assignment -> assignment to resolve(assignment.instrumentId) }
            .filter { (assignment, descriptor) -> assignment.logicalInstrument == logical.wireName || LegacyLogicalInstrumentRoles.roleFor(logical.wireName) in descriptor.roles }
            .map { (_, descriptor) -> descriptor }
            .distinctBy(ValidatedInstrumentDescriptor::id)
        require(candidates.isNotEmpty()) {
            "MIDI generation has no approved stable instrument assignment for role '${logical.wireName}'. Choose one in Arrange and approve the arrangement."
        }
        require(candidates.size == 1) {
            "MIDI generation supports one approved stable instrument per role; '${logical.wireName}' has ${candidates.joinToString { it.id }}. Choose one approved instrument and regenerate the arrangement."
        }
        return candidates.single().also { descriptor ->
            require(descriptor.licenseAdmission.admission == LicenseAdmission.ADMITTED) {
                "Approved instrument '${descriptor.id}' is unavailable: ${descriptor.licenseAdmission.reasons.joinToString("; ")}. Choose a permitted replacement in Arrange and approve it."
            }
        }
    }
    /** Immutable validated descriptors for evidence export; callers never receive registry paths as outputs. */
    fun all(): List<ValidatedInstrumentDescriptor> = descriptors.values.sortedBy { it.id }
    fun availableFor(role: ArrangementRole): List<ValidatedInstrumentDescriptor> = all().filter { role in it.roles && it.licenseAdmission.admission == LicenseAdmission.ADMITTED }
    fun automaticFor(role: ArrangementRole): List<ValidatedInstrumentDescriptor> = availableFor(role).filter {
        it.selectionMode == InstrumentSelectionMode.AUTOMATIC && it.productionApproved
    }
    fun hasRoleCoverage(required: Set<ArrangementRole>): Boolean = required.all { role -> availableFor(role).isNotEmpty() }
}

/** Local-only registry loader. Accepts a validated [SoundLibraryLocation.Success] from [SoundLibraryLocator]. */
class InstrumentRegistryLoader(val libraryRoot: Path) {
    private val validatedSamples = mutableSetOf<Path>()

    fun load(): ValidatedInstrumentRegistry {
        val root = libraryRoot.toAbsolutePath().normalize()
        require(Files.isDirectory(root)) { "Sound library root does not exist: $root. See sounds/README.md for the local asset setup." }
        val realRoot = root.toRealPath()
        val registryPath = safeFile(root, root, realRoot, "instruments.json", "Instrument registry")
        val registryContents = Files.readString(registryPath, StandardCharsets.UTF_8)
        val version = json.parseToJsonElement(registryContents).jsonObject["version"]?.toString()?.toIntOrNull()
            ?: throw IllegalArgumentException("Instrument registry requires an integer version")
        return when (version) {
            1 -> loadV1(root, realRoot, registryContents)
            2 -> loadV2(root, realRoot, registryContents)
            3 -> loadV3(root, realRoot, registryContents)
            else -> throw IllegalArgumentException("Unsupported instrument registry version: $version")
        }
    }

    private fun loadV1(root: Path, realRoot: Path, registryContents: String): ValidatedInstrumentRegistry {
        val registry = json.decodeFromString<InstrumentRegistryFile>(registryContents)
        val licensesContents = Files.readString(safeFile(root, root, realRoot, "LICENSES.json", "License registry"), StandardCharsets.UTF_8)
        val licenses = json.decodeFromString<LicenseRegistryFile>(licensesContents)
        require(registry.workingSampleRate > 0) { "Instrument registry workingSampleRate must be positive" }
        require(registry.midiChannelConvention == "one-based") { "Instrument registry midiChannelConvention must be 'one-based'" }
        validateLicenses(licenses, root, realRoot)
        validateNoDuplicateInstrumentKeys(registryContents)
        val names = registry.instruments.keys
        require(names.groupingBy { it.lowercase() }.eachCount().values.all { it == 1 }) { "Instrument registry contains case-conflicting logical names" }
        require(names == LogicalInstrument.entries.map { it.wireName }.toSet()) {
            "Instrument registry must contain exactly: ${LogicalInstrument.entries.joinToString { it.wireName }}"
        }
        val descriptors = registry.instruments.map { (name, entry) ->
            val logical = LogicalInstrument.parse(name)
            require(entry.engine == "sfz") { "Instrument '$name' uses unsupported engine '${entry.engine}'" }
            require(entry.licenseId.isNotBlank()) { "Instrument '$name' has no licenseId" }
            val license = licenses.libraries[entry.licenseId] ?: throw IllegalArgumentException("Instrument '$name' references missing license '${entry.licenseId}'")
            validateMidi(entry, name)
            val sfz = safeFile(root, root, realRoot, entry.path, "Instrument '$name' SFZ")
            require(sfz.fileName.toString().endsWith(".sfz")) { "Instrument '$name' path must be an .sfz file" }
            val regions = parseSfz(sfz, root, realRoot, name, setOf(registry.workingSampleRate))
            if (logical == LogicalInstrument.DRUMS) validateDrumMap(entry.noteMap, regions)
            logical.wireName to ValidatedInstrumentDescriptor(
                id = logical.wireName, name = logical.wireName.replaceFirstChar(Char::uppercase),
                productionApproved = true, qualityTier = InstrumentQualityTier.PRODUCTION,
                roles = setOf(LegacyLogicalInstrumentRoles.roleFor(logical.wireName)),
                preferredRoles = setOf(LegacyLogicalInstrumentRoles.roleFor(logical.wireName)),
                engine = InstrumentEngineDescriptor(InstrumentEngineType.SFZ, entry.path), enginePath = sfz,
                samplePaths = regions.map { it.sample }, license = license,
                licenseAdmission = license.admissionResult(),
                verifiedCapabilities = regions.verifiedCapabilities(entry.noteMap.orEmpty(), logical == LogicalInstrument.DRUMS),
                midiProgram = entry.midiProgram, midiChannelZeroBased = entry.midiChannel?.minus(1), noteMap = entry.noteMap ?: emptyMap(),
                sourceLibrary = SourceLibraryProvenance(
                    id = "starter-generated",
                    name = license.displayName,
                    version = "registry-v1",
                    source = license.source
                )
            )
        }.toMap()
        return ValidatedInstrumentRegistry(descriptors, 1, sha256(registryContents + licensesContents))
    }

    /**
     * A v2 catalog may contain unavailable entries, but not an ambiguous or
     * malformed catalog. This keeps an installed pack inspectable while making
     * unsafe entries impossible for the resolver to select.
     */
    private fun loadV2(root: Path, realRoot: Path, registryContents: String): ValidatedInstrumentRegistry {
        val registry = json.decodeFromString<InstrumentRegistryV2File>(registryContents)
        require(registry.workingSampleRate > 0) { "Instrument registry workingSampleRate must be positive" }
        require(registry.midiChannelConvention == "one-based") { "Instrument registry midiChannelConvention must be 'one-based'" }
        require(registry.instruments.isNotEmpty()) { "Instrument registry v2 must contain instruments" }
        require(registry.instruments.map(InstrumentDefinition::id).distinct().size == registry.instruments.size) { "Instrument registry contains duplicate stable IDs" }
        require(registry.instruments.groupingBy { it.id.lowercase() }.eachCount().values.all { it == 1 }) { "Instrument registry contains case-conflicting stable IDs" }
        val descriptors = registry.instruments.associate { definition ->
            definition.id to validateV2Entry(root, realRoot, setOf(registry.workingSampleRate), definition)
        }
        return ValidatedInstrumentRegistry(descriptors, 2, sha256(registryContents))
    }

    private fun loadV3(root: Path, realRoot: Path, registryContents: String): ValidatedInstrumentRegistry {
        val registry = json.decodeFromString<InstrumentRegistryV3File>(registryContents)
        require(registry.midiChannelConvention == "one-based") { "Instrument registry midiChannelConvention must be 'one-based'" }
        require(registry.supportedSampleRates.isNotEmpty() && registry.supportedSampleRates.all { it in 8_000..384_000 }) {
            "Instrument registry v3 requires supported sample rates from 8000 to 384000"
        }
        require(registry.instruments.isNotEmpty()) { "Instrument registry v3 must contain instruments" }
        require(registry.instruments.map(InstrumentDefinition::id).distinct().size == registry.instruments.size) { "Instrument registry contains duplicate stable IDs" }
        require(registry.instruments.groupingBy { it.id.lowercase() }.eachCount().values.all { it == 1 }) { "Instrument registry contains case-conflicting stable IDs" }
        val descriptors = registry.instruments.associate { definition ->
            definition.id to validateV2Entry(root, realRoot, registry.supportedSampleRates, definition)
        }
        return ValidatedInstrumentRegistry(descriptors, 3, sha256(registryContents))
    }

    private fun validateV2Entry(root: Path, realRoot: Path, supportedSampleRates: Set<Int>, definition: InstrumentDefinition): ValidatedInstrumentDescriptor {
        fun unavailable(reason: String): ValidatedInstrumentDescriptor = ValidatedInstrumentDescriptor(
            id = definition.id.ifBlank { "invalid-entry" }, name = definition.name.ifBlank { "Unavailable instrument" }, roles = emptySet(),
            engine = InstrumentEngineDescriptor(InstrumentEngineType.SFZ, "instruments.json"), enginePath = root.resolve("instruments.json"), samplePaths = emptyList(),
            license = SoundLibraryLicense("Unavailable", "Not available", "third-party", "unknown", commercialUse = false, attributionRequired = false, redistribution = "unknown"),
            licenseAdmission = LicenseAdmissionResult(LicenseAdmission.UNAVAILABLE, listOf(reason)),
            verifiedCapabilities = VerifiedInstrumentCapabilities(MidiPlayableRange(0, 0), 0, false, false, emptySet(), emptySet()),
            midiProgram = null, midiChannelZeroBased = null, noteMap = emptyMap()
        )
        return try {
            require(STABLE_ID.matches(definition.id)) { "Instrument stable ID is invalid" }
            requireSafeMetadata(definition.name, "Instrument '${definition.id}' name")
            require(definition.roles.isNotEmpty()) { "Instrument '${definition.id}' requires at least one role" }
            require(definition.productionApproved.not() || definition.qualityTier == InstrumentQualityTier.PRODUCTION) {
                "Production-approved instrument '${definition.id}' must have production quality"
            }
            require(definition.preferredRoles.all { it in definition.roles }) {
                "Instrument '${definition.id}' preferred roles must be supported roles"
            }
            require(definition.styleAffinity.all(STABLE_ID::matches)) {
                "Instrument '${definition.id}' style affinities must use stable IDs"
            }
            require(definition.midiProgram == null || definition.midiProgram in 0..127) { "Instrument '${definition.id}' MIDI program must be 0..127" }
            require(definition.midiChannel == null || definition.midiChannel in 1..16) { "Instrument '${definition.id}' MIDI channel must be one-based 1..16" }
            validateAffinities(definition.profileAffinities, "profile")
            validateAffinities(definition.moodAffinities, "mood")
            validateAffinities(definition.sectionAffinities, "section")
            validateProvenance(definition.library, definition.id)
            validateDeclaredCapabilities(definition.capabilities, definition.id)
            val normalizedTraits = normalizeTraits(definition)
            val enginePath = safeEngineAsset(root, root, realRoot, definition.engine.path, definition.engine.type, "Instrument '${definition.id}' engine")
            require(enginePath.fileName.toString().endsWith(definition.engine.type.extension, ignoreCase = true)) {
                "Instrument '${definition.id}' path must be a ${definition.engine.type.extension} file"
            }
            requireSafeMetadata(definition.category, "Instrument '${definition.id}' category")
            val regions = if (definition.engine.type == InstrumentEngineType.SFZ) {
                parseSfz(enginePath, root, realRoot, definition.id, supportedSampleRates)
            } else emptyList()
            val drums = ArrangementRole.DRUMS in definition.roles
            val noteMap = when {
                !drums -> definition.capabilities.noteMap
                definition.capabilities.noteMap.isNotEmpty() -> definition.capabilities.noteMap
                else -> standardDrumNoteMap.takeIf { map -> regions.isNotEmpty() && map.values.all { note -> regions.any { note in it.lowKey..it.highKey } } }.orEmpty()
            }
            if (drums) {
                require(definition.midiChannel != null) { "Drum instrument '${definition.id}' requires a MIDI channel" }
                require(noteMap.keys.containsAll(REQUIRED_DRUM_HITS)) {
                    "Drum instrument '${definition.id}' requires verified kick, snare, closedHat, and openHat mappings"
                }
            }
            val verified = if (regions.isEmpty()) definition.capabilities.unverifiedCapabilities()
            else regions.verifiedCapabilities(noteMap, drums, definition.capabilities)
            val license = definition.license.toLegacyLicense()
            ValidatedInstrumentDescriptor(
                id = definition.id, name = definition.name, category = definition.category, selectionMode = definition.selectionMode,
                productionApproved = definition.productionApproved, qualityTier = definition.qualityTier, styleAffinity = definition.styleAffinity.toSortedSet(),
                roles = definition.roles, preferredRoles = definition.preferredRoles, engine = definition.engine, enginePath = enginePath,
                samplePaths = regions.map { it.sample }.distinct(), license = license,
                licenseAdmission = definition.license.admissionResult(), verifiedCapabilities = verified,
                profileAffinities = definition.profileAffinities.toSortedMap(), moodAffinities = definition.moodAffinities.toSortedMap(),
                sectionAffinities = definition.sectionAffinities.toSortedMap(), attackTraits = normalizedTraits.attack,
                toneTraits = normalizedTraits.tone, articulationTraits = normalizedTraits.articulation,
                midiProgram = definition.midiProgram, midiChannelZeroBased = definition.midiChannel?.minus(1), noteMap = noteMap.toSortedMap(),
                sourceLibrary = definition.library
            )
        } catch (error: IllegalArgumentException) {
            unavailable(error.message ?: "Instrument '${definition.id}' is invalid")
        }
    }

    private data class NormalizedTraits(val attack: Set<SoundTrait>, val tone: Set<SoundTrait>, val articulation: Set<SoundTrait>)

    /** General MIDI pitches, admitted only after the parsed SFZ proves each is playable. */
    private val standardDrumNoteMap = mapOf("kick" to 36, "snare" to 38, "closedHat" to 42, "openHat" to 46)
    private val REQUIRED_DRUM_HITS = standardDrumNoteMap.keys
    private fun normalizeTraits(definition: InstrumentDefinition): NormalizedTraits {
        fun parse(values: Set<String>, allowed: Set<SoundTrait>, label: String): Set<SoundTrait> = values.map { raw ->
            val trait = SoundTrait.entries.firstOrNull { it.name.lowercase().replace('_', '-') == raw }
                ?: throw IllegalArgumentException("Instrument '${definition.id}' has unknown $label trait '$raw'")
            require(trait in allowed) { "Instrument '${definition.id}' has invalid $label trait '$raw'" }
            trait
        }.toSet().also { require(it.size == values.size) { "Instrument '${definition.id}' has duplicate $label traits" } }
        return NormalizedTraits(
            parse(definition.attackTraits, setOf(SoundTrait.SOFT, SoundTrait.HARD, SoundTrait.BRUSHED), "attack"),
            parse(definition.toneTraits, setOf(SoundTrait.WARM, SoundTrait.DARK, SoundTrait.BRIGHT, SoundTrait.MUTED, SoundTrait.AIRY), "tone"),
            parse(definition.articulationTraits, setOf(SoundTrait.SUSTAINED, SoundTrait.SHORT, SoundTrait.LEGATO, SoundTrait.STACCATO), "articulation")
        )
    }

    private fun validateAffinities(values: Map<String, Double>, kind: String) {
        require(values.keys.all(STABLE_ID::matches) && values.values.all { it.isFinite() && it in -1.0..1.0 }) {
            "Instrument $kind affinities must use stable IDs and weights from -1 to 1"
        }
    }
    private fun validateProvenance(value: SourceLibraryProvenance, id: String) {
        require(STABLE_ID.matches(value.id) && value.version.isNotBlank()) { "Instrument '$id' has invalid source-library provenance" }
        requireSafeMetadata(value.name, "Instrument '$id' source-library name"); requireSafeMetadata(value.source, "Instrument '$id' source-library source")
    }
    private fun validateDeclaredCapabilities(value: DeclaredInstrumentCapabilities, id: String) {
        value.playableRange?.let { require(it.low in 0..127 && it.high in it.low..127) { "Instrument '$id' has invalid playable range" } }
        value.velocityLayers?.let { require(it in 1..127) { "Instrument '$id' has invalid velocity-layer count" } }
        value.polyphony?.let { require(it in 1..512) { "Instrument '$id' has invalid polyphony" } }
        require(value.noteMap.keys.all(STABLE_ID::matches) && value.noteMap.values.all { it in 0..127 }) { "Instrument '$id' has invalid note map" }
    }

    /** SF2 and future plugin engines retain declared capability evidence until their renderer can verify it. */
    private fun DeclaredInstrumentCapabilities.unverifiedCapabilities() = VerifiedInstrumentCapabilities(
        playableRange = playableRange ?: MidiPlayableRange(0, 127), velocityLayers = velocityLayers ?: 0,
        roundRobin = roundRobin ?: false, releaseSamples = releaseSamples ?: false, performance = performance,
        declaredOnly = buildSet {
            if (playableRange != null) add("playableRange")
            if (velocityLayers != null) add("velocityLayers")
            if (roundRobin != null) add("roundRobin")
            if (releaseSamples != null) add("releaseSamples")
            if (polyphony != null) add("polyphony")
            if (articulations.isNotEmpty()) add("articulations")
            if (noteMap.isNotEmpty()) add("noteMap")
            if (performance.isNotEmpty()) add("performance")
        }
    )

    private fun validateMidi(entry: InstrumentRegistryEntry, name: String) {
        entry.midiProgram?.let { require(it in 0..127) { "Instrument '$name' MIDI program must be 0..127" } }
        entry.midiChannel?.let { require(it in 1..16) { "Instrument '$name' MIDI channel must be one-based 1..16" } }
        if (name == "drums") require(entry.midiChannel != null && entry.noteMap != null) { "Drums requires midiChannel and noteMap" }
        if (name != "drums") require(entry.midiChannel == null && entry.noteMap == null) { "Only drums may declare MIDI channel or noteMap" }
    }

    private fun validateLicenses(registry: LicenseRegistryFile, root: Path, realRoot: Path) {
        require(registry.version == 1) { "Unsupported license registry version: ${registry.version}" }
        registry.libraries.forEach { (id, license) ->
            require(id.isNotBlank()) { "License ID must not be blank" }
            require(license.displayName.isNotBlank() && license.source.isNotBlank()) { "License '$id' requires displayName and source" }
            require(license.provenance == "generated-original" || license.provenance == "third-party") { "License '$id' has invalid provenance" }
            require(license.license.isNotBlank()) { "License '$id' requires license or generated-original designation" }
            require(license.redistribution in setOf("allowed", "unknown", "prohibited")) { "License '$id' has invalid redistribution status" }
            if (license.attributionRequired) require(!license.attributionText.isNullOrBlank()) { "License '$id' requires attributionText" }
            license.licenseTextPath?.let { safeFile(root, root, realRoot, it, "License '$id' text") }
        }
    }

    /**
     * A deliberately bounded SFZ reader for local library validation.  sfizz
     * remains the playback authority; this reader resolves enough standard SFZ
     * syntax to prove that every referenced local asset is safe and present.
     */
    private fun parseSfz(sfz: Path, root: Path, realRoot: Path, name: String, supportedSampleRates: Set<Int>): List<SfzRegion> {
        data class State(
            val variables: MutableMap<String, String> = mutableMapOf(),
            var control: Map<String, String> = emptyMap(), var global: Map<String, String> = emptyMap(),
            var master: Map<String, String> = emptyMap(), var group: Map<String, String> = emptyMap()
        )
        val regions = mutableListOf<SfzRegion>()
        val active = ArrayDeque<Path>()
        val state = State()
        fun expand(value: String): String {
            var result = value
            repeat(16) {
                val changed = VARIABLE.replace(result) { match -> state.variables[match.groupValues[1]] ?: match.value }
                if (changed == result) return result
                result = changed
            }
            throw IllegalArgumentException("Instrument '$name' SFZ variable expansion is recursive")
        }
        fun values(body: String): Map<String, String> = TOKEN.findAll(body).associate { token ->
            token.groupValues[1] to expand(token.groupValues[2].trim().trim('"'))
        }
        fun parse(file: Path, sampleBase: Path) {
            require(active.size < MAX_SFZ_INCLUDE_DEPTH) { "Instrument '$name' SFZ include nesting is too deep" }
            require(!active.contains(file)) { "Instrument '$name' SFZ has a cyclic include: $file" }
            active.addLast(file)
            try {
                val contents = Files.readAllLines(file, StandardCharsets.UTF_8).joinToString("\n") { it.substringBefore("//") }
                val events = SFZ_EVENT.findAll(contents).toList()
                events.forEachIndexed { index, event ->
                    val header = event.groupValues[1].trim()
                    val body = contents.substring(event.range.last + 1, events.getOrNull(index + 1)?.range?.first ?: contents.length)
                    when {
                        header.startsWith("#include") -> {
                            val reference = header.substringAfter('"', "").substringBeforeLast('"', "")
                            require(reference.isNotBlank()) { "Instrument '$name' has an invalid SFZ include in $file" }
                            val expanded = expand(reference)
                            val current = file.parent.resolve(expanded.replace('\\', '/')).normalize()
                            val includeBase = if (Files.isRegularFile(current)) file.parent else sampleBase
                            parse(safeAssetFile(includeBase, root, realRoot, expanded, "Instrument '$name' SFZ include"), sampleBase)
                        }
                        header.startsWith("#define") -> {
                            val parts = header.removePrefix("#define").trim().split(Regex("\\s+"), limit = 2)
                            require(parts.size == 2 && parts[0].startsWith('$')) { "Instrument '$name' has an invalid SFZ define in $file" }
                            state.variables[parts[0].removePrefix("$")] = expand(parts[1].trim('"'))
                        }
                        else -> {
                            val tag = header.removePrefix("<").removeSuffix(">").lowercase()
                            val current = values(body)
                            when (tag) {
                                "control" -> state.control = current
                                "global" -> { state.global = current; state.master = emptyMap(); state.group = emptyMap() }
                                "master" -> { state.master = current; state.group = emptyMap() }
                                "group" -> state.group = current
                                "region" -> {
                                    val effective = state.control + state.global + state.master + state.group + current
                                    val sampleRef = effective["sample"] ?: throw IllegalArgumentException("Instrument '$name' SFZ has a region without sample=: $file")
                                    if (sampleRef != "*silence") {
                                        val key = effective["key"]?.toIntOrNull()
                                        val lowKey = key ?: effective["lokey"]?.toIntOrNull() ?: 0
                                        val highKey = key ?: effective["hikey"]?.toIntOrNull() ?: 127
                                        if (lowKey !in 0..127 || highKey !in lowKey..127) return@forEachIndexed
                                        val defaultPath = effective["default_path"].orEmpty()
                                        require(!defaultPath.contains('$')) { "Instrument '$name' has an unresolved default_path variable: $file" }
                                        val sample = safeAssetFile(sampleBase, root, realRoot, defaultPath + sampleRef.replace('\\', '/'), "Instrument '$name' SFZ sample")
                                        validateSample(sample, name, supportedSampleRates)
                                        regions += SfzRegion(
                                            lowKey, highKey, sample, effective["lovel"]?.toIntOrNull() ?: 0,
                                            effective["hivel"]?.toIntOrNull() ?: 127,
                                            (effective["seq_length"]?.toIntOrNull() ?: 1) > 1 || effective.containsKey("lorand"),
                                            effective["trigger"] in setOf("release", "release_key")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } finally { active.removeLast() }
        }
        parse(sfz, sfz.parent)
        require(regions.isNotEmpty()) { "Instrument '$name' SFZ contains no playable <region> sample definitions" }
        return regions
    }

    private fun validateDrumMap(map: Map<String, Int>?, regions: List<SfzRegion>) {
        val required = mapOf("kick" to "kick.wav", "snare" to "snare.wav", "clap" to "clap.wav", "closedHat" to "hat_closed.wav", "openHat" to "hat_open.wav")
        require(map?.keys == required.keys) { "Drum noteMap must contain exactly: ${required.keys.joinToString()}" }
        required.forEach { (name, file) ->
            val note = requireNotNull(map)[name]
            require(note in 0..127) { "Drum noteMap '$name' must be 0..127" }
            require(regions.any { note in it.lowKey..it.highKey && it.sample.fileName.toString() == file }) { "Drum noteMap '$name'=$note disagrees with drums.sfz" }
        }
    }

    private fun List<SfzRegion>.verifiedCapabilities(
        noteMap: Map<String, Int>,
        drums: Boolean,
        declared: DeclaredInstrumentCapabilities = DeclaredInstrumentCapabilities()
    ): VerifiedInstrumentCapabilities {
        val verifiedPerformance = buildSet {
            if (drums) add(PerformanceCapability.PERCUSSIVE) else add(PerformanceCapability.PITCHED)
            if (!drums) add(PerformanceCapability.POLYPHONIC)
            if (this@verifiedCapabilities.any { region -> region.release }) add(PerformanceCapability.SUSTAIN)
            if (!drums && this@verifiedCapabilities.maxOf { region -> region.highKey } - this@verifiedCapabilities.minOf { region -> region.lowKey } >= 12) add(PerformanceCapability.COUNTER_MELODY)
        }
        val range = MidiPlayableRange(minOf { it.lowKey }, maxOf { it.highKey })
        val layers = map { it.lowVelocity to it.highVelocity }.distinct().size
        val declaredOnly = buildSet {
            declared.playableRange?.takeIf { it != range }?.let { add("playableRange") }
            declared.velocityLayers?.takeIf { it != layers }?.let { add("velocityLayers") }
            declared.roundRobin?.takeIf { it != this@verifiedCapabilities.any { region -> region.roundRobin } }?.let { add("roundRobin") }
            declared.releaseSamples?.takeIf { it != this@verifiedCapabilities.any { region -> region.release } }?.let { add("releaseSamples") }
            declared.performance.filterNot { it in verifiedPerformance }.forEach { add("performance:${it.name.lowercase()}") }
            if (noteMap.isNotEmpty() && noteMap.values.any { note -> this@verifiedCapabilities.none { region -> note in region.lowKey..region.highKey } }) add("noteMap")
        }
        return VerifiedInstrumentCapabilities(range, layers, any { it.roundRobin }, any { it.release }, verifiedPerformance, declaredOnly)
    }

    private fun validateSample(path: Path, instrument: String, supportedSampleRates: Set<Int>) {
        if (!validatedSamples.add(path)) return
        when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
            "wav", "wave" -> validateWav(path, instrument, supportedSampleRates)
            "flac" -> validateFlac(path, instrument, supportedSampleRates)
            "ogg" -> require(Files.newInputStream(path).use { it.readNBytes(4).decodeToString() } == "OggS") { "Instrument '$instrument' sample is not Ogg: $path" }
            "mp3" -> require(Files.newInputStream(path).use { it.readNBytes(2) }.let { it.size == 2 && (it.decodeToString().startsWith("ID") || (it[0].toInt() and 0xff) == 0xff) }) { "Instrument '$instrument' sample is not MP3: $path" }
            else -> throw IllegalArgumentException("Instrument '$instrument' sample has unsupported format: $path")
        }
    }

    private fun validateWav(path: Path, instrument: String, supportedSampleRates: Set<Int>) {
        var format: Int? = null; var channels: Int? = null; var rate: Int? = null
        var byteRate: Int? = null; var blockAlign: Int? = null; var bitsPerSample: Int? = null; var dataSize: Int? = null
        val size = Files.size(path)
        Files.newInputStream(path).use { input ->
            val header = input.readNBytes(12)
            require(header.size == 12 && header.copyOfRange(0, 4).decodeToString() == "RIFF" && header.copyOfRange(8, 12).decodeToString() == "WAVE") {
                "Instrument '$instrument' sample is not a RIFF/WAVE file: $path"
            }
            var offset = 12L
            while (offset + 8 <= size) {
                val chunk = input.readNBytes(8)
                require(chunk.size == 8) { "Instrument '$instrument' sample has malformed WAV chunk: $path" }
                val id = chunk.copyOfRange(0, 4).decodeToString()
                val chunkSize = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                require(chunkSize >= 0 && offset + 8L + chunkSize <= size) { "Instrument '$instrument' sample has malformed WAV chunk: $path" }
                if (id == "fmt ") {
                    require(chunkSize >= 16) { "Instrument '$instrument' sample has short fmt chunk: $path" }
                    val fmtBytes = input.readNBytes(chunkSize)
                    require(fmtBytes.size == chunkSize) { "Instrument '$instrument' sample has malformed WAV chunk: $path" }
                    val fmt = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                    format = fmt.short.toInt() and 0xffff
                    channels = fmt.short.toInt() and 0xffff
                    rate = fmt.int
                    byteRate = fmt.int
                    blockAlign = fmt.short.toInt() and 0xffff
                    bitsPerSample = fmt.short.toInt() and 0xffff
                } else {
                    input.skipNBytes(chunkSize.toLong())
                    if (id == "data") dataSize = chunkSize
                }
                // Some otherwise-valid vendor WAVs omit the final RIFF padding byte.
                if (chunkSize and 1 == 1 && offset + 8L + chunkSize < size) input.skipNBytes(1)
                offset += 8L + chunkSize + (chunkSize and 1)
                if (id == "data") break
            }
        }
        if (format == 0xfffe) {
            val extensible = Files.newInputStream(path).use { it.readNBytes(60) }
            format = if (extensible.size >= 46) ByteBuffer.wrap(extensible, 44, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff else null
        }
        require(format in setOf(1, 3)) { "Instrument '$instrument' sample must use PCM or IEEE-float encoding: $path" }
        require(channels in 1..32 && rate != null && rate!! > 0 && blockAlign != null && blockAlign!! > 0) { "Instrument '$instrument' sample has invalid WAV format: $path" }
        require(bitsPerSample in if (format == 1) setOf(8, 16, 24, 32) else setOf(32, 64)) { "Instrument '$instrument' sample has unsupported WAV bit depth: $path" }
        val expectedBlockAlign = channels!! * (bitsPerSample!! / 8)
        require(blockAlign == expectedBlockAlign && byteRate == rate!! * expectedBlockAlign) { "Instrument '$instrument' sample has inconsistent PCM frame layout: $path" }
        require(dataSize != null && dataSize!! > 0) { "Instrument '$instrument' sample has no complete frames: $path" }
        require(rate in supportedSampleRates) { "Instrument '$instrument' sample rate $rate is not supported by this registry: $path" }
    }

    private fun validateFlac(path: Path, instrument: String, supportedSampleRates: Set<Int>) {
        val bytes = Files.newInputStream(path).use { it.readNBytes(42) }
        require(bytes.size >= 42 && bytes.copyOfRange(0, 4).decodeToString() == "fLaC") { "Instrument '$instrument' sample is not FLAC: $path" }
        val firstBlockType = bytes[4].toInt() and 0x7f
        val firstBlockSize = ((bytes[5].toInt() and 0xff) shl 16) or ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
        require(firstBlockType == 0 && firstBlockSize == 34) { "Instrument '$instrument' FLAC lacks STREAMINFO: $path" }
        val offset = 8
        val rate = ((bytes[offset + 10].toInt() and 0xff) shl 12) or ((bytes[offset + 11].toInt() and 0xff) shl 4) or ((bytes[offset + 12].toInt() and 0xff) ushr 4)
        val channels = ((bytes[offset + 12].toInt() ushr 1) and 0x7) + 1
        val bits = (((bytes[offset + 12].toInt() and 0x1) shl 4) or ((bytes[offset + 13].toInt() and 0xff) ushr 4)) + 1
        require(rate in supportedSampleRates && channels in 1..8 && bits in 4..32) { "Instrument '$instrument' FLAC has unsupported stream metadata: $path" }
    }

    /** JSON maps normally discard duplicate keys, so reject them before a registry can look valid by accident. */
    private fun validateNoDuplicateInstrumentKeys(contents: String) {
        JsonFactory().createParser(contents).use { parser ->
            while (parser.nextToken() != null) {
                if (parser.currentToken != JsonToken.FIELD_NAME || parser.currentName() != "instruments") continue
                require(parser.nextToken() == JsonToken.START_OBJECT) { "Instrument registry instruments must be an object" }
                val names = mutableSetOf<String>()
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    val name = parser.currentName()
                    require(names.add(name)) { "Instrument registry contains duplicate logical instrument name '$name'" }
                    parser.nextToken()
                    parser.skipChildren()
                }
                return
            }
        }
    }

    private fun safeFile(base: Path, libraryRoot: Path, realRoot: Path, reference: String, label: String): Path {
        val relative = try { Path.of(reference) } catch (_: Exception) { throw IllegalArgumentException("$label path is invalid: $reference") }
        require(reference.isNotBlank() && !relative.isAbsolute && !reference.split('/', '\\').contains("..")) { "$label path must be relative and must not traverse: $reference" }
        val resolved = base.resolve(relative).normalize()
        require(resolved.startsWith(libraryRoot)) { "$label path escapes the sound library: $reference" }
        require(Files.isRegularFile(resolved)) { "$label file does not exist: $reference" }
        require(resolved.toRealPath().startsWith(realRoot)) { "$label path escapes the sound library through a symlink: $reference" }
        return resolved
    }

    /** Engine bundles (VST3/Audio Unit) are directories; sampler assets remain regular files. */
    private fun safeEngineAsset(base: Path, libraryRoot: Path, realRoot: Path, reference: String, type: InstrumentEngineType, label: String): Path {
        val relative = try { Path.of(reference) } catch (_: Exception) { throw IllegalArgumentException("$label path is invalid: $reference") }
        require(reference.isNotBlank() && !relative.isAbsolute && !reference.split('/', '\\').contains("..")) { "$label path must be relative and must not traverse: $reference" }
        val resolved = base.resolve(relative).normalize()
        require(resolved.startsWith(libraryRoot)) { "$label path escapes the sound library: $reference" }
        val validType = if (type in setOf(InstrumentEngineType.SFZ, InstrumentEngineType.SF2)) Files.isRegularFile(resolved) else Files.isDirectory(resolved)
        require(validType) { "$label ${if (type in setOf(InstrumentEngineType.SFZ, InstrumentEngineType.SF2)) "file" else "bundle"} does not exist: $reference" }
        require(resolved.toRealPath().startsWith(realRoot)) { "$label path escapes the sound library through a symlink: $reference" }
        return resolved
    }

    /** SFZ sample/include references may legitimately use ../ within a vendor pack. */
    private fun safeAssetFile(base: Path, libraryRoot: Path, realRoot: Path, reference: String, label: String): Path {
        val normalizedReference = reference.replace('\\', '/')
        val relative = try { Path.of(normalizedReference) } catch (_: Exception) { throw IllegalArgumentException("$label path is invalid: $reference") }
        require(reference.isNotBlank() && !relative.isAbsolute) { "$label path must be relative: $reference" }
        val resolved = base.resolve(relative).normalize()
        require(resolved.startsWith(libraryRoot) && Files.isRegularFile(resolved)) { "$label file does not exist inside the sound library: $reference" }
        require(resolved.toRealPath().startsWith(realRoot)) { "$label path escapes the sound library through a symlink: $reference" }
        return resolved
    }

    private data class SfzRegion(
        val lowKey: Int,
        val highKey: Int,
        val sample: Path,
        val lowVelocity: Int,
        val highVelocity: Int,
        val roundRobin: Boolean,
        val release: Boolean
    )
    private companion object {
        val json = Json { ignoreUnknownKeys = false }
        val TOKEN = Regex("([A-Za-z_][A-Za-z0-9_]*)=(.*?)(?=\\s+[A-Za-z_][A-Za-z0-9_]*=|$)")
        val SFZ_EVENT = Regex("(?m)^\\s*(#(?:include|define)\\b[^\\n]*|<[A-Za-z]+>)")
        val VARIABLE = Regex("\\$([A-Za-z_][A-Za-z0-9_]*)")
        val STABLE_ID = Regex("[a-z][a-z0-9-]{0,47}")
        const val MAX_SFZ_INCLUDE_DEPTH = 64
    }
}

private fun requireSafeMetadata(value: String, label: String) {
    require(value.isNotBlank() && value.length <= 240 && !value.contains('/') && !value.contains('\\') && !value.contains(".sfz", true) && !value.contains(".wav", true)) {
        "$label is invalid"
    }
}

private fun InstrumentLicenseMetadata.toLegacyLicense(): SoundLibraryLicense {
    require(STABLE_LICENSE_ID.matches(id)) { "License ID is invalid" }
    requireSafeMetadata(sourceName, "License '$id' source name")
    require(sourceUrl == null || SAFE_URL.matches(sourceUrl)) { "License '$id' source URL is invalid" }
    require(licenseUrl == null || SAFE_URL.matches(licenseUrl)) { "License '$id' URL is invalid" }
    require(licenseText == null || licenseText.isNotBlank()) { "License '$id' text evidence is invalid" }
    if (attributionRequired) require(!attributionText.isNullOrBlank()) { "License '$id' requires ready-to-publish attribution" }
    return SoundLibraryLicense(
        displayName = sourceName, source = sourceUrl ?: sourceName, provenance = "third-party", license = id,
        commercialUse = commercialUse, attributionRequired = attributionRequired, attributionText = attributionText,
        redistribution = "unknown", date = policyReview, notes = "policy=${policyVersion ?: "unversioned"}; evidence=${licenseUrl ?: "embedded"}"
    )
}

private fun InstrumentLicenseMetadata.admissionResult(): LicenseAdmissionResult {
    // Text wins over a permissive boolean: NC and contradictory terms never become selectable.
    val normalized = id.uppercase().replace('_', '-')
    if (normalized.contains("-NC") || normalized.contains("NON-COMMERCIAL")) return LicenseAdmissionResult(LicenseAdmission.UNAVAILABLE, listOf("Non-commercial license is not admitted"))
    if (!commercialUse) return LicenseAdmissionResult(LicenseAdmission.UNAVAILABLE, listOf("Commercial use is not recorded as permitted"))
    return when {
        normalized == "CC0" || normalized == "CC0-1.0" || normalized == "OWNED" || normalized == "GENERATED-ORIGINAL" ->
            if (attributionRequired) LicenseAdmissionResult(LicenseAdmission.UNAVAILABLE, listOf("No-attribution license conflicts with required attribution"))
            else LicenseAdmissionResult(LicenseAdmission.ADMITTED, emptyList())
        normalized.startsWith("CC-BY") ->
            if (attributionRequired && !attributionText.isNullOrBlank()) LicenseAdmissionResult(LicenseAdmission.ADMITTED, emptyList())
            else LicenseAdmissionResult(LicenseAdmission.UNAVAILABLE, listOf("CC BY requires ready-to-publish attribution"))
        !policyReview.isNullOrBlank() && !policyVersion.isNullOrBlank() -> LicenseAdmissionResult(LicenseAdmission.ADMITTED, emptyList())
        else -> LicenseAdmissionResult(LicenseAdmission.UNAVAILABLE, listOf("Unknown or custom license requires explicit policy review"))
    }
}

private fun SoundLibraryLicense.admissionResult(): LicenseAdmissionResult = InstrumentLicenseMetadata(
    id = license, commercialUse = commercialUse, attributionRequired = attributionRequired, attributionText = attributionText,
    sourceName = displayName, policyReview = date, policyVersion = "registry-v1"
).admissionResult()

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

private val STABLE_LICENSE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")
private val SAFE_URL = Regex("https?://[^\\s]{1,500}")
