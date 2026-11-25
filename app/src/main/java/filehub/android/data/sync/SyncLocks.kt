package filehub.android.data.sync

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

/**
 * Serializes sync runs per profile. Photo and folder sync have their own mutexes
 * (so the same kind never overlaps itself) plus a shared mutex (so the two kinds
 * never write at the same time, even when their targets happen to overlap).
 */
internal object SyncLocks {
    private val photo = ConcurrentHashMap<String, Mutex>()
    private val folder = ConcurrentHashMap<String, Mutex>()
    private val shared = ConcurrentHashMap<String, Mutex>()

    fun photo(profileId: String): Mutex = photo.getOrPut(profileId) { Mutex() }

    fun folder(profileId: String): Mutex = folder.getOrPut(profileId) { Mutex() }

    fun shared(profileId: String): Mutex = shared.getOrPut(profileId) { Mutex() }
}
