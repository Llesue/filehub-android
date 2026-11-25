package filehub.android.data.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import filehub.android.MainActivity
import filehub.android.R

data class SyncNotificationState(
    val phase: SyncPhase,
    val total: Int = 0,
    val processed: Int = 0,
    val failed: Int = 0,
    val bytesTotal: Long = 0,
    val bytesDone: Long = 0,
    val currentItem: String? = null,
    val speedBytesPerSecond: Long = 0,
    val serverName: String = "",
    val modeLabel: String? = null,
    val conflicts: Int = 0,
    val blockReason: SyncBlockReason? = null,
    val summary: String? = null,
)

object SyncNotificationActions {
    const val ACTION_STOP = "filehub.sync.action.STOP"
    const val ACTION_CANCEL = "filehub.sync.action.CANCEL"
    const val ACTION_CONTINUE = "filehub.sync.action.CONTINUE"
    const val ACTION_RETRY = "filehub.sync.action.RETRY"
    const val EXTRA_PROFILE = "profileId"
    const val EXTRA_KIND = "kind"

    fun matches(action: String?): Boolean =
        action == ACTION_STOP || action == ACTION_CANCEL ||
            action == ACTION_CONTINUE || action == ACTION_RETRY
}

internal object SyncNotificationBuilder {

    fun build(
        context: Context,
        channelId: String,
        kind: SyncRunKind,
        dest: String,
        profileId: String,
        state: SyncNotificationState,
        ongoing: Boolean,
    ): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconFor(state.phase))
            .setContentTitle(title(context, kind, state))
            .setContentText(text(context, state))
            .setStyle(NotificationCompat.BigTextStyle().bigText(longText(context, state)))
            .setContentIntent(openIntent(context, kind, dest, profileId))
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        if (!ongoing &&
            (state.phase == SyncPhase.COMPLETED ||
                state.phase == SyncPhase.STOPPED ||
                state.phase == SyncPhase.CANCELLED)
        ) {
            // Success/stop summaries should not sit in the shade forever.
            builder.setTimeoutAfter(8_000)
        }

        progress(state, builder)

        if (ongoing) {
            when (state.phase) {
                SyncPhase.SYNCING, SyncPhase.SCANNING, SyncPhase.PREPARING, SyncPhase.STOPPING,
                SyncPhase.CANCELLING,
                -> builder.addAction(
                    0,
                    context.getString(R.string.sync_notif_stop),
                    action(context, SyncNotificationActions.ACTION_STOP, kind, profileId),
                )
                SyncPhase.QUEUED, SyncPhase.WAITING -> builder.addAction(
                    0,
                    context.getString(R.string.sync_notif_cancel),
                    action(context, SyncNotificationActions.ACTION_CANCEL, kind, profileId),
                )
                else -> Unit
            }
        }

        if (kind == SyncRunKind.FOLDER && state.conflicts > 0 && !state.phase.isActive) {
            builder.addAction(
                0,
                context.getString(R.string.sync_action_view_conflicts),
                openIntent(
                    context,
                    kind,
                    NotificationDest.FOLDER_CONFLICTS,
                    profileId,
                ),
            )
        }

        when (state.phase) {
            SyncPhase.STOPPED, SyncPhase.INTERRUPTED, SyncPhase.CANCELLED -> builder.addAction(
                0,
                context.getString(R.string.sync_notif_continue),
                action(context, SyncNotificationActions.ACTION_CONTINUE, kind, profileId),
            )
            SyncPhase.PARTIAL, SyncPhase.FAILED -> builder.addAction(
                0,
                context.getString(R.string.sync_notif_retry),
                action(context, SyncNotificationActions.ACTION_RETRY, kind, profileId),
            )
            else -> Unit
        }

        if (state.phase.isActive && !state.currentItem.isNullOrBlank()) {
            builder.setPublicVersion(
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(iconFor(state.phase))
                    .setContentTitle(baseTitle(context, kind, state))
                    .setContentText(context.getString(R.string.sync_lock_screen_text))
                    .build(),
            )
        }
        return builder.build()
    }

    private fun progress(state: SyncNotificationState, builder: NotificationCompat.Builder) {
        when {
            state.phase == SyncPhase.SCANNING || state.phase == SyncPhase.PREPARING ||
                state.phase == SyncPhase.QUEUED || state.phase == SyncPhase.WAITING ->
                builder.setProgress(0, 0, true)
            state.bytesTotal > 0 ->
                builder.setProgress(
                    1000,
                    ((state.bytesDone * 1000) / state.bytesTotal).toInt().coerceIn(0, 1000),
                    false,
                )
            state.total > 0 -> {
                val permille = ((state.processed.toLong() * 1000) / state.total).toInt()
                builder.setProgress(1000, permille.coerceIn(0, 1000), false)
            }
            else -> builder.setProgress(0, 0, true)
        }
    }

    private fun title(context: Context, kind: SyncRunKind, state: SyncNotificationState): String {
        val base = baseTitle(context, kind, state)
        return when (state.phase) {
            SyncPhase.PREPARING -> base + " · " + context.getString(R.string.sync_phase_preparing)
            SyncPhase.QUEUED -> base + " · " + context.getString(R.string.sync_phase_queued)
            SyncPhase.WAITING -> base + " · " + blockLabel(context, state.blockReason)
            SyncPhase.SCANNING -> base + " · " + context.getString(R.string.sync_phase_scanning)
            SyncPhase.SYNCING -> base + " · " + context.getString(
                R.string.sync_phase_syncing,
                state.processed,
                state.total,
            )
            SyncPhase.STOPPING -> context.getString(R.string.sync_phase_stopping)
            SyncPhase.CANCELLING -> context.getString(R.string.sync_phase_cancelling)
            SyncPhase.STOPPED -> context.getString(R.string.sync_phase_stopped)
            SyncPhase.CANCELLED -> context.getString(R.string.sync_phase_cancelled)
            SyncPhase.INTERRUPTED -> context.getString(R.string.sync_phase_interrupted)
            SyncPhase.COMPLETED -> context.getString(R.string.sync_phase_completed)
            SyncPhase.PARTIAL -> context.getString(R.string.sync_phase_partial, state.failed)
            SyncPhase.FAILED -> context.getString(R.string.sync_phase_failed)
            SyncPhase.CONFLICTS_PENDING -> context.getString(R.string.sync_phase_conflicts)
            SyncPhase.DISABLED, SyncPhase.IDLE -> base
        }
    }

    private fun baseTitle(context: Context, kind: SyncRunKind, state: SyncNotificationState): String {
        val base = context.getString(
            if (kind == SyncRunKind.PHOTO) R.string.photo_sync_channel else R.string.folder_sync_channel,
        )
        return buildString {
            append(base)
            if (state.serverName.isNotBlank()) append(" · ").append(state.serverName)
            state.modeLabel?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
    }

    private fun text(context: Context, state: SyncNotificationState): String {
        state.summary?.let { if (!state.phase.isActive) return it }
        if (state.phase.isActive) {
            bytesText(context, state)?.let { return it }
        }
        return state.currentItem?.let { context.getString(R.string.sync_notif_current, it) }
            ?: blockLabel(context, state.blockReason)
    }

    private fun longText(context: Context, state: SyncNotificationState): String {
        val builder = StringBuilder()
        state.summary?.let { builder.append(it) }
        if (state.phase.isActive) {
            bytesText(context, state)?.let {
                if (builder.isNotEmpty()) builder.append(" · ")
                builder.append(it)
            }
        }
        state.currentItem?.takeIf { it.isNotBlank() }?.let {
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append(context.getString(R.string.sync_notif_current, it))
        }
        return builder.toString()
    }

    private fun bytesText(context: Context, state: SyncNotificationState): String? = when {
        state.bytesTotal > 0 && state.speedBytesPerSecond > 0 -> context.getString(
            R.string.sync_notif_bytes_speed,
            Formatter.formatShortFileSize(context, state.bytesDone),
            Formatter.formatShortFileSize(context, state.bytesTotal),
            Formatter.formatShortFileSize(context, state.speedBytesPerSecond),
        )
        state.bytesTotal > 0 -> context.getString(
            R.string.sync_notif_bytes,
            Formatter.formatShortFileSize(context, state.bytesDone),
            Formatter.formatShortFileSize(context, state.bytesTotal),
        )
        else -> null
    }

    private fun blockLabel(context: Context, reason: SyncBlockReason?): String = when (reason) {
        SyncBlockReason.NOT_LOGGED_IN -> context.getString(R.string.sync_block_not_logged_in)
        SyncBlockReason.NO_PERMISSION -> context.getString(R.string.sync_block_no_permission)
        SyncBlockReason.NO_LOCAL_FOLDER -> context.getString(R.string.sync_block_no_local_folder)
        SyncBlockReason.NO_TARGET -> context.getString(R.string.sync_block_no_target)
        SyncBlockReason.TARGET_MISSING -> context.getString(R.string.sync_block_target_missing)
        SyncBlockReason.REMOTE_OFFLINE -> context.getString(R.string.sync_block_remote_offline)
        SyncBlockReason.WAITING_WIFI -> context.getString(R.string.sync_block_waiting_wifi)
        SyncBlockReason.WAITING_CHARGING -> context.getString(R.string.sync_block_waiting_charging)
        SyncBlockReason.QUOTA_EXCEEDED -> context.getString(R.string.sync_block_quota)
        SyncBlockReason.AUTH_FAILED -> context.getString(R.string.sync_block_auth_failed)
        SyncBlockReason.ANOTHER_RUN -> context.getString(R.string.sync_block_another_run)
        SyncBlockReason.CONFLICTS_PENDING -> context.getString(R.string.sync_block_conflicts)
        SyncBlockReason.DELETE_CONFIRMATION_REQUIRED ->
            context.getString(R.string.sync_block_delete_confirmation)
        SyncBlockReason.NETWORK -> context.getString(R.string.sync_block_network)
        null -> ""
    }

    private fun iconFor(phase: SyncPhase): Int = when (phase) {
        SyncPhase.COMPLETED -> android.R.drawable.stat_sys_download_done
        SyncPhase.PARTIAL, SyncPhase.FAILED, SyncPhase.INTERRUPTED ->
            android.R.drawable.stat_notify_error
        SyncPhase.STOPPED, SyncPhase.CANCELLED -> android.R.drawable.stat_notify_sync_noanim
        else -> android.R.drawable.stat_sys_upload
    }

    private fun openIntent(
        context: Context,
        kind: SyncRunKind,
        dest: String,
        profileId: String,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        if (kind == SyncRunKind.PHOTO) 5301 else 5401,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationDest.EXTRA, dest)
            putExtra(NotificationDest.EXTRA_PROFILE, profileId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(
        context: Context,
        action: String,
        kind: SyncRunKind,
        profileId: String,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        (action.hashCode() * 31 + profileId.hashCode()) and 0x0FFFFFFF,
        Intent(context, SyncActionReceiver::class.java).apply {
            this.action = action
            putExtra(SyncNotificationActions.EXTRA_PROFILE, profileId)
            putExtra(SyncNotificationActions.EXTRA_KIND, kind.value)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}
