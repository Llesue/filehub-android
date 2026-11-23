package filehub.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {
    private fun snapshot(text: String, cursor: Int = text.length) =
        EditorSnapshot(text, cursor, cursor)

    @Test
    fun consecutiveTypingCoalescesIntoOneStep() {
        val history = EditorHistory(coalesceMs = 600)
        history.record(snapshot("a"), 1_000)
        history.record(snapshot("ab"), 1_100)
        history.record(snapshot("abc"), 1_200)
        assertEquals("a", history.undo(snapshot("abcd"))?.text)
        assertNull(history.undo(snapshot("a")))
    }

    @Test
    fun pausesCreateSeparateSteps() {
        val history = EditorHistory(coalesceMs = 500)
        history.record(snapshot("a"), 1_000)
        history.record(snapshot("ab"), 1_700)
        assertEquals("ab", history.undo(snapshot("abc"))?.text)
        assertEquals("a", history.undo(snapshot("ab"))?.text)
    }

    @Test
    fun redoRestoresTheUndoneState() {
        val history = EditorHistory()
        history.record(snapshot("first"), 1_000)
        val undone = history.undo(snapshot("second"))
        assertEquals("first", undone?.text)
        val redone = history.redo(snapshot("first"))
        assertEquals("second", redone?.text)
        assertFalse(history.canRedo)
    }

    @Test
    fun veryLargeDocumentsAreNotRecorded() {
        val history = EditorHistory(maxTextLength = 8)
        history.record(snapshot("123456789"), 1_000)
        assertFalse(history.canUndo)
    }

    @Test
    fun stackIsBounded() {
        val history = EditorHistory(limit = 3, coalesceMs = 0)
        repeat(6) { index -> history.record(snapshot("v$index"), index * 1_000L) }
        var current = snapshot("v6")
        val seen = mutableListOf<String>()
        while (true) {
            val previous = history.undo(current) ?: break
            seen += previous.text
            current = previous
        }
        assertTrue(seen.size <= 3)
    }
}
