package ai.music.workstation.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object MusicWorkspaceTokens {
    val Canvas = Color(0xFF071017)
    val Surface = Color(0xFF0D1821)
    val ElevatedSurface = Color(0xFF12212B)
    val Border = Color(0xFF253845)
    val Teal = Color(0xFF4BD7C3)
    val Piano = Color(0xFF59CCC4)
    val Bass = Color(0xFF86C979)
    val Drums = Color(0xFFF0B356)
    val Pad = Color(0xFFAB91EB)
    val Strings = Color(0xFFF08262)
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

private val workspaceShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp)
)

@Composable
fun MusicWorkstationTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = musicColorScheme, shapes = workspaceShapes, content = content)
}
