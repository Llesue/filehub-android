package filehub.android.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStreamsTest {
    @Test
    fun mediaStoreUrisNeedOriginalOnlyWithPermissionOnAndroid10Plus() {
        assertTrue(MediaStreams.requiresOriginal("media", 29, true))
        assertTrue(MediaStreams.requiresOriginal("media", 36, true))
        assertFalse(MediaStreams.requiresOriginal("media", 36, false))
        assertFalse(MediaStreams.requiresOriginal("media", 28, true))
    }

    @Test
    fun otherProvidersKeepTheirOriginalUris() {
        assertFalse(
            MediaStreams.requiresOriginal(
                "com.android.externalstorage.documents",
                36,
                true,
            ),
        )
        assertFalse(MediaStreams.requiresOriginal(null, 36, true))
    }
}
