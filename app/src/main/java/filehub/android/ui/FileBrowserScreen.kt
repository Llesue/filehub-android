package filehub.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.text.format.Formatter
import coil3.ImageLoader
import coil3.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import filehub.android.BuildConfig
import java.io.File
import filehub.android.R
import filehub.android.core.model.BrowserViewMode
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileRoot
import filehub.android.core.model.FileKind
import filehub.android.core.model.FileSort
import filehub.android.core.model.SortOrder
import kotlinx.coroutines.flow.first
import filehub.android.ui.theme.FileHubFileBlue
import filehub.android.ui.theme.FileHubFileBrown
import filehub.android.ui.theme.FileHubFileCyan
import filehub.android.ui.theme.FileHubFileGray
import filehub.android.ui.theme.FileHubFileOrange
import filehub.android.ui.theme.FileHubFilePurple
import filehub.android.ui.theme.FileHubFileRed
import filehub.android.ui.theme.FileHubFileTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FilesViewModel,
    roots: List<FileRoot>,
    onSearch: (Long?) -> Unit,
    onEditText: (FileEntry) -> Unit,
    onPauseAudio: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val imageLoader by viewModel.imageLoader.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var newFile by remember { mutableStateOf(false) }
    var renameEntry by remember { mutableStateOf<FileEntry?>(null) }
    var moreSheet by remember { mutableStateOf(false) }
    var createSheet by remember { mutableStateOf(false) }
    var transferOpen by remember { mutableStateOf(false) }
    var transferMove by remember { mutableStateOf(true) }
    var zipDialog by remember { mutableStateOf(false) }
    var imageMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var cameraTargetPath by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val cameraUnavailable = stringResource(R.string.camera_unavailable)

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            requestNotificationPermission()
            viewModel.enqueueUploads(uris)
        }
    }
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            requestNotificationPermission()
            viewModel.enqueueUploads(uris)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (ok) {
            requestNotificationPermission()
            val takenAt = cameraFile?.lastModified()?.takeIf { it > 0 }?.div(1000)
            cameraUri?.let {
                viewModel.enqueueUploads(
                    listOf(it),
                    directory = cameraTargetPath,
                    mtime = takenAt,
                )
            }
        } else {
            cameraFile?.delete()
        }
        cameraFile = null
        cameraUri = null
        cameraTargetPath = null
    }

    fun takePhoto() {
        val file = File(context.cacheDir, "camera-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
        cameraFile = file
        cameraUri = uri
        cameraTargetPath = state.path
        runCatching { cameraLauncher.launch(uri) }
            .onFailure { cameraError = cameraUnavailable }
    }

    BackHandler(
        enabled = state.selectedEntry == null &&
            state.previewEntry == null &&
            state.deletePreview == null &&
            !newFolder && !newFile && renameEntry == null,
    ) {
        if (state.selectionMode) {
            moreSheet = false
            viewModel.clearSelection()
        } else {
            onBack()
        }
    }

    val selectedEntries = state.entries.filter { it.name in state.selected }
    val singleSelected = selectedEntries.singleOrNull()
    val canDownload = selectedEntries.any { !it.isDir }
    val canZip = state.root?.isRemote != true && selectedEntries.size > 1
    val canExtract = singleSelected != null && !singleSelected.isDir &&
        singleSelected.name.endsWith(".zip", ignoreCase = true) &&
        state.root?.isRemote != true
    val canOpenAsText = singleSelected != null && !singleSelected.isDir &&
        singleSelected.kind == FileKind.File
    val canShare = state.root?.isRemote != true &&
        selectedEntries.isNotEmpty() &&
        selectedEntries.size <= 100 &&
        (selectedEntries.size == 1 || selectedEntries.none { it.isDir })

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.selectionMode) {
                IconButton(onClick = { viewModel.clearSelection() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.selection_cancel))
                }
                Text(
                    text = stringResource(R.string.selection_selected, state.selected.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::selectAll) {
                    Text(stringResource(R.string.selection_select_all))
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.root?.alias ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.readOnly) {
                        Text(
                            text = stringResource(R.string.file_read_only_reason),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.root?.isRemote != true) {
                    IconButton(onClick = { onSearch(state.root?.id) }) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                }
                IconButton(
                    onClick = {
                        viewModel.setView(
                            if (state.view == BrowserViewMode.List) {
                                BrowserViewMode.Grid
                            } else {
                                BrowserViewMode.List
                            },
                        )
                    },
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.view == BrowserViewMode.List) {
                                R.drawable.ic_view_grid
                            } else {
                                R.drawable.ic_view_list
                            },
                        ),
                        contentDescription = stringResource(
                            if (state.view == BrowserViewMode.List) {
                                R.string.file_view_grid
                            } else {
                                R.string.file_view_list
                            },
                        ),
                    )
                }
                Box {
                    Surface(
                        modifier = Modifier.clickable(enabled = false) {},
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            text = sortLabel(state.sort) + if (state.order == SortOrder.Asc) " ↑" else " ↓",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                    // The sort chip is interactive through its own overlay so the
                    // border and the dropdown can share a touch target.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { sortMenu = true },
                    )
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortMenuItem(FileSort.Name, state.sort, state.order) {
                            sortMenu = false
                            viewModel.setSort(FileSort.Name)
                        }
                        SortMenuItem(FileSort.Size, state.sort, state.order) {
                            sortMenu = false
                            viewModel.setSort(FileSort.Size)
                        }
                        SortMenuItem(FileSort.Modified, state.sort, state.order) {
                            sortMenu = false
                            viewModel.setSort(FileSort.Modified)
                        }
                        if (state.root?.isRemote == true) {
                            Text(
                                text = stringResource(R.string.sort_remote_size_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        Breadcrumb(
            path = state.path,
            onNavigate = viewModel::navigateTo,
        )

        cameraError?.let { message ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    )
                    IconButton(onClick = { cameraError = null }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        state.activeJobs.firstOrNull()?.let { job ->
            JobProgressBar(job = job, onCancel = { viewModel.cancelJob(job.id) })
        }

        state.operationError?.let { message ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    )
                    IconButton(onClick = viewModel::clearOperationError) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        if (state.loading && state.entries.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(1.dp))
        }

        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()
        val currentPath by rememberUpdatedState(state.path)

        LaunchedEffect(listState) {
            snapshotFlow {
                Triple(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                    listState.isScrollInProgress,
                )
            }.collect { (index, offset, scrolling) ->
                viewModel.reportScroll(
                    path = currentPath,
                    view = BrowserViewMode.List,
                    index = index,
                    offset = offset.coerceAtLeast(0),
                    persist = !scrolling,
                )
            }
        }
        LaunchedEffect(gridState) {
            snapshotFlow {
                Triple(
                    gridState.firstVisibleItemIndex,
                    gridState.firstVisibleItemScrollOffset,
                    gridState.isScrollInProgress,
                )
            }.collect { (index, offset, scrolling) ->
                viewModel.reportScroll(
                    path = currentPath,
                    view = BrowserViewMode.Grid,
                    index = index,
                    offset = offset.coerceAtLeast(0),
                    persist = !scrolling,
                )
            }
        }
        LaunchedEffect(state.path, state.view, state.restore, state.entries.size) {
            val target = state.restore ?: return@LaunchedEffect
            if (state.entries.isEmpty()) return@LaunchedEffect
            val max = state.entries.lastIndex
            val index = target.index.coerceIn(0, max)
            when {
                target.view == BrowserViewMode.List && state.view == BrowserViewMode.List -> {
                    listState.scrollToItem(index, target.offset.coerceAtLeast(0))
                    viewModel.consumeRestore()
                }
                target.view == BrowserViewMode.Grid && state.view == BrowserViewMode.Grid -> {
                    gridState.scrollToItem(index, target.offset.coerceAtLeast(0))
                    viewModel.consumeRestore()
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.loginRequired -> StatusMessage(
                    icon = Icons.Filled.Warning,
                    title = stringResource(R.string.file_login_required),
                )
                state.error != null && state.entries.isEmpty() -> StatusMessage(
                    icon = Icons.Filled.Warning,
                    title = state.error.orEmpty(),
                    body = stringResource(R.string.file_error_body),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = viewModel::refresh,
                )
                state.loading && state.entries.isEmpty() ->
                    if (state.view == BrowserViewMode.Grid) SkeletonGrid() else SkeletonList()
                state.entries.isEmpty() -> EmptyFolder(
                    onAction = if (!state.readOnly) ({ createSheet = true }) else null,
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    if (state.offline) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.file_offline_cached),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::refresh) {
                                    Text(stringResource(R.string.common_retry))
                                }
                            }
                        }
                    } else {
                        state.error?.let { message ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = viewModel::refresh) {
                                        Text(stringResource(R.string.common_retry))
                                    }
                                }
                            }
                        }
                    }
                    PullToRefreshBox(
                        isRefreshing = state.loading && state.entries.isNotEmpty(),
                        onRefresh = viewModel::refresh,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        if (state.view == BrowserViewMode.List) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 0.dp,
                                    end = 0.dp,
                                    top = 4.dp,
                                    bottom = 96.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(state.entries, key = { it.name + it.modified + it.size }) { entry ->
                                    FileCard(
                                        entry = entry,
                                        imageLoader = imageLoader,
                                        thumbnailUrl = viewModel.thumbnailUrl(entry),
                                        selected = entry.name in state.selected,
                                        selectionMode = state.selectionMode,
                                        onEntry = {
                                            when (entry.kind) {
                                                FileKind.Audio -> {
                                                    requestNotificationPermission()
                                                    viewModel.requestAudioPlay(entry)
                                                }
                                                FileKind.Video -> {
                                                    onPauseAudio()
                                                    viewModel.enter(entry)
                                                }
                                                else -> viewModel.enter(entry)
                                            }
                                        },
                                        onToggle = { viewModel.toggleSelection(entry) },
                                        dirSizeState = viewModel.dirSizeState(entry),
                                        onRequestDirSize = { viewModel.requestDirSize(entry) },
                                    )
                                }
                                state.nextCursor?.let { cursor ->
                                    item(key = "footer-$cursor") {
                                        LaunchedEffect(cursor) { viewModel.loadMore() }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val columns = when {
                                    maxWidth >= 840.dp -> 4
                                    maxWidth >= 600.dp -> 3
                                    else -> 2
                                }
                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Fixed(columns),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        start = 8.dp,
                                        end = 8.dp,
                                        top = 4.dp,
                                        bottom = 96.dp,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    gridItems(
                                        state.entries,
                                        key = { it.name + it.modified + it.size },
                                    ) { entry ->
                                        GridCard(
                                            entry = entry,
                                            imageLoader = imageLoader,
                                            thumbnailUrl = viewModel.thumbnailUrl(entry),
                                            selected = entry.name in state.selected,
                                            selectionMode = state.selectionMode,
                                            onEntry = {
                                                when (entry.kind) {
                                                    FileKind.Audio -> {
                                                        requestNotificationPermission()
                                                        viewModel.requestAudioPlay(entry)
                                                    }
                                                    FileKind.Video -> {
                                                        onPauseAudio()
                                                        viewModel.enter(entry)
                                                    }
                                                    else -> viewModel.enter(entry)
                                                }
                                            },
                                            onToggle = { viewModel.toggleSelection(entry) },
                                        )
                                    }
                                    state.nextCursor?.let { cursor ->
                                        item(
                                            key = "grid-footer-$cursor",
                                            span = { GridItemSpan(maxLineSpan) },
                                        ) {
                                            LaunchedEffect(cursor) { viewModel.loadMore() }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!state.readOnly &&
                !state.selectionMode &&
                state.previewEntry == null &&
                state.unsupportedPreview == null
            ) {
                FloatingActionButton(
                    onClick = { createSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.fab_create),
                    )
                }
            }
        }
        if (state.selectionMode) {
            SelectionBar(
                showShare = canShare,
                onMove = {
                    transferMove = true
                    transferOpen = true
                },
                onDelete = viewModel::requestDeleteSelected,
                onShare = viewModel::openShareSelection,
                onMore = { moreSheet = true },
            )
        }
    }

    if (newFolder) {
        TextInputDialog(
            title = stringResource(R.string.dialog_new_folder_title),
            confirmLabel = stringResource(R.string.dialog_create),
            onDismiss = { newFolder = false },
            onConfirm = { name ->
                newFolder = false
                viewModel.createFolder(name)
            },
        )
    }
    if (newFile) {
        TextInputDialog(
            title = stringResource(R.string.dialog_new_file_title),
            confirmLabel = stringResource(R.string.dialog_create),
            onDismiss = { newFile = false },
            onConfirm = { name ->
                newFile = false
                viewModel.createFile(name)
            },
        )
    }
    renameEntry?.let { entry ->
        TextInputDialog(
            title = stringResource(R.string.dialog_rename_title),
            initialValue = entry.name,
            confirmLabel = stringResource(R.string.dialog_save),
            onDismiss = { renameEntry = null },
            onConfirm = { newName ->
                renameEntry = null
                viewModel.rename(entry, newName)
            },
        )
    }
    if (transferOpen) {
        TargetPathDialog(
            title = stringResource(if (transferMove) R.string.move_to else R.string.copy_to),
            roots = roots,
            initialRootId = state.root?.id,
            initialPath = state.path,
            onDismiss = { transferOpen = false },
            onConfirm = { rootId, path ->
                if (transferMove) {
                    viewModel.moveSelected(rootId, path)
                } else {
                    viewModel.copySelected(rootId, path)
                }
                transferOpen = false
            },
        )
    }
    if (createSheet) {
        ModalBottomSheet(onDismissRequest = { createSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                SheetAction(R.string.action_new_folder) {
                    createSheet = false
                    newFolder = true
                }
                SheetAction(R.string.action_new_file) {
                    createSheet = false
                    newFile = true
                }
                SheetAction(R.string.transfer_upload_action) {
                    createSheet = false
                    filePicker.launch(arrayOf("*/*"))
                }
                SheetAction(R.string.transfer_photo_action) {
                    createSheet = false
                    mediaPicker.launch(arrayOf("image/*", "video/*"))
                }
                SheetAction(R.string.transfer_camera_action) {
                    createSheet = false
                    takePhoto()
                }
            }
        }
    }
    if (moreSheet) {
        ModalBottomSheet(onDismissRequest = { moreSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                if (canDownload) {
                    SheetAction(R.string.transfer_download_action) {
                        moreSheet = false
                        requestNotificationPermission()
                        viewModel.downloadSelected()
                    }
                }
                if (!state.readOnly) {
                    SheetAction(R.string.copy_to) {
                        moreSheet = false
                        transferMove = false
                        transferOpen = true
                    }
                }
                if (singleSelected != null) {
                    if (!state.readOnly) {
                        SheetAction(R.string.action_rename) {
                            moreSheet = false
                            renameEntry = singleSelected
                        }
                    }
                    SheetAction(R.string.file_details) {
                        moreSheet = false
                        viewModel.showDetails(singleSelected)
                    }
                    if (canOpenAsText) {
                        SheetAction(R.string.action_open_as_text) {
                            moreSheet = false
                            viewModel.openAsText(singleSelected)
                        }
                    }
                    if (canExtract) {
                        SheetAction(R.string.extract) {
                            moreSheet = false
                            viewModel.unzip(singleSelected)
                        }
                    }
                }
                if (canZip) {
                    SheetAction(R.string.zip_create) {
                        moreSheet = false
                        zipDialog = true
                    }
                }
            }
        }
    }
    if (zipDialog) {
        TextInputDialog(
            title = stringResource(R.string.zip_create),
            initialValue = "archive.zip",
            confirmLabel = stringResource(R.string.dialog_create),
            onDismiss = { zipDialog = false },
            onConfirm = { name ->
                zipDialog = false
                viewModel.zipSelected(name)
            },
        )
    }
    if (state.shareEntries.isNotEmpty() && state.shareCreated == null) {
        val collection = state.shareEntries.size > 1
        ShareCreateDialog(
            entryName = if (collection) {
                stringResource(R.string.share_collection_count, state.shareEntries.size)
            } else {
                state.shareEntries.first().name
            },
            collection = collection,
            loading = state.shareLoading,
            error = state.shareError,
            onDismiss = viewModel::dismissShare,
            onCreate = viewModel::createShare,
        )
    }
    state.shareCreated?.let { created ->
        val collection = state.shareEntries.size > 1
        val entry = state.shareEntries.firstOrNull()
        val kind = if (collection || entry?.isDir == true) {
            FileKind.Folder
        } else {
            entry?.kind ?: FileKind.File
        }
        val title = if (collection) {
            stringResource(R.string.share_collection_count, state.shareEntries.size)
        } else {
            entry?.name.orEmpty()
        }
        ShareCardDialog(
            spec = ShareCardSpec(
                url = created.url,
                title = title,
                kind = kind,
                iconColor = fileIconColor(kind).toArgb(),
                hasPassword = state.shareHasPassword,
                expiryText = shareExpiryLabel(created.expiresAt),
                faviconUrl = state.profile?.iconUrl,
                headerTitle = "FileHub",
                headerSubtitle = stringResource(R.string.share_card_header),
                passwordText = stringResource(R.string.share_card_password),
                footerText = stringResource(R.string.share_card_footer),
            ),
            onDismiss = viewModel::dismissShare,
        )
    }
    state.audioPicker?.let { picker ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAudioPicker,
            title = { Text(stringResource(R.string.audio_track_title)) },
            text = {
                Column {
                    picker.info.tracks.forEach { track ->
                        TextButton(
                            onClick = { viewModel.chooseAudioTrack(track.index) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.audio_track_label,
                                    track.index + 1,
                                    track.codec.ifBlank { "?" },
                                    track.channels,
                                    track.language ?: "—",
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            if (track.supported) {
                                Text(stringResource(R.string.audio_track_native))
                            } else {
                                Text(stringResource(R.string.audio_track_transcode))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissAudioPicker) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    state.deletePreview?.let { preview ->
        DeleteConfirmDialog(
            preview = preview,
            loading = state.operationLoading,
            error = state.operationError,
            onDismiss = viewModel::dismissDeletePreview,
            onConfirm = viewModel::confirmDelete,
        )
    }
    state.unsupportedPreview?.let { unsupported ->
        UnsupportedPreviewDialog(
            preview = unsupported,
            onOpenAsText = {
                viewModel.dismissUnsupported()
                viewModel.openAsText(unsupported.entry)
            },
            onDownload = {
                requestNotificationPermission()
                viewModel.dismissUnsupported()
                viewModel.enqueueDownload(unsupported.entry)
            },
            onShare = if (state.root?.isRemote == true) {
                null
            } else {
                {
                    viewModel.dismissUnsupported()
                    viewModel.openShare(unsupported.entry)
                }
            },
            onDetails = {
                viewModel.dismissUnsupported()
                viewModel.showDetails(unsupported.entry)
            },
            onDismiss = viewModel::dismissUnsupported,
        )
    }
    state.previewEntry?.let { entry ->
        PreviewDialog(
            entry = entry,
            imageEntries = state.previewImages,
            session = state.previewSession,
            imageLoader = imageLoader,
            imageUrlFor = viewModel::previewImageUrl,
            thumbnailUrlFor = viewModel::thumbnailUrl,
            downloadUrlFor = viewModel::downloadUrl,
            text = state.textPreview,
            pdfFile = state.pdfPreviewFile,
            loading = state.previewLoading,
            mediaReady = state.previewMediaReady,
            error = state.previewError,
            previewAsText = state.previewAsText,
            encoding = state.previewEncoding,
            playerFactory = viewModel::createPlayer,
            onPageChanged = viewModel::previewPage,
            onLongPress = {
                imageMenu = true
                viewModel.loadProperties(entry)
            },
            onEdit = if (
                state.textPreview?.writable == true &&
                state.textPreview?.lossy != true &&
                (entry.kind == FileKind.Text || entry.kind == FileKind.Code || state.previewAsText)
            ) {
                {
                    viewModel.closePreview()
                    onEditText(entry)
                }
            } else {
                null
            },
            onReloadEncoding = viewModel::reloadPreviewText,
            onRetryText = { viewModel.reloadPreviewText(state.previewEncoding) },
            onDownload = {
                requestNotificationPermission()
                viewModel.enqueueDownload(entry)
            },
            onShare = { viewModel.openShare(entry) },
            onDetails = { viewModel.showDetails(entry) },
            onClose = viewModel::closePreview,
        )
    }
    if (imageMenu) {
        state.previewEntry?.let { entry ->
            ImageActionsDialog(
                entry = entry,
                properties = state.properties,
                loading = state.propertiesLoading,
                error = state.propertiesError,
                onDownload = {
                    requestNotificationPermission()
                    viewModel.enqueueDownload(entry)
                    imageMenu = false
                    viewModel.clearProperties()
                },
                onDismiss = {
                    imageMenu = false
                    viewModel.clearProperties()
                },
            )
        }
    }
    state.selectedEntry?.let { entry ->
        FileDetailsDialog(
            entry = entry,
            path = state.path,
            dirSizeState = viewModel.dirSizeState(entry),
            onRetryDirSize = { viewModel.retryDirSize(entry) },
            thumbStatus = state.thumbStatus,
            onRetryThumb = { viewModel.retryThumbnail(entry) },
            properties = state.properties,
            propertiesLoading = state.propertiesLoading,
            propertiesError = state.propertiesError,
            onRetryProperties = { viewModel.loadProperties(entry) },
            onDismiss = viewModel::dismissSelected,
        )
    }
}

@Composable
private fun SelectionBar(
    showShare: Boolean,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onMove) {
                Text(stringResource(R.string.move_to))
            }
            if (showShare) {
                TextButton(onClick = onShare) {
                    Text(stringResource(R.string.share_create))
                }
            }
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.selection_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onMore) {
                Text(stringResource(R.string.file_action_more))
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    sort: FileSort,
    current: FileSort,
    order: SortOrder,
    onClick: () -> Unit,
) {
    val selected = sort == current
    CompactDropdownMenuItem(
        text = {
            Text(
                text = sortLabel(sort) + if (selected) {
                    if (order == SortOrder.Asc) "  ↑" else "  ↓"
                } else {
                    ""
                },
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun sortLabel(sort: FileSort): String = when (sort) {
    FileSort.Name -> stringResource(R.string.file_sort_name)
    FileSort.Size -> stringResource(R.string.file_sort_size)
    FileSort.Modified -> stringResource(R.string.file_sort_modified)
}

@Composable
private fun Breadcrumb(
    path: String,
    onNavigate: (String) -> Unit,
) {
    val segments = path.split('/').filter { it.isNotEmpty() }
    val scrollState = rememberScrollState()
    LaunchedEffect(path) {
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_file_folder),
                contentDescription = null,
                tint = fileIconColor(FileKind.Folder),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onNavigate("") }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 1.dp),
                    )
                }
                Text(
                    text = segment,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index == segments.lastIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (index == segments.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable {
                            onNavigate(segments.take(index + 1).joinToString("/"))
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            // Keep the last path segment away from the screen edge.
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun FileCard(
    entry: FileEntry,
    imageLoader: ImageLoader?,
    thumbnailUrl: String?,
    selected: Boolean,
    selectionMode: Boolean,
    onEntry: () -> Unit,
    onToggle: () -> Unit,
    dirSizeState: DirSizeState?,
    onRequestDirSize: () -> Unit,
) {
    val context = LocalContext.current
    val sizeUnknown = entry.isDir && entry.size <= 0
    val size = if (sizeUnknown) {
        dirSizeLabel(dirSizeState)
    } else {
        Formatter.formatShortFileSize(context, entry.size)
    }
    val time = relativeTimeText(entry.modified)
    val meta = if (entry.isDir) {
        if (sizeUnknown || entry.size > 0) "$size · $time" else time
    } else {
        "$size · $time"
    }
    LaunchedEffect(entry.name, entry.modified, sizeUnknown) {
        if (sizeUnknown && dirSizeState == null) onRequestDirSize()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .combinedClickable(
                onClick = {
                    if (selectionMode || selected) onToggle() else onEntry()
                },
                onLongClick = onToggle,
            )
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumb(
            entry = entry,
            imageLoader = imageLoader,
            thumbnailUrl = thumbnailUrl,
            selected = selected,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            FileNameText(
                name = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GridCard(
    entry: FileEntry,
    imageLoader: ImageLoader?,
    thumbnailUrl: String?,
    selected: Boolean,
    selectionMode: Boolean,
    onEntry: () -> Unit,
    onToggle: () -> Unit,
) {
    val label = stringResource(R.string.file_item_description, entry.name, fileKindLabel(entry.kind))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    if (selectionMode || selected) onToggle() else onEntry()
                },
                onLongClick = onToggle,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
            }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileThumb(
            entry = entry,
            imageLoader = imageLoader,
            thumbnailUrl = thumbnailUrl,
            selected = selected,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FileThumb(
    entry: FileEntry,
    imageLoader: ImageLoader?,
    thumbnailUrl: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    var thumbFailed by remember(thumbnailUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(fileIconRes(entry.kind)),
            contentDescription = null,
            tint = fileIconColor(entry.kind),
            modifier = Modifier.size(28.dp),
        )
        if (thumbnailUrl != null && imageLoader != null && !thumbFailed) {
            AsyncImage(
                model = thumbnailUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { thumbFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (entry.kind == FileKind.Video) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
private fun FileDetailsDialog(
    entry: FileEntry,
    path: String,
    dirSizeState: DirSizeState?,
    onRetryDirSize: () -> Unit,
    thumbStatus: filehub.android.data.api.ThumbStatusDto?,
    onRetryThumb: () -> Unit,
    properties: filehub.android.core.model.FileProperties?,
    propertiesLoading: Boolean,
    propertiesError: String?,
    onRetryProperties: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sizeUnknown = entry.isDir && entry.size <= 0
    val size = if (sizeUnknown) {
        dirSizeLabel(dirSizeState)
    } else {
        Formatter.formatShortFileSize(context, entry.size)
    }
    val modified = relativeTimeText(entry.modified)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FileNameText(entry.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow(stringResource(R.string.file_type), fileKindLabel(entry.kind))
                DetailRow(stringResource(R.string.file_size), size)
                if (sizeUnknown && dirSizeState is DirSizeState.Failed) {
                    TextButton(onClick = onRetryDirSize) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
                if (thumbStatus?.state == "failed") {
                    DetailRow(
                        stringResource(R.string.thumb_status),
                        thumbStatus.message ?: thumbStatus.reason ?: "—",
                    )
                    TextButton(onClick = onRetryThumb) {
                        Text(stringResource(R.string.thumb_retry))
                    }
                } else if (thumbStatus?.state == "missing" && entry.hasPreview) {
                    TextButton(onClick = onRetryThumb) {
                        Text(stringResource(R.string.thumb_retry))
                    }
                }
                DetailRow(stringResource(R.string.file_modified), modified)
                DetailRow(
                    stringResource(R.string.file_path),
                    listOf(path, entry.name)
                        .filter { it.isNotEmpty() }
                        .joinToString("/", prefix = "/"),
                )
                if (entry.isSymlink) {
                    DetailRow(stringResource(R.string.file_symlink), entry.symlinkState ?: "—")
                }
                if (entry.kind == FileKind.Image) {
                    ImagePropertiesSection(
                        properties = properties,
                        loading = propertiesLoading,
                        error = propertiesError,
                        fallbackFormat = entry.mimeType,
                        onRetry = onRetryProperties,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.file_close))
            }
        },
    )
}

@Composable
private fun JobProgressBar(job: ActiveJob, onCancel: () -> Unit) {
    val status = when (job.status) {
        "queued" -> stringResource(R.string.job_queued)
        "running" -> stringResource(R.string.job_running)
        "completed" -> stringResource(R.string.job_completed)
        "failed" -> stringResource(R.string.job_failed)
        "cancelled" -> stringResource(R.string.job_cancelled)
        else -> job.status
    }
    val fraction = when {
        job.total > 0 -> (job.progress.toFloat() / job.total).coerceIn(0f, 1f)
        job.bytesTotal > 0 -> (job.bytesDone.toFloat() / job.bytesTotal).coerceIn(0f, 1f)
        else -> null
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    text = "${job.label} · $status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fraction == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.job_cancel),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun dirSizeLabel(state: DirSizeState?): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return when (state) {
        null, DirSizeState.Failed -> "—"
        DirSizeState.Loading -> stringResource(R.string.file_dir_size_computing)
        is DirSizeState.Ready -> {
            val prefix = if (state.partial) "≥ " else ""
            prefix + Formatter.formatShortFileSize(context, state.size)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyFolder(onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_file_folder),
            contentDescription = null,
            tint = fileIconColor(FileKind.Folder),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.file_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.file_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.file_empty_action))
            }
        }
    }
}

@Composable
private fun SheetAction(labelRes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gridItems(List(10) { it }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun SkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.size(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}
