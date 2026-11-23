package filehub.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import filehub.android.R
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileKind

@Composable
fun DirectoryPickerDialog(
    state: DirectoryPickerState,
    viewModel: DirectoryPickerViewModel,
    onPick: (rootId: Long, path: String) -> Unit,
) {
    var rootMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }

    val pickedRoot = state.pickedRootId
    val pickedPath = state.pickedPath
    LaunchedEffect(pickedRoot, pickedPath) {
        if (pickedRoot != null && pickedPath != null) {
            onPick(pickedRoot, pickedPath)
        }
    }

    AlertDialog(
        onDismissRequest = viewModel::close,
        title = {
            Column {
                Text(stringResource(R.string.directory_picker_title))
                Box {
                    TextButton(
                        onClick = { rootMenu = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(
                            text = state.rootAlias + if (state.rootReadOnly) {
                                " · " + stringResource(R.string.directory_picker_read_only_short)
                            } else {
                                ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(expanded = rootMenu, onDismissRequest = { rootMenu = false }) {
                        state.roots.forEach { root ->
                            CompactDropdownMenuItem(
                                text = {
                                    Text(
                                        root.alias + when {
                                            root.online == false -> " · " +
                                                stringResource(R.string.directory_picker_offline)
                                            root.readOnly -> " · " +
                                                stringResource(R.string.directory_picker_read_only_short)
                                            else -> ""
                                        },
                                    )
                                },
                                onClick = {
                                    rootMenu = false
                                    viewModel.switchRoot(root.id)
                                },
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::up, enabled = state.path.isNotEmpty()) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.directory_picker_up),
                        )
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.common_refresh),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { viewModel.navigate("") }
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                        )
                        val segments = state.path.split('/').filter { it.isNotEmpty() }
                        segments.forEachIndexed { index, segment ->
                            if (index > 0) {
                                Text(
                                    text = "/",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = segment,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (index == segments.lastIndex) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                maxLines = 1,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.navigate(
                                            segments.take(index + 1).joinToString("/"),
                                        )
                                    }
                                    .padding(horizontal = 3.dp, vertical = 2.dp),
                            )
                        }
                    }
                    TextButton(onClick = viewModel::toggleManual) {
                        Text(stringResource(R.string.directory_picker_manual))
                    }
                }

                if (state.manualOpen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.manualPath,
                            onValueChange = viewModel::setManualPath,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = {
                                Text(stringResource(R.string.directory_picker_manual_hint))
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = { viewModel.navigateManual() },
                            ),
                        )
                        TextButton(onClick = viewModel::navigateManual) {
                            Text(stringResource(R.string.directory_picker_go))
                        }
                    }
                }

                when {
                    state.loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    }
                    state.error != null -> Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.directories.isEmpty() -> Text(
                        text = stringResource(R.string.directory_picker_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(state.directories, key = { it.name }) { directory ->
                            DirectoryRow(
                                entry = directory,
                                onClick = {
                                    val path = if (state.path.isEmpty()) {
                                        directory.name
                                    } else {
                                        "${state.path}/${directory.name}"
                                    }
                                    viewModel.navigate(path)
                                },
                            )
                        }
                    }
                }

                if (state.nextCursor != null) {
                    TextButton(onClick = viewModel::loadMore, enabled = !state.loadingMore) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.directory_picker_load_more))
                        }
                    }
                }

                if (!state.rootReadOnly) {
                    TextButton(
                        onClick = { newFolder = true },
                        enabled = !state.loading,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.directory_picker_new))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.confirm(state.path) },
                enabled = !state.loading && !state.checking && !state.creating &&
                    !state.rootReadOnly,
            ) {
                if (state.checking || state.creating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.directory_picker_select))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::close) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )

    if (newFolder) {
        TextInputDialog(
            title = stringResource(R.string.directory_picker_new),
            confirmLabel = stringResource(R.string.dialog_create),
            onDismiss = { newFolder = false },
            onConfirm = { name ->
                newFolder = false
                viewModel.createFolder(name)
            },
        )
    }

    state.missingPath?.let { missing ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMissing,
            title = { Text(stringResource(R.string.directory_picker_missing_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.directory_picker_missing_body))
                    Text(
                        text = "${state.rootAlias}:/$missing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::createMissingAndPick,
                    enabled = !state.creating,
                ) {
                    Text(stringResource(R.string.directory_picker_create_and_select))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMissing) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun DirectoryRow(entry: FileEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_file_folder),
            contentDescription = null,
            tint = fileIconColor(FileKind.Folder),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DirectoryField(
    label: String,
    rootAlias: String,
    path: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedTextField(
        value = if (path.isEmpty()) "/" else "/$path",
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        trailingIcon = {
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_file_folder),
                    contentDescription = stringResource(R.string.directory_picker_title),
                )
            }
        },
        supportingText = if (rootAlias.isNotEmpty()) {
            { Text(rootAlias) }
        } else {
            null
        },
    )
}
