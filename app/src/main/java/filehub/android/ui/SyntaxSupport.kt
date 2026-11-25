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

/** Languages the bundled grammar does not cover, handled by the local lexers below. */
enum class CustomSyntax {
    JSON,
    XML,
    HTML,
    CSS,
    SQL,
    ;
}

fun customSyntaxFor(name: String): CustomSyntax? {
    val extension = name.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "json" -> CustomSyntax.JSON
        "xml", "xsd", "xsl", "xslt", "svg" -> CustomSyntax.XML
        "html", "htm", "xhtml" -> CustomSyntax.HTML
        "css" -> CustomSyntax.CSS
        "sql" -> CustomSyntax.SQL
        else -> null
    }
}

/** A code file is anything we have a real grammar for; `.txt` stays plain. */
fun isCodeFile(name: String): Boolean =
    syntaxLanguageFor(name) != SyntaxLanguage.DEFAULT || customSyntaxFor(name) != null

internal enum class SyntaxTokenType {
    Keyword,
    String,
    Literal,
    Comment,
    Metadata,
    Punctuation,
}

internal data class SyntaxToken(
    val start: Int,
    val end: Int,
    val type: SyntaxTokenType,
)

internal fun tokenizeCustom(text: String, syntax: CustomSyntax): List<SyntaxToken> = buildList {
    val sink = SyntaxSink(this)
    when (syntax) {
        CustomSyntax.JSON -> scanJson(text, sink)
        CustomSyntax.XML, CustomSyntax.HTML -> scanMarkup(text, sink)
        CustomSyntax.CSS -> scanCss(text, sink)
        CustomSyntax.SQL -> scanSql(text, sink)
    }
}

private class SyntaxSink(private val tokens: MutableList<SyntaxToken>) {
    fun add(start: Int, end: Int, type: SyntaxTokenType) {
        if (end > start) tokens += SyntaxToken(start, end, type)
    }
}

private fun isNameChar(c: Char): Boolean =
    c.isLetterOrDigit() || c == '_' || c == '-' || c == ':' || c == '.'

private fun scanQuoted(text: String, start: Int): Int {
    val quote = text[start]
    var index = start + 1
    while (index < text.length) {
        val c = text[index]
        if (c == '\\' && index + 1 < text.length) {
            index += 2
            continue
        }
        if (c == quote) return index + 1
        if (c == '\n' && quote != '`') return index
        index++
    }
    return text.length
}

private fun scanJson(text: String, sink: SyntaxSink) {
    var index = 0
    while (index < text.length) {
        val c = text[index]
        when {
            c == '"' -> {
                val end = scanQuoted(text, index)
                var next = end
                while (next < text.length && text[next].isWhitespace()) next++
                val type = if (next < text.length && text[next] == ':') {
                    SyntaxTokenType.Metadata
                } else {
                    SyntaxTokenType.String
                }
                sink.add(index, end, type)
                index = end
            }
            c == '/' && index + 1 < text.length && text[index + 1] == '/' -> {
                val end = text.indexOf('\n', index).let { if (it < 0) text.length else it }
                sink.add(index, end, SyntaxTokenType.Comment)
                index = end
            }
            c == '/' && index + 1 < text.length && text[index + 1] == '*' -> {
                val close = text.indexOf("*/", index + 2)
                val end = if (close < 0) text.length else close + 2
                sink.add(index, end, SyntaxTokenType.Comment)
                index = end
            }
            c == '-' || c.isDigit() -> {
                val end = scanNumber(text, index)
                if (end > index) {
                    sink.add(index, end, SyntaxTokenType.Literal)
                    index = end
                } else {
                    index++
                }
            }
            c.isLetter() -> {
                var end = index + 1
                while (end < text.length && text[end].isLetter()) end++
                if (text.substring(index, end) in setOf("true", "false", "null")) {
                    sink.add(index, end, SyntaxTokenType.Literal)
                }
                index = end
            }
            c in "{}[],:" -> {
                sink.add(index, index + 1, SyntaxTokenType.Punctuation)
                index++
            }
            else -> index++
        }
    }
}

private fun scanMarkup(text: String, sink: SyntaxSink) {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("<!--", index) -> {
                val close = text.indexOf("-->", index + 4)
                val end = if (close < 0) text.length else close + 3
                sink.add(index, end, SyntaxTokenType.Comment)
                index = end
            }
            text.startsWith("<![CDATA[", index) -> {
                val close = text.indexOf("]]>", index + 9)
                val end = if (close < 0) text.length else close + 3
                sink.add(index, end, SyntaxTokenType.String)
                index = end
            }
            text[index] == '<' -> {
                var cursor = index + 1
                if (cursor < text.length && (text[cursor] == '/' || text[cursor] == '!' ||
                        text[cursor] == '?')
                ) {
                    cursor++
                }
                val nameStart = cursor
                while (cursor < text.length && isNameChar(text[cursor])) cursor++
                if (cursor > nameStart) {
                    sink.add(nameStart, cursor, SyntaxTokenType.Keyword)
                }
                while (cursor < text.length && text[cursor] != '>') {
                    val c = text[cursor]
                    when {
                        c == '"' || c == '\'' -> {
                            val end = scanQuoted(text, cursor)
                            sink.add(cursor, end, SyntaxTokenType.String)
                            cursor = end
                        }
                        c.isWhitespace() || c == '=' || c == '/' || c == '?' || c == '<' -> {
                            cursor++
                        }
                        else -> {
                            val attrStart = cursor
                            while (cursor < text.length && isNameChar(text[cursor])) cursor++
                            if (cursor == attrStart) {
                                cursor++
                            } else {
                                sink.add(attrStart, cursor, SyntaxTokenType.Metadata)
                            }
                        }
                    }
                }
                if (cursor < text.length) {
                    sink.add(cursor, cursor + 1, SyntaxTokenType.Punctuation)
                    cursor++
                }
                index = cursor
            }
            text[index] == '&' -> {
                val close = text.indexOf(';', index + 1)
                if (close in (index + 2)..(index + 12)) {
                    sink.add(index, close + 1, SyntaxTokenType.Literal)
                    index = close + 1
                } else {
                    index++
                }
            }
            else -> index++
        }
    }
}

private fun scanCss(text: String, sink: SyntaxSink) {
    var index = 0
    var depth = 0
    var lastSignificant = '\u0000'
    while (index < text.length) {
        val c = text[index]
        when {
            text.startsWith("/*", index) -> {
                val close = text.indexOf("*/", index + 2)
                val end = if (close < 0) text.length else close + 2
                sink.add(index, end, SyntaxTokenType.Comment)
                index = end
            }
            c == '"' || c == '\'' -> {
                val end = scanQuoted(text, index)
                sink.add(index, end, SyntaxTokenType.String)
                index = end
            }
            c == '{' -> {
                sink.add(index, index + 1, SyntaxTokenType.Punctuation)
                depth++
                lastSignificant = c
                index++
            }
            c == '}' -> {
                sink.add(index, index + 1, SyntaxTokenType.Punctuation)
                depth = (depth - 1).coerceAtLeast(0)
                lastSignificant = c
                index++
            }
            c == ';' || c == ',' || c == '(' || c == ')' -> {
                sink.add(index, index + 1, SyntaxTokenType.Punctuation)
                lastSignificant = c
                index++
            }
            c == ':' -> {
                sink.add(index, index + 1, SyntaxTokenType.Punctuation)
                lastSignificant = c
                index++
            }
            c == '@' -> {
                var end = index + 1
                while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '-')) end++
                sink.add(index, end, SyntaxTokenType.Keyword)
                lastSignificant = 'x'
                index = end
            }
            c == '-' && index + 1 < text.length && text[index + 1] == '-' -> {
                var end = index + 2
                while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '-')) end++
                sink.add(index, end, SyntaxTokenType.Metadata)
                lastSignificant = 'x'
                index = end
            }
            c.isDigit() || (c == '.' && index + 1 < text.length && text[index + 1].isDigit()) -> {
                val end = scanNumber(text, index)
                var tail = end
                while (tail < text.length && (text[tail].isLetter() || text[tail] == '%')) tail++
                sink.add(index, tail, SyntaxTokenType.Literal)
                lastSignificant = '0'
                index = tail
            }
            c.isLetter() || c == '_' -> {
                var end = index + 1
                while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_' ||
                        text[end] == '-')
                ) {
                    end++
                }
                var lookahead = end
                while (lookahead < text.length && text[lookahead].isWhitespace()) lookahead++
                val isProperty = depth > 0 &&
                    (lastSignificant == '{' || lastSignificant == ';') &&
                    lookahead < text.length && text[lookahead] == ':'
                if (isProperty) {
                    sink.add(index, end, SyntaxTokenType.Keyword)
                }
                lastSignificant = 'x'
                index = end
            }
            else -> {
                if (!c.isWhitespace()) lastSignificant = c
                index++
            }
        }
    }
}

private val SqlKeywords = setOf(
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
    "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "VIEW", "TRIGGER", "JOIN", "LEFT",
    "RIGHT", "INNER", "OUTER", "FULL", "CROSS", "ON", "AS", "AND", "OR", "NOT",
    "NULL", "IS", "IN", "EXISTS", "BETWEEN", "LIKE", "GLOB", "GROUP", "BY", "ORDER",
    "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "CASE", "WHEN", "THEN",
    "ELSE", "END", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "DEFAULT",
    "CHECK", "CONSTRAINT", "DATABASE", "IF", "BEGIN", "COMMIT", "ROLLBACK",
    "TRANSACTION", "WITH", "RECURSIVE", "RETURNING", "ASC", "DESC", "EXPLAIN",
    "VACUUM", "PRAGMA", "REPLACE", "CAST", "INTERVAL",
)

private fun scanSql(text: String, sink: SyntaxSink) {
    var index = 0
    while (index < text.length) {
        val c = text[index]
        when {
            c == '-' && index + 1 < text.length && text[index + 1] == '-' -> {
                val end = text.indexOf('\n', index).let { if (it < 0) text.length else it }
                sink.add(index, end, SyntaxTokenType.Comment)
                index = end
            }
            c == '/' && index + 1 < text.length && text[index + 1] == '*' -> {
                val close = text.indexOf("*/", index + 2)
                val end = if (close < 0) text.length else close + 2
                sink.add(index, end, SyntaxTokenType.Comment)
                index = end
            }
            c == '\'' -> {
                var end = index + 1
                while (end < text.length) {
                    if (text[end] == '\'') {
                        if (end + 1 < text.length && text[end + 1] == '\'') {
                            end += 2
                            continue
                        }
                        end++
                        break
                    }
                    end++
                }
                sink.add(index, end, SyntaxTokenType.String)
                index = end
            }
            c == '"' || c == '`' -> {
                val end = scanQuoted(text, index)
                sink.add(index, end, SyntaxTokenType.Metadata)
                index = end
            }
            c == '[' -> {
                val close = text.indexOf(']', index + 1)
                if (close < 0) {
                    index++
                } else {
                    sink.add(index, close + 1, SyntaxTokenType.Metadata)
                    index = close + 1
                }
            }
            c == '@' -> {
                var end = index + 1
                while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
                sink.add(index, end, SyntaxTokenType.Metadata)
                index = end
            }
            c.isDigit() -> {
                val end = scanNumber(text, index)
                sink.add(index, end, SyntaxTokenType.Literal)
                index = end
            }
            c.isLetter() || c == '_' -> {
                var end = index + 1
                while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
                if (text.substring(index, end).uppercase() in SqlKeywords) {
                    sink.add(index, end, SyntaxTokenType.Keyword)
                }
                index = end
            }
            c in "(),;.*" -> {
                sink.add(index, index + 1, SyntaxTokenType.Punctuation)
                index++
            }
            else -> index++
        }
    }
}

private fun scanNumber(text: String, start: Int): Int {
    var index = start
    if (index < text.length && (text[index] == '-' || text[index] == '+')) index++
    var digits = false
    while (index < text.length && (text[index].isDigit() || text[index] == '.' ||
            text[index] == 'e' || text[index] == 'E' || text[index] == '+' || text[index] == '-')
    ) {
        if (text[index].isDigit()) digits = true
        index++
    }
    return if (digits) index else start
}

fun buildHighlightedText(
    text: String,
    language: SyntaxLanguage,
    theme: SyntaxTheme,
    matches: List<TextMatch> = emptyList(),
    currentMatch: Int = -1,
    matchColor: Color = Color.Transparent,
    currentMatchColor: Color = Color.Transparent,
    customSyntax: CustomSyntax? = null,
): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    if (text.isNotEmpty()) {
        if (customSyntax != null) {
            for (token in tokenizeCustom(text, customSyntax)) {
                val start = token.start.coerceIn(0, text.length)
                val end = token.end.coerceIn(start, text.length)
                if (start == end) continue
                builder.addStyle(
                    SpanStyle(color = customTokenColor(token.type, theme)),
                    start,
                    end,
                )
            }
        } else if (language != SyntaxLanguage.DEFAULT) {
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
    }
    applySearchSpans(builder, text.length, matches, currentMatch, matchColor, currentMatchColor)
    return builder.toAnnotatedString()
}

private fun customTokenColor(type: SyntaxTokenType, theme: SyntaxTheme): Color = Color(
    when (type) {
        SyntaxTokenType.Keyword -> theme.keyword
        SyntaxTokenType.String -> theme.string
        SyntaxTokenType.Literal -> theme.literal
        SyntaxTokenType.Comment -> theme.comment
        SyntaxTokenType.Metadata -> theme.metadata
        SyntaxTokenType.Punctuation -> theme.punctuation
    },
)

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
    customSyntax: CustomSyntax? = null,
): AnnotatedString {
    val syntax = produceState(
        initialValue = AnnotatedString(text),
        text,
        language,
        customSyntax,
        theme,
        enabled,
    ) {
        if (!enabled || text.isEmpty()) {
            value = AnnotatedString(text)
            return@produceState
        }
        if (debounceMs > 0) delay(debounceMs)
        value = withContext(Dispatchers.Default) {
            buildHighlightedText(text, language, theme, customSyntax = customSyntax)
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
