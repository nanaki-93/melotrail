package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

sealed class DSPEffect {
    abstract fun process(input: FloatArray): FloatArray
    abstract fun getSettings(): DSPSettings
}
