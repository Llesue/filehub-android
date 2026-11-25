package filehub.android.ui

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import filehub.android.BuildConfig
import filehub.android.R
import filehub.android.core.model.FileKind
import filehub.android.core.model.FileRoot
import filehub.android.data.SessionState
import filehub.android.data.api.VersionDto

enum class ShellMode {
    Compact,
    Landscape,
    Wide,
}

@Composable
fun AppNavigationDrawer(
    session: SessionState,
    tab: MainTab,
    usage: UsageUiState,
    onSelectTab: (MainTab) -> Unit,
    onOpenRoot: (FileRoot) -> Unit,
    onShares: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val offlineHint = stringResource(R.string.browse_root_offline_toast)
    var offlineExpanded by remember { mutableStateOf(false) }
    val groups = rootGroups(session.roots)
    val online = groups.online
    val offline = groups.offline

    ModalDrawerSheet(
        modifier = Modifier.width(310.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            session.active?.let { active ->
                Text(
                    text = active.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                MainTab.entries.forEach { item ->
                    NavigationDrawerItemRow(
                        icon = item.icon,
                        label = stringResource(item.labelRes),
                        selected = tab == item,
                        onClick = {
                            onSelectTab(item)
                            onClose()
                        },
                    )
                }
                if (online.isNotEmpty()) {
                    SidebarSectionLabel(stringResource(R.string.sidebar_directories))
                    online.forEach { root ->
                        SidebarRootRow(
                            root = root,
                            selected = false,
                            onClick = {
                                onOpenRoot(root)
                                onClose()
                            },
                        )
                    }
                }
                if (offline.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { offlineExpanded = !offlineExpanded }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (offlineExpanded) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.browse_offline_group, offline.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (offlineExpanded) {
                        offline.forEach { root ->
                            SidebarRootRow(
                                root = root,
                                selected = false,
                                dimmed = true,
                                onClick = {
                                    Toast.makeText(context, offlineHint, Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
                SidebarItem(
                    icon = Icons.Filled.Share,
                    label = stringResource(R.string.sidebar_shares),
                    selected = false,
                    onClick = {
                        onShares()
                        onClose()
                    },
                )
            }
            SidebarFooter(usage = usage, serverVersion = null)
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NavigationDrawerItemRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun SidebarSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun SidebarRootRow(
    root: FileRoot,
    selected: Boolean,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.5f else 1f)
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_file_folder),
            contentDescription = null,
            tint = fileIconColor(FileKind.Folder),
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = root.alias,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (root.isRemote) {
            Text(
                text = stringResource(R.string.browse_root_remote),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SidebarFooter(
    usage: UsageUiState,
    serverVersion: VersionDto?,
    clientUpdate: ClientUpdateUiState? = null,
) {
    val context = LocalContext.current
    val data = usage.usage
    val usedBytes = data?.usedBytes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (usedBytes != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.sidebar_quota),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = buildString {
                        append(Formatter.formatShortFileSize(context, usedBytes))
                        data.quotaBytes?.takeIf { it > 0 }?.let {
                            append(" / ")
                            append(Formatter.formatShortFileSize(context, it))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val quota = data.quotaBytes?.takeIf { it > 0 }
            if (quota != null) {
                LinearProgressIndicator(
                    progress = { (data.usedBytes.toFloat() / quota).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )
            }
        }
        val serverNewer = serverVersion?.let {
            it.checkEnabled && it.updateAvailable && !it.latest.isNullOrBlank()
        } == true
        Text(
            text = stringResource(
                R.string.sidebar_server_version_format,
                serverVersion?.version ?: "—",
            ) + if (serverNewer) {
                stringResource(R.string.version_update_available)
            } else {
                ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val release = clientUpdate?.release
        val clientNewer = release != null &&
            filehub.android.data.ClientUpdateChecker.isNewer(
                release.version,
                BuildConfig.VERSION_NAME,
            )
        Text(
            text = stringResource(
                R.string.sidebar_client_version_format,
                BuildConfig.VERSION_NAME,
            ) + if (clientNewer) {
                stringResource(R.string.version_update_available)
            } else {
                ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
