package filehub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onEditText: (filehub.android.core.model.FileEntry) -> Unit,
    onPauseAudio: () -> Unit,
) {
    val active = state.active
    val files by filesViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(active?.id) {
        val currentProfile = filesViewModel.state.value.profile
        if (currentProfile != null && (active == null || currentProfile.id != active.id)) {
            filesViewModel.release()
        }
    }

    if (active == null) {
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
        return
    }

    val root = files.root
    if (root != null) {
        FileBrowserScreen(
            viewModel = filesViewModel,
            roots = state.roots,
            onSearch = onSearch,
            onEditText = onEditText,
            onPauseAudio = onPauseAudio,
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.roots, key = { it.id }) { item ->
                    RootCard(
                        root = item,
                        onClick = {
                            filesViewModel.open(profile = active, root = item)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RootCard(root: FileRoot, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
