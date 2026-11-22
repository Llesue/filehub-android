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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.R
import filehub.android.data.SessionState
import filehub.android.data.sync.PhotoSyncConfigEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun PhotoSyncScreen(
    viewModel: PhotoSyncViewModel,
    session: SessionState,
    onBack: () -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val active = session.active

    var selectedRootId by rememberSaveable { mutableStateOf<Long?>(null) }
    var remotePath by rememberSaveable { mutableStateOf("Photos/Phone") }
    var includeVideos by rememberSaveable { mutableStateOf(true) }
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var chargingOnly by rememberSaveable { mutableStateOf(false) }
    var albumSubfolders by rememberSaveable { mutableStateOf(true) }
    var schedule by rememberSaveable { mutableStateOf("daily") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var albumsInitialized by rememberSaveable { mutableStateOf(false) }
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
            wifiOnly = existing.wifiOnly
            chargingOnly = existing.chargingOnly
            albumSubfolders = existing.albumSubfolders
            schedule = existing.schedule
            initialized = true
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
            Text(
                text = stringResource(R.string.photo_sync_screen),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (active == null) {
                Text(stringResource(R.string.login_error_generic))
                return@Column
            }

            config?.let { existing ->
                SyncStatusCard(
                    config = existing,
                    onSyncNow = viewModel::syncNow,
                    onToggle = { viewModel.setEnabled(!existing.enabled) },
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
                        DropdownMenuItem(
                            text = { Text(item.alias) },
                            onClick = {
                                selectedRootId = item.id
                                rootMenu = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = remotePath,
                onValueChange = { remotePath = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.photo_sync_target_path)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            SwitchRow(stringResource(R.string.photo_sync_include_videos), includeVideos) {
                includeVideos = it
            }
            SwitchRow(stringResource(R.string.photo_sync_wifi_only), wifiOnly) { wifiOnly = it }
            SwitchRow(stringResource(R.string.photo_sync_charging_only), chargingOnly) { chargingOnly = it }
            SwitchRow(stringResource(R.string.photo_sync_album_folders), albumSubfolders) { albumSubfolders = it }

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
                            DropdownMenuItem(
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
                                .clickable {
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
                            albums = selectedAlbums,
                            includeVideos = includeVideos,
                            wifiOnly = wifiOnly,
                            chargingOnly = chargingOnly,
                            albumSubfolders = albumSubfolders,
                            schedule = schedule,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !state.saving && root != null && hasPermission && selectedAlbums.isNotEmpty(),
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

@Composable
private fun SyncStatusCard(
    config: PhotoSyncConfigEntity,
    onSyncNow: () -> Unit,
    onToggle: () -> Unit,
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
                TextButton(onClick = onToggle) {
                    Text(
                        stringResource(
                            if (config.enabled) R.string.photo_sync_disable else R.string.photo_sync_save,
                        ),
                    )
                }
            }
            Text(
                text = stringResource(R.string.photo_sync_last_run) + ": " +
                    (config.lastRunAt?.let { formatTime(it) } ?: "—"),
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
            TextButton(onClick = onSyncNow, enabled = config.enabled) {
                Text(stringResource(R.string.photo_sync_now))
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun scheduleLabel(value: String): String = when (value) {
    "manual" -> stringResource(R.string.photo_sync_schedule_manual)
    "15m" -> stringResource(R.string.photo_sync_schedule_15m)
    "hourly" -> stringResource(R.string.photo_sync_schedule_hourly)
    "weekly" -> stringResource(R.string.photo_sync_schedule_weekly)
    else -> stringResource(R.string.photo_sync_schedule_daily)
}

private fun formatTime(epochSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochSeconds * 1000))
