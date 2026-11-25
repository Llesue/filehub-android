package filehub.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxSupportTest {
    private val theme = SyntaxTheme(
        key = "test",
        code = 0xFF000000.toInt(),
        keyword = 0xFFFF0000.toInt(),
        string = 0xFF00AA00.toInt(),
        literal = 0xFF0066CC.toInt(),
        comment = 0xFF888888.toInt(),
        metadata = 0xFF6633CC.toInt(),
        multilineComment = 0xFF888888.toInt(),
        punctuation = 0xFF333333.toInt(),
        mark = 0xFF000000.toInt(),
    )

    @Test
    fun mapsCommonExtensions() {
        assertEquals(SyntaxLanguage.KOTLIN, syntaxLanguageFor("Main.kt"))
        assertEquals(SyntaxLanguage.JAVA, syntaxLanguageFor("App.java"))
        assertEquals(SyntaxLanguage.PYTHON, syntaxLanguageFor("script.py"))
        assertEquals(SyntaxLanguage.TYPESCRIPT, syntaxLanguageFor("view.tsx"))
        assertEquals(SyntaxLanguage.DEFAULT, syntaxLanguageFor("notes.md"))
        assertEquals(SyntaxLanguage.DEFAULT, syntaxLanguageFor("data.json"))
        assertEquals(CustomSyntax.JSON, customSyntaxFor("data.json"))
        assertEquals(CustomSyntax.XML, customSyntaxFor("layout.xml"))
        assertEquals(CustomSyntax.HTML, customSyntaxFor("index.html"))
        assertEquals(CustomSyntax.CSS, customSyntaxFor("theme.css"))
        assertEquals(CustomSyntax.SQL, customSyntaxFor("query.sql"))
        assertTrue(isCodeFile("main.rs"))
        assertTrue(isCodeFile("data.json"))
        assertTrue(isCodeFile("query.sql"))
        assertTrue(!isCodeFile("readme.txt"))
    }

    @Test
    fun jsonMarksKeysStringsAndLiterals() {
        val source = """{"name": "demo", "count": 2, "ok": true, "none": null}"""
        val tokens = tokenizeCustom(source, CustomSyntax.JSON)
        val key = tokens.first { source.substring(it.start, it.end) == "\"name\"" }
        assertEquals(SyntaxTokenType.Metadata, key.type)
        val value = tokens.first { source.substring(it.start, it.end) == "\"demo\"" }
        assertEquals(SyntaxTokenType.String, value.type)
        assertTrue(tokens.any { source.substring(it.start, it.end) == "true" })
        assertTrue(tokens.any { source.substring(it.start, it.end) == "null" })
    }

    @Test
    fun markupMarksTagsAttributesAndComments() {
        val source = """<root id="1"><!-- note --><child>text</child></root>"""
        val tokens = tokenizeCustom(source, CustomSyntax.XML)
        assertTrue(
            tokens.any {
                source.substring(it.start, it.end) == "root" &&
                    it.type == SyntaxTokenType.Keyword
            },
        )
        assertTrue(
            tokens.any {
                source.substring(it.start, it.end) == "id" &&
                    it.type == SyntaxTokenType.Metadata
            },
        )
        assertTrue(
            tokens.any {
                source.substring(it.start, it.end) == "\"1\"" &&
                    it.type == SyntaxTokenType.String
            },
        )
        assertTrue(
            tokens.any {
                source.substring(it.start, it.end) == "<!-- note -->" &&
                    it.type == SyntaxTokenType.Comment
            },
        )
    }

    @Test
    fun cssMarksPropertiesAtRulesAndComments() {
        val source = """@media screen { .card { color: #fff; margin: 4px; } } /* done */"""
        val tokens = tokenizeCustom(source, CustomSyntax.CSS)
        assertTrue(tokens.any { source.substring(it.start, it.end) == "@media" })
        assertTrue(tokens.any { source.substring(it.start, it.end) == "color" })
        assertTrue(tokens.any { source.substring(it.start, it.end) == "margin" })
        assertTrue(
            tokens.any {
                source.substring(it.start, it.end) == "/* done */" &&
                    it.type == SyntaxTokenType.Comment
            },
        )
    }

    @Test
    fun sqlMarksKeywordsStringsAndComments() {
        val source = """SELECT id FROM users WHERE name = 'Ada' -- note"""
        val tokens = tokenizeCustom(source, CustomSyntax.SQL)
        assertTrue(tokens.any { source.substring(it.start, it.end) == "SELECT" })
        assertTrue(tokens.any { source.substring(it.start, it.end) == "FROM" })
        assertTrue(tokens.any { source.substring(it.start, it.end) == "'Ada'" })
        assertTrue(tokens.any { source.substring(it.start, it.end) == "-- note" })
    }

    @Test
    fun customSyntaxProducesColourSpans() {
        val source = """{"key": "value"}"""
        val annotated = buildHighlightedText(
            text = source,
            language = SyntaxLanguage.DEFAULT,
            theme = theme,
            customSyntax = CustomSyntax.JSON,
        )
        assertEquals(source, annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.color == Color(0xFF6633CC) })
        assertTrue(annotated.spanStyles.any { it.item.color == Color(0xFF00AA00) })
    }

    @Test
    fun highlightKeepsTheTextAndAddsColourSpans() {
        val source = "package demo\n\nfun main() { println(\"hi\") }\n"
        val annotated = buildHighlightedText(source, SyntaxLanguage.KOTLIN, theme)
        assertEquals(source, annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
        assertTrue(
            annotated.spanStyles.any {
                it.item.color == Color(0xFFFF0000) || it.item.color == Color(0xFF00AA00)
            },
        )
    }

    @Test
    fun searchSpansAreAppliedWithoutChangingOffsets() {
        val source = "fun main() { val value = 1 }"
        val matches = findTextMatches(source, "value")
        val annotated = buildHighlightedText(
            text = source,
            language = SyntaxLanguage.KOTLIN,
            theme = theme,
            matches = matches,
            currentMatch = 0,
            matchColor = Color(0x33FF0000),
            currentMatchColor = Color(0x66FF0000),
        )
        assertEquals(source, annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.background == Color(0x66FF0000) })
    }

    @Test
    fun customThemeUsesArgb() {
        assertEquals(theme.keyword, Color(0xFFFF0000).toArgb())
    }
}
