package filehub.android.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import filehub.android.R
import filehub.android.core.model.BrowserViewMode
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileKind
import filehub.android.core.model.FileRoot
import filehub.android.core.model.FileSort
import filehub.android.core.model.ServerProfile
import filehub.android.core.model.SortOrder
import filehub.android.data.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface ColumnLevel {
    data class Roots(val serverId: String) : ColumnLevel
    data class Entries(val root: FileRoot, val path: String) : ColumnLevel
}

data class ColumnSnapshot(
    val level: ColumnLevel,
    val serverId: String? = null,
    val title: String = "",
    val entries: List<FileEntry> = emptyList(),
    val nextCursor: String? = null,
    val sort: FileSort = FileSort.Name,
    val order: SortOrder = SortOrder.Asc,
    val view: BrowserViewMode = BrowserViewMode.List,
)

class ColumnNavViewModel : ViewModel() {
    private val _columns = MutableStateFlow<List<ColumnSnapshot>>(emptyList())
    val columns: StateFlow<List<ColumnSnapshot>> = _columns.asStateFlow()

    fun reset() {
        _columns.value = emptyList()
    }

    fun set(columns: List<ColumnSnapshot>) {
        _columns.value = columns
    }

    fun push(column: ColumnSnapshot) {
        _columns.update { it + column }
    }

    fun replaceLast(column: ColumnSnapshot) {
        _columns.update { current ->
            if (current.isEmpty()) {
                listOf(column)
            } else {
                current.dropLast(1) + column
            }
        }
    }

    fun truncate(size: Int) {
        _columns.update { it.take(size.coerceIn(1, it.size)) }
    }
}

internal fun columnFolderPath(parentPath: String, name: String): String =
    if (parentPath.isEmpty()) name else "$parentPath/$name"

internal fun columnFolderTitle(root: FileRoot, path: String): String =
    path.substringAfterLast('/').ifEmpty { root.alias }

internal fun columnPathChain(path: String): List<String> {
    if (path.isEmpty()) return listOf("")
    val result = mutableListOf("")
    var current = ""
    path.split('/').filter { it.isNotEmpty() }.forEach { segment ->
        current = if (current.isEmpty()) segment else "$current/$segment"
        result += current
    }
    return result
}

@Composable
fun ColumnBrowserScreen(
    session: SessionState,
    filesViewModel: FilesViewModel,
    shell: ShellMode,
    onRetryRoots: () -> Unit,
    onSearch: (Long?) -> Unit,
    onPauseAudio: () -> Unit,
    onResumeAudio: () -> Unit,
    directoryPicker: DirectoryPickerViewModel,
    onOpenMy: (() -> Unit)?,
    onOpenServerPicker: (() -> Unit)?,
    onShares: () -> Unit,
    usage: UsageUiState,
    serverVersion: filehub.android.data.api.VersionDto?,
    clientUpdate: ClientUpdateUiState?,
    imageLoader: ImageLoader?,
) {
    val files by filesViewModel.state.collectAsStateWithLifecycle()
    val columnNav: ColumnNavViewModel = viewModel()
    val columns by columnNav.columns.collectAsStateWithLifecycle()
    val right = if (columns.size >= 2) columns.last() else null
    val left = if (columns.size >= 2) columns[columns.size - 2] else columns.firstOrNull()

    LaunchedEffect(session.active?.id) {
        val activeId = session.active?.id
        if (activeId == null) {
            columnNav.reset()
            filesViewModel.clear()
            return@LaunchedEffect
        }
        val current = columnNav.columns.value
        val stale = current.isEmpty() || current.any { column ->
            column.serverId != null && column.serverId != activeId
        }
        if (stale) {
            columnNav.set(
                listOf(
                    ColumnSnapshot(
                        level = ColumnLevel.Roots(activeId),
                        serverId = activeId,
                    ),
                ),
            )
            filesViewModel.clear()
        }
    }

    LaunchedEffect(session.roots, session.rootsLoading) {
        if (session.rootsLoading) return@LaunchedEffect
        val activeId = session.active?.id ?: return@LaunchedEffect
        val current = columnNav.columns.value
        val entryRoots = current.mapNotNull { it.level as? ColumnLevel.Entries }
        if (entryRoots.isEmpty()) return@LaunchedEffect
        val existing = session.roots.mapTo(mutableSetOf()) { it.id }
        if (entryRoots.all { it.root.id in existing }) return@LaunchedEffect
        val rootsColumn = current.firstOrNull { it.level is ColumnLevel.Roots }
            ?: ColumnSnapshot(
                level = ColumnLevel.Roots(activeId),
                serverId = activeId,
            )
        columnNav.set(listOf(rootsColumn))
        filesViewModel.clear()
    }

    LaunchedEffect(files.sort, files.order, files.view) {
        val current = columnNav.columns.value.lastOrNull() ?: return@LaunchedEffect
        if (current.level !is ColumnLevel.Entries) return@LaunchedEffect
        val updated = current.copy(
            sort = files.sort,
            order = files.order,
            view = files.view,
        )
        if (updated != current) columnNav.replaceLast(updated)
    }

    fun openRoot(root: FileRoot) {
        val profile = session.active ?: return
        val rootsColumn = columnNav.columns.value.firstOrNull {
            it.level is ColumnLevel.Roots
        } ?: ColumnSnapshot(
            level = ColumnLevel.Roots(profile.id),
            serverId = profile.id,
        )
        val column = ColumnSnapshot(
            level = ColumnLevel.Entries(root, ""),
            serverId = profile.id,
            title = root.alias,
            sort = files.sort,
            order = files.order,
            view = files.view,
        )
        columnNav.set(listOf(rootsColumn, column))
        filesViewModel.open(profile, root, "")
    }

    fun openFolder(entry: FileEntry) {
        val profile = session.active ?: return
        val current = filesViewModel.state.value
        val root = current.root ?: return
        val base = columnNav.columns.value
        val parent = base.lastOrNull() ?: return
        val parentWithSnapshot = if (parent.level is ColumnLevel.Entries) {
            parent.copy(
                entries = current.entries,
                nextCursor = current.nextCursor,
                sort = current.sort,
                order = current.order,
                view = current.view,
            )
        } else {
            parent
        }
        val path = columnFolderPath(current.path, entry.name)
        val child = ColumnSnapshot(
            level = ColumnLevel.Entries(root, path),
            serverId = profile.id,
            title = entry.name,
            sort = current.sort,
            order = current.order,
            view = current.view,
        )
        columnNav.set(base.dropLast(1) + parentWithSnapshot + child)
        filesViewModel.open(profile, root, path)
    }

    fun openFolderFromLeft(entry: FileEntry) {
        val profile = session.active ?: return
        val base = columnNav.columns.value
        if (base.size < 2) return
        val parent = base[base.size - 2]
        val level = parent.level as? ColumnLevel.Entries ?: return
        if (!entry.isDir) return
        val path = columnFolderPath(level.path, entry.name)
        val child = ColumnSnapshot(
            level = ColumnLevel.Entries(level.root, path),
            serverId = profile.id,
            title = entry.name,
            sort = parent.sort,
            order = parent.order,
            view = parent.view,
        )
        columnNav.set(base.dropLast(1) + child)
        filesViewModel.open(profile, level.root, path)
    }

    fun goBack() {
        val current = columnNav.columns.value
        if (current.size <= 1) return
        val next = current.dropLast(1)
        columnNav.set(next)
        val target = next.last()
        when (val level = target.level) {
            is ColumnLevel.Entries -> {
                val profile = session.active ?: return
                if (target.entries.isNotEmpty()) {
                    filesViewModel.restoreColumn(
                        profile = profile,
                        root = level.root,
                        path = level.path,
                        entries = target.entries,
                        nextCursor = target.nextCursor,
                        sort = target.sort,
                        order = target.order,
                        view = target.view,
                    )
                } else {
                    filesViewModel.open(profile, level.root, level.path)
                }
            }
            is ColumnLevel.Roots -> filesViewModel.clear()
        }
    }

    val textEntry = files.previewEntry?.takeIf { entry ->
        !entry.isDir && (
            entry.kind == FileKind.Text ||
                entry.kind == FileKind.Code ||
                entry.isMarkdown ||
                files.previewAsText
            )
    }
    val textProfile = files.profile
    val textRoot = files.root
    if (textEntry != null && textProfile != null && textRoot != null) {
        val path = columnFolderPath(files.path, textEntry.name)
        val text = files.textPreview
        if (text != null) {
            DocumentHost(
                profile = textProfile,
                root = textRoot,
                path = path,
                entry = textEntry,
                initialText = text,
                session = files.previewSession,
                onClose = filesViewModel::closePreview,
                onSaved = filesViewModel::refresh,
                allowEditing = hasHardwareKeyboard(),
            )
        } else {
            DocumentLoading(
                name = textEntry.name,
                loading = files.previewLoading,
                error = files.previewError,
                onClose = filesViewModel::closePreview,
                onRetry = { filesViewModel.reloadPreviewText(files.previewEncoding) },
            )
        }
        return
    }

    BackHandler(
        enabled = textEntry == null &&
            columns.size > 1 &&
            right?.level !is ColumnLevel.Entries,
    ) {
        goBack()
    }

    val selectionLocked = files.selectionMode
    val leftListState = rememberLazyListState()
    var offlineExpanded by remember { mutableStateOf(false) }
    val offlineHint = stringResource(R.string.browse_root_offline_toast)
    val context = LocalContext.current
    val onlineRoots = session.roots.filter { it.online != false }
    val offlineRoots = session.roots.filter { it.online == false }

    LaunchedEffect(left?.level, left?.title, left?.entries?.size) {
        leftListState.scrollToItem(0)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                state = leftListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
            ) {
                item(key = "landscape-server") {
                    LandscapeServerHeader(
                        profile = session.active,
                        imageLoader = imageLoader,
                        onOpenServers = { onOpenServerPicker?.invoke() },
                        onOpenMy = onOpenMy,
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    )
                }
                when (val level = left?.level) {
                    is ColumnLevel.Roots -> {
                        items(onlineRoots, key = { "root-${it.id}" }) { root ->
                            RootColumnRow(
                                root = root,
                                selected = root.id ==
                                    (right?.level as? ColumnLevel.Entries)?.root?.id,
                                enabled = !selectionLocked,
                                dimmed = false,
                                onClick = { openRoot(root) },
                            )
                        }
                        if (offlineRoots.isNotEmpty()) {
                            item(key = "offline-header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { offlineExpanded = !offlineExpanded }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (offlineExpanded) {
                                            Icons.Filled.KeyboardArrowUp
                                        } else {
                                            Icons.Filled.KeyboardArrowDown
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.browse_offline_group,
                                            offlineRoots.size,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (offlineExpanded) {
                                items(offlineRoots, key = { "offline-${it.id}" }) { root ->
                                    RootColumnRow(
                                        root = root,
                                        selected = false,
                                        enabled = !selectionLocked,
                                        dimmed = true,
                                        onClick = {
                                            Toast.makeText(
                                                context,
                                                offlineHint,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    )
                                }
                            }
                        }
                        if (onlineRoots.isEmpty() && offlineRoots.isEmpty()) {
                            item(key = "empty-roots") {
                                ColumnHint(stringResource(R.string.column_no_roots))
                            }
                        }
                    }
                    is ColumnLevel.Entries -> {
                        val folders = left.entries.filter { it.isDir }
                        if (folders.isEmpty()) {
                            item(key = "empty-folders") {
                                ColumnHint(stringResource(R.string.column_no_folders))
                            }
                        } else {
                            items(folders, key = { "folder-${it.name}" }) { folder ->
                                ColumnRow(
                                    selected = folder.name == selectedFolderName(left, right),
                                    enabled = !selectionLocked,
                                    onClick = { openFolderFromLeft(folder) },
                                    leading = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_file_folder),
                                            contentDescription = null,
                                            tint = fileIconColor(FileKind.Folder),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    title = folder.name,
                                    subtitle = null,
                                )
                            }
                        }
                    }
                    null -> item(key = "no-level") {
                        ColumnHint(stringResource(R.string.column_no_roots))
                    }
                }
                item(key = "landscape-share") {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onShares)
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.sidebar_shares),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    )
                }
                item(key = "landscape-footer") {
                    SidebarFooter(
                        usage = usage,
                        serverVersion = serverVersion,
                        clientUpdate = clientUpdate,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (val level = right?.level) {
                null -> Unit
                is ColumnLevel.Roots -> Unit
                is ColumnLevel.Entries -> FileBrowserScreen(
                    viewModel = filesViewModel,
                    roots = session.roots,
                    onSearch = onSearch,
                    onPauseAudio = onPauseAudio,
                    onResumeAudio = onResumeAudio,
                    directoryPicker = directoryPicker,
                    shell = shell,
                    onOpenMy = onOpenMy,
                    columnMode = true,
                    onColumnBack = ::goBack,
                    onEnterDirectory = ::openFolder,
                    onBack = ::goBack,
                )
            }
        }
    }
}

@Composable
private fun LandscapeServerHeader(
    profile: ServerProfile?,
    imageLoader: ImageLoader?,
    onOpenServers: () -> Unit,
    onOpenMy: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clickable(onClick = onOpenServers)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (profile?.iconUrl != null && imageLoader != null) {
                    AsyncImage(
                        model = profile.iconUrl,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_folder),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = profile?.displayName ?: stringResource(R.string.servers_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.server_picker_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        onOpenMy?.let { openMy ->
            Spacer(Modifier.width(10.dp))
            val avatarUrl = profile?.let { active ->
                active.avatarVersion?.takeIf { it > 0 }?.let { version ->
                    "${active.baseUrl.trimEnd('/')}/api/v1/me/avatar?v=$version"
                }
            }
            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = openMy),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = profile?.username?.take(1)?.uppercase().orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    if (avatarUrl != null && imageLoader != null) {
                        AsyncImage(
                            model = avatarUrl,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackHeaderScroll(
    state: LazyListState,
    onDirection: ((Int) -> Unit)?,
) {
    val callback = onDirection ?: return
    LaunchedEffect(state, callback) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow {
            state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            when {
                index == 0 && offset == 0 -> callback(-1)
                index > lastIndex || (index == lastIndex && offset > lastOffset) -> callback(1)
                index < lastIndex || (index == lastIndex && offset < lastOffset) -> callback(-1)
            }
            lastIndex = index
            lastOffset = offset
        }
    }
}

@Composable
private fun ColumnHeader(
    title: String,
    onOpenDrawer: (() -> Unit)?,
    action: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 4.dp, end = 6.dp),
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onOpenDrawer == null) 12.dp else 0.dp),
        )
        action?.invoke()
    }
}

@Composable
internal fun RootColumn(
    roots: List<FileRoot>,
    selectedRootId: Long?,
    enabled: Boolean,
    onSelect: (FileRoot) -> Unit,
    onScrollDirection: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val online = roots.filter { it.online != false }
    val offline = roots.filter { it.online == false }
    val offlineHint = stringResource(R.string.browse_root_offline_toast)
    var offlineExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    TrackHeaderScroll(listState, onScrollDirection)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 6.dp,
            bottom = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(online, key = { it.id }) { root ->
            RootColumnRow(
                root = root,
                selected = root.id == selectedRootId,
                enabled = enabled,
                dimmed = false,
                onClick = { onSelect(root) },
            )
        }
        if (offline.isNotEmpty()) {
            item(key = "offline-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { offlineExpanded = !offlineExpanded }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (offlineExpanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.browse_offline_group, offline.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (offlineExpanded) {
                items(offline, key = { it.id }) { root ->
                    RootColumnRow(
                        root = root,
                        selected = false,
                        enabled = enabled,
                        dimmed = true,
                        onClick = {
                            Toast.makeText(context, offlineHint, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
        if (online.isEmpty() && offline.isEmpty()) {
            item(key = "empty") {
                ColumnHint(stringResource(R.string.column_no_roots))
            }
        }
    }
}

@Composable
private fun RootColumnRow(
    root: FileRoot,
    selected: Boolean,
    enabled: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    ColumnRow(
        selected = selected,
        enabled = enabled,
        dimmed = dimmed,
        onClick = onClick,
        leading = {
            Icon(
                painter = painterResource(R.drawable.ic_file_folder),
                contentDescription = null,
                tint = fileIconColor(FileKind.Folder),
                modifier = Modifier.size(20.dp),
            )
        },
        title = root.alias,
        subtitle = when {
            root.isRemote && root.online == false -> stringResource(R.string.browse_root_offline)
            root.isRemote -> stringResource(R.string.browse_root_remote)
            root.readOnly -> stringResource(R.string.browse_root_read_only)
            else -> null
        },
    )
}

@Composable
private fun FolderColumn(
    folders: List<FileEntry>,
    selectedName: String?,
    enabled: Boolean,
    onSelect: (FileEntry) -> Unit,
    onScrollDirection: ((Int) -> Unit)? = null,
) {
    if (folders.isEmpty()) {
        ColumnHint(stringResource(R.string.column_no_folders))
        return
    }
    val listState = rememberLazyListState()
    TrackHeaderScroll(listState, onScrollDirection)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 6.dp,
            bottom = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(folders, key = { it.name }) { folder ->
            ColumnRow(
                selected = folder.name == selectedName,
                enabled = enabled,
                onClick = { onSelect(folder) },
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_folder),
                        contentDescription = null,
                        tint = fileIconColor(FileKind.Folder),
                        modifier = Modifier.size(20.dp),
                    )
                },
                title = folder.name,
                subtitle = null,
            )
        }
    }
}

@Composable
private fun ColumnRow(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.5f else 1f)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            leading()
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun ColumnHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyRightPane(hasServer: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_file_folder),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (hasServer) R.string.column_empty_title else R.string.browse_no_server_title,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (hasServer) R.string.column_empty_body else R.string.browse_no_server_body,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun selectedFolderName(
    left: ColumnSnapshot?,
    right: ColumnSnapshot?,
): String? {
    val leftLevel = left?.level as? ColumnLevel.Entries ?: return null
    val rightLevel = right?.level as? ColumnLevel.Entries ?: return null
    if (rightLevel.root.id != leftLevel.root.id) return null
    val prefix = if (leftLevel.path.isEmpty()) "" else "${leftLevel.path}/"
    if (!rightLevel.path.startsWith(prefix) || rightLevel.path == leftLevel.path) return null
    return rightLevel.path.removePrefix(prefix).substringBefore('/')
}
