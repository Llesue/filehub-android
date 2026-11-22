package filehub.android.data.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import filehub.android.MainActivity
import filehub.android.R

object PhotoSyncNotifications {
    private const val CHANNEL_ID = "filehub_photo_sync"
    private const val ID = 5301

    fun idFor(profileId: String): Int = ID + (profileId.hashCode() and 0x0FFFFF)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.photo_sync_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun foregroundInfo(
        context: Context,
        done: Int,
        total: Int,
        current: String,
        id: Int = ID,
        serverName: String = "",
        profileId: String = "",
    ): ForegroundInfo {
        ensureChannel(context)
        val notification = notification(context, done, total, current, serverName, profileId, id)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    @SuppressLint("MissingPermission") // guarded by canNotify()
    fun update(
        context: Context,
        done: Int,
        total: Int,
        current: String,
        id: Int = ID,
        serverName: String = "",
        profileId: String = "",
    ) {
        if (!canNotify(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(
                id,
                notification(context, done, total, current, serverName, profileId, id),
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun finished(
        context: Context,
        message: String,
        id: Int = ID,
        serverName: String = "",
        profileId: String = "",
    ) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title(context, serverName))
            .setContentText(message)
            .setContentIntent(intent(context, id, profileId))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id + 1, notification) }
    }

    private fun title(context: Context, serverName: String): String {
        val channel = context.getString(R.string.photo_sync_channel)
        return if (serverName.isBlank()) channel else "$channel · $serverName"
    }

    private fun notification(
        context: Context,
        done: Int,
        total: Int,
        current: String,
        serverName: String,
        profileId: String,
        id: Int,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title(context, serverName))
            .setContentText(if (total > 0) "$done / $total · $current" else current)
            .setContentIntent(intent(context, id, profileId))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (total > 0) builder.setProgress(total, done, false) else builder.setProgress(0, 0, true)
        return builder.build()
    }

    private fun intent(context: Context, id: Int, profileId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(NotificationDest.EXTRA, NotificationDest.PHOTO_SYNC)
                putExtra(NotificationDest.EXTRA_PROFILE, profileId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}
