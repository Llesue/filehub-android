package filehub.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "filehub_settings")

class SettingsStore(private val context: Context) {
    val theme: Flow<String> = context.settingsDataStore.data.map { it[THEME] ?: "system" }

    suspend fun setTheme(value: String) {
        context.settingsDataStore.edit { it[THEME] = value }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
    }
}
