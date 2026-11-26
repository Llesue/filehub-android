package filehub.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
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

class ProfileStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.fileHubDataStore)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun read(): StoredProfiles {
        val prefs = dataStore.data.first()
        val decoded = prefs[PROFILES]?.let { raw ->
            runCatching { json.decodeFromString<List<ServerProfile>>(raw) }
                .onFailure { error ->
                    System.err.println("ProfileStore: failed to decode profiles: ${error.message}")
                }
                .getOrDefault(emptyList())
        }.orEmpty()
        return StoredProfiles(decoded, prefs[ACTIVE_PROFILE])
    }

    suspend fun saveProfiles(profiles: List<ServerProfile>) {
        // Explicit removal uses clearProfiles(). Refusing an empty list here
        // keeps a background session that was started without load() from
        // overwriting the persisted server list.
        if (profiles.isEmpty()) {
            System.err.println("ProfileStore: refusing to persist an empty profile list")
            return
        }
        val raw = json.encodeToString(profiles)
        dataStore.edit { it[PROFILES] = raw }
    }

    suspend fun clearProfiles() {
        dataStore.edit { it.remove(PROFILES) }
    }

    suspend fun setActive(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(ACTIVE_PROFILE) else prefs[ACTIVE_PROFILE] = id
        }
    }

    suspend fun saveToken(profileId: String, encryptedToken: String) {
        dataStore.edit { it[tokenKey(profileId)] = encryptedToken }
    }

    suspend fun readToken(profileId: String): String? =
        dataStore.data.first()[tokenKey(profileId)]

    suspend fun removeToken(profileId: String) {
        dataStore.edit { it.remove(tokenKey(profileId)) }
    }

    private fun tokenKey(profileId: String): Preferences.Key<String> =
        stringPreferencesKey("token_$profileId")

    private companion object {
        val PROFILES = stringPreferencesKey("profiles")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
    }
}
