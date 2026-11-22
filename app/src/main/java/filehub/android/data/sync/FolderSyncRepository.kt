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

class FolderSyncRepository(
    private val context: Context,
    private val dao: FolderSyncDao,
) {
    fun configFlow(profileId: String): Flow<FolderSyncConfigEntity?> = dao.configFlow(profileId)

    suspend fun config(profileId: String): FolderSyncConfigEntity? = dao.config(profileId)

    suspend fun save(config: FolderSyncConfigEntity) {
        dao.saveConfig(config)
        if (config.enabled) {
            FolderSyncScheduler.schedulePeriodic(context, config)
        } else {
            FolderSyncScheduler.cancelPeriodic(context, config.profileId)
        }
    }

    suspend fun updateConfig(config: FolderSyncConfigEntity) = dao.saveConfig(config)

    suspend fun setEnabled(profileId: String, enabled: Boolean) {
        val current = dao.config(profileId) ?: return
        save(current.copy(enabled = enabled))
    }

    suspend fun syncNow(profileId: String) = FolderSyncScheduler.enqueueNow(context, profileId)

    suspend fun cancel(profileId: String) = FolderSyncScheduler.cancelAll(context, profileId)

    suspend fun entries(profileId: String): List<FolderSyncEntryEntity> = dao.entries(profileId)

    suspend fun saveEntry(entry: FolderSyncEntryEntity) = dao.saveEntry(entry)

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

    suspend fun clearEntries(profileId: String) = dao.clearEntries(profileId)
}

object FolderSyncScheduler {
    private fun periodicName(profileId: String) = "folder-sync-periodic-$profileId"
    private fun nowName(profileId: String) = "folder-sync-now-$profileId"

    fun schedulePeriodic(context: Context, config: FolderSyncConfigEntity) {
        val interval = when (config.schedule) {
            "15m" -> 15L
            "hourly" -> 60L
            "weekly" -> 7L * 24 * 60
            else -> 24L * 60
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (config.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(config.chargingOnly)
            .build()
        val request = PeriodicWorkRequestBuilder<FolderSyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(workDataOf("profileId" to config.profileId))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            periodicName(config.profileId),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueNow(context: Context, profileId: String) {
        val request = OneTimeWorkRequestBuilder<FolderSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("profileId" to profileId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            nowName(profileId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelPeriodic(context: Context, profileId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(periodicName(profileId))
    }

    fun cancelAll(context: Context, profileId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(periodicName(profileId))
        WorkManager.getInstance(context).cancelUniqueWork(nowName(profileId))
    }
}
