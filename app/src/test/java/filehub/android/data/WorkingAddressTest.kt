package filehub.android.data

import filehub.android.core.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkingAddressTest {
    @Test
    fun emptyPersistedProfilesAreNotReplaced() {
        val update = applyWorkingAddress(
            profiles = emptyList(),
            profileId = "one",
            updated = profile("one", "https://fallback.example.com"),
        )

        assertFalse(update.changed)
        assertTrue(update.profiles.isEmpty())
    }

    @Test
    fun matchingProfileGetsTheWorkingAddress() {
        val update = applyWorkingAddress(
            profiles = listOf(profile("one", "https://old.example.com")),
            profileId = "one",
            updated = profile("one", "https://fallback.example.com"),
        )

        assertTrue(update.changed)
        assertEquals("https://fallback.example.com", update.profiles.single().baseUrl)
    }

    @Test
    fun unknownProfileIsNotAdded() {
        val update = applyWorkingAddress(
            profiles = listOf(profile("one", "https://old.example.com")),
            profileId = "two",
            updated = profile("two", "https://fallback.example.com"),
        )

        assertFalse(update.changed)
        assertEquals(listOf("one"), update.profiles.map { it.id })
    }

    private fun profile(id: String, baseUrl: String) = ServerProfile(
        id = id,
        baseUrl = baseUrl,
        displayName = "Example",
        username = "user",
        userId = 1,
        tokenId = 1,
        tokenExpiresAt = null,
        createdAt = 1,
    )
}
