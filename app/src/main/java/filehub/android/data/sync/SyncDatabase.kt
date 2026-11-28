package filehub.android.data.sync

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val photosWifiOnly: Boolean = true,
    val videosWifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val albumSubfolders: Boolean = true,
    val schedule: String = "daily",
    val lastRunAt: Long? = null,
    val lastResult: String? = null,
    val lastError: String? = null,
    val pendingCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastScanAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastMaxId: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastFullScanAt: Long = 0,
    val lastScanAlbums: String? = null,
)

const val PHOTO_HASH_VERSION = 1

@Entity(tableName = "photo_sync_entries", primaryKeys = ["profileId", "signature"])
data class PhotoSyncEntryEntity(
    val profileId: String,
    val signature: String,
    val localId: Long,
    val bucketId: Long,
    val album: String,
    val relativePath: String = "",
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
    val sha256: String? = null,
    @ColumnInfo(defaultValue = "0") val hashSize: Long = 0,
    @ColumnInfo(defaultValue = "0") val hashMtime: Long = 0,
    @ColumnInfo(defaultValue = "0") val hashVersion: Int = 0,
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
    val sha256: String? = null,
    @ColumnInfo(defaultValue = "0") val hashSize: Long = 0,
    @ColumnInfo(defaultValue = "0") val hashMtime: Long = 0,
    @ColumnInfo(defaultValue = "0") val hashVersion: Int = 0,
)

@Entity(tableName = "directory_cache", primaryKeys = ["profileId", "rootId", "path", "sort", "order"])
data class DirectoryCacheEntity(
    val profileId: String,
    val rootId: Long,
    val path: String,
    val sort: String,
    val order: String,
    val entriesJson: String,
    val readOnly: Boolean,
    val readOnlyReason: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "browser_state", primaryKeys = ["profileId", "rootId", "path"])
data class BrowserStateEntity(
    val profileId: String,
    val rootId: Long,
    val path: String,
    val sort: String,
    val order: String,
    val view: String,
    val listIndex: Int,
    val listOffset: Int,
    val gridIndex: Int,
    val gridOffset: Int,
    val updatedAt: Long,
)

@Dao
interface BrowserStateDao {
    @Query(
        "SELECT * FROM browser_state WHERE profileId = :profileId AND rootId = :rootId " +
            "AND path = :path",
    )
    suspend fun get(profileId: String, rootId: Long, path: String): BrowserStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: BrowserStateEntity)

    @Query(
        "DELETE FROM browser_state WHERE rowid NOT IN " +
            "(SELECT rowid FROM browser_state ORDER BY updatedAt DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)

    @Query("DELETE FROM browser_state WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: String)
}

@Dao
interface DirectoryCacheDao {
    @Query(
        "SELECT * FROM directory_cache WHERE profileId = :profileId AND rootId = :rootId " +
            "AND path = :path AND sort = :sort AND \"order\" = :order",
    )
    suspend fun get(
        profileId: String,
        rootId: Long,
        path: String,
        sort: String,
        order: String,
    ): DirectoryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: DirectoryCacheEntity)

    @Query(
        "DELETE FROM directory_cache WHERE rowid NOT IN " +
            "(SELECT rowid FROM directory_cache ORDER BY fetchedAt DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)

    @Query("DELETE FROM directory_cache WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: String)
}

@Dao
interface FolderSyncDao {
    @Query("SELECT * FROM folder_sync_config WHERE profileId = :profileId")
    fun configFlow(profileId: String): Flow<FolderSyncConfigEntity?>

    @Query("SELECT * FROM folder_sync_config WHERE profileId = :profileId")
    suspend fun config(profileId: String): FolderSyncConfigEntity?

    @Query("SELECT * FROM folder_sync_config")
    suspend fun configs(): List<FolderSyncConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: FolderSyncConfigEntity)

    @Query(
        "UPDATE folder_sync_config SET lastRunAt = :lastRunAt, lastResult = :lastResult, " +
            "lastError = :lastError, pendingCount = :pendingCount WHERE profileId = :profileId",
    )
    suspend fun updateOutcome(
        profileId: String,
        lastRunAt: Long?,
        lastResult: String?,
        lastError: String?,
        pendingCount: Int,
    )

    @Query("UPDATE folder_sync_config SET enabled = :enabled WHERE profileId = :profileId")
    suspend fun setEnabled(profileId: String, enabled: Boolean)

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

    @Query(
        "UPDATE folder_sync_entries SET sha256 = :sha256, hashSize = :hashSize, " +
            "hashMtime = :hashMtime, hashVersion = :hashVersion " +
            "WHERE profileId = :profileId AND relativePath = :relativePath",
    )
    suspend fun updateHash(
        profileId: String,
        relativePath: String,
        sha256: String,
        hashSize: Long,
        hashMtime: Long,
        hashVersion: Int,
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

    @Query("SELECT * FROM photo_sync_config")
    suspend fun configs(): List<PhotoSyncConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: PhotoSyncConfigEntity)

    @Query(
        "UPDATE photo_sync_config SET lastRunAt = :lastRunAt, lastResult = :lastResult, " +
            "lastError = :lastError, pendingCount = :pendingCount WHERE profileId = :profileId",
    )
    suspend fun updateOutcome(
        profileId: String,
        lastRunAt: Long?,
        lastResult: String?,
        lastError: String?,
        pendingCount: Int,
    )

    @Query("UPDATE photo_sync_config SET enabled = :enabled WHERE profileId = :profileId")
    suspend fun setEnabled(profileId: String, enabled: Boolean)

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

    @Query(
        "UPDATE photo_sync_entries SET sha256 = :sha256, hashSize = :hashSize, " +
            "hashMtime = :hashMtime, hashVersion = :hashVersion " +
            "WHERE profileId = :profileId AND signature = :signature",
    )
    suspend fun updateHash(
        profileId: String,
        signature: String,
        sha256: String,
        hashSize: Long,
        hashMtime: Long,
        hashVersion: Int,
    )

    @Query("DELETE FROM photo_sync_entries WHERE profileId = :profileId")
    suspend fun clearEntries(profileId: String)

    @Query("SELECT COUNT(*) FROM photo_sync_entries WHERE profileId = :profileId AND status != 'done'")
    suspend fun pendingCount(profileId: String): Int
}

@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val kind: String,
    val timestamp: Long,
    val level: String,
    val message: String,
)

@Dao
interface SyncLogDao {
    @Query(
        "SELECT * FROM sync_log WHERE profileId = :profileId AND kind = :kind " +
            "ORDER BY id DESC LIMIT :limit",
    )
    fun flow(profileId: String, kind: String, limit: Int): Flow<List<SyncLogEntity>>

    @Insert
    suspend fun insert(entry: SyncLogEntity)

    @Query(
        "DELETE FROM sync_log WHERE profileId = :profileId AND kind = :kind AND id NOT IN " +
            "(SELECT id FROM sync_log WHERE profileId = :profileId AND kind = :kind " +
            "ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun prune(profileId: String, kind: String, keep: Int)

    @Query("DELETE FROM sync_log WHERE profileId = :profileId AND kind = :kind")
    suspend fun clear(profileId: String, kind: String)

    @Query("DELETE FROM sync_log WHERE profileId = :profileId")
    suspend fun clearProfile(profileId: String)
}

@Database(
    entities = [
        PhotoSyncConfigEntity::class,
        PhotoSyncEntryEntity::class,
        FolderSyncConfigEntity::class,
        FolderSyncEntryEntity::class,
        DirectoryCacheEntity::class,
        BrowserStateEntity::class,
        SyncRunEntity::class,
        SyncLogEntity::class,
    ],
    version = 14,
    exportSchema = false,
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun photoSyncDao(): PhotoSyncDao
    abstract fun folderSyncDao(): FolderSyncDao
    abstract fun directoryCacheDao(): DirectoryCacheDao
    abstract fun browserStateDao(): BrowserStateDao
    abstract fun syncRunDao(): SyncRunDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        /** Moves old default browser states to newest-first without touching custom sorts. */
        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE browser_state SET sort = 'modified', \"order\" = 'desc', " +
                        "listIndex = 0, listOffset = 0, gridIndex = 0, gridOffset = 0 " +
                        "WHERE sort = 'name' AND \"order\" = 'asc'",
                )
            }
        }

        /** Adds per-category transfer counts for the status card. */
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE sync_runs ADD COLUMN uploaded INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE sync_runs ADD COLUMN downloaded INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** Stores the full MediaStore relative path used for the remote layout. */
        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE photo_sync_entries ADD COLUMN relativePath " +
                        "TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /** Adds incremental-scan watermarks and the current sync action. */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE photo_sync_config ADD COLUMN lastScanAt " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE photo_sync_config ADD COLUMN lastMaxId " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE photo_sync_config ADD COLUMN lastFullScanAt " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE photo_sync_config ADD COLUMN lastScanAlbums TEXT")
                db.execSQL("ALTER TABLE sync_runs ADD COLUMN currentAction TEXT")
                db.execSQL(
                    "ALTER TABLE folder_sync_entries ADD COLUMN hashVersion " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** Distinguishes hashes read from the original media stream. */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE photo_sync_entries ADD COLUMN hashVersion " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** Adds cached SHA-256 values to photo and folder sync entries. */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photo_sync_entries ADD COLUMN sha256 TEXT")
                db.execSQL(
                    "ALTER TABLE photo_sync_entries ADD COLUMN hashSize INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE photo_sync_entries ADD COLUMN hashMtime INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE folder_sync_entries ADD COLUMN sha256 TEXT")
                db.execSQL(
                    "ALTER TABLE folder_sync_entries ADD COLUMN hashSize INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE folder_sync_entries ADD COLUMN hashMtime INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** Adds the bounded sync log shown in the right-hand pane. */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `level` TEXT NOT NULL,
                        `message` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
        /** Adds the shared photo/folder run state used for progress and notifications. */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_runs` (
                        `profileId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `runId` TEXT NOT NULL,
                        `phase` TEXT NOT NULL,
                        `blockReason` TEXT,
                        `cancelReason` TEXT,
                        `total` INTEGER NOT NULL,
                        `processed` INTEGER NOT NULL,
                        `succeeded` INTEGER NOT NULL,
                        `skipped` INTEGER NOT NULL,
                        `failed` INTEGER NOT NULL,
                        `bytesTotal` INTEGER NOT NULL,
                        `bytesDone` INTEGER NOT NULL,
                        `currentItem` TEXT,
                        `direction` TEXT,
                        `created` INTEGER NOT NULL,
                        `updated` INTEGER NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `conflicts` INTEGER NOT NULL,
                        `trigger` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `heartbeatAt` INTEGER NOT NULL,
                        `stopRequested` INTEGER NOT NULL,
                        `cancelRequested` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `kind`)
                    )
                    """.trimIndent(),
                )
            }
        }
        /** Adds the per-directory browser state (sort, view, scroll). */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `browser_state` (
                        `profileId` TEXT NOT NULL,
                        `rootId` INTEGER NOT NULL,
                        `path` TEXT NOT NULL,
                        `sort` TEXT NOT NULL,
                        `order` TEXT NOT NULL,
                        `view` TEXT NOT NULL,
                        `listIndex` INTEGER NOT NULL,
                        `listOffset` INTEGER NOT NULL,
                        `gridIndex` INTEGER NOT NULL,
                        `gridOffset` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `rootId`, `path`)
                    )
                    """.trimIndent(),
                )
            }
        }
        /** Splits the old global Wi-Fi switch into separate photo/video policies. */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photo_sync_config ADD COLUMN photosWifiOnly INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE photo_sync_config ADD COLUMN videosWifiOnly INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE photo_sync_config SET photosWifiOnly = wifiOnly, videosWifiOnly = wifiOnly")
            }
        }

        /** Adds the directory metadata cache without touching sync checkpoints. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `directory_cache` (
                        `profileId` TEXT NOT NULL,
                        `rootId` INTEGER NOT NULL,
                        `path` TEXT NOT NULL,
                        `sort` TEXT NOT NULL,
                        `order` TEXT NOT NULL,
                        `entriesJson` TEXT NOT NULL,
                        `readOnly` INTEGER NOT NULL,
                        `readOnlyReason` TEXT,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `rootId`, `path`, `sort`, `order`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
