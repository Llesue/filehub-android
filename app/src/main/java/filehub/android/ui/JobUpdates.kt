package filehub.android.ui

internal val TERMINAL_JOB_STATUSES = setOf("completed", "failed", "cancelled")

internal fun isTerminalJobStatus(status: String): Boolean = status in TERMINAL_JOB_STATUSES

internal fun mergeJobUpdate(
    existing: ActiveJob,
    status: String,
    progress: Int,
    total: Int,
    bytesDone: Long? = null,
    bytesTotal: Long? = null,
): ActiveJob = existing.copy(
    status = status.ifBlank { existing.status },
    progress = progress.coerceAtLeast(0),
    total = total.coerceAtLeast(0),
    bytesDone = bytesDone ?: existing.bytesDone,
    bytesTotal = bytesTotal ?: existing.bytesTotal,
)
