package app.melotrail.provenance

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ProvenanceRecord(
    @SerialName("applicationVersion")
    val applicationVersion: String = "0.1.0",
    @SerialName("entries")
    val entries: List<ProvenanceEntry> = emptyList()
) {
    fun withEntry(entry: ProvenanceEntry): ProvenanceRecord {
        return copy(entries = entries + entry)
    }

    fun filterByType(type: String): List<ProvenanceEntry> {
        return entries.filter { it.operation == type }
    }

    fun filterByDateRange(start: Instant, end: Instant): List<ProvenanceEntry> {
        return entries.filter { it.timestamp in start..end }
    }
}
