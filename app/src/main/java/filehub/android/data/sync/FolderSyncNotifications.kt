package filehub.android.data.sync

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import filehub.android.R

object FolderSyncNotifications {
    private const val CHANNEL_ID = "filehub_folder_sync"
    private const val ID = 5401

    fun idFor(profileId: String): Int = ID + (profileId.hashCode() and 0x0FFFFF)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.folder_sync_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                enableVibration(false)
            },
        )
    }

    fun foregroundInfo(
        context: Context,
        state: SyncNotificationState,
        profileId: String,
    ): ForegroundInfo {
        ensureChannel(context)
        val notification = SyncNotificationBuilder.build(
            context = context,
            channelId = CHANNEL_ID,
            kind = SyncRunKind.FOLDER,
            dest = NotificationDest.FOLDER_SYNC,
            profileId = profileId,
            state = state,
            ongoing = true,
        )
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                idFor(profileId),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(idFor(profileId), notification)
        }
    }

    @SuppressLint("MissingPermission") // guarded by canNotify()
    fun update(context: Context, state: SyncNotificationState, profileId: String) {
        if (!SyncNotificationBuilder.canNotify(context)) return
        ensureChannel(context)
        runCatching {
            NotificationManagerCompat.from(context).notify(
                idFor(profileId),
                SyncNotificationBuilder.build(
                    context,
                    CHANNEL_ID,
                    SyncRunKind.FOLDER,
                    NotificationDest.FOLDER_SYNC,
                    profileId,
                    state,
                    ongoing = true,
                ),
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun finished(context: Context, state: SyncNotificationState, profileId: String) {
        if (!SyncNotificationBuilder.canNotify(context)) return
        ensureChannel(context)
        // Remove the foreground/ongoing notification before posting the summary.
        runCatching { NotificationManagerCompat.from(context).cancel(idFor(profileId)) }
        runCatching {
            NotificationManagerCompat.from(context).notify(
                idFor(profileId) + 1,
                SyncNotificationBuilder.build(
                    context,
                    CHANNEL_ID,
                    SyncRunKind.FOLDER,
                    NotificationDest.FOLDER_SYNC,
                    profileId,
                    state,
                    ongoing = false,
                ),
            )
        }
    }

    fun cancel(context: Context, profileId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(idFor(profileId)) }
    }
}
