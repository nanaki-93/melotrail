package app.melotrail.arrangement

import kotlinx.serialization.Serializable

/**
 * A bounded, user-authored production mix.  It is deliberately independent of
 * MIDI/arrangement decisions and records every audio decision needed to
 * reproduce a mix from the rendered stems.
 */
@Serializable
data class MixPlan(
    val version: Int = VERSION,
    val mixerId: String = MIXER_ID,
    val inputStems: List<MixPlanInputStem> = emptyList(),
    val tracks: Map<String, MixTrackPlan> = defaults(),
    val room: SharedRoomPlan = SharedRoomPlan(),
    val buses: Map<MixBus, MixBusPlan> = defaultBuses()
) {
    fun requireValid() {
        require(version == VERSION && mixerId == MIXER_ID) { "Unsupported production mix plan" }
        require(inputStems.map(MixPlanInputStem::name).distinct().size == inputStems.size) { "Mix plan repeats a stem input" }
        require(tracks.keys.all { it in logicalNames }) { "Mix plan contains unsupported logical instruments" }
        tracks.forEach { (name, track) -> track.requireValid(name) }
        room.requireValid()
        require(buses.keys.all { it != MixBus.DIRECT }) { "Direct tracks cannot have a bus configuration" }
        buses.forEach { (bus, plan) -> plan.requireValid(bus) }
    }

    fun withInputs(inputs: List<MixPlanInputStem>) = copy(inputStems = inputs.sortedBy(MixPlanInputStem::name))

    companion object {
        const val VERSION = 1
        const val MIXER_ID = "production-stem-mixer-v1"
        val logicalNames = LogicalInstrument.entries.map(LogicalInstrument::wireName).toSet()
        fun defaults(): Map<String, MixTrackPlan> = logicalNames.associateWith { name ->
            MixTrackPlan(bus = if (name == LogicalInstrument.DRUMS.wireName) MixBus.DRUMS else MixBus.MUSIC)
        }
        fun defaultBuses() = mapOf(MixBus.MUSIC to MixBusPlan(), MixBus.DRUMS to MixBusPlan())
    }
}

@Serializable
data class MixPlanInputStem(val name: String, val sha256: String) {
    init {
        require(name in MixPlan.logicalNames) { "Mix plan stem name is unsupported" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "Mix plan stem fingerprint is invalid" }
    }
}

@Serializable
enum class MixBus { DIRECT, MUSIC, DRUMS }

@Serializable
data class MixTrackPlan(
    val gainDb: Double = 0.0,
    val pan: Double = 0.0,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val filter: FilterPlan = FilterPlan(),
    val eq: List<EqBandPlan> = emptyList(),
    val compression: CompressionPlan = CompressionPlan(),
    val reverbSend: Double = 0.12,
    val stereoWidth: Double = 1.0,
    val bus: MixBus = MixBus.MUSIC,
    val sectionAutomation: List<SectionMixAutomation> = emptyList()
) {
    fun requireValid(name: String) {
        require(gainDb.isFinite() && gainDb in -24.0..12.0) { "Mix track '$name' gain must be between -24 and 12 dB" }
        require(pan.isFinite() && pan in -1.0..1.0) { "Mix track '$name' pan must be between -1 and 1" }
        filter.requireValid(); eq.forEach(EqBandPlan::requireValid); compression.requireValid()
        require(reverbSend.isFinite() && reverbSend in 0.0..1.0) { "Mix track '$name' reverb send must be between 0 and 1" }
        require(stereoWidth.isFinite() && stereoWidth in 0.0..2.0) { "Mix track '$name' stereo width must be between 0 and 2" }
        require(sectionAutomation.map(SectionMixAutomation::sectionId).distinct().size == sectionAutomation.size) { "Mix track '$name' repeats a section automation" }
        sectionAutomation.forEach(SectionMixAutomation::requireValid)
    }
}

@Serializable
data class FilterPlan(val highPassHz: Double? = null, val lowPassHz: Double? = null) {
    fun requireValid() {
        highPassHz?.let { require(it.isFinite() && it in 20.0..20_000.0) { "High-pass cutoff is invalid" } }
        lowPassHz?.let { require(it.isFinite() && it in 20.0..20_000.0) { "Low-pass cutoff is invalid" } }
        require(highPassHz == null || lowPassHz == null || highPassHz < lowPassHz) { "High-pass cutoff must be below low-pass cutoff" }
    }
}

@Serializable
data class EqBandPlan(val frequencyHz: Double, val gainDb: Double, val q: Double = 1.0) {
    fun requireValid() {
        require(frequencyHz.isFinite() && frequencyHz in 20.0..20_000.0) { "EQ frequency is invalid" }
        require(gainDb.isFinite() && gainDb in -18.0..18.0) { "EQ gain is invalid" }
        require(q.isFinite() && q in 0.1..12.0) { "EQ Q is invalid" }
    }
}

@Serializable
data class CompressionPlan(val enabled: Boolean = false, val thresholdDb: Double = -18.0, val ratio: Double = 2.0, val makeupDb: Double = 0.0) {
    fun requireValid() {
        require(thresholdDb.isFinite() && thresholdDb in -60.0..0.0) { "Compression threshold is invalid" }
        require(ratio.isFinite() && ratio in 1.0..20.0) { "Compression ratio is invalid" }
        require(makeupDb.isFinite() && makeupDb in -18.0..18.0) { "Compression makeup gain is invalid" }
    }
}

@Serializable
data class SectionMixAutomation(
    val sectionId: String,
    val startFrame: Int,
    val endFrameExclusive: Int,
    val gainDb: Double? = null,
    val pan: Double? = null,
    val reverbSend: Double? = null
) {
    fun requireValid() {
        require(sectionId.matches(Regex("[A-Za-z0-9_-]{1,80}")) && startFrame >= 0 && endFrameExclusive > startFrame) { "Section automation range is invalid" }
        gainDb?.let { require(it.isFinite() && it in -24.0..12.0) { "Section automation gain is invalid" } }
        pan?.let { require(it.isFinite() && it in -1.0..1.0) { "Section automation pan is invalid" } }
        reverbSend?.let { require(it.isFinite() && it in 0.0..1.0) { "Section automation reverb send is invalid" } }
    }
}

@Serializable
data class SharedRoomPlan(val enabled: Boolean = true, val decaySeconds: Double = 0.65, val mix: Double = 0.18) {
    fun requireValid() {
        require(decaySeconds.isFinite() && decaySeconds in 0.1..4.0) { "Shared room decay is invalid" }
        require(mix.isFinite() && mix in 0.0..1.0) { "Shared room mix is invalid" }
    }
}

@Serializable
data class MixBusPlan(val enabled: Boolean = true, val gainDb: Double = 0.0, val compression: CompressionPlan = CompressionPlan()) {
    fun requireValid(bus: MixBus) {
        require(bus != MixBus.DIRECT && gainDb.isFinite() && gainDb in -24.0..18.0) { "Bus '$bus' gain is invalid" }
        compression.requireValid()
    }
}
