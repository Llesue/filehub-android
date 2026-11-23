package filehub.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSearchTest {
    @Test
    fun findsEveryOccurrenceCaseInsensitively() {
        val text = "Hello hello HELLO"
        val matches = findTextMatches(text, "hello")
        assertEquals(3, matches.size)
        assertEquals(0, matches[0].start)
        assertEquals(6, matches[1].start)
        assertEquals(12, matches[2].start)
        assertEquals("Hello", text.substring(matches[0].start, matches[0].end))
    }

    @Test
    fun blankQueryReturnsNothing() {
        assertEquals(emptyList<TextMatch>(), findTextMatches("abc", ""))
        assertEquals(emptyList<TextMatch>(), findTextMatches("", "a"))
    }

    @Test
    fun stepWrapsAround() {
        assertEquals(1, stepMatch(0, 3, true))
        assertEquals(0, stepMatch(2, 3, true))
        assertEquals(2, stepMatch(0, 3, false))
        assertEquals(-1, stepMatch(0, 0, true))
        assertEquals(0, stepMatch(-1, 3, true))
        assertEquals(2, stepMatch(-1, 3, false))
    }
}
