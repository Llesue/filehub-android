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
    @PrimaryKey val profileId: String,
    val enabled: Boolean = false,
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

@Entity(tableName = "photo_sync_entries", primaryKeys = ["profileId", "signature"])
data class PhotoSyncEntryEntity(
    val profileId: String,
    val signature: String,
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
    @PrimaryKey val profileId: String,
    val enabled: Boolean = false,
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

@Entity(tableName = "folder_sync_entries", primaryKeys = ["profileId", "relativePath"])
data class FolderSyncEntryEntity(
    val profileId: String,
    val relativePath: String,
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
    @Query("SELECT * FROM folder_sync_config WHERE profileId = :profileId")
    fun configFlow(profileId: String): Flow<FolderSyncConfigEntity?>

    @Query("SELECT * FROM folder_sync_config WHERE profileId = :profileId")
    suspend fun config(profileId: String): FolderSyncConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: FolderSyncConfigEntity)

    @Query("DELETE FROM folder_sync_config WHERE profileId = :profileId")
    suspend fun clearConfig(profileId: String)

    @Query("SELECT * FROM folder_sync_entries WHERE profileId = :profileId")
    suspend fun entries(profileId: String): List<FolderSyncEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEntry(entry: FolderSyncEntryEntity)

    @Query(
        "UPDATE folder_sync_entries SET localUri = :localUri, localSize = :localSize, " +
            "localMtime = :localMtime, remotePath = :remotePath, remoteSize = :remoteSize, " +
            "remoteMtime = :remoteMtime, baseLocalSize = :baseLocalSize, " +
            "baseLocalMtime = :baseLocalMtime, baseRemoteSize = :baseRemoteSize, " +
            "baseRemoteMtime = :baseRemoteMtime, hasBase = :hasBase, status = :status, " +
            "uploadId = :uploadId, bytesDone = :bytesDone, error = :error, " +
            "conflict = :conflict, updatedAt = :updatedAt " +
            "WHERE profileId = :profileId AND relativePath = :relativePath",
    )
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
        updatedAt: Long,
    )

    @Query("DELETE FROM folder_sync_entries WHERE profileId = :profileId AND relativePath = :relativePath")
    suspend fun deleteEntry(profileId: String, relativePath: String)

    @Query("DELETE FROM folder_sync_entries WHERE profileId = :profileId")
    suspend fun clearEntries(profileId: String)

    @Query("SELECT COUNT(*) FROM folder_sync_entries WHERE profileId = :profileId AND status = 'conflict'")
    suspend fun conflictCount(profileId: String): Int

    @Query("SELECT COUNT(*) FROM folder_sync_entries WHERE profileId = :profileId AND status NOT IN ('synced')")
    suspend fun pendingCount(profileId: String): Int
}

@Dao
interface PhotoSyncDao {
    @Query("SELECT * FROM photo_sync_config WHERE profileId = :profileId")
    fun configFlow(profileId: String): Flow<PhotoSyncConfigEntity?>

    @Query("SELECT * FROM photo_sync_config WHERE profileId = :profileId")
    suspend fun config(profileId: String): PhotoSyncConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: PhotoSyncConfigEntity)

    @Query("DELETE FROM photo_sync_config WHERE profileId = :profileId")
    suspend fun clearConfig(profileId: String)

    @Query("SELECT * FROM photo_sync_entries WHERE profileId = :profileId")
    suspend fun entries(profileId: String): List<PhotoSyncEntryEntity>

    @Query("SELECT * FROM photo_sync_entries WHERE profileId = :profileId AND signature = :signature")
    suspend fun entry(profileId: String, signature: String): PhotoSyncEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEntry(entry: PhotoSyncEntryEntity)

    @Query(
        "UPDATE photo_sync_entries SET status = :status, uploadId = :uploadId, " +
            "bytesDone = :bytesDone, remotePath = :remotePath, error = :error, " +
            "updatedAt = :updatedAt WHERE profileId = :profileId AND signature = :signature",
    )
    suspend fun updateEntry(
        profileId: String,
        signature: String,
        status: String,
        uploadId: String?,
        bytesDone: Long,
        remotePath: String,
        error: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM photo_sync_entries WHERE profileId = :profileId")
    suspend fun clearEntries(profileId: String)

    @Query("SELECT COUNT(*) FROM photo_sync_entries WHERE profileId = :profileId AND status != 'done'")
    suspend fun pendingCount(profileId: String): Int
}

@Database(
    entities = [
        PhotoSyncConfigEntity::class,
        PhotoSyncEntryEntity::class,
        FolderSyncConfigEntity::class,
        FolderSyncEntryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun photoSyncDao(): PhotoSyncDao
    abstract fun folderSyncDao(): FolderSyncDao
}
