package filehub.android.ui

data class EditorSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/**
 * Bounded undo/redo for the text editor. Consecutive keystrokes inside
 * [coalesceMs] collapse into one step so undo does not remove a single
 * character at a time. Very large documents opt out to keep memory bounded.
 */
class EditorHistory(
    private val limit: Int = 40,
    private val maxTextLength: Int = 200_000,
    private val coalesceMs: Long = 600,
) {
    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private var lastRecordAt = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun record(previous: EditorSnapshot, now: Long) {
        if (previous.text.length > maxTextLength) return
        redoStack.clear()
        if (undoStack.isNotEmpty() && now - lastRecordAt < coalesceMs) {
            lastRecordAt = now
            return
        }
        undoStack.addLast(previous)
        while (undoStack.size > limit) undoStack.removeFirst()
        lastRecordAt = now
    }

    fun undo(current: EditorSnapshot): EditorSnapshot? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        while (redoStack.size > limit) redoStack.removeFirst()
        lastRecordAt = 0L
        return previous
    }

    fun redo(current: EditorSnapshot): EditorSnapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        while (undoStack.size > limit) undoStack.removeFirst()
        lastRecordAt = 0L
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastRecordAt = 0L
    }
}
