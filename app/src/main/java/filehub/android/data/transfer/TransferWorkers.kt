package filehub.android.data.transfer

import filehub.android.R
import filehub.android.core.model.FileKind
import filehub.android.core.model.detectKind
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import filehub.android.FileHubApplication
import filehub.android.core.model.ServerProfile
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException
import filehub.android.data.toUserMessage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import filehub.android.data.sync.MediaStreams

class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val app = applicationContext as FileHubApplication
        val id = inputData.getString(KEY_ID).orEmpty()
        val item = app.container.transferStore.find(id)
        return if (item != null) {
            TransferNotifications.foregroundInfo(applicationContext, item)
        } else {
            val placeholder = TransferItem(
                id = id, kind = TransferKind.UPLOAD, profileId = "", baseUrl = "", rootId = 0,
                remoteDir = "", remotePath = "", name = "upload", size = 0, mimeType = "",
                localUri = "", mtime = 0,
            )
            TransferNotifications.foregroundInfo(applicationContext, placeholder)
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as FileHubApplication
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val store = app.container.transferStore
        var item = store.find(id) ?: return Result.success()
        if (item.status == TransferStatus.CANCELLED || item.status == TransferStatus.COMPLETED) {
            return Result.success()
        }

        store.update(id) { it.copy(status = TransferStatus.RUNNING, error = null, updatedAt = now()) }
        item = item.copy(status = TransferStatus.RUNNING)
        try {
            setForeground(TransferNotifications.foregroundInfo(applicationContext, item))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Foreground promotion can be denied on Android 12+. Keep running
            // this batch as a normal worker; state is checkpointed per chunk.
        }

        val (profile, token) = app.container.profileAndToken(item.profileId)
            ?: return fail(store, id, applicationContext.getString(R.string.error_unauthorized), retry = false)
        val api = FileHubApi(profile.baseUrl, token)

        return try {
            var uploadId = item.uploadId
            var chunkSize = item.chunkSize.takeIf { it > 0 } ?: TransferRepository.UPLOAD_CHUNK_BYTES
            var recreated = false

            if (uploadId != null) {
                val status = runCatching { api.uploadStatus(uploadId) }.getOrNull()
                if (status == null) {
                    uploadId = null
                } else {
                    chunkSize = status.chunkSize.takeIf { it > 0 } ?: chunkSize
                }
            }
            if (uploadId == null) {
                val created = api.createUpload(
                    rootId = item.rootId,
                    path = item.remoteDir,
                    filename = item.name,
                    totalSize = item.size,
                    chunkSize = chunkSize,
                    mtime = item.mtime.takeIf { it > 0 },
                )
                uploadId = created.uploadId
                chunkSize = created.chunkSize
                store.update(id) { it.copy(uploadId = uploadId, chunkSize = chunkSize, updatedAt = now()) }
            }

            val chunks = if (item.size == 0L) 0 else ((item.size + chunkSize - 1) / chunkSize).toInt()
            var received = runCatching { api.uploadStatus(uploadId!!).receivedChunks }.getOrDefault(emptyList()).toSet()
            var bytesDone = min(item.size, received.size.toLong() * chunkSize)

            for (index in 0 until chunks) {
                if (isStopped) return Result.success()
                if (index in received) continue
                val offset = index * chunkSize
                val length = min(chunkSize, item.size - offset)
                val data = readChunk(item.localUri, offset, length)
                try {
                    api.uploadChunk(uploadId!!, index, data)
                } catch (error: FileHubApiException) {
                    if (error.statusCode == 404 && !recreated) {
                        recreated = true
                        uploadId = null
                        val created = api.createUpload(
                            rootId = item.rootId,
                            path = item.remoteDir,
                            filename = item.name,
                            totalSize = item.size,
                            chunkSize = chunkSize,
                            mtime = item.mtime.takeIf { it > 0 },
                        )
                        uploadId = created.uploadId
                        chunkSize = created.chunkSize
                        received = emptySet()
                        bytesDone = 0
                        store.update(id) { it.copy(uploadId = uploadId, chunkSize = chunkSize, bytesDone = 0) }
                        api.uploadChunk(uploadId!!, index, data)
                    } else {
                        throw error
                    }
                }
                received = received + index
                bytesDone = min(item.size, received.size.toLong() * chunkSize)
                store.update(id) { it.copy(bytesDone = bytesDone, updatedAt = now()) }
                TransferNotifications.updateProgress(applicationContext, item.copy(bytesDone = bytesDone))
                setProgress(workDataOf("bytesDone" to bytesDone, "size" to item.size))
            }

            val complete = api.completeUpload(uploadId!!)
            store.update(id) {
                it.copy(
                    status = TransferStatus.COMPLETED,
                    bytesDone = it.size,
                    remotePath = complete.path.ifEmpty { it.remotePath },
                    error = null,
                    updatedAt = now(),
                )
            }
            TransferNotifications.notifyFinished(
                applicationContext,
                item.copy(status = TransferStatus.COMPLETED, bytesDone = item.size),
            )
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: FileHubApiException) {
            val retry = error.code == "NETWORK_ERROR" || error.statusCode == 0
            fail(store, id, error.toUserMessage(applicationContext), retry)
        } catch (error: Exception) {
            fail(store, id, error.toUserMessage(applicationContext), retry = false)
        }
    }

    private suspend fun readChunk(uri: String, offset: Long, length: Long): ByteArray =
        withContext(Dispatchers.IO) {
            val input = openInput(applicationContext, uri)
            input.use {
                skipFully(it, offset)
                val buffer = ByteArray(length.toInt())
                var read = 0
                while (read < buffer.size) {
                    val count = it.read(buffer, read, buffer.size - read)
                    if (count <= 0) break
                    read += count
                }
                if (read == buffer.size) buffer else buffer.copyOf(read)
            }
        }

    private fun skipFully(input: InputStream, offset: Long) {
        var remaining = offset
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() < 0) {
                break
            } else {
                remaining -= 1
            }
        }
    }

    private suspend fun fail(
        store: TransferStore,
        id: String,
        message: String,
        retry: Boolean,
    ): Result {
        store.update(id) {
            it.copy(
                status = if (retry) TransferStatus.QUEUED else TransferStatus.FAILED,
                error = message,
                updatedAt = now(),
            )
        }
        if (!retry) {
            val current = store.find(id)
            if (current != null) TransferNotifications.notifyFinished(applicationContext, current)
        }
        return if (retry) Result.retry() else Result.failure()
    }

    private fun now(): Long = System.currentTimeMillis() / 1000

    companion object {
        const val KEY_ID = "transferId"
    }
}

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val app = applicationContext as FileHubApplication
        val id = inputData.getString(UploadWorker.KEY_ID).orEmpty()
        val item = app.container.transferStore.find(id) ?: TransferItem(
            id = id, kind = TransferKind.DOWNLOAD, profileId = "", baseUrl = "", rootId = 0,
            remoteDir = "", remotePath = "", name = "download", size = 0, mimeType = "",
            localUri = "", mtime = 0,
        )
        return TransferNotifications.foregroundInfo(applicationContext, item)
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as FileHubApplication
        val id = inputData.getString(UploadWorker.KEY_ID) ?: return Result.failure()
        val store = app.container.transferStore
        var item = store.find(id) ?: return Result.success()
        if (item.status == TransferStatus.CANCELLED || item.status == TransferStatus.COMPLETED) {
            return Result.success()
        }
        store.update(id) { it.copy(status = TransferStatus.RUNNING, error = null, updatedAt = now()) }
        item = item.copy(status = TransferStatus.RUNNING)

        try {
            setForeground(TransferNotifications.foregroundInfo(applicationContext, item))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // See UploadWorker: keep going without foreground promotion.
        }

        val (_, token) = app.container.profileAndToken(item.profileId)
            ?: return fail(store, id, applicationContext.getString(R.string.error_unauthorized), retry = false)
        val base = item.baseUrl.toHttpUrlOrNull()
            ?: return fail(store, id, applicationContext.getString(R.string.error_invalid_url), retry = false)
        val url = base.newBuilder()
            .addPathSegments("api/v1/download")
            .addQueryParameter("root", item.rootId.toString())
            .addQueryParameter("path", item.remotePath)
            .addQueryParameter("inline", "false")
            .build()
        val dir = File(applicationContext.cacheDir, "downloads").apply { mkdirs() }
        val part = File(dir, "${item.id}.part")
        var done = part.length()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build(),
                )
            }
            .build()

        return try {
            val request = Request.Builder()
                .url(url)
                .apply { if (done > 0) header("Range", "bytes=$done-") }
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 416) {
                    part.delete()
                    done = 0
                    return fail(store, id, applicationContext.getString(R.string.transfer_error_resume_expired), retry = true)
                }
                if (!response.isSuccessful) {
                    return fail(store, id, "HTTP ${response.code}", retry = response.code >= 500)
                }
                val body = response.body
                if (response.code == 200) done = 0
                val total = if (response.code == 206) done + body.contentLength() else body.contentLength()
                val append = done > 0 && response.code == 206
                FileOutputStream(part, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var lastNotify = 0L
                        while (true) {
                            if (isStopped) return Result.success()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (System.currentTimeMillis() - lastNotify > 500) {
                                lastNotify = System.currentTimeMillis()
                                val progressItem = item.copy(
                                    bytesDone = done,
                                    size = if (total > 0) total else item.size,
                                )
                                store.update(id) {
                                    it.copy(
                                        bytesDone = done,
                                        size = if (total > 0) total.coerceAtLeast(0) else it.size,
                                    )
                                }
                                TransferNotifications.updateProgress(applicationContext, progressItem)
                                setProgress(workDataOf("bytesDone" to done, "size" to progressItem.size))
                            }
                        }
                    }
                }
                val finalSize = if (total > 0) total else done
                val saved = saveCompleted(item, part)
                store.update(id) {
                    it.copy(
                        status = TransferStatus.COMPLETED,
                        bytesDone = finalSize,
                        size = finalSize,
                        localFile = saved,
                        error = null,
                        updatedAt = now(),
                    )
                }
                TransferNotifications.notifyFinished(
                    applicationContext,
                    item.copy(status = TransferStatus.COMPLETED, bytesDone = finalSize, localFile = saved),
                )
                Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            fail(store, id, error.message ?: applicationContext.getString(R.string.error_network), retry = true)
        } catch (error: Exception) {
            fail(store, id, error.message ?: applicationContext.getString(R.string.transfer_error_download_failed), retry = false)
        }
    }

    private suspend fun saveCompleted(item: TransferItem, part: File): String = withContext(Dispatchers.IO) {
        val kind = detectKind(item.name, item.mimeType, isDir = false)
        val visual = kind == FileKind.Image || kind == FileKind.Video
        val mime = item.mimeType.ifBlank {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(item.name.substringAfterLast('.', "").lowercase())
                ?: when (kind) {
                    FileKind.Image -> "image/*"
                    FileKind.Video -> "video/*"
                    else -> "application/octet-stream"
                }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = when (kind) {
                FileKind.Image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                FileKind.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            val relativePath = when (kind) {
                FileKind.Image -> Environment.DIRECTORY_PICTURES + "/FileHub"
                FileKind.Video -> Environment.DIRECTORY_MOVIES + "/FileHub"
                else -> Environment.DIRECTORY_DOWNLOADS + "/FileHub"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(collection, values)
                ?: error("cannot create download entry")
            resolver.openOutputStream(uri)?.use { output ->
                part.inputStream().use { input -> input.copyTo(output) }
            } ?: error("cannot open download entry")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            part.delete()
            uri.toString()
        } else {
            val publicDir = when (kind) {
                FileKind.Image -> Environment.DIRECTORY_PICTURES
                FileKind.Video -> Environment.DIRECTORY_MOVIES
                else -> Environment.DIRECTORY_DOWNLOADS
            }
            val target = runCatching {
                val dir = File(Environment.getExternalStoragePublicDirectory(publicDir), "FileHub")
                    .apply { mkdirs() }
                uniqueFile(dir, item.name).also { file ->
                    part.inputStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }.getOrElse {
                val dir = File(applicationContext.getExternalFilesDir(publicDir), "FileHub")
                    .apply { mkdirs() }
                uniqueFile(dir, item.name).also { file ->
                    part.inputStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            part.delete()
            Uri.fromFile(target).toString()
        }
    }

    /**
     * Android 9 and below writes straight to the public folder. Never replace
     * an existing download: append " (1)", " (2)" and so on instead.
     */
    private fun uniqueFile(dir: File, name: String): File {
        val direct = File(dir, name)
        if (!direct.exists()) return direct
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(dir, "$base ($index)$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private suspend fun fail(
        store: TransferStore,
        id: String,
        message: String,
        retry: Boolean,
    ): Result {
        store.update(id) {
            it.copy(
                status = if (retry) TransferStatus.QUEUED else TransferStatus.FAILED,
                error = message,
                updatedAt = now(),
            )
        }
        if (!retry) {
            store.find(id)?.let { TransferNotifications.notifyFinished(applicationContext, it) }
        }
        return if (retry) Result.retry() else Result.failure()
    }

    private fun now(): Long = System.currentTimeMillis() / 1000
}

private fun openInput(context: Context, uri: String): InputStream {
    val parsed = Uri.parse(uri)
    return if (parsed.scheme == "file") {
        FileInputStream(File(parsed.path ?: error("file uri without path")))
    } else {
        MediaStreams.open(context, parsed)
    }
}
