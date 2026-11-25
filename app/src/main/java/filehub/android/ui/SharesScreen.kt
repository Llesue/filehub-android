package filehub.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import filehub.android.R
import filehub.android.core.model.ShareInfo
import kotlinx.coroutines.delay

@Composable
fun SharesScreen(
    viewModel: ShareViewModel,
    faviconUrl: String?,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var nowSeconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowSeconds = System.currentTimeMillis() / 1000
        }
    }

    LaunchedEffect(Unit) { viewModel.load() }

    DetailScaffold(
        title = stringResource(R.string.shares_title),
        onBack = onBack,
        compact = true,
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
                else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = if (maxWidth >= 780.dp) 2 else 1
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.shares, key = { it.id }) { share ->
                            ShareCard(
                                share = share,
                                nowSeconds = nowSeconds,
                                onCopy = { viewModel.copyLink(share) },
                                onEdit = { viewModel.beginEdit(share) },
                                onRotate = { viewModel.rotate(share) },
                                onStop = { viewModel.stop(share) },
                                onEnable = { viewModel.enable(share) },
                                onOpen = { viewModel.open(share) },
                                onShowCard = { viewModel.showCard(share) },
                                onDelete = { viewModel.requestDelete(share) },
                            )
                        }
                        state.nextCursor?.let { cursor ->
                            item(key = "footer-$cursor", span = { GridItemSpan(maxLineSpan) }) {
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
        copyShareLink(context, link)
        Toast.makeText(context, R.string.share_copy_done, Toast.LENGTH_SHORT).show()
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

    state.cardShare?.let { share ->
        val link = state.cardLink ?: return@let
        val collection = share.kind == "collection"
        val name = if (collection) {
            stringResource(R.string.share_collection_count, share.itemCount)
        } else {
            share.realPath.trimEnd('/').substringAfterLast('/').ifEmpty { share.realPath }
        }
        val kind = if (collection || share.isDir) {
            filehub.android.core.model.FileKind.Folder
        } else {
            filehub.android.core.model.detectKind(name, "", false)
        }
        ShareCardDialog(
            spec = ShareCardSpec(
                url = link,
                title = name,
                kind = kind,
                iconColor = fileIconColor(kind).toArgb(),
                hasPassword = share.hasPassword,
                expiryText = shareExpiryLabel(share.expiresAt),
                faviconUrl = faviconUrl,
                headerTitle = "FileHub",
                headerSubtitle = stringResource(R.string.share_card_header),
                passwordText = stringResource(R.string.share_card_password),
                footerText = stringResource(R.string.share_card_footer),
            ),
            onDismiss = viewModel::dismissCard,
        )
    }

    state.deleting?.let { share ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.share_delete_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.share_delete_confirm_body))
                    state.deleteError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                    enabled = !state.deleteLoading,
                ) {
                    Text(
                        text = stringResource(R.string.share_delete),
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

    state.editing?.let { share ->
        ShareEditDialog(
            share = share,
            loading = state.editLoading,
            error = state.editError,
            onDismiss = viewModel::dismissEdit,
            onSave = { readOnly, expiresAt, clearExpires, password, clearPassword ->
                viewModel.update(
                    share = share,
                    readOnly = readOnly,
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
    nowSeconds: Long,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRotate: () -> Unit,
    onStop: () -> Unit,
    onEnable: () -> Unit,
    onOpen: () -> Unit,
    onShowCard: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val stopped = share.isStopped(nowSeconds)
    val collection = share.kind == "collection"
    val name = if (collection) {
        stringResource(R.string.share_collection_count, share.itemCount)
    } else {
        share.realPath.trimEnd('/').substringAfterLast('/').ifEmpty { share.realPath }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (stopped) 0.55f else 1f)
            .clickable(enabled = !stopped, onClick = onShowCard),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val kind = if (collection || share.isDir) {
                    filehub.android.core.model.FileKind.Folder
                } else {
                    filehub.android.core.model.detectKind(name, "", false)
                }
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
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    FileNameText(
                        name = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusText(share, nowSeconds) + " · " + stringResource(
                            R.string.share_visits,
                            share.visitCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!collection && share.realPath.isNotEmpty()) {
                        Text(
                            text = share.realPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (!stopped) {
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
                                text = { Text(stringResource(R.string.share_card_qr_action)) },
                                onClick = {
                                    menuOpen = false
                                    onShowCard()
                                },
                            )
                        }
                        CompactDropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (stopped) R.string.share_enable else R.string.share_revoke,
                                    ),
                                    color = if (stopped) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            },
                            onClick = {
                                menuOpen = false
                                if (stopped) onEnable() else onStop()
                            },
                        )
                        CompactDropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.share_delete),
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
            Spacer(Modifier.size(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy, enabled = !stopped) {
                    Text(stringResource(R.string.share_copy))
                }
                TextButton(onClick = onShowCard, enabled = !stopped) {
                    Text(stringResource(R.string.share_card_qr_action))
                }
                TextButton(onClick = onOpen, enabled = !stopped) {
                    Text(stringResource(R.string.shares_open))
                }
            }
        }
    }
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
        expiresAt: Long?,
        clearExpires: Boolean,
        password: String?,
        clearPassword: Boolean,
    ) -> Unit,
) {
    var readOnly by remember(share.id) { mutableStateOf(share.readOnly) }
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
                        AppSwitch(checked = readOnly, onCheckedChange = { readOnly = it })
                    }
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
                        AppSwitch(
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
private fun statusText(share: ShareInfo, nowSeconds: Long): String {
    if (share.isExpired(nowSeconds)) return stringResource(R.string.share_expired)
    if (share.revoked) return stringResource(R.string.share_revoked)
    val expiresAt = share.expiresAt ?: return stringResource(R.string.share_never_expires)
    val remaining = (expiresAt - nowSeconds).coerceAtLeast(0)
    if (remaining < 60) return stringResource(R.string.share_expiring_soon)
    val relative = when {
        remaining < 3_600 -> {
            val minutes = roundDiv(remaining, 60)
            pluralStringResource(R.plurals.share_expires_minutes, minutes, minutes)
        }
        // Prefer whole days once the remaining time is close to a day, so a
        // one-day share reads "1 day" instead of "23 hours".
        remaining < 12 * 3_600L -> {
            val hours = roundDiv(remaining, 3_600)
            pluralStringResource(R.plurals.share_expires_hours, hours, hours)
        }
        remaining < 30 * 86_400L -> {
            val days = roundDiv(remaining, 86_400)
            pluralStringResource(R.plurals.share_expires_days, days, days)
        }
        remaining < 365 * 86_400L -> {
            val months = roundDiv(remaining, 30 * 86_400L)
            pluralStringResource(R.plurals.share_expires_months, months, months)
        }
        else -> {
            val years = roundDiv(remaining, 365 * 86_400L)
            pluralStringResource(R.plurals.share_expires_years, years, years)
        }
    }
    return stringResource(R.string.share_expires_in, relative)
}

private fun roundDiv(value: Long, unit: Long): Int =
    ((value + unit / 2) / unit).toInt().coerceAtLeast(1)

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
