package filehub.android.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import filehub.android.R
import filehub.android.core.model.FileKind
import filehub.android.core.model.FileRoot
import filehub.android.data.SessionState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BrowseScreen(
    state: SessionState,
    filesViewModel: FilesViewModel,
    onAddServer: () -> Unit,
    onRetry: () -> Unit,
    onSearch: (Long?) -> Unit,
    onPauseAudio: () -> Unit,
    onResumeAudio: () -> Unit,
    directoryPicker: DirectoryPickerViewModel,
    shell: ShellMode = ShellMode.Compact,
    onOpenDrawer: (() -> Unit)? = null,
    onOpenMy: (() -> Unit)? = null,
    onOpenServerPicker: (() -> Unit)? = null,
    onShares: () -> Unit = {},
    usage: UsageUiState = UsageUiState(),
    serverVersion: filehub.android.data.api.VersionDto? = null,
    clientUpdate: ClientUpdateUiState? = null,
    theme: String = "system",
    onToggleTheme: () -> Unit = {},
    imageLoader: coil3.ImageLoader? = null,
) {
    val active = state.active
    val files by filesViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(active?.id) {
        val currentProfile = filesViewModel.state.value.profile
        if (currentProfile != null && (active == null || currentProfile.id != active.id)) {
            filesViewModel.release()
        }
    }

    LaunchedEffect(active?.id, active?.baseUrl) {
        val currentProfile = filesViewModel.state.value.profile
        if (active != null && currentProfile?.id == active.id) {
            filesViewModel.updateProfile(active)
            if (filesViewModel.state.value.error != null) {
                filesViewModel.retry()
            }
        }
    }

    if (shell == ShellMode.Wide) {
        TabletBrowseScreen(
            session = state,
            filesViewModel = filesViewModel,
            usage = usage,
            serverVersion = serverVersion,
            clientUpdate = clientUpdate,
            theme = theme,
            onToggleTheme = onToggleTheme,
            imageLoader = imageLoader,
            onSearch = onSearch,
            onPauseAudio = onPauseAudio,
            onResumeAudio = onResumeAudio,
            directoryPicker = directoryPicker,
            onOpenMy = { onOpenMy?.invoke() },
            onOpenServerPicker = { onOpenServerPicker?.invoke() },
            onShares = onShares,
        )
        return
    }

    if (shell == ShellMode.Landscape) {
        ColumnBrowserScreen(
            session = state,
            filesViewModel = filesViewModel,
            shell = shell,
            onRetryRoots = onRetry,
            onSearch = onSearch,
            onPauseAudio = onPauseAudio,
            onResumeAudio = onResumeAudio,
            directoryPicker = directoryPicker,
            onOpenMy = onOpenMy,
            onOpenServerPicker = onOpenServerPicker,
            onShares = onShares,
            usage = usage,
            serverVersion = serverVersion,
            clientUpdate = clientUpdate,
            imageLoader = imageLoader,
        )
        return
    }

    if (active == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.browse_no_server_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.browse_no_server_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAddServer) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.servers_add))
                }
            }
            onOpenDrawer?.let { open ->
                IconButton(
                    onClick = open,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.nav_menu),
                    )
                }
            }
        }
        return
    }

    val root = files.root
    if (root != null) {
        FileBrowserScreen(
            viewModel = filesViewModel,
            roots = state.roots,
            onSearch = onSearch,
            onPauseAudio = onPauseAudio,
            onResumeAudio = onResumeAudio,
            directoryPicker = directoryPicker,
            shell = shell,
            onOpenMy = onOpenMy,
            onBack = {
                if (files.path.isEmpty()) {
                    filesViewModel.clear()
                } else {
                    filesViewModel.goUp()
                }
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier
            .widthIn(max = 1320.dp)
            .fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onOpenDrawer?.let { open ->
                IconButton(onClick = open) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.nav_menu),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.browse_locations),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = active.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onSearch(null) }) {
                Icon(Icons.Filled.Search, contentDescription = null)
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh))
            }
            if (shell == ShellMode.Wide) {
                onOpenMy?.let { open ->
                    IconButton(onClick = open) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = stringResource(R.string.tab_my),
                        )
                    }
                }
            }
        }

        if (state.rootsLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        when {
            state.needsLogin -> CenteredMessage(
                title = stringResource(R.string.servers_needs_login),
                action = {
                    Button(onClick = onAddServer) {
                        Text(stringResource(R.string.login_connect))
                    }
                },
            )
            state.error != null -> CenteredMessage(
                title = state.error.orEmpty(),
                action = {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.browse_retry))
                    }
                },
            )
            state.rootsLoading && state.roots.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.roots.isEmpty() -> CenteredMessage(
                title = stringResource(R.string.browse_no_roots),
                action = {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.common_refresh))
                    }
                },
            )
            else -> BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val wide = maxWidth >= 600.dp
                val columns = rootGridColumns(maxWidth.value)
                RootGrid(
                    roots = state.roots,
                    wide = wide,
                    columns = columns,
                    onOpen = { item -> filesViewModel.open(profile = active, root = item) },
                )
            }
        }
    }
    }
}

@Composable
internal fun RootGrid(
    roots: List<FileRoot>,
    wide: Boolean,
    columns: Int,
    onOpen: (FileRoot) -> Unit,
) {
    val context = LocalContext.current
    val offlineHint = stringResource(R.string.browse_root_offline_toast)
    var offlineExpanded by remember { mutableStateOf(false) }
    val groups = rootGroups(roots)
    val online = groups.online
    val offline = groups.offline
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(online, key = { "root-${it.id}" }) { item ->
            RootCard(
                root = item,
                borderless = wide,
                onClick = { onOpen(item) },
            )
        }
        if (offline.isNotEmpty()) {
            item(key = "offline-header", span = { GridItemSpan(maxLineSpan) }) {
                OfflineGroupHeader(
                    count = offline.size,
                    expanded = offlineExpanded,
                    onToggle = { offlineExpanded = !offlineExpanded },
                )
            }
            if (offlineExpanded) {
                items(offline, key = { "offline-${it.id}" }) { item ->
                    RootCard(
                        root = item,
                        borderless = wide,
                        dimmed = true,
                        onClick = { Toast.makeText(context, offlineHint, Toast.LENGTH_SHORT).show() },
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineGroupHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.browse_offline_group, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RootCard(
    root: FileRoot,
    onClick: () -> Unit,
    borderless: Boolean = false,
    dimmed: Boolean = false,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.45f else 1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (borderless) Color.Transparent else MaterialTheme.colorScheme.surface,
        border = if (borderless) null else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_file_folder),
                    contentDescription = null,
                    tint = fileIconColor(FileKind.Folder),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = root.alias,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (root.readOnly || root.isRemote) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (root.readOnly) Badge(stringResource(R.string.browse_root_read_only))
                        if (root.isRemote) Badge(stringResource(R.string.browse_root_remote))
                        if (root.isRemote) {
                            when (root.online) {
                                true -> Badge(stringResource(R.string.browse_root_online))
                                false -> StatusBadge(
                                    text = stringResource(R.string.browse_root_offline),
                                    color = MaterialTheme.colorScheme.error,
                                )
                                null -> Badge(stringResource(R.string.browse_root_unknown))
                            }
                        }
                    }
                }
            }
            Text(text = "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Badge(text: String) {
    StatusBadge(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    action: (@Composable () -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
