package filehub.android.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import filehub.android.core.model.ServerProfile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: ProfileStore

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = ProfileStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tempFolder.root, "filehub.preferences_pb") },
            ),
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun saveProfilesRoundTrips() = runTest {
        store.saveProfiles(listOf(profile("one")))

        val stored = store.read()

        assertEquals(listOf("one"), stored.profiles.map { it.id })
    }

    @Test
    fun emptySaveDoesNotOverwritePersistedProfiles() = runTest {
        store.saveProfiles(listOf(profile("one")))

        store.saveProfiles(emptyList())

        val stored = store.read()
        assertEquals(listOf("one"), stored.profiles.map { it.id })
    }

    @Test
    fun clearProfilesRemovesPersistedProfiles() = runTest {
        store.saveProfiles(listOf(profile("one")))

        store.clearProfiles()

        assertTrue(store.read().profiles.isEmpty())
    }

    private fun profile(id: String) = ServerProfile(
        id = id,
        baseUrl = "https://example.com",
        displayName = "Example",
        username = "user",
        userId = 1,
        tokenId = 1,
        tokenExpiresAt = null,
        createdAt = 1,
    )
}
