package dev.zenda.inputbridge.lab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightScheme = darkColorScheme(
    primary = Color(0xFF6DE7D0),
    onPrimary = Color(0xFF062A25),
    primaryContainer = Color(0xFF174B45),
    onPrimaryContainer = Color(0xFFB8FFF2),
    secondary = Color(0xFFA8BBFF),
    onSecondary = Color(0xFF17295D),
    secondaryContainer = Color(0xFF293C70),
    onSecondaryContainer = Color(0xFFDCE3FF),
    tertiary = Color(0xFFD0AEFF),
    onTertiary = Color(0xFF352052),
    background = Color(0xFF090F1D),
    onBackground = Color(0xFFEAF1FF),
    surface = Color(0xFF101A2B),
    onSurface = Color(0xFFEAF1FF),
    surfaceVariant = Color(0xFF1A2940),
    onSurfaceVariant = Color(0xFFB8C6DE),
    outline = Color(0xFF465775),
    error = Color(0xFFFF8A9A),
    onError = Color(0xFF4B0715)
)

private val DayScheme = lightColorScheme(
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB1F2E7),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF435AA9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE1FF),
    onSecondaryContainer = Color(0xFF101E5B),
    tertiary = Color(0xFF6B438B),
    onTertiary = Color.White,
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF151B28),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151B28),
    surfaceVariant = Color(0xFFE8EDF6),
    onSurfaceVariant = Color(0xFF424D62),
    outline = Color(0xFF77839A),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun InputBridgeTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NightScheme else DayScheme,
        content = content
    )
}
