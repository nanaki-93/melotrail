package ai.music.workstation.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MusicWorkspaceTokens {
    val Canvas = Color(0xFF090F14)
    val Surface = Color(0xFF111B23)
    val ElevatedSurface = Color(0xFF17242D)
    val Border = Color(0xFF263741)
    val Teal = Color(0xFF47D7C5)
    val Piano = Color(0xFF55C8C2)
    val Bass = Color(0xFF7BC96F)
    val Drums = Color(0xFFF1AE46)
    val Pad = Color(0xFFA88BE8)
    val Strings = Color(0xFFF27958)
}

val instrumentLaneColors = mapOf(
    "piano" to MusicWorkspaceTokens.Piano,
    "bass" to MusicWorkspaceTokens.Bass,
    "drums" to MusicWorkspaceTokens.Drums,
    "pad" to MusicWorkspaceTokens.Pad,
    "strings" to MusicWorkspaceTokens.Strings
)

private val musicColorScheme = darkColorScheme(
    primary = MusicWorkspaceTokens.Teal,
    onPrimary = MusicWorkspaceTokens.Canvas,
    background = MusicWorkspaceTokens.Canvas,
    onBackground = Color(0xFFE2EDF1),
    surface = MusicWorkspaceTokens.Surface,
    onSurface = Color(0xFFE2EDF1),
    surfaceVariant = MusicWorkspaceTokens.ElevatedSurface,
    outline = MusicWorkspaceTokens.Border,
    error = Color(0xFFFFB4AB)
)

@Composable
fun MusicWorkstationTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = musicColorScheme, content = content)
}
