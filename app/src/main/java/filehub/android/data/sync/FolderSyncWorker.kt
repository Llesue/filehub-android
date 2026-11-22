package filehub.android.data.sync

import filehub.android.R
import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import filehub.android.FileHubApplication
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException
import filehub.android.data.toUserMessage
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class FolderSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val profileId = inputData.getString("profileId").orEmpty()
        val config = (applicationContext as FileHubApplication)
            .container.folderSyncRepository.config(profileId)
        return FolderSyncNotifications.foregroundInfo(
            applicationContext,
            0,
            0,
            "",
            FolderSyncNotifications.idFor(profileId),
            config?.serverName.orEmpty(),
            profileId,
        )
    }

    override suspend fun doWork(): Result {
        val profileId = inputData.getString("profileId") ?: return Result.success()
        val lock = SyncLocks.folder(profileId)
        if (!lock.tryLock()) return Result.success()
        return try {
            runSync(profileId)
        } finally {
            lock.unlock()
        }
    }

    private suspend fun runSync(profileId: String): Result {
        val app = applicationContext as FileHubApplication
        val repo = app.container.folderSyncRepository
        val config = repo.config(profileId) ?: return Result.success()
        if (!config.enabled) return Result.success()
        if (config.localTreeUri.isBlank()) {
            repo.updateConfig(config.copy(lastError = applicationContext.getString(R.string.folder_sync_error_no_local), lastRunAt = now()))
            return Result.success()
        }

        try {
            setForeground(
                FolderSyncNotifications.foregroundInfo(
                    applicationContext,
                    0,
                    0,
                    "",
                    FolderSyncNotifications.idFor(profileId),
                    config.serverName,
                    profileId,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Checkpointing still works without foreground promotion.
        }

        val (profile, token) = app.container.profileAndToken(config.profileId)
            ?: return failPermanently(repo, config, applicationContext.getString(R.string.error_unauthorized))
        val api = FileHubApi(profile.baseUrl, token)
        val roots = runCatching { api.roots() }
            .getOrElse { error ->
                if (error is FileHubApiException && error.statusCode == 401) {
                    return failPermanently(
                        repo,
                        config,
                        applicationContext.getString(R.string.error_unauthorized),
                    )
                }
                return fail(repo, config, error.toUserMessage(applicationContext))
            }
        val root = roots.firstOrNull { it.id == config.rootId }
        if (root == null) {
            return failPermanently(
                repo,
                config,
                applicationContext.getString(R.string.sync_root_missing),
            )
        }
        if (root.isRemote && root.online == false) {
            return fail(repo, config, applicationContext.getString(R.string.sync_remote_offline))
        }
        val remoteDirs = RemoteDirEnsurer(api, config.rootId)
        try {
            remoteDirs.ensure(config.remotePath)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return syncFailure(repo, config, error)
        }

        val localResult = withContext(Dispatchers.IO) {
            SafScanner.scan(applicationContext, Uri.parse(config.localTreeUri))
        }
        val localNodes = localResult.getOrElse { error ->
            repo.updateConfig(config.copy(lastError = error.toUserMessage(applicationContext), lastRunAt = now()))
            return Result.success()
        }
        val localFiles = localNodes.filterNot { it.isDir }.associateBy { it.relativePath }
        val remoteNodes = runCatching { scanRemote(api, config.rootId, config.remotePath) }
            .getOrElse { error ->
                return fail(repo, config, error.toUserMessage(applicationContext))
            }
        val remoteFiles = remoteNodes.filterNot { it.value.isDir }.mapValues { it.value }
        val entries = repo.entries(profileId).associateBy { it.relativePath }.toMutableMap()
        val paths = (localFiles.keys + remoteFiles.keys + entries.keys).toSortedSet()
        var done = 0
        var conflicts = 0
        var failed = 0

        for (path in paths) {
            if (isStopped) {
                finish(repo, config, done, paths.size, conflicts, failed)
                return Result.success()
            }
            val local = localFiles[path]
            val remote = remoteFiles[path]
            val entry = entries[path]
            val action = decide(config, local, remote, entry)
            try {
                when (action) {
                    Action.REMOVE -> {
                        repo.deleteEntry(profileId, path)
                        entries.remove(path)
                    }
                    Action.IGNORE -> {
                        if (local != null && remote != null) {
                            repo.saveEntry(
                                syncedEntry(profileId, path, local, remote, entry),
                            )
                        } else if (entry != null) {
                            repo.updateEntry(
                                profileId = profileId,
                                relativePath = path,
                                localUri = entry.localUri,
                                localSize = entry.localSize,
                                localMtime = entry.localMtime,
                                remotePath = entry.remotePath,
                                remoteSize = entry.remoteSize,
                                remoteMtime = entry.remoteMtime,
                                baseLocalSize = entry.baseLocalSize,
                                baseLocalMtime = entry.baseLocalMtime,
                                baseRemoteSize = entry.baseRemoteSize,
                                baseRemoteMtime = entry.baseRemoteMtime,
                                hasBase = entry.hasBase,
                                status = "ignored",
                                uploadId = entry.uploadId,
                                bytesDone = entry.bytesDone,
                                error = null,
                                conflict = entry.conflict,
                            )
                        }
                    }
                    Action.CONFLICT -> {
                        conflicts += 1
                        repo.saveEntry(
                            conflictEntry(profileId, path, local, remote, entry),
                        )
                    }
                    Action.UPLOAD -> {
                        if (local == null) continue
                        uploadFile(api, profileId, config, local, entry, repo, remoteDirs)
                        entries[path] = local.toEntry(profileId, config, remote)
                        done += 1
                    }
                    Action.DOWNLOAD -> {
                        if (remote == null) continue
                        downloadFile(api, config, profileId, path, remote, repo)
                        done += 1
                    }
                    Action.DELETE_REMOTE -> {
                        remoteFull(config, path)?.let { full ->
                            deleteRemote(api, config.rootId, full)
                        }
                        repo.deleteEntry(profileId, path)
                        entries.remove(path)
                        done += 1
                    }
                    Action.DELETE_LOCAL -> {
                        local?.let { SafScanner.delete(applicationContext, it.uri) }
                        repo.deleteEntry(profileId, path)
                        entries.remove(path)
                        done += 1
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: FileHubApiException) {
                if (error.code == "REMOTE_OFFLINE") {
                    return fail(repo, config, applicationContext.getString(R.string.sync_remote_offline))
                }
                failed += 1
                repo.saveEntry(
                    (entry ?: blankEntry(profileId, path)).copy(
                        status = "error",
                        error = error.toUserMessage(applicationContext),
                        updatedAt = now(),
                    ),
                )
                if (error.statusCode == 401 || error.statusCode == 403 || error.code == "QUOTA_EXCEEDED") {
                    finish(repo, config, done, paths.size, conflicts, failed)
                    if (error.statusCode == 401) {
                        repo.setEnabled(config.profileId, false)
                        return Result.failure()
                    }
                    return Result.success()
                }
            } catch (error: Exception) {
                failed += 1
                repo.saveEntry(
                    (entry ?: blankEntry(profileId, path)).copy(
                        status = "error",
                        error = error.toUserMessage(applicationContext),
                        updatedAt = now(),
                    ),
                )
            }
            FolderSyncNotifications.update(
                applicationContext,
                done + failed + conflicts,
                paths.size,
                path,
                FolderSyncNotifications.idFor(profileId),
                config.serverName,
                profileId,
            )
        }

        finish(repo, config, done, paths.size, conflicts, failed)
        return Result.success()
    }

    private suspend fun uploadFile(
        api: FileHubApi,
        profileId: String,
        config: FolderSyncConfigEntity,
        local: LocalNode,
        existing: FolderSyncEntryEntity?,
        repo: FolderSyncRepository,
        remoteDirs: RemoteDirEnsurer,
    ) {
        val parent = remoteParent(config, local.relativePath)
        remoteDirs.ensure(parent)
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
                rootId = config.rootId,
                path = parent,
                filename = local.name,
                totalSize = local.size,
                chunkSize = chunkSize,
                mtime = local.mtime.takeIf { it > 0 },
            )
            uploadId = created.uploadId
            chunkSize = created.chunkSize
        }
        repo.saveEntry(local.toEntry(profileId, config, null).copy(status = "uploading", uploadId = uploadId))
        val chunks = if (local.size == 0L) 0 else ((local.size + chunkSize - 1) / chunkSize).toInt()
        var received = runCatching { api.uploadStatus(uploadId!!).receivedChunks }
            .getOrDefault(emptyList())
            .toSet()
        for (index in 0 until chunks) {
            if (isStopped) return
            if (index in received) continue
            val offset = index * chunkSize
            val length = min(chunkSize, local.size - offset)
            val data = readLocalChunk(local.uri, offset, length)
            api.uploadChunk(uploadId!!, index, data)
            received = received + index
            repo.updateEntry(
                profileId = profileId,
                relativePath = local.relativePath,
                localUri = local.uri,
                localSize = local.size,
                localMtime = local.mtime,
                remotePath = local.relativePath,
                remoteSize = local.size,
                remoteMtime = local.mtime,
                baseLocalSize = local.size,
                baseLocalMtime = local.mtime,
                baseRemoteSize = local.size,
                baseRemoteMtime = local.mtime,
                hasBase = true,
                status = "uploading",
                uploadId = uploadId,
                bytesDone = min(local.size, received.size.toLong() * chunkSize),
                error = null,
                conflict = null,
            )
        }
        val complete = api.completeUpload(uploadId!!)
        repo.saveEntry(
            local.toEntry(profileId, config, null).copy(
                remotePath = complete.path,
                remoteSize = local.size,
                remoteMtime = local.mtime,
                baseLocalSize = local.size,
                baseLocalMtime = local.mtime,
                baseRemoteSize = local.size,
                baseRemoteMtime = local.mtime,
                hasBase = true,
                status = "synced",
                uploadId = null,
                bytesDone = local.size,
                error = null,
                conflict = null,
                updatedAt = now(),
            ),
        )
    }

    private suspend fun downloadFile(
        api: FileHubApi,
        config: FolderSyncConfigEntity,
        profileId: String,
        path: String,
        remote: RemoteNode,
        repo: FolderSyncRepository,
    ) {
        val file = SafScanner.findOrCreateFile(
            applicationContext,
            Uri.parse(config.localTreeUri),
            path,
            remote.mimeType,
        ).getOrThrow()
        val (profile, token) = (applicationContext as FileHubApplication).container
            .profileAndToken(config.profileId) ?: error(applicationContext.getString(R.string.error_unauthorized))
        val base = profile.baseUrl.toHttpUrlOrNull() ?: error(applicationContext.getString(R.string.error_invalid_url))
        val url = base.newBuilder()
            .addPathSegments("api/v1/download")
            .addQueryParameter("root", config.rootId.toString())
            .addQueryParameter("path", remoteFull(config, path))
            .addQueryParameter("inline", "false")
            .build()
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
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                SafScanner.openOutput(applicationContext, file.uri).use { output ->
                    response.body.byteStream().use { input -> input.copyTo(output) }
                }
            }
        } catch (error: Exception) {
            SafScanner.delete(applicationContext, file.uri.toString())
            throw error
        }
        val localMtime = file.lastModified().coerceAtLeast(0) / 1000
        repo.saveEntry(
            FolderSyncEntryEntity(
                profileId = profileId,
                relativePath = path,
                localUri = file.uri.toString(),
                localSize = remote.size,
                localMtime = localMtime,
                remotePath = remoteFull(config, path),
                remoteSize = remote.size,
                remoteMtime = remote.mtime,
                baseLocalSize = remote.size,
                baseLocalMtime = localMtime,
                baseRemoteSize = remote.size,
                baseRemoteMtime = remote.mtime,
                hasBase = true,
                status = "synced",
                updatedAt = now(),
            ),
        )
    }

    private suspend fun deleteRemote(api: FileHubApi, rootId: Long, path: String) {
        val preview = api.deletePreview(rootId, listOf(path))
        api.deleteConfirm(
            token = preview.token,
            confirmCode = if (preview.requiresCode) preview.confirmCode else null,
        )
    }

    private suspend fun scanRemote(
        api: FileHubApi,
        rootId: Long,
        base: String,
    ): Map<String, RemoteNode> {
        val result = linkedMapOf<String, RemoteNode>()
        suspend fun walk(fullPath: String, prefix: String, depth: Int) {
            if (depth > 32 || result.size > 20_000) return
            var cursor: String? = null
            do {
                val listing = api.list(rootId, fullPath, cursor = cursor, limit = 1000)
                listing.entries.forEach { entry ->
                    if (entry.name.startsWith(".")) return@forEach
                    val relative = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                    result[relative] = RemoteNode(
                        relativePath = relative,
                        isDir = entry.isDir,
                        size = entry.size,
                        mtime = entry.modified,
                        mimeType = entry.mimeType,
                    )
                    if (entry.isDir) {
                        walk(join(fullPath, entry.name), relative, depth + 1)
                    }
                }
                cursor = listing.nextCursor
            } while (cursor != null)
        }
        walk(base, "", 0)
        return result
    }


    private suspend fun readLocalChunk(uri: String, offset: Long, length: Long): ByteArray =
        withContext(Dispatchers.IO) {
            SafScanner.openInput(applicationContext, Uri.parse(uri)).use { input ->
                skipFully(input, offset)
                val buffer = ByteArray(length.toInt())
                var read = 0
                while (read < buffer.size) {
                    val count = input.read(buffer, read, buffer.size - read)
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

    private fun decide(
        config: FolderSyncConfigEntity,
        local: LocalNode?,
        remote: RemoteNode?,
        entry: FolderSyncEntryEntity?,
    ): Action {
        val hasBase = entry?.hasBase == true
        val localChanged = local != null &&
            (!hasBase || local.size != entry!!.baseLocalSize || local.mtime != entry.baseLocalMtime)
        val remoteChanged = remote != null &&
            (!hasBase || remote.size != entry!!.baseRemoteSize || remote.mtime != entry.baseRemoteMtime)

        if (local == null && remote == null) return Action.REMOVE
        if (local != null && remote != null) {
            if (local.size == remote.size && local.mtime == remote.mtime) return Action.IGNORE
            return when (config.mode) {
                "upload" -> when {
                    !hasBase -> Action.CONFLICT
                    localChanged && !remoteChanged -> Action.UPLOAD
                    !localChanged && remoteChanged -> Action.CONFLICT
                    else -> Action.CONFLICT
                }
                "download" -> when {
                    !hasBase -> Action.CONFLICT
                    !localChanged && remoteChanged -> Action.DOWNLOAD
                    localChanged && !remoteChanged -> Action.CONFLICT
                    else -> Action.CONFLICT
                }
                else -> when {
                    localChanged && !remoteChanged -> Action.UPLOAD
                    !localChanged && remoteChanged -> Action.DOWNLOAD
                    else -> Action.CONFLICT
                }
            }
        }
        if (local != null) {
            if (hasBase) {
                if (localChanged) return Action.CONFLICT
                return if (config.deleteSync && config.mode != "download") Action.DELETE_LOCAL else Action.IGNORE
            }
            return if (config.mode == "download") Action.IGNORE else Action.UPLOAD
        }
        // remote only
        if (hasBase) {
            if (remoteChanged) return Action.CONFLICT
            return if (config.deleteSync && config.mode != "upload") Action.DELETE_REMOTE else Action.IGNORE
        }
        return if (config.mode == "upload") Action.IGNORE else Action.DOWNLOAD
    }

    private fun LocalNode.toEntry(
        profileId: String,
        config: FolderSyncConfigEntity,
        remote: RemoteNode?,
    ): FolderSyncEntryEntity = FolderSyncEntryEntity(
        profileId = profileId,
        relativePath = relativePath,
        localUri = uri,
        localSize = size,
        localMtime = mtime,
        remotePath = remoteFull(config, relativePath),
        remoteSize = remote?.size ?: size,
        remoteMtime = remote?.mtime ?: mtime,
        baseLocalSize = size,
        baseLocalMtime = mtime,
        baseRemoteSize = remote?.size ?: size,
        baseRemoteMtime = remote?.mtime ?: mtime,
        hasBase = true,
        status = "synced",
        updatedAt = now(),
    )

    private fun syncedEntry(
        profileId: String,
        path: String,
        local: LocalNode,
        remote: RemoteNode,
        existing: FolderSyncEntryEntity?,
    ): FolderSyncEntryEntity = FolderSyncEntryEntity(
        profileId = profileId,
        relativePath = path,
        localUri = local.uri,
        localSize = local.size,
        localMtime = local.mtime,
        remotePath = remote.relativePath,
        remoteSize = remote.size,
        remoteMtime = remote.mtime,
        baseLocalSize = local.size,
        baseLocalMtime = local.mtime,
        baseRemoteSize = remote.size,
        baseRemoteMtime = remote.mtime,
        hasBase = true,
        status = "synced",
        updatedAt = now(),
    )

    private fun conflictEntry(
        profileId: String,
        path: String,
        local: LocalNode?,
        remote: RemoteNode?,
        existing: FolderSyncEntryEntity?,
    ): FolderSyncEntryEntity = FolderSyncEntryEntity(
        profileId = profileId,
        relativePath = path,
        localUri = local?.uri ?: existing?.localUri.orEmpty(),
        localSize = local?.size ?: existing?.localSize ?: 0,
        localMtime = local?.mtime ?: existing?.localMtime ?: 0,
        remotePath = remote?.relativePath ?: existing?.remotePath.orEmpty(),
        remoteSize = remote?.size ?: existing?.remoteSize ?: 0,
        remoteMtime = remote?.mtime ?: existing?.remoteMtime ?: 0,
        baseLocalSize = existing?.baseLocalSize ?: 0,
        baseLocalMtime = existing?.baseLocalMtime ?: 0,
        baseRemoteSize = existing?.baseRemoteSize ?: 0,
        baseRemoteMtime = existing?.baseRemoteMtime ?: 0,
        hasBase = existing?.hasBase ?: false,
        status = "conflict",
        conflict = "both sides changed",
        updatedAt = now(),
    )

    private fun blankEntry(profileId: String, path: String): FolderSyncEntryEntity =
        FolderSyncEntryEntity(profileId = profileId, relativePath = path)

    private fun remoteFull(config: FolderSyncConfigEntity, path: String): String =
        join(config.remotePath, path)

    private fun remoteParent(config: FolderSyncConfigEntity, path: String): String =
        join(config.remotePath, path.substringBeforeLast('/', ""))

    private fun join(left: String, right: String): String {
        val a = left.trim('/')
        val b = right.trim('/')
        return when {
            a.isEmpty() -> b
            b.isEmpty() -> a
            else -> "$a/$b"
        }
    }

    private suspend fun finish(
        repo: FolderSyncRepository,
        config: FolderSyncConfigEntity,
        done: Int,
        total: Int,
        conflicts: Int,
        failed: Int,
    ) {
        val result = buildString {
            append(applicationContext.getString(R.string.folder_sync_result, done, total))
            if (conflicts > 0) {
                append(" · ")
                append(applicationContext.getString(R.string.folder_sync_conflicts_count, conflicts))
            }
        }
        repo.updateConfig(
            config.copy(
                lastRunAt = now(),
                lastResult = result,
                lastError = if (failed > 0) applicationContext.resources.getQuantityString(R.plurals.sync_failed_count, failed, failed) else null,
                pendingCount = repo.pendingCount(config.profileId),
            ),
        )
        FolderSyncNotifications.finished(
            applicationContext,
            result,
            FolderSyncNotifications.idFor(config.profileId),
            config.serverName,
            config.profileId,
        )
    }

    private suspend fun syncFailure(
        repo: FolderSyncRepository,
        config: FolderSyncConfigEntity,
        error: Throwable,
    ): Result {
        val code = (error as? FileHubApiException)?.code
        val message = error.toUserMessage(applicationContext)
        return if (code == "REMOTE_TOKEN_INVALID" ||
            code == "REMOTE_GRANT_REVOKED" ||
            code == "UNAUTHORIZED" ||
            code == "TOKEN_REVOKED" ||
            code == "TOKEN_EXPIRED"
        ) {
            failPermanently(repo, config, message)
        } else {
            fail(repo, config, message)
        }
    }

    private suspend fun fail(
        repo: FolderSyncRepository,
        config: FolderSyncConfigEntity,
        message: String,
    ): Result {
        repo.updateConfig(config.copy(lastError = message, lastRunAt = now()))
        FolderSyncNotifications.finished(
            applicationContext,
            message,
            FolderSyncNotifications.idFor(config.profileId),
            config.serverName,
            config.profileId,
        )
        return Result.retry()
    }

    private suspend fun failPermanently(
        repo: FolderSyncRepository,
        config: FolderSyncConfigEntity,
        message: String,
    ): Result {
        repo.updateConfig(
            config.copy(enabled = false, lastError = message, lastRunAt = now()),
        )
        FolderSyncScheduler.cancelAll(applicationContext, config.profileId)
        FolderSyncNotifications.finished(
            applicationContext,
            message,
            FolderSyncNotifications.idFor(config.profileId),
            config.serverName,
            config.profileId,
        )
        return Result.failure()
    }

    private fun now(): Long = System.currentTimeMillis() / 1000

    private data class RemoteNode(
        val relativePath: String,
        val isDir: Boolean,
        val size: Long,
        val mtime: Long,
        val mimeType: String = "",
    )

    private enum class Action {
        UPLOAD, DOWNLOAD, DELETE_REMOTE, DELETE_LOCAL, CONFLICT, IGNORE, REMOVE
    }

    companion object {
        private const val UPLOAD_CHUNK = 4L * 1024 * 1024
    }
}
