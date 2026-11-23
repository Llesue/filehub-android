package filehub.android.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.snipme.highlights.model.SyntaxTheme

enum class EditorThemeMode(val key: String) {
    AUTO("auto"),
    LIGHT("light"),
    SEPIA("sepia"),
    ;

    companion object {
        fun fromKey(key: String?): EditorThemeMode =
            entries.firstOrNull { it.key == key } ?: AUTO
    }
}

object EditorThemePreference {
    private const val FILE = "editor_preferences"
    private const val KEY = "theme"

    fun read(context: Context): EditorThemeMode =
        EditorThemeMode.fromKey(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null),
        )

    fun write(context: Context, mode: EditorThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, mode.key)
            .apply()
    }
}

/**
 * Colours for the editor surface and the syntax highlighter. The app theme
 * still owns the chrome (top bar, dialogs); only the document area follows
 * this palette so a sepia page can sit inside a dark app.
 */
data class EditorPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val gutter: Color,
    val cursor: Color,
    val activeLine: Color,
    val syntax: SyntaxTheme,
)

@Composable
fun rememberEditorPalette(mode: EditorThemeMode): EditorPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(mode, dark) {
        when (mode) {
            EditorThemeMode.AUTO -> if (dark) darkPalette() else lightPalette()
            EditorThemeMode.LIGHT -> lightPalette()
            EditorThemeMode.SEPIA -> sepiaPalette()
        }
    }
}

private fun syntaxTheme(
    key: String,
    code: Color,
    keyword: Color,
    string: Color,
    literal: Color,
    comment: Color,
    metadata: Color,
    punctuation: Color,
) = SyntaxTheme(
    key = key,
    code = code.toArgb(),
    keyword = keyword.toArgb(),
    string = string.toArgb(),
    literal = literal.toArgb(),
    comment = comment.toArgb(),
    metadata = metadata.toArgb(),
    multilineComment = comment.toArgb(),
    punctuation = punctuation.toArgb(),
    mark = code.toArgb(),
)

private fun lightPalette() = EditorPalette(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F7F9),
    text = Color(0xFF1B1F24),
    muted = Color(0xFF6A737D),
    gutter = Color(0xFF9AA4B2),
    cursor = Color(0xFF3B6EF5),
    activeLine = Color(0x0F3B6EF5),
    syntax = syntaxTheme(
        key = "filehub-light",
        code = Color(0xFF24292F),
        keyword = Color(0xFFCF222E),
        string = Color(0xFF0A3069),
        literal = Color(0xFF0550AE),
        comment = Color(0xFF6E7781),
        metadata = Color(0xFF8250DF),
        punctuation = Color(0xFF57606A),
    ),
)

private fun darkPalette() = EditorPalette(
    background = Color(0xFF121418),
    surface = Color(0xFF1B1E24),
    text = Color(0xFFE6EDF3),
    muted = Color(0xFF9AA4B2),
    gutter = Color(0xFF5B6472),
    cursor = Color(0xFF8AB4FF),
    activeLine = Color(0x1F8AB4FF),
    syntax = syntaxTheme(
        key = "filehub-dark",
        code = Color(0xFFE6EDF3),
        keyword = Color(0xFFFF7B72),
        string = Color(0xFFA5D6FF),
        literal = Color(0xFF79C0FF),
        comment = Color(0xFF8B949E),
        metadata = Color(0xFFD2A8FF),
        punctuation = Color(0xFFC9D1D9),
    ),
)

private fun sepiaPalette() = EditorPalette(
    background = Color(0xFFF4ECD8),
    surface = Color(0xFFEDE3CD),
    text = Color(0xFF5B4636),
    muted = Color(0xFF8A7A63),
    gutter = Color(0xFFA4937A),
    cursor = Color(0xFF8B5E34),
    activeLine = Color(0x148B5E34),
    syntax = syntaxTheme(
        key = "filehub-sepia",
        code = Color(0xFF5B4636),
        keyword = Color(0xFFA0522D),
        string = Color(0xFF2E7D32),
        literal = Color(0xFFB26A00),
        comment = Color(0xFF9A8A70),
        metadata = Color(0xFF7B5EA7),
        punctuation = Color(0xFF7A6A55),
    ),
)
