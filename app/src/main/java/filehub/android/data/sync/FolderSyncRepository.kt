package filehub.android.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import filehub.android.FileHubApplication
import filehub.android.R
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow

class FolderSyncRepository(
    private val context: Context,
    private val dao: FolderSyncDao,
    runDao: SyncRunDao,
    logDao: SyncLogDao,
    private val targetGuard: SyncTargetGuard,
) {
    val runs = SyncRunStore(runDao)
    val logs = SyncLogStore(logDao)

    fun configFlow(profileId: String): Flow<FolderSyncConfigEntity?> = dao.configFlow(profileId)

    suspend fun config(profileId: String): FolderSyncConfigEntity? = dao.config(profileId)

    suspend fun save(
        config: FolderSyncConfigEntity,
        allowOverlap: Boolean = false,
    ) {
        if (config.enabled && !allowOverlap &&
            targetGuard.folderConflicts(config.profileId, config.rootId, config.remotePath)
        ) {
            throw SyncTargetOverlapException()
        }
        val previous = dao.config(config.profileId)
        val targetChanged = previous != null && (
            previous.rootId != config.rootId ||
                previous.remotePath.trim('/') != config.remotePath.trim('/') ||
                previous.localTreeUri != config.localTreeUri ||
                previous.mode != config.mode
            )
        if (targetChanged) {
            FolderSyncScheduler.cancelAll(context, config.profileId)
            runs.current(config.profileId, SyncRunKind.FOLDER)?.let { run ->
                if (run.syncPhase.isActive) {
                    runs.finish(run, SyncPhase.CANCELLED, SyncCancelReason.TARGET_CHANGED)
                }
            }
            // The three-way baseline belongs to the old target; keeping it would
            // misreport every file as changed on the next run.
            dao.clearEntries(config.profileId)
        }
        dao.saveConfig(config)
        if (config.enabled) {
            FolderSyncScheduler.schedulePeriodic(context, config)
        } else {
            FolderSyncScheduler.cancelPeriodic(context, config.profileId)
        }
    }

    suspend fun updateConfig(config: FolderSyncConfigEntity) = dao.saveConfig(config)

    suspend fun reconcileSchedules() {
        dao.configs().forEach { config ->
            if (config.enabled) {
                FolderSyncScheduler.ensurePeriodic(context, config)
            } else {
                FolderSyncScheduler.cancelPeriodic(context, config.profileId)
            }
        }
    }

    suspend fun reconcileSchedule(profileId: String) {
        val config = dao.config(profileId) ?: return
        if (config.enabled) {
            FolderSyncScheduler.ensurePeriodic(context, config)
        } else {
            FolderSyncScheduler.cancelPeriodic(context, profileId)
        }
    }

    suspend fun updateOutcome(
        profileId: String,
        lastRunAt: Long?,
        lastResult: String?,
        lastError: String?,
    ) {
        dao.updateOutcome(profileId, lastRunAt, lastResult, lastError, dao.pendingCount(profileId))
    }

    suspend fun setEnabledColumn(profileId: String, enabled: Boolean) = dao.setEnabled(profileId, enabled)

    suspend fun requestStop(profileId: String, cancel: Boolean) {
        runs.requestStop(profileId, SyncRunKind.FOLDER, stop = !cancel, cancel = cancel)
        FolderSyncScheduler.cancelAll(context, profileId)
    }

    suspend fun setEnabled(profileId: String, enabled: Boolean) {
        val current = dao.config(profileId) ?: return
        save(current.copy(enabled = enabled))
        if (!enabled) {
            FolderSyncScheduler.cancelAll(context, profileId)
            runs.current(profileId, SyncRunKind.FOLDER)?.let { run ->
                if (run.syncPhase.isActive) {
                    runs.finish(run, SyncPhase.CANCELLED, SyncCancelReason.CONFIG_CHANGED)
                }
            }
        }
    }

    suspend fun syncNow(profileId: String, fullScan: Boolean = false) {
        val config = dao.config(profileId) ?: return
        FolderSyncScheduler.enqueueNow(context, config, fullScan)
    }

    suspend fun catchUp(profileId: String) {
        val config = dao.config(profileId) ?: return
        if (config.enabled) FolderSyncScheduler.enqueueCatchUp(context, config)
    }

    suspend fun disableAndCancel(profileId: String) {
        val current = dao.config(profileId)
        if (current != null) save(current.copy(enabled = false))
        FolderSyncScheduler.cancelAll(context, profileId)
        runs.current(profileId, SyncRunKind.FOLDER)?.let { run ->
            if (run.syncPhase.isActive) {
                runs.finish(run, SyncPhase.CANCELLED, SyncCancelReason.LOGOUT)
            }
        }
    }

    suspend fun cancel(profileId: String) = FolderSyncScheduler.cancelAll(context, profileId)

    suspend fun entries(profileId: String): List<FolderSyncEntryEntity> = dao.entries(profileId)

    suspend fun saveEntry(entry: FolderSyncEntryEntity) = dao.saveEntry(entry)

    suspend fun saveHash(
        profileId: String,
        relativePath: String,
        sha256: String,
        size: Long,
        mtime: Long,
        hashVersion: Int = PHOTO_HASH_VERSION,
    ) = dao.updateHash(profileId, relativePath, sha256, size, mtime, hashVersion)

    suspend fun updateEntry(
        profileId: String,
        relativePath: String,
        localUri: String,
        localSize: Long,
        localMtime: Long,
        remotePath: String,
        remoteSize: Long,
        remoteMtime: Long,
        baseLocalSize: Long,
        baseLocalMtime: Long,
        baseRemoteSize: Long,
        baseRemoteMtime: Long,
        hasBase: Boolean,
        status: String,
        uploadId: String?,
        bytesDone: Long,
        error: String?,
        conflict: String?,
    ) {
        dao.updateEntry(
            profileId = profileId,
            relativePath = relativePath,
            localUri = localUri,
            localSize = localSize,
            localMtime = localMtime,
            remotePath = remotePath,
            remoteSize = remoteSize,
            remoteMtime = remoteMtime,
            baseLocalSize = baseLocalSize,
            baseLocalMtime = baseLocalMtime,
            baseRemoteSize = baseRemoteSize,
            baseRemoteMtime = baseRemoteMtime,
            hasBase = hasBase,
            status = status,
            uploadId = uploadId,
            bytesDone = bytesDone,
            error = error,
            conflict = conflict,
            updatedAt = System.currentTimeMillis() / 1000,
        )
    }

    suspend fun deleteEntry(profileId: String, relativePath: String) = dao.deleteEntry(profileId, relativePath)

    suspend fun pendingCount(profileId: String): Int = dao.pendingCount(profileId)

    suspend fun conflictCount(profileId: String): Int = dao.conflictCount(profileId)

    /**
     * Resolves one conflict by keeping one side. The other side is deleted, the
     * baseline entry is dropped, and the next sync re-uploads or re-downloads the
     * kept version. Nothing is deleted if the delete call fails.
     */
    suspend fun resolveConflict(
        profileId: String,
        entry: FolderSyncEntryEntity,
        keepLocal: Boolean,
    ): Result<Unit> = runCatching {
        val config = dao.config(profileId) ?: error("folder sync is not configured")
        if (keepLocal) {
            val (profile, token) = (context as FileHubApplication).container
                .profileAndToken(profileId) ?: error(context.getString(R.string.error_unauthorized))
            val api = FileHubApi(profile.baseUrl, token)
            val full = joinRemote(config.remotePath, entry.relativePath)
            try {
                val preview = api.deletePreview(config.rootId, listOf(full))
                api.deleteConfirm(
                    token = preview.token,
                    confirmCode = if (preview.requiresCode) preview.confirmCode else null,
                )
            } catch (error: FileHubApiException) {
                // The remote copy is already gone, which is what this choice wants.
                if (error.statusCode != 404) throw error
            }
        } else if (entry.localUri.isNotBlank()) {
            if (!SafScanner.delete(context, entry.localUri)) {
                error(context.getString(R.string.folder_sync_conflict_delete_failed))
            }
        }
        dao.deleteEntry(profileId, entry.relativePath)
    }

    private fun joinRemote(base: String, path: String): String {
        val a = base.trim('/')
        val b = path.trim('/')
        return when {
            a.isEmpty() -> b
            b.isEmpty() -> a
            else -> "$a/$b"
        }
    }

    suspend fun clearEntries(profileId: String) = dao.clearEntries(profileId)
}

object FolderSyncScheduler {
    private fun periodicName(profileId: String) = "folder-sync-periodic-$profileId"
    private fun nowName(profileId: String) = "folder-sync-now-$profileId"
    private fun catchUpName(profileId: String) = "folder-sync-catchup-$profileId"

    fun schedulePeriodic(context: Context, config: FolderSyncConfigEntity) {
        val interval = scheduleIntervalMinutes(config.schedule)
        if (interval == null || !config.enabled) {
            cancelPeriodic(context, config.profileId)
            return
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            periodicWorkName(config.profileId),
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest(context, config, interval),
        )
    }

    /** Startup reconciliation: update an existing schedule or rebuild it if missing. */
    fun ensurePeriodic(context: Context, config: FolderSyncConfigEntity) {
        val interval = scheduleIntervalMinutes(config.schedule)
        if (interval == null || !config.enabled) {
            cancelPeriodic(context, config.profileId)
            return
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            periodicWorkName(config.profileId),
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest(context, config, interval),
        )
    }

    private fun periodicRequest(
        context: Context,
        config: FolderSyncConfigEntity,
        intervalMinutes: Long,
    ) = PeriodicWorkRequestBuilder<FolderSyncWorker>(
        intervalMinutes,
        TimeUnit.MINUTES,
    )
        .setConstraints(constraints(config))
        .setInputData(workDataOf("profileId" to config.profileId, "trigger" to "periodic"))
        .build()

    fun periodicWorkName(profileId: String): String = periodicName(profileId)

    fun enqueueNow(
        context: Context,
        config: FolderSyncConfigEntity,
        fullScan: Boolean = false,
    ) {
        val request = OneTimeWorkRequestBuilder<FolderSyncWorker>()
            .setConstraints(constraints(config))
            .setInputData(
                workDataOf(
                    "profileId" to config.profileId,
                    "trigger" to if (fullScan) "manual-full" else "manual",
                    "fullScan" to fullScan,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            nowName(config.profileId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueueCatchUp(context: Context, config: FolderSyncConfigEntity) {
        val request = OneTimeWorkRequestBuilder<FolderSyncWorker>()
            .setConstraints(constraints(config))
            .setInputData(workDataOf("profileId" to config.profileId, "trigger" to "catchup"))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            catchUpName(config.profileId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelPeriodic(context: Context, profileId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(periodicName(profileId))
    }

    fun cancelAll(context: Context, profileId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(periodicName(profileId))
        WorkManager.getInstance(context).cancelUniqueWork(nowName(profileId))
        WorkManager.getInstance(context).cancelUniqueWork(catchUpName(profileId))
    }

    private fun constraints(config: FolderSyncConfigEntity): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(if (config.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(config.chargingOnly)
            .build()
}
