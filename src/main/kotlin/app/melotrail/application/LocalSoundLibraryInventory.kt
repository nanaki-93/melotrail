package app.melotrail.application

import app.melotrail.arrangement.InstrumentRegistryLoader
import java.nio.file.Path
import java.util.Locale

/**
 * UI-safe, read-only projection of the configured local sound library.
 *
 * Paths and registry documents remain inside the registry boundary.  A caller
 * receives an instrument only after its SFZ and every referenced sample have
 * passed the existing registry validation.
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
    /** Stable logical registry name, not a filesystem path. */
    val id: String,
    val name: String,
    val category: String,
    val sampleCount: Int,
    val licenseName: String,
    val license: String,
    val source: String,
    val commercialUse: Boolean,
    val attributionRequired: Boolean
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
                recoveryMessage = "Choose the local sounds folder containing instruments.json and LICENSES.json."
            )
        }
        return runCatching { InstrumentRegistryLoader(validatedRoot).load() }
            .fold(
                onSuccess = { registry ->
                    LocalSoundLibraryInventory(
                        LocalSoundLibraryInventoryState.READY,
                        registry.all().map { descriptor ->
                            LocalSoundLibraryInstrument(
                                id = descriptor.instrument.wireName,
                                name = descriptor.instrument.wireName.replaceFirstChar(Char::uppercase),
                                category = descriptor.instrument.wireName.replaceFirstChar(Char::uppercase),
                                sampleCount = descriptor.samplePaths.size,
                                licenseName = descriptor.license.displayName,
                                license = descriptor.license.license,
                                source = descriptor.license.source,
                                commercialUse = descriptor.license.commercialUse,
                                attributionRequired = descriptor.license.attributionRequired
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
            (normalizedQuery.isEmpty() || listOf(instrument.name, instrument.category, instrument.licenseName, instrument.source)
                .any { it.lowercase(Locale.ROOT).contains(normalizedQuery) })
    }
}
