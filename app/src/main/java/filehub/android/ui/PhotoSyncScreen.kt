package filehub.android.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.R
import filehub.android.data.SessionState
import filehub.android.data.sync.PhotoSyncConfigEntity

@Composable
fun PhotoSyncScreen(
    viewModel: PhotoSyncViewModel,
    folderPicker: RemoteFolderPickerViewModel,
    session: SessionState,
    onBack: () -> Unit,
) {
    val folderPickerState by folderPicker.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val active = session.active

    var selectedRootId by rememberSaveable { mutableStateOf<Long?>(null) }
    var remotePath by rememberSaveable { mutableStateOf("Phone/Photos") }
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
    var rootMenu by remember { mutableStateOf(false) }
    var scheduleMenu by remember { mutableStateOf(false) }

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
                runCatching {
                    kotlinx.serialization.json.Json.decodeFromString<List<Long>>(raw).toSet()
                }.getOrNull()
            }
            selectedAlbums = saved ?: state.buckets.map { it.id }.toSet()
            albumsInitialized = true
        }
    }

    val roots = session.roots
    val root = roots.firstOrNull { it.id == selectedRootId } ?: roots.firstOrNull()

    fun persist(resetEntries: Boolean) {
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
        )
    }

    DetailScaffold(
        title = stringResource(R.string.photo_sync_screen),
        onBack = onBack,
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (active == null) {
                Text(stringResource(R.string.login_error_generic))
                return@Column
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
                    Column(modifier = Modifier.weight(1f)) {
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

            config?.let { existing ->
                SyncStatusCard(
                    config = existing,
                    onSyncNow = viewModel::syncNow,
                    syncEnabled = masterEnabled,
                    queued = state.queued,
                )
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Text(
                text = stringResource(R.string.photo_sync_target_root),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                OutlinedButton(
                    onClick = { rootMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(root?.alias ?: "—")
                }
                DropdownMenu(expanded = rootMenu, onDismissRequest = { rootMenu = false }) {
                    roots.forEach { item ->
                        CompactDropdownMenuItem(
                            text = { Text(item.alias) },
                            onClick = {
                                selectedRootId = item.id
                                rootMenu = false
                            },
                        )
                    }
                }
            }

            if (root != null && config?.rootId?.let { it != root.id } == true) {
                Text(
                    text = stringResource(R.string.sync_root_changed_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = remotePath,
                onValueChange = { remotePath = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.photo_sync_target_path)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = clearFocusOnDone(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            if (root != null) folderPicker.open(root, remotePath)
                        },
                        enabled = root != null,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_file_folder),
                            contentDescription = stringResource(R.string.folder_picker_title),
                        )
                    }
                },
            )

            SwitchRow(stringResource(R.string.photo_sync_include_videos), includeVideos, true) {
                includeVideos = it
            }
            SwitchRow(stringResource(R.string.photo_sync_photos_wifi), photosWifiOnly, true) {
                photosWifiOnly = it
            }
            SwitchRow(
                stringResource(R.string.photo_sync_videos_wifi),
                videosWifiOnly,
                includeVideos,
            ) { videosWifiOnly = it }
            SwitchRow(stringResource(R.string.photo_sync_charging_only), chargingOnly, true) { chargingOnly = it }
            SwitchRow(stringResource(R.string.photo_sync_album_folders), albumSubfolders, true) { albumSubfolders = it }

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
                    OutlinedButton(onClick = { scheduleMenu = true }) {
                        Text(scheduleLabel(schedule))
                    }
                    DropdownMenu(expanded = scheduleMenu, onDismissRequest = { scheduleMenu = false }) {
                        listOf("manual", "15m", "hourly", "daily", "weekly").forEach { value ->
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
                    enabled = masterEnabled,
                    onClick = {
                        selectedAlbums = if (selectedAlbums.size == state.buckets.size) {
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.buckets.forEach { bucket ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = masterEnabled) {
                                    selectedAlbums = if (bucket.id in selectedAlbums) {
                                        selectedAlbums - bucket.id
                                    } else {
                                        selectedAlbums + bucket.id
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = bucket.id in selectedAlbums,
                                enabled = masterEnabled,
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
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    if (root == null) return@Button
                    val path = remotePath.trim('/')
                    val targetChanged = config?.let {
                        it.rootId != root.id || it.remotePath != path
                    } == true
                    if (targetChanged) {
                        confirmTargetChange = true
                    } else {
                        persist(resetEntries = false)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = masterEnabled && !state.saving && root != null && hasPermission && selectedAlbums.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.photo_sync_save))
                }
            }
            if (state.saved) {
                Text(
                    text = stringResource(R.string.photo_sync_saved),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }

    if (folderPickerState.visible) {
        RemoteFolderPickerDialog(
            state = folderPickerState,
            onNavigate = folderPicker::navigate,
            onUp = folderPicker::up,
            onLoadMore = folderPicker::loadMore,
            onCreateFolder = folderPicker::createFolder,
            onDismiss = folderPicker::close,
            onConfirm = { selected ->
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
}

@Composable
private fun SyncStatusCard(
    config: PhotoSyncConfigEntity,
    onSyncNow: () -> Unit,
    syncEnabled: Boolean,
    queued: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (config.enabled) {
                        stringResource(R.string.photo_sync_enabled)
                    } else {
                        stringResource(R.string.photo_sync_disable)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.photo_sync_last_run) + ": " +
                    (config.lastRunAt?.let { relativeTimeText(it) } ?: "—"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            config.lastResult?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            config.lastError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onSyncNow, enabled = syncEnabled && config.enabled) {
                Text(stringResource(R.string.photo_sync_now))
            }
            if (queued) {
                Text(
                    text = stringResource(R.string.sync_queued_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
