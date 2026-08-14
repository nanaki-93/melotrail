package ai.music.workstation.arrangement

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

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

data class ValidatedInstrumentDescriptor(
    val instrument: LogicalInstrument,
    val sfzPath: Path,
    val samplePaths: List<Path>,
    val license: SoundLibraryLicense,
    val midiProgram: Int?,
    /** MIDI API channel (zero based); the JSON value is explicitly one based. */
    val midiChannelZeroBased: Int?,
    val noteMap: Map<String, Int>
)

class ValidatedInstrumentRegistry internal constructor(private val descriptors: Map<LogicalInstrument, ValidatedInstrumentDescriptor>) {
    fun logicalNames(): Set<String> = descriptors.keys.map { it.wireName }.toSet()
    fun plannerNames(): List<String> = descriptors.keys.map { it.wireName }.sorted()
    fun resolve(name: String): ValidatedInstrumentDescriptor = descriptors[LogicalInstrument.parse(name)]
        ?: throw IllegalArgumentException("Instrument is not validated: $name")
}

/** Local-only registry loader. Accepts a validated [SoundLibraryLocation.Success] from [SoundLibraryLocator]. */
class InstrumentRegistryLoader(val libraryRoot: Path) {
    fun load(): ValidatedInstrumentRegistry {
        val root = libraryRoot.toAbsolutePath().normalize()
        require(Files.isDirectory(root)) { "Sound library root does not exist: $root. See sounds/README.md for the local asset setup." }
        val realRoot = root.toRealPath()
        val registryPath = safeFile(root, root, realRoot, "instruments.json", "Instrument registry")
        val registryContents = Files.readString(registryPath, StandardCharsets.UTF_8)
        val registry = json.decodeFromString<InstrumentRegistryFile>(registryContents)
        val licenses = json.decodeFromString<LicenseRegistryFile>(Files.readString(safeFile(root, root, realRoot, "LICENSES.json", "License registry"), StandardCharsets.UTF_8))
        require(registry.version == 1) { "Unsupported instrument registry version: ${registry.version}" }
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
            logical to ValidatedInstrumentDescriptor(
                logical, sfz, regions.map { it.sample }, license, entry.midiProgram,
                entry.midiChannel?.minus(1), entry.noteMap ?: emptyMap()
            )
        }.toMap()
        return ValidatedInstrumentRegistry(descriptors)
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
            SfzRegion(key, sample)
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

    private data class SfzRegion(val key: Int, val sample: Path)
    private companion object {
        val json = Json { ignoreUnknownKeys = false }
        val TOKEN = Regex("([A-Za-z_]+)=([^\\s]+)")
    }
}
