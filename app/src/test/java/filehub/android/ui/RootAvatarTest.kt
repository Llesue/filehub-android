package filehub.android.ui

import filehub.android.core.model.FileRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootAvatarTest {
    @Test
    fun remoteAvatarUrlUsesMountAndVersion() {
        val root = root(
            kind = "remote",
            mountId = 42,
            remoteAvatarVersion = 7,
        )

        assertEquals(
            "https://example.com/api/v1/remote/mounts/42/avatar?v=7",
            remoteAvatarUrl("https://example.com/", root),
        )
    }

    @Test
    fun localRootHasNoAvatarUrl() {
        assertNull(remoteAvatarUrl("https://example.com", root(kind = null, mountId = 42)))
    }

    @Test
    fun missingBaseUrlOrMountOrVersionReturnsNull() {
        val remote = root(kind = "remote", mountId = 42, remoteAvatarVersion = 7)

        assertNull(remoteAvatarUrl(null, remote))
        assertNull(remoteAvatarUrl("", remote))
        assertNull(remoteAvatarUrl("https://example.com", remote.copy(mountId = null)))
        assertNull(remoteAvatarUrl("https://example.com", remote.copy(remoteAvatarVersion = 0)))
    }

    private fun root(
        kind: String?,
        mountId: Long? = null,
        remoteAvatarVersion: Long = 0,
    ) = FileRoot(
        id = 1,
        alias = "Remote",
        readOnly = false,
        kind = kind,
        mountId = mountId,
        remoteOwnerName = "Owner",
        remoteAvatarVersion = remoteAvatarVersion,
    )
}
