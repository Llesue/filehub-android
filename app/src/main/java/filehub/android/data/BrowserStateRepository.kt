package filehub.android.data

import filehub.android.core.model.BrowserViewMode
import filehub.android.core.model.FileSort
import filehub.android.core.model.SortOrder
import filehub.android.data.sync.BrowserStateDao
import filehub.android.data.sync.BrowserStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BrowserState(
    val sort: FileSort,
    val order: SortOrder,
    val view: BrowserViewMode,
    val listIndex: Int,
    val listOffset: Int,
    val gridIndex: Int,
    val gridOffset: Int,
) {
    companion object {
        val Default = BrowserState(
            sort = FileSort.Modified,
            order = SortOrder.Desc,
            view = BrowserViewMode.List,
            listIndex = 0,
            listOffset = 0,
            gridIndex = 0,
            gridOffset = 0,
        )
    }
}

class BrowserStateRepository(private val dao: BrowserStateDao) {
    suspend fun get(profileId: String, rootId: Long, path: String): BrowserState? =
        withContext(Dispatchers.IO) {
            val row = dao.get(profileId, rootId, path) ?: return@withContext null
            val sort = runCatching { FileSort.entries.first { it.apiValue == row.sort } }
                .getOrDefault(FileSort.Modified)
            val order = runCatching { SortOrder.entries.first { it.apiValue == row.order } }
                .getOrDefault(SortOrder.Desc)
            val view = runCatching { BrowserViewMode.entries.first { it.storageValue == row.view } }
                .getOrDefault(BrowserViewMode.List)
            BrowserState(
                sort = sort,
                order = order,
                view = view,
                listIndex = row.listIndex.coerceAtLeast(0),
                listOffset = row.listOffset.coerceAtLeast(0),
                gridIndex = row.gridIndex.coerceAtLeast(0),
                gridOffset = row.gridOffset.coerceAtLeast(0),
            )
        }

    suspend fun put(
        profileId: String,
        rootId: Long,
        path: String,
        state: BrowserState,
    ) = withContext(Dispatchers.IO) {
        dao.put(
            BrowserStateEntity(
                profileId = profileId,
                rootId = rootId,
                path = path,
                sort = state.sort.apiValue,
                order = state.order.apiValue,
                view = state.view.storageValue,
                listIndex = state.listIndex,
                listOffset = state.listOffset,
                gridIndex = state.gridIndex,
                gridOffset = state.gridOffset,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        dao.prune(500)
    }

    suspend fun clearForProfile(profileId: String) = withContext(Dispatchers.IO) {
        dao.clearForProfile(profileId)
    }
}
