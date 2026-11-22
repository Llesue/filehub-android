package filehub.android.data.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import filehub.android.core.model.ServerProfile
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class TransferRepository(
    private val context: Context,
    private val store: TransferStore,
) {
    val items: Flow<List<TransferItem>> = store.items

    suspend fun all(): List<TransferItem> = store.all()

    suspend fun enqueueUpload(
        profile: ServerProfile,
        rootId: Long,
        remoteDir: String,
        uri: Uri,
        mimeType: String? = null,
    ): TransferItem? = runCatching {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Photo picker and file:// URIs do not support persistable grants;
            // the worker still has access for the current process lifetime.
        }
        val meta = resolveMeta(uri, mimeType)
        val item = TransferItem(
            id = UUID.randomUUID().toString(),
            kind = TransferKind.UPLOAD,
            profileId = profile.id,
            baseUrl = profile.baseUrl,
            rootId = rootId,
            remoteDir = remoteDir,
            remotePath = join(remoteDir, meta.name),
            name = meta.name,
            size = meta.size,
            mimeType = meta.mime,
            localUri = meta.localUri,
            mtime = meta.mtime,
            chunkSize = UPLOAD_CHUNK_BYTES,
        )
        store.upsert(item)
        enqueue(item)
        item
    }.getOrNull()

    suspend fun enqueueDownload(
        profile: ServerProfile,
        rootId: Long,
        remotePath: String,
        name: String,
        size: Long,
        mimeType: String,
    ): TransferItem {
        val item = TransferItem(
            id = UUID.randomUUID().toString(),
            kind = TransferKind.DOWNLOAD,
            profileId = profile.id,
            baseUrl = profile.baseUrl,
            rootId = rootId,
            remoteDir = remotePath.substringBeforeLast('/', ""),
            remotePath = remotePath,
            name = name,
            size = size,
            mimeType = mimeType,
            localUri = "",
            mtime = 0,
        )
        store.upsert(item)
        enqueue(item)
        return item
    }

    suspend fun retry(item: TransferItem) {
        store.update(item.id) {
            it.copy(status = TransferStatus.QUEUED, error = null, bytesDone = 0, updatedAt = now())
        }
        enqueue(item)
    }

    suspend fun cancel(item: TransferItem) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(item.id))
        store.update(item.id) {
            it.copy(status = TransferStatus.CANCELLED, updatedAt = now())
        }
    }

    suspend fun remove(item: TransferItem) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(item.id))
        store.remove(item.id)
    }

    suspend fun clearCompleted() = store.clearCompleted()

    private fun enqueue(item: TransferItem) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = when (item.kind) {
            TransferKind.UPLOAD -> OneTimeWorkRequestBuilder<UploadWorker>()
            TransferKind.DOWNLOAD -> OneTimeWorkRequestBuilder<DownloadWorker>()
        }
            .setConstraints(constraints)
            .setInputData(workDataOf("transferId" to item.id))
            .addTag("filehub-transfer")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(item.id),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun workName(id: String): String = "filehub-transfer-$id"

    private fun resolveMeta(uri: Uri, fallbackMime: String?): Meta {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "upload.bin"
        var size = -1L
        var mtime = now()
        var mime = fallbackMime ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        } else if (uri.scheme == "file") {
            uri.path?.let { File(it) }?.takeIf { it.isFile }?.let {
                name = it.name
                size = it.length()
                mtime = it.lastModified() / 1000
            }
        }
        if (size < 0) {
            // Unknown source size: copy once to app cache so the worker has a
            // stable, seekable file and an exact total_size.
            val cache = File(context.cacheDir, "upload-cache").apply { mkdirs() }
            val target = File(cache, "${UUID.randomUUID()}-$name")
            openInput(uri.toString()).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            size = target.length()
            mtime = target.lastModified() / 1000
            mime = fallbackMime ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
            return Meta(name = name, size = size, mtime = mtime, mime = mime, localUri = Uri.fromFile(target).toString())
        }
        return Meta(name = name, size = size, mtime = mtime, mime = mime, localUri = uri.toString())
    }

    private fun openInput(uri: String): java.io.InputStream {
        val parsed = Uri.parse(uri)
        return if (parsed.scheme == "file") {
            FileInputStream(File(parsed.path ?: error("file uri without path")))
        } else {
            context.contentResolver.openInputStream(parsed) ?: error("cannot open $uri")
        }
    }

    private fun now(): Long = System.currentTimeMillis() / 1000

    private data class Meta(
        val name: String,
        val size: Long,
        val mtime: Long,
        val mime: String,
        val localUri: String,
    )

    companion object {
        const val UPLOAD_CHUNK_BYTES = 4L * 1024 * 1024
    }
}

private fun join(dir: String, name: String): String =
    if (dir.isEmpty()) name else "${dir.trimEnd('/')}/$name"
