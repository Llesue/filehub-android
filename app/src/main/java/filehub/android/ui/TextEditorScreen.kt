package filehub.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.R
import java.util.Locale

private val ENCODINGS = listOf(
    null to "auto",
    "utf-8" to "UTF-8",
    "gbk" to "GBK",
    "gb18030" to "GB18030",
    "big5" to "Big5",
    "shift_jis" to "Shift_JIS",
    "euc-kr" to "EUC-KR",
    "windows-1250" to "Windows-1250",
    "windows-1251" to "Windows-1251",
    "windows-1252" to "Windows-1252",
    "iso-8859-1" to "ISO-8859-1",
    "iso-8859-15" to "ISO-8859-15",
    "utf-16le" to "UTF-16LE",
    "utf-16be" to "UTF-16BE",
)

@Composable
fun TextEditorScreen(
    viewModel: TextEditorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var menuOpen by remember { mutableStateOf(false) }
    var encodingDialog by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var pendingEncoding by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(!viewModel.isMarkdown) }

    LaunchedEffect(state.loading) {
        if (!state.loading && fieldValue.text != state.content) {
            fieldValue = TextFieldValue(state.content)
        }
    }

    BackHandler(enabled = state.dirty) { discardDialog = true }

    fun back() {
        if (state.dirty) discardDialog = true else onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::back) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
            FileNameText(
                name = viewModel.pathLabel(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            if (viewModel.isMarkdown) {
                TextButton(onClick = { preview = !preview }) {
                    Text(
                        stringResource(if (preview) R.string.editor_edit else R.string.editor_preview),
                    )
                }
            }
            if (state.dirty) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                )
                Spacer(Modifier.size(8.dp))
            }
            if (state.saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(8.dp))
            }
            TextButton(
                onClick = { viewModel.save() },
                enabled = state.dirty && state.writable && !state.saving,
            ) {
                Text(stringResource(R.string.editor_save))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.file_more),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_encoding)) },
                        onClick = {
                            menuOpen = false
                            encodingDialog = true
                        },
                    )
                    if (!viewModel.isMarkdown) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (wrap) R.string.editor_no_wrap else R.string.editor_wrap,
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
                        text = { Text(stringResource(R.string.editor_save_utf8)) },
                        onClick = {
                            menuOpen = false
                            viewModel.useUtf8()
                        },
                    )
                }
            }
        }

        if (state.lossy) {
            Text(
                text = stringResource(R.string.text_lossy_hint),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        state.saveError?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::clearSaveError) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.load() }) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
                preview -> MarkdownPreview(
                    markdown = state.content,
                    textColor = MaterialTheme.colorScheme.onBackground,
                    linkColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> EditorBody(
                    state = state,
                    markdown = viewModel.isMarkdown,
                    wrap = wrap,
                    fieldValue = fieldValue,
                    onValueChange = { value ->
                        fieldValue = value
                        viewModel.setContent(value.text)
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.encoding ?: stringResource(R.string.editor_encoding_auto),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "EOL ${state.lineEnding.uppercase(Locale.ROOT)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.editor_chars, state.content.length),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.writable) {
                Text(
                    text = stringResource(
                        R.string.editor_read_only,
                        state.readOnlyReason ?: stringResource(R.string.editor_read_only_short),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (discardDialog) {
        AlertDialog(
            onDismissRequest = { discardDialog = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialog = false
                        val encoding = pendingEncoding
                        pendingEncoding = null
                        if (encoding != null) {
                            viewModel.load(encoding)
                        } else {
                            onBack()
                        }
                    },
                ) {
                    Text(stringResource(R.string.editor_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { discardDialog = false; pendingEncoding = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (state.conflict) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = { Text(stringResource(R.string.editor_conflict)) },
            text = { Text(stringResource(R.string.editor_conflict_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissConflict()
                        viewModel.save(force = true)
                    },
                ) {
                    Text(stringResource(R.string.editor_conflict_force))
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.dismissConflict()
                            viewModel.load()
                        },
                    ) {
                        Text(stringResource(R.string.editor_conflict_reload))
                    }
                    TextButton(onClick = viewModel::dismissConflict) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            },
        )
    }

    if (encodingDialog) {
        AlertDialog(
            onDismissRequest = { encodingDialog = false },
            title = { Text(stringResource(R.string.editor_encoding)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ENCODINGS.forEach { (value, label) ->
                        val selected = state.encoding == value ||
                            (value == null && state.encoding == null)
                        TextButton(
                            onClick = {
                                encodingDialog = false
                                if (state.dirty) {
                                    pendingEncoding = value
                                    discardDialog = true
                                } else {
                                    viewModel.load(value)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (value == null) {
                                    stringResource(R.string.editor_encoding_auto)
                                } else {
                                    label
                                },
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (selected) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { encodingDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
}

@Composable
private fun EditorBody(
    state: TextEditorState,
    markdown: Boolean,
    wrap: Boolean,
    fieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val lineCount = state.content.count { it == '\n' } + 1
    val numbers = remember(lineCount) {
        buildString {
            for (index in 1..lineCount) {
                if (index > 1) append('\n')
                append(index)
            }
        }
    }
    val bodyStyle: TextStyle = if (markdown) {
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(vertical)
            .padding(vertical = 8.dp),
    ) {
        if (!markdown) {
            Text(
                text = numbers,
                style = bodyStyle.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 24.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                textAlign = TextAlign.End,
                softWrap = false,
                modifier = Modifier.padding(start = 12.dp, end = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (!markdown && !wrap) Modifier.horizontalScroll(horizontal) else Modifier,
                ),
        ) {
            BasicTextField(
                value = fieldValue,
                onValueChange = onValueChange,
                readOnly = !state.writable,
                textStyle = bodyStyle.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = (if (markdown || wrap) Modifier.fillMaxWidth() else Modifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
