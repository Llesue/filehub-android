package filehub.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EncodingsTest {
    @Test
    fun labelFallsBackToTheRawValue() {
        assertEquals("UTF-8", encodingDisplayName("utf-8"))
        assertEquals("GBK", encodingDisplayName("gbk"))
        assertEquals("Auto", encodingDisplayName(null))
        assertEquals("x-custom", encodingDisplayName("x-custom"))
    }

    @Test
    fun everyServerEncodingIsOfferedExactlyOnce() {
        val values = EncodingOptions.map { it.first }
        assertEquals(values.size, values.distinct().size)
        assertEquals(null, values.first())
    }
}
