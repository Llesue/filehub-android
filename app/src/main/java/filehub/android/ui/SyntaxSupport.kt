package filehub.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme
import filehub.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Bundled JetBrains Mono (SIL OFL 1.1), see res/raw/jetbrains_mono_ofl.txt. */
val CodeFontFamily = FontFamily(Font(R.font.jetbrains_mono))

fun syntaxLanguageFor(name: String): SyntaxLanguage {
    val extension = name.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "kt", "kts" -> SyntaxLanguage.KOTLIN
        "java" -> SyntaxLanguage.JAVA
        "py", "pyw" -> SyntaxLanguage.PYTHON
        "js", "mjs", "cjs", "jsx" -> SyntaxLanguage.JAVASCRIPT
        "ts", "tsx", "mts", "cts" -> SyntaxLanguage.TYPESCRIPT
        "c", "h" -> SyntaxLanguage.C
        "cc", "cpp", "cxx", "hpp", "hh", "hxx" -> SyntaxLanguage.CPP
        "rs" -> SyntaxLanguage.RUST
        "go" -> SyntaxLanguage.GO
        "sh", "bash", "zsh", "fish" -> SyntaxLanguage.SHELL
        "php" -> SyntaxLanguage.PHP
        "rb" -> SyntaxLanguage.RUBY
        "swift" -> SyntaxLanguage.SWIFT
        "cs" -> SyntaxLanguage.CSHARP
        "dart" -> SyntaxLanguage.DART
        "pl", "pm" -> SyntaxLanguage.PERL
        "coffee" -> SyntaxLanguage.COFFEESCRIPT
        else -> SyntaxLanguage.DEFAULT
    }
}

/** A code file is anything we have a real grammar for; `.txt` and JSON stay plain. */
fun isCodeFile(name: String): Boolean = syntaxLanguageFor(name) != SyntaxLanguage.DEFAULT

fun buildHighlightedText(
    text: String,
    language: SyntaxLanguage,
    theme: SyntaxTheme,
    matches: List<TextMatch> = emptyList(),
    currentMatch: Int = -1,
    matchColor: Color = Color.Transparent,
    currentMatchColor: Color = Color.Transparent,
): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    if (language != SyntaxLanguage.DEFAULT && text.isNotEmpty()) {
        val highlights = runCatching {
            Highlights.Builder()
                .code(text)
                .language(language)
                .theme(theme)
                .build()
                .getHighlights()
        }.getOrDefault(emptyList())
        for (highlight in highlights) {
            if (highlight !is ColorHighlight) continue
            val start = highlight.location.start.coerceIn(0, text.length)
            val end = highlight.location.end.coerceIn(start, text.length)
            if (start == end) continue
            builder.addStyle(SpanStyle(color = Color(highlight.rgb)), start, end)
        }
    }
    applySearchSpans(builder, text.length, matches, currentMatch, matchColor, currentMatchColor)
    return builder.toAnnotatedString()
}

private fun applySearchSpans(
    builder: AnnotatedString.Builder,
    length: Int,
    matches: List<TextMatch>,
    currentMatch: Int,
    matchColor: Color,
    currentMatchColor: Color,
) {
    matches.forEachIndexed { index, match ->
        val start = match.start.coerceIn(0, length)
        val end = match.end.coerceIn(start, length)
        if (start == end) return@forEachIndexed
        val color = if (index == currentMatch) currentMatchColor else matchColor
        if (color != Color.Transparent) {
            builder.addStyle(SpanStyle(background = color), start, end)
        }
    }
}

/**
 * Highlights [text] off the main thread. The editor debounces keystrokes; the
 * preview asks for an immediate pass. Search spans are applied synchronously on
 * top of the cached syntax result so moving between matches stays cheap.
 */
@Composable
fun rememberHighlightedText(
    text: String,
    language: SyntaxLanguage,
    theme: SyntaxTheme,
    enabled: Boolean = true,
    debounceMs: Long = 0,
    matches: List<TextMatch> = emptyList(),
    currentMatch: Int = -1,
    matchColor: Color = Color.Transparent,
    currentMatchColor: Color = Color.Transparent,
): AnnotatedString {
    val syntax = produceState(
        initialValue = AnnotatedString(text),
        text,
        language,
        theme,
        enabled,
    ) {
        if (!enabled || text.isEmpty()) {
            value = AnnotatedString(text)
            return@produceState
        }
        if (debounceMs > 0) delay(debounceMs)
        value = withContext(Dispatchers.Default) {
            buildHighlightedText(text, language, theme)
        }
    }
    return remember(syntax.value, text, matches, currentMatch, matchColor, currentMatchColor) {
        when {
            syntax.value.text != text -> AnnotatedString(text)
            matches.isEmpty() -> syntax.value
            else -> AnnotatedString.Builder().apply {
                append(syntax.value)
                applySearchSpans(this, text.length, matches, currentMatch, matchColor, currentMatchColor)
            }.toAnnotatedString()
        }
    }
}
