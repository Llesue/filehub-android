package filehub.android.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import filehub.android.R
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileKind
import filehub.android.core.model.PublicShareEntry

@Composable
fun PublicShareScreen(
    viewModel: PublicShareViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }

    BackHandler {
        if (state.path.isEmpty()) onBack() else viewModel.goUp()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (state.path.isEmpty()) onBack() else viewModel.goUp()
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.meta?.name ?: "Share",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.meta?.readOnly == true) {
                    Text(
                        text = stringResource(R.string.public_share_read_only),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        when {
            state.loading && state.meta == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.needsPassword -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.public_share_unlock),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.public_share_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = { viewModel.unlock(password) },
                    enabled = password.isNotBlank() && !state.loading,
                ) {
                    Text(stringResource(R.string.public_share_unlock_button))
                }
                state.error?.let {
                    Spacer(Modifier.size(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
            state.error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            state.meta != null -> {
                if (state.path.isNotEmpty()) {
                    Text(
                        text = "/${state.path}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                val meta = state.meta!!
                if (meta.entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.public_share_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(meta.entries, key = { it.name + it.size + it.modified }) { entry ->
                            PublicEntryCard(
                                entry = entry,
                                thumbUrl = if (entry.kind() == FileKind.Image || entry.kind() == FileKind.Video) {
                                    viewModel.thumbUrl(entry)
                                } else {
                                    null
                                },
                                onOpen = {
                                    if (entry.isDir) {
                                        viewModel.openDir(entry)
                                    } else {
                                        download(context, entry, viewModel.downloadUrl(entry))
                                    }
                                },
                                onDownload = {
                                    download(context, entry, viewModel.downloadUrl(entry))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicEntryCard(
    entry: PublicShareEntry,
    thumbUrl: String?,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
) {
    val kind = entry.kind()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                    painter = painterResource(fileIconRes(kind)),
                    contentDescription = null,
                    tint = fileIconColor(kind),
                    modifier = Modifier.size(24.dp),
                )
                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
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
                if (!entry.isDir) {
                    Text(
                        text = android.text.format.Formatter.formatShortFileSize(
                            LocalContext.current,
                            entry.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!entry.isDir) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.public_share_download))
                }
            }
        }
    }
}

private fun PublicShareEntry.kind(): FileKind {
    val entry = FileEntry(
        name = name,
        isDir = isDir,
        isSymlink = false,
        size = size,
        modified = modified,
        mimeType = mimeType,
        hasPreview = hasPreview,
        symlinkState = null,
    )
    return entry.kind
}

private fun download(context: Context, entry: PublicShareEntry, url: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(entry.name)
        .setDescription("FileHub share")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "FileHub/${entry.name}",
        )
    val manager = context.getSystemService(DownloadManager::class.java)
    manager.enqueue(request)
    Toast.makeText(context, R.string.public_share_downloading, Toast.LENGTH_SHORT).show()
}
