package filehub.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.R
import filehub.android.ui.theme.LocalWarningColors
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TextEditorScreen(
    viewModel: TextEditorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val wideLayout = configuration.screenWidthDp >= 720
    val split = viewModel.isMarkdown && wideLayout

    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val history = remember { EditorHistory() }
    var historyVersion by remember { mutableIntStateOf(0) }
    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var encodingDialog by remember { mutableStateOf(false) }
    var eolDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var pendingEncoding by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    var wrap by remember(viewModel.pathLabel()) {
        mutableStateOf(!isCodeFile(viewModel.pathLabel()))
    }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIndex by remember { mutableIntStateOf(0) }
    var findFocusRequest by remember { mutableIntStateOf(0) }
    var themeMode by remember { mutableStateOf(EditorThemePreference.read(context)) }
    val palette = rememberEditorPalette(themeMode)
    val warning = LocalWarningColors.current
    val language = remember(viewModel.pathLabel()) { syntaxLanguageFor(viewModel.pathLabel()) }
    val code = !viewModel.isMarkdown && language != dev.snipme.highlights.model.SyntaxLanguage.DEFAULT
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val textFocus = remember { FocusRequester() }
    val findFocus = remember { FocusRequester() }
    val matches = remember(state.content, findQuery) {
        if (findOpen) findTextMatches(state.content, findQuery) else emptyList()
    }
    val safeFindIndex = if (matches.isEmpty()) {
        0
    } else {
        findIndex.coerceIn(0, matches.lastIndex)
    }
    val matchColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val currentMatchColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val highlighted = rememberHighlightedText(
        text = state.content,
        language = language,
        theme = palette.syntax,
        enabled = code && state.content.length <= 160_000,
        debounceMs = 220,
        matches = if (findOpen && findQuery.isNotEmpty()) matches else emptyList(),
        currentMatch = safeFindIndex,
        matchColor = matchColor,
        currentMatchColor = currentMatchColor,
    )
    val transformation = remember(highlighted) {
        VisualTransformation { input ->
            val annotated = if (highlighted.text == input.text) {
                highlighted
            } else {
                AnnotatedString(input.text)
            }
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }

    LaunchedEffect(state.content) {
        if (fieldValue.text != state.content) {
            fieldValue = TextFieldValue(state.content, TextRange(state.content.length))
            history.clear()
            historyVersion++
        }
    }

    LaunchedEffect(state.justSaved) {
        if (state.justSaved) {
            delay(2_000)
            viewModel.clearJustSaved()
        }
    }

    LaunchedEffect(findFocusRequest) {
        if (findOpen) findFocus.requestFocus()
    }

    BackHandler(enabled = state.dirty) { discardDialog = true }

    fun back() {
        if (state.dirty) discardDialog = true else onBack()
    }

    fun snapshot(value: TextFieldValue) =
        EditorSnapshot(value.text, value.selection.start, value.selection.end)

    fun applySnapshot(snapshot: EditorSnapshot?) {
        if (snapshot == null) return
        fieldValue = TextFieldValue(
            snapshot.text,
            TextRange(snapshot.selectionStart, snapshot.selectionEnd),
        )
        viewModel.setContent(snapshot.text)
        textFocus.requestFocus()
    }

    fun undo() {
        applySnapshot(history.undo(snapshot(fieldValue)))
        historyVersion++
    }

    fun redo() {
        applySnapshot(history.redo(snapshot(fieldValue)))
        historyVersion++
    }

    fun scrollToMatch(offset: Int) {
        val layout = layoutResult ?: return
        val box = runCatching { layout.getBoundingBox(offset) }.getOrNull() ?: return
        scope.launch {
            val target = (vertical.value + box.top.toInt() - 120).coerceAtLeast(0)
            vertical.animateScrollTo(target)
            if (!wrap) {
                horizontal.animateScrollTo((horizontal.value + box.left.toInt() - 48).coerceAtLeast(0))
            }
        }
    }

    fun selectMatch(index: Int) {
        val match = matches.getOrNull(index) ?: return
        findIndex = index
        fieldValue = fieldValue.copy(selection = TextRange(match.start, match.end))
        scrollToMatch(match.start)
    }

    LaunchedEffect(findQuery) {
        findIndex = 0
        if (findQuery.isEmpty()) return@LaunchedEffect
        val first = findTextMatches(state.content, findQuery).firstOrNull() ?: return@LaunchedEffect
        fieldValue = fieldValue.copy(selection = TextRange(first.start, first.end))
        scrollToMatch(first.start)
    }

    fun insert(prefix: String, suffix: String) {
        if (!state.writable) return
        val start = minOf(fieldValue.selection.start, fieldValue.selection.end)
        val end = maxOf(fieldValue.selection.start, fieldValue.selection.end)
        val selected = fieldValue.text.substring(start, end)
        val inserted = prefix + selected + suffix
        val newText = fieldValue.text.replaceRange(start, end, inserted)
        val cursor = if (selected.isEmpty() && suffix.isNotEmpty()) {
            start + prefix.length
        } else {
            start + inserted.length
        }
        history.record(snapshot(fieldValue), System.currentTimeMillis())
        historyVersion++
        fieldValue = TextFieldValue(newText, TextRange(cursor, cursor))
        viewModel.setContent(newText)
        textFocus.requestFocus()
    }

    fun prefixLine(prefix: String) {
        if (!state.writable) return
        val text = fieldValue.text
        val start = minOf(fieldValue.selection.start, fieldValue.selection.end)
        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let {
            if (it < 0) 0 else it + 1
        }
        val present = text.startsWith(prefix, lineStart)
        val newText = if (present) {
            text.removeRange(lineStart, lineStart + prefix.length)
        } else {
            text.replaceRange(lineStart, lineStart, prefix)
        }
        val delta = if (present) -prefix.length else prefix.length
        val cursor = (fieldValue.selection.start + delta).coerceIn(0, newText.length)
        val selectionEnd = (fieldValue.selection.end + delta).coerceIn(0, newText.length)
        history.record(snapshot(fieldValue), System.currentTimeMillis())
        historyVersion++
        fieldValue = TextFieldValue(newText, TextRange(cursor, selectionEnd))
        viewModel.setContent(newText)
        textFocus.requestFocus()
    }

    fun insertLink() {
        if (!state.writable) return
        val start = minOf(fieldValue.selection.start, fieldValue.selection.end)
        val end = maxOf(fieldValue.selection.start, fieldValue.selection.end)
        val selected = fieldValue.text.substring(start, end)
        val inserted = if (selected.isEmpty()) "[](https://)" else "[$selected](https://)"
        val newText = fieldValue.text.replaceRange(start, end, inserted)
        val cursor = if (selected.isEmpty()) start + 1 else start + inserted.length
        history.record(snapshot(fieldValue), System.currentTimeMillis())
        historyVersion++
        fieldValue = TextFieldValue(newText, TextRange(cursor, cursor))
        viewModel.setContent(newText)
        textFocus.requestFocus()
    }

    val canUndo = remember(historyVersion) { history.canUndo }
    val canRedo = remember(historyVersion) { history.canRedo }
    val statusText = when {
        !state.writable -> stringResource(R.string.editor_read_only_short)
        state.saving -> stringResource(R.string.editor_saving)
        state.justSaved -> stringResource(R.string.editor_saved)
        state.dirty -> stringResource(R.string.editor_unsaved)
        else -> stringResource(R.string.editor_saved)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                Column(modifier = Modifier.weight(1f)) {
                    FileNameText(
                        name = viewModel.pathLabel(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.dirty) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.dirty) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                if (viewModel.isMarkdown && !split && !state.loading && state.error == null) {
                    TextButton(onClick = { preview = !preview }) {
                        Text(
                            stringResource(if (preview) R.string.editor_edit else R.string.editor_preview),
                        )
                    }
                }
                if (!state.loading && state.error == null && !preview) {
                    IconButton(
                        onClick = {
                            findOpen = true
                            findFocusRequest++
                        },
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.editor_find),
                        )
                    }
                }
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Button(
                    onClick = { viewModel.save() },
                    enabled = state.dirty && state.writable && !state.saving,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_find)) },
                            onClick = {
                                menuOpen = false
                                findOpen = true
                                findFocusRequest++
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_theme)) },
                            onClick = {
                                menuOpen = false
                                themeDialog = true
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

            if (findOpen) {
                Surface(color = palette.surface) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        OutlinedTextField(
                            value = findQuery,
                            onValueChange = { findQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(findFocus),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.editor_find)) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    selectMatch(stepMatch(findIndex, matches.size, true))
                                },
                            ),
                            trailingIcon = {
                                Text(
                                    text = if (findQuery.isBlank()) {
                                        ""
                                    } else if (matches.isEmpty()) {
                                        stringResource(R.string.editor_find_none)
                                    } else {
                                        "${safeFindIndex + 1}/${matches.size}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.muted,
                                )
                            },
                        )
                        TextButton(
                            onClick = { selectMatch(stepMatch(findIndex, matches.size, false)) },
                            enabled = matches.isNotEmpty(),
                        ) {
                            Text("↑")
                        }
                        TextButton(
                            onClick = { selectMatch(stepMatch(findIndex, matches.size, true)) },
                            enabled = matches.isNotEmpty(),
                        ) {
                            Text("↓")
                        }
                        IconButton(
                            onClick = {
                                findOpen = false
                                findQuery = ""
                                textFocus.requestFocus()
                            },
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.common_close),
                            )
                        }
                    }
                }
            }

            if (state.lossy) {
                Text(
                    text = stringResource(R.string.text_lossy_hint),
                    color = warning.content,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(warning.container)
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(palette.background),
            ) {
                val editorBody: @Composable (Modifier) -> Unit = { modifier ->
                    EditorBody(
                        state = state,
                        markdown = viewModel.isMarkdown,
                        code = code,
                        wrap = wrap,
                        fieldValue = fieldValue,
                        palette = palette,
                        vertical = vertical,
                        horizontal = horizontal,
                        transformation = transformation,
                        focusRequester = textFocus,
                        onValueChange = { value ->
                            if (value.text != fieldValue.text) {
                                history.record(snapshot(fieldValue), System.currentTimeMillis())
                                historyVersion++
                            }
                            fieldValue = value
                            viewModel.setContent(value.text)
                        },
                        onTextLayout = { layoutResult = it },
                        modifier = modifier,
                    )
                }
                when {
                    state.loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    state.error != null -> StatusMessage(
                        icon = Icons.Filled.Warning,
                        title = state.error.orEmpty(),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction = { viewModel.load() },
                        modifier = Modifier.fillMaxSize(),
                    )
                    split -> Row(modifier = Modifier.fillMaxSize()) {
                        editorBody(Modifier.weight(1f))
                        VerticalDivider(color = palette.gutter.copy(alpha = 0.35f))
                        MarkdownPreview(
                            markdown = state.content,
                            textColor = palette.text,
                            linkColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    preview -> MarkdownPreview(
                        markdown = state.content,
                        textColor = palette.text,
                        linkColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> editorBody(Modifier.fillMaxSize())
                }
            }

            if (!preview && state.writable && !state.loading && state.error == null) {
                EditorToolbar(
                    markdown = viewModel.isMarkdown,
                    palette = palette,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onInsert = ::insert,
                    onPrefix = ::prefixLine,
                    onLink = ::insertLink,
                    onUndo = ::undo,
                    onRedo = ::redo,
                )
            }

            Surface(color = palette.surface, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditorPill(
                        text = encodingLabel(state.encoding),
                        palette = palette,
                        onClick = { encodingDialog = true },
                    )
                    EditorPill(
                        text = when (state.lineEnding) {
                            "crlf" -> "CRLF"
                            "mixed" -> stringResource(R.string.preview_mixed_eol)
                            else -> "LF"
                        },
                        palette = palette,
                        onClick = { eolDialog = true },
                    )
                    EditorPill(
                        text = stringResource(R.string.editor_chars, state.content.length),
                        palette = palette,
                    )
                    Spacer(Modifier.weight(1f))
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
        EncodingPickerDialog(
            selected = state.encoding,
            onSelect = { value ->
                encodingDialog = false
                if (state.dirty) {
                    pendingEncoding = value
                    discardDialog = true
                } else {
                    viewModel.load(value)
                }
            },
            onDismiss = { encodingDialog = false },
        )
    }

    if (eolDialog) {
        AlertDialog(
            onDismissRequest = { eolDialog = false },
            title = { Text(stringResource(R.string.editor_line_ending)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            eolDialog = false
                            viewModel.setLineEnding("lf")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.editor_eol_lf),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                        if (state.lineEnding == "lf") Text("✓")
                    }
                    TextButton(
                        onClick = {
                            eolDialog = false
                            viewModel.setLineEnding("crlf")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.editor_eol_crlf),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                        if (state.lineEnding == "crlf") Text("✓")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { eolDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text(stringResource(R.string.editor_theme)) },
            text = {
                Column {
                    listOf(
                        EditorThemeMode.AUTO to R.string.editor_theme_auto,
                        EditorThemeMode.LIGHT to R.string.editor_theme_light,
                        EditorThemeMode.SEPIA to R.string.editor_theme_sepia,
                    ).forEach { (mode, label) ->
                        TextButton(
                            onClick = {
                                themeMode = mode
                                EditorThemePreference.write(context, mode)
                                themeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(label),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                            )
                            if (themeMode == mode) Text("✓")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialog = false }) {
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
    code: Boolean,
    wrap: Boolean,
    fieldValue: TextFieldValue,
    palette: EditorPalette,
    vertical: androidx.compose.foundation.ScrollState,
    horizontal: androidx.compose.foundation.ScrollState,
    transformation: VisualTransformation,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            fontFamily = CodeFontFamily,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
    }
    val showGutter = !markdown && (code || !wrap)
    val gutterStyle = bodyStyle.copy(
        fontFamily = CodeFontFamily,
        fontSize = 12.sp,
        lineHeight = 24.sp,
    )
    val density = LocalDensity.current
    val lineHeight = with(density) { 24.sp.toDp() }
    val activeLine = remember(fieldValue.text, fieldValue.selection.start) {
        fieldValue.text.take(fieldValue.selection.start.coerceIn(0, fieldValue.text.length))
            .count { it == '\n' }
    }
    Row(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(vertical)
            .padding(vertical = 8.dp),
    ) {
        if (showGutter) {
            Text(
                text = numbers,
                style = gutterStyle,
                color = palette.gutter,
                textAlign = TextAlign.End,
                softWrap = false,
                modifier = Modifier
                    .width((lineCount.toString().length * 8 + 20).dp)
                    .padding(start = 12.dp, end = 8.dp),
            )
        }
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = palette.cursor,
                backgroundColor = palette.cursor.copy(alpha = 0.24f),
            ),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(if (!markdown && !wrap) Modifier.horizontalScroll(horizontal) else Modifier),
            ) {
                if (code && !wrap) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = 4.dp + lineHeight * activeLine)
                            .height(lineHeight)
                            .background(palette.activeLine),
                    )
                }
                BasicTextField(
                    value = fieldValue,
                    onValueChange = onValueChange,
                    readOnly = !state.writable,
                    textStyle = bodyStyle.copy(color = palette.text),
                    cursorBrush = SolidColor(palette.cursor),
                    visualTransformation = transformation,
                    onTextLayout = onTextLayout,
                    modifier = (if (markdown || wrap) Modifier.fillMaxWidth() else Modifier)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .focusRequester(focusRequester),
                )
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    markdown: Boolean,
    palette: EditorPalette,
    canUndo: Boolean,
    canRedo: Boolean,
    onInsert: (String, String) -> Unit,
    onPrefix: (String) -> Unit,
    onLink: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Surface(color = palette.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (markdown) {
                ToolButton("H1", palette) { onPrefix("# ") }
                ToolButton(stringResource(R.string.markdown_bold), palette) { onInsert("**", "**") }
                ToolButton(stringResource(R.string.markdown_italic), palette) { onInsert("*", "*") }
                ToolButton(stringResource(R.string.markdown_list), palette) { onPrefix("- ") }
                ToolButton(stringResource(R.string.markdown_ordered), palette) { onPrefix("1. ") }
                ToolButton(stringResource(R.string.markdown_quote), palette) { onPrefix("> ") }
                ToolButton(stringResource(R.string.markdown_code), palette) { onInsert("`", "`") }
                ToolButton(stringResource(R.string.markdown_link), palette) { onLink() }
            } else {
                ToolButton("Tab", palette) { onInsert("  ", "") }
                listOf("\"", "'", "(", ")", "{", "}", "[", "]", "<", ">", "=", ":", ";", "/").forEach { symbol ->
                    ToolButton(symbol, palette) {
                        when (symbol) {
                            "\"", "'", "(", ")", "{", "}", "[", "]", "<", ">" -> onInsert(symbol, symbol)
                            else -> onInsert(symbol, "")
                        }
                    }
                }
            }
            ToolButton("↶", palette, enabled = canUndo, onClick = onUndo)
            ToolButton("↷", palette, enabled = canRedo, onClick = onRedo)
        }
    }
}

@Composable
private fun ToolButton(
    text: String,
    palette: EditorPalette,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.surface,
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) palette.text else palette.muted.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun EditorPill(
    text: String,
    palette: EditorPalette,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = palette.surface,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
