package filehub.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferPathTest {
    @Test
    fun rootPathsStayUnchanged() {
        assertEquals(
            listOf("a.txt", "b.txt"),
            resolveTransferPaths("", listOf("a.txt", "b.txt")),
        )
    }

    @Test
    fun nestedPathsGetTheCurrentDirectoryPrefix() {
        assertEquals(
            listOf("docs/a.txt", "docs/sub/b.txt"),
            resolveTransferPaths("docs", listOf("a.txt", "sub/b.txt")),
        )
    }
}
