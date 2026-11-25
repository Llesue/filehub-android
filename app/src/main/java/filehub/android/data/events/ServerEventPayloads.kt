package filehub.android.data.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class JobUpdatedPayload(
    val id: String = "",
    val kind: String = "",
    val status: String = "",
    val progress: Int = 0,
    val total: Int = 0,
    val error: String? = null,
)

@Serializable
data class UsageUpdatedPayload(
    @SerialName("used_bytes") val usedBytes: Long? = null,
    @SerialName("quota_bytes") val quotaBytes: Long? = null,
    @SerialName("computed_at") val computedAt: Long? = null,
)

@Serializable
data class RootsChangedPayload(
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("root_id") val rootId: Long? = null,
    val online: Boolean? = null,
)

internal val EventPayloadJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal fun <T> JsonObject?.decodePayload(
    deserializer: kotlinx.serialization.DeserializationStrategy<T>,
): T? = this?.let { data ->
    runCatching { EventPayloadJson.decodeFromJsonElement(deserializer, data) }.getOrNull()
}
