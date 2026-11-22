package filehub.android.data.sync

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

/** Serializes sync runs per profile so manual and periodic work cannot overlap. */
internal object SyncLocks {
    private val photo = ConcurrentHashMap<String, Mutex>()
    private val folder = ConcurrentHashMap<String, Mutex>()

    fun photo(profileId: String): Mutex = photo.getOrPut(profileId) { Mutex() }

    fun folder(profileId: String): Mutex = folder.getOrPut(profileId) { Mutex() }
}
