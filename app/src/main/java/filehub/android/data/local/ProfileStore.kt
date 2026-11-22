package filehub.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import filehub.android.core.model.ServerProfile
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.fileHubDataStore by preferencesDataStore(name = "filehub")

data class StoredProfiles(
    val profiles: List<ServerProfile> = emptyList(),
    val activeId: String? = null,
)

class ProfileStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun read(): StoredProfiles {
        val prefs = context.fileHubDataStore.data.first()
        val decoded = prefs[PROFILES]?.let { raw ->
            runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
        }.orEmpty()
        return StoredProfiles(decoded, prefs[ACTIVE_PROFILE])
    }

    suspend fun saveProfiles(profiles: List<ServerProfile>) {
        val raw = json.encodeToString(profiles)
        context.fileHubDataStore.edit { it[PROFILES] = raw }
    }

    suspend fun setActive(id: String?) {
        context.fileHubDataStore.edit { prefs ->
            if (id == null) prefs.remove(ACTIVE_PROFILE) else prefs[ACTIVE_PROFILE] = id
        }
    }

    suspend fun saveToken(profileId: String, encryptedToken: String) {
        context.fileHubDataStore.edit { it[tokenKey(profileId)] = encryptedToken }
    }

    suspend fun readToken(profileId: String): String? =
        context.fileHubDataStore.data.first()[tokenKey(profileId)]

    suspend fun removeToken(profileId: String) {
        context.fileHubDataStore.edit { it.remove(tokenKey(profileId)) }
    }

    private fun tokenKey(profileId: String): Preferences.Key<String> =
        stringPreferencesKey("token_$profileId")

    private companion object {
        val PROFILES = stringPreferencesKey("profiles")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
    }
}
