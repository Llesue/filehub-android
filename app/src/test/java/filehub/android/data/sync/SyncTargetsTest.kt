package filehub.android.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTargetsTest {

    @Test
    fun `same path on the same root overlaps`() {
        assertTrue(SyncTargets.overlaps(1, "phone/mi", 1, "phone/mi"))
    }

    @Test
    fun `paths are normalized before comparison`() {
        assertTrue(SyncTargets.overlaps(1, "/phone/mi/", 1, "phone//mi"))
    }

    @Test
    fun `parent and child overlap in both directions`() {
        assertTrue(SyncTargets.overlaps(1, "phone", 1, "phone/mi"))
        assertTrue(SyncTargets.overlaps(1, "phone/mi/Camera", 1, "phone/mi"))
    }

    @Test
    fun `different roots never overlap`() {
        assertFalse(SyncTargets.overlaps(1, "phone/mi", 2, "phone/mi"))
    }

    @Test
    fun `siblings do not overlap`() {
        assertFalse(SyncTargets.overlaps(1, "phone/mi", 1, "phone/documents"))
        assertFalse(SyncTargets.overlaps(1, "phone/mi", 1, "phone/mi2"))
    }

    @Test
    fun `root of a root overlaps everything below it`() {
        assertTrue(SyncTargets.overlaps(1, "", 1, "phone/mi"))
        assertTrue(SyncTargets.overlaps(1, "phone/mi", 1, ""))
    }
}
