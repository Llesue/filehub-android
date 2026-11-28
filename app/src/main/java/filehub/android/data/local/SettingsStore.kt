package filehub.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "filehub_settings")

class SettingsStore(private val context: Context) {
    val theme: Flow<String> = context.settingsDataStore.data.map { it[THEME] ?: "system" }
    val appLockEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[APP_LOCK] ?: false }

    suspend fun setTheme(value: String) {
        context.settingsDataStore.edit { it[THEME] = value }
    }

    suspend fun setAppLockEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[APP_LOCK] = value }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
    }
}
