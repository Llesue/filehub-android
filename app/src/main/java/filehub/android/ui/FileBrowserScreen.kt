package filehub.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import filehub.android.BuildConfig
import java.io.File
import filehub.android.R
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

@Composable
fun FileBrowserScreen(
    viewModel: FilesViewModel,
    roots: List<FileRoot>,
    onSearch: (Long?) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val imageLoader by viewModel.imageLoader.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var newFile by remember { mutableStateOf(false) }
    var renameEntry by remember { mutableStateOf<FileEntry?>(null) }
    var transferEntry by remember { mutableStateOf<FileEntry?>(null) }
    var transferMove by remember { mutableStateOf(true) }
    var zipDialog by remember { mutableStateOf(false) }
    var imageMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
            cameraUri?.let { viewModel.enqueueUploads(listOf(it)) }
        }
    }

    fun takePhoto() {
        val file = File(context.cacheDir, "camera-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    BackHandler(
        enabled = state.selectedEntry == null &&
            state.previewEntry == null &&
            state.deletePreview == null &&
            !newFolder && !newFile && renameEntry == null,
    ) {
        if (state.selectionMode) viewModel.clearSelection() else onBack()
    }

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
            IconButton(onClick = { onSearch(state.root?.id) }) {
                Icon(Icons.Filled.Search, contentDescription = null)
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
                }
            }
            Box {
                IconButton(onClick = { moreMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.file_more))
                }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    if (!state.readOnly) {
                        CompactDropdownMenuItem(
                            text = { Text(stringResource(R.string.transfer_upload_action)) },
                            onClick = {
                                moreMenu = false
                                filePicker.launch(arrayOf("*/*"))
                            },
                        )
                        CompactDropdownMenuItem(
                            text = { Text(stringResource(R.string.transfer_photo_action)) },
                            onClick = {
                                moreMenu = false
                                mediaPicker.launch(arrayOf("image/*", "video/*"))
                            },
                        )
                        CompactDropdownMenuItem(
                            text = { Text(stringResource(R.string.transfer_camera_action)) },
                            onClick = {
                                moreMenu = false
                                takePhoto()
                            },
                        )
                    }
                    CompactDropdownMenuItem(
                        text = { Text(stringResource(R.string.action_new_folder)) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = {
                            moreMenu = false
                            newFolder = true
                        },
                    )
                    CompactDropdownMenuItem(
                        text = { Text(stringResource(R.string.action_new_file)) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = {
                            moreMenu = false
                            newFile = true
                        },
                    )
                    CompactDropdownMenuItem(
                        text = { Text(stringResource(R.string.file_refresh)) },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = {
                            moreMenu = false
                            viewModel.refresh()
                        },
                    )
                }
            }
            }
        }

        Breadcrumb(
            path = state.path,
            onNavigate = viewModel::navigateTo,
        )

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

        Box(modifier = Modifier.weight(1f)) {
        when {
            state.loginRequired -> MessageState(
                title = stringResource(R.string.file_login_required),
                action = null,
            )
            state.error != null && state.entries.isEmpty() -> MessageState(
                title = state.error.orEmpty(),
                action = {
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.common_retry))
                    }
                },
            )
            state.loading && state.entries.isEmpty() -> SkeletonList()
            state.entries.isEmpty() -> EmptyFolder()
            else -> Column(modifier = Modifier.fillMaxSize()) {
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
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 0.dp,
                        end = 0.dp,
                        top = 4.dp,
                        bottom = 24.dp,
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
                        onEntry = { viewModel.enter(entry) },
                        onToggle = { viewModel.toggleSelection(entry) },
                        onDetails = { viewModel.showDetails(entry) },
                        onRename = { renameEntry = entry },
                        onDelete = { viewModel.requestDelete(listOf(entry)) },
                        onDownload = {
                            requestNotificationPermission()
                            viewModel.enqueueDownload(entry)
                        },
                        onShare = { viewModel.openShare(entry) },
                        onMove = {
                            transferEntry = entry
                            transferMove = true
                        },
                        onCopy = {
                            transferEntry = entry
                            transferMove = false
                        },
                        onExtract = { viewModel.unzip(entry) },
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
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
        }
        }
        if (state.selectionMode) {
            SelectionBar(
                count = state.selected.size,
                onDelete = viewModel::requestDeleteSelected,
                onDownload = {
                    requestNotificationPermission()
                    viewModel.downloadSelected()
                },
                onZip = { zipDialog = true },
                onClear = viewModel::clearSelection,
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
    transferEntry?.let { entry ->
        TargetPathDialog(
            title = stringResource(if (transferMove) R.string.move_to else R.string.copy_to),
            roots = roots,
            initialRootId = state.root?.id,
            initialPath = state.path,
            onDismiss = { transferEntry = null },
            onConfirm = { rootId, path ->
                if (transferMove) {
                    viewModel.moveEntry(entry, rootId, path)
                } else {
                    viewModel.copyEntry(entry, rootId, path)
                }
                transferEntry = null
            },
        )
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
    state.shareEntry?.let { entry ->
        if (state.shareCreated == null) {
            ShareCreateDialog(
                entryName = entry.name,
                loading = state.shareLoading,
                error = state.shareError,
                onDismiss = viewModel::dismissShare,
                onCreate = viewModel::createShare,
            )
        }
    }
    state.shareCreated?.let { created ->
        ShareLinkDialog(link = created.url, onDismiss = viewModel::dismissShare)
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
    state.previewEntry?.let { entry ->
        PreviewDialog(
            entry = entry,
            imageEntries = state.previewImages,
            imageLoader = imageLoader,
            imageUrlFor = viewModel::previewImageUrl,
            thumbnailUrlFor = viewModel::thumbnailUrl,
            downloadUrlFor = viewModel::downloadUrl,
            text = state.textPreview,
            pdfFile = state.pdfPreviewFile,
            loading = state.previewLoading,
            mediaReady = state.previewMediaReady,
            error = state.previewError,
            playerFactory = viewModel::createPlayer,
            onPageChanged = viewModel::previewPage,
            onLongPress = {
                imageMenu = true
                viewModel.loadProperties(entry)
            },
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
        FileDetailsDialog(entry = entry, path = state.path, onDismiss = viewModel::dismissSelected)
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onZip: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.selection_selected, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            Row {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.selection_cancel))
                }
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.transfer_download_action))
                }
                TextButton(onClick = onZip) {
                    Text(stringResource(R.string.zip_create))
                }
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.selection_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
    onDetails: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onExtract: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val size = Formatter.formatShortFileSize(context, entry.size)
    val time = DateUtils.getRelativeTimeSpanString(
        entry.modified * 1000,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
    val meta = if (entry.isDir) {
        if (entry.size > 0) "$size · $time" else time
    } else {
        "$size · $time"
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
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(fileIconRes(entry.kind)),
                    contentDescription = null,
                    tint = fileIconColor(entry.kind),
                    modifier = Modifier.size(26.dp),
                )
                if (thumbnailUrl != null && imageLoader != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.file_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    CompactDropdownMenuItem(
                        text = { Text(stringResource(R.string.file_details)) },
                        onClick = {
                            menuOpen = false
                            onDetails()
                        },
                    )
                    CompactDropdownMenuItem(
                        text = { Text(stringResource(R.string.share_create)) },
                        onClick = {
                            menuOpen = false
                            onShare()
                        },
                    )
                    CompactDropdownMenuItem(
                        text = { Text(stringResource(R.string.move_to)) },
                        onClick = {
                            menuOpen = false
                            onMove()
                        },
                    )
                    CompactDropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_to)) },
                        onClick = {
                            menuOpen = false
                            onCopy()
                        },
                    )
                    if (entry.name.endsWith(".zip", ignoreCase = true)) {
                        CompactDropdownMenuItem(
                            text = { Text(stringResource(R.string.extract)) },
                            onClick = {
                                menuOpen = false
                                onExtract()
                            },
                        )
                    }
                    if (!entry.isDir) {
                        CompactDropdownMenuItem(
                            text = { Text(stringResource(R.string.transfer_download_action)) },
                            onClick = {
                                menuOpen = false
                                onDownload()
                            },
                        )
                    }
                    CompactDropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    CompactDropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
}

@Composable
private fun FileDetailsDialog(entry: FileEntry, path: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val size = Formatter.formatShortFileSize(context, entry.size)
    val modified = DateUtils.getRelativeTimeSpanString(
        entry.modified * 1000,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow(stringResource(R.string.file_type), entry.kind.name)
                DetailRow(stringResource(R.string.file_size), size)
                DetailRow(stringResource(R.string.file_modified), modified)
                DetailRow(
                    stringResource(R.string.file_path),
                    if (path.isEmpty()) "/" else "/$path",
                )
                if (entry.isSymlink) {
                    DetailRow(stringResource(R.string.file_symlink), entry.symlinkState ?: "—")
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
private fun MessageState(title: String, action: (@Composable () -> Unit)?) {
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
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

@Composable
private fun EmptyFolder() {
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
