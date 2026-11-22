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

object FolderSyncNotifications {
    private const val CHANNEL_ID = "filehub_folder_sync"
    private const val ID = 5401

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.folder_sync_channel),
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
    ): ForegroundInfo {
        ensureChannel(context)
        val notification = notification(context, done, total, current)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context, done: Int, total: Int, current: String, id: Int = ID) {
        if (!canNotify(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification(context, done, total, current))
        }
    }

    @SuppressLint("MissingPermission")
    fun finished(context: Context, message: String, id: Int = ID) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(context.getString(R.string.folder_sync_channel))
            .setContentText(message)
            .setContentIntent(intent(context))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id + 1, notification) }
    }

    private fun notification(context: Context, done: Int, total: Int, current: String): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(context.getString(R.string.folder_sync_channel))
            .setContentText(if (total > 0) "$done / $total · $current" else current)
            .setContentIntent(intent(context))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (total > 0) builder.setProgress(total, done, false) else builder.setProgress(0, 0, true)
        return builder.build()
    }

    private fun intent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
