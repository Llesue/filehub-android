package filehub.android.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.R
import filehub.android.data.SessionState
import filehub.android.data.sync.FolderSyncConfigEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun FolderSyncScreen(
    viewModel: FolderSyncViewModel,
    remoteFolderPicker: RemoteFolderPickerViewModel,
    session: SessionState,
    onBack: () -> Unit,
) {
    val folderPickerState by remoteFolderPicker.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val active = session.active

    var rootId by rememberSaveable { mutableStateOf<Long?>(null) }
    var remotePath by rememberSaveable { mutableStateOf("Sync") }
    var localTreeUri by rememberSaveable { mutableStateOf("") }
    var localName by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("upload") }
    var deleteSync by rememberSaveable { mutableStateOf(false) }
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var chargingOnly by rememberSaveable { mutableStateOf(false) }
    var schedule by rememberSaveable { mutableStateOf("daily") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var masterEnabled by rememberSaveable { mutableStateOf(false) }
    var rootMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var scheduleMenu by remember { mutableStateOf(false) }

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

    LaunchedEffect(config) {
        viewModel.refreshStats()
    }

    val roots = session.roots
    val root = roots.firstOrNull { it.id == rootId } ?: roots.firstOrNull()

    DetailScaffold(
        title = stringResource(R.string.folder_sync_screen),
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
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = { value ->
                            masterEnabled = value
                            if (config != null) viewModel.setEnabled(value)
                        },
                    )
                }
            }

            config?.let { existing ->
                FolderStatusCard(
                    config = existing,
                    pending = state.pending,
                    conflicts = state.conflicts,
                    onSyncNow = viewModel::syncNow,
                    syncEnabled = masterEnabled,
                )
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
                enabled = masterEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(localName.ifBlank { stringResource(R.string.folder_sync_choose) })
            }

            Text(
                text = stringResource(R.string.photo_sync_target_root),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                OutlinedButton(
                    onClick = { rootMenu = true },
                    enabled = masterEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(root?.alias ?: "—")
                }
                DropdownMenu(expanded = rootMenu, onDismissRequest = { rootMenu = false }) {
                    roots.forEach { item ->
                        CompactDropdownMenuItem(
                            text = { Text(item.alias) },
                            onClick = {
                                rootId = item.id
                                rootMenu = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = remotePath,
                onValueChange = { remotePath = it },
                enabled = masterEnabled,
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
                            if (root != null) remoteFolderPicker.open(root, remotePath)
                        },
                        enabled = masterEnabled && root != null,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_file_folder),
                            contentDescription = stringResource(R.string.folder_picker_title),
                        )
                    }
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
                    OutlinedButton(onClick = { modeMenu = true }, enabled = masterEnabled) {
                        Text(modeLabel(mode))
                    }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
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

            FolderSwitch(stringResource(R.string.folder_sync_delete), deleteSync, masterEnabled) { deleteSync = it }
            FolderSwitch(stringResource(R.string.photo_sync_wifi_only), wifiOnly, masterEnabled) { wifiOnly = it }
            FolderSwitch(stringResource(R.string.photo_sync_charging_only), chargingOnly, masterEnabled) { chargingOnly = it }

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
                    OutlinedButton(onClick = { scheduleMenu = true }, enabled = masterEnabled) {
                        Text(folderScheduleLabel(schedule))
                    }
                    DropdownMenu(expanded = scheduleMenu, onDismissRequest = { scheduleMenu = false }) {
                        listOf("manual", "15m", "hourly", "daily", "weekly").forEach { value ->
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
            if (state.conflicts > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.folder_sync_conflicts) + ": ${state.conflicts}",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
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
                    if (root != null) {
                        viewModel.save(
                            profile = active,
                            root = root,
                            remotePath = remotePath,
                            localTreeUri = localTreeUri,
                            localName = localName,
                            mode = mode,
                            deleteSync = deleteSync,
                            wifiOnly = wifiOnly,
                            chargingOnly = chargingOnly,
                            schedule = schedule,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = masterEnabled && !state.saving && root != null && localTreeUri.isNotBlank(),
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
            onNavigate = remoteFolderPicker::navigate,
            onUp = remoteFolderPicker::up,
            onLoadMore = remoteFolderPicker::loadMore,
            onDismiss = remoteFolderPicker::close,
            onConfirm = { selected ->
                remotePath = selected
                remoteFolderPicker.close()
                focusManager.clearFocus()
                keyboard?.hide()
            },
        )
    }
}

@Composable
private fun FolderStatusCard(
    config: FolderSyncConfigEntity,
    pending: Int,
    conflicts: Int,
    onSyncNow: () -> Unit,
    syncEnabled: Boolean,
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
                        stringResource(R.string.folder_sync_enabled)
                    } else {
                        stringResource(R.string.folder_sync_off)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.folder_sync_last_run) + ": " +
                    (config.lastRunAt?.let { formatFolderTime(it) } ?: "—"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            config.lastResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            config.lastError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.folder_sync_pending) + ": $pending · " +
                    stringResource(R.string.folder_sync_conflicts) + ": $conflicts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSyncNow, enabled = syncEnabled && config.enabled) {
                Text(stringResource(R.string.folder_sync_now))
            }
        }
    }
}

@Composable
private fun FolderSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
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

private fun formatFolderTime(epochSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochSeconds * 1000))
