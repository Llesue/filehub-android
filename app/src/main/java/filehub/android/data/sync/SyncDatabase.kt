package filehub.android.data.sync

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "photo_sync_config")
data class PhotoSyncConfigEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
    val profileId: String = "",
    val serverName: String = "",
    val rootId: Long = 0,
    val rootAlias: String = "",
    val remotePath: String = "",
    val albumsJson: String? = null,
    val includeVideos: Boolean = true,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val albumSubfolders: Boolean = true,
    val schedule: String = "daily",
    val lastRunAt: Long? = null,
    val lastResult: String? = null,
    val lastError: String? = null,
    val pendingCount: Int = 0,
)

@Entity(tableName = "photo_sync_entries")
data class PhotoSyncEntryEntity(
    @PrimaryKey val signature: String,
    val localId: Long,
    val bucketId: Long,
    val album: String,
    val name: String,
    val size: Long,
    val mtime: Long,
    val mimeType: String,
    val remotePath: String,
    val status: String = "pending",
    val uploadId: String? = null,
    val bytesDone: Long = 0,
    val error: String? = null,
    val updatedAt: Long = 0,
)

@Dao
interface PhotoSyncDao {
    @Query("SELECT * FROM photo_sync_config WHERE id = 1")
    fun configFlow(): Flow<PhotoSyncConfigEntity?>

    @Query("SELECT * FROM photo_sync_config WHERE id = 1")
    suspend fun config(): PhotoSyncConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: PhotoSyncConfigEntity)

    @Query("DELETE FROM photo_sync_config")
    suspend fun clearConfig()

    @Query("SELECT * FROM photo_sync_entries")
    suspend fun entries(): List<PhotoSyncEntryEntity>

    @Query("SELECT * FROM photo_sync_entries WHERE signature = :signature")
    suspend fun entry(signature: String): PhotoSyncEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEntry(entry: PhotoSyncEntryEntity)

    @Query(
        "UPDATE photo_sync_entries SET status = :status, uploadId = :uploadId, " +
            "bytesDone = :bytesDone, remotePath = :remotePath, error = :error, " +
            "updatedAt = :updatedAt WHERE signature = :signature",
    )
    suspend fun updateEntry(
        signature: String,
        status: String,
        uploadId: String?,
        bytesDone: Long,
        remotePath: String,
        error: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM photo_sync_entries")
    suspend fun clearEntries()

    @Query("SELECT COUNT(*) FROM photo_sync_entries WHERE status != 'done'")
    suspend fun pendingCount(): Int
}

@Database(
    entities = [PhotoSyncConfigEntity::class, PhotoSyncEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun photoSyncDao(): PhotoSyncDao
}
