package filehub.android.data.sync

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

enum class SyncRunKind(val value: String) {
    PHOTO("photo"),
    FOLDER("folder"),
    ;

    companion object {
        fun from(value: String): SyncRunKind =
            entries.firstOrNull { it.value == value } ?: PHOTO
    }
}

enum class SyncPhase {
    DISABLED,
    IDLE,
    PREPARING,
    QUEUED,
    WAITING,
    SCANNING,
    SYNCING,
    STOPPING,
    CANCELLING,
    STOPPED,
    CANCELLED,
    INTERRUPTED,
    COMPLETED,
    PARTIAL,
    FAILED,
    CONFLICTS_PENDING,
    ;

    val isActive: Boolean
        get() = this == PREPARING || this == QUEUED || this == WAITING ||
            this == SCANNING || this == SYNCING || this == STOPPING || this == CANCELLING

    val isTerminal: Boolean
        get() = this == STOPPED || this == CANCELLED || this == INTERRUPTED ||
            this == COMPLETED || this == PARTIAL || this == FAILED ||
            this == CONFLICTS_PENDING

    companion object {
        fun from(value: String?): SyncPhase =
            entries.firstOrNull { it.name == value } ?: IDLE
    }
}

enum class SyncBlockReason {
    NOT_LOGGED_IN,
    NO_PERMISSION,
    NO_LOCAL_FOLDER,
    NO_TARGET,
    TARGET_MISSING,
    REMOTE_OFFLINE,
    WAITING_WIFI,
    WAITING_CHARGING,
    QUOTA_EXCEEDED,
    AUTH_FAILED,
    ANOTHER_RUN,
    CONFLICTS_PENDING,
    DELETE_CONFIRMATION_REQUIRED,
    NETWORK,
    ;

    companion object {
        fun from(value: String?): SyncBlockReason? =
            entries.firstOrNull { it.name == value }
    }
}

enum class SyncCancelReason {
    USER_STOP,
    USER_CANCEL,
    TARGET_CHANGED,
    CONFIG_CHANGED,
    LOGOUT,
    SERVER_REMOVED,
    AUTH_FAILED,
    SYSTEM_STOPPED,
    WORK_REPLACED,
    PROCESS_DEATH,
    UNKNOWN,
    ;

    companion object {
        fun from(value: String?): SyncCancelReason? =
            entries.firstOrNull { it.name == value }
    }
}

@Entity(tableName = "sync_runs", primaryKeys = ["profileId", "kind"])
data class SyncRunEntity(    val profileId: String,
    val kind: String,
    val runId: String = "",
    val phase: String = SyncPhase.IDLE.name,
    val blockReason: String? = null,
    val cancelReason: String? = null,
    val total: Int = 0,
    val processed: Int = 0,
    val succeeded: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val bytesTotal: Long = 0,
    val bytesDone: Long = 0,
    val currentItem: String? = null,
    val direction: String? = null,
    val created: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val conflicts: Int = 0,
    val trigger: String? = null,
    val startedAt: Long = 0,
    val updatedAt: Long = 0,
    val heartbeatAt: Long = 0,
    val stopRequested: Boolean = false,
    val cancelRequested: Boolean = false,
) {
    val syncPhase: SyncPhase get() = SyncPhase.from(phase)
    val syncBlockReason: SyncBlockReason? get() = SyncBlockReason.from(blockReason)
    val syncCancelReason: SyncCancelReason? get() = SyncCancelReason.from(cancelReason)

    /**
     * A run that claims to be active but stopped updating its heartbeat was killed
     * with the process, force-stopped, or cancelled before it could write a terminal
     * phase. The UI shows it as interrupted instead of a frozen progress bar.
     */
    fun effectivePhase(nowSeconds: Long, staleAfterSeconds: Long = STALE_AFTER_SECONDS): SyncPhase {
        val raw = syncPhase
        if (raw.isActive && heartbeatAt > 0 && nowSeconds - heartbeatAt > staleAfterSeconds) {
            return SyncPhase.INTERRUPTED
        }
        return raw
    }

    companion object {
        const val STALE_AFTER_SECONDS = 120L

        fun idle(profileId: String, kind: SyncRunKind, enabled: Boolean = false): SyncRunEntity =
            SyncRunEntity(
                profileId = profileId,
                kind = kind.value,
                phase = if (enabled) SyncPhase.IDLE.name else SyncPhase.DISABLED.name,
            )
    }
}

@Dao
abstract class SyncRunDao {
    @Query("SELECT * FROM sync_runs WHERE profileId = :profileId AND kind = :kind")
    abstract fun flow(profileId: String, kind: String): Flow<SyncRunEntity?>

    @Query("SELECT * FROM sync_runs WHERE profileId = :profileId AND kind = :kind")
    abstract suspend fun get(profileId: String, kind: String): SyncRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(run: SyncRunEntity)

    @Query(
        "UPDATE sync_runs SET stopRequested = :stop, cancelRequested = :cancel " +
            "WHERE profileId = :profileId AND kind = :kind",
    )
    abstract suspend fun requestStop(
        profileId: String,
        kind: String,
        stop: Boolean,
        cancel: Boolean,
    )

    @Query("DELETE FROM sync_runs WHERE profileId = :profileId")
    abstract suspend fun clearForProfile(profileId: String)

    /**
     * Read-modify-write that ignores workers whose run was already replaced by a newer
     * one. This keeps stop/cancel requests and run ids from being overwritten by a
     * stale worker.
     */
    @Transaction
    open suspend fun mutate(
        profileId: String,
        kind: String,
        runId: String,
        block: (SyncRunEntity) -> SyncRunEntity,
    ) {
        val current = get(profileId, kind) ?: return
        if (current.runId != runId) return
        put(block(current))
    }
}

/** Thin facade so both sync repositories expose the same run API. */
class SyncRunStore(private val dao: SyncRunDao) {
    fun flow(profileId: String, kind: SyncRunKind): Flow<SyncRunEntity?> =
        dao.flow(profileId, kind.value)

    suspend fun current(profileId: String, kind: SyncRunKind): SyncRunEntity? =
        dao.get(profileId, kind.value)

    suspend fun begin(
        profileId: String,
        kind: SyncRunKind,
        trigger: String?,
        phase: SyncPhase = SyncPhase.PREPARING,
        total: Int = 0,
        bytesTotal: Long = 0,
    ): SyncRunEntity {
        val timestamp = SyncRunClock.now()
        val run = SyncRunEntity(
            profileId = profileId,
            kind = kind.value,
            runId = "${timestamp}-${System.nanoTime()}",
            phase = phase.name,
            total = total,
            bytesTotal = bytesTotal,
            trigger = trigger,
            startedAt = timestamp,
            updatedAt = timestamp,
            heartbeatAt = timestamp,
        )
        dao.put(run)
        return run
    }

    suspend fun progress(
        run: SyncRunEntity,
        phase: SyncPhase? = null,
        blockReason: SyncBlockReason? = null,
        total: Int? = null,
        processed: Int? = null,
        succeeded: Int? = null,
        skipped: Int? = null,
        failed: Int? = null,
        bytesTotal: Long? = null,
        bytesDone: Long? = null,
        currentItem: String? = null,
        direction: String? = null,
        created: Int? = null,
        updated: Int? = null,
        deleted: Int? = null,
        conflicts: Int? = null,
        clearBlockReason: Boolean = false,
        clearCurrentItem: Boolean = false,
    ) {
        dao.mutate(run.profileId, run.kind, run.runId) { current ->
            if (current.syncPhase.isTerminal) return@mutate current
            current.copy(
                phase = (phase ?: current.syncPhase).name,
                blockReason = when {
                    clearBlockReason -> null
                    blockReason != null -> blockReason.name
                    else -> current.blockReason
                },
                total = total ?: current.total,
                processed = processed ?: current.processed,
                succeeded = succeeded ?: current.succeeded,
                skipped = skipped ?: current.skipped,
                failed = failed ?: current.failed,
                bytesTotal = bytesTotal ?: current.bytesTotal,
                bytesDone = bytesDone ?: current.bytesDone,
                currentItem = when {
                    clearCurrentItem -> null
                    currentItem != null -> currentItem
                    else -> current.currentItem
                },
                direction = direction ?: current.direction,
                created = created ?: current.created,
                updated = updated ?: current.updated,
                deleted = deleted ?: current.deleted,
                conflicts = conflicts ?: current.conflicts,
                updatedAt = SyncRunClock.now(),
                heartbeatAt = SyncRunClock.now(),
            )
        }
    }

    suspend fun finish(
        run: SyncRunEntity,
        phase: SyncPhase,
        reason: SyncCancelReason? = null,
        processed: Int = run.processed,
        succeeded: Int = run.succeeded,
        skipped: Int = run.skipped,
        failed: Int = run.failed,
        bytesDone: Long = run.bytesDone,
        created: Int = run.created,
        updated: Int = run.updated,
        deleted: Int = run.deleted,
        conflicts: Int = run.conflicts,
        blockReason: SyncBlockReason? = null,
    ) {
        dao.mutate(run.profileId, run.kind, run.runId) { current ->
            val timestamp = SyncRunClock.now()
            current.copy(
                phase = phase.name,
                blockReason = blockReason?.name,
                cancelReason = reason?.name,
                processed = processed,
                succeeded = succeeded,
                skipped = skipped,
                failed = failed,
                bytesDone = bytesDone,
                created = created,
                updated = updated,
                deleted = deleted,
                conflicts = conflicts,
                currentItem = null,
                stopRequested = false,
                cancelRequested = false,
                updatedAt = timestamp,
                heartbeatAt = timestamp,
            )
        }
    }

    suspend fun requestStop(profileId: String, kind: SyncRunKind, stop: Boolean, cancel: Boolean) {
        dao.requestStop(profileId, kind.value, stop, cancel)
    }

    suspend fun clear(profileId: String) = dao.clearForProfile(profileId)
}

internal object SyncRunClock {
    fun now(): Long = System.currentTimeMillis() / 1000
}
