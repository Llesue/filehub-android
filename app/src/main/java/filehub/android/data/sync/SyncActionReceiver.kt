package filehub.android.data.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import filehub.android.FileHubApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles stop / cancel / continue / retry from the sync notification. */
class SyncActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (!SyncNotificationActions.matches(action)) return
        val profileId = intent.getStringExtra(SyncNotificationActions.EXTRA_PROFILE) ?: return
        val kind = SyncRunKind.from(
            intent.getStringExtra(SyncNotificationActions.EXTRA_KIND) ?: return,
        )
        val app = context.applicationContext as? FileHubApplication ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val runs = when (kind) {
                    SyncRunKind.PHOTO -> app.container.photoSyncRepository.runs
                    SyncRunKind.FOLDER -> app.container.folderSyncRepository.runs
                }
                when (action) {
                    SyncNotificationActions.ACTION_STOP,
                    SyncNotificationActions.ACTION_CANCEL,
                    -> {
                        val cancel = action == SyncNotificationActions.ACTION_CANCEL
                        when (kind) {
                            SyncRunKind.PHOTO -> app.container.photoSyncRepository
                                .requestStop(profileId, cancel)
                            SyncRunKind.FOLDER -> app.container.folderSyncRepository
                                .requestStop(profileId, cancel)
                        }
                        val phase = if (cancel) SyncPhase.CANCELLING else SyncPhase.STOPPING
                        runs.current(profileId, kind)?.let { run ->
                            runs.progress(run, phase = phase)
                        }
                    }
                    SyncNotificationActions.ACTION_CONTINUE,
                    SyncNotificationActions.ACTION_RETRY,
                    -> when (kind) {
                        SyncRunKind.PHOTO -> app.container.photoSyncRepository.syncNow(profileId)
                        SyncRunKind.FOLDER -> app.container.folderSyncRepository.syncNow(profileId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
