package filehub.android.ui
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.text.format.Formatter
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin
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
import filehub.android.data.api.DeleteItemDto
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
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.dialog_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = clearFocusOnDone {
                    val trimmed = value.trim()
                    if (trimmed.isNotBlank()) onConfirm(trimmed)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
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
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (preview.requiresCode) {
        LaunchedEffect(preview.token) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = {
            Column {
                Text(
                    text = stringResource(R.string.delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.delete_body,
                        preview.items.size,
                        Formatter.formatShortFileSize(context, totalBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(preview.items, key = { it.relative }) { item ->
                        DeleteItemRow(item = item)
                    }
                }
                if (preview.requiresCode) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.delete_code_prompt),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { value ->
                            code = value.filter(Char::isDigit).take(4)
                        },
                        placeholder = {
                            Text(
                                text = preview.confirmCode,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = clearFocusOnDone(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
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
                enabled = !loading && (!preview.requiresCode || code.length == 4),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.delete_confirm))
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
private fun DeleteItemRow(item: DeleteItemDto) {
    val name = item.relative.substringAfterLast('/')
    Text(
        text = name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
fun ShareCreateDialog(
    entryName: String,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (password: String?, expiresAt: Long?, readOnly: Boolean) -> Unit,
) {
    var readOnly by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var expiryDays by remember { mutableStateOf<Int?>(7) }
    var expiryMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FileNameText(entryName, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.share_read_only),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppSwitch(checked = readOnly, onCheckedChange = { readOnly = it })
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.share_password_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = clearFocusOnDone(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    TextButton(onClick = { expiryMenu = true }) {
                        Text(
                            stringResource(R.string.share_expiry) + ": " + expiryLabel(expiryDays),
                        )
                    }
                    DropdownMenu(expanded = expiryMenu, onDismissRequest = { expiryMenu = false }) {
                        listOf(1, 7, 30, null).forEach { days ->
                            DropdownMenuItem(
                                text = { Text(expiryLabel(days)) },
                                onClick = {
                                    expiryDays = days
                                    expiryMenu = false
                                },
                            )
                        }
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val expiresAt = expiryDays?.let {
                        System.currentTimeMillis() / 1000 + it * 86_400L
                    }
                    onCreate(password.takeIf { it.isNotBlank() }, expiresAt, readOnly)
                },
                enabled = !loading,
            ) {
                Text(stringResource(R.string.share_create))
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
private fun expiryLabel(days: Int?): String = when (days) {
    1 -> stringResource(R.string.share_expiry_1d)
    7 -> stringResource(R.string.share_expiry_7d)
    30 -> stringResource(R.string.share_expiry_30d)
    else -> stringResource(R.string.share_expiry_never)
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PreviewDialog(
    entry: FileEntry,
    imageEntries: List<FileEntry>,
    session: Long,
    imageLoader: ImageLoader?,
    imageUrlFor: (FileEntry) -> String?,
    thumbnailUrlFor: (FileEntry) -> String?,
    downloadUrlFor: (FileEntry) -> String?,
    text: TextFileDto?,
    pdfFile: File?,
    loading: Boolean,
    mediaReady: Int,
    error: String?,
    playerFactory: () -> ExoPlayer?,
    onPageChanged: (FileEntry) -> Unit,
    onLongPress: () -> Unit,
    onEdit: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val isMedia = entry.kind == FileKind.Image ||
        entry.kind == FileKind.Video ||
        entry.kind == FileKind.Audio ||
        entry.kind == FileKind.Pdf
    val overlayBar = entry.kind == FileKind.Image ||
        entry.kind == FileKind.Video ||
        entry.kind == FileKind.Audio
    val background = if (isMedia) Color.Black else MaterialTheme.colorScheme.background
    val contentColor = if (isMedia) Color.White else MaterialTheme.colorScheme.onBackground
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = remember(context) { context.findActivity() }
    val originalOrientation = remember(activity) { activity?.requestedOrientation }
    var controlsVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showOriginal by remember(session) { mutableStateOf(false) }
    var originalError by remember(session) { mutableStateOf(false) }

    val pages = if (entry.kind == FileKind.Image) {
        imageEntries.ifEmpty { listOf(entry) }
    } else {
        listOf(entry)
    }
    val initialPage = pages.indexOfFirst {
        it.name == entry.name && it.modified == entry.modified
    }.coerceAtLeast(0)
    val pagerState = key(session) {
        rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
    }
    val currentEntry = pages.getOrElse(pagerState.currentPage) { entry }

    LaunchedEffect(pages.size) {
        if (pagerState.currentPage > pages.lastIndex) {
            pagerState.scrollToPage(pages.lastIndex.coerceAtLeast(0))
        }
    }

    LaunchedEffect(currentEntry.name, currentEntry.modified) {
        showOriginal = false
        originalError = false
    }

    LaunchedEffect(originalError) {
        if (originalError) {
            delay(2500)
            originalError = false
        }
    }

    LaunchedEffect(pages) {
        val index = pages.indexOfFirst {
            it.name == entry.name && it.modified == entry.modified
        }
        if (index >= 0 && pagerState.currentPage != index) {
            pagerState.scrollToPage(index)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        zoomed = false
        val page = pages.getOrNull(pagerState.currentPage)
        if (entry.kind == FileKind.Image && page != null && page != entry) {
            onPageChanged(page)
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity != null && originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        val insetsController = (dialogWindow ?: activity?.window)?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).also { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        LaunchedEffect(entry.kind, controlsVisible, insetsController) {
            if (entry.kind == FileKind.Image && insetsController != null) {
                if (controlsVisible) {
                    insetsController.show(WindowInsetsCompat.Type.statusBars())
                } else {
                    insetsController.hide(WindowInsetsCompat.Type.statusBars())
                }
            }
        }
        DisposableEffect(insetsController) {
            onDispose {
                insetsController?.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
        ) {
            if (overlayBar) {
                PreviewBody(
                    entry = entry,
                    pages = pages,
                    pagerState = pagerState,
                    imageLoader = imageLoader,
                    imageUrlFor = imageUrlFor,
                    thumbnailUrlFor = thumbnailUrlFor,
                    downloadUrlFor = downloadUrlFor,
                    text = text,
                    pdfFile = pdfFile,
                    loading = loading,
                    mediaReady = mediaReady,
                    error = error,
                    contentColor = contentColor,
                    playerFactory = playerFactory,
                    original = showOriginal && entry.kind == FileKind.Image,
                    onOriginalError = { originalError = true },
                    pagerEnabled = !zoomed,
                    onZoomChanged = { zoomed = it },
                    onLongPress = onLongPress,
                    onImageTap = { controlsVisible = !controlsVisible },
                    onControlsVisibilityChanged = { controlsVisible = it },
                    modifier = Modifier.fillMaxSize(),
                )
                val showBar = when (entry.kind) {
                    FileKind.Video, FileKind.Image -> controlsVisible
                    else -> true
                }
                AnimatedVisibility(
                    visible = showBar,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(160)),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    PreviewTopBar(
                        entry = currentEntry,
                        contentColor = contentColor,
                        showDetails = entry.kind == FileKind.Image,
                        showOrientation = entry.kind == FileKind.Video && activity != null,
                        showEdit = entry.kind == FileKind.Text ||
                            entry.kind == FileKind.Code ||
                            entry.isMarkdown,
                        onEdit = onEdit ?: {},
                        onDetails = onLongPress,
                        onToggleOrientation = {
                            activity?.requestedOrientation = if (landscape) {
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        },
                        onClose = onClose,
                        scrim = true,
                    )
                }
                if (entry.kind == FileKind.Image) {
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(16.dp),
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    if (!showOriginal && entry.size > ORIGINAL_MAX_BYTES) {
                                        originalError = true
                                    } else {
                                        showOriginal = !showOriginal
                                    }
                                },
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.62f),
                            contentColor = Color.White,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        if (showOriginal) {
                                            R.string.preview_show_preview
                                        } else {
                                            R.string.preview_show_original
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    if (originalError) {
                        Text(
                            text = stringResource(R.string.preview_original_failed),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    PreviewTopBar(
                        entry = entry,
                        contentColor = contentColor,
                        showDetails = false,
                        showOrientation = false,
                        showEdit = entry.kind == FileKind.Text ||
                            entry.kind == FileKind.Code ||
                            entry.isMarkdown,
                        onEdit = onEdit ?: {},
                        onDetails = onLongPress,
                        onToggleOrientation = {},
                        onClose = onClose,
                        scrim = false,
                    )
                    PreviewBody(
                        entry = entry,
                        pages = pages,
                        pagerState = pagerState,
                        imageLoader = imageLoader,
                        imageUrlFor = imageUrlFor,
                        thumbnailUrlFor = thumbnailUrlFor,
                        downloadUrlFor = downloadUrlFor,
                        text = text,
                        pdfFile = pdfFile,
                        loading = loading,
                        mediaReady = mediaReady,
                        error = error,
                        contentColor = contentColor,
                        playerFactory = playerFactory,
                        original = false,
                        onOriginalError = {},
                        pagerEnabled = false,
                        onZoomChanged = {},
                        onLongPress = onLongPress,
                        onImageTap = {},
                        onControlsVisibilityChanged = {},
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewTopBar(
    entry: FileEntry,
    contentColor: Color,
    showDetails: Boolean,
    showOrientation: Boolean,
    showEdit: Boolean,
    onEdit: () -> Unit,
    onDetails: () -> Unit,
    onToggleOrientation: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scrim: Boolean,
) {
    val barModifier = modifier
        .fillMaxWidth()
        .then(
            if (scrim) {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                    ),
                )
            } else {
                Modifier.background(MaterialTheme.colorScheme.background)
            },
        )
        .statusBarsPadding()
        .padding(horizontal = 4.dp, vertical = 2.dp)
    Row(
        modifier = barModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.preview_close),
                tint = contentColor,
            )
        }
        FileNameText(
            name = entry.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
        )
        if (showOrientation) {
            IconButton(onClick = onToggleOrientation) {
                Icon(
                    painter = painterResource(R.drawable.ic_screen_rotation),
                    contentDescription = stringResource(R.string.preview_rotate),
                    tint = contentColor,
                )
            }
        }
        if (showEdit) {
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.editor_edit),
                    tint = contentColor,
                )
            }
        }
        if (showDetails) {
            IconButton(onClick = onDetails) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.file_details),
                    tint = contentColor,
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PreviewBody(
    entry: FileEntry,
    pages: List<FileEntry>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    imageLoader: ImageLoader?,
    imageUrlFor: (FileEntry) -> String?,
    thumbnailUrlFor: (FileEntry) -> String?,
    downloadUrlFor: (FileEntry) -> String?,
    text: TextFileDto?,
    pdfFile: File?,
    loading: Boolean,
    mediaReady: Int,
    error: String?,
    contentColor: Color,
    playerFactory: () -> ExoPlayer?,
    original: Boolean,
    onOriginalError: () -> Unit,
    pagerEnabled: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    onImageTap: () -> Unit,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = contentColor)
            }
            error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = error, color = contentColor)
            }
            entry.kind == FileKind.Image && imageLoader != null -> {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = pagerEnabled,
                    beyondViewportPageCount = 1,
                    key = { index ->
                        val page = pages.getOrNull(index)
                        if (page == null) {
                            "missing:$index"
                        } else {
                            "${page.name}:${page.modified}:${page.size}"
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    val page = pages.getOrNull(pageIndex)
                    if (page != null) {
                        val fullUrl = if (original) {
                            downloadUrlFor(page)
                        } else {
                            imageUrlFor(page) ?: downloadUrlFor(page)
                        }
                        ZoomableImage(
                            url = fullUrl.orEmpty(),
                            thumbnailUrl = if (original) null else thumbnailUrlFor(page),
                            imageLoader = imageLoader,
                            resetSignal = pagerState.currentPage,
                            fullSize = original,
                            onScaleChanged = onZoomChanged,
                            onLongPress = onLongPress,
                            onTap = onImageTap,
                            onError = if (original) onOriginalError else null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            text != null -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (text.lossy) {
                        Text(
                            text = stringResource(R.string.text_lossy_hint),
                            color = Color(0xFF3B2F00),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFE082))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    val bodyModifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                    if (entry.isMarkdown) {
                        MarkdownPreview(
                            markdown = text.content,
                            textColor = contentColor,
                            linkColor = MaterialTheme.colorScheme.primary,
                            modifier = bodyModifier,
                        )
                    } else if (entry.kind == FileKind.Code) {
                        CodePreview(
                            content = text.content,
                            contentColor = contentColor,
                            modifier = bodyModifier,
                        )
                    } else {
                        val truncated = text.content.length > CODE_PREVIEW_MAX_CHARS
                        val shown = if (truncated) {
                            text.content.take(CODE_PREVIEW_MAX_CHARS)
                        } else {
                            text.content
                        }
                        Column(modifier = bodyModifier) {
                            if (truncated) {
                                Text(
                                    text = stringResource(R.string.text_preview_truncated),
                                    color = contentColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            SelectionContainer {
                                Text(
                                    text = shown,
                                    color = contentColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            entry.kind == FileKind.Pdf && pdfFile != null -> {
                PdfPreview(file = pdfFile)
            }
            (entry.kind == FileKind.Video || entry.kind == FileKind.Audio) && downloadUrlFor(entry) != null -> {
                val url = downloadUrlFor(entry).orEmpty()
                val player = remember(url, mediaReady) { playerFactory() }
                DisposableEffect(player) {
                    onDispose { player?.release() }
                }
                LaunchedEffect(player, url) {
                    if (player != null) {
                        player.setMediaItem(MediaItem.fromUri(url))
                        player.prepare()
                        player.playWhenReady = true
                    }
                }
                if (player != null) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                useController = true
                                controllerShowTimeoutMs = 3500
                                setControllerVisibilityListener(
                                    object : PlayerView.ControllerVisibilityListener {
                                        override fun onVisibilityChanged(visibility: Int) {
                                            onControlsVisibilityChanged(visibility == View.VISIBLE)
                                        }
                                    },
                                )
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
                        Text(text = stringResource(R.string.preview_unsupported), color = contentColor)
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
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
fun MarkdownPreview(
    markdown: String,
    textColor: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }
    val padding = with(LocalDensity.current) { 16.dp.roundToPx() }
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor.toArgb())
                setLinkTextColor(linkColor.toArgb())
                textSize = 15f
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setPadding(padding, padding, padding, padding)
                movementMethod = ScrollingMovementMethod()
                setTextIsSelectable(true)
            }
        },
        update = { markwon.setMarkdown(it, markdown) },
        modifier = modifier.fillMaxSize(),
    )
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

private data class PdfPageBitmap(
    val bitmap: Bitmap?,
    val width: Int,
    val height: Int,
    val failed: Boolean = false,
)

@Composable
private fun PdfPage(renderer: PdfRenderer, index: Int) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val availableWidthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val pageState by produceState<PdfPageBitmap?>(
            initialValue = null,
            renderer,
            index,
            availableWidthPx,
        ) {
            value = withContext(Dispatchers.IO) {
                synchronized(renderer) {
                    runCatching {
                        val page = renderer.openPage(index)
                        try {
                            val pageWidth = page.width.coerceAtLeast(1)
                            val pageHeight = page.height.coerceAtLeast(1)
                            var scale = (availableWidthPx.toFloat() / pageWidth).coerceIn(0.5f, 3f)
                            val maxPixels = 4096
                            if (pageWidth * scale > maxPixels) {
                                scale = maxPixels.toFloat() / pageWidth
                            }
                            val width = (pageWidth * scale).toInt().coerceAtLeast(1)
                            val height = (pageHeight * scale).toInt().coerceAtLeast(1)
                            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            result.eraseColor(android.graphics.Color.WHITE)
                            page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            PdfPageBitmap(result, pageWidth, pageHeight)
                        } finally {
                            page.close()
                        }
                    }.getOrElse {
                        PdfPageBitmap(null, 1, 1, failed = true)
                    }
                }
            }
        }
        val page = pageState
        when {
            page == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
            page.failed || page.bitmap == null -> Text(
                text = stringResource(R.string.preview_unsupported),
                color = Color.White,
                modifier = Modifier.padding(16.dp),
            )
            else -> {
                DisposableEffect(page.bitmap) {
                    onDispose { page.bitmap.recycle() }
                }
                Image(
                    bitmap = page.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(page.width.toFloat() / page.height)
                        .padding(horizontal = 2.dp),
                )
            }
        }
    }
}

private const val CODE_PREVIEW_MAX_CHARS = 256 * 1024

@Composable
private fun CodePreview(
    content: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val truncated = content.length > CODE_PREVIEW_MAX_CHARS
    val shown = if (truncated) content.take(CODE_PREVIEW_MAX_CHARS) else content
    val lines = remember(shown) { shown.split('\n') }
    val digits = remember(lines) { lines.size.toString().length }
    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    val gutterWidth = (digits * 8 + 16).dp
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (truncated) {
            item(key = "truncated") {
                Text(
                    text = stringResource(R.string.code_preview_truncated),
                    color = contentColor,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        itemsIndexed(lines) { index, line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = (index + 1).toString(),
                    style = codeStyle,
                    color = contentColor.copy(alpha = 0.55f),
                    textAlign = TextAlign.End,
                    softWrap = false,
                    modifier = Modifier
                        .width(gutterWidth)
                        .padding(end = 8.dp),
                )
                SelectionContainer {
                    Text(
                        text = line.ifEmpty { " " },
                        style = codeStyle,
                        color = contentColor,
                        softWrap = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                    )
                }
            }
        }
    }
}

private const val ORIGINAL_MAX_BYTES = 64L * 1024 * 1024

@Composable
private fun ZoomableImage(
    url: String,
    thumbnailUrl: String?,
    imageLoader: ImageLoader,
    resetSignal: Any?,
    fullSize: Boolean,
    onScaleChanged: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
    onError: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember(url) { mutableStateOf(IntSize.Zero) }
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(200),
        label = "preview-image-alpha",
    )
    val request = remember(url, fullSize) {
        ImageRequest.Builder(context)
            .data(url)
            .apply { if (!fullSize) size(2048) }
            .build()
    }

    LaunchedEffect(resetSignal, url) {
        zoomJob?.cancel()
        scale = 1f
        offset = Offset.Zero
        onScaleChanged(false)
    }

    fun animateZoom(
        targetScale: Float,
        targetOffset: Offset,
        onFinished: (() -> Unit)? = null,
    ) {
        val startScale = scale
        val startOffset = offset
        zoomJob = scope.launch {
            animate(0f, 1f, animationSpec = tween(220)) { fraction, _ ->
                scale = startScale + (targetScale - startScale) * fraction
                offset = startOffset + (targetOffset - startOffset) * fraction
            }
            onFinished?.invoke()
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .background(Color.Black),
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(
                            radius = 30.dp,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f)),
                )
            }
        }

        if (thumbnailUrl == null && !loaded && !failed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        if (failed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.preview_unsupported), color = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tap ->
                            zoomJob?.cancel()
                            if (scale > 1.01f) {
                                onScaleChanged(false)
                                animateZoom(1f, Offset.Zero)
                            } else {
                                val target = targetZoom(scale, imageSize, containerSize)
                                val center = Offset(
                                    containerSize.width / 2f,
                                    containerSize.height / 2f,
                                )
                                val targetOffset = clampOffset(
                                    offset = tap - center - (tap - center - offset) * (target / scale),
                                    scale = target,
                                    imageSize = imageSize,
                                    containerSize = containerSize,
                                )
                                onScaleChanged(true)
                                animateZoom(target, targetOffset)
                            }
                        },
                        onLongPress = { onLongPress() },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            val center = Offset(
                                containerSize.width / 2f,
                                containerSize.height / 2f,
                            )
                            if (pressed >= 2) {
                                zoomJob?.cancel()
                                val oldScale = scale
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val newScale = (oldScale * zoom).coerceIn(1f, 8f)
                                if (centroid != Offset.Unspecified && oldScale > 0f) {
                                    offset += (centroid - center - offset) * (1f - newScale / oldScale)
                                }
                                offset += pan
                                scale = newScale
                                offset = clampOffset(offset, scale, imageSize, containerSize)
                                event.changes.forEach { it.consume() }
                                onScaleChanged(scale > 1.01f)
                            } else if (scale > 1f && pressed == 1) {
                                zoomJob?.cancel()
                                val pan = event.calculatePan()
                                if (pan != Offset.Zero) {
                                    offset = clampOffset(offset + pan, scale, imageSize, containerSize)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (scale <= 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val clamped = clampOffset(offset, scale, imageSize, containerSize)
                            if (clamped != offset) {
                                animateZoom(scale, clamped)
                            }
                        }
                        onScaleChanged(scale > 1.01f)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = imageAlpha,
                onSuccess = { state ->
                    val image = state.result.image
                    imageSize = IntSize(image.width, image.height)
                    loaded = true
                    failed = false
                },
                onError = {
                    failed = true
                    loaded = false
                    onError?.invoke()
                },
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
}

private fun targetZoom(scale: Float, imageSize: IntSize, containerSize: IntSize): Float {
    if (imageSize.width <= 0 || imageSize.height <= 0 ||
        containerSize.width <= 0 || containerSize.height <= 0
    ) {
        return 2f
    }
    val fit = minOf(
        containerSize.width.toFloat() / imageSize.width,
        containerSize.height.toFloat() / imageSize.height,
    )
    if (fit <= 0f) return 2f
    return (1.5f / fit).coerceIn(1.5f, 8f)
}

private fun clampOffset(
    offset: Offset,
    scale: Float,
    imageSize: IntSize,
    containerSize: IntSize,
): Offset {
    if (scale <= 1f || imageSize.width <= 0 || imageSize.height <= 0 ||
        containerSize.width <= 0 || containerSize.height <= 0
    ) {
        return Offset.Zero
    }
    val fit = minOf(
        containerSize.width.toFloat() / imageSize.width,
        containerSize.height.toFloat() / imageSize.height,
    )
    val contentWidth = imageSize.width * fit * scale
    val contentHeight = imageSize.height * fit * scale
    val maxX = ((contentWidth - containerSize.width) / 2f).coerceAtLeast(0f)
    val maxY = ((contentHeight - containerSize.height) / 2f).coerceAtLeast(0f)
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
}

@Composable
fun TargetPathDialog(
    title: String,
    roots: List<filehub.android.core.model.FileRoot>,
    initialRootId: Long?,
    initialPath: String,
    onDismiss: () -> Unit,
    onConfirm: (rootId: Long, path: String) -> Unit,
) {
    var rootId by remember(initialRootId) { mutableStateOf(initialRootId ?: roots.firstOrNull()?.id) }
    var path by remember(initialPath) { mutableStateOf(initialPath) }
    var menu by remember { mutableStateOf(false) }
    val root = roots.firstOrNull { it.id == rootId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    TextButton(onClick = { menu = true }) {
                        Text(
                            stringResource(R.string.target_root) + ": " + (root?.alias ?: "—"),
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        roots.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.alias) },
                                onClick = {
                                    rootId = item.id
                                    menu = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.target_path)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = clearFocusOnDone(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    rootId?.let { onConfirm(it, path.trim('/')) }
                },
                enabled = rootId != null,
            ) {
                Text(stringResource(R.string.dialog_save))
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
fun ImagePropertiesSection(
    properties: filehub.android.core.model.FileProperties?,
    loading: Boolean,
    error: String?,
    fallbackFormat: String? = null,
    onRetry: (() -> Unit)?,
) {
    when {
        loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.preview_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error != null -> Column {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
        properties?.image != null -> {
            val image = properties.image
            if (image.width != null && image.height != null) {
                DetailLine(
                    label = stringResource(R.string.image_dimensions),
                    value = "${image.width} × ${image.height}",
                )
            }
            (image.format ?: fallbackFormat)?.takeIf { it.isNotBlank() }?.let { format ->
                DetailLine(label = stringResource(R.string.image_format), value = format)
            }
            Text(
                text = stringResource(R.string.image_exif),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (image.exif.isEmpty()) {
                Text(
                    text = stringResource(R.string.image_no_exif),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    image.exif.forEach { (key, value) ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = exifLabel(key),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = exifValue(key, value),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.4f),
                            )
                        }
                    }
                }
            }
        }
        properties != null -> {
            fallbackFormat?.takeIf { it.isNotBlank() }?.let { format ->
                DetailLine(label = stringResource(R.string.image_format), value = format)
            }
            Text(
                text = stringResource(R.string.image_no_exif),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun exifLabel(key: String): String = when (key.lowercase()) {
    "make" -> stringResource(R.string.exif_make)
    "model" -> stringResource(R.string.exif_model)
    "lens" -> stringResource(R.string.exif_lens)
    "taken_at" -> stringResource(R.string.exif_taken_at)
    "exposure" -> stringResource(R.string.exif_exposure)
    "f_number" -> stringResource(R.string.exif_aperture)
    "iso" -> "ISO"
    "focal_length" -> stringResource(R.string.exif_focal_length)
    "orientation" -> stringResource(R.string.exif_orientation)
    else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun exifValue(key: String, value: String): String {
    if (key.lowercase() != "orientation") return value
    return when (value.trim()) {
        "1" -> stringResource(R.string.exif_orientation_normal)
        "2" -> stringResource(R.string.exif_orientation_flip_h)
        "3" -> stringResource(R.string.exif_orientation_180)
        "4" -> stringResource(R.string.exif_orientation_flip_v)
        "5" -> stringResource(R.string.exif_orientation_90_flip)
        "6" -> stringResource(R.string.exif_orientation_90)
        "7" -> stringResource(R.string.exif_orientation_270_flip)
        "8" -> stringResource(R.string.exif_orientation_270)
        else -> value
    }
}

@Composable
fun ImageActionsDialog(
    entry: FileEntry,
    properties: filehub.android.core.model.FileProperties?,
    loading: Boolean,
    error: String?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.file_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    Formatter.formatShortFileSize(context, entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ImagePropertiesSection(
                    properties = properties,
                    loading = loading,
                    error = error,
                    fallbackFormat = entry.mimeType,
                    onRetry = null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(stringResource(R.string.image_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.file_close))
            }
        },
    )
}

@Composable
fun CompactDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp,
            vertical = 4.dp,
        ),
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
