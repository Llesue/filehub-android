package filehub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil3.ImageLoader
import filehub.android.R
import filehub.android.core.model.ServerProfile
import filehub.android.data.ServerConnection
import filehub.android.data.ServerConnectionStatus
import filehub.android.data.SessionState
import filehub.android.data.api.VersionDto

@Composable
fun MyAccountModal(
    state: SessionState,
    imageLoader: ImageLoader?,
    usage: UsageUiState,
    serverVersion: VersionDto?,
    clientUpdate: ClientUpdateUiState,
    versionError: String?,
    theme: String,
    language: String,
    onThemeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onClose: () -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onTransfers: () -> Unit,
    onPhotoSync: () -> Unit,
    onFolderSync: () -> Unit,
    onShares: () -> Unit,
    onLoadUsage: () -> Unit,
    onLoadServerVersion: () -> Unit,
    onLoadClientUpdate: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        HideDialogStatusBar()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, end = 12.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
        Surface(
            modifier = Modifier
                .width(340.dp)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
        ) {
            MyScreen(
                state = state,
                imageLoader = imageLoader,
                onLogout = {
                    onClose()
                    onLogout()
                },
                onRefresh = onRefresh,
                onTransfers = {
                    onClose()
                    onTransfers()
                },
                onPhotoSync = {
                    onClose()
                    onPhotoSync()
                },
                onFolderSync = {
                    onClose()
                    onFolderSync()
                },
                onShares = {
                    onClose()
                    onShares()
                },
                usage = usage,
                serverVersion = serverVersion,
                clientUpdate = clientUpdate,
                versionError = versionError,
                onLoadUsage = onLoadUsage,
                onLoadServerVersion = onLoadServerVersion,
                onLoadClientUpdate = onLoadClientUpdate,
                theme = theme,
                onThemeChange = onThemeChange,
                language = language,
                onLanguageChange = onLanguageChange,
                onOpenDrawer = null,
                onClose = onClose,
                showQuota = false,
                showVersion = false,
            )
        }
        }
    }
}

@Composable
fun TabletServerPickerModal(
    state: SessionState,
    connection: ServerConnection?,
    onSelect: (String) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onClose: () -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<ServerProfile?>(null) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        HideDialogStatusBar()
        Surface(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.server_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            onClose()
                            onAddServer()
                        },
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.servers_add),
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.common_close),
                        )
                    }
                }
                if (state.profiles.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.servers_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.servers_empty_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                    ) {
                        val columns = if (maxWidth >= 600.dp) 2 else 1
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 4.dp,
                                end = 4.dp,
                                top = 4.dp,
                                bottom = 16.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.profiles, key = { it.id }) { profile ->
                                ServerPickerCard(
                                    profile = profile,
                                    active = state.active?.id == profile.id,
                                    connection = connection?.takeIf {
                                        it.profileId == profile.id
                                    },
                                    onClick = {
                                        onClose()
                                        onSelect(profile.id)
                                    },
                                    onEdit = {
                                        onClose()
                                        onEditServer(profile.id)
                                    },
                                    onRemove = { pendingRemoval = profile },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.servers_remove_title)) },
            text = { Text(stringResource(R.string.servers_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        onRemoveServer(profile.id)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ServerPickerCard(
    profile: ServerProfile,
    active: Boolean,
    connection: ServerConnection?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profile.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profile.baseUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val status = when {
                    connection?.status == ServerConnectionStatus.Connecting ->
                        stringResource(R.string.login_connecting)
                    connection?.status == ServerConnectionStatus.Failed ->
                        stringResource(R.string.browse_root_offline)
                    active -> stringResource(R.string.servers_active)
                    else -> null
                }
                if (status != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connection?.status == ServerConnectionStatus.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            if (connection?.status == ServerConnectionStatus.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.file_more),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.server_edit)) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.common_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menu = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun HideDialogStatusBar() {
    val view = LocalView.current
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(dialogWindow) {
        val controller = dialogWindow?.let { window ->
            WindowCompat.getInsetsController(window, view)
        }
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            val activity = view.context.findHostActivity()
            val hostWindow = activity?.window
            if (hostWindow != null) {
                WindowCompat.getInsetsController(hostWindow, hostWindow.decorView)
                    .hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}

private tailrec fun android.content.Context.findHostActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
