package filehub.android.data.transfer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import filehub.android.MainActivity
import filehub.android.R
import java.util.concurrent.atomic.AtomicInteger

object TransferNotifications {
    const val CHANNEL_ID = "filehub_transfers"
    private val ids = AtomicInteger(4200)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.transfer_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.transfer_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun foregroundInfo(context: Context, item: TransferItem): ForegroundInfo {
        ensureChannel(context)
        val notification = progressNotification(context, item)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                notificationId(item),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId(item), notification)
        }
    }

    @SuppressLint("MissingPermission") // guarded by canPostNotifications()
    fun updateProgress(context: Context, item: TransferItem) {
        if (!canPostNotifications(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(item), progressNotification(context, item))
        }
    }

    @SuppressLint("MissingPermission") // guarded by canPostNotifications()
    fun notifyFinished(context: Context, item: TransferItem) {
        ensureChannel(context)
        val title = if (item.status == TransferStatus.COMPLETED) {
            context.getString(R.string.transfer_completed)
        } else {
            context.getString(R.string.transfer_failed)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(item.name)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
        if (!canPostNotifications(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(item) + 1000, notification)
        }
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun progressNotification(context: Context, item: TransferItem): Notification {
        val title = if (item.kind == TransferKind.UPLOAD) {
            context.getString(R.string.transfer_uploading, item.name)
        } else {
            context.getString(R.string.transfer_downloading, item.name)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (item.kind == TransferKind.UPLOAD) {
                    android.R.drawable.stat_sys_upload
                } else {
                    android.R.drawable.stat_sys_download
                },
            )
            .setContentTitle(title)
            .setContentText("${(item.progress * 100).toInt()}%")
            .setContentIntent(contentIntent(context))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (item.size > 0) {
            builder.setProgress(100, (item.progress * 100).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(item: TransferItem): Int =
        (item.id.hashCode() and 0x0FFFFFFF) or ids.getAndIncrement().and(0)
}
