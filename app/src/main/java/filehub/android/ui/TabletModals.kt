package filehub.android.ui

import androidx.compose.foundation.background
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
    appLockEnabled: Boolean? = null,
    onAppLockChange: (Boolean) -> Unit = {},
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
                appLockEnabled = appLockEnabled,
                onAppLockChange = onAppLockChange,
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
    onReauth: (String) -> Unit,
    onMoveProfile: (Int, Int) -> Unit,
    onLoadVersions: () -> Unit,
    onClose: () -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<ServerProfile?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) { onLoadVersions() }
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
                        ReorderableServerGrid(
                            profiles = state.profiles,
                            activeId = state.active?.id,
                            authExpiredProfiles = state.authExpiredProfiles,
                            connection = connection,
                            columns = columns,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 4.dp,
                                end = 4.dp,
                                top = 4.dp,
                                bottom = 16.dp,
                            ),
                            onMoveProfile = onMoveProfile,
                            onSelect = { id ->
                                onClose()
                                onSelect(id)
                            },
                            onReauth = { id ->
                                onClose()
                                onReauth(id)
                            },
                            onEdit = { id ->
                                onClose()
                                onEditServer(id)
                            },
                            onRemove = { pendingRemoval = it },
                        )
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
