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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PhotoSyncRepository(
    private val context: Context,
    private val dao: PhotoSyncDao,
    runDao: SyncRunDao,
    logDao: SyncLogDao,
    private val targetGuard: SyncTargetGuard,
) {
    val runs = SyncRunStore(runDao)
    val logs = SyncLogStore(logDao)

    fun configFlow(profileId: String): Flow<PhotoSyncConfigEntity?> = dao.configFlow(profileId)

    suspend fun config(profileId: String): PhotoSyncConfigEntity? = dao.config(profileId)

    suspend fun save(
        config: PhotoSyncConfigEntity,
        resetEntries: Boolean = false,
        allowOverlap: Boolean = false,
    ) {
        if (config.enabled && !allowOverlap &&
            targetGuard.photoConflicts(config.profileId, config.rootId, config.remotePath)
        ) {
            throw SyncTargetOverlapException()
        }
        val previous = dao.config(config.profileId)
        val targetChanged = previous != null && (
            previous.rootId != config.rootId ||
                previous.remotePath.trim('/') != config.remotePath.trim('/') ||
                previous.albumSubfolders != config.albumSubfolders
            )
        val albumsChanged = previous != null && previous.albumsJson != config.albumsJson
        val stored = if (previous == null || targetChanged || albumsChanged) {
            config.copy(
                lastScanAt = 0,
                lastMaxId = 0,
                lastFullScanAt = 0,
                lastScanAlbums = null,
            )
        } else {
            config
        }
        if (targetChanged) {
            PhotoSyncScheduler.cancelAll(context, config.profileId)
            runs.current(config.profileId, SyncRunKind.PHOTO)?.let { run ->
                if (run.syncPhase.isActive) {
                    runs.finish(run, SyncPhase.CANCELLED, SyncCancelReason.TARGET_CHANGED)
                }
            }
        }
        dao.saveConfig(stored)
        if (resetEntries) dao.clearEntries(config.profileId)
        if (stored.enabled) {
            PhotoSyncScheduler.schedulePeriodic(context, stored)
        } else {
            PhotoSyncScheduler.cancelPeriodic(context, stored.profileId)
        }
    }

    suspend fun updateConfig(config: PhotoSyncConfigEntity) = dao.saveConfig(config)

    suspend fun updateScanState(
        profileId: String,
        lastScanAt: Long,
        lastMaxId: Long,
        lastFullScanAt: Long,
        lastScanAlbums: String?,
    ) {
        val current = dao.config(profileId) ?: return
        dao.saveConfig(
            current.copy(
                lastScanAt = lastScanAt,
                lastMaxId = lastMaxId,
                lastFullScanAt = lastFullScanAt,
                lastScanAlbums = lastScanAlbums,
            ),
        )
    }

    suspend fun setEnabled(profileId: String, enabled: Boolean) {
        val current = dao.config(profileId) ?: return
        save(current.copy(enabled = enabled, wifiOnly = current.photosWifiOnly && current.videosWifiOnly))
        if (!enabled) {
            PhotoSyncScheduler.cancelAll(context, profileId)
            runs.current(profileId, SyncRunKind.PHOTO)?.let { run ->
                if (run.syncPhase.isActive) {
                    runs.finish(run, SyncPhase.CANCELLED, SyncCancelReason.CONFIG_CHANGED)
                }
            }
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

    /** User asked to stop or cancel the current run; the worker writes the final phase. */
    suspend fun requestStop(profileId: String, cancel: Boolean) {
        runs.requestStop(profileId, SyncRunKind.PHOTO, stop = !cancel, cancel = cancel)
        PhotoSyncScheduler.cancelAll(context, profileId)
    }

    suspend fun syncNow(profileId: String, fullScan: Boolean = false) {
        val config = dao.config(profileId) ?: return
        PhotoSyncScheduler.enqueueNow(context, config, fullScan)
    }

    suspend fun catchUp(profileId: String) {
        val config = dao.config(profileId) ?: return
        if (config.enabled) PhotoSyncScheduler.enqueueCatchUp(context, config)
    }

    suspend fun disableAndCancel(profileId: String) {
        val current = dao.config(profileId)
        if (current != null) {
            save(current.copy(enabled = false))
        }
        PhotoSyncScheduler.cancelAll(context, profileId)
        runs.current(profileId, SyncRunKind.PHOTO)?.let { run ->
            if (run.syncPhase.isActive) {
                runs.finish(run, SyncPhase.CANCELLED, SyncCancelReason.LOGOUT)
            }
        }
    }

    suspend fun cancel(profileId: String) = PhotoSyncScheduler.cancelAll(context, profileId)

    suspend fun pendingCount(profileId: String): Int = dao.pendingCount(profileId)

    suspend fun clearEntries(profileId: String) = dao.clearEntries(profileId)

    suspend fun entries(profileId: String): List<PhotoSyncEntryEntity> = dao.entries(profileId)

    suspend fun saveEntry(entry: PhotoSyncEntryEntity) = dao.saveEntry(entry)

    suspend fun saveHash(
        profileId: String,
        signature: String,
        sha256: String,
        size: Long,
        mtime: Long,
        hashVersion: Int,
    ) = dao.updateHash(profileId, signature, sha256, size, mtime, hashVersion)

    suspend fun updateEntry(
        profileId: String,
        signature: String,
        status: String,
        uploadId: String?,
        bytesDone: Long,
        remotePath: String,
        error: String?,
    ) {
        dao.updateEntry(
            profileId = profileId,
            signature = signature,
            status = status,
            uploadId = uploadId,
            bytesDone = bytesDone,
            remotePath = remotePath,
            error = error,
            updatedAt = System.currentTimeMillis() / 1000,
        )
    }

    fun selectedAlbums(config: PhotoSyncConfigEntity): Set<Long>? =
        config.albumsJson?.let { raw ->
            runCatching { Json.decodeFromString<List<Long>>(raw).toSet() }.getOrNull()
        }

    fun encodeAlbums(albums: Set<Long>?): String? =
        albums?.let { Json.encodeToString(it.toList()) }
}

object PhotoSyncScheduler {
    private fun periodicName(profileId: String) = "photo-sync-periodic-$profileId"
    private fun nowName(profileId: String) = "photo-sync-now-$profileId"
    private fun catchUpName(profileId: String) = "photo-sync-catchup-$profileId"
    private fun wifiCatchUpName(profileId: String) = "photo-sync-wifi-$profileId"

    fun schedulePeriodic(context: Context, config: PhotoSyncConfigEntity) {
        val interval = when (config.schedule) {
            "15m" -> 15L
            "hourly" -> 60L
            "weekly" -> 7L * 24 * 60
            else -> 24L * 60
        }
        val request = PeriodicWorkRequestBuilder<PhotoSyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints(config))
            .setInputData(workDataOf("profileId" to config.profileId, "trigger" to "periodic"))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            periodicName(config.profileId),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueNow(
        context: Context,
        config: PhotoSyncConfigEntity,
        fullScan: Boolean = false,
    ) {
        val request = OneTimeWorkRequestBuilder<PhotoSyncWorker>()
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

    /** Runs only when the periodic schedule was missed (app opened after a long gap). */
    fun enqueueCatchUp(context: Context, config: PhotoSyncConfigEntity) {
        val request = OneTimeWorkRequestBuilder<PhotoSyncWorker>()
            .setConstraints(constraints(config))
            .setInputData(workDataOf("profileId" to config.profileId, "trigger" to "catchup"))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            catchUpName(config.profileId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Waits for Wi-Fi after a metered run skipped protected photo/video items. */
    fun enqueueWifiCatchUp(context: Context, config: PhotoSyncConfigEntity) {
        val request = OneTimeWorkRequestBuilder<PhotoSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresCharging(config.chargingOnly)
                    .build(),
            )
            .setInputData(workDataOf("profileId" to config.profileId, "trigger" to "wifi"))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            wifiCatchUpName(config.profileId),
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
        WorkManager.getInstance(context).cancelUniqueWork(wifiCatchUpName(profileId))
    }

    private fun constraints(config: PhotoSyncConfigEntity): Constraints {
        val allRequireWifi = config.photosWifiOnly &&
            (!config.includeVideos || config.videosWifiOnly)
        return Constraints.Builder()
            .setRequiredNetworkType(if (allRequireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(config.chargingOnly)
            .build()
    }
}
