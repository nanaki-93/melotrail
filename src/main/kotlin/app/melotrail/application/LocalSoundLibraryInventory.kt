package app.melotrail.application

import app.melotrail.arrangement.InstrumentRegistryLoader
import app.melotrail.arrangement.InstrumentQualityTier
import app.melotrail.arrangement.InstrumentSelectionMode
import java.nio.file.Path
import java.util.Locale

/**
 * UI-safe, read-only projection of the configured local sound library.
 *
 * Paths and registry documents remain inside the registry boundary.  A caller
 * receives an instrument only after its engine asset and any inspectable
 * sample references have passed registry validation.
 */
data class LocalSoundLibraryInventory(
    val state: LocalSoundLibraryInventoryState,
    val instruments: List<LocalSoundLibraryInstrument> = emptyList(),
    val recoveryMessage: String? = null
) {
    init {
        require(instruments.map(LocalSoundLibraryInstrument::id).distinct().size == instruments.size) {
            "Local sound-library inventory IDs must be unique"
        }
        require(instruments == instruments.sortedBy(LocalSoundLibraryInstrument::id)) {
            "Local sound-library inventory must have deterministic ordering"
        }
    }
}

enum class LocalSoundLibraryInventoryState { UNCONFIGURED, INVALID, READY }

data class LocalSoundLibraryInstrument(
    /** Stable registry ID, never a filesystem path or SFZ filename. */
    val id: String,
    val name: String,
    val category: String,
    val selectionMode: InstrumentSelectionMode = InstrumentSelectionMode.AUTOMATIC,
    val productionApproved: Boolean = false,
    val qualityTier: InstrumentQualityTier = InstrumentQualityTier.DRAFT,
    val styleAffinity: Set<String> = emptySet(),
    val preferredRoles: Set<String> = emptySet(),
    val sampleCount: Int,
    val licenseName: String,
    val license: String,
    val source: String,
    val commercialUse: Boolean,
    val attributionRequired: Boolean,
    val roles: Set<String> = emptySet(),
    val verifiedCapabilities: Set<String> = emptySet(),
    val available: Boolean = true,
    val diagnostics: List<String> = emptyList()
)

fun interface LocalSoundLibraryInventoryReader {
    fun read(validatedRoot: Path?): LocalSoundLibraryInventory
}

/** Reads the existing validated registry without writing to it or the project. */
object RegistryLocalSoundLibraryInventoryReader : LocalSoundLibraryInventoryReader {
    override fun read(validatedRoot: Path?): LocalSoundLibraryInventory {
        if (validatedRoot == null) {
            return LocalSoundLibraryInventory(
                LocalSoundLibraryInventoryState.UNCONFIGURED,
                recoveryMessage = "Choose the local sounds folder containing instruments.json."
            )
        }
        return runCatching { InstrumentRegistryLoader(validatedRoot).load() }
            .fold(
                onSuccess = { registry ->
                    LocalSoundLibraryInventory(
                        LocalSoundLibraryInventoryState.READY,
                        registry.all().map { descriptor ->
                            LocalSoundLibraryInstrument(
                                id = descriptor.id,
                                name = descriptor.name,
                                category = descriptor.category,
                                selectionMode = descriptor.selectionMode,
                                productionApproved = descriptor.productionApproved,
                                qualityTier = descriptor.qualityTier,
                                styleAffinity = descriptor.styleAffinity,
                                preferredRoles = descriptor.preferredRoles.map { it.name.lowercase().replace('_', '-') }.toSortedSet(),
                                sampleCount = descriptor.samplePaths.size,
                                licenseName = descriptor.license.displayName,
                                license = descriptor.license.license,
                                source = descriptor.license.source,
                                commercialUse = descriptor.license.commercialUse,
                                attributionRequired = descriptor.license.attributionRequired,
                                roles = descriptor.roles.map { it.name.lowercase().replace('_', '-') }.toSortedSet(),
                                verifiedCapabilities = descriptor.verifiedCapabilities.performance.map { it.name.lowercase().replace('_', '-') }.toSortedSet(),
                                available = descriptor.licenseAdmission.admission.name == "ADMITTED",
                                diagnostics = descriptor.licenseAdmission.reasons
                            )
                        }.sortedBy(LocalSoundLibraryInstrument::id)
                    )
                },
                onFailure = { failure ->
                    LocalSoundLibraryInventory(
                        LocalSoundLibraryInventoryState.INVALID,
                        recoveryMessage = failure.message ?: "The selected sound library is not valid."
                    )
                }
            )
    }
}

/** Deterministic local UI projection; it never changes the registry or source library. */
fun LocalSoundLibraryInventory.filtered(query: String, category: String?): List<LocalSoundLibraryInstrument> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return instruments.filter { instrument ->
        (category == null || instrument.category == category) &&
            (normalizedQuery.isEmpty() || (listOf(instrument.name, instrument.category, instrument.licenseName, instrument.source) + instrument.roles + instrument.verifiedCapabilities + instrument.diagnostics)
                .any { it.lowercase(Locale.ROOT).contains(normalizedQuery) })
    }
}
