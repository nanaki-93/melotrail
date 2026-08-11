package ai.music.workstation.audio

import kotlinx.serialization.Serializable

@Serializable
data class AudioFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
    val isFloat: Boolean,
    val isBigEndian: Boolean,
    val encoding: String = "PCM"
) {
    val isWav: Boolean get() = encoding == "WAV"
    val isMp3: Boolean get() = encoding == "MP3"
    val isFlac: Boolean get() = encoding == "FLAC"
    val isOgg: Boolean get() = encoding == "OGG"
    val isAiff: Boolean get() = encoding == "AIFF"
    val isStereo: Boolean get() = channels == 2
    val isMono: Boolean get() = channels == 1

    fun description(): String = buildString {
        append("$sampleRate Hz, $channels ch, $bitDepth-bit")
        if (isFloat) append(", float")
        if (isBigEndian) append(", big-endian")
        append(" ($encoding)")
    }
}

object AudioFormatFactory {
    fun wav(
        sampleRate: Int = 44100,
        channels: Int = 2,
        bitDepth: Int = 16,
        isBigEndian: Boolean = false
    ): AudioFormat = AudioFormat(
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth,
        isFloat = bitDepth == 32,
        isBigEndian = isBigEndian,
        encoding = "WAV"
    )

    fun mp3(
        sampleRate: Int = 44100,
        channels: Int = 2,
        bitDepth: Int = 16
    ): AudioFormat = AudioFormat(
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth,
        isFloat = false,
        isBigEndian = false,
        encoding = "MP3"
    )

    fun flac(
        sampleRate: Int = 44100,
        channels: Int = 2,
        bitDepth: Int = 24
    ): AudioFormat = AudioFormat(
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth,
        isFloat = false,
        isBigEndian = false,
        encoding = "FLAC"
    )

    fun pcmFloat(
        sampleRate: Int = 48000,
        channels: Int = 2
    ): AudioFormat = AudioFormat(
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = 32,
        isFloat = true,
        isBigEndian = false,
        encoding = "PCM_FLOAT"
    )
}
