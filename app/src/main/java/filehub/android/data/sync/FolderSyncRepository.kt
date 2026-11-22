package filehub.android.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow

class FolderSyncRepository(
    private val context: Context,
    private val dao: FolderSyncDao,
) {
    val config: Flow<FolderSyncConfigEntity?> = dao.configFlow()

    suspend fun config(): FolderSyncConfigEntity? = dao.config()

    suspend fun save(config: FolderSyncConfigEntity) {
        dao.saveConfig(config)
        if (config.enabled) {
            FolderSyncScheduler.schedulePeriodic(context, config)
        } else {
            FolderSyncScheduler.cancelPeriodic(context)
        }
    }

    suspend fun updateConfig(config: FolderSyncConfigEntity) = dao.saveConfig(config)

    suspend fun setEnabled(enabled: Boolean) {
        val current = dao.config() ?: return
        save(current.copy(enabled = enabled))
    }

    suspend fun syncNow() = FolderSyncScheduler.enqueueNow(context)

    suspend fun cancel() = FolderSyncScheduler.cancelAll(context)

    suspend fun entries(): List<FolderSyncEntryEntity> = dao.entries()

    suspend fun saveEntry(entry: FolderSyncEntryEntity) = dao.saveEntry(entry)

    suspend fun updateEntry(
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

    suspend fun deleteEntry(relativePath: String) = dao.deleteEntry(relativePath)

    suspend fun pendingCount(): Int = dao.pendingCount()

    suspend fun conflictCount(): Int = dao.conflictCount()

    suspend fun clearEntries() = dao.clearEntries()
}

object FolderSyncScheduler {
    private const val PERIODIC = "folder-sync-periodic"
    private const val NOW = "folder-sync-now"

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
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<FolderSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
    }
}
