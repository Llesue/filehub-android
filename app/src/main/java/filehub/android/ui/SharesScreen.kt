package filehub.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import filehub.android.R
import filehub.android.core.model.ShareInfo
import filehub.android.data.api.PublicLink
import filehub.android.data.api.PublicShareApi
import java.text.DateFormat
import java.util.Date

@Composable
fun SharesScreen(
    viewModel: ShareViewModel,
    onBack: () -> Unit,
    onOpenPublic: (PublicLink) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var openDialog by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    DetailScaffold(
        title = stringResource(R.string.shares_title),
        onBack = onBack,
        actions = {
            TextButton(onClick = { openDialog = true }) {
                Text(stringResource(R.string.shares_open))
            }
        },
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        if (state.loading && state.shares.isEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        when {
            state.error != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            state.shares.isEmpty() && !state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.shares_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.shares, key = { it.id }) { share ->
                    ShareCard(
                        share = share,
                        onCopy = {
                            viewModel.copyLink(share)
                            linkDialog = null
                        },
                        onEdit = { viewModel.beginEdit(share) },
                        onRotate = { viewModel.rotate(share) },
                        onRevoke = { viewModel.revoke(share) },
                        onOpen = { viewModel.open(share) },
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

    LaunchedEffect(state.openLink) {
        val link = state.openLink ?: return@LaunchedEffect
        viewModel.dismissOpenLink()
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(link),
                ),
            )
        }
    }

    LaunchedEffect(state.actionLink) {
        val link = state.actionLink ?: return@LaunchedEffect
        linkDialog = link
        copyToClipboard(context, link)
        viewModel.dismissAction()
    }

    state.actionError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAction,
            title = { Text(stringResource(R.string.operation_error)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissAction) {
                    Text(stringResource(R.string.share_close))
                }
            },
        )
    }

    if (openDialog) {
        OpenShareDialog(
            onDismiss = { openDialog = false },
            onOpen = {
                openDialog = false
                onOpenPublic(it)
            },
        )
    }

    linkDialog?.let { link ->
        ShareLinkDialog(link = link, onDismiss = { linkDialog = null })
    }

    state.editing?.let { share ->
        ShareEditDialog(
            share = share,
            loading = state.editLoading,
            error = state.editError,
            onDismiss = viewModel::dismissEdit,
            onSave = { readOnly, revoked, expiresAt, clearExpires, password, clearPassword ->
                viewModel.update(
                    share = share,
                    readOnly = readOnly,
                    revoked = revoked,
                    expiresAt = expiresAt,
                    clearExpires = clearExpires,
                    password = password,
                    clearPassword = clearPassword,
                )
            },
        )
    }
}

@Composable
private fun ShareCard(
    share: ShareInfo,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRotate: () -> Unit,
    onRevoke: () -> Unit,
    onOpen: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val name = share.realPath.trimEnd('/').substringAfterLast('/').ifEmpty { share.realPath }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (share.isDir) "DIR" else "FILE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    FileNameText(
                        name = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusText(share) + " · " + stringResource(
                            R.string.share_visits,
                            share.visitCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = share.realPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        CompactDropdownMenuItem(
                            text = { Text(stringResource(R.string.share_edit)) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        CompactDropdownMenuItem(
                            text = { Text(stringResource(R.string.share_rotate)) },
                            onClick = {
                                menuOpen = false
                                onRotate()
                            },
                        )
                        CompactDropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.share_revoke),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onRevoke()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.size(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy) {
                    Text(stringResource(R.string.share_copy))
                }
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.shares_open))
                }
            }
        }
    }
}

@Composable
private fun OpenShareDialog(
    onDismiss: () -> Unit,
    onOpen: (PublicLink) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidLink = stringResource(R.string.share_link_invalid)
    val submit = {
        val link = PublicShareApi.parseLink(value)
        if (link == null) {
            error = invalidLink
        } else {
            onOpen(link)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_open_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    placeholder = { Text(stringResource(R.string.share_open_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = clearFocusOnDone { submit() },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { submit() },
            ) {
                Text(stringResource(R.string.share_open_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
fun ShareLinkDialog(link: String, onDismiss: () -> Unit) {
    val bitmap = remember(link) { qrBitmap(link) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_created)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.share_qr),
                        modifier = Modifier.size(220.dp),
                    )
                }
                Text(
                    text = link,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    copyToClipboard(context, link)
                },
            ) {
                Text(stringResource(R.string.share_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.share_close))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareEditDialog(
    share: ShareInfo,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (
        readOnly: Boolean,
        revoked: Boolean,
        expiresAt: Long?,
        clearExpires: Boolean,
        password: String?,
        clearPassword: Boolean,
    ) -> Unit,
) {
    var readOnly by remember(share.id) { mutableStateOf(share.readOnly) }
    var revoked by remember(share.id) { mutableStateOf(share.revoked) }
    var expiryChoice by remember(share.id) { mutableStateOf<Int?>(null) }
    var password by remember(share.id) { mutableStateOf("") }
    var clearPassword by remember(share.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.share_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (share.isDir) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.share_read_only),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = readOnly, onCheckedChange = { readOnly = it })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.share_edit_paused),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = revoked, onCheckedChange = { revoked = it })
                }
                Text(
                    text = stringResource(R.string.share_edit_expiry),
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf<Int?>(null, 1, 7, 30, -1).forEach { choice ->
                        val label = when (choice) {
                            null -> stringResource(R.string.share_edit_expiry_keep)
                            1 -> "1d"
                            7 -> "7d"
                            30 -> "30d"
                            else -> stringResource(R.string.share_edit_expiry_never)
                        }
                        FilterChip(
                            selected = expiryChoice == choice,
                            onClick = { expiryChoice = choice },
                            label = { Text(label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            if (share.hasPassword) {
                                R.string.share_edit_password_set
                            } else {
                                R.string.share_edit_password_unset
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !clearPassword,
                    label = { Text(stringResource(R.string.share_edit_password_new)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (share.hasPassword) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.share_edit_clear_password),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = clearPassword,
                            onCheckedChange = {
                                clearPassword = it
                                if (it) password = ""
                            },
                        )
                    }
                }
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val expiresAt = when (val choice = expiryChoice) {
                        null -> null
                        -1 -> null
                        else -> System.currentTimeMillis() / 1000 + choice * 86_400L
                    }
                    onSave(
                        readOnly,
                        revoked,
                        expiresAt,
                        expiryChoice == -1,
                        password.takeIf { it.isNotBlank() },
                        clearPassword,
                    )
                },
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.editor_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun statusText(share: ShareInfo): String = when {
    share.revoked -> stringResource(R.string.share_revoked)
    share.expired -> stringResource(R.string.share_expired)
    else -> stringResource(R.string.share_valid)
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("FileHub share", text))
}

private fun qrBitmap(text: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    bitmap
}.getOrNull()
