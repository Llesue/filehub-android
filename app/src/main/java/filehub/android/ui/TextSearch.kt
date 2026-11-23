package filehub.android.ui

data class TextMatch(val start: Int, val end: Int)

/**
 * Case-insensitive search. Kotlin's `indexOf(ignoreCase = true)` keeps offsets
 * in the original string, so the ranges can be used directly for selection and
 * highlighting.
 */
fun findTextMatches(text: String, query: String, limit: Int = 2_000): List<TextMatch> {
    if (query.isEmpty() || text.isEmpty()) return emptyList()
    val matches = ArrayList<TextMatch>()
    var index = text.indexOf(query, 0, ignoreCase = true)
    while (index >= 0 && matches.size < limit) {
        matches += TextMatch(index, index + query.length)
        index = text.indexOf(query, index + maxOf(1, query.length), ignoreCase = true)
    }
    return matches
}

fun stepMatch(current: Int, count: Int, forward: Boolean): Int {
    if (count <= 0) return -1
    if (current !in 0 until count) return if (forward) 0 else count - 1
    return if (forward) (current + 1) % count else (current - 1 + count) % count
}
