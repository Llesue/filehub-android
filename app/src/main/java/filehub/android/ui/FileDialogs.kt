package filehub.android.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.text.format.Formatter
import coil3.ImageLoader
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import filehub.android.R
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileKind
import filehub.android.data.api.DeletePreviewDto
import filehub.android.data.api.TextFileDto

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String = "",
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.dialog_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Text(confirmLabel)
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
fun DeleteConfirmDialog(
    preview: DeletePreviewDto,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var code by remember(preview.token) { mutableStateOf("") }
    val context = LocalContext.current
    val totalBytes = preview.items.sumOf { it.size }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        R.string.delete_body,
                        preview.items.size,
                        Formatter.formatShortFileSize(context, totalBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                preview.items.take(4).forEach { item ->
                    Text(
                        text = "· ${item.relative}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (preview.requiresCode) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.delete_code)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                onClick = { onConfirm(if (preview.requiresCode) code else null) },
                enabled = !loading && (!preview.requiresCode || code.length >= 4),
            ) {
                Text(stringResource(R.string.delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PreviewDialog(
    entry: FileEntry,
    imageLoader: ImageLoader?,
    downloadUrl: String?,
    text: TextFileDto?,
    pdfFile: File?,
    loading: Boolean,
    mediaReady: Int,
    error: String?,
    playerFactory: () -> ExoPlayer?,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.preview_close),
                        tint = Color.White,
                    )
                }
                Text(
                    text = entry.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
                error != null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = error, color = Color.White)
                }
                entry.kind == FileKind.Image && downloadUrl != null && imageLoader != null -> {
                    ZoomableImage(url = downloadUrl, imageLoader = imageLoader)
                }
                (entry.kind == FileKind.Text || entry.kind == FileKind.Code) && text != null -> {
                    SelectionContainer {
                        Text(
                            text = text.content,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        )
                    }
                }
                entry.kind == FileKind.Pdf && pdfFile != null -> {
                    PdfPreview(file = pdfFile)
                }
                (entry.kind == FileKind.Video || entry.kind == FileKind.Audio) && downloadUrl != null -> {
                    val player = remember(downloadUrl, mediaReady) { playerFactory() }
                    DisposableEffect(player) {
                        onDispose { player?.release() }
                    }
                    LaunchedEffect(player, downloadUrl) {
                        if (player != null) {
                            player.setMediaItem(MediaItem.fromUri(downloadUrl))
                            player.prepare()
                            player.playWhenReady = true
                        }
                    }
                    if (player != null) {
                        AndroidView(
                            factory = { context ->
                                PlayerView(context).apply {
                                    useController = true
                                    this.player = player
                                }
                            },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = stringResource(R.string.preview_unsupported), color = Color.White)
                        }
                    }
                }
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.preview_unsupported),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPreview(file: File) {
    val renderer by produceState<PdfRenderer?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
            }.getOrNull()
        }
    }
    val current = renderer
    DisposableEffect(current) {
        onDispose { current?.close() }
    }
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.preview_unsupported), color = Color.White)
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF303030)),
    ) {
        items(current.pageCount) { index ->
            PdfPage(renderer = current, index = index)
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, index: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, renderer, index) {
        value = withContext(Dispatchers.IO) {
            synchronized(renderer) {
                runCatching {
                    val page = renderer.openPage(index)
                    try {
                        val width = (page.width * 1.5f).toInt().coerceAtLeast(1)
                        val height = (page.height * 1.5f).toInt().coerceAtLeast(1)
                        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        result.eraseColor(android.graphics.Color.WHITE)
                        page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        result
                    } finally {
                        page.close()
                    }
                }.getOrNull()
            }
        }
    }
    val image = bitmap
    if (image == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        )
    }
}

@Composable
private fun ZoomableImage(url: String, imageLoader: ImageLoader) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}
