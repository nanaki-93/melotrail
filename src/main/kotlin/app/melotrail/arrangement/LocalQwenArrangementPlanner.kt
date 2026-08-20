package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * The small boundary used by the arrangement planner to request one JSON response
 * from a local model. Tests provide fixture-backed implementations of this interface.
 */
fun interface LocalQwenClient {
    fun complete(systemPrompt: String, userPrompt: String): String
}

/** Calls LM Studio's OpenAI-compatible local chat-completions endpoint. */
class LmStudioQwenClient(
    private val endpoint: String = System.getenv("LM_STUDIO_CHAT_COMPLETIONS_URL")
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_ENDPOINT,
    private val model: String = System.getenv("QWEN_MODEL")
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_MODEL,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(600, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(600, java.util.concurrent.TimeUnit.SECONDS)
        .build()
) : LocalQwenClient {
    override fun complete(systemPrompt: String, userPrompt: String): String {
        val payload = json.encodeToString(
            ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", userPrompt)
                ),
                temperature = 0.0
            )
        )
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                require(response.isSuccessful) {
                    "LM Studio request failed with HTTP ${response.code}: ${body.take(MAX_ERROR_BODY_LENGTH)}"
                }
                return extractContent(body)
            }
        } catch (exception: IOException) {
            throw IllegalStateException(
                "Could not reach LM Studio at $endpoint. Start a local model or use --planner deterministic.",
                exception
            )
        }
    }

    private fun extractContent(responseBody: String): String {
        val response = try {
            json.parseToJsonElement(responseBody).jsonObject
        } catch (exception: Exception) {
            throw IllegalArgumentException("LM Studio returned invalid JSON: ${exception.message}", exception)
        }
        val choices = response["choices"] as? JsonArray
            ?: throw IllegalArgumentException("LM Studio response did not contain choices")
        val content = choices.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.content
            ?.trim()
        require(!content.isNullOrEmpty()) { "LM Studio response did not contain arrangement content" }
        return content
    }

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String
    )

    private companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:1234/v1/chat/completions"
        const val DEFAULT_MODEL = "qwen"
        const val MAX_ERROR_BODY_LENGTH = 500
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val json = Json { ignoreUnknownKeys = false }
    }
}

/**
 * Plans an arrangement with a local Qwen model, then treats its response only as
 * untrusted JSON data. No model-supplied code, commands, or file paths are used.
 */
class LocalQwenArrangementPlanner(
    private val client: LocalQwenClient = LmStudioQwenClient()
) : ArrangementPlanner {
    override fun plan(input: ArrangementInput): Arrangement {
        input.requireValid()
        val allowedInstruments = input.requestedInstruments.ifEmpty { listOf(SOURCE_INSTRUMENT_NAME) }
        val output = client.complete(SYSTEM_PROMPT, createUserPrompt(input, allowedInstruments))
        val arrangement = parseArrangement(output)

        val errors = arrangement.validate(input.project.parts.map { it.id }, input.structure).errors.toMutableList()
        val allowedInstrumentNames = allowedInstruments.map { it.lowercase() }.toSet()
        arrangement.sections.forEachIndexed { sectionIndex, section ->
            section.instruments
                .filter { it.name.lowercase() !in allowedInstrumentNames }
                .forEach { instrument ->
                    errors += "Section ${sectionIndex + 1} uses instrument '${instrument.name}', which is not allowed"
                }
        }
        require(errors.isEmpty()) { "Invalid Qwen arrangement: ${errors.joinToString("; ")}" }
        return arrangement
    }

    private fun parseArrangement(output: String): Arrangement = try {
        strictJson.decodeFromString<Arrangement>(output)
    } catch (exception: Exception) {
        throw IllegalArgumentException(
            "Qwen returned invalid arrangement JSON: ${exception.message}",
            exception
        )
    }

    private fun createUserPrompt(input: ArrangementInput, allowedInstruments: List<String>): String {
        val projectMetadata = QwenProjectMetadata(
            version = input.project.version,
            name = input.project.name,
            parts = input.project.parts.map { QwenPartMetadata(it.id, it.name, it.sectionType.value) }
        )
        val analyses = input.analyses.map { (partId, analysis) ->
            QwenAnalysis(partId, analysis)
        }
        return """
            Project metadata:
            ${promptJson.encodeToString(projectMetadata)}

            Part analyses:
            ${promptJson.encodeToString(analyses)}

            Requested structure:
            ${promptJson.encodeToString(input.structure)}

            Allowed instruments:
            ${promptJson.encodeToString(allowedInstruments)}

            Style:
            ${promptJson.encodeToString(input.style ?: "")}

            Constraints:
            ${promptJson.encodeToString(CONSTRAINTS)}
        """.trimIndent()
    }

    @Serializable
    private data class QwenProjectMetadata(
        val version: Int,
        val name: String,
        val parts: List<QwenPartMetadata>
    )

    @Serializable
    private data class QwenPartMetadata(
        val id: String,
        val name: String,
        val sectionType: String
    )

    @Serializable
    private data class QwenAnalysis(
        val partId: String,
        val analysis: PartAnalysis
    )

    private companion object {
        const val SOURCE_INSTRUMENT_NAME = "source"
        val strictJson = Json { ignoreUnknownKeys = false }
        val promptJson = Json { encodeDefaults = true }
        val CONSTRAINTS = listOf(
            "Return JSON only, with no markdown or prose.",
            "Use arrangement schema version 2 exactly; do not add fields.",
            "Preserve the requested structure exactly.",
            "Use only allowed instruments.",
            "Every section must retain one source instrument.",
            "Generated instrument density must be a finite number from 0 through 1.",
            "A transition is either none (0 bars), crossfade (0 bars and crossfadeMs 80..4000), or bridge (1..2 bars with bridge data).",
            "Bridge data can use only bass_pickup, drum_fill, pad_swell, and melody_pickup; use melody_pickup only with confident harmony.",
            "Do not include paths, code, commands, or executable content."
        )
        const val SYSTEM_PROMPT = """
            You are a music arrangement planner. You do not generate audio, code, commands, or file paths.
            Return only a valid JSON arrangement matching this schema. Top-level fields are exactly version and sections.
            {"version":2,"sections":[{"index":0,"partId":"A","instruments":[
            {"name":"source","mode":"source"},
            {"name":"bass","mode":"generated","role":"root_fifth","density":0.3}],
            "transitionOut":{"type":"bridge","bars":1,"crossfadeMs":180,
            "bridge":{"energy":0.5,"elements":["bass_pickup","drum_fill","pad_swell"]}}}]}
            Source instruments never set density. Generated instruments always set density. For the final section use
            {"type":"none","bars":0,"crossfadeMs":0}. Do not use markdown.
             Do not include markdown or prose.
        """
    }
}
