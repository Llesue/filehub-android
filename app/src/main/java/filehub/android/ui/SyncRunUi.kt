package filehub.android.ui

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import filehub.android.R
import filehub.android.ui.theme.FileHubFileGreen
import filehub.android.data.sync.SyncBlockReason
import filehub.android.data.sync.SyncCancelReason
import filehub.android.data.sync.SyncLogEntity
import filehub.android.data.sync.SyncLogStore
import filehub.android.data.sync.SyncPhase
import filehub.android.data.sync.SyncRunEntity
import filehub.android.data.sync.SyncRunKind
import filehub.android.data.sync.ScheduleStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

internal val DetailWideBreakpoint = 720.dp

@Composable
internal fun rememberEffectivePhase(run: SyncRunEntity?): SyncPhase {
    var now by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(run?.updatedAt, run?.phase) {
        now = System.currentTimeMillis() / 1000
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis() / 1000
        }
    }
    return run?.effectivePhase(now) ?: SyncPhase.IDLE
}

/**
 * Left column: settings. Right column: live state machine and sync log.
 * Below 720dp the two columns collapse into one scroll column and the log opens
 * as a dialog instead of taking a permanent pane.
 */
@Composable
internal fun ResponsiveDetailLayout(
    status: @Composable ColumnScope.(Boolean) -> Unit,
    settings: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= DetailWideBreakpoint
        val paneWidth = if (maxWidth >= 1100.dp) 400.dp else 360.dp
        Column(Modifier.fillMaxSize()) {
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .width(paneWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        settings()
                    }
                    VerticalDivider()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        status(true)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    status(false)
                    settings()
                }
            }
        }
    }
}

@Composable
internal fun SyncStatusPanel(
    kind: SyncRunKind,
    enabled: Boolean,
    run: SyncRunEntity?,
    lastError: String?,
    queued: Boolean,
    logs: List<SyncLogEntity>,
    wide: Boolean,
    onSyncNow: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onOpenLogs: () -> Unit,
    onFullSync: (() -> Unit)? = null,
    syncNowLabel: String? = null,
    onClearLogs: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = if (wide) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth()
        },
    ) {
        SyncRunStatusCard(
            kind = kind,
            enabled = enabled,
            run = run,
            lastError = lastError,
            queued = queued,
            onSyncNow = onSyncNow,
            onStop = onStop,
            onCancel = onCancel,
            onFullSync = onFullSync,
            syncNowLabel = syncNowLabel,
            extra = extra,
        )
        if (wide) {
            Spacer(Modifier.height(12.dp))
            SyncLogList(
                logs = logs,
                onClear = onClearLogs,
                modifier = Modifier.weight(1f),
            )
        } else if (logs.isNotEmpty()) {
            TextButton(onClick = onOpenLogs) {
                Text(stringResource(R.string.sync_log_open) + " (${logs.size})")
            }
        }
    }
}

@Composable
internal fun SyncRunStatusCard(
    kind: SyncRunKind,
    enabled: Boolean,
    run: SyncRunEntity?,
    lastError: String?,
    queued: Boolean,
    onSyncNow: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onFullSync: (() -> Unit)? = null,
    syncNowLabel: String? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val phase = rememberEffectivePhase(run)
    val active = phase.isActive

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (phase == SyncPhase.FAILED || phase == SyncPhase.PARTIAL) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = phaseTitle(phase, run),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    phase == SyncPhase.STOPPING || phase == SyncPhase.CANCELLING -> Unit
                    active -> {
                        TextButton(onClick = onStop) {
                            Text(stringResource(R.string.sync_action_stop))
                        }
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.sync_action_cancel))
                        }
                    }
                    phase == SyncPhase.STOPPED || phase == SyncPhase.INTERRUPTED ||
                        phase == SyncPhase.CANCELLED -> {
                        onFullSync?.let { full ->
                            TextButton(onClick = full, enabled = enabled) {
                                Text(stringResource(R.string.sync_action_full))
                            }
                        }
                        TextButton(onClick = onSyncNow, enabled = enabled) {
                            Text(stringResource(R.string.sync_action_continue))
                        }
                    }
                    phase == SyncPhase.PARTIAL || phase == SyncPhase.FAILED -> {
                        onFullSync?.let { full ->
                            TextButton(onClick = full, enabled = enabled) {
                                Text(stringResource(R.string.sync_action_full))
                            }
                        }
                        TextButton(onClick = onSyncNow, enabled = enabled) {
                            Text(stringResource(R.string.sync_action_retry))
                        }
                    }
                    else -> {
                        onFullSync?.let { full ->
                            TextButton(onClick = full, enabled = enabled) {
                                Text(stringResource(R.string.sync_action_full))
                            }
                        }
                        TextButton(onClick = onSyncNow, enabled = enabled) {
                            Text(
                                syncNowLabel
                                    ?: stringResource(R.string.sync_action_sync_now),
                            )
                        }
                    }
                }
            }

            SyncPhaseStepper(kind = kind, phase = phase)

            when {
                phase == SyncPhase.PREPARING || phase == SyncPhase.SCANNING ||
                    phase == SyncPhase.QUEUED || phase == SyncPhase.WAITING ->
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                run != null && run.bytesTotal > 0 -> LinearProgressIndicator(
                    progress = { (run.bytesDone.toFloat() / run.bytesTotal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                run != null && run.total > 0 -> LinearProgressIndicator(
                    progress = { (run.processed.toFloat() / run.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (run != null && (active || phase == SyncPhase.PARTIAL)) {
                if (run.bytesTotal > 0) {
                    Text(
                        text = stringResource(
                            R.string.sync_progress_bytes,
                            Formatter.formatShortFileSize(LocalContext.current, run.bytesDone),
                            Formatter.formatShortFileSize(LocalContext.current, run.bytesTotal),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val counts = buildList {
                    if (run.succeeded > 0) add(stringResource(R.string.sync_counts_success, run.succeeded))
                    if (run.skipped > 0) add(stringResource(R.string.sync_counts_skipped, run.skipped))
                    if (run.failed > 0) add(stringResource(R.string.sync_counts_failed, run.failed))
                    if (run.conflicts > 0) add(stringResource(R.string.sync_counts_conflicts, run.conflicts))
                }
                if (counts.isNotEmpty()) {
                    Text(
                        text = counts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                run.currentItem?.takeIf { it.isNotBlank() }?.let { item ->
                    val action = currentActionLabel(run.currentAction)
                    Text(
                        text = stringResource(
                            R.string.sync_notif_current,
                            listOfNotNull(action, item).joinToString(" "),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            run?.syncBlockReason?.let { reason ->
                Text(
                    text = blockReasonLabel(reason),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            run?.syncCancelReason?.takeIf { phase.isTerminal }?.let { reason ->
                Text(
                    text = cancelReasonLabel(reason),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!active && !lastError.isNullOrBlank()) {
                Text(
                    text = lastError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (queued) {
                Text(
                    text = stringResource(R.string.sync_queued_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            extra()
        }
    }
}

@Composable
private fun SyncPhaseStepper(kind: SyncRunKind, phase: SyncPhase) {
    val steps = if (kind == SyncRunKind.PHOTO) {
        listOf(
            R.string.sync_step_prepare,
            R.string.sync_step_scan,
            R.string.sync_step_apply,
            R.string.sync_step_finish,
        )
    } else {
        listOf(
            R.string.sync_step_scan,
            R.string.sync_step_compare,
            R.string.sync_step_apply,
            R.string.sync_step_finish,
        )
    }
    val activeIndex = when (phase) {
        SyncPhase.DISABLED, SyncPhase.IDLE -> -1
        SyncPhase.PREPARING, SyncPhase.QUEUED, SyncPhase.WAITING -> 0
        SyncPhase.SCANNING -> if (kind == SyncRunKind.PHOTO) 1 else 0
        SyncPhase.SYNCING, SyncPhase.STOPPING, SyncPhase.CANCELLING -> 2
        else -> 3
    }
    val activeColor = if (phase == SyncPhase.FAILED) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        steps.forEachIndexed { index, label ->
            val color = when {
                index == activeIndex -> activeColor
                index < activeIndex -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(color, CircleShape),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = if (index == activeIndex) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (index != steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

@Composable
private fun currentActionLabel(action: String?): String? = when (action) {
    "skip" -> stringResource(R.string.sync_action_skip)
    "upload" -> stringResource(R.string.sync_action_upload)
    "download" -> stringResource(R.string.sync_action_download)
    "verify" -> stringResource(R.string.sync_action_verify)
    "rename" -> stringResource(R.string.sync_action_rename)
    "delete" -> stringResource(R.string.sync_action_delete)
    "conflict" -> stringResource(R.string.sync_action_conflict)
    "scan" -> stringResource(R.string.sync_action_scan)
    else -> null
}

@Composable
internal fun SyncScheduleStatusRow(
    enabled: Boolean,
    manual: Boolean,
    status: ScheduleStatus,
    onRebuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled || status.loading) return
    val context = LocalContext.current
    val next = status.nextRunAt?.let { millis ->
        DateFormat.getTimeFormat(context).format(Date(millis))
    }
    val warning = !manual && !status.scheduled
    val text = when {
        manual -> stringResource(R.string.sync_schedule_manual)
        status.scheduled -> if (next != null) {
            stringResource(R.string.sync_schedule_scheduled, next)
        } else {
            stringResource(R.string.sync_schedule_scheduled_short)
        }
        status.cancelled -> stringResource(R.string.sync_schedule_cancelled)
        status.failed -> stringResource(R.string.sync_schedule_failed)
        else -> stringResource(R.string.sync_schedule_missing)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (warning) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            if (warning) {
                TextButton(onClick = onRebuild) {
                    Text(stringResource(R.string.sync_schedule_rebuild))
                }
            }
        }
    }
}

@Composable
internal fun SyncLogList(
    logs: List<SyncLogEntity>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = remember(logs) { logs.asReversed() }
    val listState = rememberLazyListState()
    val newestId = ordered.lastOrNull()?.id
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(newestId) {
        if (ordered.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        // The list is capped at 200, so `size` stops changing and `newestId`
        // is the signal that a new line was appended. On first layout the
        // visible range is not reliable yet, so always jump to the newest.
        if (!initialScrollDone || lastVisible < 0 || lastVisible >= ordered.lastIndex - 2) {
            listState.scrollToItem(ordered.lastIndex)
            initialScrollDone = true
        }
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sync_log_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (ordered.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.sync_log_clear))
                }
            }
        }
        if (ordered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.sync_log_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(ordered, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = logTime(entry.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.width(50.dp),
                        )
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (entry.level) {
                                SyncLogStore.LEVEL_ERROR -> MaterialTheme.colorScheme.error
                                SyncLogStore.LEVEL_WARN -> MaterialTheme.colorScheme.tertiary
                                SyncLogStore.LEVEL_START -> MaterialTheme.colorScheme.primary
                                SyncLogStore.LEVEL_SUCCESS -> FileHubFileGreen
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SyncLogDialog(
    logs: List<SyncLogEntity>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_log_title)) },
        text = {
            SyncLogList(
                logs = logs,
                onClear = onClear,
                modifier = Modifier.height(420.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.file_close))
            }
        },
    )
}

@Composable
private fun logTime(epochSeconds: Long): String {
    val formatter = remember {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
    return formatter.format(Date(epochSeconds * 1000))
}

@Composable
internal fun phaseTitle(phase: SyncPhase, run: SyncRunEntity?): String = when (phase) {
    SyncPhase.DISABLED -> stringResource(R.string.sync_phase_off)
    SyncPhase.IDLE -> stringResource(R.string.sync_phase_idle)
    SyncPhase.PREPARING -> stringResource(R.string.sync_phase_preparing)
    SyncPhase.QUEUED -> stringResource(R.string.sync_phase_queued)
    SyncPhase.WAITING -> stringResource(R.string.sync_phase_queued)
    SyncPhase.SCANNING -> stringResource(R.string.sync_phase_scanning)
    SyncPhase.SYNCING -> stringResource(
        R.string.sync_phase_syncing,
        run?.processed ?: 0,
        run?.total ?: 0,
    )
    SyncPhase.STOPPING -> stringResource(R.string.sync_phase_stopping)
    SyncPhase.CANCELLING -> stringResource(R.string.sync_phase_cancelling)
    SyncPhase.STOPPED -> stringResource(R.string.sync_phase_stopped)
    SyncPhase.CANCELLED -> stringResource(R.string.sync_phase_cancelled)
    SyncPhase.INTERRUPTED -> stringResource(R.string.sync_phase_interrupted)
    SyncPhase.COMPLETED -> stringResource(R.string.sync_phase_completed)
    SyncPhase.PARTIAL -> stringResource(R.string.sync_phase_partial, run?.failed ?: 0)
    SyncPhase.FAILED -> stringResource(R.string.sync_phase_failed)
    SyncPhase.CONFLICTS_PENDING -> stringResource(R.string.sync_phase_conflicts)
}

@Composable
internal fun blockReasonLabel(reason: SyncBlockReason): String = when (reason) {
    SyncBlockReason.NOT_LOGGED_IN -> stringResource(R.string.sync_block_not_logged_in)
    SyncBlockReason.NO_PERMISSION -> stringResource(R.string.sync_block_no_permission)
    SyncBlockReason.NO_LOCAL_FOLDER -> stringResource(R.string.sync_block_no_local_folder)
    SyncBlockReason.NO_TARGET -> stringResource(R.string.sync_block_no_target)
    SyncBlockReason.TARGET_MISSING -> stringResource(R.string.sync_block_target_missing)
    SyncBlockReason.REMOTE_OFFLINE -> stringResource(R.string.sync_block_remote_offline)
    SyncBlockReason.WAITING_WIFI -> stringResource(R.string.sync_block_waiting_wifi)
    SyncBlockReason.WAITING_CHARGING -> stringResource(R.string.sync_block_waiting_charging)
    SyncBlockReason.QUOTA_EXCEEDED -> stringResource(R.string.sync_block_quota)
    SyncBlockReason.AUTH_FAILED -> stringResource(R.string.sync_block_auth_failed)
    SyncBlockReason.ANOTHER_RUN -> stringResource(R.string.sync_block_another_run)
    SyncBlockReason.CONFLICTS_PENDING -> stringResource(R.string.sync_block_conflicts)
    SyncBlockReason.DELETE_CONFIRMATION_REQUIRED ->
        stringResource(R.string.sync_block_delete_confirmation)
    SyncBlockReason.NETWORK -> stringResource(R.string.sync_block_network)
}

@Composable
internal fun cancelReasonLabel(reason: SyncCancelReason): String = when (reason) {
    SyncCancelReason.USER_STOP -> stringResource(R.string.sync_cancel_user_stop)
    SyncCancelReason.USER_CANCEL -> stringResource(R.string.sync_cancel_user_cancel)
    SyncCancelReason.TARGET_CHANGED -> stringResource(R.string.sync_cancel_target_changed)
    SyncCancelReason.CONFIG_CHANGED -> stringResource(R.string.sync_cancel_config_changed)
    SyncCancelReason.LOGOUT -> stringResource(R.string.sync_cancel_logout)
    SyncCancelReason.SERVER_REMOVED -> stringResource(R.string.sync_cancel_server_removed)
    SyncCancelReason.AUTH_FAILED -> stringResource(R.string.sync_cancel_auth_failed)
    SyncCancelReason.SYSTEM_STOPPED -> stringResource(R.string.sync_cancel_system_stopped)
    SyncCancelReason.WORK_REPLACED -> stringResource(R.string.sync_cancel_work_replaced)
    SyncCancelReason.PROCESS_DEATH -> stringResource(R.string.sync_cancel_process_death)
    SyncCancelReason.UNKNOWN -> stringResource(R.string.sync_cancel_unknown)
}
