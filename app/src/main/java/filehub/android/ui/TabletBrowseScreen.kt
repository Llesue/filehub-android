package filehub.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import filehub.android.R
import filehub.android.core.model.BrowserViewMode
import filehub.android.core.model.FileRoot
import filehub.android.core.model.SortOrder
import filehub.android.data.SessionState
import filehub.android.data.api.VersionDto

@Composable
fun TabletBrowseScreen(
    session: SessionState,
    filesViewModel: FilesViewModel,
    usage: UsageUiState,
    serverVersion: VersionDto?,
    clientUpdate: ClientUpdateUiState?,
    theme: String,
    onToggleTheme: () -> Unit,
    imageLoader: coil3.ImageLoader? = null,
    onSearch: (Long?) -> Unit,
    onPauseAudio: () -> Unit,
    onResumeAudio: () -> Unit,
    directoryPicker: DirectoryPickerViewModel,
    onOpenMy: () -> Unit,
    onOpenServerPicker: () -> Unit,
    onShares: () -> Unit,
) {
    val files by filesViewModel.state.collectAsStateWithLifecycle()
    val active = session.active
    var createRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(active?.id, session.roots.map { it.id }, files.profile?.id, files.root?.id) {
        val profile = session.active ?: return@LaunchedEffect
        val current = filesViewModel.state.value
        val rootStillExists = session.roots.any { it.id == current.root?.id }
        if (current.profile?.id == profile.id && current.root != null && rootStillExists) {
            return@LaunchedEffect
        }
        val first = session.roots.firstOrNull { it.online != false }
            ?: session.roots.firstOrNull()
            ?: run {
                if (current.root != null) filesViewModel.clear()
                return@LaunchedEffect
            }
        filesViewModel.open(profile, first, "")
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidebarWidth = tabletSidebarWidth(maxWidth.value)
        val compactHeader = maxWidth < 900.dp
        Column(modifier = Modifier.fillMaxSize()) {
            TabletTopBar(
                session = session,
                files = files,
                onOpenServerPicker = onOpenServerPicker,
                onSearch = { onSearch(files.root?.id) },
                onViewChange = filesViewModel::setView,
                onSortChange = filesViewModel::setSort,
                theme = theme,
                onToggleTheme = onToggleTheme,
                imageLoader = imageLoader,
                onUpload = { createRequest++ },
                onOpenMy = onOpenMy,
                sidebarWidth = sidebarWidth,
                compact = compactHeader,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            )
            Row(modifier = Modifier.weight(1f)) {
                TabletSidebar(
                    session = session,
                    selectedRootId = files.root?.id,
                    usage = usage,
                    serverVersion = serverVersion,
                    clientUpdate = clientUpdate,
                    imageLoader = imageLoader,
                    onOpenRoot = { root ->
                        session.active?.let { profile -> filesViewModel.open(profile, root, "") }
                    },
                    onShares = onShares,
                    width = sidebarWidth,
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    when {
                        active == null && session.loading -> TabletLoading()
                        active == null -> TabletEmpty(
                            title = stringResource(R.string.browse_no_server_title),
                            body = stringResource(R.string.browse_no_server_body),
                        )
                        files.root == null && session.roots.isEmpty() -> TabletEmpty(
                            title = stringResource(R.string.browse_no_roots),
                            body = stringResource(R.string.column_empty_body),
                        )
                        files.root == null -> TabletEmpty(
                            title = stringResource(R.string.column_empty_title),
                            body = stringResource(R.string.column_empty_body),
                        )
                        else -> FileBrowserScreen(
                            viewModel = filesViewModel,
                            roots = session.roots,
                            onSearch = onSearch,
                            onPauseAudio = onPauseAudio,
                            onResumeAudio = onResumeAudio,
                            directoryPicker = directoryPicker,
                            shell = ShellMode.Wide,
                            onOpenMy = onOpenMy,
                            tabletMode = true,
                            tabletCreateRequest = createRequest,
                            onBack = {
                                if (files.path.isNotEmpty()) filesViewModel.goUp()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletTopBar(
    session: SessionState,
    files: FileBrowserState,
    onOpenServerPicker: () -> Unit,
    onSearch: () -> Unit,
    onViewChange: (BrowserViewMode) -> Unit,
    onSortChange: (filehub.android.core.model.FileSort) -> Unit,
    theme: String,
    onToggleTheme: () -> Unit,
    imageLoader: coil3.ImageLoader? = null,
    onUpload: () -> Unit,
    onOpenMy: () -> Unit,
    sidebarWidth: Dp,
    compact: Boolean,
) {
    val active = session.active
    val canSearch = files.root != null && files.root.isRemote != true
    val canUpload = files.root != null && !files.readOnly
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(62.dp)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .width(sidebarWidth)
                    .clickable(onClick = onOpenServerPicker)
                    .padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerIcon(
                    profile = active,
                    size = 28.dp,
                    corner = 8.dp,
                    imageLoader = imageLoader,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = active?.displayName ?: stringResource(
                        if (session.loading) R.string.server_loading else R.string.servers_empty_title,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.server_picker_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 560.dp)
                        .height(38.dp)
                        .clickable(enabled = canSearch, onClick = onSearch),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row {
                        ViewButton(
                            icon = R.drawable.ic_view_list,
                            selected = files.view == BrowserViewMode.List,
                            onClick = { onViewChange(BrowserViewMode.List) },
                        )
                        ViewButton(
                            icon = R.drawable.ic_view_grid,
                            selected = files.view == BrowserViewMode.Grid,
                            onClick = { onViewChange(BrowserViewMode.Grid) },
                        )
                    }
                }
                SortChip(
                    sort = files.sort,
                    order = files.order,
                    remote = files.root?.isRemote == true,
                    onSort = onSortChange,
                )
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        painter = painterResource(R.drawable.ic_theme),
                        contentDescription = stringResource(
                            if (theme == "dark") R.string.theme_light else R.string.theme_dark,
                        ),
                    )
                }
                if (compact) {
                    Button(
                        onClick = onUpload,
                        enabled = canUpload,
                        modifier = Modifier.size(38.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_upload),
                            contentDescription = stringResource(R.string.transfer_upload_action),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Button(
                        onClick = onUpload,
                        enabled = canUpload,
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_upload),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.transfer_upload_action))
                    }
                }
                val avatarUrl = active?.let { profile ->
                    profile.avatarVersion?.takeIf { it > 0 }?.let { version ->
                        "${profile.baseUrl.trimEnd('/')}/api/v1/me/avatar?v=$version"
                    }
                }
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onOpenMy),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = active?.username?.take(1)?.uppercase().orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        if (avatarUrl != null && imageLoader != null) {
                            coil3.compose.AsyncImage(
                                model = avatarUrl,
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewButton(
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .then(
                if (selected) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(8.dp),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TabletSidebar(
    session: SessionState,
    selectedRootId: Long?,
    usage: UsageUiState,
    serverVersion: VersionDto?,
    clientUpdate: ClientUpdateUiState?,
    imageLoader: coil3.ImageLoader?,
    onOpenRoot: (FileRoot) -> Unit,
    onShares: () -> Unit,
    width: Dp,
) {
    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                if (session.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    RootColumn(
                        roots = session.roots,
                        selectedRootId = selectedRootId,
                        enabled = true,
                        baseUrl = session.active?.baseUrl,
                        imageLoader = imageLoader,
                        onSelect = onOpenRoot,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShares)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.sidebar_shares),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            SidebarFooter(
                usage = usage,
                serverVersion = serverVersion,
                clientUpdate = clientUpdate,
            )
        }
    }
}

@Composable
private fun TabletLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun TabletEmpty(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
