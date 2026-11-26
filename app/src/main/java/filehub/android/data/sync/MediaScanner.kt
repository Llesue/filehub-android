package filehub.android.data.sync

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

data class MediaBucket(
    val id: Long,
    val name: String,
    val count: Int,
    val relativePath: String = "",
)

data class MediaPhoto(
    val localId: Long,
    val uri: Uri,
    val bucketId: Long,
    val album: String,
    val relativePath: String = "",
    val name: String,
    val size: Long,
    val mtime: Long,
    val mimeType: String,
    val isVideo: Boolean,
) {
    val signature: String get() = "$bucketId|$name|$size|$mtime"
}

object MediaScanner {

    fun buckets(context: Context, includeVideos: Boolean): List<MediaBucket> {
        val counts = linkedMapOf<Long, MutableMap<String, Int>>()
        val paths = linkedMapOf<Long, MutableMap<String, Int>>()
        fun scan(collection: Uri) {
            val relativeColumn = relativePathColumn()
            val projection = arrayOf(
                MediaStore.Images.ImageColumns.BUCKET_ID,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns._ID,
                relativeColumn,
            )
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME} ASC",
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                    val relativeIndex = cursor.getColumnIndexOrThrow(relativeColumn)
                    val fromData = relativeColumn == MediaStore.MediaColumns.DATA
                    while (cursor.moveToNext()) {
                        val id = if (cursor.isNull(idIndex)) 0L else cursor.getLong(idIndex)
                        val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                            ?: AlbumNames.DEFAULT_ALBUM
                        val relative = relativePathAt(cursor, relativeIndex, name, fromData)
                        val names = counts.getOrPut(id) { mutableMapOf() }
                        names[name] = (names[name] ?: 0) + 1
                        val bucketPaths = paths.getOrPut(id) { mutableMapOf() }
                        bucketPaths[relative] = (bucketPaths[relative] ?: 0) + 1
                    }
                }
            }
        }
        scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (includeVideos) scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        return counts
            .map { (id, names) ->
                val name = AlbumNames.canonical(names) ?: AlbumNames.DEFAULT_ALBUM
                MediaBucket(
                    id = id,
                    name = name,
                    count = names.values.sum(),
                    relativePath = AlbumNames.canonical(paths[id].orEmpty()) ?: name,
                )
            }
            .sortedWith(
                compareBy(
                    { it.relativePath.lowercase() },
                    { it.relativePath },
                    { it.name.lowercase() },
                    { it.id },
                ),
            )
    }

    private fun relativePathColumn(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }

    private fun relativePathAt(
        cursor: Cursor,
        index: Int,
        fallback: String,
        fromData: Boolean,
    ): String {
        val raw = if (cursor.isNull(index)) "" else cursor.getString(index).orEmpty()
        val value = if (fromData) {
            raw.substringAfter("/storage/emulated/0/", raw)
                .substringAfter("/sdcard/", raw)
                .substringBeforeLast('/', "")
        } else {
            raw.trim('/')
        }
        return value.trim('/').ifBlank { fallback }
    }

    fun photoFromEntry(context: Context, entry: PhotoSyncEntryEntity): MediaPhoto? {
        val isVideo = entry.mimeType.startsWith("video/")
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = ContentUris.withAppendedId(collection, entry.localId)
        val exists = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns._ID),
            null,
            null,
            null,
        )?.use { it.moveToFirst() } == true
        if (!exists) return null
        return MediaPhoto(
            localId = entry.localId,
            uri = uri,
            bucketId = entry.bucketId,
            album = entry.album,
            relativePath = entry.relativePath,
            name = entry.name,
            size = entry.size,
            mtime = entry.mtime,
            mimeType = entry.mimeType,
            isVideo = isVideo,
        )
    }

    fun photos(
        context: Context,
        selectedAlbums: Set<Long>?,
        includeVideos: Boolean,
        modifiedAfter: Long? = null,
        idAfter: Long? = null,
    ): Result<List<MediaPhoto>> = runCatching {
        val result = mutableListOf<MediaPhoto>()
        fun scan(collection: Uri, isVideo: Boolean) {
            val relativeColumn = relativePathColumn()
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.Images.ImageColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.Images.ImageColumns.BUCKET_ID,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                relativeColumn,
            )
            val selection = buildList {
                if (modifiedAfter != null && modifiedAfter > 0) {
                    add("${MediaStore.MediaColumns.DATE_MODIFIED} >= ?")
                }
                if (idAfter != null && idAfter > 0) {
                    add("${MediaStore.MediaColumns._ID} > ?")
                }
            }.joinToString(" OR ").takeIf { it.isNotBlank() }
            val selectionArgs = buildList {
                if (modifiedAfter != null && modifiedAfter > 0) add(modifiedAfter.toString())
                if (idAfter != null && idAfter > 0) add(idAfter.toString())
            }.toTypedArray()
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val takenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN)
                    val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val bucketIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
                    val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val relativeIndex = cursor.getColumnIndexOrThrow(relativeColumn)
                    val fromData = relativeColumn == MediaStore.MediaColumns.DATA
                    while (cursor.moveToNext()) {
                        val bucket = if (cursor.isNull(bucketIndex)) 0L else cursor.getLong(bucketIndex)
                        val id = cursor.getLong(idIndex)
                        val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                            ?: if (isVideo) "video-$id" else "photo-$id"
                        val size = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                        val taken = if (cursor.isNull(takenIndex)) 0L else cursor.getLong(takenIndex) / 1000
                        val added = if (cursor.isNull(addedIndex)) 0L else cursor.getLong(addedIndex)
                        val modified = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex)
                        val mtime = taken.takeIf { it > 0 } ?: added.takeIf { it > 0 } ?: modified
                        val album = cursor.getString(albumIndex)?.takeIf { it.isNotBlank() }
                            ?: AlbumNames.DEFAULT_ALBUM
                        val relative = relativePathAt(cursor, relativeIndex, album, fromData)
                        val mime = cursor.getString(mimeIndex)
                            ?: if (isVideo) "video/*" else "image/*"
                        result += MediaPhoto(
                            localId = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            bucketId = bucket,
                            album = album,
                            relativePath = relative,
                            name = name,
                            size = size,
                            mtime = mtime,
                            mimeType = mime,
                            isVideo = isVideo,
                        )
                    }
            } ?: error("media query returned no cursor")
        }
        scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
        if (includeVideos) scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
        // One bucket id can report several case variants; use the same canonical name
        // as the album picker and filter only afterwards so both stay consistent.
        val canonical = AlbumNames.canonicalByBucket(result.map { it.bucketId to it.album })
        val canonicalPaths = AlbumNames.canonicalByBucket(
            result.map { it.bucketId to it.relativePath },
        )
        result
            .asSequence()
            .map { photo ->
                photo.copy(
                    album = canonical[photo.bucketId] ?: photo.album,
                    relativePath = canonicalPaths[photo.bucketId] ?: photo.relativePath,
                )
            }
            .filter { selectedAlbums == null || it.bucketId in selectedAlbums }
            .distinctBy { it.localId }
            .toList()
    }
}
