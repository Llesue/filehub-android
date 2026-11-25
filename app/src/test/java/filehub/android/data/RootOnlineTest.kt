package filehub.android.data

import filehub.android.core.model.FileRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RootOnlineTest {

    private fun root(id: Long, online: Boolean?): FileRoot = FileRoot(
        id = id,
        alias = "root-$id",
        readOnly = false,
        kind = if (id == 2L) "remote" else null,
        mountId = null,
        online = online,
    )

    @Test
    fun updatesOnlyTheMatchingRemoteRoot() {
        val roots = listOf(root(1, null), root(2, true))

        val updated = roots.withRootOnline(2, false)

        assertEquals(null, updated[0].online)
        assertEquals(false, updated[1].online)
    }

    @Test
    fun unknownRootLeavesTheListUntouched() {
        val roots = listOf(root(1, null))

        assertSame(roots, roots.withRootOnline(99, false))
    }
}
