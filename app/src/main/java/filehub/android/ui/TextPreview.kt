package filehub.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import filehub.android.R
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileKind
import filehub.android.data.api.TextFileDto
import filehub.android.ui.theme.LocalWarningColors

/**
 * Reader-style text preview: a document header with real metadata, a padded
 * surface for the body and a persistent action bar. Deliberately separate from
 * the media lightbox, which needs a black backdrop and gesture chrome.
 */
@Composable
fun TextPreviewScaffold(
    entry: FileEntry,
    text: TextFileDto?,
    loading: Boolean,
    error: String?,
    encoding: String?,
    onReloadEncoding: (String?) -> Unit,
    onRetry: () -> Unit,
    onEdit: (() -> Unit)?,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val themeMode = remember(context) { EditorThemePreference.read(context) }
    val palette = rememberEditorPalette(themeMode)
    val warning = LocalWarningColors.current
    val copiedLabel = stringResource(R.string.preview_copied)
    val code = entry.kind == FileKind.Code || isCodeFile(entry.name)
    val markdown = entry.isMarkdown
    var menuOpen by remember { mutableStateOf(false) }
    var encodingDialog by remember { mutableStateOf(false) }
    var wrap by remember(entry.name, entry.modified) {
        mutableStateOf(!isCodeFile(entry.name))
    }
    val currentEncoding = encoding ?: text?.encoding

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.preview_close))
            }
            FileNameText(
                name = entry.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.file_more),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preview_share)) },
                        onClick = {
                            menuOpen = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preview_details)) },
                        onClick = {
                            menuOpen = false
                            onDetails()
                        },
                    )
                    if (!markdown) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (wrap) R.string.preview_no_wrap else R.string.preview_wrap,
                                    ),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                wrap = !wrap
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preview_reload)) },
                        onClick = {
                            menuOpen = false
                            onRetry()
                        },
                    )
                }
            }
        }

        DocumentHeader(
            entry = entry,
            text = text,
            encoding = currentEncoding,
            onEncodingClick = { encodingDialog = true },
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = palette.background,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (text?.lossy == true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(warning.container)
                            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.text_lossy_hint),
                            color = warning.content,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { encodingDialog = true }) {
                            Text(
                                text = stringResource(R.string.preview_try_encoding),
                                color = warning.content,
                            )
                        }
                    }
                }
                if (error != null && text != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        loading && text == null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                        text == null -> StatusMessage(
                            icon = Icons.Filled.Warning,
                            title = error ?: stringResource(R.string.preview_unsupported),
                            actionLabel = stringResource(R.string.common_retry),
                            onAction = onRetry,
                            modifier = Modifier.fillMaxSize(),
                        )
                        markdown -> MarkdownPreview(
                            markdown = text.content,
                            textColor = palette.text,
                            linkColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize(),
                        )
                        code -> CodePreviewBlock(
                            content = text.content,
                            language = syntaxLanguageFor(entry.name),
                            palette = palette,
                            wrap = wrap,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> PlainPreviewBlock(
                            content = text.content,
                            palette = palette,
                            wrap = wrap,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = {
                        text?.let { file ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(entry.name, file.content),
                            )
                            Toast.makeText(
                                context,
                                copiedLabel,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    enabled = text != null,
                ) {
                    Text(stringResource(R.string.preview_copy_all))
                }
                TextButton(
                    onClick = { encodingDialog = true },
                    enabled = text != null,
                ) {
                    Text(encodingLabel(currentEncoding))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.preview_download))
                }
                if (onEdit != null) {
                    Button(onClick = onEdit) {
                        Text(stringResource(R.string.editor_edit))
                    }
                }
            }
        }
    }

    if (encodingDialog) {
        EncodingPickerDialog(
            selected = currentEncoding,
            onSelect = {
                encodingDialog = false
                onReloadEncoding(it)
            },
            onDismiss = { encodingDialog = false },
        )
    }
}

@Composable
private fun DocumentHeader(
    entry: FileEntry,
    text: TextFileDto?,
    encoding: String?,
    onEncodingClick: () -> Unit,
) {
    val context = LocalContext.current
    val warning = LocalWarningColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = fileIconColor(entry.kind).copy(alpha = 0.14f),
            ) {
                Icon(
                    painter = painterResource(fileIconRes(entry.kind)),
                    contentDescription = null,
                    tint = fileIconColor(entry.kind),
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = Formatter.formatShortFileSize(context, text?.size ?: entry.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = relativeTimeText(entry.modified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaChip(
                text = encodingLabel(encoding),
                onClick = onEncodingClick,
            )
            MetaChip(
                text = when (text?.lineEnding) {
                    "crlf" -> "CRLF"
                    "mixed" -> stringResource(R.string.preview_mixed_eol)
                    else -> "LF"
                },
            )
            MetaChip(text = stringResource(R.string.editor_chars, text?.content?.length ?: 0))
            if (text?.bom == true) MetaChip(text = "BOM")
            if (text?.lossy == true) {
                Surface(shape = RoundedCornerShape(50), color = warning.container) {
                    Text(
                        text = stringResource(R.string.preview_lossy_chip),
                        color = warning.content,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
internal fun PlainPreviewBlock(
    content: String,
    palette: EditorPalette,
    wrap: Boolean,
    modifier: Modifier = Modifier,
) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    Box(
        modifier = modifier
            .verticalScroll(vertical)
            .then(if (!wrap) Modifier.horizontalScroll(horizontal) else Modifier),
    ) {
        SelectionContainer {
            Text(
                text = content,
                color = palette.text,
                fontFamily = CodeFontFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                softWrap = wrap,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
internal fun CodePreviewBlock(
    content: String,
    language: dev.snipme.highlights.model.SyntaxLanguage,
    palette: EditorPalette,
    wrap: Boolean,
    modifier: Modifier = Modifier,
) {
    val truncated = content.length > CODE_PREVIEW_MAX_CHARS
    val shown = if (truncated) content.take(CODE_PREVIEW_MAX_CHARS) else content
    val annotated = rememberHighlightedText(
        text = shown,
        language = language,
        theme = palette.syntax,
        enabled = language != dev.snipme.highlights.model.SyntaxLanguage.DEFAULT,
    )
    if (!wrap) {
        val lines = remember(annotated) { annotated.text.split('\n').size }
        val numbers = remember(lines) { (1..lines).joinToString("\n") }
        val vertical = rememberScrollState()
        val horizontal = rememberScrollState()
        Column(modifier = modifier) {
            if (truncated) TruncatedNotice()
            Row(modifier = Modifier.verticalScroll(vertical)) {
                Text(
                    text = numbers,
                    color = palette.gutter,
                    fontFamily = CodeFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.End,
                    softWrap = false,
                    modifier = Modifier
                        .width((lines.toString().length * 8 + 20).dp)
                        .padding(end = 8.dp),
                )
                Box(modifier = Modifier.horizontalScroll(horizontal)) {
                    SelectionContainer {
                        Text(
                            text = annotated,
                            color = palette.text,
                            fontFamily = CodeFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            softWrap = false,
                            modifier = Modifier.padding(end = 20.dp),
                        )
                    }
                }
            }
        }
        return
    }
    val lines = remember(annotated) { splitAnnotatedLines(annotated) }
    val digits = remember(lines) { lines.size.toString().length }
    LazyColumn(modifier = modifier) {
        if (truncated) {
            item(key = "truncated") { TruncatedNotice() }
        }
        itemsIndexed(lines) { index, line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = (index + 1).toString(),
                    color = palette.gutter,
                    fontFamily = CodeFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.End,
                    softWrap = false,
                    modifier = Modifier
                        .width((digits * 8 + 20).dp)
                        .padding(end = 8.dp),
                )
                SelectionContainer {
                    Text(
                        text = line,
                        color = palette.text,
                        fontFamily = CodeFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        softWrap = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TruncatedNotice() {
    Text(
        text = stringResource(R.string.code_preview_truncated),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

private fun splitAnnotatedLines(annotated: AnnotatedString): List<AnnotatedString> {
    val lines = ArrayList<AnnotatedString>()
    var start = 0
    annotated.text.forEachIndexed { index, value ->
        if (value == '\n') {
            lines += annotated.subSequence(start, index)
            start = index + 1
        }
    }
    lines += annotated.subSequence(start, annotated.length)
    return lines
}

@Composable
fun EncodingPickerDialog(
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_encoding)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                EncodingOptions.forEach { (value, label) ->
                    TextButton(
                        onClick = {
                            chosen = value
                            onSelect(value)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (value == null) {
                                stringResource(R.string.editor_encoding_auto)
                            } else {
                                label
                            },
                            color = if (value == chosen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                        if (value == chosen) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@Composable
fun UnsupportedPreviewDialog(
    preview: UnsupportedPreview,
    onOpenAsText: () -> Unit,
    onDownload: () -> Unit,
    onShare: (() -> Unit)?,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = preview.entry
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = fileIconColor(entry.kind).copy(alpha = 0.14f),
                    ) {
                        Icon(
                            painter = painterResource(fileIconRes(entry.kind)),
                            contentDescription = null,
                            tint = fileIconColor(entry.kind),
                            modifier = Modifier
                                .padding(11.dp)
                                .size(26.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.preview_unavailable_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = preview.message
                        ?: stringResource(R.string.preview_unavailable_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (preview.allowText) {
                        OutlinedButton(
                            onClick = onOpenAsText,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.preview_open_as_text_short),
                                maxLines = 1,
                            )
                        }
                    }
                    Button(
                        onClick = onDownload,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(stringResource(R.string.preview_download))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onShare != null) {
                        OutlinedButton(
                            onClick = onShare,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            Text(stringResource(R.string.preview_share))
                        }
                    }
                    OutlinedButton(
                        onClick = onDetails,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(stringResource(R.string.preview_details))
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    }
}
