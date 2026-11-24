package filehub.android.ui

import android.content.Context

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.R
import filehub.android.ui.theme.LocalWarningColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.snipme.highlights.model.SyntaxLanguage

/** Lets the host Scaffold paint the status/navigation bar area with the editor page colour. */
object DocumentBackdrop {
    var color by mutableStateOf<Color?>(null)
}

private val EncodingCycle = listOf(
    "utf-8",
    "gb18030",
    "gbk",
    "big5",
    "shift_jis",
    "euc-kr",
    "utf-16le",
    "utf-16be",
    "windows-1252",
    "windows-1251",
    "windows-1250",
    "iso-8859-15",
    "iso-8859-1",
)

@Composable
fun TextEditorScreen(
    viewModel: TextEditorViewModel,
    onBack: () -> Unit,
    startInEdit: Boolean = false,
    onSaved: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = viewModel.pathLabel()
    val language = remember(name) { syntaxLanguageFor(name) }
    val isCode = language != SyntaxLanguage.DEFAULT && !viewModel.isMarkdown

    var editing by remember { mutableStateOf(startInEdit) }
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val history = remember { EditorHistory() }
    var historyVersion by remember { mutableIntStateOf(0) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var encodingDialog by remember { mutableStateOf(false) }
    var eolDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIndex by remember { mutableIntStateOf(0) }
    var findFocusRequest by remember { mutableIntStateOf(0) }
    val documentPath = viewModel.preferenceKey()
    var wrap by remember(documentPath) {
        mutableStateOf(EditorWrapPreference.read(context, documentPath))
    }
    var themeMode by remember { mutableStateOf(EditorThemePreference.read(context)) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    val palette = rememberEditorPalette(themeMode)
    val activity = remember(context) { context.findDocumentActivity() }
    val view = LocalView.current
    SideEffect { DocumentBackdrop.color = palette.background }
    DisposableEffect(Unit) {
        onDispose { DocumentBackdrop.color = null }
    }
    val appLightChrome = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(activity, palette, view, appLightChrome) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val light = palette.background.luminance() > 0.5f
        controller?.isAppearanceLightStatusBars = light
        controller?.isAppearanceLightNavigationBars = light
        onDispose {
            controller?.isAppearanceLightStatusBars = appLightChrome
            controller?.isAppearanceLightNavigationBars = appLightChrome
        }
    }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val textFocus = remember { FocusRequester() }
    val findFocus = remember { FocusRequester() }
    val matches = remember(state.content, findQuery, findOpen, editing) {
        if (editing && findOpen) findTextMatches(state.content, findQuery) else emptyList()
    }
    val safeFindIndex = if (matches.isEmpty()) 0 else findIndex.coerceIn(0, matches.lastIndex)
    val matchColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val currentMatchColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val syntaxHighlightEnabled = isCode && state.content.length <= 40_000
    val highlighted = rememberHighlightedText(
        text = state.content,
        language = language,
        theme = palette.syntax,
        enabled = syntaxHighlightEnabled,
        debounceMs = 180,
        matches = if (editing && findOpen) matches else emptyList(),
        currentMatch = safeFindIndex,
        matchColor = matchColor,
        currentMatchColor = currentMatchColor,
    )
    val transformation = remember(highlighted, syntaxHighlightEnabled) {
        if (!syntaxHighlightEnabled) {
            VisualTransformation.None
        } else {
            VisualTransformation { input ->
                val annotated = if (highlighted.text == input.text) {
                    highlighted
                } else {
                    AnnotatedString(input.text)
                }
                TransformedText(annotated, OffsetMapping.Identity)
            }
        }
    }

    LaunchedEffect(state.content) {
        if (fieldValue.text != state.content) {
            fieldValue = TextFieldValue(state.content, TextRange(0))
            history.clear()
            historyVersion++
        }
    }

    LaunchedEffect(editing) {
        if (editing) {
            vertical.scrollTo(0)
            horizontal.scrollTo(0)
            headerOffsetPx = 0f
            delay(60)
            textFocus.requestFocus()
        }
    }

    LaunchedEffect(state.justSaved) {
        if (state.justSaved) {
            onSaved?.invoke()
            delay(2_000)
            viewModel.clearJustSaved()
        }
    }

    LaunchedEffect(findFocusRequest) {
        if (findOpen) findFocus.requestFocus()
    }

    LaunchedEffect(findQuery) {
        findIndex = 0
        if (findQuery.isEmpty()) return@LaunchedEffect
        val first = findTextMatches(state.content, findQuery).firstOrNull() ?: return@LaunchedEffect
        fieldValue = fieldValue.copy(selection = TextRange(first.start, first.end))
        scrollToMatch(state.content, layoutResult, vertical, horizontal, wrap, first.start, scope)
    }

    fun requestBack() {
        if (!editing) {
            onBack()
            return
        }
        if (state.dirty) {
            discardDialog = true
        } else {
            editing = false
            headerOffsetPx = 0f
        }
    }

    BackHandler { requestBack() }

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

    fun selectMatch(index: Int) {
        val match = matches.getOrNull(index) ?: return
        findIndex = index
        fieldValue = fieldValue.copy(selection = TextRange(match.start, match.end))
        scrollToMatch(state.content, layoutResult, vertical, horizontal, wrap, match.start, scope)
    }

    val canUndo = remember(historyVersion) { history.canUndo }
    val canRedo = remember(historyVersion) { history.canRedo }
    val charCount = remember(state.content) {
        state.content.codePointCount(0, state.content.length)
    }
    val encodingText = encodingLabel(state.encoding) + if (state.bom) " BOM" else ""

    val density = LocalDensity.current
    val contentTopPadding = with(density) {
        (headerHeightPx + headerOffsetPx).coerceAtLeast(0f).toDp()
    }
    val headerConnection = remember(headerHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f && headerOffsetPx > -headerHeightPx) {
                    val next = (headerOffsetPx + delta)
                        .coerceIn(-headerHeightPx.toFloat(), 0f)
                    val consumed = next - headerOffsetPx
                    headerOffsetPx = next
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                if (delta > 0f && headerOffsetPx < 0f) {
                    val next = (headerOffsetPx + delta)
                        .coerceIn(-headerHeightPx.toFloat(), 0f)
                    val consumedByHeader = next - headerOffsetPx
                    headerOffsetPx = next
                    return Offset(0f, consumedByHeader)
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(headerHeightPx) {
        headerOffsetPx = headerOffsetPx.coerceIn(-headerHeightPx.toFloat(), 0f)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = palette.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(headerConnection)
                        .padding(top = contentTopPadding),
                ) {
                    if (state.loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    val readOnlyReason = state.readOnlyReason
                    if (!state.writable && readOnlyReason != null) {
                        ReadOnlyNotice(state.lossy, readOnlyReason)
                    }

                    state.saveError?.let { message ->
                        ErrorNotice(message = message, onDismiss = viewModel::clearSaveError)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds(),
                    ) {
                        when {
                            state.loading && state.content.isEmpty() -> Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                            state.error != null && state.content.isEmpty() -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = state.error.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = { viewModel.load() }) {
                                    Text(stringResource(R.string.common_retry))
                                }
                            }
                            editing && state.writable -> EditorBody(
                                state = state,
                                markdown = viewModel.isMarkdown,
                                code = isCode,
                                wrap = wrap,
                                fieldValue = fieldValue,
                                palette = palette,
                                vertical = vertical,
                                horizontal = horizontal,
                                transformation = transformation,
                                focusRequester = textFocus,
                                layout = layoutResult,
                                onValueChange = { value ->
                                    if (value.text != fieldValue.text) {
                                        history.record(snapshot(fieldValue), System.currentTimeMillis())
                                        historyVersion++
                                    }
                                    fieldValue = value
                                    viewModel.setContent(value.text)
                                },
                                onTextLayout = { layoutResult = it },
                            )
                            viewModel.isMarkdown -> MarkdownPreview(
                                markdown = state.content,
                                textColor = palette.text,
                                linkColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(palette.background),
                                onScroll = { scrollY ->
                                    headerOffsetPx = -minOf(scrollY, headerHeightPx).toFloat()
                                },
                            )
                            isCode -> CodePreviewBlock(
                                content = state.content,
                                language = language,
                                palette = palette,
                                wrap = wrap,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(palette.background),
                            )
                            else -> PlainPreviewBlock(
                                content = state.content,
                                palette = palette,
                                wrap = wrap,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(palette.background),
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, headerOffsetPx.roundToInt()) }
                        .onSizeChanged { headerHeightPx = it.height }
                        .zIndex(2f),
                    color = palette.background,
                    contentColor = palette.text,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = ::requestBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back),
                                )
                            }
                            FileNameText(
                                name = name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (editing) {
                                IconButton(
                                    onClick = {
                                        findOpen = !findOpen
                                        if (!findOpen) findQuery = ""
                                        findFocusRequest++
                                    },
                                ) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.editor_find),
                                    )
                                }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = stringResource(R.string.file_more),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_undo)) },
                                            enabled = canUndo,
                                            onClick = {
                                                menuOpen = false
                                                undo()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_redo)) },
                                            enabled = canRedo,
                                            onClick = {
                                                menuOpen = false
                                                redo()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_encoding)) },
                                            onClick = {
                                                menuOpen = false
                                                encodingDialog = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_line_ending)) },
                                            onClick = {
                                                menuOpen = false
                                                eolDialog = true
                                            },
                                        )
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
                                                EditorWrapPreference.write(context, documentPath, wrap)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_theme)) },
                                            onClick = {
                                                menuOpen = false
                                                themeDialog = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_preview)) },
                                            enabled = !state.dirty && !state.saving,
                                            onClick = {
                                                menuOpen = false
                                                editing = false
                                                headerOffsetPx = 0f
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (findOpen && editing) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = { findQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .focusRequester(findFocus),
                                placeholder = { Text(stringResource(R.string.editor_find)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { findFocusRequest++ }),
                                trailingIcon = {
                                    Text(
                                        text = if (matches.isEmpty()) {
                                            stringResource(R.string.editor_find_none)
                                        } else {
                                            "${safeFindIndex + 1}/${matches.size}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (matches.isNotEmpty()) {
                                            selectMatch((safeFindIndex - 1 + matches.size) % matches.size)
                                        }
                                    },
                                    enabled = matches.isNotEmpty(),
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.editor_find_previous),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (matches.isNotEmpty()) {
                                            selectMatch((safeFindIndex + 1) % matches.size)
                                        }
                                    },
                                    enabled = matches.isNotEmpty(),
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.editor_find_next),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        findOpen = false
                                        findQuery = ""
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
                }
            }

            DocumentStatusBar(
                encodingText = encodingText,
                uncertain = state.lossy,
                charCount = charCount,
                lineEnding = state.lineEnding,
                size = state.size,
                dirty = state.dirty,
                writable = state.writable,
                editing = editing,
                saving = state.saving,
                background = palette.surface,
                textColor = palette.text,
                mutedColor = palette.muted,
                onClickEncoding = {
                    if (state.lossy && !state.loading) {
                        val next = nextEncoding(state.encoding)
                        viewModel.load(next)
                    }
                },
                onLongClickEncoding = { encodingDialog = true },
                onToggleEdit = {
                    if (editing) {
                        viewModel.save()
                    } else {
                        editing = true
                        headerOffsetPx = 0f
                    }
                },
            )
        }
    }

    if (encodingDialog) {
        EncodingPickerDialog(
            selected = state.encoding,
            onSelect = { encoding ->
                encodingDialog = false
                viewModel.load(encoding)
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
                    listOf("lf", "crlf").forEach { value ->
                        TextButton(
                            onClick = {
                                eolDialog = false
                                viewModel.setLineEnding(value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(
                                    if (value == "lf") R.string.editor_eol_lf else R.string.editor_eol_crlf,
                                ),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                            )
                            if (state.lineEnding == value) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
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
                                EditorThemePreference.write(context, mode)
                                themeMode = mode
                                themeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(label),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                            )
                            if (themeMode == mode) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
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

    if (discardDialog) {
        AlertDialog(
            onDismissRequest = { discardDialog = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialog = false
                        viewModel.setContent(state.original)
                        editing = false
                        headerOffsetPx = 0f
                    },
                ) {
                    Text(
                        text = stringResource(R.string.editor_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { discardDialog = false }) {
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
                TextButton(
                    onClick = {
                        viewModel.dismissConflict()
                        viewModel.load()
                    },
                ) {
                    Text(stringResource(R.string.editor_conflict_reload))
                }
            },
        )
    }

}

@Composable
private fun ReadOnlyNotice(lossy: Boolean, reason: String) {
    val warning = LocalWarningColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = warning.container,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = if (lossy) {
                stringResource(R.string.text_lossy_hint)
            } else {
                stringResource(R.string.editor_read_only, reason)
            },
            style = MaterialTheme.typography.bodySmall,
            color = warning.content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ErrorNotice(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun DocumentStatusBar(
    encodingText: String,
    uncertain: Boolean,
    charCount: Int,
    lineEnding: String,
    size: Long,
    dirty: Boolean,
    writable: Boolean,
    editing: Boolean,
    saving: Boolean,
    background: Color,
    textColor: Color,
    mutedColor: Color,
    onClickEncoding: () -> Unit,
    onLongClickEncoding: () -> Unit,
    onToggleEdit: () -> Unit,
) {
    val context = LocalContext.current
    Surface(color = background, contentColor = textColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uncertain) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = "$encodingText?",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = onClickEncoding,
                                    onLongClick = onLongClickEncoding,
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Text(
                        text = encodingText,
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = onClickEncoding,
                                onLongClick = onLongClickEncoding,
                            )
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = " · " + stringResource(R.string.editor_chars, charCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
                Text(
                    text = " · " + lineEnding.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
                Text(
                    text = " · " + Formatter.formatShortFileSize(context, size),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
                if (dirty) {
                    Text(
                        text = " · " + stringResource(R.string.editor_unsaved),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (!writable) {
                Text(
                    text = stringResource(R.string.editor_read_only_short),
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedColor,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            } else {
                TextButton(
                    onClick = onToggleEdit,
                    enabled = if (editing) dirty && !saving else true,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = when {
                            !editing -> stringResource(R.string.editor_edit)
                            saving -> stringResource(R.string.editor_saving)
                            else -> stringResource(R.string.editor_save)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
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
    layout: TextLayoutResult?,
    onValueChange: (TextFieldValue) -> Unit,
    onTextLayout: (TextLayoutResult) -> Unit,
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
    val fontSize = if (code) 14.sp else 15.sp
    val lineHeight = if (code) 22.sp else 23.sp
    val bodyStyle: TextStyle = if (markdown) {
        MaterialTheme.typography.bodyLarge.copy(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = 0.sp,
        )
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            fontFamily = CodeFontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
        )
    }
    val showGutter = !markdown && (code || !wrap)
    val gutterStyle = bodyStyle.copy(
        fontFamily = CodeFontFamily,
        fontSize = 12.sp,
        lineHeight = lineHeight,
    )
    val gutterWidth = (lineCount.toString().length * 8 + 20).dp
    val density = LocalDensity.current
    val lineHeightDp = with(density) { lineHeight.toDp() }
    val activeLine = remember(fieldValue.text, fieldValue.selection.start) {
        fieldValue.text.take(fieldValue.selection.start.coerceIn(0, fieldValue.text.length))
            .count { it == '\n' }
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(vertical)
            .padding(16.dp)
            .background(palette.background),
    ) {
        if (showGutter) {
            if (layout != null) {
                EditorLineNumbers(
                    content = state.content,
                    layout = layout,
                    style = gutterStyle,
                    color = palette.gutter,
                    width = gutterWidth,
                )
            } else {
                Text(
                    text = numbers,
                    style = gutterStyle,
                    color = palette.gutter,
                    textAlign = TextAlign.End,
                    softWrap = false,
                    modifier = Modifier
                        .width(gutterWidth)
                        .padding(end = 8.dp),
                )
            }
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
                            .offset(y = lineHeightDp * activeLine)
                            .height(lineHeightDp)
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
                        .focusRequester(focusRequester),
                )
            }
        }
    }
}

@Composable
private fun EditorLineNumbers(
    content: String,
    layout: TextLayoutResult,
    style: TextStyle,
    color: Color,
    width: androidx.compose.ui.unit.Dp,
) {
    val numbers = remember(layout, content) {
        buildString {
            var number = 1
            for (line in 0 until layout.lineCount) {
                if (line > 0) append('\n')
                val start = layout.getLineStart(line)
                val startsLogical = line == 0 ||
                    (start > 0 && content.getOrNull(start - 1) == '\n')
                if (startsLogical) {
                    append(number)
                    number++
                }
            }
        }
    }
    Text(
        text = numbers,
        style = style.copy(color = color),
        textAlign = TextAlign.End,
        softWrap = false,
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp),
    )
}

private fun nextEncoding(current: String?): String {
    if (current == null) return EncodingCycle.first()
    val index = EncodingCycle.indexOfFirst { it.equals(current, ignoreCase = true) }
    if (index < 0) return EncodingCycle.first()
    return EncodingCycle[(index + 1) % EncodingCycle.size]
}

private fun scrollToMatch(
    content: String,
    layout: TextLayoutResult?,
    vertical: androidx.compose.foundation.ScrollState,
    horizontal: androidx.compose.foundation.ScrollState,
    wrap: Boolean,
    offset: Int,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (content.isEmpty() || layout == null) return
    val box = runCatching { layout.getBoundingBox(offset) }.getOrNull() ?: return
    scope.launch {
        val target = (vertical.value + box.top.toInt() - 120).coerceAtLeast(0)
        vertical.animateScrollTo(target)
        if (!wrap) {
            horizontal.animateScrollTo((horizontal.value + box.left.toInt() - 48).coerceAtLeast(0))
        }
    }
}

private tailrec fun Context.findDocumentActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findDocumentActivity()
    else -> null
}
