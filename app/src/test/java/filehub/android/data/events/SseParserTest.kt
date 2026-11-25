package filehub.android.data.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    private fun parseAll(vararg chunks: String): List<SseEventFrame> {
        val parser = SseParser()
        return chunks.flatMap(parser::feed)
    }

    @Test
    fun parsesSingleLineEvent() {
        val events = parseAll("id: 7\nevent: usage.updated\ndata: {\"used_bytes\":1}\n\n")

        assertEquals(1, events.size)
        assertEquals("7", events.single().id)
        assertEquals("usage.updated", events.single().name)
        assertEquals("{\"used_bytes\":1}", events.single().data)
    }

    @Test
    fun joinsMultipleDataLinesWithNewline() {
        val events = parseAll("event: note\ndata: first\ndata: second\n\n")

        assertEquals("note", events.single().name)
        assertEquals("first\nsecond", events.single().data)
    }

    @Test
    fun ignoresCommentHeartbeatAndUnknownFields() {
        val events = parseAll(": keep-alive\nretry: 3000\nfoo: bar\nevent: ping\ndata: ok\n\n")

        assertEquals(1, events.size)
        assertEquals("ping", events.single().name)
        assertEquals("ok", events.single().data)
    }

    @Test
    fun handlesCrlfLineEndings() {
        val events = parseAll("event: ping\r\ndata: ok\r\n\r\n")

        assertEquals(1, events.size)
        assertEquals("ping", events.single().name)
        assertEquals("ok", events.single().data)
    }

    @Test
    fun handlesChunksSplitInsideLineAndField() {
        val parser = SseParser()

        assertTrue(parser.feed("id: 1\nevent: usage").isEmpty())
        assertTrue(parser.feed(".updated\ndata: {\"a\":").isEmpty())
        val events = parser.feed("1}\n\n")

        assertEquals(1, events.size)
        assertEquals("usage.updated", events.single().name)
        assertEquals("{\"a\":1}", events.single().data)
    }

    @Test
    fun dispatchesEventWithoutData() {
        val events = parseAll("event: resync\n\n")

        assertEquals(1, events.size)
        assertEquals("resync", events.single().name)
        assertEquals("", events.single().data)
    }

    @Test
    fun ignoresBlankBlocks() {
        assertTrue(parseAll("\n\n\n").isEmpty())
    }

    @Test
    fun rejectsOversizedEvents() {
        val parser = SseParser(maxDataChars = 8)

        assertThrows(SseParseException::class.java) {
            parser.feed("data: 123456789\n\n")
        }
    }

    @Test
    fun rejectsOversizedLines() {
        val parser = SseParser(maxLineChars = 8)

        assertThrows(SseParseException::class.java) {
            parser.feed("data: 123456789")
        }
    }
}
