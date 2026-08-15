package app.melotrail.dsp

import app.melotrail.model.DSPSettings

sealed class DSPEffect {
    abstract fun process(input: FloatArray): FloatArray
    abstract fun getSettings(): DSPSettings
}
