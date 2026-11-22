package filehub.android.data

import filehub.android.core.model.DirectoryListing
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileSort
import filehub.android.core.model.SortOrder
import filehub.android.data.sync.DirectoryCacheDao
import filehub.android.data.sync.DirectoryCacheEntity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CachedListing(
    val listing: DirectoryListing,
    val fetchedAt: Long,
)

class DirectoryCacheRepository(private val dao: DirectoryCacheDao) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun get(
        profileId: String,
        rootId: Long,
        path: String,
        sort: FileSort,
        order: SortOrder,
    ): CachedListing? = withContext(Dispatchers.IO) {
        val row = dao.get(profileId, rootId, path, sort.apiValue, order.apiValue) ?: return@withContext null
        val entries = runCatching {
            json.decodeFromString<List<FileEntry>>(row.entriesJson)
        }.getOrNull() ?: return@withContext null
        CachedListing(
            DirectoryListing(
                entries = entries,
                nextCursor = null,
                readOnly = row.readOnly,
                readOnlyReason = row.readOnlyReason,
                stale = true,
            ),
            fetchedAt = row.fetchedAt,
        )
    }

    suspend fun put(
        profileId: String,
        rootId: Long,
        path: String,
        sort: FileSort,
        order: SortOrder,
        listing: DirectoryListing,
    ) = withContext(Dispatchers.IO) {
        val encoded = runCatching { json.encodeToString(listing.entries) }.getOrNull() ?: return@withContext
        dao.put(
            DirectoryCacheEntity(
                profileId = profileId,
                rootId = rootId,
                path = path,
                sort = sort.apiValue,
                order = order.apiValue,
                entriesJson = encoded,
                readOnly = listing.readOnly,
                readOnlyReason = listing.readOnlyReason,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        dao.prune(200)
    }
}
