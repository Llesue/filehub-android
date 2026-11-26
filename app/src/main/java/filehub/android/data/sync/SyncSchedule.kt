package filehub.android.data.sync

import androidx.work.WorkInfo

internal fun scheduleIntervalMinutes(schedule: String): Long? = when (schedule) {
    "15m" -> 15L
    "hourly" -> 60L
    "daily" -> 24L * 60
    "weekly" -> 7L * 24 * 60
    else -> null
}

internal fun isScheduleOverdue(lastRunAt: Long?, schedule: String, now: Long): Boolean {
    if (lastRunAt == null) return false
    val minutes = scheduleIntervalMinutes(schedule) ?: return false
    return now - lastRunAt > minutes * 60
}

data class ScheduleStatus(
    val loading: Boolean = false,
    val scheduled: Boolean = false,
    val nextRunAt: Long? = null,
    val state: String? = null,
    val cancelled: Boolean = false,
    val failed: Boolean = false,
)

internal fun List<WorkInfo>.toScheduleStatus(): ScheduleStatus {
    val active = firstOrNull {
        it.state == WorkInfo.State.ENQUEUED ||
            it.state == WorkInfo.State.RUNNING ||
            it.state == WorkInfo.State.BLOCKED
    }
    val info = active ?: maxByOrNull { it.generation }
    if (info == null) return ScheduleStatus()
    val next = info.nextScheduleTimeMillis.takeIf {
        it > 0 && it != Long.MAX_VALUE
    }
    return ScheduleStatus(
        loading = false,
        scheduled = info.state == WorkInfo.State.ENQUEUED ||
            info.state == WorkInfo.State.RUNNING ||
            info.state == WorkInfo.State.BLOCKED,
        nextRunAt = next,
        state = info.state.name,
        cancelled = info.state == WorkInfo.State.CANCELLED,
        failed = info.state == WorkInfo.State.FAILED,
    )
}
