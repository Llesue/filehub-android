package filehub.android.data.sync

/**
 * MediaStore derives [android.provider.MediaStore.MediaColumns.BUCKET_ID] from the
 * lower-cased parent path, while [android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME]
 * keeps whatever casing the writing app used. The same physical album can therefore
 * surface as both `Camera` and `camera` under one bucket id.
 *
 * Pick one canonical name per bucket id so the album picker and the uploader agree.
 * The rule is deterministic and independent of row order:
 *
 * 1. the name used by most items;
 * 2. on a tie, the capitalized form;
 * 3. then lexicographic order.
 */
internal object AlbumNames {

    fun canonical(counts: Map<String, Int>): String? =
        counts.entries
            .asSequence()
            .filter { it.key.isNotBlank() && it.value > 0 }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key.firstOrNull()?.isUpperCase() == true }
                    .thenBy { it.key.lowercase() }
                    .thenBy { it.key },
            )
            .firstOrNull()
            ?.key

    fun canonicalByBucket(rows: Iterable<Pair<Long, String>>): Map<Long, String> {
        val counts = mutableMapOf<Long, MutableMap<String, Int>>()
        rows.forEach { (bucketId, album) ->
            val names = counts.getOrPut(bucketId) { mutableMapOf() }
            names[album] = (names[album] ?: 0) + 1
        }
        return counts.mapValues { (_, names) -> canonical(names) ?: DEFAULT_ALBUM }
    }

    const val DEFAULT_ALBUM = "Camera"
}
