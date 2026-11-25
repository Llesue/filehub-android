package filehub.android.data.events

import kotlinx.serialization.json.JsonObject

/** One event received for the active server. */
data class ServerEvent(
    val profileId: String,
    val id: String?,
    val name: String,
    val data: JsonObject?,
    val rawData: String?,
    val receivedAt: Long,
)

/** Connection target; token stays in memory and is only sent as a header. */
data class StreamTarget(
    val profileId: String,
    val baseUrl: String,
    val token: String,
)

sealed interface EventStreamState {
    data object Idle : EventStreamState
    data object Connecting : EventStreamState
    data class Connected(val profileId: String, val since: Long) : EventStreamState
    data class Reconnecting(
        val profileId: String,
        val attempt: Int,
        val delayMs: Long,
    ) : EventStreamState
    data class Unsupported(val profileId: String) : EventStreamState
    data class Unauthorized(val profileId: String) : EventStreamState
}

object ServerEventNames {
    const val JOB_UPDATED = "job.updated"
    const val USAGE_UPDATED = "usage.updated"
    const val ROOTS_CHANGED = "roots.changed"
    const val UPDATES_CHANGED = "updates.changed"
    const val SETTINGS_CHANGED = "settings.changed"
    const val USER_FEATURES_CHANGED = "user.features_changed"
    const val RESYNC = "resync"
}
