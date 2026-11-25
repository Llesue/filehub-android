package filehub.android.ui

import filehub.android.core.model.FileRoot
import org.junit.Assert.assertEquals
import org.junit.Test

class ColumnBrowserTest {
    private val root = FileRoot(
        id = 7,
        alias = "My Files",
        readOnly = false,
        kind = "local",
        mountId = 7,
        online = true,
    )

    @Test
    fun pathsBuildFromTheParentDirectory() {
        assertEquals("docs", columnFolderPath("", "docs"))
        assertEquals("docs/sub", columnFolderPath("docs", "sub"))
        assertEquals("My Files", columnFolderTitle(root, ""))
        assertEquals("sub", columnFolderTitle(root, "docs/sub"))
    }

    @Test
    fun pathChainIncludesEveryAncestorForSearchJumps() {
        assertEquals(listOf(""), columnPathChain(""))
        assertEquals(listOf("", "docs"), columnPathChain("docs"))
        assertEquals(listOf("", "docs", "docs/sub"), columnPathChain("docs/sub"))
    }

    @Test
    fun stackPushesReplacesAndTruncates() {
        val nav = ColumnNavViewModel()
        assertEquals(0, nav.columns.value.size)

        val roots = ColumnSnapshot(level = ColumnLevel.Roots("server-1"), serverId = "server-1")
        nav.push(roots)
        assertEquals(1, nav.columns.value.size)
        assertEquals(ColumnLevel.Roots("server-1"), nav.columns.value.last().level)

        val entries = ColumnSnapshot(level = ColumnLevel.Entries(root, ""), serverId = "server-1")
        nav.replaceLast(entries)
        assertEquals(1, nav.columns.value.size)
        assertEquals(ColumnLevel.Entries(root, ""), nav.columns.value.last().level)

        nav.push(ColumnSnapshot(level = ColumnLevel.Entries(root, "docs"), serverId = "server-1"))
        assertEquals(2, nav.columns.value.size)
        nav.truncate(1)
        assertEquals(1, nav.columns.value.size)
        assertEquals(ColumnLevel.Entries(root, ""), nav.columns.value.last().level)
    }
}
