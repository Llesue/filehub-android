package filehub.android.ui

import filehub.android.core.model.FileRoot
import org.junit.Assert.assertEquals
import org.junit.Test

class RootFiltersTest {
    private fun root(id: Long, online: Boolean?) = FileRoot(
        id = id,
        alias = "root-$id",
        readOnly = false,
        kind = "remote",
        mountId = id,
        online = online,
    )

    @Test
    fun offlineRootsAreSeparatedFromOnlineAndUnknownOnes() {
        val groups = rootGroups(listOf(root(1, true), root(2, false), root(3, null)))
        assertEquals(listOf(1L, 3L), groups.online.map { it.id })
        assertEquals(listOf(2L), groups.offline.map { it.id })
    }

    @Test
    fun directoryPickerOnlyGetsBrowsableRoots() {
        val roots = listOf(root(1, true), root(2, false), root(3, null))
        assertEquals(listOf(1L, 3L), selectableRoots(roots).map { it.id })
    }
}
