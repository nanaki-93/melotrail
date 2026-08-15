package app.melotrail.modellifecycle

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelState {
    @SerialName("IDLE")
    IDLE,

    @SerialName("LOADING")
    LOADING,

    @SerialName("LOADED")
    LOADED,

    @SerialName("UNLOADING")
    UNLOADING,

    @SerialName("UNLOADED")
    UNLOADED,

    @SerialName("ERROR")
    ERROR
}

@Serializable
data class ModelManifest(
    @SerialName("name")
    val name: String,
    @SerialName("version")
    val version: String,
    @SerialName("fileHash")
    val fileHash: String? = null,
    @SerialName("fileSize")
    val fileSize: Long = 0L,
    @SerialName("estimatedMemoryGB")
    val estimatedMemoryGB: Double = 0.0,
    @SerialName("capabilities")
    val capabilities: List<String> = emptyList(),
    @SerialName("minimumRAM")
    val minimumRAM: Double = 0.0,
    @SerialName("installed")
    val installed: Boolean = false,
    @SerialName("installedAt")
    val installedAt: Instant? = null
) {
    val id: String = "$name/$version"
}

@Serializable
data class ModelHandle(
    @SerialName("name")
    val name: String,
    @SerialName("version")
    val version: String,
    @SerialName("referenceCount")
    var referenceCount: Int = 0,
    @SerialName("loadedAt")
    val loadedAt: Instant = Clock.System.now(),
    @SerialName("state")
    var state: ModelState = ModelState.IDLE
) {
    val id: String = "$name/$version"

    fun incrementReference(): ModelHandle {
        referenceCount++
        return this
    }

    fun decrementReference(): Int {
        return referenceCount--
    }
}
