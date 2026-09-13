package app.melotrail.video.adapter

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Setup-only contract for a local video candidate. It does not run or select a backend. */
object LocalVideoProfileBoundary {
    const val PROFILE_SCHEMA = "melotrail-local-video-profile"
    const val REQUEST_SCHEMA = "melotrail-local-video-probe-request"
    const val SCHEMA_VERSION = 1
    const val SUPPORTED_BACKEND_ID = "draw-things-cli"
    const val MAX_VIDEO_PROFILES = 2
    const val MAX_TAKES_PER_PROFILE = 3
    private const val MAX_PROMPT_CHARACTERS = 20_000

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        // Null measurement/selection fields are material: NOT_RUN reports must show them explicitly.
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun loadBundledProfile(): LocalVideoProfileDocument {
        val text = checkNotNull(LocalVideoProfileBoundary::class.java.getResource("/video/local-profile.json")) {
            "Bundled local video profile is missing: /video/local-profile.json"
        }.readText()
        return decodeProfile(text)
    }

    fun decodeProfile(text: String): LocalVideoProfileDocument = decode("local video profile") {
        json.decodeFromString<LocalVideoProfileDocument>(text).also(::validateProfile)
    }

    fun decodeRequest(text: String): LocalVideoProbeRequest = decode("local video probe request") {
        json.decodeFromString<LocalVideoProbeRequest>(text).also(::validateRequestShape)
    }

    /**
     * Reads and hashes pinned inputs, and validates unused output names. It deliberately performs no
     * process launch, network access, directory creation, or output write.
     */
    fun prepare(profile: LocalVideoProfileDocument, request: LocalVideoProbeRequest): LocalVideoProbePreparationReport {
        validateProfile(profile)
        validateRequestShape(request)
        require(request.profileId == profile.id) {
            "Probe profileId '${request.profileId}' does not match bundled profile '${profile.id}'"
        }
        require(request.backendId == profile.backend.id) {
            "Probe backendId '${request.backendId}' does not match profile backend '${profile.backend.id}'"
        }
        require(request.tool.id == request.backendId) {
            "Tool pin id '${request.tool.id}' must match backendId '${request.backendId}'"
        }
        require(request.profiles.size <= profile.limits.maxVideoProfiles &&
            request.profiles.all { it.takes.size <= profile.limits.maxTakesPerProfile }) {
            "Probe request exceeds the bundled profile limits"
        }

        val candidateProfiles = profile.videoProfiles.associateBy(LocalVideoProfileCandidate::id)
        val pinnedInputs = buildList {
            add(validatePin(request.tool, "Tool"))
            request.references.forEach { add(validatePin(it.pin, "Reference '${it.id}'")) }
            request.profiles.forEach { probe ->
                require(candidateProfiles.containsKey(probe.profileId)) {
                    "Unknown candidate video profile '${probe.profileId}'"
                }
                probe.modelPins.forEach { add(validatePin(it, "Model '${probe.profileId}/${it.id}'")) }
            }
        }

        val outputDirectory = absoluteNormalized(request.outputDirectory, "outputDirectory")
        require(!Files.exists(outputDirectory, NOFOLLOW_LINKS) || Files.isDirectory(outputDirectory, NOFOLLOW_LINKS)) {
            "outputDirectory must be a directory when it already exists: $outputDirectory"
        }
        val plannedOutputs = listOf(request.reportPath) + request.profiles.flatMap { profileRequest ->
            profileRequest.takes.map(LocalVideoProbeTake::outputPath)
        }
        val normalizedOutputs = plannedOutputs.map { raw ->
            absoluteNormalized(raw, "Output path").also { output ->
                require(output.startsWith(outputDirectory) && output != outputDirectory) {
                    "Output path must be a child of outputDirectory: $output"
                }
                require(!Files.exists(output, NOFOLLOW_LINKS)) {
                    "Refusing to replace existing output: $output"
                }
                requireNearestExistingParentIsDirectory(output)
            }
        }
        val canonicalInputs = pinnedInputs.map { it.path.toRealPath() }
        val canonicalOutputDirectory = canonicalFuturePath(outputDirectory)
        val canonicalOutputs = normalizedOutputs.map(::canonicalFuturePath)
        canonicalOutputs.forEachIndexed { index, output ->
            require(hasResolvedPrefix(output, canonicalOutputDirectory) && output.nameCount > canonicalOutputDirectory.nameCount) {
                "Output path must resolve to a child of outputDirectory: ${normalizedOutputs[index]}"
            }
            canonicalOutputs.drop(index + 1).forEach { other ->
                require(!hasResolvedPrefix(output, other, portableNames = true) &&
                    !hasResolvedPrefix(other, output, portableNames = true)) {
                    "Every report and take must have a distinct output path with no ancestor/descendant overlap: $output and $other"
                }
            }
        }
        require(canonicalInputs.none { input -> hasResolvedPrefix(input, canonicalOutputDirectory, portableNames = true) }) {
            "outputDirectory contains a pinned tool, model, or reference input"
        }
        canonicalOutputs.forEach { output ->
            require(canonicalInputs.none { input ->
                hasResolvedPrefix(input, output, portableNames = true) ||
                    hasResolvedPrefix(output, input, portableNames = true)
            }) {
                "Unsafe output overlap with a pinned tool, model, or reference: $output"
            }
        }

        return LocalVideoProbePreparationReport(
            status = LocalVideoProbeStatus.NOT_RUN,
            reason = "Preparation validated pins and reserved output names only; V11 must explicitly run and measure the local backend.",
            profileId = profile.id,
            profileEvidence = profile.evidence,
            backendId = profile.backend.id,
            validatedInputPins = pinnedInputs.size,
            preparedVideoProfiles = request.profiles.size,
            preparedTakes = request.profiles.sumOf { it.takes.size },
            outputLocations = normalizedOutputs.map(Path::toString),
            measurements = null,
            selectedProfileId = null,
        )
    }

    fun prepare(profileText: String, requestText: String): LocalVideoProbePreparationReport =
        prepare(decodeProfile(profileText), decodeRequest(requestText))

    fun encodeReport(report: LocalVideoProbePreparationReport): String = json.encodeToString(report)

    private fun validateProfile(profile: LocalVideoProfileDocument) {
        require(profile.schema == PROFILE_SCHEMA && profile.version == SCHEMA_VERSION) {
            "Unsupported local video profile schema/version '${profile.schema}' v${profile.version}; expected $PROFILE_SCHEMA v$SCHEMA_VERSION"
        }
        requireToken(profile.id, "Profile id")
        require(profile.backend.id == SUPPORTED_BACKEND_ID) {
            "Unsupported local backend '${profile.backend.id}'; this candidate supports only '$SUPPORTED_BACKEND_ID'"
        }
        require(profile.backend.label.isNotBlank()) { "Backend label must not be blank" }
        require(profile.limits.maxVideoProfiles in 1..MAX_VIDEO_PROFILES &&
            profile.limits.maxTakesPerProfile in 1..MAX_TAKES_PER_PROFILE) {
            "Profile limits must allow at least one and at most $MAX_VIDEO_PROFILES video profiles / $MAX_TAKES_PER_PROFILE takes"
        }
        require(profile.videoProfiles.isNotEmpty() && profile.videoProfiles.size <= MAX_VIDEO_PROFILES) {
            "Profile must declare between one and $MAX_VIDEO_PROFILES candidate video profiles"
        }
        require(profile.videoProfiles.map(LocalVideoProfileCandidate::id).distinct().size == profile.videoProfiles.size) {
            "Candidate video profile ids must be unique"
        }
        validateEvidence(profile.evidence, "Local profile")
        profile.videoProfiles.forEach { candidate ->
            requireToken(candidate.id, "Candidate video profile id")
            require(candidate.label.isNotBlank()) { "Candidate video profile label must not be blank" }
            validateEvidence(candidate.evidence, "Candidate video profile '${candidate.id}'")
        }
    }

    private fun validateEvidence(evidence: LocalVideoEvidence, owner: String) {
        when (evidence.status) {
            LocalVideoEvidenceStatus.CANDIDATE_UNVERIFIED -> require(
                evidence.measurementRecord == null && evidence.selectionRecord == null,
            ) { "$owner is candidate/unverified and cannot contain measured or selected facts" }
            LocalVideoEvidenceStatus.MEASURED -> require(
                evidence.measurementRecord != null && evidence.selectionRecord == null,
            ) { "$owner measured evidence requires only a pinned measurement record" }
            LocalVideoEvidenceStatus.SELECTED -> require(
                evidence.measurementRecord != null && evidence.selectionRecord != null,
            ) { "$owner selected evidence requires pinned measurement and selection records" }
        }
        evidence.measurementRecord?.let { validateEvidencePin(it, "$owner measurement record") }
        evidence.selectionRecord?.let { validateEvidencePin(it, "$owner selection record") }
    }

    private fun validateEvidencePin(pin: EvidenceRecordPin, label: String) {
        require(pin.id.isNotBlank()) { "$label id must not be blank" }
        requireHash(pin.sha256, "$label SHA-256")
    }

    private fun validateRequestShape(request: LocalVideoProbeRequest) {
        require(request.schema == REQUEST_SCHEMA && request.version == SCHEMA_VERSION) {
            "Unsupported local video probe request schema/version '${request.schema}' v${request.version}; expected $REQUEST_SCHEMA v$SCHEMA_VERSION"
        }
        requireToken(request.profileId, "profileId")
        require(request.backendId == SUPPORTED_BACKEND_ID) {
            "Unsupported local backend '${request.backendId}'; use '$SUPPORTED_BACKEND_ID' for this candidate"
        }
        require(request.prompt.isNotBlank() && request.prompt.length <= MAX_PROMPT_CHARACTERS) {
            "Prompt must contain 1..$MAX_PROMPT_CHARACTERS characters"
        }
        require(request.profiles.isNotEmpty() && request.profiles.size <= MAX_VIDEO_PROFILES) {
            "Probe request must contain between one and $MAX_VIDEO_PROFILES video profiles"
        }
        require(request.profiles.map(LocalVideoProbeProfile::profileId).distinct().size == request.profiles.size) {
            "Each candidate video profile may appear only once"
        }
        require(request.references.isNotEmpty()) { "Probe request requires at least one pinned reference" }
        require(request.references.map(LocalVideoReferencePin::id).distinct().size == request.references.size) {
            "Reference ids must be unique"
        }
        request.references.forEach { reference ->
            requireToken(reference.id, "Reference id")
        }
        val referenceIds = request.references.mapTo(mutableSetOf(), LocalVideoReferencePin::id)
        request.profiles.forEach { profile ->
            requireToken(profile.profileId, "Probe video profile id")
            require(profile.modelPins.isNotEmpty()) { "Video profile '${profile.profileId}' requires at least one pinned model artifact" }
            require(profile.modelPins.map(PinnedLocalFile::id).distinct().size == profile.modelPins.size) {
                "Model pin ids must be unique within video profile '${profile.profileId}'"
            }
            require(profile.takes.isNotEmpty() && profile.takes.size <= MAX_TAKES_PER_PROFILE) {
                "Video profile '${profile.profileId}' must request between one and $MAX_TAKES_PER_PROFILE takes"
            }
            require(profile.takes.map(LocalVideoProbeTake::id).distinct().size == profile.takes.size) {
                "Take ids must be unique within video profile '${profile.profileId}'"
            }
            profile.takes.forEach { requireToken(it.id, "Take id") }
            require(profile.referenceBindings.distinct().size == profile.referenceBindings.size) {
                "Reference bindings must be unique within video profile '${profile.profileId}'"
            }
            require(profile.referenceBindings.all(referenceIds::contains)) {
                "Video profile '${profile.profileId}' contains an unknown reference binding"
            }
        }
    }

    private fun validatePin(pin: PinnedLocalFile, label: String): ValidatedLocalPin {
        requireToken(pin.id, "$label id")
        requireHash(pin.sha256, "$label SHA-256")
        val path = absoluteNormalized(pin.path, "$label path")
        require(!Files.isSymbolicLink(path) && Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            "$label pin is missing or is not a regular non-symbolic-link file: $path"
        }
        val actual = sha256(path)
        require(actual == pin.sha256) {
            "$label pin mismatch for '$path': expected ${pin.sha256}, found $actual"
        }
        return ValidatedLocalPin(pin.id, path)
    }

    private fun absoluteNormalized(raw: String, label: String): Path {
        val path = try {
            Path.of(raw)
        } catch (error: Exception) {
            throw IllegalArgumentException("$label is not a valid path: ${error.message}", error)
        }
        require(path.isAbsolute) { "$label must be absolute: $raw" }
        // Lexical normalization before following symlinks can change the named input or output.
        // Reject traversal components instead of validating a different path from the request.
        require(path.none { it.toString() == "." || it.toString() == ".." }) {
            "$label must be normalized without '.' or '..' path components: $raw"
        }
        return path
    }

    private fun requireNearestExistingParentIsDirectory(path: Path) {
        var ancestor = path.parent
        while (ancestor != null && !Files.exists(ancestor, NOFOLLOW_LINKS)) ancestor = ancestor.parent
        require(ancestor != null && Files.isDirectory(ancestor, NOFOLLOW_LINKS)) {
            "Output path has no existing directory ancestor: $path"
        }
    }

    private fun canonicalFuturePath(path: Path): Path {
        var existing = path
        val missing = ArrayDeque<String>()
        while (!Files.exists(existing, NOFOLLOW_LINKS)) {
            val name = existing.fileName.toString()
            require(Regex("[A-Za-z0-9._-]+").matches(name)) {
                "Absent output path components must use portable ASCII letters, digits, dot, underscore or hyphen: $path"
            }
            missing.addFirst(name)
            existing = checkNotNull(existing.parent) { "Path has no existing ancestor: $path" }
        }
        var canonical = existing.toRealPath()
        missing.forEach { canonical = canonical.resolve(it) }
        return canonical.normalize()
    }

    /**
     * Existing prefixes use native file identity when their spelling differs. Missing components
     * are restricted to ASCII above, so collision checks can conservatively ignore case without
     * guessing a volume's Unicode rules. Containment keeps case-sensitive names distinct unless
     * native identity proves an alias; otherwise folding could admit a symlink escape on a
     * case-sensitive volume. No check creates a filesystem probe or rewrites the requested paths.
     */
    private fun hasResolvedPrefix(path: Path, prefix: Path, portableNames: Boolean = false): Boolean {
        if (path.root != prefix.root || path.nameCount < prefix.nameCount) return false
        var pathPart = path.root
        var prefixPart = prefix.root
        for (index in 0 until prefix.nameCount) {
            val pathName = path.getName(index).toString()
            val prefixName = prefix.getName(index).toString()
            pathPart = pathPart.resolve(pathName)
            prefixPart = prefixPart.resolve(prefixName)
            if (pathName == prefixName || (portableNames && pathName.equals(prefixName, ignoreCase = true))) continue
            if (!Files.exists(pathPart, NOFOLLOW_LINKS) || !Files.exists(prefixPart, NOFOLLOW_LINKS) ||
                !Files.isSameFile(pathPart, prefixPart)) return false
        }
        return true
    }

    private fun requireToken(value: String, label: String) {
        require(Regex("[a-z0-9][a-z0-9._-]{0,127}").matches(value)) {
            "$label must be a lowercase stable token"
        }
    }

    private fun requireHash(value: String, label: String) {
        require(Regex("[0-9a-f]{64}").matches(value)) { "$label must be 64 lowercase hexadecimal characters" }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private inline fun <T> decode(label: String, block: () -> T): T = try {
        block()
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Invalid $label JSON: ${error.message ?: error.javaClass.simpleName}", error)
    }

    private data class ValidatedLocalPin(val id: String, val path: Path)
}

@Serializable
data class LocalVideoProfileDocument(
    val schema: String,
    val version: Int,
    val id: String,
    val backend: LocalVideoBackendCandidate,
    val evidence: LocalVideoEvidence,
    val videoProfiles: List<LocalVideoProfileCandidate>,
    val limits: LocalVideoProbeLimits,
)

@Serializable
data class LocalVideoBackendCandidate(val id: String, val label: String)

@Serializable
data class LocalVideoProfileCandidate(val id: String, val label: String, val evidence: LocalVideoEvidence)

@Serializable
data class LocalVideoEvidence(
    val status: LocalVideoEvidenceStatus,
    val measurementRecord: EvidenceRecordPin? = null,
    val selectionRecord: EvidenceRecordPin? = null,
)

@Serializable
enum class LocalVideoEvidenceStatus { CANDIDATE_UNVERIFIED, MEASURED, SELECTED }

@Serializable
data class EvidenceRecordPin(val id: String, val sha256: String)

@Serializable
data class LocalVideoProbeLimits(val maxVideoProfiles: Int, val maxTakesPerProfile: Int)

@Serializable
data class LocalVideoProbeRequest(
    val schema: String,
    val version: Int,
    val profileId: String,
    val backendId: String,
    val tool: PinnedLocalFile,
    val prompt: String,
    val references: List<LocalVideoReferencePin> = emptyList(),
    val profiles: List<LocalVideoProbeProfile>,
    val outputDirectory: String,
    val reportPath: String,
)

@Serializable
data class PinnedLocalFile(val id: String, val path: String, val sha256: String)

@Serializable
data class LocalVideoReferencePin(
    val id: String,
    val pin: PinnedLocalFile,
    val role: LocalVideoReferenceRole? = null,
)

@Serializable
enum class LocalVideoReferenceRole { SUBJECT, CHARACTER, ENVIRONMENT, STYLE, COMPLETE_SCENE }

@Serializable
data class LocalVideoProbeProfile(
    val profileId: String,
    val modelPins: List<PinnedLocalFile>,
    val referenceBindings: List<String> = emptyList(),
    val takes: List<LocalVideoProbeTake>,
)

@Serializable
data class LocalVideoProbeTake(val id: String, val outputPath: String)

@Serializable
enum class LocalVideoProbeStatus { NOT_RUN }

@Serializable
data class LocalVideoProbePreparationReport(
    val status: LocalVideoProbeStatus,
    val reason: String,
    val profileId: String,
    val profileEvidence: LocalVideoEvidence,
    val backendId: String,
    val validatedInputPins: Int,
    val preparedVideoProfiles: Int,
    val preparedTakes: Int,
    val outputLocations: List<String>,
    val measurements: LocalVideoProbeMeasurements? = null,
    val selectedProfileId: String? = null,
)

/** Reserved for real V11 evidence. V11a always reports this field as null. */
@Serializable
data class LocalVideoProbeMeasurements(
    val measurementRecord: EvidenceRecordPin,
    val coldLoadMillis: Long,
    val generatedVideoMillis: Long,
    val wallClockMillis: Long,
    val peakMemoryBytes: Long,
    val peakSwapBytes: Long,
)
