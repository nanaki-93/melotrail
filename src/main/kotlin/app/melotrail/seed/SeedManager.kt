package app.melotrail.seed

import kotlin.random.Random

data class SeedOptions(
    val customSeed: Long? = null,
    val randomize: Boolean = true
) {
    fun resolve(): Long {
        return customSeed ?: Random.nextLong(0, (1L shl 53) - 1)
    }

    fun withCustomSeed(seed: Long): SeedOptions {
        return copy(customSeed = seed, randomize = false)
    }

    fun randomized(): SeedOptions {
        return copy(
            customSeed = Random.nextLong(0, (1L shl 53) - 1),
            randomize = false
        )
    }
}

data class ValidationResult(
    val valid: Boolean,
    val error: String? = null
) {
    companion object {
        fun valid(): ValidationResult = ValidationResult(true, null)
        fun invalid(error: String): ValidationResult = ValidationResult(false, error)
    }
}

class SeedManager {
    companion object {
        private const val MAX_SEED = (1L shl 53) - 1
        private const val MIN_SEED = 0L
    }

    fun generate(): Long {
        return Random.nextLong(MIN_SEED, MAX_SEED + 1)
    }

    fun validate(seed: Long): ValidationResult {
        return when {
            seed < MIN_SEED -> ValidationResult.invalid("Seed must be non-negative (got $seed)")
            seed > MAX_SEED -> ValidationResult.invalid(
                "Seed exceeds float precision range (max ${(1L shl 53) - 1}, got $seed)"
            )
            else -> ValidationResult.valid()
        }
    }

    fun randomize(current: Long): Long {
        return generate()
    }

    fun resolve(options: SeedOptions): Long {
        return options.resolve()
    }
}
