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

@Entity(tableName = "folder_sync_config")
data class FolderSyncConfigEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
    val profileId: String = "",
    val serverName: String = "",
    val rootId: Long = 0,
    val rootAlias: String = "",
    val remotePath: String = "",
    val localTreeUri: String = "",
    val localName: String = "",
    val mode: String = "upload",
    val deleteSync: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val schedule: String = "daily",
    val lastRunAt: Long? = null,
    val lastResult: String? = null,
    val lastError: String? = null,
    val pendingCount: Int = 0,
)

@Entity(tableName = "folder_sync_entries")
data class FolderSyncEntryEntity(
    @PrimaryKey val relativePath: String,
    val localUri: String = "",
    val localSize: Long = 0,
    val localMtime: Long = 0,
    val remotePath: String = "",
    val remoteSize: Long = 0,
    val remoteMtime: Long = 0,
    val baseLocalSize: Long = 0,
    val baseLocalMtime: Long = 0,
    val baseRemoteSize: Long = 0,
    val baseRemoteMtime: Long = 0,
    val hasBase: Boolean = false,
    val status: String = "synced",
    val uploadId: String? = null,
    val bytesDone: Long = 0,
    val error: String? = null,
    val conflict: String? = null,
    val updatedAt: Long = 0,
)

@Dao
interface FolderSyncDao {
    @Query("SELECT * FROM folder_sync_config WHERE id = 1")
    fun configFlow(): Flow<FolderSyncConfigEntity?>

    @Query("SELECT * FROM folder_sync_config WHERE id = 1")
    suspend fun config(): FolderSyncConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: FolderSyncConfigEntity)

    @Query("DELETE FROM folder_sync_config")
    suspend fun clearConfig()

    @Query("SELECT * FROM folder_sync_entries")
    suspend fun entries(): List<FolderSyncEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEntry(entry: FolderSyncEntryEntity)

    @Query(
        "UPDATE folder_sync_entries SET localUri = :localUri, localSize = :localSize, " +
            "localMtime = :localMtime, remotePath = :remotePath, remoteSize = :remoteSize, " +
            "remoteMtime = :remoteMtime, baseLocalSize = :baseLocalSize, " +
            "baseLocalMtime = :baseLocalMtime, baseRemoteSize = :baseRemoteSize, " +
            "baseRemoteMtime = :baseRemoteMtime, hasBase = :hasBase, status = :status, " +
            "uploadId = :uploadId, bytesDone = :bytesDone, error = :error, " +
            "conflict = :conflict, updatedAt = :updatedAt WHERE relativePath = :relativePath",
    )
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
        updatedAt: Long,
    )

    @Query("DELETE FROM folder_sync_entries WHERE relativePath = :relativePath")
    suspend fun deleteEntry(relativePath: String)

    @Query("DELETE FROM folder_sync_entries")
    suspend fun clearEntries()

    @Query("SELECT COUNT(*) FROM folder_sync_entries WHERE status = 'conflict'")
    suspend fun conflictCount(): Int

    @Query("SELECT COUNT(*) FROM folder_sync_entries WHERE status NOT IN ('synced')")
    suspend fun pendingCount(): Int
}

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
    entities = [
        PhotoSyncConfigEntity::class,
        PhotoSyncEntryEntity::class,
        FolderSyncConfigEntity::class,
        FolderSyncEntryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun photoSyncDao(): PhotoSyncDao
    abstract fun folderSyncDao(): FolderSyncDao
}
