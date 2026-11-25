package filehub.android.data.sync

/** Thrown when a new sync target overlaps the other sync kind on the same root. */
class SyncTargetOverlapException : Exception("sync target overlaps another sync")

internal object SyncTargets {
    fun normalize(path: String): String =
        path.trim().trim('/').replace(Regex("/{2,}"), "/")

    fun overlaps(rootIdA: Long, pathA: String, rootIdB: Long, pathB: String): Boolean {
        if (rootIdA != rootIdB) return false
        val a = normalize(pathA)
        val b = normalize(pathB)
        if (a.isEmpty() || b.isEmpty()) return true
        return a == b || a.startsWith("$b/") || b.startsWith("$a/")
    }

    /**
     * Target used by the photo sync; album subfolders make it the parent of the
     * directories it actually writes, which is what folder sync may collide with.
     */
    fun photoPath(config: PhotoSyncConfigEntity): String = normalize(config.remotePath)

    fun folderPath(config: FolderSyncConfigEntity): String = normalize(config.remotePath)
}

/** Checks the other sync kind before a target is persisted. */
class SyncTargetGuard(
    private val photoDao: PhotoSyncDao,
    private val folderDao: FolderSyncDao,
) {
    suspend fun photoConflicts(profileId: String, rootId: Long, remotePath: String): Boolean {
        val folder = folderDao.config(profileId) ?: return false
        if (!folder.enabled || folder.localTreeUri.isBlank()) return false
        return SyncTargets.overlaps(rootId, remotePath, folder.rootId, folder.remotePath)
    }

    suspend fun folderConflicts(profileId: String, rootId: Long, remotePath: String): Boolean {
        val photo = photoDao.config(profileId) ?: return false
        if (!photo.enabled) return false
        return SyncTargets.overlaps(rootId, remotePath, photo.rootId, photo.remotePath)
    }
}
