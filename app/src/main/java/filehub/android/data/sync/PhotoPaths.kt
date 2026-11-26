package filehub.android.data.sync

internal fun sanitizePhotoPathSegment(value: String): String =
    value.replace(Regex("[/\\\\\\p{Cntrl}]"), "_").trim()

/**
 * Converts MediaStore's relative path into the remote album subdirectory.
 * Falls back to the bucket display name when the platform does not expose a
 * relative path, and drops traversal/empty segments.
 */
internal fun photoAlbumRelativePath(relativePath: String, album: String): String {
    val raw = relativePath.trim('/').ifBlank { album }
    val sanitized = raw.split('/')
        .mapNotNull { segment ->
            val clean = sanitizePhotoPathSegment(segment)
            clean.takeIf { it.isNotEmpty() && it != "." && it != ".." }
        }
        .joinToString("/")
    return sanitized.ifBlank {
        sanitizePhotoPathSegment(album).ifBlank { AlbumNames.DEFAULT_ALBUM }
    }
}
