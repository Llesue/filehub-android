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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun PhotoSyncEntryEntity.cachedHash(
    size: Long,
    mtime: Long,
    version: Int,
): String? = sha256?.takeIf {
    hashVersion == version && hashSize == size && hashMtime == mtime
}

class PhotoSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val profileId = inputData.getString("profileId").orEmpty()
        val config = (applicationContext as FileHubApplication)
            .container.photoSyncRepository.config(profileId)
        return PhotoSyncNotifications.foregroundInfo(
            applicationContext,
            SyncNotificationState(
                phase = SyncPhase.PREPARING,
                serverName = config?.serverName.orEmpty(),
            ),
            profileId,
        )
    }

    override suspend fun doWork(): Result {
        val profileId = inputData.getString("profileId") ?: return Result.success()
        val trigger = inputData.getString("trigger") ?: "periodic"
        val forceFullScan = inputData.getBoolean("fullScan", false)
        val repo = (applicationContext as FileHubApplication).container.photoSyncRepository

        val own = SyncLocks.photo(profileId)
        if (!own.tryLock()) return Result.success()
        return try {
            val shared = SyncLocks.shared(profileId)
            if (shared.isLocked) {
                val queued = repo.runs.begin(
                    profileId,
                    SyncRunKind.PHOTO,
                    trigger,
                    phase = SyncPhase.QUEUED,
                )
                repo.runs.progress(queued, blockReason = SyncBlockReason.ANOTHER_RUN)
                val config = repo.config(profileId)
                PhotoSyncNotifications.update(
                    applicationContext,
                    SyncNotificationState(
                        phase = SyncPhase.QUEUED,
                        serverName = config?.serverName.orEmpty(),
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
        val repo = app.container.photoSyncRepository
        val config = repo.config(profileId) ?: return Result.success()
        if (!config.enabled) {
            repo.runs.current(profileId, SyncRunKind.PHOTO)?.let { run ->
                if (run.syncPhase.isActive) {
                    repo.runs.finish(run, SyncPhase.CANCELLED, SyncCancelReason.CONFIG_CHANGED)
                }
            }
            PhotoSyncNotifications.cancel(applicationContext, profileId)
            return Result.success()
        }

        val run = repo.runs.begin(profileId, SyncRunKind.PHOTO, trigger)
        repo.logs.log(
            profileId,
            SyncRunKind.PHOTO,
            applicationContext.getString(
                R.string.sync_log_started,
                SyncText.trigger(applicationContext, trigger),
                "${config.rootAlias}:${config.remotePath}",
            ),
            SyncLogStore.LEVEL_START,
        )

        if (!hasPhotoPermission(config)) {
            val message = applicationContext.getString(R.string.photo_sync_permission)
            repo.logs.log(profileId, SyncRunKind.PHOTO, message, SyncLogStore.LEVEL_ERROR)
            repo.updateOutcome(profileId, now(), null, message)
            return finishFailed(repo, run, config, message, SyncBlockReason.NO_PERMISSION, retry = false)
        }

        try {
            setForeground(
                PhotoSyncNotifications.foregroundInfo(
                    applicationContext,
                    SyncNotificationState(
                        phase = SyncPhase.PREPARING,
                        serverName = config.serverName,
                    ),
                    profileId,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Foreground promotion is best-effort; state is checkpointed per file.
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !MediaStreams.hasLocationPermission(applicationContext)
        ) {
            repo.logs.log(
                profileId,
                SyncRunKind.PHOTO,
                applicationContext.getString(R.string.photo_sync_location_log),
                SyncLogStore.LEVEL_WARN,
            )
        }
        val remoteDirs = RemoteDirEnsurer(api, config.rootId)
        try {
            remoteDirs.ensure(config.remotePath)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return finishFailure(repo, run, config, error)
        }

        repo.runs.progress(
            run,
            phase = SyncPhase.SCANNING,
            currentAction = "scan",
            clearCurrentItem = true,
        )
        val entries = repo.entries(profileId).associateBy { it.signature }
        val albums = repo.selectedAlbums(config)
        val scanStartedAt = now()
        val albumsChanged = config.lastFullScanAt > 0 &&
            config.lastScanAlbums != config.albumsJson
        val fullScan = forceFullScan ||
            config.lastFullScanAt == 0L ||
            config.lastScanAt == 0L ||
            config.lastMaxId == 0L ||
            albumsChanged ||
            scanStartedAt - config.lastFullScanAt >= FULL_SCAN_INTERVAL_SECONDS
        repo.logs.log(
            profileId,
            SyncRunKind.PHOTO,
            applicationContext.getString(
                if (fullScan) R.string.sync_log_scan_full else R.string.sync_log_scan_incremental,
            ),
        )
        val scanned = withContext(Dispatchers.IO) {
            MediaScanner.photos(
                context = applicationContext,
                selectedAlbums = albums,
                includeVideos = config.includeVideos,
                modifiedAfter = if (fullScan) null else config.lastScanAt,
                idAfter = if (fullScan) null else config.lastMaxId,
            )
        }.getOrElse { error ->
            return finishFailed(
                repo,
                run,
                config,
                error.toUserMessage(applicationContext),
                SyncBlockReason.NETWORK,
            )
        }
        val pendingPhotos = entries.values
            .filter { it.status != STATUS_DONE && (albums == null || it.bucketId in albums) }
            .mapNotNull { MediaScanner.photoFromEntry(applicationContext, it) }
        val merged = (scanned + pendingPhotos).distinctBy { it.localId }
        val metered = NetworkStatus.isMetered(applicationContext)
        val photosAllowed = !config.photosWifiOnly || !metered
        val videosAllowed = config.includeVideos && (!config.videosWifiOnly || !metered)
        val photos = merged.filter { photo ->
            if (photo.isVideo) videosAllowed else photosAllowed
        }
        val skipped = merged.size - photos.size
        if (skipped > 0 && metered) {
            PhotoSyncScheduler.enqueueWifiCatchUp(applicationContext, config)
        }
        if (skipped == 0) {
            repo.updateScanState(
                profileId = profileId,
                lastScanAt = (scanStartedAt - 1).coerceAtLeast(0),
                lastMaxId = merged.maxOfOrNull { it.localId } ?: config.lastMaxId,
                lastFullScanAt = if (fullScan) scanStartedAt else config.lastFullScanAt,
                lastScanAlbums = config.albumsJson,
            )
        }
        val bytesTotal = photos.sumOf { it.size }
        repo.logs.log(
            profileId,
            SyncRunKind.PHOTO,
            applicationContext.getString(
                R.string.sync_log_scan_photos,
                photos.size,
                android.text.format.Formatter.formatShortFileSize(applicationContext, bytesTotal),
            ),
        )
        if (skipped > 0) {
            repo.logs.log(
                profileId,
                SyncRunKind.PHOTO,
                applicationContext.getString(R.string.sync_log_skipped_wifi, skipped),
                SyncLogStore.LEVEL_WARN,
            )
        }
        if (photos.isEmpty()) {
            repo.logs.log(
                profileId,
                SyncRunKind.PHOTO,
                applicationContext.getString(R.string.sync_log_no_changes),
            )
            return finishCompleted(repo, run, config, 0, 0, skipped, 0, 0, 0, 0)
        }
        repo.runs.progress(
            run,
            phase = SyncPhase.SYNCING,
            total = photos.size,
            bytesTotal = bytesTotal,
            skipped = skipped,
            clearCurrentItem = true,
        )

        val remoteNames = mutableMapOf<String, MutableMap<String, Long>>()
        var done = 0
        var failed = 0
        var alreadyOnServer = 0
        var uploaded = 0
        var skippedFiles = 0
        var renamed = 0
        var bytesDone = 0L
        var lastNotifyAt = 0L
        var lastNotifyBytes = 0L
        var lastTickAt = System.currentTimeMillis()
        var speed = 0L

        suspend fun publish(bytes: Long, item: String?, action: String?, force: Boolean) {
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
                bytesDone = bytes,
                processed = done + failed,
                succeeded = done,
                uploaded = uploaded,
                downloaded = 0,
                skipped = skippedFiles,
                failed = failed,
                currentItem = item,
                currentAction = action,
            )
            PhotoSyncNotifications.update(
                applicationContext,
                SyncNotificationState(
                    phase = SyncPhase.SYNCING,
                    total = photos.size,
                    processed = done + failed,
                    failed = failed,
                    bytesTotal = bytesTotal,
                    bytesDone = bytes,
                    currentItem = item,
                    speedBytesPerSecond = speed,
                    serverName = config.serverName,
                ),
                profileId,
            )
        }

        for ((index, photo) in photos.withIndex()) {
            val state = repo.runs.current(profileId, SyncRunKind.PHOTO)
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
                    skipped,
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
                    skipped,
                    bytesDone,
                )
            }

            val currentItem = "${photo.album}/${photo.name}"
            var existing = entries[photo.signature]
            val baseBytes = bytesDone
            try {
                val remoteDir = remoteDir(config, photo)
                val oldEntry = existing
                if (oldEntry != null &&
                    oldEntry.relativePath.isBlank() &&
                    photo.relativePath.isNotBlank()
                ) {
                    val remoteName = oldEntry.remotePath.substringAfterLast('/')
                    val newPath = if (remoteDir.isEmpty()) remoteName else "$remoteDir/$remoteName"
                    if (newPath != oldEntry.remotePath) {
                        val updated = oldEntry.copy(
                            relativePath = photo.relativePath,
                            remotePath = newPath,
                        )
                        existing = updated
                        repo.saveEntry(updated)
                    }
                }
                remoteDirs.ensure(remoteDir)
                val hashVersion = MediaStreams.hashVersion(applicationContext, photo.uri)
                // Fast path: an unchanged entry whose remote file is still
                // listed with the same size needs no hash request at all.
                if (!forceFullScan &&
                    existing != null &&
                    existing.status == STATUS_DONE &&
                    existing.sha256 != null &&
                    existing.hashVersion == hashVersion &&
                    existing.hashSize == photo.size &&
                    existing.hashMtime == photo.mtime &&
                    existing.remotePath.substringBeforeLast('/', "") == remoteDir
                ) {
                    val savedName = existing.remotePath.substringAfterLast('/')
                    val names = runCatching {
                        remoteNamesFor(api, config.rootId, remoteDir, remoteNames)
                    }.getOrNull()
                    if (savedName.isNotBlank() && names?.get(savedName) == photo.size) {
                        repo.logs.log(
                            profileId,
                            SyncRunKind.PHOTO,
                            applicationContext.getString(
                                R.string.sync_log_skipped_unchanged,
                                "${photo.album}/${photo.name}",
                            ),
                        )
                        skippedFiles += 1
                        done += 1
                        alreadyOnServer += 1
                        bytesDone = baseBytes + photo.size
                        publish(bytesDone, currentItem, "skip", true)
                        continue
                    }
                }
                publish(baseBytes, currentItem, "verify", true)
                val cachedHash = existing?.cachedHash(photo.size, photo.mtime, hashVersion)
                val sha256 = if (hashSupported) {
                    cachedHash ?: SyncHasher.sha256(applicationContext, photo.uri.toString())
                        ?.also { hash ->
                            repo.saveHash(
                                profileId = profileId,
                                signature = photo.signature,
                                sha256 = hash,
                                size = photo.size,
                                mtime = photo.mtime,
                                hashVersion = hashVersion,
                            )
                        }
                } else {
                    null
                }
                if (hashSupported && sha256 == null) {
                    error("cannot read photo for hashing")
                }
                val target = selectRemoteTarget(
                    api = api,
                    rootId = config.rootId,
                    remoteDir = remoteDir,
                    cache = remoteNames,
                    photo = photo,
                    existing = existing,
                    sha256 = sha256,
                    hashSupported = hashSupported,
                )
                if (target.alreadyExists) {
                    repo.logs.log(
                        profileId,
                        SyncRunKind.PHOTO,
                        applicationContext.getString(
                            R.string.sync_log_skipped_existing,
                            "${photo.album}/${photo.name}",
                        ),
                    )
                    repo.saveEntry(
                        entry(
                            profileId, photo, remoteDir, target.name, STATUS_DONE,
                            null, photo.size, sha256 = sha256,
                            hashSize = photo.size, hashMtime = photo.mtime,
                            hashVersion = if (target.hashMatched) {
                                hashVersion
                            } else {
                                existing?.hashVersion ?: 0
                            },
                        ),
                    )
                    skippedFiles += 1
                    done += 1
                    alreadyOnServer += 1
                    bytesDone = baseBytes + photo.size
                    publish(bytesDone, currentItem, "skip", true)
                    continue
                }
                if (hashBatch && sha256 != null) {
                    val sameContent = findSameContent(
                        api = api,
                        rootId = config.rootId,
                        remoteDir = remoteDir,
                        names = remoteNames[remoteDir].orEmpty(),
                        size = photo.size,
                        sha256 = sha256,
                        excludeName = target.name,
                    )
                    if (sameContent != null) {
                        repo.saveEntry(
                            entry(
                                profileId, photo, remoteDir, target.name, STATUS_DONE,
                                null, photo.size, sha256 = sha256,
                                hashSize = photo.size, hashMtime = photo.mtime,
                                hashVersion = hashVersion,
                                remotePath = sameContent,
                            ),
                        )
                        repo.logs.log(
                            profileId,
                            SyncRunKind.PHOTO,
                            applicationContext.getString(
                                R.string.sync_log_skipped_same_content,
                                "${photo.album}/${photo.name}",
                                sameContent,
                            ),
                        )
                        skippedFiles += 1
                        done += 1
                        alreadyOnServer += 1
                        bytesDone = baseBytes + photo.size
                        publish(bytesDone, currentItem, "skip", true)
                        continue
                    }
                }
                if (target.name != photo.name) {
                    renamed += 1
                    repo.logs.log(
                        profileId,
                        SyncRunKind.PHOTO,
                        applicationContext.getString(
                            R.string.sync_log_renamed,
                            photo.name,
                            target.name,
                        ),
                        SyncLogStore.LEVEL_WARN,
                    )
                }
                repo.saveEntry(
                    entry(
                        profileId, photo, remoteDir, target.name, STATUS_PENDING,
                        null, 0, sha256 = sha256,
                        hashSize = photo.size, hashMtime = photo.mtime,
                        hashVersion = hashVersion,
                    ),
                )
                uploadPhotoWithRoot(
                    api = api,
                    profileId = profileId,
                    rootId = config.rootId,
                    photo = photo,
                    remoteDir = remoteDir,
                    remoteName = target.name,
                    existing = existing,
                    repo = repo,
                    sha256 = sha256,
                    verifyUpload = uploadVerify,
                    hashVersion = hashVersion,
                    onBytes = { chunkBytes ->
                        publish(
                            baseBytes + chunkBytes,
                            currentItem,
                            if (target.name != photo.name) "rename" else "upload",
                            false,
                        )
                    },
                )
                repo.logs.log(
                    profileId,
                    SyncRunKind.PHOTO,
                    applicationContext.getString(
                        R.string.sync_log_uploaded,
                        "${photo.album}/${photo.name}",
                    ),
                    SyncLogStore.LEVEL_SUCCESS,
                )
                uploaded += 1
                done += 1
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
                    )
                }
                failed += 1
                val failure = error.toUserMessage(applicationContext)
                repo.logs.log(
                    profileId,
                    SyncRunKind.PHOTO,
                    applicationContext.getString(
                        R.string.sync_log_failed,
                        "${photo.album}/${photo.name}",
                        failure,
                    ),
                    SyncLogStore.LEVEL_ERROR,
                )
                repo.saveEntry(
                    entry(
                        profileId = profileId,
                        photo = photo,
                        remoteDir = remoteDir(config, photo),
                        remoteName = existing?.remotePath?.substringAfterLast('/') ?: photo.name,
                        status = STATUS_ERROR,
                        error = failure,
                        bytesDone = existing?.bytesDone ?: 0,
                    ),
                )
                if (error.statusCode == 401 || error.statusCode == 403 || error.code == "QUOTA_EXCEEDED") {
                    val reason = if (error.statusCode == 401) {
                        SyncBlockReason.AUTH_FAILED
                    } else {
                        SyncBlockReason.QUOTA_EXCEEDED
                    }
                    return finishFailed(
                        repo,
                        run,
                        config,
                        error.toUserMessage(applicationContext),
                        reason,
                        retry = false,
                        disable = error.statusCode == 401,
                        done = done,
                        failed = failed,
                        skipped = skipped,
                        bytesDone = baseBytes,
                    )
                }
            } catch (error: Exception) {
                failed += 1
                val failure = error.toUserMessage(applicationContext)
                repo.logs.log(
                    profileId,
                    SyncRunKind.PHOTO,
                    applicationContext.getString(
                        R.string.sync_log_failed,
                        "${photo.album}/${photo.name}",
                        failure,
                    ),
                    SyncLogStore.LEVEL_ERROR,
                )
                repo.saveEntry(
                    entry(
                        profileId = profileId,
                        photo = photo,
                        remoteDir = remoteDir(config, photo),
                        remoteName = existing?.remotePath?.substringAfterLast('/') ?: photo.name,
                        status = STATUS_ERROR,
                        error = failure,
                        bytesDone = existing?.bytesDone ?: 0,
                    ),
                )
            }
            bytesDone = baseBytes + photo.size
            if ((done + failed) % 25 == 0) {
                repo.logs.log(
                    profileId,
                    SyncRunKind.PHOTO,
                    applicationContext.getString(
                        R.string.sync_log_progress,
                        done + failed,
                        photos.size,
                        currentItem,
                    ),
                )
            }
            publish(
                bytesDone,
                currentItem,
                null,
                force = index == photos.lastIndex || bytesDone == bytesTotal,
            )
        }

        if (alreadyOnServer > 0) {
            repo.logs.log(
                profileId,
                SyncRunKind.PHOTO,
                applicationContext.getString(R.string.sync_log_existing, alreadyOnServer),
            )
        }
        return finishCompleted(
            repo,
            run,
            config,
            done,
            failed,
            skipped,
            uploaded,
            skippedFiles,
            renamed,
            bytesDone,
        )
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
        sha256: String?,
        verifyUpload: Boolean,
        hashVersion: Int,
        onBytes: suspend (Long) -> Unit,
    ): String {
        val uploadSha = if (verifyUpload) sha256 else null
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
                    // The file changed since this upload session was created.
                    uploadId = null
                }
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
                sha256 = uploadSha,
                verify = if (uploadSha != null) "auto" else null,
            )
            uploadId = created.uploadId
            chunkSize = created.chunkSize
        }
        val sessionId = uploadId ?: error("upload session missing")
        repo.saveEntry(
            entry(
                profileId, photo, remoteDir, remoteName, STATUS_RUNNING, null, 0, sessionId,
                sha256 = sha256, hashSize = photo.size, hashMtime = photo.mtime,
                hashVersion = hashVersion,
            ),
        )
        val chunks = if (photo.size == 0L) 0 else ((photo.size + chunkSize - 1) / chunkSize).toInt()
        var received = runCatching { api.uploadStatus(sessionId).receivedChunks }
            .getOrDefault(emptyList())
            .toSet()
        for (index in 0 until chunks) {
            if (isStopped) throw CancellationException("sync stopped")
            if (index in received) continue
            val offset = index * chunkSize
            val length = min(chunkSize, photo.size - offset)
            val data = readChunk(photo.uri.toString(), offset, length)
            api.uploadChunk(sessionId, index, data)
            received = received + index
            repo.updateEntry(
                profileId = profileId,
                signature = photo.signature,
                status = STATUS_RUNNING,
                uploadId = sessionId,
                bytesDone = min(photo.size, received.size.toLong() * chunkSize),
                remotePath = "$remoteDir/$remoteName",
                error = null,
            )
            onBytes(min(photo.size, received.size.toLong() * chunkSize))
        }
        val complete = api.completeUpload(sessionId)
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
                sha256 = sha256,
                hashSize = photo.size,
                hashMtime = photo.mtime,
                hashVersion = hashVersion,
            ),
        )
        onBytes(photo.size)
        return complete.path
    }

    /** Returns the ready SHA-256 for a remote path, or null when unavailable. */
    private suspend fun remoteHash(api: FileHubApi, rootId: Long, path: String): String? {
        val first = runCatching { api.getHash(rootId, path, wait = true) }.getOrNull()
        if (first?.status == "ready") return first.sha256
        if (first?.status == "pending") {
            delay(first.retryAfterMs.takeIf { it in 1..3000 } ?: 1000)
            val second = runCatching { api.getHash(rootId, path, wait = true) }.getOrNull()
            if (second?.status == "ready") return second.sha256
        }
        return null
    }

    /** Finds a different remote file with the same content in this directory. */
    private suspend fun findSameContent(
        api: FileHubApi,
        rootId: Long,
        remoteDir: String,
        names: Map<String, Long>,
        size: Long,
        sha256: String,
        excludeName: String,
    ): String? {
        val candidates = names.entries
            .asSequence()
            .filter { it.value == size && it.key != excludeName }
            .take(100)
            .map { it.key }
            .toList()
        if (candidates.isEmpty()) return null
        val hashes = runCatching {
            api.hashes(rootId, candidates.map { "$remoteDir/$it" })
        }.getOrNull() ?: return null
        return hashes.firstOrNull { it.status == "ready" && it.sha256 == sha256 }?.path
    }

    private suspend fun remoteNamesFor(
        api: FileHubApi,
        rootId: Long,
        remoteDir: String,
        cache: MutableMap<String, MutableMap<String, Long>>,
    ): MutableMap<String, Long> = cache.getOrPut(remoteDir) {
        val result = mutableMapOf<String, Long>()
        var cursor: String? = null
        var pages = 0
        do {
            val listing = api.list(
                rootId = rootId,
                path = remoteDir,
                cursor = cursor,
                limit = 1000,
                includeSizes = true,
            )
            listing.entries.filterNot { it.isDir }.forEach { result[it.name] = it.size }
            cursor = listing.nextCursor
            pages += 1
        } while (cursor != null && pages < 20)
        result.toMutableMap()
    }

    private suspend fun selectRemoteTarget(
        api: FileHubApi,
        rootId: Long,
        remoteDir: String,
        cache: MutableMap<String, MutableMap<String, Long>>,
        photo: MediaPhoto,
        existing: PhotoSyncEntryEntity?,
        sha256: String?,
        hashSupported: Boolean,
    ): RemoteTarget {
        val names = remoteNamesFor(api, rootId, remoteDir, cache)
        // Prefer the canonical name, then the name this entry was synced to
        // last time. Reusing that name is what stops (1) growing into (2).
        val savedName = existing?.remotePath
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() && it != photo.name }
        val candidates = (listOf(photo.name) + listOfNotNull(savedName)).distinct()
        val hashUnavailable = mutableSetOf<String>()

        candidates.forEach { name ->
            if (names[name] != photo.size) return@forEach
            if (!hashSupported || sha256 == null) {
                return RemoteTarget(name, alreadyExists = true)
            }
            val remote = remoteHash(api, rootId, "$remoteDir/$name")
            if (remote == null) {
                hashUnavailable += name
            } else if (remote == sha256) {
                return RemoteTarget(name, alreadyExists = true, hashMatched = true)
            }
        }

        // Rows created before original-stream hashing have no usable baseline.
        // Adopt an existing same-size copy once instead of re-uploading it.
        if (existing != null && existing.hashVersion < PHOTO_HASH_VERSION) {
            candidates.firstOrNull { names[it] == photo.size }?.let { name ->
                return RemoteTarget(name, alreadyExists = true)
            }
        }

        // If the server could not prove a mismatch, keep the same-size file
        // rather than creating a duplicate.
        candidates.firstOrNull { it in hashUnavailable }?.let { name ->
            return RemoteTarget(name, alreadyExists = true)
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
            val input = MediaStreams.open(applicationContext, android.net.Uri.parse(uri))
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
        val album = if (config.albumSubfolders) {
            photoAlbumRelativePath(photo.relativePath, photo.album)
        } else {
            ""
        }
        return listOf(base, album).filter { it.isNotEmpty() }.joinToString("/")
    }

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
        sha256: String? = null,
        hashSize: Long = 0,
        hashMtime: Long = 0,
        hashVersion: Int = 0,
    ): PhotoSyncEntryEntity = PhotoSyncEntryEntity(
        profileId = profileId,
        signature = photo.signature,
        localId = photo.localId,
        bucketId = photo.bucketId,
        album = photo.album,
        relativePath = photo.relativePath,
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
        sha256 = sha256,
        hashSize = hashSize,
        hashMtime = hashMtime,
        hashVersion = hashVersion,
    )

    private suspend fun targetChanged(
        repo: PhotoSyncRepository,
        config: PhotoSyncConfigEntity,
    ): Boolean {
        val current = repo.config(config.profileId) ?: return true
        return !current.enabled ||
            current.rootId != config.rootId ||
            current.remotePath.trim('/') != config.remotePath.trim('/') ||
            current.albumSubfolders != config.albumSubfolders
    }

    private suspend fun finishCompleted(
        repo: PhotoSyncRepository,
        run: SyncRunEntity,
        config: PhotoSyncConfigEntity,
        done: Int,
        failed: Int,
        skipped: Int,
        uploaded: Int,
        skippedFiles: Int,
        renamed: Int,
        bytesDone: Long,
    ): Result {
        val result = buildString {
            append(applicationContext.getString(R.string.photo_sync_result, done, done + failed))
            if (skipped > 0) {
                append(" · ")
                append(applicationContext.getString(R.string.sync_waiting_wifi, skipped))
            }
        }
        val error = if (failed > 0) {
            applicationContext.resources.getQuantityString(R.plurals.sync_failed_count, failed, failed)
        } else {
            null
        }
        repo.updateOutcome(config.profileId, now(), result, error)
        val total = run.total.takeIf { it > 0 } ?: (done + failed + skipped)
        val brief = buildList {
            add(
                applicationContext.getString(
                    R.string.sync_brief_total,
                    total,
                    android.text.format.Formatter.formatShortFileSize(applicationContext, bytesDone),
                ),
            )
            add(applicationContext.getString(R.string.sync_brief_duration, durationText(run.startedAt)))
            if (uploaded > 0) {
                add(applicationContext.getString(R.string.sync_brief_uploaded, uploaded))
            }
            if (skippedFiles > 0) {
                add(applicationContext.getString(R.string.sync_brief_skipped, skippedFiles))
            }
            if (renamed > 0) {
                add(applicationContext.getString(R.string.sync_brief_conflicts, renamed))
            }
            if (failed > 0) {
                add(applicationContext.getString(R.string.sync_brief_failed, failed))
            }
        }.joinToString("\n")
        repo.logs.log(
            config.profileId,
            SyncRunKind.PHOTO,
            brief,
            when {
                failed > 0 -> SyncLogStore.LEVEL_ERROR
                renamed > 0 -> SyncLogStore.LEVEL_WARN
                else -> SyncLogStore.LEVEL_SUCCESS
            },
        )
        repo.runs.finish(
            run,
            if (failed > 0) SyncPhase.PARTIAL else SyncPhase.COMPLETED,
            processed = done + failed,
            succeeded = done,
            uploaded = uploaded,
            downloaded = 0,
            skipped = skippedFiles,
            failed = failed,
            conflicts = renamed,
            bytesDone = bytesDone,
        )
        PhotoSyncNotifications.finished(
            applicationContext,
            SyncNotificationState(
                phase = if (failed > 0) SyncPhase.PARTIAL else SyncPhase.COMPLETED,
                total = done + failed,
                processed = done + failed,
                failed = failed,
                bytesTotal = bytesDone,
                bytesDone = bytesDone,
                serverName = config.serverName,
                summary = result,
            ),
            config.profileId,
        )
        return Result.success()
    }

    private suspend fun finishInterrupted(
        repo: PhotoSyncRepository,
        run: SyncRunEntity,
        config: PhotoSyncConfigEntity,
        phase: SyncPhase,
        reason: SyncCancelReason,
        done: Int,
        failed: Int,
        skipped: Int,
        bytesDone: Long,
    ): Result {
        repo.logs.log(
            config.profileId,
            SyncRunKind.PHOTO,
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
            processed = done + failed,
            succeeded = done,
            skipped = skipped,
            failed = failed,
            bytesDone = bytesDone,
        )
        PhotoSyncNotifications.finished(
            applicationContext,
            SyncNotificationState(
                phase = phase,
                total = done + failed,
                processed = done + failed,
                failed = failed,
                bytesTotal = bytesDone,
                bytesDone = bytesDone,
                serverName = config.serverName,
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
        repo: PhotoSyncRepository,
        run: SyncRunEntity,
        config: PhotoSyncConfigEntity,
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
        repo: PhotoSyncRepository,
        run: SyncRunEntity,
        config: PhotoSyncConfigEntity,
        message: String,
        reason: SyncBlockReason,
        retry: Boolean = true,
        disable: Boolean = false,
        done: Int = 0,
        failed: Int = 0,
        skipped: Int = 0,
        bytesDone: Long = 0,
    ): Result {
        repo.updateOutcome(config.profileId, now(), null, message)
        repo.logs.log(
            config.profileId,
            SyncRunKind.PHOTO,
            applicationContext.getString(R.string.sync_log_error, message),
            SyncLogStore.LEVEL_ERROR,
        )
        if (disable) {
            repo.setEnabledColumn(config.profileId, false)
            PhotoSyncScheduler.cancelAll(applicationContext, config.profileId)
        }
        repo.runs.finish(
            run,
            SyncPhase.FAILED,
            blockReason = reason,
            processed = done + failed,
            succeeded = done,
            failed = failed,
            skipped = skipped,
            bytesDone = bytesDone,
        )
        PhotoSyncNotifications.finished(
            applicationContext,
            SyncNotificationState(
                phase = SyncPhase.FAILED,
                failed = failed,
                serverName = config.serverName,
                blockReason = reason,
                summary = message,
            ),
            config.profileId,
        )
        return if (retry) Result.retry() else Result.failure()
    }

    private suspend fun finalizeCancelled(profileId: String) {
        val repo = (applicationContext as FileHubApplication).container.photoSyncRepository
        val run = repo.runs.current(profileId, SyncRunKind.PHOTO) ?: return
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
            SyncRunKind.PHOTO,
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
        PhotoSyncNotifications.cancel(applicationContext, profileId)
        PhotoSyncNotifications.finished(
            applicationContext,
            SyncNotificationState(
                phase = phase,
                serverName = "",
                summary = applicationContext.getString(
                    if (phase == SyncPhase.STOPPED) R.string.sync_phase_stopped else R.string.sync_phase_cancelled,
                ),
            ),
            profileId,
        )
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

    private fun now(): Long = System.currentTimeMillis() / 1000

    private fun durationText(startedAt: Long): String {
        val total = (now() - startedAt).coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }

    private data class RemoteTarget(
        val name: String,
        val alreadyExists: Boolean,
        val hashMatched: Boolean = false,
    )

    companion object {
        private const val STATUS_PENDING = "pending"
        private const val STATUS_RUNNING = "running"
        private const val STATUS_DONE = "done"
        private const val STATUS_ERROR = "error"
        private const val UPLOAD_CHUNK = 4L * 1024 * 1024
        private const val NOTIFY_INTERVAL_MS = 800L
        private const val FULL_SCAN_INTERVAL_SECONDS = 7L * 24 * 60 * 60
    }
}
