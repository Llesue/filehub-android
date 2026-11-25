package filehub.android.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutMetricsTest {
    @Test
    fun tabletAndCarKeepTheSameShellInBothOrientations() {
        assertEquals(ShellMode.Wide, shellModeFor(1280, 720, 720))
        assertEquals(ShellMode.Wide, shellModeFor(720, 1280, 720))
        assertEquals(ShellMode.Wide, shellModeFor(1920, 1080, 1080))
        assertEquals(ShellMode.Wide, shellModeFor(1080, 1920, 1080))
        assertEquals(ShellMode.Wide, shellModeFor(800, 1280, 800))
    }

    @Test
    fun phonesKeepCompactAndLandscapeShells() {
        assertEquals(ShellMode.Compact, shellModeFor(390, 853, 390))
        assertEquals(ShellMode.Landscape, shellModeFor(853, 390, 390))
        assertEquals(ShellMode.Landscape, shellModeFor(853, 384, 384))
    }

    @Test
    fun sidebarAndListColumnsAdaptToAvailableWidth() {
        assertEquals(280.dp, tabletSidebarWidth(1280f))
        assertEquals(240.dp, tabletSidebarWidth(900f))
        assertEquals(220.dp, tabletSidebarWidth(720f))

        assertEquals(4, tabletListColumns(1200f))
        assertEquals(3, tabletListColumns(1000f))
        assertEquals(2, tabletListColumns(520f))
        assertEquals(1, tabletListColumns(400f))
    }

    @Test
    fun lightboxIsOnlyForTabletBelowAndroid12() {
        assertTrue(usesLightboxBackdrop(tabletMode = true, sdkInt = 30))
        assertFalse(usesLightboxBackdrop(tabletMode = true, sdkInt = 31))
        assertFalse(usesLightboxBackdrop(tabletMode = false, sdkInt = 30))
        assertFalse(usesLightboxBackdrop(tabletMode = false, sdkInt = 33))
    }

    @Test
    fun columnsClampToTheAgreedRange() {
        assertEquals(2, fileGridColumns(390f))
        assertEquals(4, fileGridColumns(820f))
        assertEquals(6, fileGridColumns(1920f))
        assertEquals(1, rootGridColumns(390f))
        assertEquals(2, rootGridColumns(700f))
        assertEquals(4, rootGridColumns(1920f))
    }
}
