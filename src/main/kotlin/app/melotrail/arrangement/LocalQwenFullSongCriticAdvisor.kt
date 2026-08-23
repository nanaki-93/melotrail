package app.melotrail.arrangement

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Optional local-Qwen critic companion that receives only deterministic issue and metric evidence. */
class LocalQwenFullSongCriticAdvisor(
    private val client: LocalQwenClient = LmStudioQwenClient(),
    private val modelIdentity: String = System.getenv("QWEN_MODEL")?.takeIf { it.matches(MODEL_ID) } ?: "qwen-local"
) : FullSongCriticAdvisor {
    /** Requests a bounded producer summary from the local model after deterministic analysis completes. */
    override fun advise(report: FullSongCriticReport): FullSongCriticAdvice {
        val response = requestQwenWithAutomaticRetries(client, SYSTEM_PROMPT, json.encodeToString(Evidence(report))) { text ->
            json.decodeFromString<ModelAdvice>(text).observations
        }
        return FullSongCriticAdvice(modelIdentity, response.sorted())
    }

    @Serializable private data class Evidence(val metrics: List<FullSongAggregateMetric>, val issues: List<Issue>) {
        constructor(report: FullSongCriticReport) : this(report.aggregateMetrics, report.issues.map(::Issue))
    }
    @Serializable private data class Issue(val category: FullSongIssueCategory, val severity: FullSongIssueSeverity, val targetRole: String, val occurrenceId: String? = null, val startBar: Long, val endBar: Long, val reasonCode: String) {
        constructor(issue: FullSongIssue) : this(issue.category, issue.severity, issue.targetRole, issue.occurrenceId, issue.window.startBar, issue.window.endBar, issue.reasonCode)
    }
    @Serializable private data class ModelAdvice(val observations: List<String>)
    @OptIn(ExperimentalSerializationApi::class) private companion object {
        val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        val MODEL_ID = Regex("[A-Za-z0-9._:-]{1,120}")
        const val SYSTEM_PROMPT = """
            Return exactly one JSON object: {"observations":[...]}. Write one to eight short producer observations from the supplied deterministic metrics and issue locations.
            Do not invent scores, MIDI notes, operations, paths, commands, or fixes. The deterministic report is authoritative.
        """
    }
}
