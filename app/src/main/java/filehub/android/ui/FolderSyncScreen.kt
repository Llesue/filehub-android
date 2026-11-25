package filehub.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import filehub.android.data.sync.MediaStreams
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.R
import filehub.android.data.SessionState
import filehub.android.data.sync.SyncRunKind

@Composable
fun FolderSyncScreen(
    viewModel: FolderSyncViewModel,
    remoteFolderPicker: DirectoryPickerViewModel,
    session: SessionState,
    onBack: () -> Unit,
    onOpenConflicts: () -> Unit,
) {
    val folderPickerState by remoteFolderPicker.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val active = session.active

    var rootId by rememberSaveable { mutableStateOf<Long?>(null) }
    var remotePath by rememberSaveable { mutableStateOf("") }
    var localTreeUri by rememberSaveable { mutableStateOf("") }
    var localName by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("upload") }
    var deleteSync by rememberSaveable { mutableStateOf(false) }
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var chargingOnly by rememberSaveable { mutableStateOf(false) }
    var schedule by rememberSaveable { mutableStateOf("daily") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var masterEnabled by rememberSaveable { mutableStateOf(false) }
    var rootPicker by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var scheduleMenu by remember { mutableStateOf(false) }
    var confirmTargetChange by remember { mutableStateOf(false) }
    var confirmDeleteSync by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var remoteHint by remember { mutableStateOf(false) }
    var confirmFullSync by remember { mutableStateOf(false) }
    var permissionVersion by remember { mutableIntStateOf(0) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionVersion++ }
    val hasLocationPermission = remember(permissionVersion, context) {
        Build.VERSION.SDK_INT < 29 || MediaStreams.hasLocationPermission(context) ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            localTreeUri = uri.toString()
            localName = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment.orEmpty()
        }
    }

    LaunchedEffect(config, initialized) {
        val existing = config
        if (existing != null && !initialized) {
            rootId = existing.rootId
            remotePath = existing.remotePath
            localTreeUri = existing.localTreeUri
            localName = existing.localName
            mode = existing.mode
            deleteSync = existing.deleteSync
            wifiOnly = existing.wifiOnly
            chargingOnly = existing.chargingOnly
            schedule = existing.schedule
            masterEnabled = existing.enabled
            initialized = true
        }
    }

    LaunchedEffect(config?.enabled) {
        masterEnabled = config?.enabled ?: masterEnabled
    }

    LaunchedEffect(config?.lastRunAt) {
        if (state.queued) viewModel.clearQueued()
    }

    LaunchedEffect(config) {
        viewModel.refreshStats()
    }

    val roots = session.roots
    val usableRoots = roots.filter { it.online != false && !it.readOnly }
    val root = roots.firstOrNull { it.id == rootId }
        ?: usableRoots.firstOrNull()
        ?: roots.firstOrNull()
    val phase = rememberEffectivePhase(run)
    val running = phase.isActive
    val formEnabled = masterEnabled && !running
    val targetChanged = config?.let { cfg ->
        cfg.rootId != root?.id ||
            cfg.remotePath != remotePath.trim('/') ||
            cfg.localTreeUri != localTreeUri ||
            cfg.mode != mode
    } == true
    val dirty = config?.let { cfg ->
        targetChanged ||
            cfg.localName != localName ||
            cfg.deleteSync != deleteSync ||
            cfg.wifiOnly != wifiOnly ||
            cfg.chargingOnly != chargingOnly ||
            cfg.schedule != schedule
    } ?: true

    LaunchedEffect(dirty) {
        if (dirty) viewModel.resetSaved()
    }

    fun persist(allowOverlap: Boolean = false) {
        if (root == null) return
        viewModel.save(
            profile = active ?: return,
            root = root,
            remotePath = remotePath,
            localTreeUri = localTreeUri,
            localName = localName,
            mode = mode,
            deleteSync = deleteSync,
            wifiOnly = wifiOnly,
            chargingOnly = chargingOnly,
            schedule = schedule,
            allowOverlap = allowOverlap,
        )
    }

    DetailScaffold(
        title = stringResource(R.string.folder_sync_screen),
        onBack = onBack,
        compact = true,
        actions = {
            if (state.saving) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                val label = when {
                    running -> stringResource(R.string.sync_running)
                    targetChanged -> stringResource(R.string.sync_apply_and_resync)
                    config == null -> stringResource(R.string.sync_save_and_enable)
                    state.saved && !dirty -> stringResource(R.string.sync_saved_check)
                    !dirty -> stringResource(R.string.sync_no_changes)
                    else -> stringResource(R.string.sync_save_changes)
                }
                TextButton(
                    onClick = {
                        if (root == null) return@TextButton
                        if (targetChanged) {
                            confirmTargetChange = true
                        } else {
                            persist()
                        }
                    },
                    enabled = masterEnabled && !running &&
                        root != null && localTreeUri.isNotBlank() && dirty,
                ) {
                    Text(text = label, maxLines = 1)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxWidth().padding(padding)) {
            if (active == null) {
                Text(
                    text = stringResource(R.string.login_error_generic),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                ResponsiveDetailLayout(
                    status = { wide ->
                        SyncStatusPanel(
                            kind = SyncRunKind.FOLDER,
                            enabled = masterEnabled,
                            run = run,
                            lastError = state.error ?: config?.lastError,
                            queued = state.queued,
                            logs = logs,
                            wide = wide,
                            onSyncNow = viewModel::syncNow,
                            onStop = { viewModel.requestStop(cancel = false) },
                            onCancel = { viewModel.requestStop(cancel = true) },
                            onOpenLogs = { showLogs = true },
                            onClearLogs = viewModel::clearLogs,
                            onFullSync = { confirmFullSync = true },
                            syncNowLabel = stringResource(R.string.sync_action_daily),
                            extra = {
                                Text(
                                    text = stringResource(R.string.folder_sync_pending) +
                                        ": ${state.pending} · " +
                                        stringResource(R.string.folder_sync_conflicts) +
                                        ": ${state.conflicts}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.conflicts > 0) {
                                    TextButton(onClick = onOpenConflicts) {
                                        Text(
                                            text = stringResource(
                                                R.string.sync_action_view_conflicts,
                                            ) + " (${state.conflicts})",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                        )
                    },
                    settings = {
                        if (!hasLocationPermission) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(
                                            R.string.folder_sync_location_missing,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            locationPermissionLauncher.launch(
                                                Manifest.permission.ACCESS_MEDIA_LOCATION,
                                            )
                                        },
                                    ) {
                                        Text(stringResource(R.string.photo_sync_location_button))
                                    }
                                }
                            }
                        }
                        if (state.saved && !dirty) {
                            Text(
                                text = stringResource(R.string.photo_sync_saved),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.folder_sync_enable),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.folder_sync_enable_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                AppSwitch(
                                    checked = masterEnabled,
                                    onCheckedChange = { value ->
                                        masterEnabled = value
                                        if (config != null) viewModel.setEnabled(value)
                                    },
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.alpha(if (masterEnabled) 1f else 0.45f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.folder_sync_local),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { folderPicker.launch(null) },
                                enabled = formEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(localName.ifBlank { stringResource(R.string.folder_sync_choose) })
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (root?.isRemote == true) {
                                            Modifier.clickable { remoteHint = true }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.photo_sync_target_root),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (root?.isRemote == true) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        painter = painterResource(R.drawable.ic_alert_circle),
                                        contentDescription = stringResource(
                                            R.string.sync_remote_root_warning_cd,
                                        ),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            RootField(
                                label = stringResource(R.string.photo_sync_target_root),
                                root = root,
                                enabled = formEnabled,
                                onClick = { rootPicker = true },
                            )
                            if (root != null && config?.rootId?.let { it != root.id } == true) {
                                Text(
                                    text = stringResource(R.string.sync_root_changed_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DirectoryField(
                                label = stringResource(R.string.photo_sync_target_path),
                                rootAlias = root?.alias.orEmpty(),
                                path = remotePath,
                                enabled = formEnabled && root != null,
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    remoteFolderPicker.open(
                                        roots = roots,
                                        initialRootId = rootId,
                                        initialPath = remotePath,
                                        mode = DirectoryPickerMode.Sync,
                                    )
                                },
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.folder_sync_mode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Box {
                                    OutlinedButton(
                                        onClick = { modeMenu = true },
                                        enabled = formEnabled,
                                    ) {
                                        Text(modeLabel(mode))
                                    }
                                    DropdownMenu(
                                        expanded = modeMenu,
                                        onDismissRequest = { modeMenu = false },
                                    ) {
                                        listOf("upload", "download", "two_way").forEach { value ->
                                            CompactDropdownMenuItem(
                                                text = { Text(modeLabel(value)) },
                                                onClick = {
                                                    mode = value
                                                    modeMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            FolderAppSwitch(
                                stringResource(R.string.folder_sync_delete),
                                deleteSync,
                                formEnabled,
                            ) { value ->
                                if (value) confirmDeleteSync = true else deleteSync = false
                            }
                            FolderAppSwitch(
                                stringResource(R.string.photo_sync_wifi_only),
                                wifiOnly,
                                formEnabled,
                            ) { wifiOnly = it }
                            FolderAppSwitch(
                                stringResource(R.string.photo_sync_charging_only),
                                chargingOnly,
                                formEnabled,
                            ) { chargingOnly = it }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.photo_sync_schedule),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Box {
                                    OutlinedButton(
                                        onClick = { scheduleMenu = true },
                                        enabled = formEnabled,
                                    ) {
                                        Text(folderScheduleLabel(schedule))
                                    }
                                    DropdownMenu(
                                        expanded = scheduleMenu,
                                        onDismissRequest = { scheduleMenu = false },
                                    ) {
                                        listOf("manual", "15m", "hourly", "daily", "weekly")
                                            .forEach { value ->
                                                CompactDropdownMenuItem(
                                                    text = { Text(folderScheduleLabel(value)) },
                                                    onClick = {
                                                        schedule = value
                                                        scheduleMenu = false
                                                    },
                                                )
                                            }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    if (confirmFullSync) {
        AlertDialog(
            onDismissRequest = { confirmFullSync = false },
            title = { Text(stringResource(R.string.folder_sync_full_confirm_title)) },
            text = { Text(stringResource(R.string.folder_sync_full_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFullSync = false
                        viewModel.fullSync()
                    },
                ) {
                    Text(stringResource(R.string.photo_sync_full_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFullSync = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (remoteHint) {
        AlertDialog(
            onDismissRequest = { remoteHint = false },
            title = { Text(stringResource(R.string.sync_remote_root_warning_title)) },
            text = { Text(stringResource(R.string.sync_remote_root_warning_body)) },
            confirmButton = {
                TextButton(onClick = { remoteHint = false }) {
                    Text(stringResource(R.string.file_close))
                }
            },
        )
    }
    if (rootPicker) {
        RootPickerDialog(
            roots = roots,
            selectedId = root?.id,
            onDismiss = { rootPicker = false },
            onPick = { picked ->
                rootId = picked.id
                rootPicker = false
            },
        )
    }
    if (showLogs) {
        SyncLogDialog(
            logs = logs,
            onDismiss = { showLogs = false },
            onClear = viewModel::clearLogs,
        )
    }
    if (folderPickerState.visible) {
        DirectoryPickerDialog(
            state = folderPickerState,
            viewModel = remoteFolderPicker,
            onPick = { pickedRootId, selected ->
                rootId = pickedRootId
                remotePath = selected
                remoteFolderPicker.close()
                focusManager.clearFocus()
                keyboard?.hide()
            },
        )
    }
    if (confirmTargetChange) {
        AlertDialog(
            onDismissRequest = { confirmTargetChange = false },
            title = { Text(stringResource(R.string.folder_sync_target_change_title)) },
            text = { Text(stringResource(R.string.folder_sync_target_change_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmTargetChange = false
                        persist()
                    },
                ) {
                    Text(stringResource(R.string.dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTargetChange = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (confirmDeleteSync) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSync = false },
            title = { Text(stringResource(R.string.folder_sync_delete_title)) },
            text = { Text(stringResource(R.string.folder_sync_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteSync = false
                        deleteSync = true
                    },
                ) {
                    Text(stringResource(R.string.dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSync = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (state.overlap) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOverlap,
            title = { Text(stringResource(R.string.sync_overlap_title)) },
            text = { Text(stringResource(R.string.sync_overlap_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissOverlap()
                        persist(allowOverlap = true)
                    },
                ) {
                    Text(stringResource(R.string.sync_overlap_save_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissOverlap) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun FolderAppSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    AppSwitchRow(
        label = label,
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
    )
}

@Composable
private fun modeLabel(value: String): String = when (value) {
    "download" -> stringResource(R.string.folder_sync_mode_download)
    "two_way" -> stringResource(R.string.folder_sync_mode_two_way)
    else -> stringResource(R.string.folder_sync_mode_upload)
}

@Composable
private fun folderScheduleLabel(value: String): String = when (value) {
    "manual" -> stringResource(R.string.photo_sync_schedule_manual)
    "15m" -> stringResource(R.string.photo_sync_schedule_15m)
    "hourly" -> stringResource(R.string.photo_sync_schedule_hourly)
    "weekly" -> stringResource(R.string.photo_sync_schedule_weekly)
    else -> stringResource(R.string.photo_sync_schedule_daily)
}
