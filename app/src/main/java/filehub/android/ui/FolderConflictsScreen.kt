package filehub.android.ui

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.R
import filehub.android.data.sync.FolderSyncEntryEntity

@Composable
fun FolderConflictsScreen(
    viewModel: FolderSyncViewModel,
    onBack: () -> Unit,
) {
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<FolderSyncEntryEntity, Boolean>?>(null) }
    val mode = config?.mode ?: "upload"

    LaunchedEffect(Unit) { viewModel.loadConflicts() }

    DetailScaffold(
        title = stringResource(R.string.folder_conflicts_title),
        onBack = onBack,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (conflicts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.folder_conflict_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(conflicts, key = { it.relativePath }) { entry ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = entry.relativePath,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.folder_conflict_local),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = Formatter.formatShortFileSize(
                                                context,
                                                entry.localSize,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            text = relativeTimeText(entry.localMtime),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.folder_conflict_remote),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = Formatter.formatShortFileSize(
                                                context,
                                                entry.remoteSize,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            text = relativeTimeText(entry.remoteMtime),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (mode != "download") {
                                        TextButton(onClick = { pending = entry to true }) {
                                            Text(stringResource(R.string.folder_conflict_keep_local))
                                        }
                                    }
                                    if (mode != "upload") {
                                        TextButton(onClick = { pending = entry to false }) {
                                            Text(stringResource(R.string.folder_conflict_keep_remote))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.operation_error)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.file_close))
                }
            },
        )
    }

    pending?.let { (entry, keepLocal) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = {
                Text(
                    stringResource(
                        if (keepLocal) {
                            R.string.folder_conflict_keep_local
                        } else {
                            R.string.folder_conflict_keep_remote
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (keepLocal) {
                            R.string.folder_conflict_keep_local_body
                        } else {
                            R.string.folder_conflict_keep_remote_body
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        viewModel.resolveConflict(entry, keepLocal)
                    },
                ) {
                    Text(stringResource(R.string.dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
