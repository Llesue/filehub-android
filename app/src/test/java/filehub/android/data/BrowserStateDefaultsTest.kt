package filehub.android.data

import filehub.android.core.model.BrowserViewMode
import filehub.android.core.model.FileSort
import filehub.android.core.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserStateDefaultsTest {
    @Test
    fun defaultBrowserStateIsNewestFirstInListMode() {
        assertEquals(FileSort.Modified, BrowserState.Default.sort)
        assertEquals(SortOrder.Desc, BrowserState.Default.order)
        assertEquals(BrowserViewMode.List, BrowserState.Default.view)
    }
}
