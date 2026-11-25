package filehub.android.data.sync

import kotlinx.coroutines.flow.Flow

/** Bounded, user-visible log of sync decisions. Newest entries are kept. */
class SyncLogStore(private val dao: SyncLogDao) {

    fun flow(profileId: String, kind: SyncRunKind): Flow<List<SyncLogEntity>> =
        dao.flow(profileId, kind.value, MAX_ENTRIES)

    suspend fun log(
        profileId: String,
        kind: SyncRunKind,
        message: String,
        level: String = LEVEL_INFO,
    ) {
        if (message.isBlank()) return
        dao.insert(
            SyncLogEntity(
                profileId = profileId,
                kind = kind.value,
                timestamp = System.currentTimeMillis() / 1000,
                level = level,
                message = message,
            ),
        )
        dao.prune(profileId, kind.value, MAX_ENTRIES)
    }

    suspend fun clear(profileId: String, kind: SyncRunKind) =
        dao.clear(profileId, kind.value)

    suspend fun clearProfile(profileId: String) = dao.clearProfile(profileId)

    companion object {
        const val MAX_ENTRIES = 200
        const val LEVEL_INFO = "info"
        const val LEVEL_START = "start"
        const val LEVEL_SUCCESS = "success"
        const val LEVEL_WARN = "warn"
        const val LEVEL_ERROR = "error"
    }
}
