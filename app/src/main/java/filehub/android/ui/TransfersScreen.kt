package filehub.android.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.BuildConfig
import filehub.android.R
import filehub.android.data.transfer.TransferItem
import filehub.android.data.transfer.TransferKind
import filehub.android.data.transfer.TransferStatus
import filehub.android.core.model.FileKind
import filehub.android.core.model.detectKind
import java.io.File

@Composable
fun TransfersScreen(
    viewModel: TransferViewModel,
    onBack: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current

    DetailScaffold(
        title = stringResource(R.string.transfers_title),
        onBack = onBack,
        actions = {
            TextButton(onClick = { confirmClear = true }) {
                Text(stringResource(R.string.transfers_clear))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.transfers_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    TransferCard(
                        item = item,
                        onCancel = { viewModel.cancel(item) },
                        onRetry = { viewModel.retry(item) },
                        onRemove = { viewModel.remove(item) },
                        onOpen = {
                            openTransfer(context, item)
                        },
                    )
                }
            }
        }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.transfers_clear)) },
            text = { Text(stringResource(R.string.transfer_completed)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearCompleted()
                    },
                ) {
                    Text(stringResource(R.string.selection_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun TransferCard(
    item: TransferItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (item.kind == TransferKind.UPLOAD) "↑" else "↓",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusLabel(item.status) +
                            " · " + Formatter.formatShortFileSize(context, item.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.status == TransferStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                when (item.status) {
                    TransferStatus.QUEUED, TransferStatus.RUNNING -> TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.transfer_cancel))
                    }
                    TransferStatus.FAILED, TransferStatus.CANCELLED -> TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.transfer_retry))
                    }
                    TransferStatus.COMPLETED -> {
                        if (item.kind == TransferKind.DOWNLOAD && item.localFile != null) {
                            TextButton(onClick = onOpen) {
                                Text(stringResource(R.string.transfer_open))
                            }
                        }
                    }
                }
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.common_remove))
                }
            }
            if (item.status == TransferStatus.COMPLETED &&
                item.kind == TransferKind.DOWNLOAD &&
                item.localFile != null
            ) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.transfer_saved_to,
                            downloadLocation(context, item) + "/" + item.name,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { openDownloadFolder(context, item) }) {
                        Text(stringResource(R.string.transfer_open_folder))
                    }
                }
            }
            if (item.status == TransferStatus.RUNNING || item.status == TransferStatus.QUEUED) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item.error?.let { error ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: TransferStatus): String = when (status) {
    TransferStatus.QUEUED -> stringResource(R.string.transfer_status_queued)
    TransferStatus.RUNNING -> stringResource(R.string.transfer_status_running)
    TransferStatus.COMPLETED -> stringResource(R.string.transfer_status_completed)
    TransferStatus.FAILED -> stringResource(R.string.transfer_status_failed)
    TransferStatus.CANCELLED -> stringResource(R.string.transfer_status_cancelled)
}

private fun downloadLocation(context: android.content.Context, item: TransferItem): String {
    val uri = Uri.parse(item.localFile.orEmpty())
    if (uri.scheme == "file") {
        return uri.path?.substringBeforeLast('/') ?: uri.path.orEmpty()
    }
    val kind = detectKind(item.name, item.mimeType, isDir = false)
    return if (kind == FileKind.Image || kind == FileKind.Video) {
        "DCIM/FileHub"
    } else {
        "Download/FileHub"
    }
}

private fun openDownloadFolder(context: android.content.Context, item: TransferItem) {
    val uri = Uri.parse(item.localFile.orEmpty())
    if (uri.scheme == "file") {
        openTransfer(context, item)
        return
    }
    val kind = detectKind(item.name, item.mimeType, isDir = false)
    val folderPath = if (kind == FileKind.Image || kind == FileKind.Video) {
        "primary:DCIM/FileHub"
    } else {
        "primary:Download/FileHub"
    }
    val folder = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        folderPath,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(
                R.string.transfer_saved_to,
                downloadLocation(context, item) + "/" + item.name,
            ),
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun openTransfer(context: android.content.Context, item: TransferItem) {
    val raw = item.localFile ?: return
    val uri = Uri.parse(raw)
    val viewUri = if (uri.scheme == "file") {
        val file = File(uri.path ?: return)
        FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
    } else {
        uri
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(viewUri, item.mimeType.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, item.name))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, item.name, Toast.LENGTH_SHORT).show()
    }
}
