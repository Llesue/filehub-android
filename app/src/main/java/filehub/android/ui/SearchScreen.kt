package filehub.android.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import filehub.android.R
import filehub.android.core.model.SearchResult
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    scopeRootId: Long?,
    scopeRootAlias: String?,
    rootAliases: Map<Long, String>,
    onBack: () -> Unit,
    onOpenResult: (SearchResult) -> Unit,
    imageLoader: ImageLoader? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.setScope(scopeRootId)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LaunchedEffect(query) {
        delay(300)
        viewModel.search(query)
    }

    BackHandler(enabled = state.selected.isNotEmpty()) {
        viewModel.clearSelection()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_clear),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.search(query)
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.scopeRootId == null,
                onClick = { viewModel.setScope(null) },
                label = { Text(stringResource(R.string.search_all)) },
            )
            if (scopeRootId != null && scopeRootAlias != null) {
                FilterChip(
                    selected = state.scopeRootId == scopeRootId,
                    onClick = { viewModel.setScope(scopeRootId) },
                    label = { Text(scopeRootAlias) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KindChip(stringResource(R.string.search_kind_all), null, state.kind) { viewModel.setKind(it) }
            KindChip(stringResource(R.string.search_kind_image), "image", state.kind) { viewModel.setKind(it) }
            KindChip(stringResource(R.string.search_kind_video), "video", state.kind) { viewModel.setKind(it) }
            KindChip(stringResource(R.string.search_kind_audio), "audio", state.kind) { viewModel.setKind(it) }
            KindChip(stringResource(R.string.search_kind_pdf), "pdf", state.kind) { viewModel.setKind(it) }
            KindChip(stringResource(R.string.search_kind_text), "text", state.kind) { viewModel.setKind(it) }
            KindChip(stringResource(R.string.search_kind_dir), "dir", state.kind) { viewModel.setKind(it) }
        }

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(1.dp))
        }

        state.actionError?.let { message ->
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
                    IconButton(onClick = viewModel::clearActionError) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        when {
            state.error != null -> CenterHint(state.error.orEmpty())
            !state.searched && query.isBlank() -> CenterHint(stringResource(R.string.search_min_chars))
            !state.searched -> CenterHint(stringResource(R.string.search_min_chars))
            state.loading && state.results.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.results.isEmpty() -> SearchEmptyState(
                state = state,
                onStartIndex = viewModel::startIndex,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = if (state.selected.isEmpty()) 24.dp else 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.results, key = { "${it.rootId}:${it.path}" }) { result ->
                    SearchResultCard(
                        result = result,
                        rootAlias = rootAliases[result.rootId],
                        imageLoader = imageLoader,
                        thumbUrl = viewModel.thumbnailUrl(result),
                        selected = state.key(result) in state.selected,
                        selectionMode = state.selected.isNotEmpty(),
                        onToggle = { viewModel.toggleSelection(result) },
                        onClick = { onOpenResult(result) },
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

        if (state.selected.isNotEmpty()) {
            SearchSelectionBar(
                count = state.selected.size,
                onSelectAll = viewModel::selectAll,
                onDownload = viewModel::downloadSelected,
                onDelete = viewModel::requestDeleteSelected,
                onClear = viewModel::clearSelection,
                busy = state.actionLoading,
            )
        }
    }

    if (state.deleteOpen) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.search_delete_title, state.deleteCount)) },
            text = { Text(stringResource(R.string.search_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                    enabled = !state.actionLoading,
                ) {
                    Text(
                        text = stringResource(R.string.selection_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SearchSelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    busy: Boolean,
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
            )
            Row {
                TextButton(onClick = onSelectAll, enabled = !busy) {
                    Text(stringResource(R.string.selection_select_all))
                }
                TextButton(onClick = onClear, enabled = !busy) {
                    Text(stringResource(R.string.selection_cancel))
                }
                TextButton(onClick = onDownload, enabled = !busy) {
                    Text(stringResource(R.string.transfer_download_action))
                }
                TextButton(onClick = onDelete, enabled = !busy) {
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
private fun SearchEmptyState(state: SearchState, onStartIndex: () -> Unit) {
    val status = state.indexStatus
    val job = status?.job
    val running = job != null && job.status != "completed" &&
        job.status != "failed" && job.status != "cancelled"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.indexError != null -> {
                Text(
                    text = state.indexError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.indexLoading && status == null -> CircularProgressIndicator()
            running -> {
                Text(
                    text = stringResource(R.string.search_index_running),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                val total = job?.total ?: 0
                val progress = job?.progress ?: 0
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            status?.status == "missing" -> {
                Text(
                    text = stringResource(R.string.search_index_missing),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onStartIndex,
                    enabled = !state.indexLoading,
                ) {
                    Text(stringResource(R.string.search_index_start))
                }
            }
            status?.partial == true -> {
                Text(
                    text = stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.search_index_partial),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> CenterHint(stringResource(R.string.search_no_results))
        }
    }
}

@Composable
private fun KindChip(label: String, value: String?, selected: String?, onSelect: (String?) -> Unit) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    rootAlias: String?,
    imageLoader: ImageLoader?,
    thumbUrl: String?,
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val entry = result.toEntry()
    val context = LocalContext.current
    val subtitle = buildString {
        if (rootAlias != null) append(rootAlias).append(" · ")
        append(if (result.path.isEmpty()) "/" else "/${result.path}")
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { if (selectionMode || selected) onToggle() else onClick() },
                onLongClick = onToggle,
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(fileIconRes(entry.kind)),
                    contentDescription = null,
                    tint = fileIconColor(entry.kind),
                    modifier = Modifier.size(24.dp),
                )
                if (thumbUrl != null && imageLoader != null) {
                    AsyncImage(
                        model = thumbUrl,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                FileNameText(
                    name = result.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!result.isDir) {
                    Text(
                        text = Formatter.formatShortFileSize(context, result.size) + " · " +
                            relativeTimeText(result.modified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
