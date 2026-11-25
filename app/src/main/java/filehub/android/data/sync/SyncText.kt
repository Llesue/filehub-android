package filehub.android.data.sync

import android.content.Context
import filehub.android.R

/** Context-based labels for worker logs (UI composables use stringResource). */
internal object SyncText {
    fun trigger(context: Context, trigger: String?): String = when (trigger) {
        "manual" -> context.getString(R.string.sync_trigger_manual)
        "catchup" -> context.getString(R.string.sync_trigger_catchup)
        "wifi" -> context.getString(R.string.sync_trigger_wifi)
        else -> context.getString(R.string.sync_trigger_periodic)
    }

    fun cancelReason(context: Context, reason: SyncCancelReason): String = when (reason) {
        SyncCancelReason.USER_STOP -> context.getString(R.string.sync_cancel_user_stop)
        SyncCancelReason.USER_CANCEL -> context.getString(R.string.sync_cancel_user_cancel)
        SyncCancelReason.TARGET_CHANGED -> context.getString(R.string.sync_cancel_target_changed)
        SyncCancelReason.CONFIG_CHANGED -> context.getString(R.string.sync_cancel_config_changed)
        SyncCancelReason.LOGOUT -> context.getString(R.string.sync_cancel_logout)
        SyncCancelReason.SERVER_REMOVED -> context.getString(R.string.sync_cancel_server_removed)
        SyncCancelReason.AUTH_FAILED -> context.getString(R.string.sync_cancel_auth_failed)
        SyncCancelReason.SYSTEM_STOPPED -> context.getString(R.string.sync_cancel_system_stopped)
        SyncCancelReason.WORK_REPLACED -> context.getString(R.string.sync_cancel_work_replaced)
        SyncCancelReason.PROCESS_DEATH -> context.getString(R.string.sync_cancel_process_death)
        SyncCancelReason.UNKNOWN -> context.getString(R.string.sync_cancel_unknown)
    }
}
