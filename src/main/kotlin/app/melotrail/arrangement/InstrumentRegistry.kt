package app.melotrail.arrangement

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
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

@Serializable
data class InstrumentDefinition(
    val id: String,
    val name: String,
    val roles: Set<ArrangementRole>,
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

@Serializable data class InstrumentEngineDescriptor(val type: String, val path: String)
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
    val roles: Set<ArrangementRole>,
    val sfzPath: Path,
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
)

class ValidatedInstrumentRegistry internal constructor(
    private val descriptors: Map<String, ValidatedInstrumentDescriptor>,
    val version: Int,
    val registrySha256: String
) {
    fun logicalNames(): Set<String> = descriptors.keys
    fun plannerNames(): List<String> = descriptors.keys.sorted()
    fun resolve(name: String): ValidatedInstrumentDescriptor = descriptors[name]
        ?: throw IllegalArgumentException("Instrument is not validated: $name")
    /** Immutable validated descriptors for evidence export; callers never receive registry paths as outputs. */
    fun all(): List<ValidatedInstrumentDescriptor> = descriptors.values.sortedBy { it.id }
    fun availableFor(role: ArrangementRole): List<ValidatedInstrumentDescriptor> = all().filter { role in it.roles && it.licenseAdmission.admission == LicenseAdmission.ADMITTED }
    fun hasRoleCoverage(required: Set<ArrangementRole>): Boolean = required.all { role -> availableFor(role).isNotEmpty() }
}

/** Local-only registry loader. Accepts a validated [SoundLibraryLocation.Success] from [SoundLibraryLocator]. */
class InstrumentRegistryLoader(val libraryRoot: Path) {
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
            val regions = parseSfz(sfz, root, realRoot, name, registry.workingSampleRate)
            if (logical == LogicalInstrument.DRUMS) validateDrumMap(entry.noteMap, regions)
            logical.wireName to ValidatedInstrumentDescriptor(
                id = logical.wireName, name = logical.wireName.replaceFirstChar(Char::uppercase), roles = setOf(LegacyLogicalInstrumentRoles.roleFor(logical.wireName)),
                sfzPath = sfz, samplePaths = regions.map { it.sample }, license = license,
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
            definition.id to validateV2Entry(root, realRoot, registry.workingSampleRate, definition)
        }
        return ValidatedInstrumentRegistry(descriptors, 2, sha256(registryContents))
    }

    private fun validateV2Entry(root: Path, realRoot: Path, workingSampleRate: Int, definition: InstrumentDefinition): ValidatedInstrumentDescriptor {
        fun unavailable(reason: String): ValidatedInstrumentDescriptor = ValidatedInstrumentDescriptor(
            id = definition.id.ifBlank { "invalid-entry" }, name = definition.name.ifBlank { "Unavailable instrument" }, roles = emptySet(),
            sfzPath = root.resolve("instruments.json"), samplePaths = emptyList(),
            license = SoundLibraryLicense("Unavailable", "Not available", "third-party", "unknown", commercialUse = false, attributionRequired = false, redistribution = "unknown"),
            licenseAdmission = LicenseAdmissionResult(LicenseAdmission.UNAVAILABLE, listOf(reason)),
            verifiedCapabilities = VerifiedInstrumentCapabilities(MidiPlayableRange(0, 0), 0, false, false, emptySet(), emptySet()),
            midiProgram = null, midiChannelZeroBased = null, noteMap = emptyMap()
        )
        return try {
            require(STABLE_ID.matches(definition.id)) { "Instrument stable ID is invalid" }
            requireSafeMetadata(definition.name, "Instrument '${definition.id}' name")
            require(definition.roles.isNotEmpty()) { "Instrument '${definition.id}' requires at least one role" }
            require(definition.engine.type == "sfz") { "Instrument '${definition.id}' uses unsupported engine '${definition.engine.type}'" }
            require(definition.midiProgram == null || definition.midiProgram in 0..127) { "Instrument '${definition.id}' MIDI program must be 0..127" }
            require(definition.midiChannel == null || definition.midiChannel in 1..16) { "Instrument '${definition.id}' MIDI channel must be one-based 1..16" }
            validateAffinities(definition.profileAffinities, "profile")
            validateAffinities(definition.moodAffinities, "mood")
            validateAffinities(definition.sectionAffinities, "section")
            validateProvenance(definition.library, definition.id)
            validateDeclaredCapabilities(definition.capabilities, definition.id)
            val normalizedTraits = normalizeTraits(definition)
            val sfz = safeFile(root, root, realRoot, definition.engine.path, "Instrument '${definition.id}' SFZ")
            require(sfz.fileName.toString().endsWith(".sfz", ignoreCase = true)) { "Instrument '${definition.id}' path must be an .sfz file" }
            val regions = parseSfz(sfz, root, realRoot, definition.id, workingSampleRate)
            val verified = regions.verifiedCapabilities(definition.capabilities.noteMap, ArrangementRole.DRUMS in definition.roles, definition.capabilities)
            val license = definition.license.toLegacyLicense()
            ValidatedInstrumentDescriptor(
                id = definition.id, name = definition.name, roles = definition.roles, sfzPath = sfz,
                samplePaths = regions.map { it.sample }.distinct(), license = license,
                licenseAdmission = definition.license.admissionResult(), verifiedCapabilities = verified,
                profileAffinities = definition.profileAffinities.toSortedMap(), moodAffinities = definition.moodAffinities.toSortedMap(),
                sectionAffinities = definition.sectionAffinities.toSortedMap(), attackTraits = normalizedTraits.attack,
                toneTraits = normalizedTraits.tone, articulationTraits = normalizedTraits.articulation,
                midiProgram = definition.midiProgram, midiChannelZeroBased = definition.midiChannel?.minus(1), noteMap = definition.capabilities.noteMap.toSortedMap(),
                sourceLibrary = definition.library
            )
        } catch (error: IllegalArgumentException) {
            unavailable(error.message ?: "Instrument '${definition.id}' is invalid")
        }
    }

    private data class NormalizedTraits(val attack: Set<SoundTrait>, val tone: Set<SoundTrait>, val articulation: Set<SoundTrait>)
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

    private fun parseSfz(sfz: Path, root: Path, realRoot: Path, name: String, workingSampleRate: Int): List<SfzRegion> {
        val regions = Files.readAllLines(sfz, StandardCharsets.UTF_8).mapNotNull { raw ->
            val line = raw.substringBefore("//").trim()
            if (!line.contains("<region>")) return@mapNotNull null
            val values = TOKEN.findAll(line.substringAfter("<region>")).associate { it.groupValues[1] to it.groupValues[2].trim('"') }
            val sampleRef = values["sample"] ?: throw IllegalArgumentException("Instrument '$name' SFZ has a region without sample=: $sfz")
            val key = values["key"]?.toIntOrNull() ?: throw IllegalArgumentException("Instrument '$name' SFZ has a region without valid key=: $sfz")
            require(key in 0..127) { "Instrument '$name' SFZ key must be 0..127: $key" }
            val sample = safeFile(sfz.parent, root, realRoot, sampleRef, "Instrument '$name' SFZ sample")
            validateWav(sample, name, workingSampleRate)
            SfzRegion(
                key = key,
                sample = sample,
                lowVelocity = values["lovel"]?.toIntOrNull() ?: 0,
                highVelocity = values["hivel"]?.toIntOrNull() ?: 127,
                roundRobin = (values["seq_length"]?.toIntOrNull() ?: 1) > 1,
                release = values["trigger"] == "release"
            ).also {
                require(it.lowVelocity in 0..127 && it.highVelocity in it.lowVelocity..127) {
                    "Instrument '$name' SFZ has invalid velocity range"
                }
            }
        }
        require(regions.isNotEmpty()) { "Instrument '$name' SFZ contains no <region> sample definitions" }
        return regions
    }

    private fun validateDrumMap(map: Map<String, Int>?, regions: List<SfzRegion>) {
        val required = mapOf("kick" to "kick.wav", "snare" to "snare.wav", "clap" to "clap.wav", "closedHat" to "hat_closed.wav", "openHat" to "hat_open.wav")
        require(map?.keys == required.keys) { "Drum noteMap must contain exactly: ${required.keys.joinToString()}" }
        required.forEach { (name, file) ->
            val note = requireNotNull(map)[name]
            require(note in 0..127) { "Drum noteMap '$name' must be 0..127" }
            require(regions.any { it.key == note && it.sample.fileName.toString() == file }) { "Drum noteMap '$name'=$note disagrees with drums.sfz" }
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
            if (!drums && this@verifiedCapabilities.maxOf { region -> region.key } - this@verifiedCapabilities.minOf { region -> region.key } >= 12) add(PerformanceCapability.COUNTER_MELODY)
        }
        val range = MidiPlayableRange(minOf { it.key }, maxOf { it.key })
        val layers = map { it.lowVelocity to it.highVelocity }.distinct().size
        val declaredOnly = buildSet {
            declared.playableRange?.takeIf { it != range }?.let { add("playableRange") }
            declared.velocityLayers?.takeIf { it != layers }?.let { add("velocityLayers") }
            declared.roundRobin?.takeIf { it != this@verifiedCapabilities.any { region -> region.roundRobin } }?.let { add("roundRobin") }
            declared.releaseSamples?.takeIf { it != this@verifiedCapabilities.any { region -> region.release } }?.let { add("releaseSamples") }
            declared.performance.filterNot { it in verifiedPerformance }.forEach { add("performance:${it.name.lowercase()}") }
            if (noteMap.isNotEmpty() && noteMap.values.any { note -> this@verifiedCapabilities.none { region -> region.key == note } }) add("noteMap")
        }
        return VerifiedInstrumentCapabilities(range, layers, any { it.roundRobin }, any { it.release }, verifiedPerformance, declaredOnly)
    }

    private fun validateWav(path: Path, instrument: String, workingSampleRate: Int) {
        val bytes = Files.readAllBytes(path)
        require(bytes.size >= 44 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" && bytes.copyOfRange(8, 12).decodeToString() == "WAVE") {
            "Instrument '$instrument' sample is not a RIFF/WAVE file: $path"
        }
        var offset = 12
        var format: Int? = null; var channels: Int? = null; var rate: Int? = null
        var byteRate: Int? = null; var blockAlign: Int? = null; var bitsPerSample: Int? = null; var dataSize: Int? = null
        while (offset + 8 <= bytes.size) {
            val id = bytes.copyOfRange(offset, offset + 4).decodeToString()
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            require(size >= 0 && offset + 8L + size <= bytes.size.toLong()) { "Instrument '$instrument' sample has malformed WAV chunk: $path" }
            if (id == "fmt ") {
                require(size >= 16) { "Instrument '$instrument' sample has short fmt chunk: $path" }
                val fmt = ByteBuffer.wrap(bytes, offset + 8, size).order(ByteOrder.LITTLE_ENDIAN)
                format = fmt.short.toInt() and 0xffff
                channels = fmt.short.toInt() and 0xffff
                rate = fmt.int
                byteRate = fmt.int
                blockAlign = fmt.short.toInt() and 0xffff
                bitsPerSample = fmt.short.toInt() and 0xffff
            } else if (id == "data") dataSize = size
            offset += 8 + size + (size and 1)
        }
        require(format == 1) { "Instrument '$instrument' sample must use PCM encoding: $path" }
        require(channels in 1..32 && rate != null && rate!! > 0 && blockAlign != null && blockAlign!! > 0) { "Instrument '$instrument' sample has invalid WAV format: $path" }
        require(bitsPerSample in setOf(8, 16, 24, 32)) { "Instrument '$instrument' sample has unsupported PCM bit depth: $path" }
        val expectedBlockAlign = channels!! * (bitsPerSample!! / 8)
        require(blockAlign == expectedBlockAlign && byteRate == rate!! * expectedBlockAlign) { "Instrument '$instrument' sample has inconsistent PCM frame layout: $path" }
        require(dataSize != null && dataSize!! > 0 && dataSize!! % blockAlign!! == 0) { "Instrument '$instrument' sample has no complete frames: $path" }
        // Integer PCM has no NaN or infinite sample values. Floating-point WAV is rejected above.
        require(rate == workingSampleRate) { "Instrument '$instrument' sample rate $rate does not match registry workingSampleRate $workingSampleRate: $path" }
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

    private data class SfzRegion(
        val key: Int,
        val sample: Path,
        val lowVelocity: Int,
        val highVelocity: Int,
        val roundRobin: Boolean,
        val release: Boolean
    )
    private companion object {
        val json = Json { ignoreUnknownKeys = false }
        val TOKEN = Regex("([A-Za-z_]+)=([^\\s]+)")
        val STABLE_ID = Regex("[a-z][a-z0-9-]{0,47}")
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
