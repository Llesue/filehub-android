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
        assertTrue(isCodeFile("main.rs"))
        assertTrue(!isCodeFile("readme.txt"))
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
