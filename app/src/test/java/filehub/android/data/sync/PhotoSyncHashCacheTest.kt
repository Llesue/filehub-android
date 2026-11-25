package filehub.android.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoSyncHashCacheTest {
    private fun entry(
        sha256: String? = "abc",
        hashVersion: Int = PHOTO_HASH_VERSION,
        hashSize: Long = 10,
        hashMtime: Long = 20,
    ) = PhotoSyncEntryEntity(
        profileId = "p",
        signature = "s",
        localId = 1,
        bucketId = 2,
        album = "Camera",
        name = "a.jpg",
        size = 10,
        mtime = 20,
        mimeType = "image/jpeg",
        remotePath = "Camera/a.jpg",
        sha256 = sha256,
        hashVersion = hashVersion,
        hashSize = hashSize,
        hashMtime = hashMtime,
    )

    @Test
    fun onlyOriginalStreamHashesAreReused() {
        assertEquals("abc", entry().cachedHash(10, 20, PHOTO_HASH_VERSION))
        assertEquals("abc", entry(hashVersion = 0).cachedHash(10, 20, 0))
        assertNull(entry(hashVersion = 0).cachedHash(10, 20, PHOTO_HASH_VERSION))
        assertNull(entry(sha256 = null).cachedHash(10, 20, PHOTO_HASH_VERSION))
        assertNull(entry().cachedHash(11, 20, PHOTO_HASH_VERSION))
        assertNull(entry().cachedHash(10, 21, PHOTO_HASH_VERSION))
    }
}
