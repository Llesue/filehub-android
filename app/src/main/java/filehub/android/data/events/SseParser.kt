package filehub.android.data.events

/** A single dispatched SSE event, before it is attached to a profile. */
data class SseEventFrame(
    val id: String?,
    val name: String,
    val data: String,
)

class SseParseException(message: String) : Exception(message)

/**
 * Incremental Server-Sent Events parser.
 *
 * The transport reads one line at a time and calls [consumeLine]; [feed] is
 * available for callers that receive arbitrary chunks. Both paths share the
 * same event buffer, so a chunk boundary can split a field or a line.
 */
class SseParser(
    private val maxDataChars: Int = DEFAULT_MAX_DATA_CHARS,
    private val maxLineChars: Int = DEFAULT_MAX_LINE_CHARS,
) {
    private val data = StringBuilder()
    private val pendingLine = StringBuilder()
    private var eventName: String? = null
    private var eventId: String? = null

    /** Feeds a chunk and returns every event completed by it. */
    fun feed(chunk: CharSequence): List<SseEventFrame> {
        val events = mutableListOf<SseEventFrame>()
        for (index in chunk.indices) {
            val char = chunk[index]
            if (char == '\n') {
                val line = pendingLine.toString().removeSuffix("\r")
                pendingLine.setLength(0)
                consumeLine(line)?.let(events::add)
            } else {
                pendingLine.append(char)
                if (pendingLine.length > maxLineChars) {
                    throw SseParseException("SSE line exceeds $maxLineChars characters")
                }
            }
        }
        return events
    }

    /** Processes one line without its trailing newline. */
    fun consumeLine(line: String): SseEventFrame? {
        val text = line.removeSuffix("\r")
        if (text.isEmpty()) return dispatch()
        if (text.startsWith(":")) return null

        val colon = text.indexOf(':')
        val field = if (colon < 0) text else text.substring(0, colon)
        var value = if (colon < 0) "" else text.substring(colon + 1)
        if (value.startsWith(' ')) value = value.substring(1)

        when (field) {
            "data" -> {
                if (data.length + value.length + 1 > maxDataChars) {
                    throw SseParseException("SSE event exceeds $maxDataChars characters")
                }
                data.append(value).append('\n')
            }
            "event" -> eventName = value
            "id" -> if (!value.contains('\u0000')) eventId = value
            // retry and unknown fields are intentionally ignored.
        }
        return null
    }

    private fun dispatch(): SseEventFrame? {
        if (data.isEmpty() && eventName == null) return null
        val payload = if (data.isEmpty()) {
            ""
        } else {
            data.setLength(data.length - 1)
            data.toString()
        }
        val frame = SseEventFrame(
            id = eventId,
            name = eventName ?: "message",
            data = payload,
        )
        data.setLength(0)
        eventName = null
        eventId = null
        return frame
    }

    private companion object {
        const val DEFAULT_MAX_DATA_CHARS = 256 * 1024
        const val DEFAULT_MAX_LINE_CHARS = 64 * 1024
    }
}
