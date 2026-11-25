package filehub.android.data.sync

import filehub.android.R
import android.content.Context
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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
            SyncNotificationState(
                phase = SyncPhase.PREPARING,
                serverName = config?.serverName.orEmpty(),
                        modeLabel = config?.let { modeLabel(it) },
            ),
            profileId,
        )
    }

    override suspend fun doWork(): Result {
        val profileId = inputData.getString("profileId") ?: return Result.success()
        val trigger = inputData.getString("trigger") ?: "periodic"
        val forceFullScan = inputData.getBoolean("fullScan", false)
        val repo = (applicationContext as FileHubApplication).container.folderSyncRepository

        val own = SyncLocks.folder(profileId)
        if (!own.tryLock()) return Result.success()
        return try {
            val shared = SyncLocks.shared(profileId)
            if (shared.isLocked) {
                val queued = repo.runs.begin(
                    profileId,
                    SyncRunKind.FOLDER,
                    trigger,
                    phase = SyncPhase.QUEUED,
                )
                repo.runs.progress(queued, blockReason = SyncBlockReason.ANOTHER_RUN)
                val config = repo.config(profileId)
                FolderSyncNotifications.update(
                    applicationContext,
                    SyncNotificationState(
                        phase = SyncPhase.QUEUED,
                        serverName = config?.serverName.orEmpty(),
                        modeLabel = config?.let { modeLabel(it) },
                        blockReason = SyncBlockReason.ANOTHER_RUN,
                    ),
                    profileId,
                )
            }
            try {
                shared.withLock { runSync(profileId, trigger, forceFullScan) }
            } catch (error: CancellationException) {
                withContext(NonCancellable) { finalizeCancelled(profileId) }
                throw error
            }
        } finally {
            own.unlock()
        }
    }

    private suspend fun runSync(
        profileId: String,
        trigger: String,
        forceFullScan: Boolean,
    ): Result {
        val app = applicationContext as FileHubApplication
        val repo = app.container.folderSyncRepository
        val config = repo.config(profileId) ?: return Result.success()
        if (!config.enabled) {
            repo.runs.current(profileId, SyncRunKind.FOLDER)?.let { run ->
                if (run.syncPhase.isActive) {
                    repo.runs.finish(run, SyncPhase.CANCELLED, SyncCancelReason.CONFIG_CHANGED)
                }
            }
            FolderSyncNotifications.cancel(applicationContext, profileId)
            return Result.success()
        }

        val run = repo.runs.begin(profileId, SyncRunKind.FOLDER, trigger)
        repo.logs.log(
            profileId,
            SyncRunKind.FOLDER,
            applicationContext.getString(
                R.string.sync_log_started,
                SyncText.trigger(applicationContext, trigger),
                "${config.rootAlias}:${config.remotePath}",
            ),
            SyncLogStore.LEVEL_START,
        )

        if (config.deleteSync && photoTargetOverlaps(profileId, config)) {
            return finishFailed(
                repo,
                run,
                config,
                applicationContext.getString(R.string.sync_block_delete_confirmation),
                SyncBlockReason.DELETE_CONFIRMATION_REQUIRED,
                retry = false,
            )
        }

        if (config.localTreeUri.isBlank()) {
            return finishFailed(
                repo,
                run,
                config,
                applicationContext.getString(R.string.folder_sync_error_no_local),
                SyncBlockReason.NO_LOCAL_FOLDER,
                retry = false,
            )
        }

        try {
            setForeground(
                FolderSyncNotifications.foregroundInfo(
                    applicationContext,
                    SyncNotificationState(
                        phase = SyncPhase.PREPARING,
                        serverName = config.serverName,
                modeLabel = modeLabel(config),
                    ),
                    profileId,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Checkpointing still works without foreground promotion.
        }

        val (profile, token) = app.container.profileAndToken(config.profileId)
            ?: return finishFailed(
                repo,
                run,
                config,
                applicationContext.getString(R.string.error_unauthorized),
                SyncBlockReason.AUTH_FAILED,
                retry = false,
                disable = true,
            )
        val baseUrl = app.container.sessionRepository.workingBaseUrl(profile, token)
            ?: return finishFailed(
                repo,
                run,
                config,
                applicationContext.getString(R.string.error_network),
                SyncBlockReason.NETWORK,
            )
        val api = FileHubApi(baseUrl, token)
        val roots = runCatching { api.roots() }
            .getOrElse { error ->
                if (error is FileHubApiException && error.statusCode == 401) {
                    return finishFailed(
                        repo,
                        run,
                        config,
                        applicationContext.getString(R.string.error_unauthorized),
                        SyncBlockReason.AUTH_FAILED,
                        retry = false,
                        disable = true,
                    )
                }
                return finishFailed(
                    repo,
                    run,
                    config,
                    error.toUserMessage(applicationContext),
                    SyncBlockReason.NETWORK,
                )
            }
        val root = roots.firstOrNull { it.id == config.rootId }
            ?: return finishFailed(
                repo,
                run,
                config,
                applicationContext.getString(R.string.sync_root_missing),
                SyncBlockReason.TARGET_MISSING,
                retry = false,
                disable = true,
            )
        if (root.isRemote && root.online == false) {
            return finishFailed(
                repo,
                run,
                config,
                applicationContext.getString(R.string.sync_remote_offline),
                SyncBlockReason.REMOTE_OFFLINE,
            )
        }
        val capabilities = runCatching { api.me().capabilities }.getOrNull()
        val hashSupported = !root.isRemote && capabilities?.hash == true &&
            capabilities.hashAlgo == "sha256"
        val hashBatch = hashSupported && capabilities?.hashBatch == true
        val uploadVerify = hashSupported && capabilities?.hashUploadVerify == true
        val remoteDirs = RemoteDirEnsurer(api, config.rootId)
        try {
            remoteDirs.ensure(config.remotePath)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return finishFailure(repo, run, config, error)
        }

        repo.runs.progress(run, phase = SyncPhase.SCANNING, direction = "local", clearCurrentItem = true)
        val localResult = coroutineScope {
            val heartbeat = launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    repo.runs.progress(run, phase = SyncPhase.SCANNING)
                }
            }
            try {
                withContext(Dispatchers.IO) {
                    SafScanner.scan(applicationContext, Uri.parse(config.localTreeUri))
                }
            } finally {
                heartbeat.cancel()
            }
        }
        val localNodes = localResult.getOrElse { error ->
            return finishFailed(
                repo,
                run,
                config,
                error.toUserMessage(applicationContext),
                SyncBlockReason.NO_LOCAL_FOLDER,
                retry = false,
            )
        }
        val localFiles = localNodes.filterNot { it.isDir }.associateBy { it.relativePath }
        repo.logs.log(
            profileId,
            SyncRunKind.FOLDER,
            applicationContext.getString(R.string.sync_log_scan_local, localFiles.size),
        )
        val mediaExtensions = setOf(
            "jpg", "jpeg", "png", "heic", "heif", "webp", "dng",
            "mp4", "mov", "mkv", "3gp", "avi",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !MediaStreams.hasLocationPermission(applicationContext) &&
            localFiles.keys.any { it.substringAfterLast('.', "").lowercase() in mediaExtensions }
        ) {
            repo.logs.log(
                profileId,
                SyncRunKind.FOLDER,
                applicationContext.getString(R.string.folder_sync_location_log),
                SyncLogStore.LEVEL_WARN,
            )
        }

        repo.runs.progress(run, phase = SyncPhase.SCANNING, direction = "remote")
        val remoteNodes = runCatching { scanRemote(api, config.rootId, config.remotePath) }
            .getOrElse { error ->
                return finishFailed(
                    repo,
                    run,
                    config,
                    error.toUserMessage(applicationContext),
                    SyncBlockReason.NETWORK,
                )
            }
        val remoteFiles = remoteNodes.filterNot { it.value.isDir }.mapValues { it.value }
        val entries = repo.entries(profileId).associateBy { it.relativePath }.toMutableMap()
        val paths = (localFiles.keys + remoteFiles.keys + entries.keys).toSortedSet()
        val bytesTotal = paths.sumOf { path ->
            maxOf(localFiles[path]?.size ?: 0, remoteFiles[path]?.size ?: 0)
        }
        val localHashes = mutableMapOf<String, String>()
        val localVersions = mutableMapOf<String, Int>()
        val verifiedPaths = mutableSetOf<String>()
        val sameContentPaths = if (!hashBatch) {
            emptySet()
        } else {
            coroutineScope {
                val heartbeat = launch {
                    while (isActive) {
                        delay(HEARTBEAT_MS)
                        repo.runs.progress(run, phase = SyncPhase.SCANNING)
                    }
                }
                try {
                    val candidates = paths.filter { path ->
                        val local = localFiles[path]
                        val remote = remoteFiles[path]
                        local != null && remote != null && local.size == remote.size &&
                            (forceFullScan || local.mtime != remote.mtime)
                    }
                    candidates.forEach { path ->
                        val local = localFiles[path] ?: return@forEach
                        val localVersion = MediaStreams.hashVersion(
                            applicationContext,
                            Uri.parse(local.uri),
                        )
                        localVersions[path] = localVersion
                        val cached = if (forceFullScan) {
                            null
                        } else {
                            entries[path]
                                ?.takeIf {
                                    it.hashVersion == localVersion &&
                                        it.sha256 != null && it.hashSize == local.size &&
                                        it.hashMtime == local.mtime
                                }
                                ?.sha256
                        }
                        val hash = cached ?: runCatching {
                            SyncHasher.sha256Saf(applicationContext, local.uri)
                        }.getOrNull()
                        if (hash != null) {
                            localHashes[path] = hash
                            if (cached == null) {
                                repo.saveHash(
                                    profileId,
                                    path,
                                    hash,
                                    local.size,
                                    local.mtime,
                                    localVersion,
                                )
                            }
                        }
                    }
                    val matched = mutableSetOf<String>()
                    val verified = mutableSetOf<String>()
                    localHashes.keys
                        .filter { remoteFiles[it] != null }
                        .chunked(100)
                        .forEach { chunk ->
                            val results = runCatching {
                                api.hashes(config.rootId, chunk)
                            }.getOrNull() ?: return@forEach
                            results.forEach { result ->
                                val localHash = localHashes[result.path]
                                if (result.status == "ready" && result.sha256 != null) {
                                    verified += result.path
                                    if (localHash == result.sha256) matched += result.path
                                }
                            }
                        }
                    verifiedPaths += verified
                    matched
                } finally {
                    heartbeat.cancel()
                }
            }
        }
        if (sameContentPaths.isNotEmpty()) {
            repo.logs.log(
                profileId,
                SyncRunKind.FOLDER,
                applicationContext.getString(
                    R.string.sync_log_hash_matched,
                    sameContentPaths.size,
                ),
            )
        }
        repo.logs.log(
            profileId,
            SyncRunKind.FOLDER,
            applicationContext.getString(R.string.sync_log_scan_remote, remoteFiles.size),
        )
        if (forceFullScan) {
            repo.logs.log(
                profileId,
                SyncRunKind.FOLDER,
                applicationContext.getString(R.string.folder_sync_full_scan_log),
            )
        }
        var plannedNew = 0
        var plannedUpdated = 0
        var plannedDeleted = 0
        var plannedConflicts = 0
        paths.forEach { path ->
            when (
                decide(
                    config,
                    localFiles[path],
                    remoteFiles[path],
                    entries[path],
                    contentEqual = path in sameContentPaths,
                    contentVerified = path in verifiedPaths,
                    verifyContent = forceFullScan,
                )
            ) {
                Action.UPLOAD, Action.DOWNLOAD ->
                    if (entries[path] == null) plannedNew += 1 else plannedUpdated += 1
                Action.DELETE_REMOTE, Action.DELETE_LOCAL -> plannedDeleted += 1
                Action.CONFLICT -> plannedConflicts += 1
                else -> Unit
            }
        }
        repo.logs.log(
            profileId,
            SyncRunKind.FOLDER,
            applicationContext.getString(
                R.string.sync_log_plan,
                plannedNew,
                plannedUpdated,
                plannedDeleted,
                plannedConflicts,
            ),
        )

        repo.runs.progress(
            run,
            phase = SyncPhase.SYNCING,
            total = paths.size,
            bytesTotal = bytesTotal,
            clearCurrentItem = true,
        )

        var done = 0
        var conflicts = 0
        var failed = 0
        var created = 0
        var updated = 0
        var deleted = 0
        var bytesDone = 0L
        var lastNotifyAt = 0L
        var lastNotifyBytes = 0L
        var lastTickAt = System.currentTimeMillis()
        var speed = 0L

        suspend fun publish(
            path: String,
            direction: String?,
            bytes: Long,
            force: Boolean,
            action: String? = direction,
        ) {
            val current = System.currentTimeMillis()
            if (!force && current - lastNotifyAt < NOTIFY_INTERVAL_MS) return
            val elapsed = current - lastTickAt
            if (elapsed > 0 && bytes >= lastNotifyBytes) {
                val instant = (bytes - lastNotifyBytes) * 1000 / elapsed
                speed = if (speed == 0L) instant else (speed * 3 + instant) / 4
            }
            lastTickAt = current
            lastNotifyBytes = bytes
            lastNotifyAt = current
            repo.runs.progress(
                run,
                processed = done + failed + conflicts,
                succeeded = done,
                failed = failed,
                conflicts = conflicts,
                created = created,
                updated = updated,
                deleted = deleted,
                bytesDone = bytes,
                currentItem = path,
                currentAction = action,
                direction = direction,
            )
            val arrow = when (direction) {
                "upload" -> "↑ "
                "download" -> "↓ "
                "delete" -> "× "
                else -> ""
            }
            FolderSyncNotifications.update(
                applicationContext,
                SyncNotificationState(
                    phase = SyncPhase.SYNCING,
                    total = paths.size,
                    processed = done + failed + conflicts,
                    failed = failed,
                    conflicts = conflicts,
                    bytesTotal = bytesTotal,
                    bytesDone = bytes,
                    currentItem = arrow + path,
                    speedBytesPerSecond = speed,
                    serverName = config.serverName,
                modeLabel = modeLabel(config),
                ),
                profileId,
            )
        }

        for ((index, path) in paths.withIndex()) {
            val state = repo.runs.current(profileId, SyncRunKind.FOLDER)
            if (state == null || state.runId != run.runId || !state.syncPhase.isActive) {
                return Result.success()
            }
            if (state.cancelRequested || state.stopRequested) {
                val stop = state.stopRequested && !state.cancelRequested
                return finishInterrupted(
                    repo,
                    run,
                    config,
                    if (stop) SyncPhase.STOPPED else SyncPhase.CANCELLED,
                    if (stop) SyncCancelReason.USER_STOP else SyncCancelReason.USER_CANCEL,
                    done,
                    failed,
                    conflicts,
                    created,
                    updated,
                    deleted,
                    bytesDone,
                )
            }
            if (targetChanged(repo, config)) {
                return finishInterrupted(
                    repo,
                    run,
                    config,
                    SyncPhase.CANCELLED,
                    SyncCancelReason.TARGET_CHANGED,
                    done,
                    failed,
                    conflicts,
                    created,
                    updated,
                    deleted,
                    bytesDone,
                )
            }

            val local = localFiles[path]
            val remote = remoteFiles[path]
            val entry = entries[path]
            val contentEqual = path in sameContentPaths
            val action = decide(
                config,
                local,
                remote,
                entry,
                contentEqual = contentEqual,
                contentVerified = path in verifiedPaths,
                verifyContent = forceFullScan,
            )
            val baseBytes = bytesDone
            val actionBytes = maxOf(local?.size ?: 0, remote?.size ?: 0)
            try {
                when (action) {
                    Action.REMOVE -> {
                        repo.deleteEntry(profileId, path)
                        entries.remove(path)
                        done += 1
                    }
                    Action.IGNORE -> {
                        if (local != null && remote != null) {
                            repo.saveEntry(
                                syncedEntry(
                                    profileId, path, local, remote, entry,
                                    sha256 = localHashes[path],
                                    hashVersion = localVersions[path] ?: 0,
                                ),
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
                        done += 1
                        publish(path, null, bytesDone, true, action = "skip")
                    }
                    Action.CONFLICT -> {
                        conflicts += 1
                        repo.logs.log(
                            profileId,
                            SyncRunKind.FOLDER,
                            applicationContext.getString(R.string.sync_log_conflict, path),
                            SyncLogStore.LEVEL_WARN,
                        )
                        repo.saveEntry(
                            conflictEntry(
                                profileId, path, local, remote, entry,
                                sha256 = localHashes[path],
                                hashSize = local?.size ?: 0,
                                hashMtime = local?.mtime ?: 0,
                                hashVersion = localVersions[path] ?: 0,
                            ),
                        )
                    }
                    Action.UPLOAD -> {
                        if (local == null) continue
                        val localVersion = localVersions[path] ?: MediaStreams.hashVersion(
                            applicationContext,
                            Uri.parse(local.uri),
                        )
                        localVersions[path] = localVersion
                        val localHash = if (hashSupported) {
                            localHashes[path] ?: runCatching {
                                SyncHasher.sha256Saf(applicationContext, local.uri)
                            }.getOrNull()?.also { hash ->
                                localHashes[path] = hash
                                repo.saveHash(
                                    profileId,
                                    path,
                                    hash,
                                    local.size,
                                    local.mtime,
                                    localVersion,
                                )
                            }
                        } else {
                            null
                        }
                        uploadFile(
                            api = api,
                            profileId = profileId,
                            config = config,
                            local = local,
                            existing = entry,
                            repo = repo,
                            remoteDirs = remoteDirs,
                            sha256 = localHash,
                            verifyUpload = uploadVerify,
                            hashVersion = localVersion,
                            onBytes = { bytes -> publish(path, "upload", baseBytes + bytes, false) },
                        )
                        entries[path] = local.toEntry(
                            profileId, config, remote,
                            sha256 = localHash, hashSize = local.size, hashMtime = local.mtime,
                            hashVersion = localVersion,
                        )
                        done += 1
                        if (entry == null) created += 1 else updated += 1
                    }
                    Action.DOWNLOAD -> {
                        if (remote == null) continue
                        downloadFile(
                            api = api,
                            config = config,
                            profileId = profileId,
                            path = path,
                            remote = remote,
                            repo = repo,
                            onBytes = { bytes -> publish(path, "download", baseBytes + bytes, false) },
                        )
                        done += 1
                        if (entry == null) created += 1 else updated += 1
                    }
                    Action.DELETE_REMOTE -> {
                        deleteRemote(api, config.rootId, remoteFull(config, path))
                        repo.deleteEntry(profileId, path)
                        entries.remove(path)
                        done += 1
                        deleted += 1
                        repo.logs.log(
                            profileId,
                            SyncRunKind.FOLDER,
                            applicationContext.getString(R.string.sync_log_deleted, path),
                            SyncLogStore.LEVEL_WARN,
                        )
                    }
                    Action.DELETE_LOCAL -> {
                        local?.let { SafScanner.delete(applicationContext, it.uri) }
                        repo.deleteEntry(profileId, path)
                        entries.remove(path)
                        done += 1
                        deleted += 1
                        repo.logs.log(
                            profileId,
                            SyncRunKind.FOLDER,
                            applicationContext.getString(R.string.sync_log_deleted, path),
                            SyncLogStore.LEVEL_WARN,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: FileHubApiException) {
                if (error.code == "REMOTE_OFFLINE") {
                    return finishFailed(
                        repo,
                        run,
                        config,
                        applicationContext.getString(R.string.sync_remote_offline),
                        SyncBlockReason.REMOTE_OFFLINE,
                        done = done,
                        failed = failed,
                        conflicts = conflicts,
                        bytesDone = baseBytes,
                    )
                }
                failed += 1
                val failure = error.toUserMessage(applicationContext)
                repo.logs.log(
                    profileId,
                    SyncRunKind.FOLDER,
                    applicationContext.getString(R.string.sync_log_failed, path, failure),
                    SyncLogStore.LEVEL_ERROR,
                )
                repo.saveEntry(
                    (entry ?: blankEntry(profileId, path)).copy(
                        status = "error",
                        error = failure,
                        updatedAt = now(),
                    ),
                )
                if (error.statusCode == 401 || error.statusCode == 403 || error.code == "QUOTA_EXCEEDED") {
                    return finishFailed(
                        repo,
                        run,
                        config,
                        error.toUserMessage(applicationContext),
                        if (error.statusCode == 401) {
                            SyncBlockReason.AUTH_FAILED
                        } else {
                            SyncBlockReason.QUOTA_EXCEEDED
                        },
                        retry = false,
                        disable = error.statusCode == 401,
                        done = done,
                        failed = failed,
                        conflicts = conflicts,
                        bytesDone = baseBytes,
                    )
                }
            } catch (error: Exception) {
                failed += 1
                val failure = error.toUserMessage(applicationContext)
                repo.logs.log(
                    profileId,
                    SyncRunKind.FOLDER,
                    applicationContext.getString(R.string.sync_log_failed, path, failure),
                    SyncLogStore.LEVEL_ERROR,
                )
                repo.saveEntry(
                    (entry ?: blankEntry(profileId, path)).copy(
                        status = "error",
                        error = failure,
                        updatedAt = now(),
                    ),
                )
            }
            bytesDone = baseBytes + actionBytes
            if ((done + failed + conflicts) % 25 == 0) {
                repo.logs.log(
                    profileId,
                    SyncRunKind.FOLDER,
                    applicationContext.getString(
                        R.string.sync_log_progress,
                        done + failed + conflicts,
                        paths.size,
                        path,
                    ),
                )
            }
            publish(path, null, bytesDone, force = index == paths.size - 1)
        }

        return finishCompleted(
            repo,
            run,
            config,
            done,
            failed,
            conflicts,
            created,
            updated,
            deleted,
            bytesDone,
        )
    }

    private suspend fun uploadFile(
        api: FileHubApi,
        profileId: String,
        config: FolderSyncConfigEntity,
        local: LocalNode,
        existing: FolderSyncEntryEntity?,
        repo: FolderSyncRepository,
        remoteDirs: RemoteDirEnsurer,
        sha256: String?,
        verifyUpload: Boolean,
        hashVersion: Int,
        onBytes: suspend (Long) -> Unit,
    ) {
        val uploadSha = if (verifyUpload) sha256 else null
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
                if (uploadSha != null && status.sha256Expected != null &&
                    status.sha256Expected != uploadSha
                ) {
                    uploadId = null
                }
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
                sha256 = uploadSha,
                verify = if (uploadSha != null) "auto" else null,
            )
            uploadId = created.uploadId
            chunkSize = created.chunkSize
        }
        val sessionId = uploadId ?: error("upload session missing")
        repo.saveEntry(
            local.toEntry(
                profileId, config, null,
                sha256 = sha256, hashSize = local.size, hashMtime = local.mtime,
                hashVersion = hashVersion,
            ).copy(status = "uploading", uploadId = sessionId),
        )
        val chunks = if (local.size == 0L) 0 else ((local.size + chunkSize - 1) / chunkSize).toInt()
        var received = runCatching { api.uploadStatus(sessionId).receivedChunks }
            .getOrDefault(emptyList())
            .toSet()
        for (index in 0 until chunks) {
            if (isStopped) throw CancellationException("sync stopped")
            if (index in received) continue
            val offset = index * chunkSize
            val length = min(chunkSize, local.size - offset)
            val data = readLocalChunk(local.uri, offset, length)
            api.uploadChunk(sessionId, index, data)
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
                uploadId = sessionId,
                bytesDone = min(local.size, received.size.toLong() * chunkSize),
                error = null,
                conflict = null,
            )
            onBytes(min(local.size, received.size.toLong() * chunkSize))
        }
        val complete = api.completeUpload(sessionId)
        repo.saveEntry(
            local.toEntry(
                profileId, config, null,
                sha256 = sha256, hashSize = local.size, hashMtime = local.mtime,
                hashVersion = hashVersion,
            ).copy(
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
        onBytes(local.size)
    }

    private suspend fun downloadFile(
        api: FileHubApi,
        config: FolderSyncConfigEntity,
        profileId: String,
        path: String,
        remote: RemoteNode,
        repo: FolderSyncRepository,
        onBytes: suspend (Long) -> Unit,
    ) {
        val file = SafScanner.findOrCreateFile(
            applicationContext,
            Uri.parse(config.localTreeUri),
            path,
            remote.mimeType,
        ).getOrThrow()
        val container = (applicationContext as FileHubApplication).container
        val (profile, token) = container
            .profileAndToken(config.profileId) ?: error(applicationContext.getString(R.string.error_unauthorized))
        val downloadBase = container.sessionRepository.workingBaseUrl(profile, token)
            ?: profile.baseUrl
        val base = downloadBase.toHttpUrlOrNull() ?: error(applicationContext.getString(R.string.error_invalid_url))
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
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER)
                        var copied = 0L
                        while (true) {
                            if (isStopped) throw CancellationException("sync stopped")
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            onBytes(copied)
                        }
                    }
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
        onBytes(remote.size)
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
            MediaStreams.open(applicationContext, Uri.parse(uri)).use { input ->
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
        contentEqual: Boolean = false,
        contentVerified: Boolean = false,
        verifyContent: Boolean = false,
    ): Action {
        val base = entry
        val hasBase = base?.hasBase == true
        val localChanged = local != null && (
            base == null || !base.hasBase ||
                local.size != base.baseLocalSize || local.mtime != base.baseLocalMtime
            )
        val remoteChanged = remote != null && (
            base == null || !base.hasBase ||
                remote.size != base.baseRemoteSize || remote.mtime != base.baseRemoteMtime
            )

        if (local == null && remote == null) return Action.REMOVE
        if (local != null && remote != null) {
            if (contentEqual) return Action.IGNORE
            if (verifyContent && contentVerified &&
                local.size == remote.size && local.mtime == remote.mtime
            ) {
                return Action.CONFLICT
            }
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
        sha256: String? = null,
        hashSize: Long = 0,
        hashMtime: Long = 0,
        hashVersion: Int = 0,
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
        sha256 = sha256,
        hashSize = hashSize,
        hashMtime = hashMtime,
        hashVersion = hashVersion,
    )

    private fun syncedEntry(
        profileId: String,
        path: String,
        local: LocalNode,
        remote: RemoteNode,
        existing: FolderSyncEntryEntity?,
        sha256: String? = null,
        hashVersion: Int = 0,
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
        sha256 = sha256,
        hashSize = local.size,
        hashMtime = local.mtime,
        hashVersion = hashVersion,
    )

    private fun conflictEntry(
        profileId: String,
        path: String,
        local: LocalNode?,
        remote: RemoteNode?,
        existing: FolderSyncEntryEntity?,
        sha256: String? = null,
        hashSize: Long = 0,
        hashMtime: Long = 0,
        hashVersion: Int = 0,
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
        sha256 = sha256,
        hashSize = hashSize,
        hashMtime = hashMtime,
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

    private suspend fun targetChanged(
        repo: FolderSyncRepository,
        config: FolderSyncConfigEntity,
    ): Boolean {
        val current = repo.config(config.profileId) ?: return true
        return !current.enabled ||
            current.rootId != config.rootId ||
            current.remotePath.trim('/') != config.remotePath.trim('/') ||
            current.localTreeUri != config.localTreeUri ||
            current.mode != config.mode
    }

    /** Delete sync must never touch files that photo sync owns. */
    private suspend fun photoTargetOverlaps(
        profileId: String,
        config: FolderSyncConfigEntity,
    ): Boolean {
        val photo = (applicationContext as FileHubApplication).container
            .photoSyncRepository.config(profileId) ?: return false
        return SyncTargets.overlaps(
            config.rootId,
            config.remotePath,
            photo.rootId,
            photo.remotePath,
        )
    }

    private suspend fun finishCompleted(
        repo: FolderSyncRepository,
        run: SyncRunEntity,
        config: FolderSyncConfigEntity,
        done: Int,
        failed: Int,
        conflicts: Int,
        created: Int,
        updated: Int,
        deleted: Int,
        bytesDone: Long,
    ): Result {
        val result = buildString {
            append(applicationContext.getString(R.string.folder_sync_result, done, done + failed + conflicts))
            if (conflicts > 0) {
                append(" · ")
                append(applicationContext.getString(R.string.folder_sync_conflicts_count, conflicts))
            }
        }
        val error = if (failed > 0) {
            applicationContext.resources.getQuantityString(R.plurals.sync_failed_count, failed, failed)
        } else {
            null
        }
        repo.updateOutcome(config.profileId, now(), result, error)
        repo.logs.log(
            config.profileId,
            SyncRunKind.FOLDER,
            applicationContext.getString(
                R.string.sync_log_finished_folder,
                done,
                failed,
                conflicts,
            ),
            when {
                failed > 0 -> SyncLogStore.LEVEL_ERROR
                conflicts > 0 -> SyncLogStore.LEVEL_WARN
                else -> SyncLogStore.LEVEL_SUCCESS
            },
        )
        val phase = when {
            failed > 0 -> SyncPhase.PARTIAL
            else -> SyncPhase.COMPLETED
        }
        repo.runs.finish(
            run,
            phase = if (conflicts > 0 && failed == 0) SyncPhase.CONFLICTS_PENDING else phase,
            processed = done + failed + conflicts,
            succeeded = done,
            failed = failed,
            conflicts = conflicts,
            created = created,
            updated = updated,
            deleted = deleted,
            bytesDone = bytesDone,
        )
        FolderSyncNotifications.finished(
            applicationContext,
            SyncNotificationState(
                phase = if (conflicts > 0 && failed == 0) {
                    SyncPhase.CONFLICTS_PENDING
                } else {
                    phase
                },
                total = done + failed + conflicts,
                processed = done + failed + conflicts,
                failed = failed,
                conflicts = conflicts,
                bytesTotal = bytesDone,
                bytesDone = bytesDone,
                serverName = config.serverName,
                modeLabel = modeLabel(config),
                summary = result,
            ),
            config.profileId,
        )
        return Result.success()
    }

    private suspend fun finishInterrupted(
        repo: FolderSyncRepository,
        run: SyncRunEntity,
        config: FolderSyncConfigEntity,
        phase: SyncPhase,
        reason: SyncCancelReason,
        done: Int,
        failed: Int,
        conflicts: Int,
        created: Int,
        updated: Int,
        deleted: Int,
        bytesDone: Long,
    ): Result {
        repo.logs.log(
            config.profileId,
            SyncRunKind.FOLDER,
            applicationContext.getString(
                if (phase == SyncPhase.STOPPED) {
                    R.string.sync_log_stopped
                } else {
                    R.string.sync_log_cancelled
                },
                SyncText.cancelReason(applicationContext, reason),
            ),
            SyncLogStore.LEVEL_WARN,
        )
        repo.runs.finish(
            run,
            phase,
            reason = reason,
            processed = done + failed + conflicts,
            succeeded = done,
            failed = failed,
            conflicts = conflicts,
            created = created,
            updated = updated,
            deleted = deleted,
            bytesDone = bytesDone,
        )
        FolderSyncNotifications.finished(
            applicationContext,
            SyncNotificationState(
                phase = phase,
                total = done + failed + conflicts,
                processed = done + failed + conflicts,
                failed = failed,
                conflicts = conflicts,
                bytesTotal = bytesDone,
                bytesDone = bytesDone,
                serverName = config.serverName,
                modeLabel = modeLabel(config),
                summary = applicationContext.getString(
                    when (phase) {
                        SyncPhase.STOPPED -> R.string.sync_phase_stopped
                        SyncPhase.CANCELLED -> R.string.sync_phase_cancelled
                        else -> R.string.sync_phase_interrupted
                    },
                ),
            ),
            config.profileId,
        )
        return Result.success()
    }

    private suspend fun finishFailure(
        repo: FolderSyncRepository,
        run: SyncRunEntity,
        config: FolderSyncConfigEntity,
        error: Throwable,
    ): Result {
        val code = (error as? FileHubApiException)?.code
        val message = error.toUserMessage(applicationContext)
        val permanent = code == "REMOTE_TOKEN_INVALID" ||
            code == "REMOTE_GRANT_REVOKED" ||
            code == "UNAUTHORIZED" ||
            code == "TOKEN_REVOKED" ||
            code == "TOKEN_EXPIRED"
        return finishFailed(
            repo,
            run,
            config,
            message,
            if (permanent) SyncBlockReason.AUTH_FAILED else SyncBlockReason.NETWORK,
            retry = !permanent,
            disable = permanent,
        )
    }

    private suspend fun finishFailed(
        repo: FolderSyncRepository,
        run: SyncRunEntity,
        config: FolderSyncConfigEntity,
        message: String,
        reason: SyncBlockReason,
        retry: Boolean = true,
        disable: Boolean = false,
        done: Int = 0,
        failed: Int = 0,
        conflicts: Int = 0,
        bytesDone: Long = 0,
    ): Result {
        repo.updateOutcome(config.profileId, now(), null, message)
        repo.logs.log(
            config.profileId,
            SyncRunKind.FOLDER,
            applicationContext.getString(R.string.sync_log_error, message),
            SyncLogStore.LEVEL_ERROR,
        )
        if (disable) {
            repo.setEnabledColumn(config.profileId, false)
            FolderSyncScheduler.cancelAll(applicationContext, config.profileId)
        }
        repo.runs.finish(
            run,
            SyncPhase.FAILED,
            blockReason = reason,
            processed = done + failed + conflicts,
            succeeded = done,
            failed = failed,
            conflicts = conflicts,
            bytesDone = bytesDone,
        )
        FolderSyncNotifications.finished(
            applicationContext,
            SyncNotificationState(
                phase = SyncPhase.FAILED,
                failed = failed,
                conflicts = conflicts,
                serverName = config.serverName,
                modeLabel = modeLabel(config),
                blockReason = reason,
                summary = message,
            ),
            config.profileId,
        )
        return if (retry) Result.retry() else Result.failure()
    }

    private suspend fun finalizeCancelled(profileId: String) {
        val repo = (applicationContext as FileHubApplication).container.folderSyncRepository
        val run = repo.runs.current(profileId, SyncRunKind.FOLDER) ?: return
        if (!run.syncPhase.isActive) return
        val reason = when {
            run.cancelRequested -> SyncCancelReason.USER_CANCEL
            run.stopRequested -> SyncCancelReason.USER_STOP
            else -> SyncCancelReason.SYSTEM_STOPPED
        }
        val phase = if (reason == SyncCancelReason.USER_STOP) {
            SyncPhase.STOPPED
        } else {
            SyncPhase.CANCELLED
        }
        repo.runs.finish(run, phase, reason = reason)
        repo.logs.log(
            profileId,
            SyncRunKind.FOLDER,
            applicationContext.getString(
                if (phase == SyncPhase.STOPPED) {
                    R.string.sync_log_stopped
                } else {
                    R.string.sync_log_cancelled
                },
                SyncText.cancelReason(applicationContext, reason),
            ),
            SyncLogStore.LEVEL_WARN,
        )
        FolderSyncNotifications.cancel(applicationContext, profileId)
        FolderSyncNotifications.finished(
            applicationContext,
            SyncNotificationState(
                phase = phase,
                summary = applicationContext.getString(
                    if (phase == SyncPhase.STOPPED) R.string.sync_phase_stopped else R.string.sync_phase_cancelled,
                ),
            ),
            profileId,
        )
    }

    private fun modeLabel(config: FolderSyncConfigEntity): String =
        applicationContext.getString(
            when (config.mode) {
                "download" -> R.string.folder_sync_mode_download
                "two_way" -> R.string.folder_sync_mode_two_way
                else -> R.string.folder_sync_mode_upload
            },
        )

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
        private const val DOWNLOAD_BUFFER = 64 * 1024
        private const val NOTIFY_INTERVAL_MS = 800L
        private const val HEARTBEAT_MS = 30_000L
    }
}
