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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PhotoSyncRepository(
    private val context: Context,
    private val dao: PhotoSyncDao,
) {
    val config: Flow<PhotoSyncConfigEntity?> = dao.configFlow()

    suspend fun config(): PhotoSyncConfigEntity? = dao.config()

    suspend fun save(config: PhotoSyncConfigEntity) {
        dao.saveConfig(config)
        if (config.enabled) {
            PhotoSyncScheduler.schedulePeriodic(context, config)
        } else {
            PhotoSyncScheduler.cancelPeriodic(context)
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        val current = dao.config() ?: return
        save(current.copy(enabled = enabled))
    }

    suspend fun updateConfig(config: PhotoSyncConfigEntity) = dao.saveConfig(config)

    suspend fun syncNow() {
        PhotoSyncScheduler.enqueueNow(context)
    }

    suspend fun cancel() {
        PhotoSyncScheduler.cancelAll(context)
    }

    suspend fun pendingCount(): Int = dao.pendingCount()

    suspend fun clearEntries() = dao.clearEntries()

    suspend fun entries(): List<PhotoSyncEntryEntity> = dao.entries()

    suspend fun saveEntry(entry: PhotoSyncEntryEntity) = dao.saveEntry(entry)

    suspend fun updateEntry(
        signature: String,
        status: String,
        uploadId: String?,
        bytesDone: Long,
        remotePath: String,
        error: String?,
    ) {
        dao.updateEntry(
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
    private const val PERIODIC = "photo-sync-periodic"
    private const val NOW = "photo-sync-now"

    fun schedulePeriodic(context: Context, config: PhotoSyncConfigEntity) {
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
        val request = PeriodicWorkRequestBuilder<PhotoSyncWorker>(
            interval,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PhotoSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
    }
}
