package filehub.android.ui

import filehub.android.core.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AvatarImageTest {
    private fun profile(
        id: String,
        userId: Long,
        username: String,
        avatarVersion: Long?,
    ) = ServerProfile(
        id = id,
        baseUrl = "https://example.com",
        displayName = "Demo",
        username = username,
        userId = userId,
        tokenId = 1,
        tokenExpiresAt = null,
        createdAt = 0,
        avatarVersion = avatarVersion,
    )

    @Test
    fun differentUsersOnTheSameServerGetDifferentCacheKeys() {
        val first = profile("profile-a", 1, "alice", 1)
        val second = profile("profile-b", 2, "bob", 1)
        assertNotEquals(avatarCacheKey(first), avatarCacheKey(second))
    }

    @Test
    fun theSameUserKeepsTheCacheKeyAcrossTokens() {
        val before = profile("profile-a", 1, "alice", 1)
        val after = profile("profile-a", 1, "alice", 1)
        assertEquals(avatarCacheKey(before), avatarCacheKey(after))
    }

    @Test
    fun avatarVersionChangesTheCacheKey() {
        val before = profile("profile-a", 1, "alice", 1)
        val after = profile("profile-a", 1, "alice", 2)
        assertNotEquals(avatarCacheKey(before), avatarCacheKey(after))
    }
}
