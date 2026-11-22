package filehub.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = FileHubBlue,
    onPrimary = Color.White,
    primaryContainer = FileHubAccentSoft,
    onPrimaryContainer = Color(0xFF1967D2),
    background = FileHubBg,
    onBackground = FileHubText,
    surface = FileHubSurface,
    onSurface = FileHubText,
    surfaceVariant = FileHubSurface2,
    onSurfaceVariant = FileHubText2,
    outline = FileHubBorder,
    error = FileHubDanger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = FileHubBlueDark,
    onPrimary = FileHubText,
    primaryContainer = FileHubAccentSoftDark,
    onPrimaryContainer = FileHubBlueDark,
    background = FileHubBgDark,
    onBackground = FileHubTextDark,
    surface = FileHubSurfaceDark,
    onSurface = FileHubTextDark,
    surfaceVariant = FileHubSurface2Dark,
    onSurfaceVariant = FileHubText2Dark,
    outline = FileHubBorderDark,
    error = FileHubDangerDark,
    onError = FileHubBgDark,
)

@Composable
fun FileHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
