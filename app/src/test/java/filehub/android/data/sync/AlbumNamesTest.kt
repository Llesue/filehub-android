package filehub.android.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumNamesTest {

    @Test
    fun `camera and camera in one bucket resolve to the dominant name`() {
        val names = mapOf("Camera" to 1324, "camera" to 76)
        assertEquals("Camera", AlbumNames.canonical(names))
    }

    @Test
    fun `capitalized variant wins a tie regardless of insertion order`() {
        assertEquals("Camera", AlbumNames.canonical(linkedMapOf("camera" to 5, "Camera" to 5)))
        assertEquals("Camera", AlbumNames.canonical(linkedMapOf("Camera" to 5, "camera" to 5)))
    }

    @Test
    fun `lowercase is kept when it is the only casing`() {
        assertEquals("camera", AlbumNames.canonical(mapOf("camera" to 71)))
    }

    @Test
    fun `non latin names fall back to the biggest count`() {
        assertEquals("微信", AlbumNames.canonical(mapOf("微信" to 10, "WeiXin" to 3)))
        assertEquals("WeiXin", AlbumNames.canonical(mapOf("微信" to 3, "WeiXin" to 10)))
    }

    @Test
    fun `blank names are ignored`() {
        assertEquals("Camera", AlbumNames.canonical(mapOf("" to 99, "Camera" to 1)))
        assertNull(AlbumNames.canonical(mapOf(" " to 2)))
        assertNull(AlbumNames.canonical(emptyMap()))
    }

    @Test
    fun `photos from one bucket all use the canonical album`() {
        val rows = listOf(
            -1L to "Camera",
            -1L to "Camera",
            -1L to "camera",
            -2L to "screenshots",
        )

        val canonical = AlbumNames.canonicalByBucket(rows)

        assertEquals("Camera", canonical[-1L])
        assertEquals("screenshots", canonical[-2L])
        assertEquals(2, canonical.size)
    }
}
