package app.melotrail.licensing

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class ModelRegistry(
    @SerialName("version")
    val version: Int = 1,
    @SerialName("models")
    val models: Map<ModelId, ModelLicense> = emptyMap()
) {
    fun getModel(id: ModelId): ModelLicense? = models[id]

    fun isApproved(id: ModelId): Boolean = getModel(id)?.isApprovedForCommercialUse() == true

    fun requiresReview(id: ModelId): Boolean = getModel(id)?.requiresReview() == true

    fun isBlocked(id: ModelId): Boolean = getModel(id)?.isBlocked() == true

    fun listInstalledModels(): List<ModelLicense> = models.values.filter { it.installed }

    fun listApprovedModels(): List<ModelLicense> = models.values.filter { it.isApprovedForCommercialUse() }
}

class ModelRegistryManager(private val registryPath: Path) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var registry: ModelRegistry = ModelRegistry()

    init {
        load()
    }

    fun load(): ModelRegistry {
        if (Files.exists(registryPath)) {
            val content = Files.readString(registryPath)
            registry = json.decodeFromString(ModelRegistry.serializer(), content)
        }
        return registry
    }

    fun save() {
        Files.createDirectories(registryPath.parent)
        Files.writeString(registryPath, json.encodeToString(ModelRegistry.serializer(), registry))
    }

    fun addModel(license: ModelLicense): ModelRegistry {
        registry = registry.copy(models = registry.models + (license.id to license))
        save()
        return registry
    }

    fun updateLicenseStatus(id: ModelId, status: LicenseStatus): ModelLicense? {
        val existing = registry.models[id] ?: return null
        val updated = existing.copy(
            status = status,
            reviewedAt = kotlinx.datetime.Clock.System.now(),
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
        registry = registry.copy(models = registry.models + (id to updated))
        save()
        return updated
    }

    fun installModel(id: ModelId): ModelLicense? {
        val existing = registry.models[id] ?: return null
        val updated = existing.copy(
            installed = true,
            installedAt = kotlinx.datetime.Clock.System.now(),
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
        registry = registry.copy(models = registry.models + (id to updated))
        save()
        return updated
    }

    fun uninstallModel(id: ModelId): ModelLicense? {
        val existing = registry.models[id] ?: return null
        val updated = existing.copy(
            installed = false,
            installedAt = null,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
        registry = registry.copy(models = registry.models + (id to updated))
        save()
        return updated
    }

    fun canUseModel(id: ModelId): Boolean {
        return registry.isApproved(id)
    }

    fun getAllModels(): Map<ModelId, ModelLicense> = registry.models

    fun listModels(): List<ModelLicense> = registry.models.values.toList()
}
