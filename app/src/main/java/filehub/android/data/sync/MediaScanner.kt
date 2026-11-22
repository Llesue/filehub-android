package filehub.android.data.sync

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class MediaBucket(
    val id: Long,
    val name: String,
    val count: Int,
)

data class MediaPhoto(
    val localId: Long,
    val uri: Uri,
    val bucketId: Long,
    val album: String,
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
        val map = linkedMapOf<Long, MediaBucket>()
        fun scan(collection: Uri) {
            val projection = arrayOf(
                MediaStore.Images.ImageColumns.BUCKET_ID,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns._ID,
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
                    while (cursor.moveToNext()) {
                        val id = if (cursor.isNull(idIndex)) 0L else cursor.getLong(idIndex)
                        val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: "Camera"
                        val current = map[id]
                        map[id] = if (current == null) {
                            MediaBucket(id, name, 1)
                        } else {
                            current.copy(count = current.count + 1)
                        }
                    }
                }
            }
        }
        scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (includeVideos) scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        return map.values.toList()
    }

    fun photos(
        context: Context,
        selectedAlbums: Set<Long>?,
        includeVideos: Boolean,
    ): List<MediaPhoto> {
        val result = mutableListOf<MediaPhoto>()
        fun scan(collection: Uri, isVideo: Boolean) {
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
            )
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
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
                    while (cursor.moveToNext()) {
                        val bucket = if (cursor.isNull(bucketIndex)) 0L else cursor.getLong(bucketIndex)
                        if (selectedAlbums != null && bucket !in selectedAlbums) continue
                        val id = cursor.getLong(idIndex)
                        val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                            ?: if (isVideo) "video-$id" else "photo-$id"
                        val size = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                        val taken = if (cursor.isNull(takenIndex)) 0L else cursor.getLong(takenIndex) / 1000
                        val added = if (cursor.isNull(addedIndex)) 0L else cursor.getLong(addedIndex)
                        val modified = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex)
                        val mtime = taken.takeIf { it > 0 } ?: added.takeIf { it > 0 } ?: modified
                        val album = cursor.getString(albumIndex)?.takeIf { it.isNotBlank() } ?: "Camera"
                        val mime = cursor.getString(mimeIndex)
                            ?: if (isVideo) "video/*" else "image/*"
                        result += MediaPhoto(
                            localId = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            bucketId = bucket,
                            album = album,
                            name = name,
                            size = size,
                            mtime = mtime,
                            mimeType = mime,
                            isVideo = isVideo,
                        )
                    }
                }
            }
        }
        scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
        if (includeVideos) scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
        return result
    }
}
