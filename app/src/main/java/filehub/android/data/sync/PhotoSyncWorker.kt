package filehub.android.data.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import filehub.android.FileHubApplication
import filehub.android.R
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException
import filehub.android.data.toUserMessage
import java.io.InputStream
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        PhotoSyncNotifications.foregroundInfo(applicationContext, 0, 0, "")

    override suspend fun doWork(): Result {
        val app = applicationContext as FileHubApplication
        val repo = app.container.photoSyncRepository
        val profileId = inputData.getString("profileId") ?: return Result.success()
        val config = repo.config(profileId) ?: return Result.success()
        if (!config.enabled) return Result.success()

        if (!hasPhotoPermission(config)) {
            repo.updateConfig(
                config.copy(
                    lastError = applicationContext.getString(R.string.photo_sync_permission),
                    lastRunAt = now(),
                ),
            )
            return Result.success()
        }

        try {
            setForeground(
                PhotoSyncNotifications.foregroundInfo(
                    applicationContext, 0, 0, "", notificationId(profileId),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Foreground promotion is best-effort; state is checkpointed per file.
        }

        val (profile, token) = app.container.profileAndToken(config.profileId)
            ?: return fail(repo, config, "设备授权已失效，请重新登录")
        val api = FileHubApi(profile.baseUrl, token)
        val albums = repo.selectedAlbums(config)
        val photos = withContext(Dispatchers.IO) {
            MediaScanner.photos(applicationContext, albums, config.includeVideos)
        }
        val entries = repo.entries(profileId).associateBy { it.signature }
        val remoteNames = mutableMapOf<String, MutableMap<String, Long>>()
        var done = 0
        var failed = 0

        for ((index, photo) in photos.withIndex()) {
            if (isStopped) {
                finish(repo, config, done, photos.size)
                return Result.success()
            }
            val existing = entries[photo.signature]
            if (existing?.status == STATUS_DONE && existing.size == photo.size) {
                done += 1
                continue
            }
            try {
                val remoteDir = remoteDir(config, photo)
                ensureRemoteDir(api, config.rootId, remoteDir)
                val target = selectRemoteTarget(
                    api = api,
                    rootId = config.rootId,
                    remoteDir = remoteDir,
                    cache = remoteNames,
                    photo = photo,
                )
                if (target.alreadyExists) {
                    repo.saveEntry(
                        entry(profileId, photo, remoteDir, target.name, STATUS_DONE, null, photo.size),
                    )
                    done += 1
                } else {
                    uploadPhotoWithRoot(
                        api = api,
                        profileId = profileId,
                        rootId = config.rootId,
                        photo = photo,
                        remoteDir = remoteDir,
                        remoteName = target.name,
                        existing = existing,
                        repo = repo,
                    )
                    done += 1
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: FileHubApiException) {
                failed += 1
                repo.saveEntry(
                    entry(
                        profileId = profileId,
                        photo = photo,
                        remoteDir = remoteDir(config, photo),
                        remoteName = existing?.remotePath?.substringAfterLast('/') ?: photo.name,
                        status = STATUS_ERROR,
                        error = error.toUserMessage(),
                        bytesDone = existing?.bytesDone ?: 0,
                    ),
                )
                if (error.statusCode == 401 || error.statusCode == 403 || error.code == "QUOTA_EXCEEDED") {
                    finish(repo, config, done, photos.size, failed)
                    return if (error.statusCode == 401) Result.failure() else Result.success()
                }
            } catch (error: Exception) {
                failed += 1
                repo.saveEntry(
                    entry(
                        profileId = profileId,
                        photo = photo,
                        remoteDir = remoteDir(config, photo),
                        remoteName = existing?.remotePath?.substringAfterLast('/') ?: photo.name,
                        status = STATUS_ERROR,
                        error = error.toUserMessage(),
                        bytesDone = existing?.bytesDone ?: 0,
                    ),
                )
            }
            PhotoSyncNotifications.update(
                applicationContext,
                done + failed,
                photos.size,
                photos.getOrNull(index)?.name.orEmpty(),
                notificationId(profileId),
            )
        }

        finish(repo, config, done, photos.size, failed)
        return Result.success()
    }

    private suspend fun uploadPhotoWithRoot(
        api: FileHubApi,
        profileId: String,
        rootId: Long,
        photo: MediaPhoto,
        remoteDir: String,
        remoteName: String,
        existing: PhotoSyncEntryEntity?,
        repo: PhotoSyncRepository,
    ): String {
        var uploadId = existing?.uploadId
        var chunkSize = existing?.let { 4L * 1024 * 1024 } ?: UPLOAD_CHUNK
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
                rootId = rootId,
                path = remoteDir,
                filename = remoteName,
                totalSize = photo.size,
                chunkSize = chunkSize,
                mtime = photo.mtime.takeIf { it > 0 },
            )
            uploadId = created.uploadId
            chunkSize = created.chunkSize
        }
        repo.saveEntry(
            entry(profileId, photo, remoteDir, remoteName, STATUS_RUNNING, null, 0, uploadId),
        )
        val chunks = if (photo.size == 0L) 0 else ((photo.size + chunkSize - 1) / chunkSize).toInt()
        var received = runCatching { api.uploadStatus(uploadId!!).receivedChunks }
            .getOrDefault(emptyList())
            .toSet()
        for (index in 0 until chunks) {
            if (isStopped) break
            if (index in received) continue
            val offset = index * chunkSize
            val length = min(chunkSize, photo.size - offset)
            val data = readChunk(photo.uri.toString(), offset, length)
            api.uploadChunk(uploadId!!, index, data)
            received = received + index
            repo.updateEntry(
                profileId = profileId,
                signature = photo.signature,
                status = STATUS_RUNNING,
                uploadId = uploadId,
                bytesDone = min(photo.size, received.size.toLong() * chunkSize),
                remotePath = "$remoteDir/$remoteName",
                error = null,
            )
        }
        val complete = api.completeUpload(uploadId!!)
        repo.saveEntry(
            entry(
                profileId = profileId,
                photo = photo,
                remoteDir = remoteDir,
                remoteName = remoteName,
                status = STATUS_DONE,
                error = null,
                bytesDone = photo.size,
                remotePath = complete.path,
            ),
        )
        return complete.path
    }

    private suspend fun ensureRemoteDir(api: FileHubApi, rootId: Long, dir: String) {
        var parent = ""
        for (segment in dir.split('/').filter { it.isNotEmpty() }) {
            runCatching { api.mkdir(rootId, parent, segment) }
                .onFailure { error ->
                    if (error is FileHubApiException && error.statusCode !in listOf(409, 403)) throw error
                }
            parent = if (parent.isEmpty()) segment else "$parent/$segment"
        }
    }

    private suspend fun selectRemoteTarget(
        api: FileHubApi,
        rootId: Long,
        remoteDir: String,
        cache: MutableMap<String, MutableMap<String, Long>>,
        photo: MediaPhoto,
    ): RemoteTarget {
        val names = cache.getOrPut(remoteDir) {
            runCatching {
                api.list(rootId, remoteDir, limit = 1000).entries
                    .associate { it.name to it.size }
                    .toMutableMap()
            }.getOrDefault(mutableMapOf())
        }
        if (names[photo.name] == photo.size) {
            return RemoteTarget(photo.name, alreadyExists = true)
        }
        var candidate = photo.name
        if (names.containsKey(candidate)) {
            val dot = photo.name.lastIndexOf('.')
            val base = if (dot > 0) photo.name.substring(0, dot) else photo.name
            val ext = if (dot > 0) photo.name.substring(dot) else ""
            var index = 1
            while (names.containsKey("$base ($index)$ext")) index += 1
            candidate = "$base ($index)$ext"
        }
        names[candidate] = photo.size
        return RemoteTarget(candidate, alreadyExists = false)
    }

    private suspend fun readChunk(uri: String, offset: Long, length: Long): ByteArray =
        withContext(Dispatchers.IO) {
            val input = applicationContext.contentResolver.openInputStream(android.net.Uri.parse(uri))
                ?: error("cannot open media uri")
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
            if (skipped > 0) remaining -= skipped
            else if (input.read() < 0) break
            else remaining -= 1
        }
    }

    private fun remoteDir(config: PhotoSyncConfigEntity, photo: MediaPhoto): String {
        val base = config.remotePath.trim('/')
        val album = if (config.albumSubfolders) sanitize(photo.album) else ""
        return listOf(base, album).filter { it.isNotEmpty() }.joinToString("/")
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[/\\\\\\p{Cntrl}]"), "_").trim().ifEmpty { "Camera" }

    private fun entry(
        profileId: String,
        photo: MediaPhoto,
        remoteDir: String,
        remoteName: String,
        status: String,
        error: String?,
        bytesDone: Long,
        uploadId: String? = null,
        remotePath: String? = null,
    ): PhotoSyncEntryEntity = PhotoSyncEntryEntity(
        profileId = profileId,
        signature = photo.signature,
        localId = photo.localId,
        bucketId = photo.bucketId,
        album = photo.album,
        name = photo.name,
        size = photo.size,
        mtime = photo.mtime,
        mimeType = photo.mimeType,
        remotePath = remotePath ?: if (remoteDir.isEmpty()) remoteName else "$remoteDir/$remoteName",
        status = status,
        uploadId = uploadId,
        bytesDone = bytesDone,
        error = error,
        updatedAt = now(),
    )

    private suspend fun finish(
        repo: PhotoSyncRepository,
        config: PhotoSyncConfigEntity,
        done: Int,
        total: Int,
        failed: Int = 0,
    ) {
        val result = applicationContext.getString(R.string.photo_sync_result, done, total)
        repo.updateConfig(
            config.copy(
                lastRunAt = now(),
                lastResult = result,
                lastError = if (failed > 0) "$failed failed" else null,
                pendingCount = repo.pendingCount(config.profileId),
            ),
        )
        PhotoSyncNotifications.finished(applicationContext, result, notificationId(config.profileId))
    }

    private suspend fun fail(
        repo: PhotoSyncRepository,
        config: PhotoSyncConfigEntity,
        message: String,
    ): Result {
        repo.updateConfig(config.copy(lastError = message, lastRunAt = now()))
        PhotoSyncNotifications.finished(applicationContext, message, notificationId(config.profileId))
        return Result.retry()
    }

    private fun hasPhotoPermission(config: PhotoSyncConfigEntity): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val images = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED
            val selected = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == PackageManager.PERMISSION_GRANTED
            val videos = !config.includeVideos || ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_MEDIA_VIDEO,
            ) == PackageManager.PERMISSION_GRANTED
            return (images || selected) && videos
        }
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun notificationId(profileId: String): Int =
        5301 + (profileId.hashCode() and 0x0FFFFF)

    private fun now(): Long = System.currentTimeMillis() / 1000

    private data class RemoteTarget(val name: String, val alreadyExists: Boolean)

    companion object {
        private const val STATUS_RUNNING = "running"
        private const val STATUS_DONE = "done"
        private const val STATUS_ERROR = "error"
        private const val UPLOAD_CHUNK = 4L * 1024 * 1024
    }
}
