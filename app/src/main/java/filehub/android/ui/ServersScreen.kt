package filehub.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import filehub.android.R
import filehub.android.core.model.ServerProfile
import filehub.android.data.ServerConnection
import filehub.android.data.SessionState

@Composable
fun ServersScreen(
    state: SessionState,
    connection: ServerConnection?,
    onAddServer: () -> Unit,
    onSelect: (String) -> Unit,
    onReauth: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
    onMoveProfile: (Int, Int) -> Unit,
    onLoadVersions: () -> Unit = {},
    onOpenDrawer: (() -> Unit)? = null,
) {
    var pendingRemoval by remember { mutableStateOf<ServerProfile?>(null) }

    LaunchedEffect(Unit) { onLoadVersions() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onOpenDrawer?.let { open ->
                IconButton(onClick = open) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.nav_menu),
                    )
                }
            }
            Text(
                text = stringResource(R.string.tab_servers),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddServer) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.servers_add))
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (state.profiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandMark()
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.servers_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.servers_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAddServer) {
                    Text(stringResource(R.string.servers_add))
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = if (maxWidth >= 600.dp) 2 else 1
                ReorderableServerGrid(
                    profiles = state.profiles,
                    activeId = state.active?.id,
                    authExpiredProfiles = state.authExpiredProfiles,
                    connection = connection,
                    columns = columns,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp,
                    ),
                    onMoveProfile = onMoveProfile,
                    onSelect = onSelect,
                    onReauth = onReauth,
                    onEdit = onEdit,
                    onRemove = { pendingRemoval = it },
                )
            }
        }
    }

    pendingRemoval?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.servers_remove_title)) },
            text = {
                Text(
                    stringResource(R.string.servers_remove_body) +
                        "\n\n${profile.displayName}\n${profile.baseUrl}",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(profile.id)
                        pendingRemoval = null
                    },
                ) {
                    Text(stringResource(R.string.common_remove))
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
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(colorResource(R.color.filehub_blue), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_mark),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
