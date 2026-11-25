package filehub.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.R
import filehub.android.data.SessionState
import filehub.android.data.sync.PhotoSyncConfigEntity
import filehub.android.data.sync.SyncRunKind
import kotlinx.serialization.json.Json

@Composable
fun PhotoSyncScreen(
    viewModel: PhotoSyncViewModel,
    folderPicker: DirectoryPickerViewModel,
    session: SessionState,
    onBack: () -> Unit,
) {
    val folderPickerState by folderPicker.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val active = session.active

    var selectedRootId by rememberSaveable { mutableStateOf<Long?>(null) }
    var remotePath by rememberSaveable { mutableStateOf("") }
    var includeVideos by rememberSaveable { mutableStateOf(true) }
    var photosWifiOnly by rememberSaveable { mutableStateOf(true) }
    var videosWifiOnly by rememberSaveable { mutableStateOf(true) }
    var chargingOnly by rememberSaveable { mutableStateOf(false) }
    var albumSubfolders by rememberSaveable { mutableStateOf(true) }
    var schedule by rememberSaveable { mutableStateOf("daily") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var albumsInitialized by rememberSaveable { mutableStateOf(false) }
    var masterEnabled by rememberSaveable { mutableStateOf(false) }
    var confirmTargetChange by remember { mutableStateOf(false) }
    var selectedAlbums by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var rootPicker by remember { mutableStateOf(false) }
    var scheduleMenu by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var remoteHint by remember { mutableStateOf(false) }

    val permissions = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) viewModel.refreshBuckets(includeVideos)
    }
    val imagePermission = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
    val hasPermission = imagePermission &&
        (!includeVideos || Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
            PackageManager.PERMISSION_GRANTED)

    LaunchedEffect(config, initialized) {
        val existing = config
        if (existing != null && !initialized) {
            selectedRootId = existing.rootId
            remotePath = existing.remotePath
            includeVideos = existing.includeVideos
            photosWifiOnly = existing.photosWifiOnly
            videosWifiOnly = existing.videosWifiOnly
            chargingOnly = existing.chargingOnly
            albumSubfolders = existing.albumSubfolders
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

    LaunchedEffect(hasPermission, includeVideos) {
        if (hasPermission) viewModel.refreshBuckets(includeVideos)
    }

    LaunchedEffect(state.buckets, config, albumsInitialized) {
        if (!albumsInitialized && state.buckets.isNotEmpty()) {
            val saved = config?.albumsJson?.let { raw ->
                runCatching { Json.decodeFromString<List<Long>>(raw).toSet() }.getOrNull()
            }
            selectedAlbums = saved ?: state.buckets.map { it.id }.toSet()
            albumsInitialized = true
        }
    }

    val roots = session.roots
    val usableRoots = roots.filter { it.online != false && !it.readOnly }
    val root = roots.firstOrNull { it.id == selectedRootId }
        ?: usableRoots.firstOrNull()
        ?: roots.firstOrNull()
    val phase = rememberEffectivePhase(run)
    val running = phase.isActive
    val formEnabled = masterEnabled && !running
    val savedAlbums = remember(config?.albumsJson, state.buckets) {
        config?.albumsJson?.let { raw ->
            runCatching { Json.decodeFromString<List<Long>>(raw).toSet() }.getOrNull()
        } ?: state.buckets.map { it.id }.toSet()
    }
    val targetChanged = config?.let { cfg ->
        cfg.rootId != root?.id ||
            cfg.remotePath != remotePath.trim('/') ||
            cfg.albumSubfolders != albumSubfolders
    } == true
    val dirty = config?.let { cfg ->
        targetChanged ||
            cfg.includeVideos != includeVideos ||
            cfg.photosWifiOnly != photosWifiOnly ||
            cfg.videosWifiOnly != videosWifiOnly ||
            cfg.chargingOnly != chargingOnly ||
            cfg.schedule != schedule ||
            savedAlbums != selectedAlbums
    } ?: true

    LaunchedEffect(dirty) {
        if (dirty) viewModel.resetSaved()
    }

    fun persist(resetEntries: Boolean, allowOverlap: Boolean = false) {
        if (root == null) return
        viewModel.save(
            profile = active ?: return,
            root = root,
            remotePath = remotePath,
            albums = selectedAlbums,
            includeVideos = includeVideos,
            photosWifiOnly = photosWifiOnly,
            videosWifiOnly = videosWifiOnly,
            chargingOnly = chargingOnly,
            albumSubfolders = albumSubfolders,
            schedule = schedule,
            resetEntries = resetEntries,
            allowOverlap = allowOverlap,
        )
    }

    DetailScaffold(
        title = stringResource(R.string.photo_sync_screen),
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
                            persist(resetEntries = false)
                        }
                    },
                    enabled = masterEnabled && !running &&
                        root != null && hasPermission &&
                        selectedAlbums.isNotEmpty() && dirty,
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
                            kind = SyncRunKind.PHOTO,
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
                        )
                    },
                    settings = {
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
                                        text = stringResource(R.string.photo_sync_enable),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.photo_sync_enable_body),
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

                        if (!hasPermission) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.photo_sync_permission),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { permissionLauncher.launch(permissions) }) {
                                        Text(stringResource(R.string.photo_sync_permission_button))
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.alpha(if (masterEnabled) 1f else 0.45f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
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
                                    folderPicker.open(
                                        roots = roots,
                                        initialRootId = selectedRootId,
                                        initialPath = remotePath,
                                        mode = DirectoryPickerMode.Sync,
                                    )
                                },
                            )

                            SwitchRow(
                                stringResource(R.string.photo_sync_include_videos),
                                includeVideos,
                                formEnabled,
                            ) { includeVideos = it }
                            SwitchRow(
                                stringResource(R.string.photo_sync_photos_wifi),
                                photosWifiOnly,
                                formEnabled,
                            ) { photosWifiOnly = it }
                            SwitchRow(
                                stringResource(R.string.photo_sync_videos_wifi),
                                videosWifiOnly,
                                formEnabled && includeVideos,
                            ) { videosWifiOnly = it }
                            SwitchRow(
                                stringResource(R.string.photo_sync_charging_only),
                                chargingOnly,
                                formEnabled,
                            ) { chargingOnly = it }
                            SwitchRow(
                                stringResource(R.string.photo_sync_album_folders),
                                albumSubfolders,
                                formEnabled,
                            ) { albumSubfolders = it }

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
                                        Text(scheduleLabel(schedule))
                                    }
                                    DropdownMenu(
                                        expanded = scheduleMenu,
                                        onDismissRequest = { scheduleMenu = false },
                                    ) {
                                        listOf("manual", "15m", "hourly", "daily", "weekly")
                                            .forEach { value ->
                                                CompactDropdownMenuItem(
                                                    text = { Text(scheduleLabel(value)) },
                                                    onClick = {
                                                        schedule = value
                                                        scheduleMenu = false
                                                    },
                                                )
                                            }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.photo_sync_albums),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    enabled = formEnabled,
                                    onClick = {
                                        selectedAlbums =
                                            if (selectedAlbums.size == state.buckets.size) {
                                                emptySet()
                                            } else {
                                                state.buckets.map { it.id }.toSet()
                                            }
                                    },
                                ) {
                                    Text(stringResource(R.string.photo_sync_select_all))
                                }
                            }

                            if (state.loadingBuckets) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else if (state.buckets.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.photo_sync_no_albums),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    state.buckets.forEach { bucket ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = formEnabled) {
                                                    selectedAlbums =
                                                        if (bucket.id in selectedAlbums) {
                                                            selectedAlbums - bucket.id
                                                        } else {
                                                            selectedAlbums + bucket.id
                                                        }
                                                }
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(
                                                checked = bucket.id in selectedAlbums,
                                                enabled = formEnabled,
                                                onCheckedChange = {
                                                    selectedAlbums = if (it) {
                                                        selectedAlbums + bucket.id
                                                    } else {
                                                        selectedAlbums - bucket.id
                                                    }
                                                },
                                            )
                                            Text(
                                                text = bucket.name,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                text = bucket.count.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                selectedRootId = picked.id
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
            viewModel = folderPicker,
            onPick = { pickedRootId, selected ->
                selectedRootId = pickedRootId
                remotePath = selected
                folderPicker.close()
                focusManager.clearFocus()
                keyboard?.hide()
            },
        )
    }
    if (confirmTargetChange) {
        AlertDialog(
            onDismissRequest = { confirmTargetChange = false },
            title = { Text(stringResource(R.string.photo_sync_target_change_title)) },
            text = { Text(stringResource(R.string.photo_sync_target_change_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmTargetChange = false
                        persist(resetEntries = true)
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
    if (state.overlap) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOverlap,
            title = { Text(stringResource(R.string.sync_overlap_title)) },
            text = { Text(stringResource(R.string.sync_overlap_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissOverlap()
                        persist(resetEntries = false, allowOverlap = true)
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
private fun SwitchRow(
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
private fun scheduleLabel(value: String): String = when (value) {
    "manual" -> stringResource(R.string.photo_sync_schedule_manual)
    "15m" -> stringResource(R.string.photo_sync_schedule_15m)
    "hourly" -> stringResource(R.string.photo_sync_schedule_hourly)
    "weekly" -> stringResource(R.string.photo_sync_schedule_weekly)
    else -> stringResource(R.string.photo_sync_schedule_daily)
}
