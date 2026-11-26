package filehub.android.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAlbumPathTest {
    @Test
    fun fullRelativePathIsPreserved() {
        assertEquals(
            "Pictures/WeiXin",
            photoAlbumRelativePath("Pictures/WeiXin", "WeiXin"),
        )
    }

    @Test
    fun blankRelativePathFallsBackToAlbum() {
        assertEquals("Camera", photoAlbumRelativePath("", "Camera"))
    }

    @Test
    fun traversalSegmentsAreDropped() {
        assertEquals("evil", photoAlbumRelativePath("../evil", "Camera"))
    }

    @Test
    fun invalidSegmentCharactersAreReplaced() {
        assertEquals(
            "Pictures/we_chat",
            photoAlbumRelativePath("Pictures/we\\chat", "Camera"),
        )
    }
}
