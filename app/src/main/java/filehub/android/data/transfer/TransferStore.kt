package filehub.android.data.transfer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.transferDataStore by preferencesDataStore(name = "filehub_transfers")

class TransferStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val items: Flow<List<TransferItem>> = context.transferDataStore.data.map { prefs ->
        prefs[TRANSFERS]?.let { raw ->
            runCatching { json.decodeFromString<List<TransferItem>>(raw) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    suspend fun all(): List<TransferItem> =
        context.transferDataStore.data.first()[TRANSFERS]?.let { raw ->
            runCatching { json.decodeFromString<List<TransferItem>>(raw) }.getOrDefault(emptyList())
        }.orEmpty()

    suspend fun find(id: String): TransferItem? = all().firstOrNull { it.id == id }

    suspend fun upsert(item: TransferItem) {
        context.transferDataStore.edit { prefs ->
            val current = prefs[TRANSFERS]?.let { raw ->
                runCatching { json.decodeFromString<List<TransferItem>>(raw) }.getOrDefault(emptyList())
            }.orEmpty()
            val next = current.filterNot { it.id == item.id } + item
            prefs[TRANSFERS] = json.encodeToString(next)
        }
    }

    suspend fun update(id: String, transform: (TransferItem) -> TransferItem) {
        context.transferDataStore.edit { prefs ->
            val current = prefs[TRANSFERS]?.let { raw ->
                runCatching { json.decodeFromString<List<TransferItem>>(raw) }.getOrDefault(emptyList())
            }.orEmpty()
            val next = current.map { if (it.id == id) transform(it) else it }
            prefs[TRANSFERS] = json.encodeToString(next)
        }
    }

    suspend fun remove(id: String) {
        context.transferDataStore.edit { prefs ->
            val current = prefs[TRANSFERS]?.let { raw ->
                runCatching { json.decodeFromString<List<TransferItem>>(raw) }.getOrDefault(emptyList())
            }.orEmpty()
            prefs[TRANSFERS] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    suspend fun clearCompleted() {
        context.transferDataStore.edit { prefs ->
            val current = prefs[TRANSFERS]?.let { raw ->
                runCatching { json.decodeFromString<List<TransferItem>>(raw) }.getOrDefault(emptyList())
            }.orEmpty()
            prefs[TRANSFERS] = json.encodeToString(
                current.filterNot { it.status == TransferStatus.COMPLETED || it.status == TransferStatus.CANCELLED },
            )
        }
    }

    private companion object {
        val TRANSFERS = stringPreferencesKey("transfers")
    }
}
